package app.goalwidgets.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.goalwidgets.MainActivity

/**
 * Coins du widget : sur Android 12+, le rayon du système (`system_app_widget_background_radius`),
 * celui avec lequel le lanceur découpe déjà le widget → pas de double coin. Avant Android 12, Glance
 * ignore les coins (comme les widgets posés avant cette version).
 */
fun GlanceModifier.systemCorners(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) cornerRadius(android.R.dimen.system_app_widget_background_radius) else this

/** Fond neutre des messages (non configuré, objectif supprimé) et de la « Grille ». */
@Composable
internal fun Background(modifier: GlanceModifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .systemCorners()
            .padding(12.dp),
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}

@Composable
internal fun Message(text: String) {
    Text(
        text = text,
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
        maxLines = 3,
    )
}

/**
 * Ouvre le détail de l'objectif dans l'app (une URI distincte par objectif évite de fusionner les
 * PendingIntent). `CLEAR_TOP | SINGLE_TOP` : si l'app est déjà ouverte, `MainActivity` est réutilisée
 * (`onNewIntent`) → un formulaire en cours n'est pas perdu.
 */
internal fun openGoal(context: Context, goalId: Long): Action = actionStartActivity(
    Intent(context, MainActivity::class.java)
        .setData(Uri.parse("goalwidgets://goal/$goalId"))
        .putExtra(MainActivity.EXTRA_GOAL_ID, goalId)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
)

/** Ouvre l'écran de choix de l'objectif pour ce widget. */
internal fun chooseGoal(context: Context, appWidgetId: Int): Action = actionStartActivity(
    Intent(context, GoalWidgetConfigureActivity::class.java)
        .setData(Uri.parse("goalwidgets://widget/$appWidgetId"))
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
)
