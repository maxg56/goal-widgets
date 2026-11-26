package app.goalwidgets.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Graduation de la frise : 1er d'un mois ; [yearStart] pour un 1er janvier (graduation marquée). */
data class StripTick(val date: LocalDate, val position: Double, val yearStart: Boolean)

/**
 * Frise horizontale d'un objectif (bas du détail, mini frise du widget) : de [start] à [end], positions
 * 0.0–1.0 proportionnelles aux jours.
 * - Compte à rebours : départ → cible, pleine jusqu'à [target].
 * - « Depuis » ou cible dépassée : prolongée après aujourd'hui d'un dixième de l'étendue (au moins
 *   10 jours), en pointillés après [solidEnd].
 * Le départ futur d'un objectif ([goalStart] > aujourd'hui) est placé à [startPosition] ; la frise
 * commence alors aujourd'hui.
 */
data class GoalStrip(
    val start: LocalDate,
    val end: LocalDate,
    val goalStart: LocalDate,
    val target: LocalDate?,
    val today: LocalDate,
    val ticks: List<StripTick>,
) {
    private val totalDays: Long get() = ChronoUnit.DAYS.between(start, end).coerceAtLeast(1)

    /** Fin de la partie pleine : cible du compte à rebours, aujourd'hui pour « depuis ». */
    val solidEnd: LocalDate get() = target ?: today

    fun position(date: LocalDate): Double = ChronoUnit.DAYS.between(start, date).toDouble() / totalDays

    /** Jour le plus proche d'une position (appui sur la frise), borné à l'étendue. */
    fun dateAt(position: Double): LocalDate =
        start.plusDays(Math.round(position.coerceIn(0.0, 1.0) * totalDays))

    val startPosition: Double get() = position(goalStart)
    val todayPosition: Double get() = position(today)
    val targetPosition: Double? get() = target?.let(::position)

    companion object {
        private const val MIN_MARGIN_DAYS = 10L

        fun compute(goal: Goal, today: LocalDate): GoalStrip {
            val start = minOf(goal.startDate, today)
            val target = when (goal.kind) {
                GoalKind.COUNTDOWN -> maxOf(goal.startDate, requireNotNull(goal.targetDate) { "Compte à rebours sans cible" })
                GoalKind.SINCE -> null
            }
            val baseEnd = target ?: today
            val end = if (target == null || today >= baseEnd) {
                val spanDays = ChronoUnit.DAYS.between(start, maxOf(baseEnd, today)).coerceAtLeast(1)
                maxOf(baseEnd, today).plusDays(maxOf(MIN_MARGIN_DAYS, spanDays / 10))
            } else {
                baseEnd
            }
            val total = ChronoUnit.DAYS.between(start, end).coerceAtLeast(1).toDouble()
            val ticks = mutableListOf<StripTick>()
            var month = start.withDayOfMonth(1).plusMonths(1)
            while (month <= end) {
                ticks += StripTick(month, ChronoUnit.DAYS.between(start, month) / total, month.monthValue == 1)
                month = month.plusMonths(1)
            }
            return GoalStrip(start, end, goal.startDate, target, today, ticks)
        }

        /**
         * Une graduation sur combien porte une étiquette, pour qu'elles soient espacées d'au moins
         * [minSpacing] quand un mois mesure [monthWidth] (mêmes unités) : 1, 2, 3, 6 ou 12.
         * Les 1er janvier sont toujours étiquetés (année).
         */
        fun labelStep(monthWidth: Double, minSpacing: Double): Int =
            listOf(1, 2, 3, 6).firstOrNull { it * monthWidth >= minSpacing } ?: 12
    }
}
