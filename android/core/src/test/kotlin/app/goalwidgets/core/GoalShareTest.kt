package app.goalwidgets.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

/** Fichier de partage d'un objectif (ticket 13) : export v1 à un seul objectif. */
class GoalShareTest {

    private val exam = ExportedGoal(
        id = "3f2b8c1e-4d5a-4e6f-9a7b-1c2d3e4f5a6b",
        goal = Goal(GoalKind.COUNTDOWN, LocalDate.of(2026, 9, 1), LocalDate.of(2027, 6, 15), "Examen"),
        color = GoalColor.BLUE,
        emoji = "🎓",
        notificationsEnabled = true,
        createdAt = Instant.parse("2026-09-01T07:12:45.123Z"),
    )

    @Test
    fun `un fichier de partage se relit à l'identique, sans le réglage de notifications`() {
        val text = GoalShare.encode(exam, sharedAt = Instant.parse("2026-11-25T08:30:00Z"))

        assertEquals(
            GoalExport.Decoded.Success(listOf(exam.copy(notificationsEnabled = null))),
            GoalExport.decode(text),
        )
    }

    @Test
    fun `le fichier contient un seul objectif et écrit notificationsEnabled à null`() {
        val text = GoalShare.encode(exam, sharedAt = Instant.EPOCH)

        val goal = Json.parseToJsonElement(text).jsonObject.getValue("goals").jsonArray.single().jsonObject
        assertEquals(JsonNull, goal["notificationsEnabled"])
    }

    @Test
    fun `le nom du fichier est le titre suivi de goalwidgets json`() {
        assertEquals("Examen.goalwidgets.json", GoalShare.fileName("Examen"))
    }

    @Test
    fun `les caractères interdits dans un nom de fichier sont remplacés`() {
        assertEquals("Bac _ juin_ 2027 _.goalwidgets.json", GoalShare.fileName("  Bac / juin: 2027 ?  "))
    }

    @Test
    fun `un titre vide après nettoyage donne un nom par défaut`() {
        assertEquals("objectif.goalwidgets.json", GoalShare.fileName("   "))
    }

    @Test
    fun `un titre très long est raccourci à 80 caractères`() {
        assertEquals("a".repeat(80) + ".goalwidgets.json", GoalShare.fileName("a".repeat(200)))
    }
}
