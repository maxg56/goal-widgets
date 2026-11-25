package app.goalwidgets.core

import java.time.LocalDate

/** Erreur de saisie du formulaire d'objectif (voir [CountdownDraft], [GoalDraft]). */
enum class DraftError {
    /** Titre vide ou composé uniquement d'espaces. */
    TITLE_BLANK,

    /** Aucune date cible choisie. */
    TARGET_MISSING,

    /** La date cible doit être strictement postérieure à la date de départ. */
    TARGET_NOT_AFTER_START,

    /** Objectif « depuis » : la date ne doit pas être dans le futur (aujourd'hui accepté). */
    START_IN_FUTURE,
}

/** Saisie en cours d'un compte à rebours, validée avant d'être enregistrée. */
data class CountdownDraft(
    val title: String,
    val startDate: LocalDate,
    val targetDate: LocalDate?,
) {
    fun errors(): Set<DraftError> = buildSet {
        if (title.isBlank()) add(DraftError.TITLE_BLANK)
        when {
            targetDate == null -> add(DraftError.TARGET_MISSING)
            !targetDate.isAfter(startDate) -> add(DraftError.TARGET_NOT_AFTER_START)
        }
    }

    /** Objectif correspondant ; exige un brouillon sans erreur. */
    fun toGoal(): Goal {
        val problems = errors()
        require(problems.isEmpty()) { "Brouillon invalide : $problems" }
        return Goal(
            kind = GoalKind.COUNTDOWN,
            startDate = startDate,
            targetDate = targetDate,
            title = title.trim(),
        )
    }
}
