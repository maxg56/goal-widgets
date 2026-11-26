// Non vérifié : écrit sans Xcode (machine Linux).
import SwiftUI
import WidgetKit
import GoalCore

/// Widget « Objectif » à graphique : chiffre, légende et graphique au choix (anneau par défaut, arcs,
/// barres, points) sur un dégradé de la couleur de l'objectif (`containerBackground`). Graphiques
/// dessinés en SwiftUI (`Charts/ChartDrawing.swift`, partagé avec l'app), pas de bitmap.
/// Gabarits : petit (anneau toujours, nombre au centre), moyen (texte + graphique + mini frise), grand
/// (graphique + mini frise datée) ; écran verrouillé inchangé.
struct GoalWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: GoalWidgetEntry

    private var isAccessory: Bool {
        switch family {
        case .accessoryCircular, .accessoryRectangular, .accessoryInline: return true
        default: return false
        }
    }

    var body: some View {
        content
            .containerBackground(for: .widget) {
                if isAccessory {
                    Color.clear
                } else {
                    GoalTint.background(GoalTint.deepColor(entry.style), diagonal: true)
                }
            }
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
                .foregroundStyle(isAccessory ? Color.primary : Color.white)
        case .goalDeleted:
            MessageView(title: "widget_goal_deleted", hint: "widget_goal_deleted_hint", family: family)
                .foregroundStyle(isAccessory ? Color.primary : Color.white)
        case let .goal(_, title, state):
            switch family {
            case .accessoryRectangular:
                VStack(alignment: .leading) {
                    Text(title).font(.headline).lineLimit(1)
                    Text(GoalLabels.status(for: state)).lineLimit(1)
                }
            case .accessoryCircular:
                CircularGoalView(state: state)
            case .systemMedium:
                MediumChartView(title: title, state: state, entry: entry)
            case .systemLarge, .systemExtraLarge:
                LargeChartView(title: title, state: state, entry: entry)
            default:
                SmallChartView(title: title, state: state, entry: entry)
            }
        }
    }
}

/// Graphique blanc du widget pour l'objectif et le jour de l'entrée (`maxBars` : barres visibles).
private struct WidgetChart: View {
    let kind: ChartKind
    let goal: Goal
    let day: LocalDate
    var maxBars = 14

    var body: some View {
        switch kind {
        case .ring:
            GeometryReader { proxy in
                let side = min(proxy.size.width, proxy.size.height)
                RingGauge(
                    fraction: RingChart.compute(goal, today: day).fraction,
                    color: .white,
                    track: .white.opacity(0.22),
                    lineWidth: max(side * 0.11, 5)
                )
                .frame(width: side, height: side)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        case .arcs:
            ArcsDrawing(arcs: ArcsChart.compute(goal, today: day), accent: .white, maxStroke: 12)
        case .bars:
            let chart = BarsChart.compute(goal, today: day)
            BarsDrawing(chart: chart, range: chart.window(maxBars: maxBars), accent: .white, labels: false, gap: 3)
        case .dots:
            DotsDrawing(grid: DotGrid.compute(goal, today: day, period: .whole), accent: .white, maxPitch: 16)
        }
    }
}

/// Texte du widget : titre, grand nombre et légendes (les éléments masqués ne sont pas lus).
private struct WidgetTexts {
    let title: String
    let number: String
    let caption: String
    let shortCaption: String

    init(title: String, state: DisplayState) {
        self.title = title
        number = GoalLabels.bigNumber(state)
        caption = GoalLabels.caption(state)
        shortCaption = GoalLabels.shortCaption(state)
    }
}

/// Petit : titre, puis toujours l'anneau avec le nombre au centre (comme Android).
private struct SmallChartView: View {
    let title: String
    let state: DisplayState
    let entry: GoalWidgetEntry

