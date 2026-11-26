package app.goalwidgets.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.glance.appwidget.cornerRadius
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
import app.goalwidgets.core.WidgetTheme
import app.goalwidgets.core.Phase
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
 * moyen, grand. Habillage ([GoalWidgets.THEME], [WidgetTheme]) : Vif (défaut), Doux ou Nuit.
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
            val theme = WidgetTheme.fromWire(currentState(GoalWidgets.THEME))
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
                            val streak = if (row.offersStreakReset) {
                                val summary = Streaks.summary(row.toGoal(), row.history(), today)
                                WidgetStreak(
                                    goalId = row.id,
                                    armed = WidgetResetTap.isArmed(armedAt, System.currentTimeMillis()),
                                    recordDays = summary.bestDays.takeIf { summary.resetCount > 0 },
                                )
                            } else {
                                null
                            }
                            ChartWidgetContent(row.toGoal(), row.style(), today, config, theme, openGoal(context, content.goalId), streak)
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

/** Taille (sp) du grand nombre pour qu'il tienne dans [widthDp] (≈ 0,55 em par chiffre, police condensée grasse). */
private fun fitSp(text: String, widthDp: Float, maxSp: Float): Float =
    minOf(maxSp, widthDp / (0.55f * text.length.coerceAtLeast(1))).coerceAtLeast(10f)

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
    theme: WidgetTheme,
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
        title = goal.title,
        emoji = style.emoji?.takeIf { it.isNotBlank() },
        number = GoalLabels.bigNumber(resources, state),
        caption = GoalLabels.caption(resources, state),
        shortCaption = GoalLabels.shortCaption(resources, state),
        status = GoalLabels.status(resources, state),
        percent = state.percent?.takeIf { state.phase == Phase.UPCOMING }?.let { GoalLabels.percent(resources, it) },
        record = streak?.recordDays?.let { resources.getString(R.string.widget_record, it) },
    )
    val palette = WidgetPalette.of(theme, style)
    var modifier = palette.background(GlanceModifier.fillMaxSize().appWidgetBackground().systemCorners(), style, w, h)
    if (click != null) modifier = modifier.clickable(click)
    // Unité des graphiques : celle de l'objectif, sinon le réglage général (suivi en direct).
    val unit = ChartUnit.resolve(style.chartUnit, globalChartUnit(context))
    val draw = ChartDrawer(goal, today, unit, context.resources.displayMetrics.density, palette)
    val layout = WidgetSize.of(w, h)
    Box(modifier) {
        when (layout) {
            WidgetSize.COMPACT -> Compact(content, config, draw, w, h, hasButton = streak != null)
            WidgetSize.SMALL -> Small(content, config, draw, w, h)
            WidgetSize.MEDIUM -> Medium(content, config, draw, w, h)
            WidgetSize.LARGE -> Large(content, config, draw, goal, w, h)
        }
        if (streak != null) {
            Box(
                GlanceModifier.fillMaxSize().padding(8.dp),
                contentAlignment = if (layout == WidgetSize.COMPACT) Alignment.CenterEnd else Alignment.TopEnd,
            ) { ResetButton(streak, palette) }
        }
    }
}

private class WidgetTexts(
    val title: String,
    val emoji: String?,
    val number: String,
    val caption: String,
    val shortCaption: String,
    val status: String,
    /** « 42 % » d'un compte à rebours en cours, sinon `null`. */
    val percent: String?,
    /** « Record 47 j » d'un compteur « série » déjà remis à zéro, sinon `null`. */
    val record: String? = null,
) {
    val titleWithEmoji: String get() = titleWithEmoji(title, emoji)

    /** Légende longue suivie du record éventuel. */
    val captionWithRecord: String get() = listOfNotNull(caption, record).joinToString(" · ")
}

/** Réglage général de l'unité des graphiques, suivi en direct (AUTO hors de l'app, ex. captures en test). */
@Composable
private fun globalChartUnit(context: Context): ChartUnit {
    val settings = (context.applicationContext as? GoalWidgetsApplication)?.chartSettings ?: return ChartUnit.AUTO
    val unit by settings.global.collectAsState()
    return unit
}

/** Bitmaps des graphiques pour un objectif et une densité donnés, teintés selon la palette. */
private class ChartDrawer(
    private val goal: Goal,
    private val today: LocalDate,
    private val unit: ChartUnit,
    private val density: Float,
    val palette: WidgetPalette,
) {
    fun chart(kind: ChartKind, widthDp: Float, heightDp: Float): Bitmap =
        WidgetBitmaps.chart(kind, goal, today, widthDp.coerceAtLeast(1f), heightDp.coerceAtLeast(1f), density, unit)

    fun strip(widthDp: Float, heightDp: Float): Bitmap = WidgetBitmaps.strip(goal, today, widthDp.coerceAtLeast(1f), heightDp, density)
}

