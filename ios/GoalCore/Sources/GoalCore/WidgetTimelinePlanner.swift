// Non vérifié : écrit sans Xcode (machine Linux).
import Foundation

/// Objectif tel que le widget le lit dans le stockage (`goal` nil si l'enregistrement est illisible).
public struct WidgetGoal: Equatable, Sendable {
    public let id: UUID
    public let title: String
    public let goal: Goal?

    public init(id: UUID, title: String, goal: Goal?) {
        self.id = id
        self.title = title
        self.goal = goal
    }
}

/// Ce qu'affiche un widget pour un jour donné.
public enum WidgetDisplay: Equatable, Sendable {
    /// Aucun objectif choisi dans la configuration du widget.
    case notConfigured
    /// L'objectif choisi n'existe plus (supprimé) ou est illisible.
    case goalDeleted
    /// Objectif et son état du jour, calculé par `CountdownEngine`.
    case goal(id: UUID, title: String, state: DisplayState)
}

/// Une entrée de timeline : valable à partir de minuit local de `day`.
public struct WidgetDayEntry: Equatable, Sendable {
    public let day: LocalDate
    public let display: WidgetDisplay

    public init(day: LocalDate, display: WidgetDisplay) {
        self.day = day
        self.display = display
    }
}

/// Timeline complète d'un widget.
public struct WidgetTimelinePlan: Equatable, Sendable {
    public let entries: [WidgetDayEntry]
    /// Jour (à minuit local) où WidgetKit doit redemander une timeline ; nil = pas de rechargement
    /// automatique (l'état ne change qu'après une action dans l'app, qui recharge les widgets).
    public let reloadOn: LocalDate?

    public init(entries: [WidgetDayEntry], reloadOn: LocalDate?) {
        self.entries = entries
        self.reloadOn = reloadOn
    }
}

/// Calcule la timeline d'un widget : une entrée par jour à partir d'aujourd'hui.
/// La couche WidgetKit convertit chaque `day` en instant via `LocalDate.startOfDay(in:)`.
public enum WidgetTimelinePlanner {
    /// Nombre d'entrées par défaut (une semaine) avant de redemander une timeline.
    public static let defaultDays = 7

    public static func plan(
        selectedGoalID: UUID?,
        goals: [WidgetGoal],
        today: LocalDate,
        days: Int = defaultDays
    ) -> WidgetTimelinePlan {
        guard let selectedGoalID else {
            return WidgetTimelinePlan(entries: [WidgetDayEntry(day: today, display: .notConfigured)], reloadOn: nil)
        }
        guard let found = goals.first(where: { $0.id == selectedGoalID }), let goal = found.goal else {
            return WidgetTimelinePlan(entries: [WidgetDayEntry(day: today, display: .goalDeleted)], reloadOn: nil)
        }
        let count = max(days, 1)
        let entries = (0..<count).map { offset -> WidgetDayEntry in
            let day = today.adding(days: offset)
            let state = CountdownEngine.compute(goal, today: day)
            return WidgetDayEntry(day: day, display: .goal(id: found.id, title: found.title, state: state))
        }
        return WidgetTimelinePlan(entries: entries, reloadOn: today.adding(days: count))
    }
}
