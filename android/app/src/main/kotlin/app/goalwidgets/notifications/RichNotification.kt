package app.goalwidgets.notifications

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import app.goalwidgets.R
import app.goalwidgets.core.CountdownEngine
import app.goalwidgets.core.GoalKind
import app.goalwidgets.core.Milestone
import app.goalwidgets.core.Phase
import app.goalwidgets.data.GoalEntity
import app.goalwidgets.ui.DateFormats
import app.goalwidgets.ui.GoalBadge
import app.goalwidgets.ui.GoalLabels
import app.goalwidgets.ui.GoalTint
import app.goalwidgets.ui.titleWithEmoji
import java.time.LocalDate

/** Contenu d'une notification de jalon enrichie (cf. [MilestoneNotifications.show]). */
internal object RichNotification {
    fun builder(context: Context, row: GoalEntity, milestone: Milestone, today: LocalDate): NotificationCompat.Builder {
        val res = context.resources
        val goal = row.toGoal()
        val style = row.style()
        val state = CountdownEngine.compute(goal, today)
        val body = NotificationTexts.body(res, milestone)
        // Détail : « 42 jours restants · 67 % · le 8 janvier 2027 » / « 258 jours depuis · 8 mois 13 jours ».
        val detail = listOfNotNull(
            "${GoalLabels.bigNumber(res, state)} ${GoalLabels.caption(res, state)}".takeIf { state.phase != Phase.TODAY },
            state.percent?.takeIf { state.phase == Phase.UPCOMING }?.let { GoalLabels.percent(res, it) },
            GoalLabels.breakdown(res, state.breakdown),
            goal.targetDate?.takeIf { goal.kind == GoalKind.COUNTDOWN }?.let { res.getString(R.string.goal_date_countdown, it.format(DateFormats.long)) },
        ).joinToString(" · ")
        val iconPx = res.getDimensionPixelSize(android.R.dimen.notification_large_icon_width).coerceAtLeast(64)
        val builder = NotificationCompat.Builder(context, MilestoneNotifications.CHANNEL_ID)
            .setContentTitle(titleWithEmoji(goal.title, style.emoji))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (detail.isEmpty()) body else "$body\n$detail"))
            .setColor(GoalTint.deepColor(style).toArgb())
            .setLargeIcon(GoalBadge.bitmap(goal.title, style, iconPx, round = true))
            .setGroup(GROUP)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        // Compte à rebours en cours : barre de progression du départ à la cible.
        if (state.phase == Phase.UPCOMING) state.percent?.let { builder.setProgress(100, it, false) }
        return builder
    }

    private const val GROUP = "milestones"
}
