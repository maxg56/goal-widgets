package app.goalwidgets.core

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * Série terminée d'un compteur « depuis » remis à zéro : du [start] (départ de la série) au [end]
 * (jour de la remise à zéro). Sa durée est l'écart en jours entre les deux (0 si remise le jour même).
 */
data class PastStreak(val start: LocalDate, val end: LocalDate) {
    val days: Int get() = ChronoUnit.DAYS.between(start, end).toInt()
}

/** Ce qu'affichent le détail et le widget d'un compteur « série ». */
data class StreakSummary(
    /** Jours de la série en cours (0 avant le départ). */
    val currentDays: Int,
    /** Plus longue série, série en cours comprise. */
    val bestDays: Int,
    /** Nombre de remises à zéro (= séries terminées). */
    val resetCount: Int,
    /**
     * La série en cours est le record : au moins une remise à zéro, au moins 1 jour, et aucune
     * série passée plus longue (égalité comprise).
     */
    val isRecord: Boolean,
)

/** Raison pour laquelle une date de remise à zéro est refusée. */
enum class ResetDateError(val code: String) {
    /** Avant le départ de la série en cours. */
    BEFORE_START("beforeStart"),

    /** Dans le futur. */
    IN_FUTURE("inFuture"),
}

/**
 * Remise à zéro des compteurs « depuis » marqués « série » : la série en cours est archivée dans
 * l'historique et le compteur repart de la date choisie (aujourd'hui par défaut). Comportement figé
 * par `shared-cases/streaks.json`.
 */
object Streaks {

    /** Objectif remis à zéro et historique complété (ordre chronologique). */
    data class Reset(val goal: Goal, val history: List<PastStreak>)

    /** `null` si [on] est une date de remise à zéro valide pour [goal] ce jour-là. */
    fun validate(goal: Goal, on: LocalDate, today: LocalDate): ResetDateError? = when {
        on.isAfter(today) -> ResetDateError.IN_FUTURE
        on.isBefore(goal.startDate) -> ResetDateError.BEFORE_START
        else -> null
    }

    /**
     * Remet [goal] à zéro le jour [on] : la série `startDate → on` rejoint [history] et le compteur
     * repart de [on]. Seul un compteur « depuis » se remet à zéro ; [on] doit passer [validate].
     */
    fun reset(goal: Goal, history: List<PastStreak>, on: LocalDate, today: LocalDate): Reset {
        require(goal.kind == GoalKind.SINCE) { "Seul un compteur « depuis » se remet à zéro" }
        val error = validate(goal, on, today)
        require(error == null) { "Date de remise à zéro invalide : ${error?.code}" }
        return Reset(goal.copy(startDate = on), history + PastStreak(goal.startDate, on))
    }

    fun summary(goal: Goal, history: List<PastStreak>, today: LocalDate): StreakSummary {
        val current = ChronoUnit.DAYS.between(goal.startDate, today).toInt().coerceAtLeast(0)
        val bestPast = history.maxOfOrNull { it.days } ?: 0
        return StreakSummary(
            currentDays = current,
            bestDays = maxOf(current, bestPast),
            resetCount = history.size,
            isRecord = history.isNotEmpty() && current > 0 && current >= bestPast,
        )
    }

    /** Historique stocké en texte : `AAAA-MM-JJ/AAAA-MM-JJ` séparés par `;`, ordre chronologique. */
    fun encode(history: List<PastStreak>): String = history.joinToString(";") { "${it.start}/${it.end}" }

    /** Inverse de [encode] ; une entrée illisible est ignorée (jamais d'exception). */
    fun decode(raw: String?): List<PastStreak> =
        raw.orEmpty().split(';').mapNotNull { entry ->
            val parts = entry.trim().split('/')
            if (parts.size != 2) return@mapNotNull null
            try {
                val start = LocalDate.parse(parts[0])
                val end = LocalDate.parse(parts[1])
                if (end.isBefore(start)) null else PastStreak(start, end)
            } catch (e: DateTimeParseException) {
                null
            }
        }
}

/**
 * Bouton ↺ du widget, sans boîte de dialogue possible : un premier appui « arme » le bouton
 * (« Confirmer ? »), un second appui dans les [WINDOW_MILLIS] remet à zéro. Passé ce délai, l'appui
 * arme de nouveau.
 */
object WidgetResetTap {
    const val WINDOW_MILLIS = 5_000L

    enum class Outcome { ARM, RESET }

    /** [armedAtMillis] : instant du premier appui, `null` si le bouton n'est pas armé. */
    fun onTap(armedAtMillis: Long?, nowMillis: Long): Outcome =
        if (isArmed(armedAtMillis, nowMillis)) Outcome.RESET else Outcome.ARM

    fun isArmed(armedAtMillis: Long?, nowMillis: Long): Boolean =
        armedAtMillis != null && nowMillis >= armedAtMillis && nowMillis - armedAtMillis < WINDOW_MILLIS
}
