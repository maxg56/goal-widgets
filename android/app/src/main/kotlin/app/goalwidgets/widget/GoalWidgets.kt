package app.goalwidgets.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import app.goalwidgets.core.DotPeriod

/**
 * Point d'entrée de l'app vers les widgets ([GoalWidget] « Objectif » et [DotGridWidget] « Grille ») :
 * association widget → objectif (+ période pour la grille) et rafraîchissement.
 */
object GoalWidgets {
    /** Id de l'objectif affiché, dans l'état Glance de chaque instance de widget. */
    val GOAL_ID = longPreferencesKey("goal_id")

    /** Période de la grille (`DotPeriod.wire`) ; absente = objectif entier. Widget « Grille » seulement. */
    val PERIOD = stringPreferencesKey("dot_period")

    /** `true` si le widget [appWidgetId] est une « Grille » (l'écran de configuration propose alors la période). */
    fun isDotGrid(context: Context, appWidgetId: Int): Boolean =
        AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)
            ?.provider?.className == DotGridWidgetReceiver::class.java.name

    /**
     * Lie le widget [appWidgetId] à l'objectif [goalId] (création ou changement) et le redessine.
     * [period] n'est utilisé que par la « Grille ».
     */
    suspend fun assign(context: Context, appWidgetId: Int, goalId: Long, period: DotPeriod? = null) {
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        updateAppWidgetState(context, glanceId) {
            it[GOAL_ID] = goalId
            if (period != null) it[PERIOD] = period.wire
        }
        if (isDotGrid(context, appWidgetId)) DotGridWidget().update(context, glanceId) else GoalWidget().update(context, glanceId)
    }

    /** Redessine tous les widgets : après toute modification d'objectif et à minuit. */
    suspend fun refreshAll(context: Context) {
        GoalWidget().updateAll(context)
        DotGridWidget().updateAll(context)
    }

    /** Il reste au moins un widget (de l'un ou l'autre type) : l'alarme de minuit est utile. */
    fun hasAny(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return listOf(GoalWidgetReceiver::class.java, DotGridWidgetReceiver::class.java).any {
            manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty()
        }
    }
}
