// Non vérifié : écrit sans Xcode (machine Linux).
import Foundation
import SwiftData
import WidgetKit
import GoalCore

struct GoalWidgetEntry: TimelineEntry {
    let date: Date
    /// Jour représenté par l'entrée (calcul des graphiques).
    let day: LocalDate
    let display: WidgetDisplay
    /// Objectif affiché (graphiques) ; nil si non configuré, supprimé ou illisible.
    var goal: Goal? = nil
    /// Couleur et emoji de l'objectif (fond en dégradé).
    var style: GoalStyle = .none
    /// Graphique et éléments masqués choisis dans la configuration du widget.
    var config: WidgetChartConfig = .default
}

/// Couche mince : lit le stockage partagé (App Group), délègue le calcul à `WidgetTimelinePlanner`
/// (une entrée par jour, rechargement au minuit suivant) ; les graphiques sont calculés par la vue
/// pour le jour de chaque entrée.
struct GoalTimelineProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> GoalWidgetEntry {
        Self.sampleEntry()
    }

    func snapshot(for configuration: SelectGoalIntent, in context: Context) async -> GoalWidgetEntry {
        if context.isPreview && configuration.goal == nil {
            return Self.sampleEntry(config: configuration.chartConfig)
        }
        return entries(for: configuration).entries.first ?? Self.sampleEntry(config: configuration.chartConfig)
    }

    func timeline(for configuration: SelectGoalIntent, in context: Context) async -> Timeline<GoalWidgetEntry> {
        let (entries, reloadOn) = entries(for: configuration)
        let policy: TimelineReloadPolicy
        if let reloadOn {
            // Minuit local du lendemain de la dernière entrée.
            policy = .after(reloadOn.startOfDay(in: Calendar.current))
        } else {
            policy = .never
        }
        return Timeline(entries: entries, policy: policy)
    }

    /// Entrées du widget (une seule lecture du stockage).
    private func entries(for configuration: SelectGoalIntent) -> (entries: [GoalWidgetEntry], reloadOn: LocalDate?) {
        let loaded = WidgetGoalLoader.load()
        let selectedID = configuration.goal?.id
        let plan = WidgetTimelinePlanner.plan(selectedGoalID: selectedID, goals: loaded.goals, today: LocalDate.today())
        let goal = selectedID.flatMap { id in loaded.goals.first(where: { $0.id == id })?.goal }
        let style = selectedID.flatMap { loaded.styles[$0] } ?? .none
        let config = configuration.chartConfig
        let calendar = Calendar.current
        let entries = plan.entries.map { day in
            GoalWidgetEntry(
                date: day.day.startOfDay(in: calendar),
                day: day.day,
                display: day.display,
                goal: goal,
                style: style,
                config: config
            )
        }
        return (entries, plan.reloadOn)
    }

    /// Aperçu dans la galerie : compte à rebours de 30 jours, 18 écoulés.
    static func sampleEntry(config: WidgetChartConfig = .default) -> GoalWidgetEntry {
        let today = LocalDate.today()
        let goal = Goal(kind: .countdown, startDate: today.adding(days: -18), targetDate: today.adding(days: 12))
        return GoalWidgetEntry(
            date: Date(),
            day: today,
            display: .goal(
                id: UUID(),
                title: String(localized: "widget_sample_title", table: "Widget"),
                state: CountdownEngine.compute(goal, today: today)
            ),
            goal: goal,
            style: GoalStyle(color: .teal, emoji: nil),
            config: config
        )
    }
}

/// Lecture des objectifs depuis le conteneur SwiftData partagé avec l'app.
enum WidgetGoalLoader {
    private static let container: ModelContainer? = try? GoalStore.makeContainer()

    struct Loaded {
        let goals: [WidgetGoal]
        /// Couleur et emoji par objectif.
        let styles: [UUID: GoalStyle]

        /// Couleur par objectif (widget « Grille »).
        var colors: [UUID: GoalColor] { styles.compactMapValues(\.color) }
    }

    /// Objectifs (titre précédé de l'emoji, t11) et leur style.
    static func load() -> Loaded {
        guard let container else { return Loaded(goals: [], styles: [:]) }
        let context = ModelContext(container)
        let records = (try? context.fetch(FetchDescriptor<GoalRecord>())) ?? []
        var styles: [UUID: GoalStyle] = [:]
        for record in records {
            styles[record.id] = record.style
        }
        let goals = records.map { WidgetGoal(id: $0.id, title: $0.displayTitle, goal: $0.goal) }
        return Loaded(goals: goals, styles: styles)
    }

    static func loadGoals() -> [WidgetGoal] {
        load().goals
    }
}
