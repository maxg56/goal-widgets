package app.goalwidgets.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import java.time.LocalDate

/**
 * Rejoue `shared-cases/engine.json` : chaque cas devient un test JUnit.
 * Seuls les champs présents dans `expected` sont vérifiés. `action` (facultatif) transforme
 * l'objectif avant le calcul : `continueAsSince` = [Goal.continueAsSince].
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
        val stored = Goal(
            kind = GoalKind.fromWire(g.str("kind")),
            startDate = LocalDate.parse(g.str("startDate")),
            targetDate = g["targetDate"]?.jsonPrimitive?.content?.let(LocalDate::parse),
        )
        // `action` facultatif : transformation appliquée à l'objectif avant le calcul.
        val goal = when (val action = case["action"]?.jsonPrimitive?.content) {
            null -> stored
            "continueAsSince" -> stored.continueAsSince()
            else -> error("action inconnue : $action")
        }
        val state = CountdownEngine.compute(goal, today = LocalDate.parse(case.str("today")))
        val expected = case.getValue("expected").jsonObject

        expected["phase"]?.let { assertEquals(it.jsonPrimitive.content, state.phase.wire, "phase") }
        expected["days"]?.let { assertEquals(it.jsonPrimitive.int, state.days, "days") }
        // Champ absent : non vérifié. `null` explicite : doit être absent (null) dans l'état.
        expected["progress"]?.let {
            if (it is JsonNull) {
                assertNull(state.progress, "progress doit être null")
            } else {
                val actual = state.progress ?: error("progress attendu mais absent")
                assertEquals(it.jsonPrimitive.double, actual, 1e-6, "progress")
            }
        }
        expected["percent"]?.let {
            if (it is JsonNull) {
                assertNull(state.percent, "percent doit être null")
            } else {
                assertEquals(it.jsonPrimitive.int, state.percent ?: error("percent attendu mais absent"), "percent")
            }
        }
        expected["breakdown"]?.let {
            if (it is JsonNull) {
                assertNull(state.breakdown, "breakdown doit être null")
            } else {
                val b = it.jsonObject
                val expectedBreakdown = Breakdown(
                    years = b.getValue("years").jsonPrimitive.int,
                    months = b.getValue("months").jsonPrimitive.int,
                    days = b.getValue("days").jsonPrimitive.int,
                )
                assertEquals(expectedBreakdown, state.breakdown, "breakdown")
            }
        }
    }

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
}
