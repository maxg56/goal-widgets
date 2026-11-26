// Non vérifié : écrit sans Xcode (machine Linux).
import Foundation

/// Grille de points : `columns` × `rows` emplacements carrés de `pitch` (unité de l'appelant : points).
public struct DotGridLayout: Equatable, Sendable {
    public let columns: Int
    public let rows: Int
    public let pitch: Double

    public init(columns: Int, rows: Int, pitch: Double) {
        self.columns = columns
        self.rows = rows
        self.pitch = pitch
    }

    /// Nombre de colonnes qui donne les plus gros points pour `count` points dans `width` × `height`.
    public static func fit(count: Int, width: Double, height: Double) -> DotGridLayout {
        let n = max(count, 1)
        var best = DotGridLayout(columns: 1, rows: n, pitch: min(width, height / Double(n)))
        if n >= 2 {
            for columns in 2...n {
                let rows = (n + columns - 1) / columns
                let pitch = min(width / Double(columns), height / Double(rows))
                if pitch > best.pitch { best = DotGridLayout(columns: columns, rows: rows, pitch: pitch) }
            }
        }
        return best
    }
}

/// Ligne de la frise de tous les objectifs : un objectif à sa date, ou le repère du jour.
public enum TimelineRow<Item> {
    case node(item: Item, date: LocalDate)
    case today
}

/// Frise de tous les objectifs : ordre chronologique, repère « Aujourd'hui » avant la première date ≥ aujourd'hui.
public enum GoalsTimeline {
    /// Date portée sur la frise : cible d'un compte à rebours, départ d'un « depuis ».
    public static func date(of goal: Goal) -> LocalDate {
        switch goal.kind {
        case .countdown: return goal.targetDate ?? goal.startDate
        case .since: return goal.startDate
        }
    }

    /// Tri stable par date (à date égale, l'ordre reçu est gardé, comme `sortedBy` en Kotlin).
    public static func arrange<Item>(_ items: [Item], today: LocalDate, goal: (Item) -> Goal) -> [TimelineRow<Item>] {
        let nodes = items.enumerated()
            .map { (offset: $0.offset, item: $0.element, date: date(of: goal($0.element))) }
            .sorted { ($0.date, $0.offset) < ($1.date, $1.offset) }
        let split = nodes.firstIndex { $0.date >= today } ?? nodes.count
        var rows: [TimelineRow<Item>] = nodes[..<split].map { .node(item: $0.item, date: $0.date) }
        rows.append(.today)
        rows += nodes[split...].map { .node(item: $0.item, date: $0.date) }
        return rows
    }
}

/// Bloc masquable du détail. `rawValue` = valeur stockée.
public enum DetailPart: String, CaseIterable, Sendable {
    case number
    case caption
    case chart
    case timeline
}

/// Personnalisation du détail (commune à tous les objectifs) : blocs et graphiques masqués. Il reste
/// toujours au moins un graphique. Stockée en texte comme sur Android (`detail_layout`) : clés séparées
/// par des virgules, `chart:<graphique>` pour un graphique (`number,chart:bars`).
public struct DetailLayout: Equatable, Sendable {
    public let hiddenParts: Set<DetailPart>
    public let hiddenCharts: Set<ChartKind>

    public init(hiddenParts: Set<DetailPart>, hiddenCharts: Set<ChartKind>) {
        self.hiddenParts = hiddenParts
        self.hiddenCharts = hiddenCharts
    }

    public static let `default` = DetailLayout(hiddenParts: [], hiddenCharts: [])
    static let chartPrefix = "chart:"

    public func shows(_ part: DetailPart) -> Bool { !hiddenParts.contains(part) }

    /// Graphiques proposés (non masqués), dans l'ordre du sélecteur.
    public var charts: [ChartKind] { ChartKind.allCases.filter { !hiddenCharts.contains($0) } }

    public func hiding(_ part: DetailPart) -> DetailLayout {
        DetailLayout(hiddenParts: hiddenParts.union([part]), hiddenCharts: hiddenCharts)
    }

