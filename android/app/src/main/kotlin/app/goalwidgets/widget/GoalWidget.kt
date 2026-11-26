package app.goalwidgets.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
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
import app.goalwidgets.R
import app.goalwidgets.core.ChartKind
import app.goalwidgets.core.ChartUnit
import app.goalwidgets.core.CountdownEngine
import app.goalwidgets.core.Goal
import app.goalwidgets.core.GoalKind
import app.goalwidgets.core.GoalStyle
import app.goalwidgets.core.Streaks
import app.goalwidgets.core.WidgetChartConfig
import app.goalwidgets.core.WidgetContent
import app.goalwidgets.core.WidgetPart
import app.goalwidgets.core.WidgetResetTap
import app.goalwidgets.core.WidgetSize
import app.goalwidgets.ui.DateFormats
import app.goalwidgets.ui.GoalLabels
import app.goalwidgets.ui.titleWithEmoji
import kotlinx.coroutines.delay
import java.time.LocalDate

/**
 * Widget principal « Objectif » : chiffre, légende et graphique au choix (anneau, arcs, barres, points)
 * sur un dégradé de la couleur de l'objectif. Chaque instance garde dans son état Glance l'objectif
 * ([GoalWidgets.GOAL_ID]), le graphique ([GoalWidgets.CHART]) et les éléments retirés
 * ([GoalWidgets.HIDDEN]). Un widget posé avant l'arrivée des graphiques n'a ni l'un ni l'autre :
 * [WidgetChartConfig.decode] donne l'anneau avec tout affiché, sans migration à écrire.
 * Gabarit selon la taille réelle ([WidgetSize]) : compact (une rangée, dont les anciens 2×1), petit,
 * moyen, grand.
 */
class GoalWidget : GlanceAppWidget() {

