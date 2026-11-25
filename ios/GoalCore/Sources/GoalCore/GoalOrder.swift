// Non vérifié : écrit sans Xcode (machine Linux).
import Foundation

/// Ce dont le tri a besoin pour un objectif ; `id` ne sert qu'à départager les égalités.
public struct GoalSortKey: Equatable, Sendable {
    public let id: String
    public let goal: Goal
    public let createdAt: Date

    public init(id: String, goal: Goal, createdAt: Date) {
        self.id = id
        self.goal = goal
        self.createdAt = createdAt
    }
}

/// Tri de la liste des objectifs (cas partagés : `shared-cases/sort.json`, même règle que
/// `GoalOrder.kt`). `rawValue` = valeur mémorisée dans les préférences (`goal_order`).
public enum GoalOrder: String, CaseIterable, Sendable {
    /// Comptes à rebours à venir (cible ≥ aujourd'hui, la plus proche d'abord), puis atteints
    /// (le plus récemment atteint d'abord), puis objectifs « depuis » (départ le plus récent d'abord).
    case closestDate
    /// Le plus récemment créé d'abord.
    case created

    public static let defaultOrder: GoalOrder = .closestDate

    /// Valeur mémorisée → ordre ; absente ou inconnue → ordre par défaut.
    public init(storedValue: String?) {
        self = storedValue.flatMap(GoalOrder.init(rawValue:)) ?? GoalOrder.defaultOrder
    }

    public func sort<T>(_ items: [T], today: LocalDate, key: (T) -> GoalSortKey) -> [T] {
        let keyed = items.map { (item: $0, key: key($0)) }
        return keyed.sorted { a, b in isOrderedBefore(a.key, b.key, today: today) }.map(\.item)
    }

    private func isOrderedBefore(_ a: GoalSortKey, _ b: GoalSortKey, today: LocalDate) -> Bool {
        if self == .closestDate {
            let ga = Self.group(a.goal, today: today), gb = Self.group(b.goal, today: today)
            if ga != gb { return ga < gb }
            let da = Self.distance(a.goal, today: today), db = Self.distance(b.goal, today: today)
            if da != db { return da < db }
        }
        if a.createdAt != b.createdAt { return a.createdAt > b.createdAt }
        return a.id < b.id
    }

    /// 0 = compte à rebours à venir ou du jour, 1 = atteint, 2 = « depuis ».
    private static func group(_ goal: Goal, today: LocalDate) -> Int {
        guard goal.kind == .countdown, let target = goal.targetDate else { return 2 }
        return target >= today ? 0 : 1
    }

    /// Écart en jours à la date pertinente (cible ou départ), croissant dans chaque groupe.
    private static func distance(_ goal: Goal, today: LocalDate) -> Int {
        let date = goal.kind == .countdown ? (goal.targetDate ?? goal.startDate) : goal.startDate
        return abs(today.days(until: date))
    }
}
