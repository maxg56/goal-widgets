// Non vérifié : écrit sans Xcode (machine Linux).
import SwiftUI
import WidgetKit

@main
struct GoalWidgetsBundle: WidgetBundle {
    var body: some Widget {
        GoalWidget()
        // Grille de points (t12).
        GoalGridWidget()
    }
}

/// Widget configurable : chaque instance choisit son objectif (plusieurs instances, même objectif possible).
struct GoalWidget: Widget {
    static let kind = "GoalWidget"

    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: Self.kind, intent: SelectGoalIntent.self, provider: GoalTimelineProvider()) { entry in
            GoalWidgetView(entry: entry)
        }
        .configurationDisplayName(Text("widget_name", tableName: "Widget"))
        .description(Text("widget_description", tableName: "Widget"))
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryRectangular, .accessoryCircular])
    }
}
