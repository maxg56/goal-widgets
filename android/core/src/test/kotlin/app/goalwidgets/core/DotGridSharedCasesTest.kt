package app.goalwidgets.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
 * Rejoue `shared-cases/dotgrid.json` (grille de points, ticket 12). Seuls les champs présents
 * dans `expected` sont vérifiés : `unit`, `count`, `states` (une lettre par cellule :
 * `e` écoulée, `t` aujourd'hui, `r` restante, `o` hors objectif), `first`/`last` (`{start, end}`).
 */
class DotGridSharedCasesTest {

    @TestFactory
    fun dotGridCases(): List<DynamicTest> {
        val dir = System.getProperty("sharedCasesDir") ?: error("Propriété système sharedCasesDir absente")
        val root = Json.parseToJsonElement(File(dir, "dotgrid.json").readText()).jsonObject
        val cases = root.getValue("cases").jsonArray.map { it.jsonObject }
        assertTrue(cases.isNotEmpty(), "dotgrid.json ne contient aucun cas")
        return cases.map { case -> DynamicTest.dynamicTest(case.str("name")) { check(case) } }
    }

    private fun check(case: JsonObject) {
        val g = case.getValue("goal").jsonObject
        val goal = Goal(
            kind = GoalKind.fromWire(g.str("kind")),
            startDate = LocalDate.parse(g.str("startDate")),
            targetDate = g["targetDate"]?.jsonPrimitive?.content?.let(LocalDate::parse),
        )
        val grid = DotGrid.compute(
            goal,
            today = LocalDate.parse(case.str("today")),
            period = DotPeriod.fromWire(case.str("period")) ?: error("période inconnue"),
        )
        val expected = case.getValue("expected").jsonObject

        expected["unit"]?.let { assertEquals(it.jsonPrimitive.content, grid.unit.wire, "unit") }
        expected["count"]?.let { assertEquals(it.jsonPrimitive.int, grid.cells.size, "count") }
        expected["states"]?.let {
            assertEquals(it.jsonPrimitive.content, grid.cells.joinToString("") { c -> c.state.letter.toString() }, "states")
        }
        expected["first"]?.let { assertCell(it.jsonObject, grid.cells.first(), "first") }
        expected["last"]?.let { assertCell(it.jsonObject, grid.cells.last(), "last") }
    }

    private fun assertCell(expected: JsonObject, cell: DotCell, label: String) {
        assertEquals(LocalDate.parse(expected.str("start")), cell.start, "$label.start")
        assertEquals(LocalDate.parse(expected.str("end")), cell.end, "$label.end")
    }

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
}
