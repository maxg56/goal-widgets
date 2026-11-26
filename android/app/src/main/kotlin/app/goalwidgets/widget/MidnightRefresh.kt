package app.goalwidgets.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.goalwidgets.GoalWidgetsApplication
import app.goalwidgets.core.LocalMidnight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/**
 * Alarme (non exacte, sans réveil) au prochain minuit local pour redessiner les widgets au
 * changement de jour. Aucune permission d'alarme exacte requise : une fenêtre d'une minute suffit.
 */
object MidnightRefresh {
    private const val ACTION = "app.goalwidgets.action.MIDNIGHT_REFRESH"
    private const val WINDOW_MILLIS = 60_000L

    fun schedule(context: Context) {
        val at = LocalMidnight.next(ZonedDateTime.now()).toInstant().toEpochMilli()
        context.getSystemService(AlarmManager::class.java)
            .setWindow(AlarmManager.RTC, at, WINDOW_MILLIS, pendingIntent(context))
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, MidnightRefreshReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/**
 * Minuit local, redémarrage, changement d'heure ou de fuseau : redessine les widgets puis
 * reprogramme l'alarme (s'il reste des widgets).
 */
class MidnightRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // Changement de jour : comptes à rebours « chaque année » passés avancés avant le redessin.
                (context.applicationContext as GoalWidgetsApplication).rollYearly()
                if (GoalWidgets.hasAny(context)) {
                    GoalWidgets.refreshAll(context)
                    MidnightRefresh.schedule(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
