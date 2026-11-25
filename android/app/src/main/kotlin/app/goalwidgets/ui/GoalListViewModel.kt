package app.goalwidgets.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.goalwidgets.core.Goal
import app.goalwidgets.data.GoalDao
import app.goalwidgets.data.GoalEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Objectif stocké, prêt pour l'affichage (le calcul se fait avec la date du jour, côté écran). */
data class StoredGoal(val id: Long, val goal: Goal)

class GoalListViewModel(
    private val dao: GoalDao,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    /** `null` tant que la base n'a pas répondu. */
    val goals: StateFlow<List<StoredGoal>?> = dao.observeAll()
        .map { rows -> rows.map { StoredGoal(it.id, it.toGoal()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun add(goal: Goal) {
        viewModelScope.launch { dao.insert(GoalEntity.from(goal, createdAtMillis = clockMillis())) }
    }
}
