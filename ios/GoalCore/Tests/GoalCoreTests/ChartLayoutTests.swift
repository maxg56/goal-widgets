// Non vérifié : écrit sans Xcode (machine Linux).
import XCTest
import GoalCore

/// Mise en page pure des graphiques et personnalisation (détail, widget) : mêmes vecteurs que
/// `ChartLayoutTest.kt` (pas de cas partagés).
final class ChartLayoutTests: XCTestCase {

    func testDotGridTakesTheColumnCountThatGivesTheBiggestDots() {
        // 11 points dans 300 × 100 : 6 colonnes × 2 lignes de 50.
        XCTAssertEqual(DotGridLayout.fit(count: 11, width: 300, height: 100), DotGridLayout(columns: 6, rows: 2, pitch: 50))
    }

    func testDotGridInATallAreaStacksIntoASingleColumn() {
        XCTAssertEqual(DotGridLayout.fit(count: 3, width: 10, height: 300), DotGridLayout(columns: 1, rows: 3, pitch: 10))
    }

    func testStripLabelsEveryMonthWhenThereIsRoomOtherwiseEvery2_3_6Or12Months() {
        XCTAssertEqual(GoalStrip.labelStep(monthWidth: 60, minSpacing: 40), 1)
        XCTAssertEqual(GoalStrip.labelStep(monthWidth: 25, minSpacing: 40), 2)
        XCTAssertEqual(GoalStrip.labelStep(monthWidth: 15, minSpacing: 40), 3)
        XCTAssertEqual(GoalStrip.labelStep(monthWidth: 1, minSpacing: 40), 12)
    }

    func testAllGoalsTimelineIsSortedByDateWithTodayBeforeTheFirstGoalNotYetPast() {
        let today = LocalDate(year: 2026, month: 11, day: 26)
        let items: [(String, Goal)] = [
            ("Voyage", Goal(kind: .countdown, startDate: LocalDate(year: 2026, month: 10, day: 1), targetDate: LocalDate(year: 2026, month: 12, day: 10))),
            ("Arrêt", Goal(kind: .since, startDate: LocalDate(year: 2026, month: 1, day: 1))),
            ("Examen", Goal(kind: .countdown, startDate: LocalDate(year: 2026, month: 9, day: 1), targetDate: LocalDate(year: 2026, month: 11, day: 1))),
            ("Fête", Goal(kind: .countdown, startDate: LocalDate(year: 2026, month: 11, day: 1), targetDate: LocalDate(year: 2026, month: 11, day: 26))),
        ]

        let rows = GoalsTimeline.arrange(items, today: today) { $0.1 }

        XCTAssertEqual(rows.map { row -> String in
            if case let .node(item, _) = row { return item.0 }
            return "today"
        }, ["Arrêt", "Examen", "today", "Fête", "Voyage"])
        if case let .node(_, date) = rows[0] {
            XCTAssertEqual(date, LocalDate(year: 2026, month: 1, day: 1))
        } else {
            XCTFail("première ligne : un objectif")
        }
    }

    func testAllGoalsTimelineEndsWithTodayWhenEveryDateIsPast() {
        let today = LocalDate(year: 2026, month: 11, day: 26)
        let quit = Goal(kind: .since, startDate: LocalDate(year: 2026, month: 1, day: 1))
        let rows = GoalsTimeline.arrange([quit], today: today) { $0 }
        XCTAssertEqual(rows.count, 2)
        guard case .today = rows.last else { return XCTFail("dernière ligne : aujourd'hui") }
    }

    func testDetailLayoutHidesAndRestoresPartsAndIsStoredAsText() {
        let layout = DetailLayout.default.hiding(.caption).hiding(.bars)

        XCTAssertFalse(layout.shows(.caption))
        XCTAssertEqual(layout.charts, [.dots, .ring, .arcs])
        XCTAssertEqual(layout.encode(), "caption,chart:bars")
        XCTAssertEqual(DetailLayout.decode(layout.encode()), layout)
        XCTAssertEqual(layout.restoring(.caption).restoring(.bars), DetailLayout.default)
    }

    func testDetailLayoutKeepsAtLeastOneChart() {
        let one = DetailLayout.default.hiding(.dots).hiding(.ring).hiding(.arcs)
        XCTAssertEqual(one.hiding(.bars), one)
        XCTAssertEqual(one.chartToShow(preferred: .ring), .bars)
    }

    func testDetailLayoutIgnoresUnknownStoredKeys() {
        XCTAssertEqual(DetailLayout.decode("timeline,unknown,chart:pie,"), DetailLayout.default.hiding(.timeline))
        XCTAssertEqual(DetailLayout.decode(nil), DetailLayout.default)
        // Données corrompues (tous les graphiques masqués) : aucun graphique masqué.
        XCTAssertEqual(DetailLayout.decode("chart:dots,chart:ring,chart:arcs,chart:bars").charts, ChartKind.allCases)
    }

    func testWidgetConfigDefaultsToTheRingWithEverythingShownAsForWidgetsPlacedBeforeChartsExisted() {
        XCTAssertEqual(WidgetChartConfig.decode(chart: nil, hidden: nil), WidgetChartConfig(chart: .ring, hidden: []))
    }

    func testWidgetConfigRoundTripsAndKeepsAtLeastOnePart() {
        let config = WidgetChartConfig(chart: .bars, hidden: []).hiding(.title).hiding(.timeline)
        XCTAssertEqual(config.encodeHidden(), "title,timeline")
        XCTAssertEqual(WidgetChartConfig.decode(chart: config.chart.rawValue, hidden: config.encodeHidden()), config)

        let last = WidgetPart.allCases.dropFirst().reduce(WidgetChartConfig.default) { $0.hiding($1) }
        XCTAssertEqual(last.hiding(WidgetPart.allCases[0]), last)
        // Tout masqué dans l'état stocké (corrompu) : rien de masqué.
        XCTAssertEqual(WidgetChartConfig.decode(chart: "dots", hidden: "title,number,caption,chart,timeline").hidden, [])
    }
}
