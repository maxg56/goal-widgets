package app.goalwidgets.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.goalwidgets.GoalWidgetsApplication
import app.goalwidgets.MainActivity
import app.goalwidgets.R
import app.goalwidgets.core.CountdownEngine
import app.goalwidgets.core.GoalOrder
import app.goalwidgets.core.Phase
import app.goalwidgets.core.SortKey
import app.goalwidgets.data.GoalEntity
import app.goalwidgets.ui.GoalColors
import app.goalwidgets.ui.GoalLabels
import java.time.Instant
import java.time.LocalDate

/**
 * Widget « Liste » : tous les objectifs dans un seul widget, défilables, dans l'ordre choisi dans
 * l'app (menu ⋮ → tri). Aucun réglage : il suit la liste. Surface du thème système (clair/sombre,
 * Material You), accents de la couleur de chaque objectif. Appui sur une ligne → détail de l'objectif.
 */
class GoalListWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as GoalWidgetsApplication
        val dao = app.database.goalDao()
        provideContent {
            val goals by dao.observeAll().collectAsState(initial = null)
            GlanceTheme {
                ListBody(goals, GoalListOrder.read(context))
            }
        }
    }
}

/** Tri de la liste choisi dans l'app (menu ⋮), partagé par l'accueil et le widget « Liste ». */
object GoalListOrder {
    private const val PREFS = "settings"
    private const val PREF_ORDER = "goal_order"

    fun read(context: Context): GoalOrder =
        GoalOrder.fromWire(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PREF_ORDER, null))

    fun write(context: Context, order: GoalOrder) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(PREF_ORDER, order.wire).apply()
    }
}

@Composable
private fun ListBody(goals: List<GoalEntity>?, order: GoalOrder) {
    val context = LocalContext.current
    val openApp = actionStartActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    Column(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .systemCorners()
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(GlanceModifier.fillMaxWidth().clickable(openApp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                context.getString(R.string.widget_list_header),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            if (!goals.isNullOrEmpty()) {
                Box(
                    GlanceModifier.height(22.dp).cornerRadius(11.dp).background(GlanceTheme.colors.secondaryContainer).padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        goals.size.toString(),
                        style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(GlanceModifier.height(6.dp))
        when {
            goals == null -> Unit
            goals.isEmpty() -> Box(GlanceModifier.fillMaxSize().clickable(openApp), contentAlignment = Alignment.Center) {
                Text(
                    context.getString(R.string.widget_list_empty),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                    maxLines = 3,
                )
            }
            else -> {
                val today = LocalDate.now()
                val sorted = order.sort(goals, today) { SortKey(it.uid, it.toGoal(), Instant.ofEpochMilli(it.createdAtMillis)) }
                LazyColumn(GlanceModifier.fillMaxSize()) {
                    items(sorted, itemId = { it.id }) { row -> GoalRow(row, today) }
                }
            }
        }
    }
}

@Composable
private fun GoalRow(row: GoalEntity, today: LocalDate) {
    val context = LocalContext.current
    val resources = context.resources
    val goal = row.toGoal()
    val style = row.style()
    val state = CountdownEngine.compute(goal, today)
    val accent: ColorProvider = style.color
        ?.let { ColorProvider(day = GoalColors.light(it), night = GoalColors.dark(it)) }
        ?: GlanceTheme.colors.primary
    val badge: ColorProvider = style.color
        ?.let { ColorProvider(day = GoalColors.light(it).copy(alpha = 0.14f), night = GoalColors.dark(it).copy(alpha = 0.2f)) }
        ?: GlanceTheme.colors.secondaryContainer
    Row(
        GlanceModifier.fillMaxWidth().padding(vertical = 5.dp).clickable(openGoal(context, row.id)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(GlanceModifier.size(36.dp).cornerRadius(18.dp).background(badge), contentAlignment = Alignment.Center) {
            val emoji = style.emoji?.takeIf { it.isNotBlank() }
            Text(
                emoji ?: goal.title.take(1).uppercase(),
                style = TextStyle(color = accent, fontSize = if (emoji != null) 17.sp else 15.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(goal.title, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(GoalLabels.status(resources, state), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1)
            val progress = state.progress
            if (progress != null && state.phase == Phase.UPCOMING) {
                Spacer(GlanceModifier.height(4.dp))
                LinearProgressIndicator(
                    progress = progress.toFloat(),
                    modifier = GlanceModifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp),
                    color = accent,
                    backgroundColor = ColorProvider(day = Color.Black.copy(alpha = 0.08f), night = Color.White.copy(alpha = 0.12f)),
                )
            }
        }
        Spacer(GlanceModifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                GoalLabels.bigNumber(resources, state),
                style = TextStyle(color = accent, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = WidgetPalette.NUMBER_FONT),
                maxLines = 1,
            )
            Text(GoalLabels.shortCaption(resources, state), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp), maxLines = 1)
        }
    }
}
