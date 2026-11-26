package app.goalwidgets.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goalwidgets.R
import app.goalwidgets.core.ArcsChart
import app.goalwidgets.core.BarsChart
import app.goalwidgets.core.ChartKind
import app.goalwidgets.core.ChartUnit
import app.goalwidgets.core.CountdownEngine
import app.goalwidgets.core.DetailLayout
import app.goalwidgets.core.DetailPart
import app.goalwidgets.core.DotGrid
import app.goalwidgets.core.DotPeriod
import app.goalwidgets.core.GoalKind
import app.goalwidgets.core.GoalStrip
import app.goalwidgets.core.RingChart
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.absoluteValue
import kotlin.math.floor

/**
 * Personnalisation du détail, commune à tous les objectifs : graphique choisi et blocs masqués,
 * mémorisés dans les préférences « settings » (`detail_chart`, `detail_layout` = [DetailLayout.encode]).
 */
class DetailSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    var layout by mutableStateOf(DetailLayout.decode(prefs.getString(KEY_LAYOUT, null)))
        private set
    var chart by mutableStateOf(ChartKind.fromWire(prefs.getString(KEY_CHART, null)))
        private set

    fun update(new: DetailLayout) {
        layout = new
        prefs.edit().putString(KEY_LAYOUT, new.encode()).apply()
    }

    fun select(kind: ChartKind) {
        chart = kind
        prefs.edit().putString(KEY_CHART, kind.wire).apply()
    }

    private companion object {
        const val PREFS = "settings"
        const val KEY_LAYOUT = "detail_layout"
        const val KEY_CHART = "detail_chart"
    }
}

/**
 * Détail d'un objectif : une page par objectif (pager vers ses voisins), chiffre géant, graphique au
 * choix (points, anneau, arcs, barres), frise de l'objectif en bas. Crayon → personnalisation (× pour
 * masquer, rangée « Masqués » pour remettre). « Modifier » ouvre le formulaire.
 */
