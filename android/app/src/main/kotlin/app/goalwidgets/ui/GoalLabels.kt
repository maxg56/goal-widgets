package app.goalwidgets.ui

import android.content.res.Resources
import app.goalwidgets.R
import app.goalwidgets.core.Breakdown
import app.goalwidgets.core.ChartKind
import app.goalwidgets.core.MilestoneUnit
import app.goalwidgets.core.RingMilestone
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

    /** Grand nombre des cartes, du détail et du widget : jours, ou « J » le jour J. */
    fun bigNumber(resources: Resources, state: DisplayState): String =
        if (state.phase == Phase.TODAY) resources.getString(R.string.big_number_today) else state.days.toString()

    /** Légende sous le grand nombre (« jours restants », « jours depuis »…). */
    fun caption(resources: Resources, state: DisplayState): String = when (state.phase) {
        Phase.UPCOMING -> resources.getQuantityString(R.plurals.caption_days_left, state.days)
        Phase.TODAY -> resources.getString(R.string.caption_today)
        Phase.REACHED -> resources.getQuantityString(R.plurals.caption_days_after_target, state.days)
        Phase.ELAPSED -> resources.getQuantityString(R.plurals.caption_days_since, state.days)
    }

    /** Légende courte (petits widgets). */
    fun shortCaption(resources: Resources, state: DisplayState): String = when (state.phase) {
        Phase.UPCOMING, Phase.ELAPSED -> resources.getQuantityString(R.plurals.caption_short_days, state.days)
        Phase.TODAY -> resources.getString(R.string.caption_short_today)
        Phase.REACHED -> resources.getQuantityString(R.plurals.caption_short_after, state.days)
    }

    /** Écart avec aujourd'hui : « dans 3 j », « il y a 2 j », « aujourd'hui ». */
    fun relative(resources: Resources, daysFromToday: Long): String = when {
        daysFromToday > 0 -> resources.getQuantityString(R.plurals.relative_in_days, daysFromToday.toInt(), daysFromToday.toInt())
        daysFromToday < 0 -> resources.getQuantityString(R.plurals.relative_days_ago, (-daysFromToday).toInt(), (-daysFromToday).toInt())
        else -> resources.getString(R.string.relative_today)
    }

    /** Cap de l'anneau « depuis » : « 1 mois », « 7 ans ». */
    fun milestone(resources: Resources, milestone: RingMilestone): String = when (milestone.unit) {
        MilestoneUnit.MONTH -> resources.getQuantityString(R.plurals.breakdown_months, milestone.count, milestone.count)
        MilestoneUnit.YEAR -> resources.getQuantityString(R.plurals.breakdown_years, milestone.count, milestone.count)
    }

    fun chartName(resources: Resources, chart: ChartKind): String = resources.getString(
        when (chart) {
            ChartKind.DOTS -> R.string.chart_dots
            ChartKind.RING -> R.string.chart_ring
            ChartKind.ARCS -> R.string.chart_arcs
            ChartKind.BARS -> R.string.chart_bars
        },
    )
}