    // Taille exacte : gabarit et bitmaps suivent la taille réelle du widget.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = (context.applicationContext as GoalWidgetsApplication).database.goalDao()
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            // Flux Room : une modification dans l'app recompose une session encore ouverte.
            val goals by dao.observeAll().collectAsState(initial = null)
            val goalId = currentState(GoalWidgets.GOAL_ID)
            val config = WidgetChartConfig.decode(currentState(GoalWidgets.CHART), currentState(GoalWidgets.HIDDEN))
            // Bouton ↺ armé (premier appui) : retour au repos passé le délai de confirmation.
            val armedAt = currentState(RESET_ARMED_AT)
            LaunchedEffect(armedAt) {
                if (armedAt == null) return@LaunchedEffect
                delay((armedAt + WidgetResetTap.WINDOW_MILLIS - System.currentTimeMillis()).coerceAtLeast(0))
                updateAppWidgetState(context, id) { if (it[RESET_ARMED_AT] == armedAt) it.remove(RESET_ARMED_AT) }
                update(context, id)
            }
            GlanceTheme {
                val rows = goals
                if (rows == null) {
                    Background(GlanceModifier) {}
                } else {
                    val today = LocalDate.now()
                    val content = WidgetContent.resolve(goalId, today) { wanted -> rows.firstOrNull { it.id == wanted }?.toGoal() }
                    val row = rows.firstOrNull { it.id == goalId }
                    when {
                        content is WidgetContent.Showing && row != null -> {
                            val streak = if (row.isStreak) {
                                val summary = Streaks.summary(row.toGoal(), row.history(), today)
                                WidgetStreak(
                                    goalId = row.id,
                                    armed = WidgetResetTap.isArmed(armedAt, System.currentTimeMillis()),
                                    recordDays = summary.bestDays.takeIf { summary.resetCount > 0 },
                                )
                            } else {
                                null
                            }
                            ChartWidgetContent(row.toGoal(), row.style(), today, config, openGoal(context, content.goalId), streak)
                        }
                        else -> Background(GlanceModifier.clickable(chooseGoal(context, appWidgetId))) {
                            Message(
                                context.getString(
                                    if (content is WidgetContent.GoalDeleted) R.string.widget_goal_deleted else R.string.widget_not_configured,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val white = ColorProvider(Color.White)
private fun whiteAlpha(alpha: Float) = ColorProvider(Color.White.copy(alpha = alpha))

/** Taille (sp) du grand nombre pour qu'il tienne dans [widthDp] (≈ 0,6 em par chiffre en gras). */
private fun fitSp(text: String, widthDp: Float, maxSp: Float): Float =
    minOf(maxSp, widthDp / (0.62f * text.length.coerceAtLeast(1))).coerceAtLeast(10f)

/**
 * Contenu du widget à graphique pour la taille courante ([LocalSize]). Aussi rendu tel quel dans
 * l'aperçu de la configuration ([click] = null).
 */
@Composable
fun ChartWidgetContent(
    goal: Goal,
    style: GoalStyle,
    today: LocalDate,
    config: WidgetChartConfig,
    click: Action?,
    /** Compteur « série » : bouton ↺ et record (`null` sinon, et dans l'aperçu de la configuration). */
    streak: WidgetStreak? = null,
) {
    val context = LocalContext.current
    val size = LocalSize.current
    val w = size.width.value
    val h = size.height.value
    val state = CountdownEngine.compute(goal, today)
    val resources = context.resources
    val content = WidgetTexts(
        title = titleWithEmoji(goal.title, style.emoji),
        number = GoalLabels.bigNumber(resources, state),
        caption = GoalLabels.caption(resources, state),
        shortCaption = GoalLabels.shortCaption(resources, state),
        status = GoalLabels.status(resources, state),
        record = streak?.recordDays?.let { resources.getString(R.string.widget_record, it) },
    )
    var modifier = GlanceModifier
        .fillMaxSize()
        .appWidgetBackground()
        .systemCorners()
        .background(ImageProvider(WidgetBitmaps.background(style, w, h)), ContentScale.FillBounds)
    if (click != null) modifier = modifier.clickable(click)
    // Unité des graphiques : celle de l'objectif, sinon le réglage général (suivi en direct).
    val globalUnit by (context.applicationContext as GoalWidgetsApplication).chartSettings.global.collectAsState()
    val unit = ChartUnit.resolve(style.chartUnit, globalUnit)
    val draw = ChartDrawer(goal, today, unit, context.resources.displayMetrics.density)
    Box(modifier) {
        when (WidgetSize.of(w, h)) {
            WidgetSize.COMPACT -> Compact(content, config, draw, w, h, hasButton = streak != null)
            WidgetSize.SMALL -> Small(content, config, draw, w, h)
            WidgetSize.MEDIUM -> Medium(content, config, draw, w, h)
            WidgetSize.LARGE -> Large(content, config, draw, goal, today, w, h)
        }
        if (streak != null) {
            val compact = WidgetSize.of(w, h) == WidgetSize.COMPACT
            Box(
                GlanceModifier.fillMaxSize().padding(8.dp),
                contentAlignment = if (compact) Alignment.CenterEnd else Alignment.TopEnd,
            ) { ResetButton(streak) }
        }
    }
}

private class WidgetTexts(
    val title: String,
    val number: String,
    val caption: String,
    val shortCaption: String,
    val status: String,
    /** « Record 47 j » d'un compteur « série » déjà remis à zéro, sinon `null`. */
    val record: String? = null,
) {
    /** Légende longue suivie du record éventuel. */
    val captionWithRecord: String get() = listOfNotNull(caption, record).joinToString(" · ")
}

/** Bitmaps des graphiques pour un objectif et une densité donnés. */
private class ChartDrawer(private val goal: Goal, private val today: LocalDate, private val unit: ChartUnit, private val density: Float) {
    fun chart(kind: ChartKind, widthDp: Float, heightDp: Float): Bitmap =
        WidgetBitmaps.chart(kind, goal, today, widthDp.coerceAtLeast(1f), heightDp.coerceAtLeast(1f), density, unit)

    fun strip(widthDp: Float, heightDp: Float): Bitmap = WidgetBitmaps.strip(goal, today, widthDp.coerceAtLeast(1f), heightDp, density)
}

@Composable
private fun ChartImage(bitmap: Bitmap, description: String, modifier: GlanceModifier) {
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = description,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(white),
    )
}

@Composable
private fun Title(text: String, sizeSp: Float) {
    Text(text, style = TextStyle(color = whiteAlpha(0.92f), fontSize = sizeSp.sp, fontWeight = FontWeight.Bold), maxLines = 1)
}

/** Une rangée (2×1, 4×1, dont les widgets posés avant les graphiques) : texte à gauche, petit anneau à droite. */
@Composable
private fun Compact(t: WidgetTexts, config: WidgetChartConfig, draw: ChartDrawer, w: Float, h: Float, hasButton: Boolean) {
    val pad = 10f
    val side = h - 2 * pad
    Row(GlanceModifier.fillMaxSize().padding(horizontal = 14.dp, vertical = pad.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            if (config.shows(WidgetPart.TITLE)) Title(t.title, 13f)
            Row(verticalAlignment = Alignment.Bottom) {
                if (config.shows(WidgetPart.NUMBER)) {
                    val sp = fitSp(t.number, w * 0.4f, (side * 0.62f).coerceIn(16f, 34f))
                    Text(t.number, style = TextStyle(color = white, fontSize = sp.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Spacer(GlanceModifier.width(6.dp))
                }
                if (config.shows(WidgetPart.CAPTION)) {
                    Text(t.shortCaption, style = TextStyle(color = whiteAlpha(0.82f), fontSize = 12.sp), maxLines = 1)
                }
            }
        }
        if (hasButton) {
            // Place du bouton ↺ (posé par-dessus, centré à droite) à la place de l'anneau.
            Spacer(GlanceModifier.width(44.dp))
        } else if (config.shows(WidgetPart.CHART) && w >= 160f && side >= 24f) {
            Spacer(GlanceModifier.width(8.dp))
            ChartImage(draw.chart(ChartKind.RING, side, side), t.status, GlanceModifier.size(side.dp))
        }
    }
}

/** Petit (2×2) : titre, puis toujours l'anneau avec le nombre au centre. */
@Composable
private fun Small(t: WidgetTexts, config: WidgetChartConfig, draw: ChartDrawer, w: Float, h: Float) {
    val pad = 12f
    val showTitle = config.shows(WidgetPart.TITLE)
    val side = minOf(w - 2 * pad, h - 2 * pad - if (showTitle) 18f else 0f).coerceAtLeast(1f)
    val showChart = config.shows(WidgetPart.CHART)
    val inner = if (showChart) side * 0.74f else side
    Column(GlanceModifier.fillMaxSize().padding(pad.dp)) {
        if (showTitle) Title(t.title, 12f)
        Box(GlanceModifier.defaultWeight().fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (showChart) ChartImage(draw.chart(ChartKind.RING, side, side), t.status, GlanceModifier.size(side.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (config.shows(WidgetPart.NUMBER)) {
                    Text(t.number, style = TextStyle(color = white, fontSize = fitSp(t.number, inner * 0.8f, inner * 0.42f).sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
                if (config.shows(WidgetPart.CAPTION)) {
                    Text(t.shortCaption, style = TextStyle(color = whiteAlpha(0.8f), fontSize = 10.sp), maxLines = 1)
                }
                t.record?.let { Text(it, style = TextStyle(color = whiteAlpha(0.8f), fontSize = 9.sp, fontWeight = FontWeight.Bold), maxLines = 1) }
            }
        }
    }
}

/** Moyen (4×2) : texte à gauche, graphique choisi à droite, mini frise en bas. */
@Composable
private fun Medium(t: WidgetTexts, config: WidgetChartConfig, draw: ChartDrawer, w: Float, h: Float) {
    val pad = 16f
    val showLine = config.shows(WidgetPart.TIMELINE)
    val lineH = 14f
    val contentH = h - 2 * pad - if (showLine) lineH + 8f else 0f
    val showChart = config.shows(WidgetPart.CHART)
    val side = minOf(contentH, (w - 2 * pad) * 0.46f).coerceAtLeast(1f)
    val textW = w - 2 * pad - if (showChart) side + 12f else 0f
    Column(GlanceModifier.fillMaxSize().padding(pad.dp)) {
        Row(GlanceModifier.defaultWeight().fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                if (config.shows(WidgetPart.TITLE)) Title(t.title, 14f)
                if (config.shows(WidgetPart.NUMBER)) {
                    Text(t.number, style = TextStyle(color = white, fontSize = fitSp(t.number, textW, (contentH * 0.5f).coerceIn(20f, 52f)).sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
                if (config.shows(WidgetPart.CAPTION)) {
                    Text(t.captionWithRecord, style = TextStyle(color = whiteAlpha(0.82f), fontSize = 13.sp), maxLines = 1)
                }
            }
            if (showChart) {
                Spacer(GlanceModifier.width(12.dp))
                ChartImage(draw.chart(config.chart, side, side), t.status, GlanceModifier.size(side.dp))
            }
        }
        if (showLine) {
            Spacer(GlanceModifier.height(8.dp))
            ChartImage(draw.strip(w - 2 * pad, lineH), t.status, GlanceModifier.fillMaxWidth().height(lineH.dp))
        }
    }
}

/** Grand (4×4) : titre, nombre et légende, grand graphique, mini frise avec ses dates. */
@Composable
private fun Large(t: WidgetTexts, config: WidgetChartConfig, draw: ChartDrawer, goal: Goal, today: LocalDate, w: Float, h: Float) {
    val resources = LocalContext.current.resources
    val pad = 18f
    val showTitle = config.shows(WidgetPart.TITLE)
    val showNumber = config.shows(WidgetPart.NUMBER)
    val showCaption = config.shows(WidgetPart.CAPTION)
    val showLine = config.shows(WidgetPart.TIMELINE)
    val lineH = 16f
    val chartW = w - 2 * pad
    val chartH = h - 2 * pad - (if (showTitle) 24f else 0f) - (if (showNumber) 64f else if (showCaption) 20f else 0f) - 10f -
        (if (showLine) lineH + 28f else 0f)
    Column(GlanceModifier.fillMaxSize().padding(pad.dp)) {
        if (showTitle) Title(t.title, 16f)
        if (showNumber || showCaption) {
            Row(verticalAlignment = Alignment.Bottom) {
                if (showNumber) {
                    Text(t.number, style = TextStyle(color = white, fontSize = fitSp(t.number, chartW * 0.55f, 56f).sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Spacer(GlanceModifier.width(8.dp))
                }
                if (showCaption) Text(t.captionWithRecord, style = TextStyle(color = whiteAlpha(0.82f), fontSize = 13.sp), maxLines = 1)
            }
        }
        Spacer(GlanceModifier.height(10.dp))
        Box(GlanceModifier.defaultWeight().fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (config.shows(WidgetPart.CHART) && chartH > 30f) {
                ChartImage(draw.chart(config.chart, chartW, chartH), t.status, GlanceModifier.fillMaxSize())
            }
        }
        if (showLine) {
            Spacer(GlanceModifier.height(8.dp))
            ChartImage(draw.strip(chartW, lineH), t.status, GlanceModifier.fillMaxWidth().height(lineH.dp))
            Row(GlanceModifier.fillMaxWidth().padding(top = 4.dp)) {
                val label = TextStyle(color = whiteAlpha(0.75f), fontSize = 11.sp)
                Text(goal.startDate.format(DateFormats.dayMonth), style = label, maxLines = 1)
                Spacer(GlanceModifier.defaultWeight())
                val target = goal.targetDate.takeIf { goal.kind == GoalKind.COUNTDOWN }
                Text(
                    target?.let { resources.getString(R.string.strip_target, it.format(DateFormats.dayMonth)) }
                        ?: resources.getString(R.string.strip_today),
                    style = label,
                    maxLines = 1,
                )
            }
        }
    }
}
