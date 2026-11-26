package app.goalwidgets.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Récepteur commun : alarme de minuit programmée tant qu'il reste un widget, quel que soit son type. */
abstract class MidnightAwareReceiver : GlanceAppWidgetReceiver() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        MidnightRefresh.schedule(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        MidnightRefresh.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Dernier widget de CE type retiré : l'autre type peut encore avoir besoin de l'alarme.
        if (!GoalWidgets.hasAny(context)) MidnightRefresh.cancel(context)
    }
}

class GoalWidgetReceiver : MidnightAwareReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GoalWidget()
}

/** Widget « Grille » (ticket 12). */
class DotGridWidgetReceiver : MidnightAwareReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DotGridWidget()
}

/** Widget « Liste » : tous les objectifs. */
class GoalListWidgetReceiver : MidnightAwareReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GoalListWidget()
}
