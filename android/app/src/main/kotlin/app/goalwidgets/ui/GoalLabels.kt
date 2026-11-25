package app.goalwidgets.ui

import android.content.res.Resources
import app.goalwidgets.R
import app.goalwidgets.core.DisplayState
import app.goalwidgets.core.Phase

/** Libellés construits par la couche app à partir de l'état structuré du moteur. */
object GoalLabels {
    fun status(resources: Resources, state: DisplayState): String = when (state.phase) {
        Phase.UPCOMING -> resources.getQuantityString(R.plurals.countdown_in_days, state.days, state.days)
        Phase.TODAY -> resources.getString(R.string.countdown_today)
        Phase.REACHED ->
            resources.getQuantityString(R.plurals.countdown_reached_days_ago, state.days, state.days)
    }

    fun percent(resources: Resources, percent: Int): String =
        resources.getString(R.string.goal_progress_percent, percent)
}
