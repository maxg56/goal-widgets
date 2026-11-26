// Non vérifié : écrit sans Xcode (machine Linux).
import AppIntents
import SwiftUI
import WidgetKit
import GoalCore

// Widget « Grille » (ticket 12) : un point par unité de temps (`GoalCore.DotGrid`).
// Points pleins = écoulés, point cerclé = aujourd'hui, points pâles = restants,
// petits cercles = hors de l'objectif (périodes mois/semaine). Couleur de l'objectif (t11).

/// Widget configurable : objectif + période affichée.
struct GoalGridWidget: Widget {
    static let kind = "GoalGridWidget"

    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: Self.kind, intent: SelectGoalGridIntent.self, provider: GridTimelineProvider()) { entry in
            GridWidgetView(entry: entry)
        }
        .configurationDisplayName(Text("grid_widget_name", tableName: "Widget"))
        .description(Text("grid_widget_description", tableName: "Widget"))
        .supportedFamilies([.systemMedium, .systemLarge])
    }
}

// MARK: - Configuration

/// Période affichée par la grille (même valeurs que `DotGrid.Period`).
enum GridPeriodOption: String, AppEnum {
    case whole
    case month
    case week

    static var typeDisplayRepresentation = TypeDisplayRepresentation(
        name: LocalizedStringResource("grid_intent_period", table: "Widget")
    )
    static var caseDisplayRepresentations: [GridPeriodOption: DisplayRepresentation] = [
        .whole: DisplayRepresentation(title: LocalizedStringResource("grid_period_whole", table: "Widget")),
        .month: DisplayRepresentation(title: LocalizedStringResource("grid_period_month", table: "Widget")),
        .week: DisplayRepresentation(title: LocalizedStringResource("grid_period_week", table: "Widget")),
    ]

    var period: DotGrid.Period {
        switch self {
        case .whole: return .whole
        case .month: return .month
        case .week: return .week
        }
    }
}

struct SelectGoalGridIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = LocalizedStringResource("grid_intent_title", table: "Widget")
    static var description = IntentDescription(LocalizedStringResource("grid_intent_description", table: "Widget"))

    /// Même entité que le widget « Objectif » (un id supprimé reste résolu → état « supprimé »).
    @Parameter(title: LocalizedStringResource("widget_intent_goal", table: "Widget"))
    var goal: GoalEntity?

    @Parameter(title: LocalizedStringResource("grid_intent_period", table: "Widget"), default: .whole)
    var period: GridPeriodOption

    init() {}
}

// MARK: - Timeline

struct GridWidgetEntry: TimelineEntry {
    let date: Date
    let display: WidgetDisplay
    /// Grille du jour de l'entrée ; nil si aucun objectif affiché.
    let grid: DotGrid?
    var color: GoalColor? = nil
}

/// Couche mince : la timeline quotidienne vient de `WidgetTimelinePlanner` (une entrée par jour,
/// rechargement au minuit suivant), la grille de chaque entrée de `DotGrid.compute` pour ce jour.
struct GridTimelineProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> GridWidgetEntry {
        Self.sampleEntry()
    }

    func snapshot(for configuration: SelectGoalGridIntent, in context: Context) async -> GridWidgetEntry {
        if context.isPreview && configuration.goal == nil {
            return Self.sampleEntry()
        }
        return makeEntries(for: configuration).entries.first ?? Self.sampleEntry()
    }

    func timeline(for configuration: SelectGoalGridIntent, in context: Context) async -> Timeline<GridWidgetEntry> {
        let (entries, reloadOn) = makeEntries(for: configuration)
        let policy: TimelineReloadPolicy = reloadOn.map { .after($0.startOfDay(in: Calendar.current)) } ?? .never
        return Timeline(entries: entries, policy: policy)
    }

    private func makeEntries(for configuration: SelectGoalGridIntent) -> (entries: [GridWidgetEntry], reloadOn: LocalDate?) {
        let loaded = WidgetGoalLoader.load()
        let selectedID = configuration.goal?.id
        let period = configuration.period.period
        let plan = WidgetTimelinePlanner.plan(selectedGoalID: selectedID, goals: loaded.goals, today: LocalDate.today())
        let goal = selectedID.flatMap { id in loaded.goals.first(where: { $0.id == id })?.goal }
        let color = selectedID.flatMap { loaded.colors[$0] }
        let calendar = Calendar.current
        let entries = plan.entries.map { day -> GridWidgetEntry in
            var grid: DotGrid?
            if case .goal = day.display, let goal {
                grid = DotGrid.compute(goal, today: day.day, period: period)
            }
            return GridWidgetEntry(date: day.day.startOfDay(in: calendar), display: day.display, grid: grid, color: color)
        }
        return (entries, plan.reloadOn)
    }

    /// Aperçu dans la galerie : compte à rebours de 30 jours, 18 écoulés.
    static func sampleEntry() -> GridWidgetEntry {
        let today = LocalDate.today()
        let goal = Goal(kind: .countdown, startDate: today.adding(days: -18), targetDate: today.adding(days: 11))
        return GridWidgetEntry(
            date: Date(),
            display: .goal(
                id: UUID(),
                title: String(localized: "widget_sample_title", table: "Widget"),
                state: CountdownEngine.compute(goal, today: today)
            ),
            grid: DotGrid.compute(goal, today: today, period: .whole),
            color: .teal
        )
    }
}

