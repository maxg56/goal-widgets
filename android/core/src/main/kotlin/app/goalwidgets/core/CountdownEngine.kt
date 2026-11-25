package app.goalwidgets.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Phase d'un objectif à une date donnée. `wire` = valeur du jeu de cas partagé. */
enum class Phase(val wire: String) {
    /** Cible dans le futur : `days` = jours restants. */
    UPCOMING("upcoming"),

    /** Jour J : `days` = 0. */
    TODAY("today"),

    /** Cible dépassée : `days` = jours écoulés depuis la cible. */
    REACHED("reached"),

    /** Objectif « depuis » : `days` = jours écoulés depuis la date (0 si elle est future). */
    ELAPSED("elapsed"),
}

/** État d'affichage d'un objectif pour une date du jour donnée. */
data class DisplayState(
    val phase: Phase,
    /** Nombre de jours (toujours ≥ 0). */
    val days: Int,
    /** Progression 0.0–1.0, uniquement pour un compte à rebours. */
    val progress: Double?,
    /**
     * Pourcentage affiché 0–100, uniquement pour un compte à rebours : partie entière
     * (arrondi vers le bas, calcul entier) de la progression × 100. Vaut 100 seulement
     * à partir du jour J.
     */
    val percent: Int?,
    /**
     * Décomposition années/mois/jours du temps écoulé, uniquement pour un objectif « depuis »
     * (null pour un compte à rebours). Voir [Breakdown.between].
     */
    val breakdown: Breakdown? = null,
)

/** Durée lisible « 1 an 2 mois 3 jours » (chaque champ ≥ 0, `months` < 12). */
data class Breakdown(val years: Int, val months: Int, val days: Int) {
    companion object {
        /**
         * Décomposition de [start] à [end] (inclus, `end` ≥ `start`) par mois-anniversaires.
         *
         * Le k-ième mois-anniversaire de `start` est `start.plusMonths(k)`, ramené au dernier
         * jour du mois quand le jour n'existe pas (31 janvier + 1 mois = 28/29 février ;
         * 29 février + 12 mois = 28 février). On prend le plus grand k dont l'anniversaire
         * est ≤ `end` : `years = k / 12`, `months = k % 12`, `days` = jours de cet
         * anniversaire à `end`. C'est la même règle de repli que les notifications de
         * mois-anniversaire, donc « 1 mois » s'affiche le jour de la notification.
         */
        fun between(start: LocalDate, end: LocalDate): Breakdown {
            require(!end.isBefore(start)) { "end ($end) avant start ($start)" }
            var months = (end.year - start.year) * 12 + (end.monthValue - start.monthValue)
            if (start.plusMonths(months.toLong()).isAfter(end)) months -= 1
            val anniversary = start.plusMonths(months.toLong())
            return Breakdown(
                years = months / 12,
                months = months % 12,
                days = ChronoUnit.DAYS.between(anniversary, end).toInt(),
            )
        }
    }
}

/** Moteur pur : objectif + date du jour → état d'affichage. Aucune dépendance OS. */
object CountdownEngine {
    fun compute(goal: Goal, today: LocalDate): DisplayState = when (goal.kind) {
        GoalKind.COUNTDOWN -> countdown(goal, today)
        GoalKind.SINCE -> since(goal, today)
    }

    private fun since(goal: Goal, today: LocalDate): DisplayState {
        // Date « depuis » future (refusée par le formulaire, mais tolérée) : 0 jour, jamais négatif.
        val end = maxOf(today, goal.startDate)
        return DisplayState(
            phase = Phase.ELAPSED,
            days = ChronoUnit.DAYS.between(goal.startDate, end).toInt(),
            progress = null,
            percent = null,
            breakdown = Breakdown.between(goal.startDate, end),
        )
    }

    private fun countdown(goal: Goal, today: LocalDate): DisplayState {
        val target = requireNotNull(goal.targetDate) { "Un compte à rebours exige une date cible" }
        val total = ChronoUnit.DAYS.between(goal.startDate, target)
        val elapsed = ChronoUnit.DAYS.between(goal.startDate, today)
        val progress: Double
        val percent: Int
        if (total <= 0) {
            // Période vide (départ = cible) : 0 % avant la cible, 100 % à partir d'elle.
            val reached = today >= target
            progress = if (reached) 1.0 else 0.0
            percent = if (reached) 100 else 0
        } else {
            val clamped = elapsed.coerceIn(0, total)
            progress = clamped.toDouble() / total
            percent = (clamped * 100 / total).toInt()
        }
        val remaining = ChronoUnit.DAYS.between(today, target).toInt()
        val phase = when {
            remaining > 0 -> Phase.UPCOMING
            remaining == 0 -> Phase.TODAY
            else -> Phase.REACHED
        }
        return DisplayState(
            phase = phase,
            days = abs(remaining),
            progress = progress,
            percent = percent,
        )
    }
}
