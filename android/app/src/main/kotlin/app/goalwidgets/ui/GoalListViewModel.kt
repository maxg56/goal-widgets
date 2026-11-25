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
data class StoredGoal(val id: Long, val goal: Goal, val notificationsEnabled: Boolean = false)

/** Toutes les écritures d'objectifs de l'app passent par ici (création, modification, suppression). */
class GoalListViewModel(
    private val dao: GoalDao,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    /**
     * Appelé après chaque écriture (ajout, modification, suppression) : rafraîchit les widgets
     * et reprogramme les notifications de jalons.
     */
    private val onGoalsChanged: suspend () -> Unit = {},
) : ViewModel() {

    /** `null` tant que la base n'a pas répondu. */
    val goals: StateFlow<List<StoredGoal>?> = dao.observeAll()
        .map { rows -> rows.map { StoredGoal(it.id, it.toGoal(), it.notificationsEnabled) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun add(goal: Goal, notificationsEnabled: Boolean = false) {
        viewModelScope.launch {
            dao.insert(GoalEntity.from(goal, createdAtMillis = clockMillis(), notificationsEnabled = notificationsEnabled))
            onGoalsChanged()
        }
    }

    /**
     * Modifie l'objectif [id] (même id, date de création conservée). [notificationsEnabled] :
     * nouvel état de l'interrupteur de notifications, `null` = inchangé.
     */
    fun update(id: Long, goal: Goal, notificationsEnabled: Boolean? = null) {
        viewModelScope.launch {
            dao.updateContent(
                id = id,
                title = goal.title,
                kind = goal.kind.wire,
                startEpochDay = goal.startDate.toEpochDay(),
                targetEpochDay = goal.targetDate?.toEpochDay(),
            )
            if (notificationsEnabled != null) dao.setNotificationsEnabled(id, notificationsEnabled)
            onGoalsChanged()
        }
    }

    /** « Continuer en compteur depuis » : l'objectif garde son id, devient « depuis » sa cible. */
    fun continueAsSince(stored: StoredGoal) = update(stored.id, stored.goal.continueAsSince())

    fun delete(id: Long) {
        viewModelScope.launch {
            dao.deleteById(id)
            onGoalsChanged()
        }
    }
}
