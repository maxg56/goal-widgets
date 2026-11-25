package app.goalwidgets.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.goalwidgets.GoalWidgetsApplication
import app.goalwidgets.MainActivity
import app.goalwidgets.R
import app.goalwidgets.core.CountdownEngine
import app.goalwidgets.ui.GoalLabels
import app.goalwidgets.ui.GoalWidgetsTheme
import app.goalwidgets.ui.StoredGoal
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Choix de l'objectif d'un widget : lancé par le lanceur à l'ajout (et pour reconfigurer sur
 * Android 12+), ou par un appui sur un widget non configuré / dont l'objectif a été supprimé.
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
        val goalsFlow = dao.observeAll().map { rows -> rows.map { StoredGoal(it.id, it.toGoal()) } }
        setContent {
            GoalWidgetsTheme {
                val goals by goalsFlow.collectAsStateWithLifecycle(initialValue = null)
                var current by remember { mutableStateOf<Long?>(null) }
                LaunchedEffect(appWidgetId) { current = currentGoalId(appWidgetId) }
                GoalPickerScreen(
                    goals = goals,
                    selectedId = current,
                    today = LocalDate.now(),
                    onPick = { goalId ->
                        lifecycleScope.launch {
                            GoalWidgets.assign(this@GoalWidgetConfigureActivity, appWidgetId, goalId)
                            MidnightRefresh.schedule(this@GoalWidgetConfigureActivity)
                            setResult(RESULT_OK, result)
                            finish()
                        }
                    },
                    onOpenApp = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                )
            }
        }
    }

    private suspend fun currentGoalId(appWidgetId: Int): Long? = runCatching {
        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
        getAppWidgetState(this, PreferencesGlanceStateDefinition, glanceId)[GoalWidgets.GOAL_ID]
    }.getOrNull()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalPickerScreen(
    goals: List<StoredGoal>?,
    selectedId: Long?,
    today: LocalDate,
    onPick: (Long) -> Unit,
    onOpenApp: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.widget_config_title)) }) }) { padding ->
        when {
            goals == null -> Unit
            goals.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.widget_config_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onOpenApp, modifier = Modifier.padding(top = 16.dp)) {
                    Text(stringResource(R.string.widget_config_open_app))
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(goals, key = { it.id }) { stored ->
                    PickerRow(stored, selected = stored.id == selectedId, today = today) { onPick(stored.id) }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(stored: StoredGoal, selected: Boolean, today: LocalDate, onClick: () -> Unit) {
    val resources = LocalContext.current.resources
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stored.goal.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = GoalLabels.status(resources, CountdownEngine.compute(stored.goal, today)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (selected) {
                Box(modifier = Modifier.padding(start = 12.dp)) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.widget_config_current))
                }
            }
        }
    }
}