@Composable
private fun ChartImage(bitmap: Bitmap, description: String, tint: ColorProvider, modifier: GlanceModifier) {
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = description,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(tint),
    )
}

@Composable
private fun BigNumber(text: String, sizeSp: Float, palette: WidgetPalette) {
    Text(
        text,
        style = TextStyle(color = palette.number, fontSize = sizeSp.sp, fontWeight = FontWeight.Bold, fontFamily = WidgetPalette.NUMBER_FONT),
        maxLines = 1,
    )
}

@Composable
private fun Caption(text: String, sizeSp: Float, palette: WidgetPalette, bold: Boolean = false) {
    Text(text, style = TextStyle(color = palette.secondary, fontSize = sizeSp.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium), maxLines = 1)
}

@Composable
private fun Title(text: String, sizeSp: Float, palette: WidgetPalette) {
    Text(text, style = TextStyle(color = palette.primary, fontSize = sizeSp.sp, fontWeight = FontWeight.Bold), maxLines = 1)
}

/** Titre précédé de l'emoji dans une pastille ronde (moyen et grand formats). */
@Composable
private fun TitleRow(t: WidgetTexts, sizeSp: Float, palette: WidgetPalette, trailing: String? = null) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (t.emoji != null) {
            val badge = (sizeSp * 1.9f).dp
            Box(GlanceModifier.size(badge).cornerRadius(badge / 2).background(palette.chip), contentAlignment = Alignment.Center) {
                Text(t.emoji, style = TextStyle(fontSize = (sizeSp * 0.95f).sp), maxLines = 1)
            }
            Spacer(GlanceModifier.width(8.dp))
        }
        Box(GlanceModifier.defaultWeight()) { Title(t.title, sizeSp, palette) }
        if (trailing != null) Pill(trailing, palette)
    }
}

