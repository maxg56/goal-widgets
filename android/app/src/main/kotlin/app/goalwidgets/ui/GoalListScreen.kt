package app.goalwidgets.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.goalwidgets.R
import app.goalwidgets.core.CountdownEngine
import app.goalwidgets.core.Goal
import app.goalwidgets.core.GoalColor
import app.goalwidgets.core.GoalKind
import app.goalwidgets.core.GoalOrder
import app.goalwidgets.core.GoalStyle
import app.goalwidgets.core.Phase
import java.time.LocalDate

/** Écran « Mes objectifs » : liste triée selon [order], avec état calculé pour [today]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalListScreen(
    goals: List<StoredGoal>?,
    today: LocalDate,
    onAdd: () -> Unit,
    order: GoalOrder = GoalOrder.DEFAULT,
    onOrderChange: (GoalOrder) -> Unit = {},
    onExport: () -> Unit = {},
    onImport: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onOpen: (StoredGoal) -> Unit = {},
    onContinueAsSince: (StoredGoal) -> Unit = {},
    /** Objectif à faire défiler jusqu'à l'écran (ouverture depuis un widget). */
    focusGoalId: Long? = null,
) {
    val sorted = remember(goals, order, today) { goals?.let { order.sort(it, today) { g -> g.sortKey } } }
    val listState = rememberLazyListState()
    LaunchedEffect(goals != null, focusGoalId) {
        val index = sorted?.indexOfFirst { it.id == focusGoalId } ?: -1
        if (index >= 0) listState.animateScrollToItem(index)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.goal_list_title)) },
                actions = { ListMenu(order, onOrderChange, onExport, onImport) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.goal_add)) },
            )
        },
    ) { padding ->
        when {
            sorted == null -> Unit // chargement : rien à afficher pendant quelques ms
            sorted.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.goal_list_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(sorted, key = { it.id }) { stored ->
                    GoalRow(
                        goal = stored.goal,
                        style = stored.style,
                        today = today,
                        onClick = { onOpen(stored) },
                        onContinueAsSince = { onContinueAsSince(stored) },
                    )
                }
            }
        }
    }
}

/** Menu ⋮ : choix du tri, export et import. */
@Composable
private fun ListMenu(order: GoalOrder, onOrderChange: (GoalOrder) -> Unit, onExport: () -> Unit, onImport: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        Text(
            text = stringResource(R.string.goal_sort_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        listOf(
            GoalOrder.CLOSEST_DATE to R.string.goal_sort_closest_date,
            GoalOrder.CREATED to R.string.goal_sort_created,
        ).forEach { (value, label) ->
            DropdownMenuItem(
                text = { Text(stringResource(label)) },
                onClick = { open = false; onOrderChange(value) },
                leadingIcon = {
                    if (order == value) Icon(Icons.Filled.Check, contentDescription = null)
                },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(text = { Text(stringResource(R.string.action_export)) }, onClick = { open = false; onExport() })
        DropdownMenuItem(text = { Text(stringResource(R.string.action_import)) }, onClick = { open = false; onImport() })
    }
}

@Composable
private fun GoalRow(goal: Goal, style: GoalStyle, today: LocalDate, onClick: () -> Unit, onContinueAsSince: () -> Unit) {
    val resources = LocalContext.current.resources
    val state = CountdownEngine.compute(goal, today)
    val accent = style.color?.let { GoalColors.of(it, isSystemInDarkTheme()) } ?: MaterialTheme.colorScheme.primary
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = titleWithEmoji(goal.title, style.emoji),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = GoalLabels.status(resources, state),
                style = MaterialTheme.typography.headlineSmall,
                color = accent,
            )
            GoalLabels.breakdown(resources, state.breakdown)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.percent?.let { percent ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier.weight(1f),
                        color = accent,
                        trackColor = ProgressIndicatorDefaults.linearTrackColor,
                    )
                    Text(
                        text = GoalLabels.percent(resources, percent),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            if (goal.kind == GoalKind.COUNTDOWN && state.phase == Phase.REACHED) {
                TextButton(onClick = onContinueAsSince, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.goal_action_continue_as_since))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GoalListScreenPreview() {
    val today = LocalDate.of(2026, 1, 6)
    GoalWidgetsTheme {
        GoalListScreen(
            goals = listOf(
                StoredGoal(1, Goal(GoalKind.COUNTDOWN, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 11), "Examen"), GoalStyle(GoalColor.BLUE, "🎓")),
                StoredGoal(2, Goal(GoalKind.COUNTDOWN, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 7), "Voyage")),
                StoredGoal(3, Goal(GoalKind.COUNTDOWN, LocalDate.of(2025, 12, 1), LocalDate.of(2026, 1, 2), "Concours")),
                StoredGoal(4, Goal(GoalKind.SINCE, LocalDate.of(2024, 11, 3), title = "Arrêt du tabac"), GoalStyle(GoalColor.GREEN, "🚭")),
            ),
            today = today,
            onAdd = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GoalListScreenEmptyPreview() {
    GoalWidgetsTheme { GoalListScreen(goals = emptyList(), today = LocalDate.of(2026, 1, 6), onAdd = {}) }
}
