package app.goalwidgets.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

/** Réglages de notification par objectif (Android) : stockage et effet sur le planificateur. */
class NotificationPrefsTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun `empty text means defaults and defaults encode to empty text`() {
        assertEquals(NotificationPrefs.DEFAULT, NotificationPrefs.decode(""))
        assertEquals(NotificationPrefs.DEFAULT, NotificationPrefs.decode(null))
        assertEquals("", NotificationPrefs.DEFAULT.encode())
    }

    @Test
    fun `prefs round-trip and unreadable parts are ignored`() {
        val prefs = NotificationPrefs(setOf(MilestoneFamily.DAY_MILESTONES, MilestoneFamily.TARGET_DAY), setOf(14, 3), LocalTime.of(20, 30))
        assertEquals("off=days,target;before=14,3;time=1230", prefs.encode())
        assertEquals(prefs, NotificationPrefs.decode(prefs.encode()))
        assertEquals(
            NotificationPrefs(daysBefore = setOf(2)),
            NotificationPrefs.decode("off=nope;before=2,0,-4,abc,9999;time=5000;what=1"),
        )
        // « before= » vide : aucun rappel avant la cible.
        assertEquals(emptySet<Int>(), NotificationPrefs.decode("before=").daysBefore)
    }

    @Test
    fun `custom days before replace J-30 J-7 J-1`() {
        val goal = Goal(GoalKind.COUNTDOWN, d("2026-01-01"), d("2026-06-30"))
        val prefs = NotificationPrefs(disabled = setOf(MilestoneFamily.MONTHLY_REMINDERS), daysBefore = setOf(14, 3))
        val plan = MilestonePlanner.plan("1", goal, true, d("2026-06-01"), 60, prefs)
        assertEquals(
            listOf(d("2026-06-16") to 14, d("2026-06-27") to 3, d("2026-06-30") to 0),
            plan.map { it.date to it.value },
        )
    }

    @Test
    fun `since goals get day milestones, which lose to a year anniversary the same day`() {
        val goal = Goal(GoalKind.SINCE, d("2026-01-01"))
        val plan = MilestonePlanner.plan("1", goal, true, d("2026-01-02"), 120, NotificationPrefs.DEFAULT)
        val days = plan.filter { it.kind == MilestoneKind.DAY_MILESTONE }.map { it.value }
        assertEquals(listOf(7, 14, 50, 100), days)
        assertTrue(plan.any { it.kind == MilestoneKind.MONTH_ANNIVERSARY })
    }

    @Test
    fun `families can be switched off`() {
        val goal = Goal(GoalKind.SINCE, d("2026-01-01"))
        val prefs = NotificationPrefs(disabled = setOf(MilestoneFamily.MONTH_ANNIVERSARIES))
        val plan = MilestonePlanner.plan("1", goal, true, d("2026-01-02"), 120, prefs)
        assertTrue(plan.all { it.kind == MilestoneKind.DAY_MILESTONE })
    }

    @Test
    fun `day milestones continue every 500 days after 1000`() {
        assertEquals(listOf(7, 14, 50, 100, 200, 300, 500, 1000, 1500, 2000), DayMilestones.upTo(2400))
    }
}
