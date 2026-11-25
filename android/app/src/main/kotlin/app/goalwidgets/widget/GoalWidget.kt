package app.goalwidgets.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.goalwidgets.GoalWidgetsApplication
import app.goalwidgets.MainActivity
import app.goalwidgets.R
import app.goalwidgets.core.GoalStyle
import app.goalwidgets.core.WidgetContent
import app.goalwidgets.ui.GoalColors
import app.goalwidgets.ui.GoalLabels
import app.goalwidgets.ui.titleWithEmoji
import java.time.LocalDate

/**
 * Widget d'écran d'accueil (Glance). Chaque instance stocke l'id de l'objectif choisi dans son
 * état Glance ([GoalWidgets.GOAL_ID]) ; le calcul passe par [WidgetContent.resolve] (pur, testé).
 */
class GoalWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = (context.applicationContext as GoalWidgetsApplication).database.goalDao()
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            // Flux Room : une modification dans l'app recompose une session encore ouverte.
            val goals by dao.observeAll().collectAsState(initial = null)
            val goalId = currentState(GoalWidgets.GOAL_ID)
            GlanceTheme {
                val rows = goals
                if (rows == null) {
                    Background(GlanceModifier) {}
                } else {
                    val content = WidgetContent.resolve(goalId, LocalDate.now()) { wanted ->
                        rows.firstOrNull { it.id == wanted }?.toGoal()
                    }
                    val style = rows.firstOrNull { it.id == goalId }?.style() ?: GoalStyle.NONE
                    WidgetBody(content, style, appWidgetId)
                }
            }
        }
    }

    companion object {
        /** Petite taille : chiffre + titre. */
        val SMALL = DpSize(110.dp, 40.dp)

        /** Moyenne taille : chiffre + titre + progression. */
        val MEDIUM = DpSize(220.dp, 90.dp)
    }
}

@Composable
private fun WidgetBody(content: WidgetContent, style: GoalStyle, appWidgetId: Int) {
    val context = LocalContext.current
    when (content) {
        is WidgetContent.Showing -> Background(GlanceModifier.clickable(openGoal(context, content.goalId))) {
            ShowingGoal(content, style)
        }
        is WidgetContent.GoalDeleted -> Background(GlanceModifier.clickable(chooseGoal(context, appWidgetId))) {
            Message(context.getString(R.string.widget_goal_deleted))
        }
        WidgetContent.NotConfigured -> Background(GlanceModifier.clickable(chooseGoal(context, appWidgetId))) {
            Message(context.getString(R.string.widget_not_configured))
        }
    }
}

@Composable
private fun Background(modifier: GlanceModifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp),
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}

@Composable
private fun ShowingGoal(content: WidgetContent.Showing, style: GoalStyle) {
    val resources = LocalContext.current.resources
    // Couleur de l'objectif (teinte claire/sombre selon le thème), sinon couleur du thème.
    val accent: ColorProvider = style.color
        ?.let { ColorProvider(day = GoalColors.light(it), night = GoalColors.dark(it)) }
        ?: GlanceTheme.colors.primary
    val medium = LocalSize.current.width >= GoalWidget.MEDIUM.width &&
        LocalSize.current.height >= GoalWidget.MEDIUM.height
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            text = GoalLabels.status(resources, content.state),
            style = TextStyle(
                color = accent,
                fontSize = if (medium) 24.sp else 18.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        Text(
            text = titleWithEmoji(content.title, style.emoji),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
            maxLines = if (medium) 2 else 1,
        )
        val percent = content.state.percent
        if (medium && percent != null) {
            Spacer(GlanceModifier.height(8.dp))
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = percent / 100f,
                    modifier = GlanceModifier.defaultWeight().height(6.dp),
                    color = accent,
                    backgroundColor = GlanceTheme.colors.surfaceVariant,
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text = GoalLabels.percent(resources, percent),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
        maxLines = 3,
    )
}

/**
 * Ouvre l'app sur l'objectif (une URI distincte par objectif évite de fusionner les PendingIntent).
 * `CLEAR_TOP | SINGLE_TOP` : si l'app est déjà ouverte, `MainActivity` est réutilisée
 * (`onNewIntent`) → un formulaire en cours n'est pas perdu.
 */
private fun openGoal(context: Context, goalId: Long): Action = actionStartActivity(
    Intent(context, MainActivity::class.java)
        .setData(Uri.parse("goalwidgets://goal/$goalId"))
        .putExtra(MainActivity.EXTRA_GOAL_ID, goalId)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
)

/** Ouvre l'écran de choix de l'objectif pour ce widget. */
private fun chooseGoal(context: Context, appWidgetId: Int): Action = actionStartActivity(
    Intent(context, GoalWidgetConfigureActivity::class.java)
        .setData(Uri.parse("goalwidgets://widget/$appWidgetId"))
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
)
