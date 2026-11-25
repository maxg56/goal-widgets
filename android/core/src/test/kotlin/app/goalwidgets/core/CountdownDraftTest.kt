package app.goalwidgets.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CountdownDraftTest {
    private val jan1 = LocalDate.of(2026, 1, 1)
    private val jan2 = LocalDate.of(2026, 1, 2)

    @Test
    fun `brouillon valide avec titre et cible postérieure au départ`() {
        assertEquals(emptySet<DraftError>(), CountdownDraft("Examen", jan1, jan2).errors())
    }

    @Test
    fun `titre vide ou blanc refusé`() {
        assertEquals(setOf(DraftError.TITLE_BLANK), CountdownDraft("   ", jan1, jan2).errors())
    }

    @Test
    fun `cible égale au départ refusée`() {
        assertEquals(setOf(DraftError.TARGET_NOT_AFTER_START), CountdownDraft("Examen", jan1, jan1).errors())
    }

    @Test
    fun `cible antérieure au départ refusée`() {
        assertEquals(setOf(DraftError.TARGET_NOT_AFTER_START), CountdownDraft("Examen", jan2, jan1).errors())
    }

    @Test
    fun `cible absente refusée`() {
        assertEquals(setOf(DraftError.TARGET_MISSING), CountdownDraft("Examen", jan1, null).errors())
    }

    @Test
    fun `un brouillon valide devient un objectif compte à rebours au titre nettoyé`() {
        assertEquals(
            Goal(kind = GoalKind.COUNTDOWN, startDate = jan1, targetDate = jan2, title = "Examen"),
            CountdownDraft("  Examen ", jan1, jan2).toGoal(),
        )
    }
}
