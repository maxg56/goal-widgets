package app.goalwidgets.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.goalwidgets.GoalWidgetsApplication
import app.goalwidgets.MainActivity
import app.goalwidgets.R
import app.goalwidgets.core.Milestone
import app.goalwidgets.core.MilestoneKind
import app.goalwidgets.core.MilestonePlanner
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Couche OS (mince) des notifications de jalons : le planificateur pur décide quoi et quel jour,
 * on programme une alarme par jalon à l'heure d'envoi globale.
 *
 * Tout l'horizon est reprogrammé à chaque appel de [reschedule] : modification d'un objectif ou
 * de l'heure, démarrage de l'app, redémarrage, changement d'heure/de fuseau, tâche quotidienne.
 * Identifiants stables : l'alarme d'un jalon est un `PendingIntent` dont l'URI est l'id du jalon
 * (`<goalId>:<kind>:<AAAA-MM-JJ>`) et la notification affichée porte cet id en étiquette →
 * reprogrammer remplace au lieu de dupliquer.
 */
object MilestoneNotifications {
    const val CHANNEL_ID = "milestones"

    /** Fenêtre glissante : la tâche quotidienne la fait avancer. */
    const val HORIZON_DAYS = 60

    /** Plafond d'alarmes en attente (Android limite le nombre d'alarmes par app, ~500). */
    const val MAX_SCHEDULED = 100

    /** Fenêtre de déclenchement (10 min = minimum accepté par Android 12+, sans alarme exacte). */
    private const val WINDOW_MILLIS = 10 * 60_000L

    private const val ACTION_FIRE = "app.goalwidgets.action.MILESTONE"
    private const val KEY_SCHEDULED = "scheduled_milestone_ids"
    private const val EXTRA_GOAL_ID = "goal_id"
    private const val EXTRA_KIND = "kind"
    private const val EXTRA_VALUE = "value"
    private const val EXTRA_DATE = "date"

    private val mutex = Mutex()

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.notif_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** L'app peut-elle afficher des notifications (autorisation Android 13+ et réglage système) ? */
    fun canPost(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Jalon programmé et son heure d'envoi réelle (epoch ms). */
    data class Scheduled(val milestone: Milestone, val triggerAtMillis: Long)

    /** Annule et reprogramme toutes les alarmes de l'horizon à partir de la base. */
    suspend fun reschedule(context: Context) = mutex.withLock {
        val app = context.applicationContext
        val wanted = wanted(app)

        val alarms = app.getSystemService(AlarmManager::class.java)
        val prefs = NotificationSettings.prefs(app)
        val previous = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
        val wantedIds = wanted.map { it.milestone.id }.toSet()
        for (id in previous - wantedIds) {
            alarms.cancel(alarmIntent(app, Uri.parse(uriOf(id)), Intent()))
        }
        for (s in wanted) {
            // setWindow : sans permission d'alarme exacte ; une alarme identique (même URI) est remplacée.
            alarms.setWindow(AlarmManager.RTC_WAKEUP, s.triggerAtMillis, WINDOW_MILLIS, alarmIntent(app, s.milestone))
        }
        prefs.edit().putStringSet(KEY_SCHEDULED, wantedIds).apply()
    }

    /**
     * Jalons que [reschedule] programme (les [MAX_SCHEDULED] plus proches de l'horizon, heure
     * d'envoi pas encore passée), du plus proche au plus lointain. Aussi lu par l'écran de debug (t14).
     */
    suspend fun wanted(context: Context): List<Scheduled> {
        val app = context.applicationContext
        val dao = (app as GoalWidgetsApplication).database.goalDao()
        val today = LocalDate.now()
        val sendTime = NotificationSettings.sendTime(app)
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()

        val planned = dao.getAll().flatMap { row ->
            MilestonePlanner.plan(
                goalId = row.id.toString(),
                goal = row.toGoal(),
                notificationsEnabled = row.notificationsEnabled,
                today = today,
                horizonDays = HORIZON_DAYS,
            )
        }
        // Le jalon du jour n'est programmé que si l'heure d'envoi n'est pas encore passée.
        return MilestonePlanner.closest(
            planned.filter { triggerAt(it.date, sendTime, zone) > now },
            MAX_SCHEDULED,
        )
            .map { Scheduled(it, triggerAt(it.date, sendTime, zone)) }
            .sortedBy { it.triggerAtMillis }
    }

    /** Alarme déclenchée : affiche la notification si le jalon est toujours d'actualité. */
    suspend fun fire(context: Context, intent: Intent) {
        val app = context.applicationContext
        val milestone = milestoneOf(intent) ?: return
        val goalId = milestone.goalId.toLong()
        val date = milestone.date

        // Alarme en retard (téléphone éteint, Doze…) : seulement le jour même du jalon, sinon le
        // texte serait faux d'un jour. Objectif modifié, désactivé ou supprimé : le planificateur fait foi.
        // Exception : une heure d'envoi après 23:50 peut, à cause de la fenêtre de [WINDOW_MILLIS],
        // sonner juste après minuit ; le jalon de la veille reste alors valable.
        val now = LocalDateTime.now()
        val windowStartDay = now.minusNanos(WINDOW_MILLIS * 1_000_000).toLocalDate()
        val today = if (date == windowStartDay) date else now.toLocalDate()
        if (date != today) return
        val row = (app as GoalWidgetsApplication).database.goalDao().getById(goalId)
        val deliver = MilestonePlanner.shouldDeliver(milestone, row?.toGoal(), row?.notificationsEnabled == true, today)
        if (row == null || !deliver) return
        show(app, row.title, milestone)
    }

    /**
     * Affichage d'une notification de jalon (texte, appui → ouverture de l'objectif), sans aucune
     * vérification de date : chemin commun à l'alarme réelle et aux outils de debug (t14).
     * Renvoie `false` si l'app n'a pas le droit de notifier.
     */
    fun show(context: Context, goalTitle: String, milestone: Milestone): Boolean {
        val app = context.applicationContext
        if (!canPost(app)) return false
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(goalTitle)
            .setContentText(NotificationTexts.body(app.resources, milestone))
            .setContentIntent(openGoalIntent(app, milestone.goalId.toLong(), milestone.id))
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(app).notify(milestone.id, 0, notification)
            true
        } catch (_: SecurityException) {
            // Autorisation retirée entre la vérification et l'envoi.
            false
        }
    }

