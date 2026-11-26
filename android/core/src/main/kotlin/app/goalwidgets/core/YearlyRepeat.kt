package app.goalwidgets.core

import java.time.LocalDate

/**
 * Compte à rebours « chaque année » (anniversaire, fête, échéance annuelle) : une fois la cible passée,
 * il repart vers l'occurrence suivante. Le jour J reste affiché (« Aujourd'hui ») ; dès le lendemain,
 * la cible devient l'occurrence suivante et le départ l'occurrence passée — la progression mesure
 * donc l'année écoulée depuis la dernière fois.
 *
 * Les dates stockées sont réécrites ([roll]) : le moteur, les graphiques, les widgets et les
 * notifications n'ont rien de spécial à savoir. Un 29 février devient 28 février les années non
 * bissextiles (règle de `plusYears`).
 */
object YearlyRepeat {
    /**
     * Objectif à enregistrer si [goal] (compte à rebours annuel) doit avancer à la date [today], sinon
     * `null` (cible encore à venir ou aujourd'hui, ou objectif « depuis »).
     */
    fun roll(goal: Goal, today: LocalDate): Goal? {
        if (goal.kind != GoalKind.COUNTDOWN) return null
        val target = goal.targetDate ?: return null
        if (!target.isBefore(today)) return null
        var years = 1L
        while (target.plusYears(years).isBefore(today)) years++
        return goal.copy(startDate = target.plusYears(years - 1), targetDate = target.plusYears(years))
    }
}
