package app.goalwidgets.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class GoalDraftTest {
    private val jan1 = LocalDate.of(2026, 1, 1)
    private val jan2 = LocalDate.of(2026, 1, 2)

    @Test
    fun `objectif depuis valide sans date cible, titre nettoyé`() {
        val draft = GoalDraft(GoalKind.SINCE, "  Arrêt du café ", jan1, targetDate = null, today = jan2)
        assertEquals(emptySet<DraftError>(), draft.errors())
        assertEquals(Goal(GoalKind.SINCE, startDate = jan1, targetDate = null, title = "Arrêt du café"), draft.toGoal())
    }

    @Test
    fun `objectif depuis, la date cible saisie avant de changer de type est ignorée`() {
        val draft = GoalDraft(GoalKind.SINCE, "Projet", jan2, targetDate = jan1, today = jan2)
        assertEquals(emptySet<DraftError>(), draft.errors())
        assertEquals(null, draft.toGoal().targetDate)
    }

    @Test
    fun `objectif depuis daté d'aujourd'hui accepté`() {
        assertEquals(emptySet<DraftError>(), GoalDraft(GoalKind.SINCE, "Projet", jan2, null, today = jan2).errors())
    }

    @Test
    fun `objectif depuis daté dans le futur refusé`() {
        assertEquals(
            setOf(DraftError.START_IN_FUTURE),
            GoalDraft(GoalKind.SINCE, "Projet", jan2, null, today = jan1).errors(),
        )
    }

    @Test
    fun `compte à rebours au départ futur accepté`() {
        assertEquals(emptySet<DraftError>(), GoalDraft(GoalKind.COUNTDOWN, "Examen", jan2, jan2.plusDays(1), today = jan1).errors())
    }

    @Test
    fun `objectif depuis au titre vide refusé`() {
        assertEquals(setOf(DraftError.TITLE_BLANK), GoalDraft(GoalKind.SINCE, " ", jan1, null, today = jan2).errors())
    }

    @Test
    fun `compte à rebours, mêmes règles que CountdownDraft`() {
        assertEquals(setOf(DraftError.TARGET_MISSING), GoalDraft(GoalKind.COUNTDOWN, "Examen", jan1, null, today = jan1).errors())
        assertEquals(
            setOf(DraftError.TARGET_NOT_AFTER_START),
            GoalDraft(GoalKind.COUNTDOWN, "Examen", jan2, jan1, today = jan1).errors(),
        )
        assertEquals(
            Goal(GoalKind.COUNTDOWN, startDate = jan1, targetDate = jan2, title = "Examen"),
            GoalDraft(GoalKind.COUNTDOWN, "Examen ", jan1, jan2, today = jan1).toGoal(),
        )
    }

    @Test
    fun `un objectif existant se recharge en brouillon pour modification`() {
        val goal = Goal(GoalKind.COUNTDOWN, startDate = jan1, targetDate = jan2, title = "Examen")
        assertEquals(goal, GoalDraft.of(goal, today = jan1).toGoal())
    }
}
