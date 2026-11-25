package app.goalwidgets.ui

import android.content.res.Resources
import app.goalwidgets.R
import app.goalwidgets.core.Breakdown
import app.goalwidgets.core.DisplayState
import app.goalwidgets.core.Phase

/** Libellés construits par la couche app à partir de l'état structuré du moteur. */
object GoalLabels {
    fun status(resources: Resources, state: DisplayState): String = when (state.phase) {
        Phase.UPCOMING -> resources.getQuantityString(R.plurals.countdown_in_days, state.days, state.days)
        Phase.TODAY -> resources.getString(R.string.countdown_today)
        Phase.REACHED ->
            resources.getQuantityString(R.plurals.countdown_reached_days_ago, state.days, state.days)
        Phase.ELAPSED ->
            if (state.days == 0) {
                resources.getString(R.string.since_today)
            } else {
                resources.getQuantityString(R.plurals.since_days, state.days, state.days)
            }
    }

    /**
     * Forme lisible « 1 an 2 mois 3 jours » (parties nulles omises), ou `null` quand elle
     * n'apporterait rien de plus que le nombre de jours (moins d'un mois).
     */
    fun breakdown(resources: Resources, breakdown: Breakdown?): String? {
        if (breakdown == null || (breakdown.years == 0 && breakdown.months == 0)) return null
        return listOfNotNull(
            breakdown.years.takeIf { it > 0 }
                ?.let { resources.getQuantityString(R.plurals.breakdown_years, it, it) },
            breakdown.months.takeIf { it > 0 }
                ?.let { resources.getQuantityString(R.plurals.breakdown_months, it, it) },
            breakdown.days.takeIf { it > 0 }
                ?.let { resources.getQuantityString(R.plurals.breakdown_days, it, it) },
        ).joinToString(" ")
    }

    fun percent(resources: Resources, percent: Int): String =
        resources.getString(R.string.goal_progress_percent, percent)
}
