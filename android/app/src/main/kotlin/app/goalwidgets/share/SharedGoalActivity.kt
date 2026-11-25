package app.goalwidgets.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import app.goalwidgets.GoalWidgetsApplication
import app.goalwidgets.MainActivity
import app.goalwidgets.R
import app.goalwidgets.core.CountdownEngine
import app.goalwidgets.core.ExportedGoal
import app.goalwidgets.core.GoalExport
import app.goalwidgets.core.GoalImport
import app.goalwidgets.core.GoalKind
import app.goalwidgets.ui.GoalColors
import app.goalwidgets.ui.GoalLabels
import app.goalwidgets.ui.GoalWidgetsTheme
import app.goalwidgets.ui.rememberLocalToday
import app.goalwidgets.ui.titleWithEmoji
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Ouverture d'un objectif partagé (ticket 13) : fichier reçu (ACTION_VIEW depuis une messagerie ou
 * un gestionnaire de fichiers, ou ACTION_SEND « Partager avec Goal Widgets »). Aperçu de
 * l'objectif (ou de la liste, pour un export complet) → « Ajouter » / « Annuler ».
 * Même logique que l'import ([GoalExport.decode] puis [GoalImport.plan]) : un fichier invalide
 * n'écrit rien ; un objectif déjà présent (même id) est signalé et jamais dupliqué.
 */
class SharedGoalActivity : ComponentActivity() {

    private var state by mutableStateOf<UiState>(UiState.Loading)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        load(intent)
        setContent {
            GoalWidgetsTheme {
                SharedGoalScreen(
                    state = state,
                    onAdd = ::add,
                    onCancel = ::finish,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        load(intent)
    }

    private fun load(intent: Intent) {
        state = UiState.Loading
        lifecycleScope.launch { state = read(intent) }
    }

    private suspend fun read(intent: Intent): UiState {
        val text = try {
            withContext(Dispatchers.IO) { textOf(intent) }
        } catch (e: Exception) {
            null
        } ?: return UiState.Rejected(getString(R.string.import_error_unreadable))
        return when (val decoded = GoalExport.decode(text)) {
            is GoalExport.Decoded.Failure -> UiState.Rejected(GoalFiles.errorMessage(resources, decoded.error))
            is GoalExport.Decoded.Success -> {
                if (decoded.goals.isEmpty()) return UiState.Rejected(getString(R.string.import_empty))
                val existing = dao().allUids().toSet()
                val toAdd = GoalImport.plan(existing, decoded.goals).toAdd.mapTo(mutableSetOf()) { it.id }
                UiState.Preview(decoded.goals, alreadyPresent = decoded.goals.map { it.id }.toSet() - toAdd)
            }
        }
    }

    /** Contenu du fichier reçu : URI (VIEW, ou flux de SEND), sinon texte brut de SEND. */
    private fun textOf(intent: Intent): String? {
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
        if (uri != null) return GoalFiles.readText(contentResolver, uri)
        return intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.length <= GoalFiles.MAX_BYTES }
    }

    private fun add() {
        val preview = state as? UiState.Preview ?: return
        state = UiState.Adding
        lifecycleScope.launch {
            val app = application as GoalWidgetsApplication
            // Re-planifié dans la transaction : un objectif ajouté entre-temps n'est pas dupliqué.
            val plan = dao().importGoals(preview.goals)
            if (plan.toAdd.isNotEmpty()) app.onGoalsChanged()
            val added = plan.toAdd.size
            if (added > 0) {
                Toast.makeText(app, resources.getQuantityString(R.plurals.import_added, added, added), Toast.LENGTH_SHORT).show()
            }
            // Ouvre la liste sur le premier objectif ajouté (ou déjà présent).
            val uid = (plan.toAdd.firstOrNull() ?: preview.goals.first()).id
            val goalId = dao().getAll().firstOrNull { it.uid == uid }?.id
            startActivity(
                Intent(this@SharedGoalActivity, MainActivity::class.java)
                    .apply { if (goalId != null) putExtra(MainActivity.EXTRA_GOAL_ID, goalId) }
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
            finish()
        }
    }

    private fun dao() = (application as GoalWidgetsApplication).database.goalDao()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedGoalScreen(
    state: UiState,
    onAdd: () -> Unit,
    onCancel: () -> Unit,
) {
    val today by rememberLocalToday()
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.shared_title)) }) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                    val newCount = (state as? UiState.Preview)?.newCount ?: 0
                    if (state !is UiState.Rejected) {
                        Button(onClick = onAdd, enabled = newCount > 0) {
                            Text(stringResource(R.string.action_add))
                        }
                    }
                }
            }
        },
    ) { padding ->
        when (state) {
            is UiState.Rejected -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(state.message, style = MaterialTheme.typography.bodyLarge)
            }
            is UiState.Preview -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.goals.size > 1) {
                    item {
                        Text(
                            pluralStringResource(R.plurals.shared_count, state.goals.size, state.goals.size),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
                items(state.goals, key = { it.id }) { goal ->
                    SharedGoalCard(goal, alreadyPresent = goal.id in state.alreadyPresent, today = today)
                }
            }
            else -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }
        }
    }
}

/** État de l'écran d'aperçu. */
private sealed interface UiState {
    data object Loading : UiState

    /** Fichier refusé : message clair, rien n'est écrit. */
    data class Rejected(val message: String) : UiState

    /** [alreadyPresent] = ids déjà dans les objectifs locaux (signalés, jamais dupliqués). */
    data class Preview(val goals: List<ExportedGoal>, val alreadyPresent: Set<String>) : UiState {
        val newCount get() = goals.count { it.id !in alreadyPresent }
    }

    data object Adding : UiState
}

@Composable
private fun SharedGoalCard(goal: ExportedGoal, alreadyPresent: Boolean, today: LocalDate) {
    val resources = LocalContext.current.resources
    val state = CountdownEngine.compute(goal.goal, today)
    val accent = goal.color?.let { GoalColors.of(it, isSystemInDarkTheme()) } ?: MaterialTheme.colorScheme.primary
    val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = titleWithEmoji(goal.goal.title, goal.emoji),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(GoalLabels.status(resources, state), style = MaterialTheme.typography.headlineSmall, color = accent)
            val target = goal.goal.targetDate
            Text(
                text = if (goal.goal.kind == GoalKind.COUNTDOWN && target != null) {
                    stringResource(R.string.shared_dates_countdown, dates.format(goal.goal.startDate), dates.format(target))
                } else {
                    stringResource(R.string.shared_dates_since, dates.format(goal.goal.startDate))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (alreadyPresent) {
                Text(
                    stringResource(R.string.shared_already_present),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}
