package app.goalwidgets.core

import java.time.LocalDate

/** Ce qu'affiche une instance de widget, résolu depuis l'objectif qui lui est associé. */
sealed interface WidgetContent {
    /** Aucun objectif choisi pour ce widget. */
    data object NotConfigured : WidgetContent

    /** Objectif trouvé : titre et état calculé par [CountdownEngine] pour la date du jour. */
    data class Showing(val goalId: Long, val title: String, val state: DisplayState) : WidgetContent

    /** L'objectif choisi n'existe plus : le widget invite à en choisir un autre. */
    data class GoalDeleted(val goalId: Long) : WidgetContent

    companion object {
        fun resolve(goalId: Long?, today: LocalDate, findGoal: (Long) -> Goal?): WidgetContent {
            if (goalId == null) return NotConfigured
            val goal = findGoal(goalId) ?: return GoalDeleted(goalId)
            return Showing(goalId, goal.title, CountdownEngine.compute(goal, today))
        }
    }
}