@Composable
fun GoalDetailScreen(
    goals: List<StoredGoal>,
    today: LocalDate,
    initialGoalId: Long,
    sharedCard: @Composable (goalId: Long) -> Modifier,
    onFocusChange: (Long) -> Unit,
    onBack: () -> Unit,
    onEdit: (StoredGoal) -> Unit,
    onShare: (StoredGoal) -> Unit,
    /** Remise à zéro d'un compteur « série », après confirmation. */
    onResetStreak: (Long, LocalDate) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val resources = context.resources
    val settings = remember { DetailSettings(context) }
    val layout = settings.layout
    val chart = layout.chartToShow(settings.chart)
    var editing by rememberSaveable { mutableStateOf(false) }
    // Objectif dont la remise à zéro attend confirmation.
    var resetting by rememberSaveable { mutableStateOf<Long?>(null) }
    goals.firstOrNull { it.id == resetting && it.isStreak }?.let { stored ->
        ResetStreakDialog(
            stored = stored,
            today = today,
            onConfirm = { on -> onResetStreak(stored.id, on); resetting = null },
            onDismiss = { resetting = null },
        )
    }
    BackHandler { if (editing) editing = false else onBack() }

    val initial = goals.indexOfFirst { it.id == initialGoalId }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = initial) { goals.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager.settledPage) { goals.getOrNull(pager.settledPage)?.let { onFocusChange(it.id) } }
    // Liste réordonnée pendant que le détail est ouvert (import, objectif reçu, tri à minuit) :
    // le pager suit l'objectif affiché au lieu de garder son index.
    var shownId by remember { mutableStateOf(goals.getOrNull(initial)?.id) }
    LaunchedEffect(pager.settledPage) { goals.getOrNull(pager.settledPage)?.let { shownId = it.id } }
    LaunchedEffect(goals) {
        val index = goals.indexOfFirst { it.id == shownId }
        if (index >= 0 && index != pager.currentPage) pager.scrollToPage(index)
    }
    val current = goals.getOrNull(pager.currentPage) ?: goals[initial]
    val focused = goals.getOrNull(pager.settledPage) ?: current

    // Fond : interpolation continue entre la couleur de la page et celle de sa voisine.
    val position = pager.currentPage + pager.currentPageOffsetFraction
    val from = floor(position).toInt().coerceIn(0, goals.lastIndex)
    val to = (from + 1).coerceAtMost(goals.lastIndex)
    val base = lerp(GoalTint.deepColor(goals[from].style), GoalTint.deepColor(goals[to].style), (position - from).coerceIn(0f, 1f))

    Box(
        Modifier
            .fillMaxSize()
            // Bornes partagées avec la carte de l'objectif affiché (le retour vise la bonne carte).
            .then(sharedCard(focused.id))
            .background(Brush.verticalGradient(GoalTint.gradient(base, darken = 0.65f))),
    ) {
        // Halo doux derrière le chiffre.
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width * 0.5f, size.height * 0.22f)
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), center = c, radius = size.width * 0.8f),
                radius = size.width * 0.8f,
                center = c,
            )
        }
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = Color.White)
                }
                Text(
                    stringResource(R.string.detail_position, pager.currentPage + 1, goals.size),
                    color = Color.White.copy(alpha = 0.75f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                AnimatedContent(
                    targetState = editing,
                    transitionSpec = {
                        (fadeIn() + scaleIn(spring(dampingRatio = 0.6f), initialScale = 0.8f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.8f))
                    },
                    label = "detailBar",
                ) { isEditing ->
                    if (isEditing) {
                        Pill(stringResource(R.string.detail_done), Icons.Filled.Check, solid = true) { editing = false }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RoundIcon(Icons.Filled.Share, stringResource(R.string.action_share)) { onShare(current) }
                            Spacer(Modifier.width(8.dp))
                            RoundIcon(Icons.Filled.Edit, stringResource(R.string.detail_customize)) { editing = true }
                            Spacer(Modifier.width(8.dp))
                            Pill(stringResource(R.string.action_edit), null, solid = false) { onEdit(current) }
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = editing,
                enter = expandVertically(spring(dampingRatio = 0.8f)) + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Text(
                    stringResource(R.string.detail_customize_hint),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            HiddenRow(
                visible = editing,
                items = hiddenItems(layout, resources) { settings.update(it) },
                fg = Color.White,
                chipBg = Color.White.copy(alpha = 0.18f),
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            if (goals.size > 1 && !editing) {
                GoalPills(goals, today, selected = pager.currentPage, onSelect = {
                    scope.launch { pager.animateScrollToPage(it, animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)) }
                })
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f),
                userScrollEnabled = !editing,
                key = { goals[it].id },
            ) { page ->
                val distance = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                DetailPage(
                    stored = goals[page],
                    today = today,
                    isSettled = pager.settledPage == page,
                    chart = chart,
                    layout = layout,
                    editing = editing,
                    settings = settings,
                    onReset = { resetting = goals[page].id },
                    modifier = Modifier.graphicsLayer {
                        alpha = 1f - distance * 0.6f
                        val s = 1f - distance * 0.12f
                        scaleX = s
                        scaleY = s
                    },
                )
            }
        }
    }
}

private fun partLabel(part: DetailPart) = when (part) {
    DetailPart.NUMBER -> R.string.detail_part_number
    DetailPart.CAPTION -> R.string.detail_part_caption
    DetailPart.CHART -> R.string.detail_part_chart
    DetailPart.TIMELINE -> R.string.detail_part_timeline
}

/** Éléments masqués, dans l'ordre d'affichage, avec l'action qui les remet. */
private fun hiddenItems(
    layout: DetailLayout,
    resources: android.content.res.Resources,
    update: (DetailLayout) -> Unit,
): List<Pair<String, () -> Unit>> =
    DetailPart.entries.filter { !layout.shows(it) }.map { part ->
        resources.getString(partLabel(part)) to { update(layout.restore(part)) }
    } + ChartKind.entries.filter { it !in layout.charts }.map { kind ->
        resources.getString(R.string.detail_hidden_chart, GoalLabels.chartName(resources, kind)) to { update(layout.restore(kind)) }
    }

@Composable
private fun RoundIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)).clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun Pill(text: String, icon: ImageVector?, solid: Boolean, onClick: () -> Unit) {
    val fg = if (solid) Color.Black.copy(alpha = 0.85f) else Color.White
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (solid) Color.White else Color.White.copy(alpha = 0.2f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DetailPage(
    stored: StoredGoal,
    today: LocalDate,
    isSettled: Boolean,
    chart: ChartKind,
    layout: DetailLayout,
    editing: Boolean,
    settings: DetailSettings,
    onReset: () -> Unit,
    modifier: Modifier,
) {
    val resources = LocalContext.current.resources
    val goal = stored.goal
    // Unité des graphiques : celle de l'objectif, sinon le réglage général.
    val unit = ChartUnit.resolve(stored.style.chartUnit, globalChartUnit())
    val state = CountdownEngine.compute(goal, today)
    val accent = GoalTint.accent(stored.style, isSystemInDarkTheme())
    val card = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.96f)
    val numberLabel = stringResource(R.string.detail_part_number)
    val captionLabel = stringResource(R.string.detail_part_caption)
    val chartLabel = stringResource(R.string.detail_part_chart)
    val timelineLabel = stringResource(R.string.detail_part_timeline)

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        stored.style.emoji?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 40.sp) }
        Text(
            goal.title,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val number = GoalLabels.bigNumber(resources, state)
        AnimatedVisibility(layout.shows(DetailPart.NUMBER)) {
            EditableBlock(editing, seed = 0, label = numberLabel, onRemove = { settings.update(layout.hide(DetailPart.NUMBER)) }, badgeOffset = 0.dp) {
                Text(
                    number,
                    color = Color.White,
                    fontSize = bigNumberSize(number, 132),
                    lineHeight = bigNumberSize(number, 132),
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-5).sp,
                    modifier = Modifier.padding(top = 2.dp, start = 12.dp, end = 12.dp),
                )
            }
        }
        AnimatedVisibility(layout.shows(DetailPart.CAPTION)) {
            EditableBlock(editing, seed = 1, label = captionLabel, onRemove = { settings.update(layout.hide(DetailPart.CAPTION)) }) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(GoalLabels.caption(resources, state), color = Color.White.copy(alpha = 0.85f), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    val detail = listOfNotNull(
                        GoalLabels.breakdown(resources, state.breakdown),
                        state.percent?.let { stringResource(R.string.detail_percent_of_path, GoalLabels.percent(resources, it)) },
                    ).joinToString(" · ")
                    if (detail.isNotEmpty()) {
                        Text(detail, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
        AnimatedVisibility(stored.isStreak && !editing) {
            StreakBar(stored, today, onReset, Modifier.padding(top = 14.dp))
        }
        AnimatedVisibility(layout.shows(DetailPart.CHART)) {
            EditableBlock(
                editing,
                seed = 2,
                label = chartLabel,
                onRemove = { settings.update(layout.hide(DetailPart.CHART)) },
                modifier = Modifier.padding(top = 22.dp),
            ) {
                Column {
                    // Un seul graphique restant : sélecteur inutile (gardé en personnalisation).
                    AnimatedVisibility(editing || layout.charts.size > 1) {
                        ChartSelector(
                            selected = chart,
                            kinds = layout.charts,
                            onSelect = settings::select,
                            editing = editing,
                            onRemove = { settings.update(layout.hide(it)) },
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    Box(
                        Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(28.dp)).background(card).padding(16.dp),
                    ) {
                        AnimatedContent(
                            targetState = chart,
                            transitionSpec = {
                                (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                                    scaleIn(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.86f)) togetherWith
                                    (fadeOut(spring(stiffness = Spring.StiffnessMedium)) + scaleOut(targetScale = 1.06f))
                            },
                            label = "chart",
                        ) { kind ->
                            val m = Modifier.fillMaxSize()
                            when (kind) {
                                ChartKind.DOTS -> DotsChartView(remember(goal, today, unit) { DotGrid.compute(goal, today, DotPeriod.WHOLE, unit) }, accent, isSettled, m)
                                ChartKind.RING -> RingChartView(remember(goal, today) { RingChart.compute(goal, today) }, accent, isSettled, m)
                                ChartKind.ARCS -> ArcsChartView(remember(goal, today, unit) { ArcsChart.compute(goal, today, unit) }, accent, isSettled, m)
                                ChartKind.BARS -> BarsChartView(remember(goal, today, unit) { BarsChart.compute(goal, today, unit) }, accent, isSettled, m)
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(layout.shows(DetailPart.TIMELINE)) {
            EditableBlock(
                editing,
                seed = 3,
                label = timelineLabel,
                onRemove = { settings.update(layout.hide(DetailPart.TIMELINE)) },
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(card).padding(top = 14.dp, bottom = 6.dp)) {
                    Text(
                        stringResource(if (goal.kind == GoalKind.COUNTDOWN) R.string.strip_title_countdown else R.string.strip_title_since),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 18.dp),
                    )
                    GoalStripView(remember(goal, today) { GoalStrip.compute(goal, today) }, accent, isSettled)
                }
            }
        }
        if (stored.isStreak) StreakHistoryCard(stored, card, Modifier.padding(top = 16.dp))
        Spacer(Modifier.navigationBarsPadding().height(32.dp))
    }
}

/** Pastilles des objectifs (accès direct à une page du pager). */
@Composable
private fun GoalPills(goals: List<StoredGoal>, today: LocalDate, selected: Int, onSelect: (Int) -> Unit) {
    val resources = LocalContext.current.resources
    val listState = rememberLazyListState()
    LaunchedEffect(selected) { listState.animateScrollToItem((selected - 1).coerceAtLeast(0)) }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(goals, key = { _, g -> g.id }) { index, stored ->
            val isSelected = index == selected
            val bg by animateColorAsState(if (isSelected) Color.White else Color.White.copy(alpha = 0.14f), spring(stiffness = Spring.StiffnessLow), label = "pill")
            val fg by animateColorAsState(if (isSelected) GoalTint.deepColor(stored.style) else Color.White, spring(stiffness = Spring.StiffnessLow), label = "pillText")
            val state = CountdownEngine.compute(stored.goal, today)
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(bg).clickable { onSelect(index) }.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                stored.style.emoji?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 13.sp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    stored.goal.title,
                    color = fg,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 6.dp).widthIn(max = 120.dp),
                )
                Text(GoalLabels.bigNumber(resources, state), color = fg.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}
