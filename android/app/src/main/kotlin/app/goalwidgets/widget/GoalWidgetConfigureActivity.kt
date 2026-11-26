package app.goalwidgets.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.goalwidgets.GoalWidgetsApplication
import app.goalwidgets.MainActivity
import app.goalwidgets.R
import app.goalwidgets.core.ChartKind
import app.goalwidgets.core.CountdownEngine
import app.goalwidgets.core.DotPeriod
import app.goalwidgets.core.GoalStyle
import app.goalwidgets.core.WidgetChartConfig
import app.goalwidgets.core.WidgetPart
import app.goalwidgets.core.WidgetSize
import app.goalwidgets.ui.EditableBlock
import app.goalwidgets.ui.GoalLabels
import app.goalwidgets.ui.GoalTint
import app.goalwidgets.ui.GoalWidgetsTheme
import app.goalwidgets.ui.HiddenRow
import app.goalwidgets.ui.StoredGoal
import app.goalwidgets.ui.titleWithEmoji
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Configuration d'un widget : lancée par le lanceur à l'ajout (et pour reconfigurer sur Android 12+),
 * ou par un appui sur un widget non configuré / dont l'objectif a été supprimé.
 * - Widget « Objectif » : aperçu en direct (le vrai rendu du widget, petit / moyen / grand), choix de
 *   l'objectif, du graphique et des éléments affichés (× pour retirer, + pour remettre).
 * - Widget « Grille » : choix de l'objectif et de la période.
 */
class GoalWidgetConfigureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        // Retour arrière à l'ajout = widget annulé (le lanceur ne le pose pas).
        setResult(RESULT_CANCELED, result)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        enableEdgeToEdge()
        val dao = (application as GoalWidgetsApplication).database.goalDao()
        val goalsFlow = dao.observeAll().map { rows -> rows.map(StoredGoal::from) }
        val isGrid = GoalWidgets.isDotGrid(this, appWidgetId)
        setContent {
            GoalWidgetsTheme {
                val goals by goalsFlow.collectAsStateWithLifecycle(initialValue = null)
                var goalId by remember { mutableStateOf<Long?>(null) }
                var period by remember { mutableStateOf(DotPeriod.WHOLE) }
                var chart by remember { mutableStateOf(WidgetChartConfig.DEFAULT) }
                var isUpdate by remember { mutableStateOf(false) }
                LaunchedEffect(appWidgetId) {
                    val state = currentState(appWidgetId)
                    goalId = state?.get(GoalWidgets.GOAL_ID)
                    isUpdate = goalId != null
                    period = DotPeriod.fromWire(state?.get(GoalWidgets.PERIOD)) ?: DotPeriod.WHOLE
                    chart = WidgetChartConfig.decode(state?.get(GoalWidgets.CHART), state?.get(GoalWidgets.HIDDEN))
                }
                val list = goals
                WidgetConfigScreen(
                    goals = list,
                    // Objectif supprimé ou pas encore choisi : le premier est présélectionné.
                    selected = list?.let { gs -> gs.firstOrNull { it.id == goalId } ?: gs.firstOrNull() },
                    isGrid = isGrid,
                    period = period,
                    chart = chart,
                    isUpdate = isUpdate,
                    onGoal = { goalId = it },
                    onPeriod = { period = it },
                    onChart = { chart = it },
                    onClose = ::finish,
                    onOpenApp = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                    onConfirm = { id ->
                        lifecycleScope.launch {
                            val context = this@GoalWidgetConfigureActivity
                            GoalWidgets.assign(context, appWidgetId, id, period.takeIf { isGrid }, chart.takeUnless { isGrid })
                            MidnightRefresh.schedule(context)
                            setResult(RESULT_OK, result)
                            finish()
                        }
                    },
                )
            }
        }
    }

    private suspend fun currentState(appWidgetId: Int): Preferences? = runCatching {
        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
        getAppWidgetState(this, PreferencesGlanceStateDefinition, glanceId)
    }.getOrNull()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetConfigScreen(
    goals: List<StoredGoal>?,
    selected: StoredGoal?,
    isGrid: Boolean,
    period: DotPeriod,
    chart: WidgetChartConfig,
    isUpdate: Boolean,
    onGoal: (Long) -> Unit,
    onPeriod: (DotPeriod) -> Unit,
    onChart: (WidgetChartConfig) -> Unit,
    onClose: () -> Unit,
    onOpenApp: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val today = remember { LocalDate.now() }
    val base by animateColorAsState(GoalTint.deepColor(selected?.style ?: GoalStyle.NONE), spring(stiffness = Spring.StiffnessLow), label = "configBase")
    var size by remember { mutableStateOf(WidgetSize.MEDIUM) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel)) }
                Text(
                    stringResource(if (isGrid) R.string.widget_grid_name else R.string.widget_config_header),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }
            when {
                goals == null -> Unit
                goals.isEmpty() || selected == null -> Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.widget_config_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onOpenApp, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.widget_config_open_app)) }
                }
                else -> {
                    if (!isGrid) {
                        PreviewStage(selected, today, chart, size, base)
                        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SizeToggle(size, base) { size = it }
                            Text(stringResource(R.string.widget_size_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    SectionTitle(stringResource(R.string.widget_config_goal), Modifier.padding(start = 20.dp, top = 24.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(goals, key = { it.id }) { g -> GoalChoice(g, today, g.id == selected.id) { onGoal(g.id) } }
                    }
                    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        if (isGrid) {
                            PeriodChoice(period, base, onPeriod)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SectionTitle(stringResource(if (size == WidgetSize.SMALL) R.string.widget_config_chart_small else R.string.widget_config_chart))
                                ChartChoices(selected, today, chart.chart) { onChart(chart.copy(chart = it)) }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SectionTitle(stringResource(R.string.widget_config_parts))
                                val shown = WidgetPart.entries.filter { chart.shows(it) }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    shown.forEachIndexed { i, part ->
                                        val label = stringResource(partLabel(part))
                                        EditableBlock(editing = shown.size > 1, seed = i, label = label, onRemove = { onChart(chart.hide(part)) }, badgeOffset = 7.dp) {
                                            Text(
                                                label,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                color = Color.White,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(50))
                                                    .background(Brush.horizontalGradient(listOf(lerp(base, Color.White, 0.12f), base)))
                                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                            )
                                        }
                                    }
                                }
                                HiddenRow(
                                    visible = true,
                                    items = WidgetPart.entries.filter { !chart.shows(it) }.map { p -> stringResource(partLabel(p)) to { onChart(chart.restore(p)) } },
                                    fg = MaterialTheme.colorScheme.onSurface,
                                    chipBg = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                        Spacer(Modifier.navigationBarsPadding().height(100.dp))
                    }
                }
            }
        }
        if (selected != null) ConfirmBar(isUpdate, base, Modifier.align(Alignment.BottomCenter)) { onConfirm(selected.id) }
    }
}

