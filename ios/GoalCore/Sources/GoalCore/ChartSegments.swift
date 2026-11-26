// Non vérifié : écrit sans Xcode (machine Linux).

/// Graphique affiché dans le détail ou un widget. `rawValue` = valeur stockée (`detail_chart`,
/// paramètre du widget), identique au `wire` Android (`ChartKind.kt`).
public enum ChartKind: String, CaseIterable, Sendable {
    case dots
    case ring
    case arcs
    case bars
}

/// Durée d'une période des graphiques arcs/barres. `rawValue` = valeur des cas partagés.
public enum SegmentUnit: String, Sendable {
    case day
    case week
    case month
    case year
}

/// Position d'une période par rapport à aujourd'hui.
public enum SegmentState: Sendable {
    /// Finie avant aujourd'hui.
    case elapsed
    /// Contient aujourd'hui.
    case current
    /// Commence après aujourd'hui.
    case remaining

    /// Notation compacte des cas partagés (e/t/r).
    public var letter: Character {
        switch self {
        case .elapsed: return "e"
        case .current: return "t"
        case .remaining: return "r"
        }
    }
}

/// Période [start, end] (bornes incluses) : découpée sur le calendrier (semaine lundi → dimanche, mois,
/// année civile) puis rognée à l'étendue représentée. `elapsedDays` = jours écoulés au jour du calcul,
/// aujourd'hui compté comme entamé (0 pour une période future, `days` pour une période passée).
public struct ChartSegment: Equatable, Sendable {
    public let start: LocalDate
    public let end: LocalDate
    public let state: SegmentState
    public let elapsedDays: Int

    public init(start: LocalDate, end: LocalDate, state: SegmentState, elapsedDays: Int) {
        self.start = start
        self.end = end
        self.state = state
        self.elapsedDays = elapsedDays
    }

    public var days: Int { start.days(until: end) + 1 }

    /// Part écoulée 0.0–1.0 (remplissage de l'arc ou de la barre).
    public var fill: Double { Double(elapsedDays) / Double(days) }

    public func contains(_ day: LocalDate) -> Bool { start <= day && day <= end }
}

/// Étendue représentée par les graphiques arcs et barres : départ → cible pour un compte à rebours
/// (cible avant le départ ramenée au départ), départ → aujourd'hui pour « depuis » (départ futur
/// ramené à aujourd'hui).
public struct ChartSpan: Equatable, Sendable {
    public let start: LocalDate
    public let end: LocalDate

    public init(start: LocalDate, end: LocalDate) {
        self.start = start
        self.end = end
    }

    public var days: Int { start.days(until: end) + 1 }

    public static func of(_ goal: Goal, today: LocalDate) -> ChartSpan {
        switch goal.kind {
        case .countdown:
            guard let target = goal.targetDate else { preconditionFailure("Compte à rebours sans cible") }
            return ChartSpan(start: goal.startDate, end: max(goal.startDate, target))
        case .since:
            return ChartSpan(start: min(goal.startDate, today), end: today)
        }
    }

    /// Découpe en périodes de `step` `unit` (seul `.year` utilise `step`), état calculé pour `today`.
    public func split(_ unit: SegmentUnit, today: LocalDate, step: Int = 1) -> [ChartSegment] {
        var out: [ChartSegment] = []
        var s = start
        while s <= end {
            let natural: LocalDate
            switch unit {
            case .day:
                natural = s
            case .week:
                // Dimanche suivant (ou le jour même).
                natural = s.adding(days: 6 - s.isoWeekdayIndex)
            case .month:
                natural = s.lastDayOfMonth
            case .year:
                natural = LocalDate(year: s.year + step - 1, month: 12, day: 31)
            }
            let e = min(natural, end)
            out.append(Self.segment(s, e, today: today))
            s = e.adding(days: 1)
        }
        return out
    }

    private static func segment(_ s: LocalDate, _ e: LocalDate, today: LocalDate) -> ChartSegment {
        if e < today {
            return ChartSegment(start: s, end: e, state: .elapsed, elapsedDays: s.days(until: e) + 1)
        }
        if s > today {
            return ChartSegment(start: s, end: e, state: .remaining, elapsedDays: 0)
        }
        return ChartSegment(start: s, end: e, state: .current, elapsedDays: s.days(until: today) + 1)
    }
}

