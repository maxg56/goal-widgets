package app.goalwidgets.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Graphique affiché dans le détail ou un widget. `wire` = valeur stockée (préférences, état du widget). */
enum class ChartKind(val wire: String) {
    DOTS("dots"),
    RING("ring"),
    ARCS("arcs"),
    BARS("bars");

    companion object {
        fun fromWire(value: String?): ChartKind? = entries.firstOrNull { it.wire == value }
    }
}

/** Durée d'une période des graphiques arcs/barres. `wire` = valeur des cas partagés. */
enum class SegmentUnit(val wire: String) { DAY("day"), WEEK("week"), MONTH("month"), YEAR("year") }

/** Position d'une période par rapport à aujourd'hui ; `letter` = notation compacte des cas partagés. */
enum class SegmentState(val letter: Char) {
    /** Finie avant aujourd'hui. */
    ELAPSED('e'),

    /** Contient aujourd'hui. */
    CURRENT('t'),

    /** Commence après aujourd'hui. */
    REMAINING('r'),
}

/**
 * Période [start, end] (bornes incluses) : découpée sur le calendrier (semaine lundi → dimanche, mois,
 * année civile) puis rognée à l'étendue représentée. [elapsedDays] = jours écoulés au jour du calcul,
 * aujourd'hui compté comme entamé (0 pour une période future, [days] pour une période passée).
 */
data class ChartSegment(val start: LocalDate, val end: LocalDate, val state: SegmentState, val elapsedDays: Int) {
    val days: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1

    /** Part écoulée 0.0–1.0 (remplissage de l'arc ou de la barre). */
    val fill: Double get() = elapsedDays.toDouble() / days

    operator fun contains(day: LocalDate): Boolean = day in start..end
}

/**
 * Étendue représentée par les graphiques arcs et barres : départ → cible pour un compte à rebours
 * (cible avant le départ ramenée au départ), départ → aujourd'hui pour « depuis » (départ futur
 * ramené à aujourd'hui).
 */
data class ChartSpan(val start: LocalDate, val end: LocalDate) {
    val days: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1

    /** Découpe en périodes de [step] [unit] (seul `YEAR` utilise [step]), état calculé pour [today]. */
    fun split(unit: SegmentUnit, today: LocalDate, step: Int = 1): List<ChartSegment> {
        val out = mutableListOf<ChartSegment>()
        var s = start
        while (s <= end) {
            val natural = when (unit) {
                SegmentUnit.DAY -> s
                SegmentUnit.WEEK -> s.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                SegmentUnit.MONTH -> s.with(TemporalAdjusters.lastDayOfMonth())
                SegmentUnit.YEAR -> s.plusYears(step - 1L).with(TemporalAdjusters.lastDayOfYear())
            }
            val e = minOf(natural, end)
            out += segment(s, e, today)
            s = e.plusDays(1)
        }
        return out
    }

    private fun segment(s: LocalDate, e: LocalDate, today: LocalDate): ChartSegment {
        val length = ChronoUnit.DAYS.between(s, e).toInt() + 1
        return when {
            e < today -> ChartSegment(s, e, SegmentState.ELAPSED, length)
            s > today -> ChartSegment(s, e, SegmentState.REMAINING, 0)
            else -> ChartSegment(s, e, SegmentState.CURRENT, ChronoUnit.DAYS.between(s, today).toInt() + 1)
        }
    }

    companion object {
        fun of(goal: Goal, today: LocalDate): ChartSpan = when (goal.kind) {
            GoalKind.COUNTDOWN -> {
                val target = requireNotNull(goal.targetDate) { "Compte à rebours sans cible" }
                ChartSpan(goal.startDate, maxOf(goal.startDate, target))
            }
            GoalKind.SINCE -> ChartSpan(minOf(goal.startDate, today), today)
        }
    }
}

/**
 * Arcs concentriques : un arc par période, le premier à l'extérieur. Jusqu'à 62 jours un arc par
 * semaine, jusqu'à 12 mois calendaires un par mois, au-delà un par an, ou par [step] ans pour ne pas
 * dépasser 12 arcs.
 */
data class ArcsChart(val unit: SegmentUnit, val step: Int, val segments: List<ChartSegment>) {
    /** Arc contenant aujourd'hui, `null` si aujourd'hui est hors de l'étendue. */
    val currentIndex: Int? get() = segments.indexOfFirst { it.state == SegmentState.CURRENT }.takeIf { it >= 0 }