private fun partLabel(part: WidgetPart) = when (part) {
    WidgetPart.TITLE -> R.string.widget_part_title
    WidgetPart.NUMBER -> R.string.widget_part_number
    WidgetPart.CAPTION -> R.string.widget_part_caption
    WidgetPart.CHART -> R.string.widget_part_chart
    WidgetPart.TIMELINE -> R.string.widget_part_timeline
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = modifier)
}

/** Scène d'aperçu : « fond d'écran » doux et vrai rendu du widget (même composition Glance que l'écran d'accueil). */
@Composable
private fun PreviewStage(goal: StoredGoal, today: LocalDate, config: WidgetChartConfig, size: WidgetSize, base: Color) {
    Box(
        Modifier
            .padding(20.dp)
            .fillMaxWidth()
            .height(360.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(
                Brush.linearGradient(
                    listOf(lerp(base, Color.White, 0.75f), lerp(base, MaterialTheme.colorScheme.surfaceContainerHigh, 0.6f), lerp(base, Color.Black, 0.55f)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            val scaleDown = minOf(1f, maxWidth.value / size.previewWidthDp, maxHeight.value / size.previewHeightDp)
            val w by animateDpAsState((size.previewWidthDp * scaleDown).dp, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "previewWidth")
            val h by animateDpAsState((size.previewHeightDp * scaleDown).dp, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "previewHeight")
            val description = stringResource(R.string.widget_preview_a11y)
            Box(
                Modifier.size(w, h).shadow(18.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = Triple(goal, config, size),
                    transitionSpec = {
                        (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + scaleIn(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.96f)) togetherWith
                            (fadeOut(spring(stiffness = Spring.StiffnessMedium)) + scaleOut(targetScale = 1.02f))
                    },
                    label = "preview",
                ) { (g, c, s) ->
                    val k = minOf(1f, w.value / s.previewWidthDp, h.value / s.previewHeightDp)
                    WidgetRemoteView(g, today, c, s, Modifier.requiredSize(s.previewWidthDp.dp, s.previewHeightDp.dp).graphicsLayer { scaleX = k; scaleY = k })
                }
            }
        }
    }
}

/** Rendu réel du widget (RemoteViews composées par Glance, appliquées dans l'app). */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@Composable
private fun WidgetRemoteView(goal: StoredGoal, today: LocalDate, config: WidgetChartConfig, size: WidgetSize, modifier: Modifier) {
    val context = LocalContext.current
    val views by produceState<RemoteViews?>(null, goal, today, config, size) {
        // API expérimentale : en cas d'échec, pas d'aperçu plutôt qu'un plantage de la configuration.
        value = runCatching {
            GlanceRemoteViews().compose(context, DpSize(size.previewWidthDp.dp, size.previewHeightDp.dp)) {
                GlanceTheme { ChartWidgetContent(goal.goal, goal.style, today, config, click = null) }
            }.remoteViews
        }.getOrNull()
    }
    AndroidView(
        factory = { FrameLayout(it) },
        update = { frame ->
            frame.removeAllViews()
            views?.let { v -> runCatching { v.apply(frame.context, frame) }.getOrNull()?.let(frame::addView) }
        },
        modifier = modifier,
    )
}

@Composable
private fun SizeToggle(size: WidgetSize, base: Color, onSelect: (WidgetSize) -> Unit) {
    val options = listOf(WidgetSize.SMALL to R.string.widget_size_small, WidgetSize.MEDIUM to R.string.widget_size_medium, WidgetSize.LARGE to R.string.widget_size_large)
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(4.dp),
    ) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * options.indexOfFirst { it.first == size }.coerceAtLeast(0), spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow), label = "size")
        Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(RoundedCornerShape(50)).background(base))
        Row(Modifier.fillMaxSize()) {
            options.forEach { (s, label) ->
                val on = s == size
                val fg by animateColorAsState(if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, label = "sizeText")
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(50))
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton) { onSelect(s) },
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(label), color = fg, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium) }
            }
        }
    }
}

