package app.goalwidgets.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

/** Champs facultatifs ajoutés au format v1 (réglages Android) : aller-retour et tolérance. */
class ExportOptionsTest {
    private val quit = ExportedGoal(
        id = "5a6b7c8d-1e2f-4a3b-8c4d-5e6f7a8b9c0d",
        goal = Goal(GoalKind.SINCE, LocalDate.of(2025, 3, 1), title = "Sans fumer"),
        createdAt = Instant.parse("2025-03-01T08:00:00Z"),
        options = ExportedOptions(
            chartUnit = ChartUnit.WEEK,
            streak = true,
            streakHistory = listOf(PastStreak(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 10))),
            archived = true,
        ),
    )

    @Test
    fun `options survive an export round trip`() {
        val text = GoalExport.encode(listOf(quit), Instant.EPOCH)
        assertEquals(GoalExport.Decoded.Success(listOf(quit)), GoalExport.decode(text))
    }

    @Test
    fun `default options write no extra key`() {
        val text = GoalExport.encode(listOf(quit.copy(options = ExportedOptions())), Instant.EPOCH)
        val goal = Json.parseToJsonElement(text).jsonObject.getValue("goals").jsonArray.single().jsonObject
        assertEquals(setOf("id", "title", "kind", "startDate", "targetDate", "color", "emoji", "notificationsEnabled", "createdAt"), goal.keys)
    }

    @Test
    fun `invalid option values are ignored, not fatal`() {
        val text = """
            {"format":"goal-widgets-export","version":1,"goals":[{"id":"5a6b7c8d-1e2f-4a3b-8c4d-5e6f7a8b9c0d",
            "title":"T","kind":"since","startDate":"2025-03-01","createdAt":"2025-03-01T08:00:00Z",
            "chartUnit":"century","streak":"yes","streakHistory":42,"repeatYearly":true,"archived":null}]}
        """.trimIndent()
        val goals = (GoalExport.decode(text) as GoalExport.Decoded.Success).goals
        // repeatYearly ignoré pour un compteur « depuis ».
        assertEquals(ExportedOptions(), goals.single().options)
    }
}
