package app.goalwidgets.widget

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll

/** Point d'entrée de l'app vers les widgets : association widget → objectif et rafraîchissement. */
object GoalWidgets {
    /** Id de l'objectif affiché, dans l'état Glance de chaque instance de widget. */
    val GOAL_ID = longPreferencesKey("goal_id")

    /** Lie le widget [appWidgetId] à l'objectif [goalId] (création ou changement) et le redessine. */
    suspend fun assign(context: Context, appWidgetId: Int, goalId: Long) {
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        updateAppWidgetState(context, glanceId) { it[GOAL_ID] = goalId }
        GoalWidget().update(context, glanceId)
    }

    /** Redessine tous les widgets : après toute modification d'objectif et à minuit. */
    suspend fun refreshAll(context: Context) {
        GoalWidget().updateAll(context)
    }

    suspend fun hasAny(context: Context): Boolean =
        GlanceAppWidgetManager(context).getGlanceIds(GoalWidget::class.java).isNotEmpty()
}
