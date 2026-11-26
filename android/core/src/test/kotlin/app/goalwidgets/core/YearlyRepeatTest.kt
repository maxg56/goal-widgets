package app.goalwidgets.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class YearlyRepeatTest {
    private fun d(s: String) = LocalDate.parse(s)
    private fun countdown(start: String, target: String) = Goal(GoalKind.COUNTDOWN, d(start), d(target), "Anniversaire")

    @Test
    fun `nothing to do before or on the target day`() {
        assertNull(YearlyRepeat.roll(countdown("2026-01-01", "2026-06-10"), d("2026-06-09")))
        assertNull(YearlyRepeat.roll(countdown("2026-01-01", "2026-06-10"), d("2026-06-10")))
    }

    @Test
    fun `the day after, it counts down to next year from the day just passed`() {
        val rolled = YearlyRepeat.roll(countdown("2026-01-01", "2026-06-10"), d("2026-06-11"))!!
        assertEquals(d("2026-06-10"), rolled.startDate)
        assertEquals(d("2027-06-10"), rolled.targetDate)
        assertEquals(Phase.UPCOMING, CountdownEngine.compute(rolled, d("2026-06-11")).phase)
        assertEquals(364, CountdownEngine.compute(rolled, d("2026-06-11")).days)
    }

    @Test
    fun `app not opened for years jumps straight to the next occurrence`() {
        val rolled = YearlyRepeat.roll(countdown("2020-01-01", "2020-03-15"), d("2026-03-15"))!!
        assertEquals(d("2025-03-15"), rolled.startDate)
        assertEquals(d("2026-03-15"), rolled.targetDate)
        assertEquals(Phase.TODAY, CountdownEngine.compute(rolled, d("2026-03-15")).phase)
    }

    @Test
    fun `29 february falls back to 28 february on common years`() {
        val rolled = YearlyRepeat.roll(countdown("2024-01-01", "2024-02-29"), d("2024-03-01"))!!
        assertEquals(d("2025-02-28"), rolled.targetDate)
    }

    @Test
    fun `since goals never roll`() {
        assertNull(YearlyRepeat.roll(Goal(GoalKind.SINCE, d("2020-01-01")), d("2026-01-01")))
    }
}
