package app.goalwidgets.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
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
 * Rejoue `shared-cases/planner.json` (écrit par t09, référence commune avec XCTest).
 * `expected.notifications` est comparée en entier (liste complète et ordonnée).
 * `goal.notificationsEnabled` absent = true. Deux appels identiques doivent donner la même liste.
 */
class PlannerSharedCasesTest {

    /** Forme neutre d'une notification attendue, telle qu'écrite dans le JSON. */
    private data class Row(val id: String, val date: String, val kind: String, val value: Int)

    @TestFactory
    fun plannerCases(): List<DynamicTest> {
        val dir = System.getProperty("sharedCasesDir")
            ?: error("Propriété système sharedCasesDir absente (définie par core/build.gradle.kts)")
        val root = Json.parseToJsonElement(File(dir, "planner.json").readText()).jsonObject
        val cases = root.getValue("cases").jsonArray.map { it.jsonObject }
        assertTrue(cases.isNotEmpty(), "planner.json ne contient aucun cas")
        return cases.map { case -> DynamicTest.dynamicTest(case.str("name")) { check(case) } }
    }

    private fun check(case: JsonObject) {
        val g = case.getValue("goal").jsonObject
        val goal = Goal(
            kind = GoalKind.fromWire(g.str("kind")),
            startDate = LocalDate.parse(g.str("startDate")),
            targetDate = g["targetDate"]?.jsonPrimitive?.content?.let(LocalDate::parse),
        )
        val enabled = g["notificationsEnabled"]?.jsonPrimitive?.boolean ?: true
        val run = {
            MilestonePlanner.plan(
                goalId = case.str("goalId"),
                goal = goal,
                notificationsEnabled = enabled,
                today = LocalDate.parse(case.str("today")),
                horizonDays = case.getValue("horizonDays").jsonPrimitive.int,
            )
        }
        val expected = case.getValue("expected").jsonObject.getValue("notifications").jsonArray.map {
            val o = it.jsonObject
            Row(o.str("id"), o.str("date"), o.str("kind"), o.getValue("value").jsonPrimitive.int)
        }
        val first = run()
        assertEquals(expected, first.map { Row(it.id, it.date.toString(), it.kind.wire, it.value) })
        assertEquals(first, run(), "deux appels identiques doivent donner la même liste")
    }

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
}
