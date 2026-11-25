package app.goalwidgets

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.goalwidgets.ui.GoalFormScreen
import app.goalwidgets.ui.GoalListScreen
import app.goalwidgets.ui.GoalListViewModel
import app.goalwidgets.ui.GoalWidgetsTheme
import app.goalwidgets.widget.GoalWidgets
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Ouvert depuis un widget : objectif à montrer dans la liste.
        val focusGoalId = intent.getLongExtra(EXTRA_GOAL_ID, -1L).takeIf { it >= 0 }
        setContent {
            GoalWidgetsTheme {
                val vm: GoalListViewModel = viewModel(factory = factory)
                val goals by vm.goals.collectAsStateWithLifecycle()
                // Date du jour locale (fuseau du téléphone), relue à chaque retour au premier plan.
                var today by remember { mutableStateOf(LocalDate.now()) }
                LifecycleResumeEffect(Unit) {
                    today = LocalDate.now()
                    onPauseOrDispose { }
                }
                // Écran affiché : liste, création (NEW_GOAL) ou modification de l'objectif d'id donné.
                var editing by rememberSaveable { mutableStateOf<Long?>(null) }
                val current = editing
                val existing = goals?.firstOrNull { it.id == current }
                when {
                    current == NEW_GOAL -> GoalFormScreen(
                        existing = null,
                        today = today,
                        onSave = { vm.add(it); editing = null },
                        onCancel = { editing = null },
                    )
                    current != null && existing != null -> key(current) {
                        GoalFormScreen(
                            existing = existing,
                            today = today,
                            onSave = { vm.update(current, it); editing = null },
                            onCancel = { editing = null },
                            onDelete = { vm.delete(current); editing = null },
                            onContinueAsSince = { vm.continueAsSince(existing); editing = null },
                        )
                    }
                    // Objectif introuvable (supprimé entre-temps) ou liste pas encore chargée.
                    else -> GoalListScreen(
                        goals = goals,
                        today = today,
                        onAdd = { editing = NEW_GOAL },
                        onOpen = { editing = it.id },
                        onContinueAsSince = vm::continueAsSince,
                        focusGoalId = focusGoalId,
                    )
                }
            }
        }
    }

    companion object {
        /** Id (Long) de l'objectif à montrer à l'ouverture (appui sur un widget). */
        const val EXTRA_GOAL_ID = "app.goalwidgets.extra.GOAL_ID"

        /** Valeur de `editing` pour un nouvel objectif (les id Room commencent à 1). */
        private const val NEW_GOAL = -1L

        private val factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as GoalWidgetsApplication
                GoalListViewModel(app.database.goalDao(), onGoalsChanged = { GoalWidgets.refreshAll(app) })
            }
        }
    }
}
