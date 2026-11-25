// Non vérifié : écrit sans Xcode (machine Linux).
import Foundation
import SwiftData
import WidgetKit
import GoalCore

struct GoalWidgetEntry: TimelineEntry {
    let date: Date
    let display: WidgetDisplay
    /// Couleur de l'objectif affiché (t11) ; nil = couleurs du thème.
    var color: GoalColor? = nil
}

/// Couche mince : lit le stockage partagé (App Group), délègue le calcul à `WidgetTimelinePlanner`.
struct GoalTimelineProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> GoalWidgetEntry {
        GoalWidgetEntry(date: Date(), display: Self.sample)
    }

    func snapshot(for configuration: SelectGoalIntent, in context: Context) async -> GoalWidgetEntry {
        if context.isPreview && configuration.goal == nil {
            return placeholder(in: context)
        }
        let (timelinePlan, color) = plan(for: configuration)
        let first = timelinePlan.entries.first
        return GoalWidgetEntry(date: Date(), display: first?.display ?? Self.sample, color: color)
    }

    func timeline(for configuration: SelectGoalIntent, in context: Context) async -> Timeline<GoalWidgetEntry> {
        let (timelinePlan, color) = plan(for: configuration)
        let calendar = Calendar.current
        let entries = timelinePlan.entries.map {
            GoalWidgetEntry(date: $0.day.startOfDay(in: calendar), display: $0.display, color: color)
        }
        let policy: TimelineReloadPolicy
        if let reloadOn = timelinePlan.reloadOn {
            // Minuit local du lendemain de la dernière entrée.
            policy = .after(reloadOn.startOfDay(in: calendar))
        } else {
            policy = .never
        }
        return Timeline(entries: entries, policy: policy)
    }

    /// Timeline + couleur de l'objectif choisi (une seule lecture du stockage).
    private func plan(for configuration: SelectGoalIntent) -> (WidgetTimelinePlan, GoalColor?) {
        let loaded = WidgetGoalLoader.load()
        let selectedID = configuration.goal?.id
        let timelinePlan = WidgetTimelinePlanner.plan(
            selectedGoalID: selectedID,
            goals: loaded.goals,
            today: LocalDate.today()
        )
        return (timelinePlan, selectedID.flatMap { loaded.colors[$0] })
    }

    /// Aperçu dans la galerie de widgets.
    static let sample = WidgetDisplay.goal(
        id: UUID(),
        title: String(localized: "widget_sample_title", table: "Widget"),
        state: DisplayState(phase: .upcoming, days: 12, progress: 0.6, percent: 60)
    )
}

/// Lecture des objectifs depuis le conteneur SwiftData partagé avec l'app.
enum WidgetGoalLoader {
    private static let container: ModelContainer? = try? GoalStore.makeContainer()

    struct Loaded {
        let goals: [WidgetGoal]
        let colors: [UUID: GoalColor]
    }

    /// Objectifs (titre précédé de l'emoji, t11) et leur couleur.
    static func load() -> Loaded {
        guard let container else { return Loaded(goals: [], colors: [:]) }
        let context = ModelContext(container)
        let records = (try? context.fetch(FetchDescriptor<GoalRecord>())) ?? []
        var colors: [UUID: GoalColor] = [:]
        for record in records {
            if let color = record.style.color { colors[record.id] = color }
        }
        let goals = records.map { WidgetGoal(id: $0.id, title: $0.displayTitle, goal: $0.goal) }
        return Loaded(goals: goals, colors: colors)
    }

    static func loadGoals() -> [WidgetGoal] {
        load().goals
    }
}
