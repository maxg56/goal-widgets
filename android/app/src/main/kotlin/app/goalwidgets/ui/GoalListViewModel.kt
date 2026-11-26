package app.goalwidgets.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.goalwidgets.core.ExportedGoal
import app.goalwidgets.core.Goal
import app.goalwidgets.core.GoalExport
import app.goalwidgets.core.GoalKind
import app.goalwidgets.core.GoalOrder
import app.goalwidgets.core.GoalStyle
import app.goalwidgets.core.ImportError
import app.goalwidgets.core.ImportPlan
import app.goalwidgets.core.PastStreak
import app.goalwidgets.core.SortKey
import app.goalwidgets.core.StreakSummary
import app.goalwidgets.core.Streaks
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
import java.time.LocalDate

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
    /** Option « série » (remise à zéro), quel que soit le type ; voir [isStreak]. */
    val streak: Boolean = false,
    /** Séries terminées, ordre chronologique. */
    val history: List<PastStreak> = emptyList(),
    /** Compte à rebours « chaque année ». */
    val repeatYearly: Boolean = false,
    /** Archivé : section « Archivés » de l'accueil, sans notification. */
    val archived: Boolean = false,
) {
    /** Options du formulaire telles qu'enregistrées. */
    val options: GoalOptions get() = GoalOptions(notificationsEnabled, streak, repeatYearly)

    /** Remise à zéro proposée : compteur « depuis » marqué « série ». */
    val isStreak: Boolean get() = streak && goal.kind == GoalKind.SINCE

    fun streakSummary(today: LocalDate): StreakSummary = Streaks.summary(goal, history, today)

    val sortKey: SortKey get() = SortKey(uid, goal, Instant.ofEpochMilli(createdAtMillis))

    companion object {
        fun from(entity: GoalEntity) = StoredGoal(
            id = entity.id,
            goal = entity.toGoal(),
            style = entity.style(),
            uid = entity.uid,
            createdAtMillis = entity.createdAtMillis,
            notificationsEnabled = entity.notificationsEnabled,
            streak = entity.streak,
            history = entity.history(),
            repeatYearly = entity.repeatYearly,
            archived = entity.archived,
        )
    }
}

/**
 * Options d'un objectif réglées dans le formulaire : notifications de jalons, compteur « série »
 * (remise à zéro, compteur « depuis »), répétition « chaque année » (compte à rebours).
 */
data class GoalOptions(
    val notificationsEnabled: Boolean = false,
    val streak: Boolean = false,
    val repeatYearly: Boolean = false,
)

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

    fun add(goal: Goal, style: GoalStyle = GoalStyle.NONE, options: GoalOptions = GoalOptions()) {
        viewModelScope.launch {
            dao.insert(
                GoalEntity.from(
                    goal,
                    createdAtMillis = clockMillis(),
                    style = style,
                    notificationsEnabled = options.notificationsEnabled,
                    streak = options.streak,
                    repeatYearly = options.repeatYearly,
                ),
            )
            // « Chaque année » avec une date déjà passée : directement l'occurrence suivante.
            dao.rollYearly(LocalDate.now())
            onGoalsChanged()
        }
    }

    /** Copie de [stored] (« Dupliquer ») : même contenu et mêmes options, titre suffixé, nouvel uid. */
    fun duplicate(stored: StoredGoal, title: String) =
        add(stored.goal.copy(title = title), stored.style, stored.options)

    /** Archive l'objectif [id] (ou le sort de l'archive). */
    fun setArchived(id: Long, archived: Boolean) {
        viewModelScope.launch {
            dao.setArchived(id, archived)
            onGoalsChanged()
        }
    }

    /** Comptes à rebours « chaque année » passés : avancés à l'occurrence suivante. */
    fun rollYearly(today: LocalDate) {
        viewModelScope.launch {
            if (dao.rollYearly(today) > 0) onGoalsChanged()
        }
    }

    /**
     * Modifie l'objectif [id] (même id, même uid, date de création conservée).
     * [options] : nouvelles options du formulaire, `null` = inchangées.
     */
    fun update(id: Long, goal: Goal, style: GoalStyle, options: GoalOptions? = null) {
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
                chartUnit = style.chartUnit?.wire,
                notificationsEnabled = options?.notificationsEnabled,
                streak = options?.streak,
                repeatYearly = options?.let { it.repeatYearly && goal.kind == GoalKind.COUNTDOWN },
            )
            dao.rollYearly(LocalDate.now())
            onGoalsChanged()
        }
    }

    /**
     * Remise à zéro du compteur « série » [id] le jour [on] (choisi dans la confirmation) : la série en
     * cours est archivée, le compteur repart de [on]. Sans effet si la date est refusée.
     */
    fun resetStreak(id: Long, on: LocalDate, today: LocalDate) {
        viewModelScope.launch {
            if (dao.resetStreak(id, on, today)) onGoalsChanged()
        }
    }

    /** « Continuer en compteur depuis » : l'objectif garde son id, devient « depuis » sa cible. */
    fun continueAsSince(stored: StoredGoal) =
        update(stored.id, stored.goal.continueAsSince(), stored.style, stored.options.copy(repeatYearly = false))

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
