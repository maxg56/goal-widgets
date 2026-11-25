package app.goalwidgets.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import app.goalwidgets.core.GoalKind
import java.time.LocalDate

/** Écran « Mes objectifs » : liste avec état calculé pour [today]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalListScreen(
    goals: List<StoredGoal>?,
    today: LocalDate,
    onAdd: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.goal_list_title)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.goal_add)) },
            )
        },
    ) { padding ->
        when {
            goals == null -> Unit // chargement : rien à afficher pendant quelques ms
            goals.isEmpty() -> Box(
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(goals, key = { it.id }) { GoalRow(it.goal, today) }
            }
        }
    }
}

@Composable
private fun GoalRow(goal: Goal, today: LocalDate) {
    val resources = LocalContext.current.resources
    val state = CountdownEngine.compute(goal, today)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = goal.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = GoalLabels.status(resources, state),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            state.percent?.let { percent ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = GoalLabels.percent(resources, percent),
                        style = MaterialTheme.typography.labelLarge,
                    )
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
                StoredGoal(1, Goal(GoalKind.COUNTDOWN, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 11), "Examen")),
                StoredGoal(2, Goal(GoalKind.COUNTDOWN, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 7), "Voyage")),
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