/// Arcs concentriques : un arc par période, le premier à l'extérieur. Jusqu'à 62 jours un arc par
/// semaine, jusqu'à 12 mois calendaires un par mois, au-delà un par an, ou par `step` ans pour ne pas
/// dépasser 12 arcs.
public struct ArcsChart: Equatable, Sendable {
    public let unit: SegmentUnit
    public let step: Int
    public let segments: [ChartSegment]

    public init(unit: SegmentUnit, step: Int, segments: [ChartSegment]) {
        self.unit = unit
        self.step = step
        self.segments = segments
    }

    /// Arc contenant aujourd'hui, nil si aujourd'hui est hors de l'étendue.
    public var currentIndex: Int? { segments.firstIndex { $0.state == .current } }

    static let maxWeekDays = 62
    static let maxArcs = 12

    public static func compute(_ goal: Goal, today: LocalDate) -> ArcsChart {
        let span = ChartSpan.of(goal, today: today)
        let months = (span.end.year - span.start.year) * 12 + (span.end.month - span.start.month) + 1
        if span.days <= maxWeekDays {
            return ArcsChart(unit: .week, step: 1, segments: span.split(.week, today: today))
        }
        if months <= maxArcs {
            return ArcsChart(unit: .month, step: 1, segments: span.split(.month, today: today))
        }
        let years = span.end.year - span.start.year + 1
        let step = (years + maxArcs - 1) / maxArcs
        return ArcsChart(unit: .year, step: step, segments: span.split(.year, today: today, step: step))
    }
}

/// Barre : sa période et sa hauteur relative (0.06–1.0, chemin cumulé à la fin de la période).
public struct Bar: Equatable, Sendable {
    public let segment: ChartSegment
    public let height: Double

    public init(segment: ChartSegment, height: Double) {
        self.segment = segment
        self.height = height
    }
}

/// Barres arrondies : une par jour sous 3 semaines, par semaine sous 3 mois, sinon par mois. La hauteur
/// est le chemin cumulé à la fin de la période rapporté à l'étendue (escalier qui monte vers la cible,
/// ou vers aujourd'hui pour « depuis »), au moins `minHeight` pour rester visible.
public struct BarsChart: Equatable, Sendable {
    public let unit: SegmentUnit
    public let bars: [Bar]

    public init(unit: SegmentUnit, bars: [Bar]) {
        self.unit = unit
        self.bars = bars
    }

    public static let minHeight = 0.06
    static let maxDayBarsDays = 20

    /// Barre contenant aujourd'hui, nil si aujourd'hui est hors de l'étendue.
    public var todayIndex: Int? { bars.firstIndex { $0.segment.state == .current } }

    /// Barres à montrer quand il n'en tient que `maxBars` (widget) : fenêtre placée pour qu'aujourd'hui
    /// soit aux deux tiers ; avant l'étendue, le début ; après, la fin. Bornes incluses.
    public func window(maxBars: Int) -> ClosedRange<Int> {
        precondition(maxBars >= 1, "maxBars < 1")
        guard !bars.isEmpty else { return 0...0 }
        if bars.count <= maxBars { return 0...(bars.count - 1) }
        let anchor = todayIndex ?? (bars[0].segment.state == .remaining ? 0 : bars.count - 1)
        let from = min(max(anchor - maxBars * 2 / 3, 0), bars.count - maxBars)
        return from...(from + maxBars - 1)
    }

    public static func compute(_ goal: Goal, today: LocalDate) -> BarsChart {
        let span = ChartSpan.of(goal, today: today)
        let unit: SegmentUnit
        if span.days <= maxDayBarsDays {
            unit = .day
        } else if span.end < span.start.adding(months: 3) {
            unit = .week
        } else {
            unit = .month
        }
        var cumulative = 0
        let bars = span.split(unit, today: today).map { segment -> Bar in
            cumulative += segment.days
            return Bar(segment: segment, height: max(Double(cumulative) / Double(span.days), minHeight))
        }
        return BarsChart(unit: unit, bars: bars)
    }
}

extension LocalDate {
    /// Premier jour du mois.
    public var firstDayOfMonth: LocalDate { LocalDate(year: year, month: month, day: 1) }

    /// Dernier jour du mois.
    public var lastDayOfMonth: LocalDate {
        LocalDate(year: year, month: month, day: LocalDate.daysInMonth(year: year, month: month))
    }
}
