// Non vérifié : écrit sans Xcode (machine Linux).

/// Unité d'un cap de l'anneau « depuis ». `rawValue` = valeur des cas partagés.
public enum MilestoneUnit: String, Sendable {
    case month
    case year
}

/// Cap visé par l'anneau d'un objectif « depuis » : « 1 mois », « 1 an », « 7 ans »…
public struct RingMilestone: Equatable, Sendable {
    public let unit: MilestoneUnit
    public let count: Int

    public init(unit: MilestoneUnit, count: Int) {
        self.unit = unit
        self.count = count
    }
}

/// Anneau de progression (même règle que `RingChart.kt`).
/// - Compte à rebours : `fraction`/`percent` = progression du moteur, `date` = cible.
/// - « Depuis » : progression vers le prochain cap `milestone` (1 mois, puis 1 an, puis chaque
///   anniversaire), `date` = jour du cap. Caps = mois-anniversaires de `Breakdown.between` (repli fin de
///   mois, comme les notifications) : un départ le 29 février fête ses ans le 28 février.
///
/// `daysLeft` = jours jusqu'à `date` (0 une fois atteinte), `percent` = partie entière (calcul entier).
public struct RingChart: Equatable, Sendable {
    public let fraction: Double
    public let percent: Int
    public let date: LocalDate
    public let daysLeft: Int
    public let milestone: RingMilestone?

    public init(fraction: Double, percent: Int, date: LocalDate, daysLeft: Int, milestone: RingMilestone?) {
        self.fraction = fraction
        self.percent = percent
        self.date = date
        self.daysLeft = daysLeft
        self.milestone = milestone
    }

    public static func compute(_ goal: Goal, today: LocalDate) -> RingChart {
        switch goal.kind {
        case .countdown:
            return countdown(goal, today: today)
        case .since:
            return since(start: goal.startDate, today: today)
        }
    }

    private static func countdown(_ goal: Goal, today: LocalDate) -> RingChart {
        guard let target = goal.targetDate else { preconditionFailure("Compte à rebours sans cible") }
        let state = CountdownEngine.compute(goal, today: today)
        return RingChart(
            fraction: state.progress ?? 0,
            percent: state.percent ?? 0,
            date: target,
            daysLeft: max(today.days(until: target), 0),
            milestone: nil
        )
    }

    private static func since(start: LocalDate, today: LocalDate) -> RingChart {
        let months: Int
        if today > start {
            let b = Breakdown.between(start, today)
            months = b.years * 12 + b.months
        } else {
            months = 0
        }
        let previous: LocalDate
        let next: LocalDate
        let milestone: RingMilestone
        if months < 1 {
            previous = start
            next = start.adding(months: 1)
            milestone = RingMilestone(unit: .month, count: 1)
        } else if months < 12 {
            previous = start
            next = start.adding(months: 12)
            milestone = RingMilestone(unit: .year, count: 1)
        } else {
            let years = months / 12
            previous = start.adding(months: 12 * years)
            next = start.adding(months: 12 * (years + 1))
            milestone = RingMilestone(unit: .year, count: years + 1)
        }
        let total = previous.days(until: next)
        let done = min(max(previous.days(until: today), 0), total)
        return RingChart(
            fraction: Double(done) / Double(total),
            percent: done * 100 / total,
            date: next,
            daysLeft: max(today.days(until: next), 0),
            milestone: milestone
        )
    }
}
