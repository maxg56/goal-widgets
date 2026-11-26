// Non vérifié : écrit sans Xcode (machine Linux).
import AppIntents
import WidgetKit
import GoalCore

/// Configuration d'une instance de widget : l'objectif affiché, le graphique et les éléments masqués.
/// Les deux paramètres ajoutés par la nouvelle interface ont une valeur par défaut : un widget posé
/// avant leur arrivée affiche l'anneau avec tout (même règle que `WidgetChartConfig.decode(nil, nil)`).
struct SelectGoalIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = LocalizedStringResource("widget_intent_title", table: "Widget")
    static var description = IntentDescription(LocalizedStringResource("widget_intent_description", table: "Widget"))

    @Parameter(title: LocalizedStringResource("widget_intent_goal", table: "Widget"))
    var goal: GoalEntity?

    @Parameter(title: LocalizedStringResource("widget_intent_chart", table: "Widget"), default: .ring)
    var chart: ChartOption

    @Parameter(title: LocalizedStringResource("widget_intent_hidden", table: "Widget"), default: [])
    var hidden: [WidgetPartOption]

    init() {}

    /// Réglages au format commun (`GoalCore.WidgetChartConfig`, même texte que l'état Glance Android) :
    /// la règle « au moins un élément reste affiché » s'applique aussi ici.
    var chartConfig: WidgetChartConfig {
        WidgetChartConfig.decode(chart: chart.rawValue, hidden: hidden.map(\.rawValue).joined(separator: ","))
    }
}

/// Graphique du widget (valeurs = `ChartKind.rawValue`).
enum ChartOption: String, AppEnum {
    case dots
    case ring
    case arcs
    case bars

    static var typeDisplayRepresentation = TypeDisplayRepresentation(
        name: LocalizedStringResource("widget_intent_chart", table: "Widget")
    )
    static var caseDisplayRepresentations: [ChartOption: DisplayRepresentation] = [
        .dots: DisplayRepresentation(title: LocalizedStringResource("chart_dots")),
        .ring: DisplayRepresentation(title: LocalizedStringResource("chart_ring")),
        .arcs: DisplayRepresentation(title: LocalizedStringResource("chart_arcs")),
        .bars: DisplayRepresentation(title: LocalizedStringResource("chart_bars")),
    ]
}

/// Élément masquable du widget (valeurs = `WidgetPart.rawValue`).
enum WidgetPartOption: String, AppEnum {
    case title
    case number
    case caption
    case chart
    case timeline

    static var typeDisplayRepresentation = TypeDisplayRepresentation(
        name: LocalizedStringResource("widget_intent_hidden", table: "Widget")
    )
    static var caseDisplayRepresentations: [WidgetPartOption: DisplayRepresentation] = [
        .title: DisplayRepresentation(title: LocalizedStringResource("widget_part_title", table: "Widget")),
        .number: DisplayRepresentation(title: LocalizedStringResource("widget_part_number", table: "Widget")),
        .caption: DisplayRepresentation(title: LocalizedStringResource("widget_part_caption", table: "Widget")),
        .chart: DisplayRepresentation(title: LocalizedStringResource("widget_part_chart", table: "Widget")),
        .timeline: DisplayRepresentation(title: LocalizedStringResource("widget_part_timeline", table: "Widget")),
    ]
}

/// Objectif proposé dans l'écran de configuration du widget.
struct GoalEntity: AppEntity {
    let id: UUID
    let title: String

    static var typeDisplayRepresentation = TypeDisplayRepresentation(
        name: LocalizedStringResource("widget_goal_entity", table: "Widget")
    )
    static var defaultQuery = GoalEntityQuery()

    var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(title: "\(title)")
    }
}

struct GoalEntityQuery: EntityQuery {
    /// Résout les identifiants mémorisés par les widgets. Un objectif supprimé est quand même renvoyé
    /// (libellé « Objectif supprimé ») pour que la configuration garde son id : le widget distingue
    /// ainsi « supprimé » de « jamais configuré ».
    func entities(for identifiers: [UUID]) async throws -> [GoalEntity] {
        let goals = WidgetGoalLoader.loadGoals()
        return identifiers.map { id in
            if let found = goals.first(where: { $0.id == id }) {
                return GoalEntity(id: found.id, title: found.title)
            }
            return GoalEntity(id: id, title: String(localized: "widget_goal_deleted", table: "Widget"))
        }
    }

    func suggestedEntities() async throws -> [GoalEntity] {
        WidgetGoalLoader.loadGoals().map { GoalEntity(id: $0.id, title: $0.title) }
    }

    func defaultResult() async -> GoalEntity? {
        try? await suggestedEntities().first
    }
}