    /** Écrit [m] dans les extras de [intent] (alarme réelle, et alarme de test en debug). */
    fun putMilestone(intent: Intent, m: Milestone): Intent = intent
        .putExtra(EXTRA_GOAL_ID, m.goalId.toLong())
        .putExtra(EXTRA_KIND, m.kind.wire)
        .putExtra(EXTRA_VALUE, m.value)
        .putExtra(EXTRA_DATE, m.date.toString())

    /** Jalon porté par une alarme, `null` si les extras sont incomplets. */
    fun milestoneOf(intent: Intent): Milestone? {
        val goalId = intent.getLongExtra(EXTRA_GOAL_ID, -1L)
        val date = intent.getStringExtra(EXTRA_DATE)?.let(LocalDate::parse) ?: return null
        val kind = intent.getStringExtra(EXTRA_KIND)?.let(MilestoneKind::fromWire) ?: return null
        return Milestone(goalId.toString(), date, kind, intent.getIntExtra(EXTRA_VALUE, 0))
    }

    private fun triggerAt(date: LocalDate, sendTime: java.time.LocalTime, zone: ZoneId): Long =
        LocalDateTime.of(date, sendTime).atZone(zone).toInstant().toEpochMilli()

    private fun uriOf(milestoneId: String) = "goalwidgets://milestone/" + Uri.encode(milestoneId)

    private fun alarmIntent(context: Context, m: Milestone): PendingIntent =
        alarmIntent(
            context,
            Uri.parse(uriOf(m.id)),
            putMilestone(Intent(), m),
        )

    // L'identité d'un PendingIntent = composant + action + URI (les extras ne comptent pas).
    private fun alarmIntent(context: Context, uri: Uri, extras: Intent): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, MilestoneAlarmReceiver::class.java).setAction(ACTION_FIRE).setData(uri).putExtras(extras),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * Appui sur la notification : ouvre l'app sur l'objectif (même extra que le widget, t06).
     * `SINGLE_TOP` : app déjà ouverte → `MainActivity.onNewIntent`, formulaire en cours conservé.
     */
    private fun openGoalIntent(context: Context, goalId: Long, milestoneId: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .setData(Uri.parse(uriOf(milestoneId)))
                .putExtra(MainActivity.EXTRA_GOAL_ID, goalId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
