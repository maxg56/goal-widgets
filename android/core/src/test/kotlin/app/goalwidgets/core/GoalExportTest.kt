package app.goalwidgets.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class GoalExportTest {

    private val exam = ExportedGoal(
        id = "3f2b8c1e-4d5a-4e6f-9a7b-1c2d3e4f5a6b",
        goal = Goal(GoalKind.COUNTDOWN, LocalDate.of(2026, 9, 1), LocalDate.of(2027, 6, 15), "Examen"),
        color = GoalColor.BLUE,
        emoji = "🎓",
        notificationsEnabled = true,
        createdAt = Instant.parse("2026-09-01T07:12:45.123Z"),
    )
    private val quit = ExportedGoal(
        id = "a1b2c3d4-0000-4000-8000-00000000abcd",
        goal = Goal(GoalKind.SINCE, LocalDate.of(2024, 2, 29), title = "Arrêt du tabac"),
        createdAt = Instant.parse("2024-02-29T20:00:00Z"),
    )

    @Test
    fun `un export se réimporte à l'identique`() {
        val text = GoalExport.encode(listOf(exam, quit), exportedAt = Instant.parse("2026-11-25T08:30:00Z"))

        assertEquals(GoalExport.Decoded.Success(listOf(exam, quit)), GoalExport.decode(text))
    }

    @Test
    fun `l'export écrit l'en-tête versionné et tous les champs, null explicites compris`() {
        val text = GoalExport.encode(listOf(quit), exportedAt = Instant.parse("2026-11-25T08:30:00Z"))

        val root = Json.parseToJsonElement(text).jsonObject
        assertEquals("goal-widgets-export", root.getValue("format").jsonPrimitive.content)
        assertEquals(1, root.getValue("version").jsonPrimitive.content.toInt())
        assertEquals("2026-11-25T08:30:00.000Z", root.getValue("exportedAt").jsonPrimitive.content)
        val goal = root.getValue("goals").jsonArray.single().jsonObject
        assertEquals(
            setOf("id", "title", "kind", "startDate", "targetDate", "color", "emoji", "notificationsEnabled", "createdAt"),
            goal.keys,
        )
        assertEquals(JsonNull, goal["targetDate"])
        assertEquals("2024-02-29", goal.getValue("startDate").jsonPrimitive.content)
        assertEquals("2024-02-29T20:00:00.000Z", goal.getValue("createdAt").jsonPrimitive.content)
    }

    @Test
    fun `l'id est écrit en minuscules`() {
        val text = GoalExport.encode(listOf(quit.copy(id = "A1B2C3D4-0000-4000-8000-00000000ABCD")), Instant.EPOCH)

        val goal = Json.parseToJsonElement(text).jsonObject.getValue("goals").jsonArray.single().jsonObject
        assertEquals("a1b2c3d4-0000-4000-8000-00000000abcd", goal.getValue("id").jsonPrimitive.content)
    }
}
