package app.goalwidgets.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** Unité préférée des graphiques : héritage objectif → réglage général, et repli si illisible. */
class ChartUnitTest {
    private val today = LocalDate.of(2026, 11, 26)

    /** Compte à rebours de [days] jours (bornes incluses) commençant aujourd'hui. */
    private fun countdown(days: Long) = Goal(GoalKind.COUNTDOWN, today, today.plusDays(days - 1))

    @Test
    fun `goal setting wins, otherwise the global setting applies`() {
        assertEquals(ChartUnit.DAY, ChartUnit.resolve(goal = ChartUnit.DAY, global = ChartUnit.WEEK))
        assertEquals(ChartUnit.WEEK, ChartUnit.resolve(goal = null, global = ChartUnit.WEEK))
        assertEquals(ChartUnit.AUTO, ChartUnit.resolve(goal = ChartUnit.AUTO, global = ChartUnit.MONTH))
    }

    @Test
    fun `wire values round-trip and unknown values are null`() {
        ChartUnit.entries.forEach { assertEquals(it, ChartUnit.fromWire(it.wire)) }
        assertNull(ChartUnit.fromWire(null))
        assertNull(ChartUnit.fromWire("year"))
    }

    @Test
    fun `bars follow the preferred unit while readable`() {
        // 40 jours : auto = par semaine ; jour forcé = trop de barres → semaine.
        assertEquals(SegmentUnit.WEEK, BarsChart.compute(countdown(40), today).unit)
        assertEquals(SegmentUnit.WEEK, BarsChart.compute(countdown(40), today, ChartUnit.DAY).unit)
        // 30 jours : auto = par semaine, jour forcé tient (30 barres).
        assertEquals(SegmentUnit.WEEK, BarsChart.compute(countdown(30), today).unit)
        assertEquals(SegmentUnit.DAY, BarsChart.compute(countdown(30), today, ChartUnit.DAY).unit)
        // 10 jours : auto = par jour, semaine forcée respectée.
        assertEquals(SegmentUnit.DAY, BarsChart.compute(countdown(10), today).unit)
        assertEquals(SegmentUnit.WEEK, BarsChart.compute(countdown(10), today, ChartUnit.WEEK).unit)
        // 5 ans : le mois reste le dernier recours, même au-delà de la limite.
        assertEquals(SegmentUnit.MONTH, BarsChart.compute(countdown(5 * 365), today, ChartUnit.DAY).unit)
    }

    @Test
    fun `arcs follow the preferred unit, then climb up to years`() {
        assertEquals(SegmentUnit.DAY, ArcsChart.compute(countdown(10), today, ChartUnit.DAY).unit)
        assertEquals(SegmentUnit.WEEK, ArcsChart.compute(countdown(40), today, ChartUnit.DAY).unit)
        assertEquals(SegmentUnit.MONTH, ArcsChart.compute(countdown(40), today, ChartUnit.MONTH).unit)
        assertEquals(SegmentUnit.YEAR, ArcsChart.compute(countdown(5 * 365), today, ChartUnit.WEEK).unit)
        // Auto : inchangé (≤ 62 jours → semaines).
        assertEquals(SegmentUnit.WEEK, ArcsChart.compute(countdown(10), today).unit)
    }

    @Test
    fun `dot grid uses the preferred unit for the whole goal only`() {
        assertEquals(DotUnit.WEEK, DotGrid.compute(countdown(100), today, DotPeriod.WHOLE, ChartUnit.WEEK).unit)
        assertEquals(DotUnit.MONTH, DotGrid.compute(countdown(100), today, DotPeriod.WHOLE, ChartUnit.MONTH).unit)
        assertEquals(DotUnit.DAY, DotGrid.compute(countdown(100), today, DotPeriod.WHOLE, ChartUnit.DAY).unit)
        // Jour forcé sur 3 ans : trop de points → semaine.
        assertEquals(DotUnit.WEEK, DotGrid.compute(countdown(3 * 365), today, DotPeriod.WHOLE, ChartUnit.DAY).unit)
        // Période « mois » : toujours au jour.
        assertEquals(DotUnit.DAY, DotGrid.compute(countdown(100), today, DotPeriod.MONTH, ChartUnit.MONTH).unit)
    }
}
