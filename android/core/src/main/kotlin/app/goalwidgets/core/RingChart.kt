package app.goalwidgets.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Unité d'un cap de l'anneau « depuis ». `wire` = valeur des cas partagés. */
enum class MilestoneUnit(val wire: String) { MONTH("month"), YEAR("year") }

/** Cap visé par l'anneau d'un objectif « depuis » : « 1 mois », « 1 an », « 7 ans »… */
data class RingMilestone(val unit: MilestoneUnit, val count: Int)

/**
 * Anneau de progression.
 * - Compte à rebours : [fraction]/[percent] = progression du moteur, [date] = cible.
 * - « Depuis » : progression vers le prochain cap [milestone] (1 mois, puis 1 an, puis chaque
 *   anniversaire), [date] = jour du cap. Caps = mois-anniversaires de [Breakdown.between] (repli fin de
 *   mois, comme les notifications) : un départ le 29 février fête ses ans le 28 février.
 *
 * [daysLeft] = jours jusqu'à [date] (0 une fois atteinte), [percent] = partie entière (calcul entier).
 */
data class RingChart(
    val fraction: Double,
    val percent: Int,
    val date: LocalDate,
    val daysLeft: Int,
    val milestone: RingMilestone?,
) {
    companion object {
        fun compute(goal: Goal, today: LocalDate): RingChart = when (goal.kind) {
            GoalKind.COUNTDOWN -> countdown(goal, today)
            GoalKind.SINCE -> since(goal.startDate, today)
        }

        private fun countdown(goal: Goal, today: LocalDate): RingChart {
            val state = CountdownEngine.compute(goal, today)
            val target = requireNotNull(goal.targetDate)
            return RingChart(
                fraction = state.progress ?: 0.0,
                percent = state.percent ?: 0,
                date = target,
                daysLeft = ChronoUnit.DAYS.between(today, target).toInt().coerceAtLeast(0),
                milestone = null,
            )
        }

        private fun since(start: LocalDate, today: LocalDate): RingChart {
            val months = if (today > start) Breakdown.between(start, today).let { it.years * 12 + it.months } else 0
            val (previous, next, milestone) = when {
                months < 1 -> Triple(start, start.plusMonths(1), RingMilestone(MilestoneUnit.MONTH, 1))
                months < 12 -> Triple(start, start.plusMonths(12), RingMilestone(MilestoneUnit.YEAR, 1))
                else -> {
                    val years = months / 12
                    Triple(
                        start.plusMonths(12L * years),
                        start.plusMonths(12L * (years + 1)),
                        RingMilestone(MilestoneUnit.YEAR, years + 1),
                    )
                }
            }
            val total = ChronoUnit.DAYS.between(previous, next)
            val done = ChronoUnit.DAYS.between(previous, today).coerceIn(0, total)
            return RingChart(
                fraction = done.toDouble() / total,
                percent = (done * 100 / total).toInt(),
                date = next,
                daysLeft = ChronoUnit.DAYS.between(today, next).toInt().coerceAtLeast(0),
                milestone = milestone,
            )
        }
    }
}