    companion object {
        private const val MAX_WEEK_DAYS = 62
        private const val MAX_ARCS = 12

        /**
         * [unit] : unité préférée ([ChartUnit.AUTO] = règle ci-dessus). Une unité forcée qui donnerait
         * plus de [MAX_ARCS] arcs passe à l'unité au-dessus (jour → semaine → mois → années).
         */
        fun compute(goal: Goal, today: LocalDate, unit: ChartUnit = ChartUnit.AUTO): ArcsChart {
            val span = ChartSpan.of(goal, today)
            preferredUnits(unit).forEach { u ->
                val segments = span.split(u, today)
                if (segments.size <= MAX_ARCS) return ArcsChart(u, 1, segments)
            }
            val months = ChronoUnit.MONTHS.between(span.start.withDayOfMonth(1), span.end.withDayOfMonth(1)) + 1
            return when {
                span.days <= MAX_WEEK_DAYS -> ArcsChart(SegmentUnit.WEEK, 1, span.split(SegmentUnit.WEEK, today))
                months <= MAX_ARCS -> ArcsChart(SegmentUnit.MONTH, 1, span.split(SegmentUnit.MONTH, today))
                else -> {
                    val years = span.end.year - span.start.year + 1
                    val step = (years + MAX_ARCS - 1) / MAX_ARCS
                    ArcsChart(SegmentUnit.YEAR, step, span.split(SegmentUnit.YEAR, today, step))
                }
            }
        }
    }
}

/** Barre : sa période et sa hauteur relative (0.06–1.0, chemin cumulé à la fin de la période). */
data class Bar(val segment: ChartSegment, val height: Double)

/**
 * Barres arrondies : une par jour sous 3 semaines, par semaine sous 3 mois, sinon par mois. La hauteur
 * est le chemin cumulé à la fin de la période rapporté à l'étendue (escalier qui monte vers la cible,
 * ou vers aujourd'hui pour « depuis »), au moins [MIN_HEIGHT] pour rester visible.
 */
data class BarsChart(val unit: SegmentUnit, val bars: List<Bar>) {
    /** Barre contenant aujourd'hui, `null` si aujourd'hui est hors de l'étendue. */
    val todayIndex: Int? get() = bars.indexOfFirst { it.segment.state == SegmentState.CURRENT }.takeIf { it >= 0 }

    /**
     * Barres à montrer quand il n'en tient que [maxBars] (widget) : fenêtre placée pour qu'aujourd'hui
     * soit aux deux tiers ; avant l'étendue, le début ; après, la fin.
     */
    fun window(maxBars: Int): IntRange {
        require(maxBars >= 1) { "maxBars ($maxBars) < 1" }
        if (bars.size <= maxBars) return bars.indices
        val anchor = todayIndex ?: if (bars.first().segment.state == SegmentState.REMAINING) 0 else bars.lastIndex
        val from = (anchor - maxBars * 2 / 3).coerceIn(0, bars.size - maxBars)
        return from until from + maxBars
    }

    companion object {
        const val MIN_HEIGHT = 0.06
        private const val MAX_DAY_BARS_DAYS = 20

        /** Au-delà, une unité forcée passe à l'unité au-dessus (le mois reste le dernier recours). */
        private const val MAX_FORCED_BARS = 31

        /**
         * [unit] : unité préférée ([ChartUnit.AUTO] = règle ci-dessus). Une unité forcée qui donnerait
         * plus de [MAX_FORCED_BARS] barres passe à l'unité au-dessus (jour → semaine → mois).
         */
        fun compute(goal: Goal, today: LocalDate, unit: ChartUnit = ChartUnit.AUTO): BarsChart {
            val span = ChartSpan.of(goal, today)
            val segments = if (unit == ChartUnit.AUTO) {
                val auto = when {
                    span.days <= MAX_DAY_BARS_DAYS -> SegmentUnit.DAY
                    span.end < span.start.plusMonths(3) -> SegmentUnit.WEEK
                    else -> SegmentUnit.MONTH
                }
                auto to span.split(auto, today)
            } else {
                preferredUnits(unit).map { it to span.split(it, today) }
                    .firstOrNull { (u, s) -> s.size <= MAX_FORCED_BARS || u == SegmentUnit.MONTH }!!
            }
            var cumulative = 0
            val bars = segments.second.map { segment ->
                cumulative += segment.days
                Bar(segment, maxOf(cumulative.toDouble() / span.days, MIN_HEIGHT))
            }
            return BarsChart(segments.first, bars)
        }
    }
}

/** Unités à essayer, de la préférée à la plus grossière ; vide pour [ChartUnit.AUTO]. */
internal fun preferredUnits(unit: ChartUnit): List<SegmentUnit> = when (unit) {
    ChartUnit.AUTO -> emptyList()
    ChartUnit.DAY -> listOf(SegmentUnit.DAY, SegmentUnit.WEEK, SegmentUnit.MONTH)
    ChartUnit.WEEK -> listOf(SegmentUnit.WEEK, SegmentUnit.MONTH)
    ChartUnit.MONTH -> listOf(SegmentUnit.MONTH)
}
