package app.goalwidgets.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

class WeeklyScheduleTest {
    private val sunday6pm = LocalTime.of(18, 0)

    @Test
    fun `later this week, later today, or next week once the time has passed`() {
        // Vendredi 27 novembre 2026.
        assertEquals(LocalDateTime.parse("2026-11-29T18:00"), WeeklySchedule.next(LocalDateTime.parse("2026-11-27T10:00"), DayOfWeek.SUNDAY, sunday6pm))
        assertEquals(LocalDateTime.parse("2026-11-29T18:00"), WeeklySchedule.next(LocalDateTime.parse("2026-11-29T17:59"), DayOfWeek.SUNDAY, sunday6pm))
        assertEquals(LocalDateTime.parse("2026-12-06T18:00"), WeeklySchedule.next(LocalDateTime.parse("2026-11-29T18:00"), DayOfWeek.SUNDAY, sunday6pm))
    }
}
