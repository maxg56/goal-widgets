package app.goalwidgets.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.goalwidgets.GoalWidgetsApplication
import app.goalwidgets.MainActivity
import app.goalwidgets.R
import app.goalwidgets.core.CountdownEngine
import app.goalwidgets.core.GoalOrder
import app.goalwidgets.core.SortKey
import app.goalwidgets.core.WeeklySchedule
import app.goalwidgets.ui.GoalLabels
import app.goalwidgets.ui.titleWithEmoji
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Résumé hebdomadaire (facultatif, désactivé par défaut) : une notification le jour et à l'heure
 * choisis, listant les objectifs non archivés du plus proche au plus lointain (« 🎄 Noël — dans
 * 27 jours »). Une seule alarme, reprogrammée à chaque [MilestoneNotifications.reschedule] et après
 * chaque envoi.
 */
object WeeklySummary {
    const val CHANNEL_ID = "weekly_summary"
    private const val ACTION = "app.goalwidgets.action.WEEKLY_SUMMARY"
    private const val TAG = "weekly_summary"
    private const val MAX_LINES = 6
    private const val WINDOW_MILLIS = 10 * 60_000L

    private const val KEY_ENABLED = "weekly_summary_enabled"
    private const val KEY_DAY = "weekly_summary_day"
    private const val KEY_MINUTES = "weekly_summary_minutes"

    data class Settings(val enabled: Boolean, val day: DayOfWeek, val time: LocalTime)

    fun settings(context: Context): Settings {
        val prefs = NotificationSettings.prefs(context)
        val minutes = prefs.getInt(KEY_MINUTES, 18 * 60).coerceIn(0, 24 * 60 - 1)
        return Settings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            day = DayOfWeek.of(prefs.getInt(KEY_DAY, DayOfWeek.SUNDAY.value).coerceIn(1, 7)),
            time = LocalTime.of(minutes / 60, minutes % 60),
        )
    }

    fun save(context: Context, settings: Settings) {
        NotificationSettings.prefs(context).edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putInt(KEY_DAY, settings.day.value)
            .putInt(KEY_MINUTES, settings.time.hour * 60 + settings.time.minute)
            .apply()
    }

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.weekly_channel_name), NotificationManager.IMPORTANCE_LOW)
            .apply { description = context.getString(R.string.weekly_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Programme (ou annule) l'alarme du prochain résumé. */
    fun schedule(context: Context) {
        val app = context.applicationContext
        val alarms = app.getSystemService(AlarmManager::class.java)
        val s = settings(app)
        if (!s.enabled) {
            alarms.cancel(alarmIntent(app))
            return
        }
        val at = WeeklySchedule.next(LocalDateTime.now(), s.day, s.time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarms.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MILLIS, alarmIntent(app))
    }

    /** Affiche le résumé maintenant (alarme, ou bouton « Essayer » des réglages). Renvoie `false` si rien n'est affiché. */
    suspend fun show(context: Context): Boolean {
        val app = context.applicationContext as GoalWidgetsApplication
        if (!MilestoneNotifications.canPost(app)) return false
        val today = LocalDate.now()
        val rows = GoalOrder.CLOSEST_DATE.sort(app.database.goalDao().getAll().filterNot { it.archived }, today) {
            SortKey(it.uid, it.toGoal(), Instant.ofEpochMilli(it.createdAtMillis))
        }
        if (rows.isEmpty()) return false
        val res = app.resources
        val lines = rows.take(MAX_LINES).map { row ->
            val state = CountdownEngine.compute(row.toGoal(), today)
            "${titleWithEmoji(row.title, row.style().emoji)} — ${GoalLabels.status(res, state)}"
        }
        val inbox = NotificationCompat.InboxStyle()
        lines.forEach(inbox::addLine)
        if (rows.size > MAX_LINES) inbox.setSummaryText(res.getQuantityString(R.plurals.weekly_more, rows.size - MAX_LINES, rows.size - MAX_LINES))
        val open = PendingIntent.getActivity(
            app,
            0,
            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(res.getString(R.string.weekly_title))
            .setContentText(lines.first())
            .setStyle(inbox)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(app).notify(TAG, 0, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, WeeklySummaryReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Alarme du résumé hebdomadaire : affiche puis programme la semaine suivante. */
class WeeklySummaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                WeeklySummary.show(context)
                WeeklySummary.schedule(context)
            } finally {
                pending.finish()
            }
        }
    }
}
