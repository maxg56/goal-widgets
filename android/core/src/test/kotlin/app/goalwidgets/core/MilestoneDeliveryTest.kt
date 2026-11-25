package app.goalwidgets.core

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * `MilestonePlanner.shouldDeliver` : décision prise quand l'alarme d'un jalon se déclenche.
 * Le texte (« dans 7 jours », « c'est aujourd'hui »…) n'est juste que le jour du jalon.
 */
class MilestoneDeliveryTest {

    private fun d(iso: String) = LocalDate.parse(iso)

    // Compte à rebours vers le 2026-03-10 : J-7 le 2026-03-03.
    private val countdown = Goal(GoalKind.COUNTDOWN, startDate = d("2026-01-01"), targetDate = d("2026-03-10"))
    private val sevenDaysBefore = Milestone("1", d("2026-03-03"), MilestoneKind.DAYS_BEFORE, 7)

    @Test
    fun `alarme le jour du jalon - notifie`() {
        assertTrue(MilestonePlanner.shouldDeliver(sevenDaysBefore, countdown, notificationsEnabled = true, today = d("2026-03-03")))
    }

    @Test
    fun `alarme en retard le lendemain - ne notifie pas (texte faux d'un jour)`() {
        assertFalse(MilestonePlanner.shouldDeliver(sevenDaysBefore, countdown, notificationsEnabled = true, today = d("2026-03-04")))
    }

    @Test
    fun `objectif supprimé depuis la programmation - ne notifie pas`() {
        assertFalse(MilestonePlanner.shouldDeliver(sevenDaysBefore, goal = null, notificationsEnabled = true, today = d("2026-03-03")))
    }

    @Test
    fun `notifications désactivées depuis la programmation - ne notifie pas`() {
        assertFalse(MilestonePlanner.shouldDeliver(sevenDaysBefore, countdown, notificationsEnabled = false, today = d("2026-03-03")))
    }

    @Test
    fun `cible déplacée depuis la programmation - ne notifie pas l'ancien J-7`() {
        val moved = countdown.copy(targetDate = d("2026-03-20"))
        assertFalse(MilestonePlanner.shouldDeliver(sevenDaysBefore, moved, notificationsEnabled = true, today = d("2026-03-03")))
    }
}
