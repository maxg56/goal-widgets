package app.goalwidgets.notifications

import android.content.res.Resources
import app.goalwidgets.R
import app.goalwidgets.core.Breakdown
import app.goalwidgets.core.Milestone
import app.goalwidgets.core.MilestoneKind
import app.goalwidgets.ui.GoalLabels

/**
 * Texte d'une notification à partir du jalon (`:core` ne produit pas de texte).
 * Le titre de la notification est celui de l'objectif. Mêmes clés que `NotificationLabels` iOS.
 */
object NotificationTexts {
    fun body(resources: Resources, milestone: Milestone): String = when (milestone.kind) {
        MilestoneKind.MONTH_ANNIVERSARY ->
            resources.getString(R.string.notif_since_elapsed, duration(resources, months = milestone.value))
        MilestoneKind.YEAR_ANNIVERSARY ->
            resources.getString(R.string.notif_since_elapsed, duration(resources, months = milestone.value * 12))
        MilestoneKind.MONTHLY_REMINDER ->
            resources.getString(R.string.notif_countdown_remaining, duration(resources, months = milestone.value))
        MilestoneKind.DAYS_BEFORE -> when {
            milestone.value == 1 -> resources.getString(R.string.notif_countdown_tomorrow)
            // Rappel en semaines rondes (14, 21, 28… jours) : « Plus que 2 semaines ».
            milestone.value >= 14 && milestone.value % 7 == 0 && milestone.value != 30 ->
                resources.getQuantityString(R.plurals.notif_countdown_weeks_left, milestone.value / 7, milestone.value / 7)
            else -> resources.getQuantityString(R.plurals.notif_countdown_days_left, milestone.value, milestone.value)
        }
        MilestoneKind.DAY_MILESTONE ->
            resources.getQuantityString(R.plurals.notif_since_days_milestone, milestone.value, milestone.value)
        MilestoneKind.TARGET_DAY -> resources.getString(R.string.notif_countdown_today)
    }

    /** « 3 mois », « 1 an », « 1 an 1 mois » (mêmes pluriels que la forme lisible de la liste). */
    private fun duration(resources: Resources, months: Int): String =
        GoalLabels.breakdown(resources, Breakdown(years = months / 12, months = months % 12, days = 0)) ?: ""
}
