package app.goalwidgets.notifications

import android.content.Context
import java.time.LocalTime

/**
 * Réglage global : heure d'envoi des notifications (minutes depuis minuit, défaut 9 h).
 * Même clé et même défaut que l'app iOS (`notification_send_minutes`, 540).
 */
object NotificationSettings {
    private const val PREFS = "notifications"
    private const val KEY_SEND_MINUTES = "notification_send_minutes"
    const val DEFAULT_SEND_MINUTES = 9 * 60

    fun sendTime(context: Context): LocalTime {
        val minutes = prefs(context).getInt(KEY_SEND_MINUTES, DEFAULT_SEND_MINUTES).coerceIn(0, 24 * 60 - 1)
        return LocalTime.of(minutes / 60, minutes % 60)
    }

    fun setSendTime(context: Context, time: LocalTime) {
        prefs(context).edit().putInt(KEY_SEND_MINUTES, time.hour * 60 + time.minute).apply()
    }

    internal fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
