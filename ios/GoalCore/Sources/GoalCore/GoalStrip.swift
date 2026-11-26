// Non vérifié : écrit sans Xcode (machine Linux).

/// Graduation de la frise : 1er d'un mois ; `yearStart` pour un 1er janvier (graduation marquée).
public struct StripTick: Equatable, Sendable {
    public let date: LocalDate
    public let position: Double
    public let yearStart: Bool

    public init(date: LocalDate, position: Double, yearStart: Bool) {
        self.date = date
        self.position = position
        self.yearStart = yearStart
    }
}

/// Frise horizontale d'un objectif (bas du détail, mini frise du widget) : de `start` à `end`, positions
/// 0.0–1.0 proportionnelles aux jours (même règle que `GoalStrip.kt`).
/// - Compte à rebours : départ → cible, pleine jusqu'à `target`.
/// - « Depuis » ou cible dépassée : prolongée après aujourd'hui d'un dixième de l'étendue (au moins
///   10 jours), en pointillés après `solidEnd`.
/// Le départ futur d'un objectif (`goalStart` > aujourd'hui) est placé à `startPosition` ; la frise
/// commence alors aujourd'hui.
public struct GoalStrip: Equatable, Sendable {
    public let start: LocalDate
    public let end: LocalDate
    public let goalStart: LocalDate
    public let target: LocalDate?
    public let today: LocalDate
    public let ticks: [StripTick]

    public init(start: LocalDate, end: LocalDate, goalStart: LocalDate, target: LocalDate?, today: LocalDate, ticks: [StripTick]) {
        self.start = start
        self.end = end
        self.goalStart = goalStart
        self.target = target
        self.today = today
        self.ticks = ticks
    }

    private var totalDays: Int { max(start.days(until: end), 1) }

    /// Fin de la partie pleine : cible du compte à rebours, aujourd'hui pour « depuis ».
    public var solidEnd: LocalDate { target ?? today }

    public func position(_ date: LocalDate) -> Double {
        Double(start.days(until: date)) / Double(totalDays)
    }

    /// Jour le plus proche d'une position (appui sur la frise), borné à l'étendue.
    public func date(at position: Double) -> LocalDate {
        let clamped = min(max(position, 0), 1)
        return start.adding(days: Int((clamped * Double(totalDays)).rounded()))
    }

    public var startPosition: Double { position(goalStart) }
    public var todayPosition: Double { position(today) }
    public var targetPosition: Double? { target.map(position) }

    static let minMarginDays = 10

    public static func compute(_ goal: Goal, today: LocalDate) -> GoalStrip {
        let start = min(goal.startDate, today)
        let target: LocalDate?
        switch goal.kind {
        case .countdown:
            guard let t = goal.targetDate else { preconditionFailure("Compte à rebours sans cible") }
            target = max(goal.startDate, t)
        case .since:
            target = nil
        }
        let baseEnd = target ?? today
        let end: LocalDate
        if target == nil || today >= baseEnd {
            let last = max(baseEnd, today)
            let spanDays = max(start.days(until: last), 1)
            end = last.adding(days: max(minMarginDays, spanDays / 10))
        } else {
            end = baseEnd
        }
        let total = Double(max(start.days(until: end), 1))
        var ticks: [StripTick] = []
        var month = start.firstDayOfMonth.adding(months: 1)
        while month <= end {
            ticks.append(StripTick(date: month, position: Double(start.days(until: month)) / total, yearStart: month.month == 1))
            month = month.adding(months: 1)
        }
        return GoalStrip(start: start, end: end, goalStart: goal.startDate, target: target, today: today, ticks: ticks)
    }

    /// Une graduation sur combien porte une étiquette, pour qu'elles soient espacées d'au moins
    /// `minSpacing` quand un mois mesure `monthWidth` (mêmes unités) : 1, 2, 3, 6 ou 12.
    /// Les 1er janvier sont toujours étiquetés (année).
    public static func labelStep(monthWidth: Double, minSpacing: Double) -> Int {
        [1, 2, 3, 6].first { Double($0) * monthWidth >= minSpacing } ?? 12
    }
}