    public func restoring(_ part: DetailPart) -> DetailLayout {
        DetailLayout(hiddenParts: hiddenParts.subtracting([part]), hiddenCharts: hiddenCharts)
    }

    /// Sans effet s'il ne reste que ce graphique.
    public func hiding(_ chart: ChartKind) -> DetailLayout {
        guard charts.count > 1 else { return self }
        return DetailLayout(hiddenParts: hiddenParts, hiddenCharts: hiddenCharts.union([chart]))
    }

    public func restoring(_ chart: ChartKind) -> DetailLayout {
        DetailLayout(hiddenParts: hiddenParts, hiddenCharts: hiddenCharts.subtracting([chart]))
    }

    /// `preferred` s'il est encore proposé, sinon le premier graphique proposé.
    public func chartToShow(preferred: ChartKind?) -> ChartKind {
        if let preferred, charts.contains(preferred) { return preferred }
        return charts.first ?? .ring
    }

    public func encode() -> String {
        let parts = DetailPart.allCases.filter { hiddenParts.contains($0) }.map(\.rawValue)
        let charts = ChartKind.allCases.filter { hiddenCharts.contains($0) }.map { Self.chartPrefix + $0.rawValue }
        return (parts + charts).joined(separator: ",")
    }

    public static func decode(_ raw: String?) -> DetailLayout {
        let keys = Set((raw ?? "").split(separator: ",", omittingEmptySubsequences: false)
            .map { $0.trimmingCharacters(in: .whitespaces) })
        let charts = Set(keys.compactMap { key -> ChartKind? in
            guard key.hasPrefix(chartPrefix) else { return nil }
            return ChartKind(rawValue: String(key.dropFirst(chartPrefix.count)))
        })
        return DetailLayout(
            hiddenParts: Set(DetailPart.allCases.filter { keys.contains($0.rawValue) }),
            // Données corrompues (tous masqués) : on n'en garde aucun de masqué.
            hiddenCharts: charts.count < ChartKind.allCases.count ? charts : []
        )
    }
}

/// Élément retirable du widget à graphique. `rawValue` = valeur stockée (même que l'état Glance Android).
public enum WidgetPart: String, CaseIterable, Sendable {
    case title
    case number
    case caption
    case chart
    case timeline
}

/// Réglages d'un widget à graphique : graphique choisi et éléments retirés (au moins un reste).
/// Absents (widget posé avant l'arrivée des graphiques) : anneau, tout affiché.
public struct WidgetChartConfig: Equatable, Sendable {
    public let chart: ChartKind
    public let hidden: Set<WidgetPart>

    public init(chart: ChartKind, hidden: Set<WidgetPart>) {
        self.chart = chart
        self.hidden = hidden
    }

    public static let `default` = WidgetChartConfig(chart: .ring, hidden: [])

    public func shows(_ part: WidgetPart) -> Bool { !hidden.contains(part) }

    /// Sans effet s'il ne reste que cet élément.
    public func hiding(_ part: WidgetPart) -> WidgetChartConfig {
        guard WidgetPart.allCases.filter(shows).count > 1 else { return self }
        return WidgetChartConfig(chart: chart, hidden: hidden.union([part]))
    }

    public func restoring(_ part: WidgetPart) -> WidgetChartConfig {
        WidgetChartConfig(chart: chart, hidden: hidden.subtracting([part]))
    }

    public func encodeHidden() -> String {
        WidgetPart.allCases.filter { hidden.contains($0) }.map(\.rawValue).joined(separator: ",")
    }

    public static func decode(chart: String?, hidden: String?) -> WidgetChartConfig {
        let keys = Set((hidden ?? "").split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) })
        let parts = Set(WidgetPart.allCases.filter { keys.contains($0.rawValue) })
        return WidgetChartConfig(
            chart: chart.flatMap(ChartKind.init(rawValue:)) ?? Self.default.chart,
            hidden: parts.count < WidgetPart.allCases.count ? parts : []
        )
    }
}
