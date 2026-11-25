package app.goalwidgets.core

import java.time.LocalDate

/** Type de jalon. `wire` est la valeur utilisée dans `shared-cases/planner.json`. */
enum class MilestoneKind(val wire: String) {
    /** « depuis » : n-ième mois-anniversaire (`value` = mois écoulés au total, jamais multiple de 12). */
    MONTH_ANNIVERSARY("month_anniversary"),
    /** « depuis » : anniversaire annuel (`value` = années). */
    YEAR_ANNIVERSARY("year_anniversary"),
    /** Compte à rebours : rappel mensuel (`value` = mois restants, ≥ 2). */
    MONTHLY_REMINDER("monthly_reminder"),
    /** Compte à rebours : J-30, J-7, J-1 (`value` = 30, 7 ou 1). */
    DAYS_BEFORE("days_before"),
    /** Compte à rebours : jour J (`value` = 0). */
    TARGET_DAY("target_day");

    companion object {
        fun fromWire(value: String): MilestoneKind =
            entries.firstOrNull { it.wire == value } ?: error("MilestoneKind inconnu : $value")
    }
}

/**
 * Une notification à programmer. Aucune heure ni texte : la couche app ajoute l'heure d'envoi
 * (réglage global) et construit le texte à partir de `kind` + `value` (ressources).
 */
data class Milestone(
    val goalId: String,
    val date: LocalDate,
    val kind: MilestoneKind,
    val value: Int,
) {
    /** Identifiant stable `<goalId>:<kind>:<AAAA-MM-JJ>` : reprogrammer ne crée pas de doublon. */
    val id: String get() = "$goalId:${kind.wire}:$date"

    /** Force : si deux jalons d'un même objectif tombent le même jour, le plus fort l'emporte. */
    val strength: Int
        get() = when (kind) {
            MilestoneKind.TARGET_DAY -> 100
            MilestoneKind.DAYS_BEFORE -> when (value) { 1 -> 90; 7 -> 80; else -> 70 }
            MilestoneKind.YEAR_ANNIVERSARY -> 60
            MilestoneKind.MONTHLY_REMINDER -> 50
            MilestoneKind.MONTH_ANNIVERSARY -> 40
        }
}

/**
 * Planificateur de jalons (pur) : objectif + date du jour + horizon → notifications à venir.
 * Même API et mêmes règles que `GoalCore.MilestonePlanner` (Swift, t09), figées par
 * `shared-cases/planner.json` :
 * - fenêtre `today ≤ date ≤ today + horizonDays` (bornes incluses) ; objectif désactivé → rien ;
 * - « depuis » : `départ.plusMonths(n)` pour n ≥ 1 (repli fin de mois, toujours depuis le départ) ;
 *   n multiple de 12 → anniversaire annuel ;
 * - compte à rebours (cible T) : J-30, J-7, J-1, jour J, et rappels mensuels `T.minusMonths(m)`
 *   pour m ≥ 2 (J-30 prend le relais du dernier mois) ; rien après T ;
 * - aucune notification le jour de départ ni avant ;
 * - au plus un jalon par jour (le plus fort l'emporte) ; résultat trié par date.
 */
object MilestonePlanner {

    fun plan(
        goalId: String,
        goal: Goal,
        notificationsEnabled: Boolean,
        today: LocalDate,
        horizonDays: Int,
    ): List<Milestone> {
        if (!notificationsEnabled || horizonDays < 0) return emptyList()
        val last = today.plusDays(horizonDays.toLong())
        val candidates = when (goal.kind) {
            GoalKind.SINCE -> sinceCandidates(goalId, goal.startDate, last)
            GoalKind.COUNTDOWN -> {
                val target = goal.targetDate ?: return emptyList()
                countdownCandidates(goalId, target, goal.startDate, today)
            }
        }
        return candidates
            .filter { it.date >= today && it.date <= last && it.date > goal.startDate }
            .groupBy { it.date }
            .values
            .map { sameDay -> sameDay.maxBy { it.strength } }
            .sortedBy { it.date }
    }

    /**
     * Les [limit] jalons les plus proches, tous objectifs confondus (plafond d'alarmes).
     * Ordre : date, puis jalon le plus fort, puis identifiant (déterministe).
     */
    fun closest(milestones: List<Milestone>, limit: Int): List<Milestone> =
        milestones
            .sortedWith(compareBy<Milestone> { it.date }.thenByDescending { it.strength }.thenBy { it.id })
            .take(limit.coerceAtLeast(0))

    private fun sinceCandidates(goalId: String, start: LocalDate, last: LocalDate): List<Milestone> {
        val result = mutableListOf<Milestone>()
        var n = 1L
        while (true) {
            val date = start.plusMonths(n)
            if (date > last) break
            result += if (n % 12 == 0L) {
                Milestone(goalId, date, MilestoneKind.YEAR_ANNIVERSARY, (n / 12).toInt())
            } else {
                Milestone(goalId, date, MilestoneKind.MONTH_ANNIVERSARY, n.toInt())
            }
            n++
        }
        return result
    }

    // Jalons d'un compte à rebours ; le filtrage final (fenêtre, départ) est fait par `plan`.
    private fun countdownCandidates(
        goalId: String,
        target: LocalDate,
        start: LocalDate,
        today: LocalDate,
    ): List<Milestone> {
        val result = mutableListOf(
            Milestone(goalId, target, MilestoneKind.TARGET_DAY, 0),
            Milestone(goalId, target.minusDays(1), MilestoneKind.DAYS_BEFORE, 1),
            Milestone(goalId, target.minusDays(7), MilestoneKind.DAYS_BEFORE, 7),
            Milestone(goalId, target.minusDays(30), MilestoneKind.DAYS_BEFORE, 30),
        )
        // T − m mois recule quand m grandit : on s'arrête avant aujourd'hui ou au départ.
        var m = 2L
        while (true) {
            val date = target.minusMonths(m)
            if (date < today || date <= start) break
            result += Milestone(goalId, date, MilestoneKind.MONTHLY_REMINDER, m.toInt())
            m++
        }
        return result
    }
}