@Composable
private fun PeriodChoice(period: DotPeriod, base: Color, onChange: (DotPeriod) -> Unit) {
    val options = listOf(
        DotPeriod.WHOLE to R.string.widget_period_whole,
        DotPeriod.MONTH to R.string.widget_period_month,
        DotPeriod.WEEK to R.string.widget_period_week,
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.widget_config_period))
        options.forEach { (value, label) ->
            val on = value == period
            val bg by animateColorAsState(if (on) base else MaterialTheme.colorScheme.surfaceContainerHigh, label = "period")
            val fg by animateColorAsState(if (on) Color.White else MaterialTheme.colorScheme.onSurface, label = "periodText")
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(bg).clickable(role = Role.RadioButton) { onChange(value) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(label), color = fg, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (on) Icon(Icons.Filled.Check, contentDescription = null, tint = fg)
            }
        }
    }
}

@Composable
private fun GoalChoice(stored: StoredGoal, today: LocalDate, selected: Boolean, onClick: () -> Unit) {
    val resources = LocalContext.current.resources
    val scale by animateFloatAsState(if (selected) 1.04f else 0.96f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow), label = "goalChoice")
    val base = GoalTint.deepColor(stored.style)
    Column(
        Modifier
            .scale(scale)
            .width(140.dp)
            .height(108.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(GoalTint.gradient(base)))
            .border(if (selected) 3.dp else 0.dp, if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, RoundedCornerShape(24.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(titleWithEmoji(stored.goal.title, stored.style.emoji), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(GoalLabels.bigNumber(resources, CountdownEngine.compute(stored.goal, today)), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
    }
}

/** Vignettes des graphiques : même dessin que le widget. */
@Composable
private fun ChartChoices(goal: StoredGoal, today: LocalDate, selected: ChartKind, onSelect: (ChartKind) -> Unit) {
    val resources = LocalContext.current.resources
    val density = LocalDensity.current.density
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ChartKind.entries.forEach { kind ->
            val on = kind == selected
            val scale by animateFloatAsState(if (on) 1.06f else 1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow), label = "chartChoice")
            val thumb = remember(goal, today, kind, density) { WidgetBitmaps.chart(kind, goal.goal, today, 44f, 44f, density).asImageBitmap() }
            val name = GoalLabels.chartName(resources, kind)
            Column(
                Modifier.weight(1f).scale(scale).clip(RoundedCornerShape(20.dp)).clickable(role = Role.RadioButton) { onSelect(kind) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .border(if (on) 3.dp else 0.dp, if (on) MaterialTheme.colorScheme.onSurface else Color.Transparent, RoundedCornerShape(21.dp))
                        .padding(3.dp)
                        .size(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(GoalTint.gradient(GoalTint.deepColor(goal.style)))),
                    contentAlignment = Alignment.Center,
                ) { Image(thumb, contentDescription = name, colorFilter = ColorFilter.tint(Color.White), modifier = Modifier.size(44.dp)) }
                Text(name, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun ConfirmBar(isUpdate: Boolean, base: Color, modifier: Modifier, onClick: () -> Unit) {
    val bg = MaterialTheme.colorScheme.background
    Box(
        modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(bg.copy(alpha = 0f), bg))).navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 14.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(lerp(base, Color.White, 0.15f), base)))
                .clickable(role = Role.Button, onClick = onClick),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(if (isUpdate) R.string.widget_config_update else R.string.widget_config_add), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}
