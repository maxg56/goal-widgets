package app.goalwidgets.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * `MilestonePlanner.closest` : garde les jalons les plus proches tous objectifs confondus
 * (plafond d'alarmes programmées). Mêmes vecteurs que `MilestoneSelectionTests.swift`.
 */
class MilestoneSelectionTest {

    private fun d(iso: String) = LocalDate.parse(iso)

    @Test
    fun `garde les plus proches tous objectifs confondus`() {
        // 40 objectifs « depuis » démarrés le 1er décembre 2025 → 12 jalons chacun sur 364 jours.
        val plans = (0 until 40).flatMap { i ->
            MilestonePlanner.plan(
                goalId = "g$i",
                goal = Goal(GoalKind.SINCE, startDate = d("2025-12-01")),
                notificationsEnabled = true,
                today = d("2026-01-01"),
                horizonDays = 364,
            )
        }
        assertEquals(480, plans.size)

        val kept = MilestonePlanner.closest(plans, limit = 64)

        assertEquals(64, kept.size)
        assertEquals(40, kept.count { it.date == d("2026-01-01") })
        assertEquals(24, kept.count { it.date == d("2026-02-01") })
        assertEquals(d("2026-02-01"), kept.last().date)
    }

    @Test
    fun `le même jour, le jalon le plus fort passe en premier`() {
        val target = Milestone("b", d("2026-03-01"), MilestoneKind.TARGET_DAY, 0)
        val monthly = Milestone("a", d("2026-03-01"), MilestoneKind.MONTH_ANNIVERSARY, 2)
        val later = Milestone("a", d("2026-03-02"), MilestoneKind.MONTH_ANNIVERSARY, 3)

        assertEquals(listOf(target), MilestonePlanner.closest(listOf(later, monthly, target), limit = 1))
        assertEquals(listOf(target, monthly, later), MilestonePlanner.closest(listOf(later, monthly, target), limit = 5))
    }

    @Test
    fun `sous la limite tout est gardé, limite nulle ne garde rien`() {
        val one = Milestone("a", d("2026-03-01"), MilestoneKind.TARGET_DAY, 0)
        assertEquals(listOf(one), MilestonePlanner.closest(listOf(one), limit = 64))
        assertEquals(emptyList<Milestone>(), MilestonePlanner.closest(listOf(one), limit = 0))
    }
}
