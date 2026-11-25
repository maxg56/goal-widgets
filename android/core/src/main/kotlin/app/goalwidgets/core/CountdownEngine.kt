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
)

/** Moteur pur : objectif + date du jour → état d'affichage. Aucune dépendance OS. */
object CountdownEngine {
    fun compute(goal: Goal, today: LocalDate): DisplayState {
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
