package app.goalwidgets

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
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
import app.goalwidgets.ui.CountdownFormScreen
import app.goalwidgets.ui.GoalListScreen
import app.goalwidgets.ui.GoalListViewModel
import app.goalwidgets.ui.GoalWidgetsTheme
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                var creating by rememberSaveable { mutableStateOf(false) }
                if (creating) {
                    CountdownFormScreen(
                        today = today,
                        onSave = { vm.add(it); creating = false },
                        onCancel = { creating = false },
                    )
                } else {
                    GoalListScreen(goals = goals, today = today, onAdd = { creating = true })
                }
            }
        }
    }

    private companion object {
        val factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as GoalWidgetsApplication
                GoalListViewModel(app.database.goalDao())
            }
        }
    }
}
