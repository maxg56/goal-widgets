package app.goalwidgets.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.goalwidgets.GoalWidgetsApplication
import app.goalwidgets.R
import app.goalwidgets.core.WidgetResetTap
import java.time.LocalDate

/**
 * Compteur « série » affiché par un widget « Objectif » : bouton ↺ (armé ou non) et record à
 * afficher (`null` tant qu'il n'y a eu aucune remise à zéro : le record serait la série en cours).
 */
class WidgetStreak(val goalId: Long, val armed: Boolean, val recordDays: Int?)

/** Paramètre de [ResetStreakCallback] : objectif à remettre à zéro. */
internal val RESET_GOAL_ID = ActionParameters.Key<Long>("reset_goal_id")

/**
 * Instant (ms) du premier appui sur ↺, dans l'état Glance de l'instance ; absent = bouton au repos.
 * Effacé par la composition du widget passé [WidgetResetTap.WINDOW_MILLIS] (cf. [GoalWidget]).
 */
internal val RESET_ARMED_AT = longPreferencesKey("reset_armed_at")

/**
 * Appui sur ↺ : le premier arme le bouton (« Confirmer ? »), le second dans la fenêtre remet le
 * compteur à zéro aujourd'hui ([WidgetResetTap]). Aucune activité ouverte.
 */
class ResetStreakCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val goalId = parameters[RESET_GOAL_ID] ?: return
        val now = System.currentTimeMillis()
        val armedAt = getAppWidgetState<Preferences>(context, PreferencesGlanceStateDefinition, glanceId)[RESET_ARMED_AT]
        when (WidgetResetTap.onTap(armedAt, now)) {
            WidgetResetTap.Outcome.ARM -> {
                updateAppWidgetState(context, glanceId) { it[RESET_ARMED_AT] = now }
                GoalWidget().update(context, glanceId)
            }
            WidgetResetTap.Outcome.RESET -> {
                updateAppWidgetState(context, glanceId) { it.remove(RESET_ARMED_AT) }
                val app = context.applicationContext as GoalWidgetsApplication
                val today = LocalDate.now()
                if (app.database.goalDao().resetStreak(goalId, today, today)) {
                    app.onGoalsChanged() // tous les widgets et les notifications de jalons
                } else {
                    GoalWidget().update(context, glanceId)
                }
            }
        }
    }
}

/** Bouton ↺ / « Confirmer ? », posé dans un coin du widget, aux couleurs de son habillage. */
@Composable
internal fun ResetButton(streak: WidgetStreak, palette: WidgetPalette) {
    val context = LocalContext.current
    val click = actionRunCallback<ResetStreakCallback>(actionParametersOf(RESET_GOAL_ID to streak.goalId))
    if (streak.armed) {
        Box(
            GlanceModifier.height(28.dp).cornerRadius(14.dp).background(palette.armedBg).padding(horizontal = 10.dp).clickable(click),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                context.getString(R.string.widget_reset_confirm),
                style = TextStyle(color = palette.armedFg, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
    } else {
        Box(
            GlanceModifier.size(28.dp).cornerRadius(14.dp).background(palette.chip).clickable(click),
            contentAlignment = Alignment.Center,
        ) {
            Text("↺", style = TextStyle(color = palette.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
    }
}
