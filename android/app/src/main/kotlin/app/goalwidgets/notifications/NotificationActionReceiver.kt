package app.goalwidgets.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import app.goalwidgets.GoalWidgetsApplication
import app.goalwidgets.R
import app.goalwidgets.core.Milestone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Bouton « Remettre à zéro » d'une notification de série, en deux appuis comme sur le widget :
 * le premier remplace le bouton par « Confirmer la remise à zéro », le second remet le compteur à
 * zéro aujourd'hui et affiche « Nouvelle série ! ».
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val milestone = MilestoneNotifications.milestoneOf(intent) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val app = context.applicationContext as GoalWidgetsApplication
                val dao = app.database.goalDao()
                val row = dao.getById(milestone.goalId.toLong()) ?: return@launch MilestoneNotifications.dismiss(app, milestone)
                when (intent.action) {
                    ARM_RESET -> MilestoneNotifications.show(app, row, milestone, armedReset = true)
                    CONFIRM_RESET -> {
                        val today = LocalDate.now()
                        if (dao.resetStreak(row.id, today, today)) {
                            app.onGoalsChanged()
                            confirmReset(app, row.title, milestone)
                        } else {
                            MilestoneNotifications.dismiss(app, milestone)
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    /** Remplace la notification par « Nouvelle série ! » (fermée d'un glissement ou d'un appui). */
    private fun confirmReset(context: Context, title: String, milestone: Milestone) {
        val builder = NotificationCompat.Builder(context, MilestoneNotifications.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notif_reset_done))
            .setSilent(true)
        runCatching {
            if (MilestoneNotifications.canPost(context)) {
                androidx.core.app.NotificationManagerCompat.from(context).notify(
                    milestone.id,
                    0,
                    builder.setSmallIcon(R.drawable.ic_notification)
                        .setContentIntent(MilestoneNotifications.openGoalIntent(context, milestone.goalId.toLong(), milestone.id))
                        .setAutoCancel(true)
                        .build(),
                )
            }
        }
    }

    companion object {
        const val ARM_RESET = "app.goalwidgets.action.NOTIF_ARM_RESET"
        const val CONFIRM_RESET = "app.goalwidgets.action.NOTIF_CONFIRM_RESET"

        fun intent(context: Context, action: String, milestone: Milestone): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            MilestoneNotifications.putMilestone(
                Intent(context, NotificationActionReceiver::class.java)
                    .setAction(action)
                    .setData(Uri.parse("goalwidgets://notif-action/" + Uri.encode(milestone.id))),
                milestone,
            ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
