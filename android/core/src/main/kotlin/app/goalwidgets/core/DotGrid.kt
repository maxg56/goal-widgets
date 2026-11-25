package app.goalwidgets.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Période affichée par le widget « Grille ». `wire` = valeur des cas partagés et du stockage. */
enum class DotPeriod(val wire: String) {
    /** Tout l'objectif (départ → cible, ou départ → aujourd'hui pour « depuis »). */
    WHOLE("whole"),

    /** Mois calendaire contenant aujourd'hui. */
    MONTH("month"),

    /** Semaine ISO (lundi → dimanche) contenant aujourd'hui. */
    WEEK("week");

    companion object {
        fun fromWire(value: String?): DotPeriod? = entries.firstOrNull { it.wire == value }
    }
}

/** Durée représentée par un point. */
enum class DotUnit(val wire: String) { DAY("day"), WEEK("week"), MONTH("month") }

/** État d'un point ; `letter` = notation compacte des cas partagés. */
enum class DotState(val letter: Char) {
    /** Entièrement hors de l'objectif (avant le départ, ou après la cible). Périodes mois/semaine. */
    OUTSIDE('o'),

    /** Contient aujourd'hui. */
    TODAY('t'),

    /** Finit avant aujourd'hui. */
    ELAPSED('e'),

    /** Commence après aujourd'hui. */
    REMAINING('r'),
}

/** Un point : plage de dates [start, end] (bornes incluses). */
data class DotCell(val start: LocalDate, val end: LocalDate, val state: DotState)

/**
 * Grille de points du widget « Grille » (ticket 12) : un point par jour, semaine ou mois.
 * Règles figées par `shared-cases/dotgrid.json`.
 */
data class DotGrid(val unit: DotUnit, val cells: List<DotCell>) {
    companion object {
        /** Au-delà de 366 cellules, on passe à l'unité suivante (jour → semaine → mois). */
        private const val MAX_CELLS = 366L

        fun compute(goal: Goal, today: LocalDate, period: DotPeriod): DotGrid {
            // Étendue de l'objectif : départ → cible (compte à rebours) ou → sans fin (« depuis »).
            // Cible avant le départ (possible via un import) : l'objectif se réduit à son départ.
            val goalStart = goal.startDate
            val goalEnd: LocalDate? = when (goal.kind) {
                GoalKind.COUNTDOWN -> maxOf(goalStart, requireNotNull(goal.targetDate) { "Compte à rebours sans cible" })
                GoalKind.SINCE -> null
            }

            val (rangeStart, rangeEnd) = when (period) {
                DotPeriod.WHOLE -> when (goal.kind) {
                    GoalKind.COUNTDOWN -> goalStart to goalEnd!!
                    // « Depuis » à départ futur : une seule cellule, aujourd'hui.
                    GoalKind.SINCE -> if (goalStart > today) today to today else goalStart to today
                }
                DotPeriod.MONTH ->
                    today.withDayOfMonth(1) to today.with(TemporalAdjusters.lastDayOfMonth())
                DotPeriod.WEEK ->
                    today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) to
                        today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            }

            val days = ChronoUnit.DAYS.between(rangeStart, rangeEnd) + 1
            val unit = when {
                days <= MAX_CELLS -> DotUnit.DAY
                (days + 6) / 7 <= MAX_CELLS -> DotUnit.WEEK
                else -> DotUnit.MONTH
            }

            val cells = mutableListOf<DotCell>()
            var cellStart = rangeStart
            while (cellStart <= rangeEnd) {
                val natural = when (unit) {
                    DotUnit.DAY -> cellStart
                    DotUnit.WEEK -> cellStart.plusDays(6)
                    DotUnit.MONTH -> cellStart.with(TemporalAdjusters.lastDayOfMonth())
                }
                val cellEnd = minOf(natural, rangeEnd)
                // `outside` n'existe qu'en périodes mois/semaine : en « objectif entier » la plage est
                // l'objectif lui-même (« depuis » à départ futur → une cellule `today`).
                val outside = period != DotPeriod.WHOLE &&
                    (cellEnd < goalStart || (goalEnd != null && cellStart > goalEnd))
                val state = when {
                    outside -> DotState.OUTSIDE
                    cellEnd < today -> DotState.ELAPSED
                    cellStart > today -> DotState.REMAINING
                    else -> DotState.TODAY
                }
                cells += DotCell(cellStart, cellEnd, state)
                cellStart = cellEnd.plusDays(1)
            }
            return DotGrid(unit, cells)
        }
    }
}
