package app.goalwidgets.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class WidgetContentTest {
    private val today = LocalDate.of(2026, 1, 6)
    private val exam = Goal(GoalKind.COUNTDOWN, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 11), "Examen")
    private val noGoals: (Long) -> Goal? = { null }

    @Test
    fun `widget sans objectif choisi est à configurer`() {
        assertEquals(WidgetContent.NotConfigured, WidgetContent.resolve(goalId = null, today = today, findGoal = noGoals))
    }

    @Test
    fun `widget lié à un objectif existant affiche son titre et son état du jour`() {
        val content = WidgetContent.resolve(goalId = 7, today = today) { if (it == 7L) exam else null }
        assertEquals(
            WidgetContent.Showing(
                goalId = 7,
                title = "Examen",
                state = DisplayState(Phase.UPCOMING, days = 5, progress = 0.5, percent = 50),
            ),
            content,
        )
    }

    @Test
    fun `widget dont l'objectif a été supprimé le signale`() {
        assertEquals(WidgetContent.GoalDeleted(goalId = 7), WidgetContent.resolve(goalId = 7, today = today, findGoal = noGoals))
    }

    @Test
    fun `deux widgets sur le même objectif affichent la même chose`() {
        val find: (Long) -> Goal? = { if (it == 7L) exam else null }
        assertEquals(WidgetContent.resolve(7, today, find), WidgetContent.resolve(7, today, find))
    }

    @Test
    fun `le contenu suit le jour courant`() {
        val find: (Long) -> Goal? = { exam }
        val onTargetDay = WidgetContent.resolve(7, LocalDate.of(2026, 1, 11), find) as WidgetContent.Showing
        assertEquals(DisplayState(Phase.TODAY, days = 0, progress = 1.0, percent = 100), onTargetDay.state)
    }
}
