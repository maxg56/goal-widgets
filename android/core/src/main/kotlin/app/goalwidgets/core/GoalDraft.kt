package app.goalwidgets.core

import java.time.LocalDate

/**
 * Saisie en cours d'un objectif de n'importe quel type (création ou modification).
 * - `COUNTDOWN` : mêmes règles que [CountdownDraft] (titre, cible présente et après le départ).
 * - `SINCE` : titre exigé, date de départ ≤ [today] (aujourd'hui accepté) ; [targetDate] est
 *   ignorée (le formulaire la garde si l'utilisateur rebascule sur compte à rebours).
 */
data class GoalDraft(
    val kind: GoalKind,
    val title: String,
    val startDate: LocalDate,
    val targetDate: LocalDate?,
    /** Date du jour, pour refuser une date « depuis » future. */
    val today: LocalDate,
) {
    fun errors(): Set<DraftError> = when (kind) {
        GoalKind.COUNTDOWN -> asCountdown().errors()
        GoalKind.SINCE -> buildSet {
            if (title.isBlank()) add(DraftError.TITLE_BLANK)
            if (startDate.isAfter(today)) add(DraftError.START_IN_FUTURE)
        }
    }

    /** Objectif correspondant ; exige un brouillon sans erreur. */
    fun toGoal(): Goal = when (kind) {
        GoalKind.COUNTDOWN -> asCountdown().toGoal()
        GoalKind.SINCE -> {
            val problems = errors()
            require(problems.isEmpty()) { "Brouillon invalide : $problems" }
            Goal(kind = GoalKind.SINCE, startDate = startDate, targetDate = null, title = title.trim())
        }
    }

    private fun asCountdown() = CountdownDraft(title, startDate, targetDate)

    companion object {
        /** Brouillon pré-rempli pour modifier [goal]. */
        fun of(goal: Goal, today: LocalDate) = GoalDraft(goal.kind, goal.title, goal.startDate, goal.targetDate, today)
    }
}
