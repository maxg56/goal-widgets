// Non vérifié : écrit sans Xcode (machine Linux).
import Foundation
import SwiftData
import WidgetKit
import GoalCore

struct GoalWidgetEntry: TimelineEntry {
    let date: Date
    let display: WidgetDisplay
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
        let first = plan(for: configuration).entries.first
        return GoalWidgetEntry(date: Date(), display: first?.display ?? Self.sample)
    }

    func timeline(for configuration: SelectGoalIntent, in context: Context) async -> Timeline<GoalWidgetEntry> {
        let timelinePlan = plan(for: configuration)
        let calendar = Calendar.current
        let entries = timelinePlan.entries.map {
            GoalWidgetEntry(date: $0.day.startOfDay(in: calendar), display: $0.display)
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

    private func plan(for configuration: SelectGoalIntent) -> WidgetTimelinePlan {
        WidgetTimelinePlanner.plan(
            selectedGoalID: configuration.goal?.id,
            goals: WidgetGoalLoader.loadGoals(),
            today: LocalDate.today()
        )
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

    static func loadGoals() -> [WidgetGoal] {
        guard let container else { return [] }
        let context = ModelContext(container)
        let records = (try? context.fetch(FetchDescriptor<GoalRecord>())) ?? []
        return records.map { WidgetGoal(id: $0.id, title: $0.title, goal: $0.goal) }
    }
}
