package app.goalwidgets.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import java.time.LocalDate

/**
 * Rejoue `shared-cases/engine.json` : chaque cas devient un test JUnit.
 * Seuls les champs présents dans `expected` sont vérifiés.
 */
class EngineSharedCasesTest {

    @TestFactory
    fun engineCases(): List<DynamicTest> {
        val dir = System.getProperty("sharedCasesDir")
            ?: error("Propriété système sharedCasesDir absente (définie par core/build.gradle.kts)")
        val root = Json.parseToJsonElement(File(dir, "engine.json").readText()).jsonObject
        val cases = root.getValue("cases").jsonArray.map { it.jsonObject }
        assertTrue(cases.isNotEmpty(), "engine.json ne contient aucun cas")
        return cases.map { case ->
            DynamicTest.dynamicTest(case.str("name")) { check(case) }
        }
    }

    private fun check(case: JsonObject) {
        val g = case.getValue("goal").jsonObject
        val goal = Goal(
            kind = GoalKind.fromWire(g.str("kind")),
            startDate = LocalDate.parse(g.str("startDate")),
            targetDate = g["targetDate"]?.jsonPrimitive?.content?.let(LocalDate::parse),
        )
        val state = CountdownEngine.compute(goal, today = LocalDate.parse(case.str("today")))
        val expected = case.getValue("expected").jsonObject

        expected["phase"]?.let { assertEquals(it.jsonPrimitive.content, state.phase.wire, "phase") }
        expected["days"]?.let { assertEquals(it.jsonPrimitive.int, state.days, "days") }
        expected["progress"]?.let {
            val actual = state.progress ?: error("progress attendu mais absent")
            assertEquals(it.jsonPrimitive.double, actual, 1e-6, "progress")
        }
    }

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
}