/** Petite pastille de texte (pourcentage). */
@Composable
private fun Pill(text: String, palette: WidgetPalette) {
    Box(GlanceModifier.height(22.dp).cornerRadius(11.dp).background(palette.chip).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(color = palette.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

/** Une rangée (2×1, 4×1, dont les widgets posés avant les graphiques) : texte à gauche, petit anneau à droite. */
@Composable
private fun Compact(t: WidgetTexts, config: WidgetChartConfig, draw: ChartDrawer, w: Float, h: Float, hasButton: Boolean) {
    val p = draw.palette
    val pad = 10f
    val side = h - 2 * pad
    Row(GlanceModifier.fillMaxSize().padding(horizontal = 14.dp, vertical = pad.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            if (config.shows(WidgetPart.TITLE)) Title(t.titleWithEmoji, 13f, p)
            Row(verticalAlignment = Alignment.Bottom) {
                if (config.shows(WidgetPart.NUMBER)) {
                    BigNumber(t.number, fitSp(t.number, w * 0.4f, (side * 0.66f).coerceIn(16f, 36f)), p)
                    Spacer(GlanceModifier.width(6.dp))
                }
                if (config.shows(WidgetPart.CAPTION)) Caption(t.shortCaption, 12f, p)
            }
        }
        if (hasButton) {
            // Place du bouton ↺ (posé par-dessus, centré à droite) à la place de l'anneau.
            Spacer(GlanceModifier.width(44.dp))
        } else if (config.shows(WidgetPart.CHART) && w >= 160f && side >= 24f) {
            Spacer(GlanceModifier.width(8.dp))
            ChartImage(draw.chart(ChartKind.RING, side, side), t.status, p.accent, GlanceModifier.size(side.dp))
        }
    }
}

/** Petit (2×2) : titre, puis toujours l'anneau avec le nombre au centre. */
@Composable
private fun Small(t: WidgetTexts, config: WidgetChartConfig, draw: ChartDrawer, w: Float, h: Float) {
    val p = draw.palette
    val pad = 12f
    val showTitle = config.shows(WidgetPart.TITLE)
    val side = minOf(w - 2 * pad, h - 2 * pad - if (showTitle) 18f else 0f).coerceAtLeast(1f)
    val showChart = config.shows(WidgetPart.CHART)
    val inner = if (showChart) side * 0.7f else side
    Column(GlanceModifier.fillMaxSize().padding(pad.dp)) {
        if (showTitle) Title(t.titleWithEmoji, 12f, p)
        Box(GlanceModifier.defaultWeight().fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (showChart) ChartImage(draw.chart(ChartKind.RING, side, side), t.status, p.accent, GlanceModifier.size(side.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (config.shows(WidgetPart.NUMBER)) BigNumber(t.number, fitSp(t.number, inner * 0.8f, inner * 0.44f), p)
                if (config.shows(WidgetPart.CAPTION)) Caption(t.shortCaption, 10f, p)
                t.record?.let { Caption(it, 9f, p, bold = true) }
            }
        }
    }
}

/** Moyen (4×2) : titre avec emoji en pastille, gros nombre à gauche, graphique choisi à droite, mini frise en bas. */
@Composable
private fun Medium(t: WidgetTexts, config: WidgetChartConfig, draw: ChartDrawer, w: Float, h: Float) {
    val p = draw.palette
    val pad = 16f
    val showLine = config.shows(WidgetPart.TIMELINE)
    val lineH = 14f
    val contentH = h - 2 * pad - if (showLine) lineH + 8f else 0f
    val showChart = config.shows(WidgetPart.CHART)
    val side = minOf(contentH, (w - 2 * pad) * 0.46f).coerceAtLeast(1f)
    val textW = w - 2 * pad - if (showChart) side + 12f else 0f
    // Hauteur laissée au nombre une fois le titre (pastille emoji) et la légende placés ; une ligne de
    // texte occupe ≈ 1,2 × sa taille.
    val numberRoom = contentH - (if (config.shows(WidgetPart.TITLE)) 30f else 0f) - (if (config.shows(WidgetPart.CAPTION)) 18f else 0f)
    Column(GlanceModifier.fillMaxSize().padding(pad.dp)) {
        Row(GlanceModifier.defaultWeight().fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                if (config.shows(WidgetPart.TITLE)) TitleRow(t, 14f, p)
                if (config.shows(WidgetPart.NUMBER)) BigNumber(t.number, fitSp(t.number, textW, (numberRoom / 1.2f).coerceIn(18f, 58f)), p)
                if (config.shows(WidgetPart.CAPTION)) Caption(listOfNotNull(t.captionWithRecord, t.percent).joinToString(" · "), 13f, p)
            }
            if (showChart) {
                Spacer(GlanceModifier.width(12.dp))
                ChartImage(draw.chart(config.chart, side, side), t.status, p.accent, GlanceModifier.size(side.dp))
            }
        }
        if (showLine) {
            Spacer(GlanceModifier.height(8.dp))
            ChartImage(draw.strip(w - 2 * pad, lineH), t.status, p.accent, GlanceModifier.fillMaxWidth().height(lineH.dp))
        }
    }
}

/** Grand (4×4) : titre et pourcentage, nombre et légende, grand graphique, mini frise avec ses dates. */
@Composable
private fun Large(t: WidgetTexts, config: WidgetChartConfig, draw: ChartDrawer, goal: Goal, w: Float, h: Float) {
    val p = draw.palette
    val resources = LocalContext.current.resources
    val pad = 18f
    val showTitle = config.shows(WidgetPart.TITLE)
    val showNumber = config.shows(WidgetPart.NUMBER)
    val showCaption = config.shows(WidgetPart.CAPTION)
    val showLine = config.shows(WidgetPart.TIMELINE)
    val lineH = 16f
    val chartW = w - 2 * pad
    val chartH = h - 2 * pad - (if (showTitle) 30f else 0f) - (if (showNumber) 74f else if (showCaption) 20f else 0f) - 10f -
        (if (showLine) lineH + 28f else 0f)
    Column(GlanceModifier.fillMaxSize().padding(pad.dp)) {
        if (showTitle) TitleRow(t, 16f, p, trailing = t.percent)
        if (showNumber || showCaption) {
            Row(verticalAlignment = Alignment.Bottom) {
                if (showNumber) {
                    BigNumber(t.number, fitSp(t.number, chartW * 0.55f, 60f), p)
                    Spacer(GlanceModifier.width(8.dp))
                }
                if (showCaption) Caption(t.captionWithRecord, 13f, p)
            }
        }
        Spacer(GlanceModifier.height(10.dp))
        Box(GlanceModifier.defaultWeight().fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (config.shows(WidgetPart.CHART) && chartH > 30f) {
                ChartImage(draw.chart(config.chart, chartW, chartH), t.status, p.accent, GlanceModifier.fillMaxSize())
            }
        }
        if (showLine) {
            Spacer(GlanceModifier.height(8.dp))
            ChartImage(draw.strip(chartW, lineH), t.status, p.accent, GlanceModifier.fillMaxWidth().height(lineH.dp))
            Row(GlanceModifier.fillMaxWidth().padding(top = 4.dp)) {
                Caption(goal.startDate.format(DateFormats.dayMonth), 11f, p)
                Spacer(GlanceModifier.defaultWeight())
                val target = goal.targetDate.takeIf { goal.kind == GoalKind.COUNTDOWN }
                Caption(
                    target?.let { resources.getString(R.string.strip_target, it.format(DateFormats.dayMonth)) }
                        ?: resources.getString(R.string.strip_today),
                    11f,
                    p,
                )
            }
        }
    }
}
