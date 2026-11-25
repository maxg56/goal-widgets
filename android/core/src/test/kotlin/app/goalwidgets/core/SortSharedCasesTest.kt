package app.goalwidgets.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import java.time.Instant
import java.time.LocalDate

/** Rejoue `shared-cases/sort.json` : ordre de la liste selon le tri choisi. */
class SortSharedCasesTest {

    private data class Row(val id: String, val goal: Goal, val createdAt: Instant)

    @TestFactory
    fun sortCases(): List<DynamicTest> {
        val dir = System.getProperty("sharedCasesDir") ?: error("Propriété système sharedCasesDir absente")
        val root = Json.parseToJsonElement(File(dir, "sort.json").readText()).jsonObject
        val today = LocalDate.parse(root.str("today"))
        val rows = root.getValue("goals").jsonArray.map { it.jsonObject }.map { g ->
            Row(
                id = g.str("id"),
                goal = Goal(
                    kind = GoalKind.fromWire(g.str("kind")),
                    startDate = LocalDate.parse(g.str("startDate")),
                    targetDate = g["targetDate"]?.jsonPrimitive?.content?.let(LocalDate::parse),
                ),
                createdAt = Instant.parse(g.str("createdAt")),
            )
        }
        return root.getValue("cases").jsonArray.map { it.jsonObject }.map { case ->
            DynamicTest.dynamicTest(case.str("name")) {
                val order = GoalOrder.fromWire(case.str("order"))
                // Entrée mélangée : le résultat ne doit pas dépendre de l'ordre de départ.
                for (input in listOf(rows, rows.reversed())) {
                    val sorted = order.sort(input, today) { SortKey(it.id, it.goal, it.createdAt) }
                    assertEquals(case.getValue("expected").jsonArray.map { it.jsonPrimitive.content }, sorted.map { it.id })
                }
            }
        }
    }

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
}