    var body: some View {
        let t = WidgetTexts(title: title, state: state)
        let config = entry.config
        VStack(alignment: .leading, spacing: 4) {
            if config.shows(.title) {
                Text(t.title)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(.white.opacity(0.92))
                    .lineLimit(1)
            }
            ZStack {
                if config.shows(.chart), let goal = entry.goal {
                    WidgetChart(kind: .ring, goal: goal, day: entry.day)
                }
                VStack(spacing: 0) {
                    if config.shows(.number) {
                        Text(t.number)
                            .font(.system(size: 34, weight: .black, design: .rounded))
                            .minimumScaleFactor(0.4)
                            .lineLimit(1)
                    }
                    if config.shows(.caption) {
                        Text(t.shortCaption)
                            .font(.system(size: 10))
                            .opacity(0.8)
                            .lineLimit(1)
                    }
                }
                .foregroundStyle(.white)
                .padding(.horizontal, config.shows(.chart) ? 18 : 0)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(Text(GoalLabels.status(for: state)))
    }
}

/// Moyen : texte à gauche, graphique choisi à droite, mini frise en bas.
private struct MediumChartView: View {
    let title: String
    let state: DisplayState
    let entry: GoalWidgetEntry

    var body: some View {
        let t = WidgetTexts(title: title, state: state)
        let config = entry.config
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 0) {
                    if config.shows(.title) {
                        Text(t.title)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(.white.opacity(0.92))
                            .lineLimit(1)
                    }
                    if config.shows(.number) {
                        Text(t.number)
                            .font(.system(size: 48, weight: .black, design: .rounded))
                            .foregroundStyle(.white)
                            .minimumScaleFactor(0.5)
                            .lineLimit(1)
                    }
                    if config.shows(.caption) {
                        Text(t.caption)
                            .font(.system(size: 13))
                            .foregroundStyle(.white.opacity(0.82))
                            .lineLimit(1)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if config.shows(.chart), let goal = entry.goal {
                    WidgetChart(kind: config.chart, goal: goal, day: entry.day, maxBars: 10)
                        .aspectRatio(1, contentMode: .fit)
                }
            }
            .frame(maxHeight: .infinity)
            if config.shows(.timeline), let goal = entry.goal {
                MiniStripDrawing(strip: GoalStrip.compute(goal, today: entry.day), color: .white)
                    .frame(height: 14)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(Text(GoalLabels.status(for: state)))
    }
}

/// Grand : titre, nombre et légende, grand graphique, mini frise avec ses dates.
private struct LargeChartView: View {
    let title: String
    let state: DisplayState
    let entry: GoalWidgetEntry

    var body: some View {
        let t = WidgetTexts(title: title, state: state)
        let config = entry.config
        VStack(alignment: .leading, spacing: 8) {
            if config.shows(.title) {
                Text(t.title)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(.white.opacity(0.92))
                    .lineLimit(1)
            }
            if config.shows(.number) || config.shows(.caption) {
                HStack(alignment: .lastTextBaseline, spacing: 8) {
                    if config.shows(.number) {
                        Text(t.number)
                            .font(.system(size: 52, weight: .black, design: .rounded))
                            .minimumScaleFactor(0.5)
                            .lineLimit(1)
                    }
                    if config.shows(.caption) {
                        Text(t.caption)
                            .font(.system(size: 13))
                            .opacity(0.82)
                            .lineLimit(1)
                    }
                }
                .foregroundStyle(.white)
            }
            if config.shows(.chart), let goal = entry.goal {
                WidgetChart(kind: config.chart, goal: goal, day: entry.day, maxBars: 24)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                Spacer(minLength: 0)
            }
            if config.shows(.timeline), let goal = entry.goal {
                MiniStripDrawing(strip: GoalStrip.compute(goal, today: entry.day), color: .white)
                    .frame(height: 16)
                HStack {
                    Text(DateFormats.dayMonth(goal.startDate))
                    Spacer()
                    if goal.kind == .countdown, let target = goal.targetDate {
                        Text(GoalLabels.format("strip_target", DateFormats.dayMonth(target)))
                    } else {
                        Text("strip_today")
                    }
                }
                .font(.system(size: 11))
                .foregroundStyle(.white.opacity(0.75))
                .lineLimit(1)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(Text(GoalLabels.status(for: state)))
    }
}

/// Écran verrouillé (bonus) : jauge de progression autour du nombre de jours.
private struct CircularGoalView: View {
    let state: DisplayState

    var body: some View {
        if let percent = state.percent {
            Gauge(value: Double(percent), in: 0...100) {
                EmptyView()
            } currentValueLabel: {
                Text("\(state.days)")
            }
            .gaugeStyle(.accessoryCircularCapacity)
        } else {
            ZStack {
                AccessoryWidgetBackground()
                Text("\(state.days)")
            }
        }
    }
}

/// États « non configuré » et « objectif supprimé » (aussi utilisé par le widget « Grille »).
struct MessageView: View {
    let title: LocalizedStringKey
    let hint: LocalizedStringKey
    let family: WidgetFamily

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Image(systemName: "calendar.badge.exclamationmark")
                .font(.title3)
                .opacity(0.7)
            Text(title, tableName: "Widget")
                .font(.headline)
                .lineLimit(2)
            if family == .systemSmall || family == .systemMedium || family == .systemLarge {
                Text(hint, tableName: "Widget")
                    .font(.caption)
                    .opacity(0.75)
                    .lineLimit(3)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
    }
}
