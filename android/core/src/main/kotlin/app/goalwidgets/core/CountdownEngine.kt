package app.goalwidgets.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class Phase(val wire: String) {
    UPCOMING("upcoming"),
}

/** État d'affichage d'un objectif pour une date du jour donnée. */
data class DisplayState(
    val phase: Phase,
    /** Nombre de jours (toujours ≥ 0). */
    val days: Int,
    /** Progression 0.0–1.0, uniquement pour un compte à rebours. */
    val progress: Double?,
)

/** Moteur pur : objectif + date du jour → état d'affichage. Aucune dépendance OS. */
object CountdownEngine {
    fun compute(goal: Goal, today: LocalDate): DisplayState {
        val target = requireNotNull(goal.targetDate) { "Un compte à rebours exige une date cible" }
        val total = ChronoUnit.DAYS.between(goal.startDate, target).toDouble()
        val elapsed = ChronoUnit.DAYS.between(goal.startDate, today).toDouble()
        return DisplayState(
            phase = Phase.UPCOMING,
            days = ChronoUnit.DAYS.between(today, target).toInt(),
            progress = elapsed / total,
        )
    }
}
