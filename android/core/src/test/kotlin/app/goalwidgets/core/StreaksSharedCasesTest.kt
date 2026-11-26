package app.goalwidgets.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.File
import java.time.LocalDate

/** Rejoue `shared-cases/streaks.json` : remise à zéro des compteurs « série » et historique. */
class StreaksSharedCasesTest {

    private val root: JsonObject by lazy {
        val dir = System.getProperty("sharedCasesDir") ?: error("Propriété système sharedCasesDir absente")
        Json.parseToJsonElement(File(dir, "streaks.json").readText()).jsonObject
    }

    @TestFactory
    fun streakCases(): List<DynamicTest> = root.getValue("cases").jsonArray.map { it.jsonObject }.map { case ->
        DynamicTest.dynamicTest(case.str("name")) {
            val today = LocalDate.parse(case.str("today"))
            var goal = Goal(GoalKind.SINCE, LocalDate.parse(case.str("startDate")))
            var history = streaks(case.getValue("history").jsonArray)
            val expected = case.getValue("expected").jsonObject

            case["resetOn"]?.jsonPrimitive?.content?.let(LocalDate::parse)?.let { on ->
                val error = Streaks.validate(goal, on, today)
                assertEquals(expected["error"]?.jsonPrimitive?.content, error?.code, "error")
                if (error == null) {
                    val reset = Streaks.reset(goal, history, on, today)
                    goal = reset.goal
                    history = reset.history
                }
            }

            val summary = Streaks.summary(goal, history, today)
            expected["startDate"]?.let { assertEquals(LocalDate.parse(it.jsonPrimitive.content), goal.startDate, "startDate") }
            expected["history"]?.let { list ->
                val rows = list.jsonArray.map { it.jsonArray }
                assertEquals(rows.map { PastStreak(LocalDate.parse(it[0].jsonPrimitive.content), LocalDate.parse(it[1].jsonPrimitive.content)) }, history, "history")
                assertEquals(rows.map { it[2].jsonPrimitive.int }, history.map { it.days }, "history days")
            }
            expected["currentDays"]?.let { assertEquals(it.jsonPrimitive.int, summary.currentDays, "currentDays") }
            expected["bestDays"]?.let { assertEquals(it.jsonPrimitive.int, summary.bestDays, "bestDays") }
            expected["resetCount"]?.let { assertEquals(it.jsonPrimitive.int, summary.resetCount, "resetCount") }
            expected["isRecord"]?.let { assertEquals(it.jsonPrimitive.boolean, summary.isRecord, "isRecord") }
            expected["encoded"]?.let {
                assertEquals(it.jsonPrimitive.content, Streaks.encode(history), "encoded")
                assertEquals(history, Streaks.decode(Streaks.encode(history)), "aller-retour")
            }
        }
    }

    @TestFactory
    fun decodingCases(): List<DynamicTest> = root.getValue("decoding").jsonArray.map { it.jsonObject }.map { case ->
        DynamicTest.dynamicTest(case.str("name")) {
            assertEquals(streaks(case.getValue("expected").jsonArray), Streaks.decode(case.str("raw")))
        }
    }

    @Test
    fun `decode accepte null`() {
        assertEquals(emptyList<PastStreak>(), Streaks.decode(null))
    }

    @Test
    fun `double appui du widget`() {
        val t0 = 1_000_000L
        assertEquals(WidgetResetTap.Outcome.ARM, WidgetResetTap.onTap(null, t0))
        assertEquals(WidgetResetTap.Outcome.RESET, WidgetResetTap.onTap(t0, t0 + 1_000))
        assertEquals(WidgetResetTap.Outcome.RESET, WidgetResetTap.onTap(t0, t0 + WidgetResetTap.WINDOW_MILLIS - 1))
        assertEquals(WidgetResetTap.Outcome.ARM, WidgetResetTap.onTap(t0, t0 + WidgetResetTap.WINDOW_MILLIS))
        // Horloge reculée (changement d'heure manuel) : on réarme plutôt que de remettre à zéro.
        assertEquals(WidgetResetTap.Outcome.ARM, WidgetResetTap.onTap(t0, t0 - 1))
        assertTrue(WidgetResetTap.isArmed(t0, t0))
        assertFalse(WidgetResetTap.isArmed(null, t0))
    }

    private fun streaks(array: JsonArray): List<PastStreak> = array.map { it.jsonArray }.map {
        PastStreak(LocalDate.parse(it[0].jsonPrimitive.content), LocalDate.parse(it[1].jsonPrimitive.content))
    }

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
}
