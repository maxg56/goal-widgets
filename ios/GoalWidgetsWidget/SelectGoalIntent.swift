// Non vérifié : écrit sans Xcode (machine Linux).
import AppIntents
import WidgetKit
import GoalCore

/// Configuration d'une instance de widget : l'objectif affiché.
struct SelectGoalIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = LocalizedStringResource("widget_intent_title", table: "Widget")
    static var description = IntentDescription(LocalizedStringResource("widget_intent_description", table: "Widget"))

    @Parameter(title: LocalizedStringResource("widget_intent_goal", table: "Widget"))
    var goal: GoalEntity?

    init() {}
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
