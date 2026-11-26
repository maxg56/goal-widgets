package app.goalwidgets.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goalwidgets.R
import app.goalwidgets.core.CountdownEngine
import app.goalwidgets.core.GoalsTimeline
import app.goalwidgets.core.Phase
import app.goalwidgets.core.TimelineRow
import kotlinx.coroutines.delay
import java.time.LocalDate

private val DATE_COLUMN = 52.dp
private val LINE_COLUMN = 36.dp

/**
 * Frise verticale de tous les objectifs ([GoalsTimeline]) : passé en haut, futur en bas, repère
 * « Aujourd'hui », connecteurs remplis selon la progression. Appui sur un objectif → son détail.
 */
@Composable
fun GoalTimelineScreen(goals: List<StoredGoal>, today: LocalDate, onOpen: (StoredGoal) -> Unit, onBack: () -> Unit) {
    val rows = remember(goals, today) { GoalsTimeline.arrange(goals, today) { it.goal } }
    val todayIndex = rows.indexOf(TimelineRow.Today)
    // Ouverture centrée sur aujourd'hui, un peu de passé visible au-dessus.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (todayIndex - 2).coerceAtLeast(0))
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(
                stringResource(R.string.timeline_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        ) {
            itemsIndexed(rows, key = { _, r -> if (r is TimelineRow.Node) r.item.id else "today" }) { index, row ->
                val enter = Modifier.staggeredEnter(index, dx = (-24).dp, dy = 0.dp)
                when (row) {
                    TimelineRow.Today -> TodayMarker(today, isFirst = index == 0, isLast = index == rows.lastIndex, modifier = enter)
                    is TimelineRow.Node -> NodeRow(
                        stored = row.item,
                        date = row.date,
                        today = today,
                        index = index,
                        isFirst = index == 0,
                        isLast = index == rows.lastIndex,
                        onClick = { onOpen(row.item) },
                        modifier = enter.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun NodeRow(
    stored: StoredGoal,
    date: LocalDate,
    today: LocalDate,
    index: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val resources = LocalContext.current.resources
    val accent = stored.style.color?.let { GoalColors.of(it, isSystemInDarkTheme()) } ?: MaterialTheme.colorScheme.primary
    val state = CountdownEngine.compute(stored.goal, today)
    val past = date < today
    // Connecteur : plein dans le passé, selon la progression dans le futur.
    val fillTarget = if (past || state.phase != Phase.UPCOMING) 1f else (state.progress ?: 0.0).toFloat()
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(200L + 80L * index.coerceAtMost(8)); started = true }
    val fill by animateFloatAsState(if (started) fillTarget else 0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessVeryLow), label = "connector")
    val track = MaterialTheme.colorScheme.outlineVariant

    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)) {
        Column(Modifier.width(DATE_COLUMN).padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                date.format(DateFormats.monthShort) + if (date.year != today.year) " ${date.year % 100}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.width(LINE_COLUMN).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 2
                val w = 4.dp.toPx()
                val top = if (isFirst) 30.dp.toPx() else 0f
                val bottom = if (isLast) 30.dp.toPx() else size.height
                drawLine(track, Offset(x, top), Offset(x, bottom), w, StrokeCap.Round)
                drawLine(accent, Offset(x, top), Offset(x, top + (bottom - top) * fill), w, StrokeCap.Round)
            }
            Box(
                Modifier
                    .padding(top = 14.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (past) accent else MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(2.dp, accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val emoji = stored.style.emoji?.takeIf { it.isNotBlank() }
                if (emoji != null) {
                    Text(emoji, fontSize = 15.sp)
                } else {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (past) MaterialTheme.colorScheme.surface else accent))
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 6.dp, top = 8.dp, bottom = 8.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .animateContentSize(),
        ) {
            Text(stored.goal.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(GoalLabels.bigNumber(resources, state), color = accent, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(6.dp))
                Text(
                    GoalLabels.caption(resources, state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp).weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                state.percent?.let {
                    Text(
                        GoalLabels.percent(resources, it),
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            GoalLabels.breakdown(resources, state.breakdown)?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TodayMarker(today: LocalDate, isFirst: Boolean, isLast: Boolean, modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    val pulse by rememberInfiniteTransition(label = "today").animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "pulse",
    )
    Row(modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(DATE_COLUMN))
        Canvas(Modifier.width(LINE_COLUMN).fillMaxHeight()) {
            val x = size.width / 2
            val c = size.height / 2
            drawLine(track, Offset(x, if (isFirst) c else 0f), Offset(x, if (isLast) c else size.height), 4.dp.toPx())
            drawCircle(primary.copy(alpha = 0.25f), radius = 14.dp.toPx() * pulse, center = Offset(x, c))
            drawCircle(primary, radius = 7.dp.toPx(), center = Offset(x, c))
        }
        Text(
            text = stringResource(R.string.timeline_today, today.format(DateFormats.weekdayDayMonth)),
            modifier = Modifier
                .padding(start = 6.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(Modifier.weight(1f).padding(start = 8.dp).height(2.dp).background(primary.copy(alpha = 0.35f)))
    }
}
