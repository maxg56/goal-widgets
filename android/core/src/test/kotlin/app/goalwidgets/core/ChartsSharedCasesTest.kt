package app.goalwidgets.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
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
 * Rejoue `shared-cases/charts.json` (graphiques du détail et du widget). Seuls les champs présents
 * dans `expected` sont vérifiés ; `null` explicite = doit être absent. Nombres à virgule à 1e-6 près.
 */
class ChartsSharedCasesTest {

    @TestFactory
    fun chartCases(): List<DynamicTest> {
        val dir = System.getProperty("sharedCasesDir") ?: error("Propriété système sharedCasesDir absente")
        val root = Json.parseToJsonElement(File(dir, "charts.json").readText()).jsonObject
        val cases = root.getValue("cases").jsonArray.map { it.jsonObject }
        assertTrue(cases.isNotEmpty(), "charts.json ne contient aucun cas")
        return cases.map { case -> DynamicTest.dynamicTest(case.str("name")) { check(case) } }
    }

    private fun check(case: JsonObject) {
        val g = case.getValue("goal").jsonObject
        val goal = Goal(
            kind = GoalKind.fromWire(g.str("kind")),
            startDate = LocalDate.parse(g.str("startDate")),
            targetDate = g["targetDate"]?.jsonPrimitive?.content?.let(LocalDate::parse),
        )
        val today = LocalDate.parse(case.str("today"))
        val expected = case.getValue("expected").jsonObject
        when (val chart = case.str("chart")) {
            "arcs" -> checkArcs(ArcsChart.compute(goal, today), expected)
            "bars" -> checkBars(BarsChart.compute(goal, today), case["maxBars"]?.jsonPrimitive?.int, expected)
            "ring" -> checkRing(RingChart.compute(goal, today), expected)
            "strip" -> checkStrip(GoalStrip.compute(goal, today), expected)
            else -> error("graphique inconnu : $chart")
        }
    }

    private fun checkRing(ring: RingChart, expected: JsonObject) {
        expected["fraction"]?.let { assertEquals(it.doubleValue(), ring.fraction, 1e-6, "fraction") }
        expected["percent"]?.let { assertEquals(it.jsonPrimitive.int, ring.percent, "percent") }
        expected["date"]?.let { assertEquals(LocalDate.parse(it.jsonPrimitive.content), ring.date, "date") }
        expected["daysLeft"]?.let { assertEquals(it.jsonPrimitive.int, ring.daysLeft, "daysLeft") }
        if ("milestone" in expected) {
            val m = expected.getValue("milestone")
            if (m is JsonNull) {
                assertEquals(null, ring.milestone, "milestone")
            } else {
                assertEquals(m.jsonObject.str("unit"), ring.milestone?.unit?.wire, "milestone.unit")
                assertEquals(m.jsonObject.getValue("count").jsonPrimitive.int, ring.milestone?.count, "milestone.count")
            }
        }
    }

    private fun checkStrip(strip: GoalStrip, expected: JsonObject) {
        fun date(e: JsonElement) = if (e is JsonNull) null else LocalDate.parse(e.jsonPrimitive.content)
        expected["start"]?.let { assertEquals(date(it), strip.start, "start") }
        expected["end"]?.let { assertEquals(date(it), strip.end, "end") }
        expected["target"]?.let { assertEquals(date(it), strip.target, "target") }
        expected["solidEnd"]?.let { assertEquals(date(it), strip.solidEnd, "solidEnd") }
        expected["startPosition"]?.let { assertEquals(it.doubleValue(), strip.startPosition, 1e-6, "startPosition") }
        expected["todayPosition"]?.let { assertEquals(it.doubleValue(), strip.todayPosition, 1e-6, "todayPosition") }
        expected["targetPosition"]?.let {
            if (it is JsonNull) assertEquals(null, strip.targetPosition, "targetPosition")
            else assertEquals(it.doubleValue(), strip.targetPosition ?: Double.NaN, 1e-6, "targetPosition")
        }
        expected["tickCount"]?.let { assertEquals(it.jsonPrimitive.int, strip.ticks.size, "tickCount") }
        expected["firstTick"]?.let { assertEquals(date(it), strip.ticks.first().date, "firstTick") }
        expected["lastTick"]?.let { assertEquals(date(it), strip.ticks.last().date, "lastTick") }
        expected["yearTicks"]?.let {
            assertEquals(it.jsonArray.map(::date), strip.ticks.filter { t -> t.yearStart }.map { t -> t.date }, "yearTicks")
        }
    }

    private fun checkBars(bars: BarsChart, maxBars: Int?, expected: JsonObject) {
        expected["unit"]?.let { assertEquals(it.jsonPrimitive.content, bars.unit.wire, "unit") }
        checkSegments(bars.bars.map { it.segment }, expected)
        expected["todayIndex"]?.let { assertEquals(it.intOrNull(), bars.todayIndex, "todayIndex") }
        expected["heights"]?.let {
            val wanted = it.jsonArray.map { e -> e.doubleValue() }
            assertEquals(wanted.size, bars.bars.size, "heights.size")
            wanted.zip(bars.bars).forEachIndexed { i, (w, b) -> assertEquals(w, b.height, 1e-6, "heights[$i]") }
        }
        expected["window"]?.let {
            val window = bars.window(requireNotNull(maxBars) { "window exige maxBars" })
            assertEquals(it.jsonObject.getValue("from").jsonPrimitive.int, window.first, "window.from")
            assertEquals(it.jsonObject.getValue("to").jsonPrimitive.int, window.last, "window.to")
        }
    }

    private fun checkArcs(arcs: ArcsChart, expected: JsonObject) {
        expected["unit"]?.let { assertEquals(it.jsonPrimitive.content, arcs.unit.wire, "unit") }
        expected["step"]?.let { assertEquals(it.jsonPrimitive.int, arcs.step, "step") }
        checkSegments(arcs.segments, expected)
        expected["current"]?.let { assertEquals(it.intOrNull(), arcs.currentIndex, "current") }
    }

    /** Champs communs aux découpages : count, states, elapsed, first, last. */
    private fun checkSegments(segments: List<ChartSegment>, expected: JsonObject) {
        expected["count"]?.let { assertEquals(it.jsonPrimitive.int, segments.size, "count") }
        expected["states"]?.let {
            assertEquals(it.jsonPrimitive.content, segments.joinToString("") { s -> s.state.letter.toString() }, "states")
        }
        expected["elapsed"]?.let {
            assertEquals(it.jsonArray.map { e -> e.jsonPrimitive.int }, segments.map { s -> s.elapsedDays }, "elapsed")
        }
        expected["first"]?.let { assertRange(it.jsonObject, segments.first(), "first") }
        expected["last"]?.let { assertRange(it.jsonObject, segments.last(), "last") }
    }

    private fun assertRange(expected: JsonObject, segment: ChartSegment, label: String) {
        assertEquals(LocalDate.parse(expected.str("start")), segment.start, "$label.start")
        assertEquals(LocalDate.parse(expected.str("end")), segment.end, "$label.end")
    }

    private fun JsonElement.intOrNull(): Int? = if (this is JsonNull) null else jsonPrimitive.int

    private fun JsonElement.doubleValue(): Double = jsonPrimitive.double

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
}
