package app.goalwidgets.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Rejoue `shared-cases/export/cases.json` (format d'export, cf. `docs/export-format.md`) :
 * décodage du fichier puis fusion avec les objectifs locaux `existingIds`.
 */
class ExportSharedCasesTest {

    private val dir = File(
        System.getProperty("sharedCasesDir") ?: error("Propriété système sharedCasesDir absente"),
        "export",
    )

    @TestFactory
    fun importCases(): List<DynamicTest> {
        val root = Json.parseToJsonElement(File(dir, "cases.json").readText()).jsonObject
        val cases = root.getValue("cases").jsonArray.map { it.jsonObject }
        assertTrue(cases.isNotEmpty(), "cases.json ne contient aucun cas")
        return cases.map { case -> DynamicTest.dynamicTest(case.str("name")) { check(case) } }
    }

    private fun check(case: JsonObject) {
        val text = File(dir, case.str("file")).readText()
        val expected = case.getValue("expected").jsonObject
        val decoded = GoalExport.decode(text)

        expected["error"]?.let { code ->
            val error = when (decoded) {
                is GoalExport.Decoded.Failure -> decoded.error
                is GoalExport.Decoded.Success -> fail("erreur ${code.jsonPrimitive.content} attendue, fichier accepté")
            }
            assertEquals(code.jsonPrimitive.content, error.code, "code d'erreur")
            expected["index"]?.let { assertEquals(it.jsonPrimitive.int, (error as ImportError.InvalidGoal).index, "index") }
            expected["field"]?.let { assertEquals(it.jsonPrimitive.content, (error as ImportError.InvalidGoal).field, "field") }
            expected["version"]?.let { assertEquals(it.jsonPrimitive.int, (error as ImportError.UnsupportedVersion).version, "version") }
            expected["id"]?.let { assertEquals(it.jsonPrimitive.content, (error as ImportError.DuplicateId).id, "id") }
            return
        }

        val goals = when (decoded) {
            is GoalExport.Decoded.Success -> decoded.goals
            is GoalExport.Decoded.Failure -> fail("fichier refusé : ${decoded.error}")
        }
        expected["goals"]?.let { list ->
            assertEquals(list.jsonArray.map { goalFrom(it.jsonObject) }, goals, "objectifs décodés")
        }
        val existing = case.getValue("existingIds").jsonArray.map { it.jsonPrimitive.content }.toSet()
        val plan = GoalImport.plan(existingIds = existing, incoming = goals)
        expected["added"]?.let { ids ->
            assertEquals(ids.jsonArray.map { it.jsonPrimitive.content }, plan.toAdd.map { it.id }, "ajoutés")
        }
        expected["skipped"]?.let { assertEquals(it.jsonPrimitive.int, plan.skipped, "ignorés") }
    }

    /** Objectif attendu, écrit sous sa forme normalisée (tous les champs présents). */
    private fun goalFrom(o: JsonObject) = ExportedGoal(
        id = o.str("id"),
        goal = Goal(
            kind = GoalKind.fromWire(o.str("kind")),
            startDate = LocalDate.parse(o.str("startDate")),
            targetDate = o.nullable("targetDate")?.let(LocalDate::parse),
            title = o.str("title"),
        ),
        color = o.nullable("color")?.let { GoalColor.fromWire(it) ?: error("couleur attendue inconnue : $it") },
        emoji = o.nullable("emoji"),
        notificationsEnabled = o["notificationsEnabled"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.boolean,
        createdAt = OffsetDateTime.parse(o.str("createdAt")).toInstant(),
    )

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
    private fun JsonObject.nullable(key: String): String? = get(key)?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
}
