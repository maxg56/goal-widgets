package app.goalwidgets.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.goalwidgets.core.ExportedGoal
import app.goalwidgets.core.Goal
import app.goalwidgets.core.GoalExport
import app.goalwidgets.core.GoalOrder
import app.goalwidgets.core.GoalStyle
import app.goalwidgets.core.ImportError
import app.goalwidgets.core.ImportPlan
import app.goalwidgets.core.SortKey
import app.goalwidgets.data.GoalDao
import app.goalwidgets.data.GoalEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

/** Objectif stocké, prêt pour l'affichage (le calcul se fait avec la date du jour, côté écran). */
data class StoredGoal(
    val id: Long,
    val goal: Goal,
    val style: GoalStyle = GoalStyle.NONE,
    /** UUID portable (format d'export) ; départage aussi les égalités de tri. */
    val uid: String = "",
    val createdAtMillis: Long = 0,
    /** Interrupteur de notifications de jalons (t08). */
    val notificationsEnabled: Boolean = false,
) {
    val sortKey: SortKey get() = SortKey(uid, goal, Instant.ofEpochMilli(createdAtMillis))

    companion object {
        fun from(entity: GoalEntity) = StoredGoal(
            id = entity.id,
            goal = entity.toGoal(),
            style = entity.style(),
            uid = entity.uid,
            createdAtMillis = entity.createdAtMillis,
            notificationsEnabled = entity.notificationsEnabled,
        )
    }
}

/** Issue d'un import : objectifs ajoutés / ignorés, ou fichier refusé (rien n'est écrit). */
sealed interface ImportOutcome {
    data class Imported(val plan: ImportPlan) : ImportOutcome
    data class Rejected(val error: ImportError) : ImportOutcome
}

/** Toutes les écritures d'objectifs de l'app passent par ici (création, modification, suppression, import). */
class GoalListViewModel(
    private val dao: GoalDao,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    /**
     * Appelé après chaque écriture (ajout, modification, suppression, import) : rafraîchit les
     * widgets et reprogramme les notifications de jalons.
     */
    private val onGoalsChanged: suspend () -> Unit = {},
    initialOrder: GoalOrder = GoalOrder.DEFAULT,
    /** Mémorise le tri choisi (préférences). */
    private val onOrderChanged: (GoalOrder) -> Unit = {},
) : ViewModel() {

    /** `null` tant que la base n'a pas répondu. Non trié : l'écran trie selon [order] et la date du jour. */
    val goals: StateFlow<List<StoredGoal>?> = dao.observeAll()
        .map { rows -> rows.map(StoredGoal::from) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _order = MutableStateFlow(initialOrder)
    val order: StateFlow<GoalOrder> = _order.asStateFlow()

    fun setOrder(order: GoalOrder) {
        _order.value = order
        onOrderChanged(order)
    }

    fun add(goal: Goal, style: GoalStyle = GoalStyle.NONE, notificationsEnabled: Boolean = false) {
        viewModelScope.launch {
            dao.insert(
                GoalEntity.from(
                    goal,
                    createdAtMillis = clockMillis(),
                    style = style,
                    notificationsEnabled = notificationsEnabled,
                ),
            )
            onGoalsChanged()
        }
    }

    /**
     * Modifie l'objectif [id] (même id, même uid, date de création conservée).
     * [notificationsEnabled] : nouvel état de l'interrupteur de notifications, `null` = inchangé.
     */
    fun update(id: Long, goal: Goal, style: GoalStyle, notificationsEnabled: Boolean? = null) {
        viewModelScope.launch {
            // Une seule transaction : contenu + interrupteur de notifications.
            dao.updateGoal(
                id = id,
                title = goal.title,
                kind = goal.kind.wire,
                startEpochDay = goal.startDate.toEpochDay(),
                targetEpochDay = goal.targetDate?.toEpochDay(),
                color = style.color?.wire,
                emoji = style.emoji,
                notificationsEnabled = notificationsEnabled,
            )
            onGoalsChanged()
        }
    }

    /** « Continuer en compteur depuis » : l'objectif garde son id, devient « depuis » sa cible. */
    fun continueAsSince(stored: StoredGoal) = update(stored.id, stored.goal.continueAsSince(), stored.style)

    fun delete(id: Long) {
        viewModelScope.launch {
            dao.deleteById(id)
            onGoalsChanged()
        }
    }

    /** Contenu du fichier d'export (tous les objectifs) et leur nombre. */
    suspend fun exportText(): Pair<String, Int> {
        val goals: List<ExportedGoal> = dao.getAll().map(GoalEntity::toExported)
        return GoalExport.encode(goals, exportedAt = Instant.ofEpochMilli(clockMillis())) to goals.size
    }

    /** Importe [text] : fichier refusé en entier s'il est invalide, sinon ajout sans doublon. */
    suspend fun import(text: String): ImportOutcome =
        when (val decoded = GoalExport.decode(text)) {
            is GoalExport.Decoded.Failure -> ImportOutcome.Rejected(decoded.error)
            is GoalExport.Decoded.Success -> {
                val plan = dao.importGoals(decoded.goals)
                if (plan.toAdd.isNotEmpty()) onGoalsChanged()
                ImportOutcome.Imported(plan)
            }
        }
}
