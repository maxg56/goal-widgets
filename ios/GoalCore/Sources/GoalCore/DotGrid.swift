// Non vérifié : écrit sans Xcode (machine Linux).

/// Grille de points du widget « Grille » (ticket 12) : un point par unité de temps.
/// Calcul pur, figé par `shared-cases/dotgrid.json` (même règles que `DotGrid.kt`).
public struct DotGrid: Equatable, Sendable {
    /// Période affichée, choisie à la configuration du widget. `rawValue` = valeur des cas partagés.
    public enum Period: String, CaseIterable, Sendable {
        /// Objectif entier : départ … cible (compte à rebours) ou départ … aujourd'hui (« depuis »).
        case whole
        /// Mois calendaire contenant aujourd'hui.
        case month
        /// Semaine ISO (lundi … dimanche) contenant aujourd'hui.
        case week
    }

    /// Durée représentée par une cellule.
    public enum Unit: String, Sendable {
        case day
        /// 7 jours à partir du début de la plage (la dernière cellule peut être plus courte).
        case week
        /// Mois calendaire (première et dernière cellule tronquées aux bornes de la plage).
        case month
    }

    public enum CellState: String, Sendable {
        /// Entièrement hors de l'objectif (périodes `month`/`week` seulement). Évalué en premier.
        case outside
        /// Contient aujourd'hui.
        case today
        /// Finit avant aujourd'hui.
        case elapsed
        /// Commence après aujourd'hui.
        case remaining

        /// Lettre de la forme compacte des cas partagés.
        public var letter: Character {
            switch self {
            case .outside: return "o"
            case .today: return "t"
            case .elapsed: return "e"
            case .remaining: return "r"
            }
        }
    }

    public struct Cell: Equatable, Sendable {
        /// Premier jour de la cellule (inclus).
        public let start: LocalDate
        /// Dernier jour de la cellule (inclus).
        public let end: LocalDate
        public let state: CellState

        public init(start: LocalDate, end: LocalDate, state: CellState) {
            self.start = start
            self.end = end
            self.state = state
        }
    }

    public let unit: Unit
    public let cells: [Cell]

    public init(unit: Unit, cells: [Cell]) {
        self.unit = unit
        self.cells = cells
    }

    /// Une lettre par cellule (`e`/`t`/`r`/`o`), comme `expected.states` des cas partagés.
    public var compactStates: String {
        String(cells.map(\.state.letter))
    }

    /// Plafonds (inclus) avant de passer à l'unité supérieure.
    static let maxDayCells = 366
    static let maxWeekCells = 366

    public static func compute(_ goal: Goal, today: LocalDate, period: Period) -> DotGrid {
        let (lower, upper) = range(of: goal, today: today, period: period)
        let unit = period == .whole ? unitFor(lower: lower, upper: upper) : .day

        var cells: [Cell] = []
        var cursor = lower
        while cursor <= upper {
            let end = cellEnd(start: cursor, upper: upper, unit: unit)
            cells.append(Cell(start: cursor, end: end, state: state(start: cursor, end: end, goal: goal, today: today, period: period)))
            cursor = end.adding(days: 1)
        }
        return DotGrid(unit: unit, cells: cells)
    }

    /// Bornes incluses de la plage affichée.
    static func range(of goal: Goal, today: LocalDate, period: Period) -> (LocalDate, LocalDate) {
        switch period {
        case .whole:
            switch goal.kind {
            case .countdown:
                // Cible antérieure au départ (non prévu par le ticket) : plage ramenée au seul jour de départ.
                let target = goal.targetDate ?? goal.startDate
                return (goal.startDate, max(goal.startDate, target))
            case .since:
                // Départ futur : une seule cellule, aujourd'hui.
                return goal.startDate <= today ? (goal.startDate, today) : (today, today)
            }
        case .month:
            let first = LocalDate(year: today.year, month: today.month, day: 1)
            let last = LocalDate(year: today.year, month: today.month,
                                 day: LocalDate.daysInMonth(year: today.year, month: today.month))
            return (first, last)
        case .week:
            let monday = today.adding(days: -today.isoWeekdayIndex)
            return (monday, monday.adding(days: 6))
        }
    }

    static func unitFor(lower: LocalDate, upper: LocalDate) -> Unit {
        let days = lower.days(until: upper) + 1
        if days <= maxDayCells { return .day }
        let weeks = (days + 6) / 7
        if weeks <= maxWeekCells { return .week }
        return .month
    }

    private static func cellEnd(start: LocalDate, upper: LocalDate, unit: Unit) -> LocalDate {
        switch unit {
        case .day:
            return start
        case .week:
            return min(start.adding(days: 6), upper)
        case .month:
            let monthEnd = LocalDate(year: start.year, month: start.month,
                                     day: LocalDate.daysInMonth(year: start.year, month: start.month))
            return min(monthEnd, upper)
        }
    }

    private static func state(start: LocalDate, end: LocalDate, goal: Goal, today: LocalDate, period: Period) -> CellState {
        if period != .whole {
            if end < goal.startDate { return .outside }
            if goal.kind == .countdown, let target = goal.targetDate, start > target { return .outside }
        }
        if start <= today && today <= end { return .today }
        if end < today { return .elapsed }
        return .remaining
    }
}

extension LocalDate {
    /// Jour de la semaine ISO, 0 = lundi … 6 = dimanche (calcul arithmétique, sans calendrier ni fuseau).
    public var isoWeekdayIndex: Int {
        // 1970-01-05 était un lundi.
        let offset = LocalDate(year: 1970, month: 1, day: 5).days(until: self) % 7
        return offset < 0 ? offset + 7 : offset
    }
}
