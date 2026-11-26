package app.goalwidgets.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** Mise en page pure des graphiques et personnalisation (détail, widget) : pas de cas partagés. */
class ChartLayoutTest {

    @Test
    fun `dot grid takes the column count that gives the biggest dots`() {
        // 11 points dans 300 × 100 : 6 colonnes × 2 lignes de 50.
        assertEquals(DotGridLayout(columns = 6, rows = 2, pitch = 50f), DotGridLayout.fit(11, 300f, 100f))
    }

    @Test
    fun `dot grid in a tall area stacks into a single column`() {
        assertEquals(DotGridLayout(columns = 1, rows = 3, pitch = 10f), DotGridLayout.fit(3, 10f, 300f))
    }

    @Test
    fun `strip labels every month when there is room, otherwise every 2, 3, 6 or 12 months`() {
        assertEquals(1, GoalStrip.labelStep(monthWidth = 60.0, minSpacing = 40.0))
        assertEquals(2, GoalStrip.labelStep(monthWidth = 25.0, minSpacing = 40.0))
        assertEquals(3, GoalStrip.labelStep(monthWidth = 15.0, minSpacing = 40.0))
        assertEquals(12, GoalStrip.labelStep(monthWidth = 1.0, minSpacing = 40.0))
    }

    @Test
    fun `all goals timeline is sorted by date with today before the first goal not yet past`() {
        val today = LocalDate.of(2026, 11, 26)
        val trip = Goal(GoalKind.COUNTDOWN, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 10), "Voyage")
        val quit = Goal(GoalKind.SINCE, LocalDate.of(2026, 1, 1), title = "Arrêt")
        val exam = Goal(GoalKind.COUNTDOWN, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 1), "Examen")
        val party = Goal(GoalKind.COUNTDOWN, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 26), "Fête")

        val rows = GoalsTimeline.arrange(listOf(trip, quit, exam, party), today) { it }

        assertEquals(
            listOf("Arrêt", "Examen", "today", "Fête", "Voyage"),
            rows.map { if (it is TimelineRow.Node) it.item.title else "today" },
        )
        assertEquals(LocalDate.of(2026, 1, 1), (rows[0] as TimelineRow.Node).date)
    }

    @Test
    fun `all goals timeline ends with today when every date is past`() {
        val today = LocalDate.of(2026, 11, 26)
        val quit = Goal(GoalKind.SINCE, LocalDate.of(2026, 1, 1))
        assertEquals(TimelineRow.Today, GoalsTimeline.arrange(listOf(quit), today) { it }.last())
    }

    @Test
    fun `detail layout hides and restores parts and is stored as text`() {
        val layout = DetailLayout.DEFAULT.hide(DetailPart.CAPTION).hide(ChartKind.BARS)

        assertEquals(false, layout.shows(DetailPart.CAPTION))
        assertEquals(listOf(ChartKind.DOTS, ChartKind.RING, ChartKind.ARCS), layout.charts)
        assertEquals(layout, DetailLayout.decode(layout.encode()))
        assertEquals(DetailLayout.DEFAULT, layout.restore(DetailPart.CAPTION).restore(ChartKind.BARS))
    }

    @Test
    fun `detail layout keeps at least one chart`() {
        val one = DetailLayout.DEFAULT.hide(ChartKind.DOTS).hide(ChartKind.RING).hide(ChartKind.ARCS)
        assertEquals(one, one.hide(ChartKind.BARS))
        assertEquals(ChartKind.BARS, one.chartToShow(preferred = ChartKind.RING))
    }

    @Test
    fun `detail layout ignores unknown stored keys`() {
        assertEquals(DetailLayout.DEFAULT.hide(DetailPart.TIMELINE), DetailLayout.decode("timeline,unknown,chart:pie,"))
        assertEquals(DetailLayout.DEFAULT, DetailLayout.decode(null))
    }

    @Test
    fun `widget config defaults to the ring with everything shown, as for widgets placed before charts existed`() {
        assertEquals(WidgetChartConfig(ChartKind.RING, emptySet()), WidgetChartConfig.decode(chart = null, hidden = null))
    }

    @Test
    fun `widget config round trips and keeps at least one part`() {
        val config = WidgetChartConfig(ChartKind.BARS, emptySet()).hide(WidgetPart.TITLE).hide(WidgetPart.TIMELINE)
        assertEquals(config, WidgetChartConfig.decode(config.chart.wire, config.encodeHidden()))

        val last = WidgetPart.entries.drop(1).fold(WidgetChartConfig.DEFAULT) { c, p -> c.hide(p) }
        assertEquals(last, last.hide(WidgetPart.entries.first()))
    }

    @Test
    fun `widget template follows the real size, one-row widgets are compact`() {
        assertEquals(WidgetSize.COMPACT, WidgetSize.of(widthDp = 250f, heightDp = 60f))
        assertEquals(WidgetSize.SMALL, WidgetSize.of(widthDp = 150f, heightDp = 150f))
        assertEquals(WidgetSize.MEDIUM, WidgetSize.of(widthDp = 320f, heightDp = 150f))
        assertEquals(WidgetSize.LARGE, WidgetSize.of(widthDp = 320f, heightDp = 320f))
    }
}
