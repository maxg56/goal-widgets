package app.goalwidgets.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import app.goalwidgets.GoalWidgetsApplication
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Alarme d'un jalon → notification. */
class MilestoneAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync { MilestoneNotifications.fire(context, intent) }
}

/**
 * Redémarrage (les alarmes sont perdues), mise à jour de l'app, changement d'heure ou de fuseau
 * (l'heure d'envoi est une heure locale) → reprogrammation complète.
 */
class NotificationRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync {
        MilestoneNotifications.reschedule(context)
        DailyRescheduleWorker.enqueue(context)
    }
}

private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** Tâche quotidienne : fait glisser l'horizon même si l'app n'est jamais ouverte (spec 41). */
class DailyRescheduleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as GoalWidgetsApplication).rollYearly()
        MilestoneNotifications.reschedule(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "reschedule-milestone-notifications"

        /** Idempotent : une seule tâche périodique (nom unique, conservée si elle existe). */
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DailyRescheduleWorker>(1, TimeUnit.DAYS).build(),
            )
        }
    }
}
