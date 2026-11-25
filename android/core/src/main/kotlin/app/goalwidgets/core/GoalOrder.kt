package app.goalwidgets.core

import java.time.Instant
import java.time.LocalDate

/** Ce dont le tri a besoin pour un objectif ; [id] ne sert qu'à départager les égalités. */
data class SortKey(val id: String, val goal: Goal, val createdAt: Instant)

/** Tri de la liste des objectifs (cas partagés : `shared-cases/sort.json`). */
enum class GoalOrder(val wire: String) {
    /**
     * Date la plus proche : comptes à rebours à venir (cible ≥ aujourd'hui, la plus proche
     * d'abord), puis comptes à rebours atteints (le plus récemment atteint d'abord), puis
     * objectifs « depuis » (départ le plus récent d'abord).
     */
    CLOSEST_DATE("closestDate"),

    /** Création : le plus récemment créé d'abord. */
    CREATED("created");

    fun <T> sort(items: List<T>, today: LocalDate, key: (T) -> SortKey): List<T> {
        val byCreation = compareByDescending<SortKey> { it.createdAt }.thenBy { it.id }
        val comparator = when (this) {
            CREATED -> byCreation
            CLOSEST_DATE -> compareBy<SortKey> { group(it.goal, today) }
                .thenBy { distance(it.goal, today) }
                .then(byCreation)
        }
        return items.sortedWith { a, b -> comparator.compare(key(a), key(b)) }
    }

    private fun group(goal: Goal, today: LocalDate): Int {
        val target = goal.targetDate
        return when {
            goal.kind == GoalKind.SINCE || target == null -> 2
            target >= today -> 0
            else -> 1
        }
    }

    /** Écart en jours à la date pertinente (cible ou départ), croissant dans chaque groupe. */
    private fun distance(goal: Goal, today: LocalDate): Long {
        val date = if (goal.kind == GoalKind.COUNTDOWN) goal.targetDate ?: goal.startDate else goal.startDate
        return Math.abs(date.toEpochDay() - today.toEpochDay())
    }

    companion object {
        val DEFAULT = CLOSEST_DATE

        fun fromWire(value: String?): GoalOrder = entries.firstOrNull { it.wire == value } ?: DEFAULT
    }
}
