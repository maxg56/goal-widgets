// Non vérifié : écrit sans Xcode (machine Linux).
import SwiftUI
import WidgetKit
import GoalCore

/// Vue du widget : uniquement de la mise en page, l'état vient de `WidgetDisplay`.
/// Couleurs sémantiques (primary/secondary, fond système) → mode clair/sombre automatique.
/// Couleur de l'objectif (t11, `GoalAppearance` : variantes claire/sombre) sur le statut et la barre ;
/// l'emoji est déjà dans le titre (`GoalRecord.displayTitle`).
struct GoalWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: GoalWidgetEntry

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
            switch family {
            case .systemMedium:
                MediumGoalView(title: title, state: state, color: entry.color)
            case .accessoryRectangular:
                VStack(alignment: .leading) {
                    Text(title).font(.headline).lineLimit(1)
                    Text(GoalLabels.status(for: state)).lineLimit(1)
                }
            case .accessoryCircular:
                CircularGoalView(state: state, color: entry.color)
            default:
                SmallGoalView(title: title, state: state, color: entry.color)
            }
        }
    }
}

/// Grand chiffre (jours) ou « Aujourd'hui » le jour J.
private struct BigNumber: View {
    let state: DisplayState

    var body: some View {
        if state.phase == .today {
            Text(GoalLabels.status(for: state))
                .font(.title2.bold())
                .minimumScaleFactor(0.5)
                .lineLimit(1)
        } else {
            Text("\(state.days)")
                .font(.system(size: 44, weight: .bold, design: .rounded))
                .monospacedDigit()
                .minimumScaleFactor(0.5)
                .lineLimit(1)
        }
    }
}

private struct SmallGoalView: View {
    let title: String
    let state: DisplayState
    let color: GoalColor?

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.headline)
                .lineLimit(2)
            Spacer(minLength: 0)
            BigNumber(state: state)
            if state.phase != .today {
                Text(GoalLabels.status(for: state))
                    .font(.caption)
                    .foregroundStyle(GoalAppearance.statusStyle(color))
                    .lineLimit(2)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
    }
}

private struct MediumGoalView: View {
    let title: String
    let state: DisplayState
    let color: GoalColor?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.headline)
                .lineLimit(1)
            HStack(alignment: .firstTextBaseline) {
                BigNumber(state: state)
                if state.phase != .today {
                    Text(GoalLabels.status(for: state))
                        .font(.subheadline)
                        .foregroundStyle(GoalAppearance.statusStyle(color))
                        .lineLimit(2)
                }
            }
            Spacer(minLength: 0)
            // `percent` (entier arrondi vers le bas, journal [t02]) : 100 % seulement le jour J.
            if let percent = state.percent {
                HStack {
                    ProgressView(value: Double(percent), total: 100)
                        .tint(GoalAppearance.tint(color))
                    Text(GoalLabels.percent(percent))
                        .font(.caption)
                        .monospacedDigit()
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
    }
}

/// Écran verrouillé (bonus) : jauge de progression autour du nombre de jours.
private struct CircularGoalView: View {
    let state: DisplayState
    let color: GoalColor?

    var body: some View {
        if let percent = state.percent {
            Gauge(value: Double(percent), in: 0...100) {
                EmptyView()
            } currentValueLabel: {
                Text("\(state.days)")
            }
            .gaugeStyle(.accessoryCircularCapacity)
            .tint(GoalAppearance.tint(color))
        } else {
            ZStack {
                AccessoryWidgetBackground()
                Text("\(state.days)")
            }
        }
    }
}

/// États « non configuré » et « objectif supprimé ».
private struct MessageView: View {
    let title: LocalizedStringKey
    let hint: LocalizedStringKey
    let family: WidgetFamily

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Image(systemName: "calendar.badge.exclamationmark")
                .font(.title3)
                .foregroundStyle(.secondary)
            Text(title, tableName: "Widget")
                .font(.headline)
                .lineLimit(2)
            if family == .systemSmall || family == .systemMedium {
                Text(hint, tableName: "Widget")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(3)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
    }
}