// MARK: - Vues

struct GridWidgetView: View {
    let entry: GridWidgetEntry
    @Environment(\.widgetFamily) private var family

    var body: some View {
        content
            .containerBackground(.fill.tertiary, for: .widget)
            .widgetURL(link)
    }

    private var link: URL? {
        if case let .goal(id, _, _) = entry.display {
            return GoalDeepLink.url(forGoal: id)
        }
        return nil
    }

    @ViewBuilder
    private var content: some View {
        switch entry.display {
        case .notConfigured:
            MessageView(title: "widget_not_configured_title", hint: "widget_not_configured_hint", family: family)
        case .goalDeleted:
            MessageView(title: "widget_goal_deleted", hint: "widget_goal_deleted_hint", family: family)
        case let .goal(_, title, state):
            VStack(alignment: .leading, spacing: 8) {
                Text(title)
                    .font(.headline)
                    .lineLimit(1)
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    if state.phase != .today {
                        Text("\(state.days)")
                            .font(.system(.title2, design: .rounded).bold())
                            .monospacedDigit()
                    }
                    Text(GoalLabels.status(for: state))
                        .font(.caption)
                        .foregroundStyle(GoalAppearance.statusStyle(entry.color))
                        .lineLimit(1)
                }
                if let grid = entry.grid {
                    DotGridView(grid: grid, color: entry.color)
                        .accessibilityHidden(true)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
    }
}

/// Dessin des points : la taille s'adapte au nombre de cellules et à la place disponible.
struct DotGridView: View {
    let grid: DotGrid
    let color: GoalColor?

    var body: some View {
        GeometryReader { proxy in
            // `GoalCore.DotGridLayout` (commun au détail et au widget à graphique), points plafonnés à 30 pt.
            let layout = DotGridLayout.fit(count: grid.cells.count, width: proxy.size.width, height: proxy.size.height)
            let pitch = CGFloat(min(layout.pitch, 30))
            Canvas { context, _ in
                let tint = GoalAppearance.tint(color)
                let diameter = pitch * 0.72
                for (index, cell) in grid.cells.enumerated() {
                    let row = index / layout.columns
                    let column = index % layout.columns
                    let center = CGPoint(
                        x: (CGFloat(column) + 0.5) * pitch,
                        y: (CGFloat(row) + 0.5) * pitch
                    )
                    let rect = CGRect(x: center.x - diameter / 2, y: center.y - diameter / 2, width: diameter, height: diameter)
                    let dot = Path(ellipseIn: rect)
                    switch cell.state {
                    case .elapsed:
                        context.fill(dot, with: .color(tint))
                    case .today:
                        context.fill(dot, with: .color(tint))
                        let ringInset = -pitch * 0.12
                        context.stroke(
                            Path(ellipseIn: rect.insetBy(dx: ringInset, dy: ringInset)),
                            with: .color(.primary),
                            lineWidth: max(1, pitch * 0.08)
                        )
                    case .remaining:
                        context.fill(dot, with: .color(tint.opacity(0.25)))
                    case .outside:
                        let small = rect.insetBy(dx: diameter * 0.3, dy: diameter * 0.3)
                        context.fill(Path(ellipseIn: small), with: .color(.gray.opacity(0.35)))
                    }
                }
            }
        }
    }
}
