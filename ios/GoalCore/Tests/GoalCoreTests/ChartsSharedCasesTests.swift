// Non vérifié : écrit sans Xcode (machine Linux).
import Foundation
import XCTest
import GoalCore

/// Rejoue `shared-cases/charts.json` (mêmes cas que `ChartsSharedCasesTest.kt`) : graphiques du détail
/// et du widget. Seuls les champs présents dans `expected` sont vérifiés ; `null` explicite = doit être
/// absent ; réels à 1e-6 près. Format : Journal `[modern-ui]`.
final class ChartsSharedCasesTests: XCTestCase {

    // MARK: - Lecture du fichier (arbre JSON : distinguer champ absent et `null` explicite)

    private enum JSON: Decodable {
        case null
        case bool(Bool)
        case number(Double)
        case string(String)
        case array([JSON])
        case object([String: JSON])

        init(from decoder: Decoder) throws {
            let c = try decoder.singleValueContainer()
            if c.decodeNil() { self = .null }
            else if let b = try? c.decode(Bool.self) { self = .bool(b) }
            else if let n = try? c.decode(Double.self) { self = .number(n) }
            else if let s = try? c.decode(String.self) { self = .string(s) }
            else if let a = try? c.decode([JSON].self) { self = .array(a) }
            else { self = .object(try c.decode([String: JSON].self)) }
        }

        subscript(key: String) -> JSON? {
            if case let .object(o) = self { return o[key] }
            return nil
        }

        var string: String? { if case let .string(s) = self { return s }; return nil }
        var double: Double? { if case let .number(n) = self { return n }; return nil }
        var int: Int? { double.map { Int($0) } }
        var array: [JSON]? { if case let .array(a) = self { return a }; return nil }
        var isNull: Bool { if case .null = self { return true }; return false }
        var date: LocalDate? { string.flatMap { LocalDate(iso: $0) } }
    }

    func testChartSharedCases() throws {
        let url = EngineSharedCasesTests.sharedCasesDir.appendingPathComponent("charts.json")
        guard FileManager.default.fileExists(atPath: url.path) else {
            throw XCTSkip("shared-cases/charts.json absent")
        }
        let root = try JSONDecoder().decode(JSON.self, from: Data(contentsOf: url))
        let cases = try XCTUnwrap(root["cases"]?.array)
        XCTAssertFalse(cases.isEmpty, "charts.json ne contient aucun cas")

        for c in cases {
            let name = c["name"]?.string ?? "?"
            let g = try XCTUnwrap(c["goal"], name)
            let kind = try XCTUnwrap(g["kind"]?.string.flatMap(GoalKind.init(rawValue:)), "\(name): kind")
            let start = try XCTUnwrap(g["startDate"]?.date, "\(name): startDate")
            let target = g["targetDate"]?.date
            let today = try XCTUnwrap(c["today"]?.date, "\(name): today")
            let expected = try XCTUnwrap(c["expected"], "\(name): expected")
            let goal = Goal(kind: kind, startDate: start, targetDate: target)

            switch c["chart"]?.string {
            case "arcs":
                checkArcs(ArcsChart.compute(goal, today: today), expected, name)
            case "bars":
                try checkBars(BarsChart.compute(goal, today: today), maxBars: c["maxBars"]?.int, expected, name)
            case "ring":
                checkRing(RingChart.compute(goal, today: today), expected, name)
            case "strip":
                checkStrip(GoalStrip.compute(goal, today: today), expected, name)
            default:
                XCTFail("\(name): graphique inconnu")
            }
        }
    }

    // MARK: - Vérifications

    private func checkArcs(_ arcs: ArcsChart, _ e: JSON, _ name: String) {
        if let unit = e["unit"]?.string { XCTAssertEqual(arcs.unit.rawValue, unit, "\(name): unit") }
        if let step = e["step"]?.int { XCTAssertEqual(arcs.step, step, "\(name): step") }
        checkSegments(arcs.segments, e, name)
        if let current = e["current"] { XCTAssertEqual(arcs.currentIndex, current.int, "\(name): current") }
    }

    private func checkBars(_ bars: BarsChart, maxBars: Int?, _ e: JSON, _ name: String) throws {
        if let unit = e["unit"]?.string { XCTAssertEqual(bars.unit.rawValue, unit, "\(name): unit") }
        checkSegments(bars.bars.map(\.segment), e, name)
        if let todayIndex = e["todayIndex"] { XCTAssertEqual(bars.todayIndex, todayIndex.int, "\(name): todayIndex") }
        if let heights = e["heights"]?.array {
            XCTAssertEqual(heights.count, bars.bars.count, "\(name): heights.count")
            for (i, (wanted, bar)) in zip(heights, bars.bars).enumerated() {
                XCTAssertEqual(bar.height, wanted.double ?? .nan, accuracy: 1e-6, "\(name): heights[\(i)]")
            }
        }
        if let window = e["window"] {
            let range = bars.window(maxBars: try XCTUnwrap(maxBars, "\(name): window exige maxBars"))
            XCTAssertEqual(range.lowerBound, window["from"]?.int, "\(name): window.from")
            XCTAssertEqual(range.upperBound, window["to"]?.int, "\(name): window.to")
        }
    }

    private func checkRing(_ ring: RingChart, _ e: JSON, _ name: String) {
        if let fraction = e["fraction"]?.double { XCTAssertEqual(ring.fraction, fraction, accuracy: 1e-6, "\(name): fraction") }
        if let percent = e["percent"]?.int { XCTAssertEqual(ring.percent, percent, "\(name): percent") }
        if let date = e["date"]?.date { XCTAssertEqual(ring.date, date, "\(name): date") }
        if let daysLeft = e["daysLeft"]?.int { XCTAssertEqual(ring.daysLeft, daysLeft, "\(name): daysLeft") }
        if let milestone = e["milestone"] {
            if milestone.isNull {
                XCTAssertNil(ring.milestone, "\(name): milestone")
            } else {
                XCTAssertEqual(ring.milestone?.unit.rawValue, milestone["unit"]?.string, "\(name): milestone.unit")
                XCTAssertEqual(ring.milestone?.count, milestone["count"]?.int, "\(name): milestone.count")
            }
        }
    }

    private func checkStrip(_ strip: GoalStrip, _ e: JSON, _ name: String) {
        if let v = e["start"] { XCTAssertEqual(strip.start, v.date, "\(name): start") }
        if let v = e["end"] { XCTAssertEqual(strip.end, v.date, "\(name): end") }
        if let v = e["target"] { XCTAssertEqual(strip.target, v.date, "\(name): target") }
        if let v = e["solidEnd"] { XCTAssertEqual(strip.solidEnd, v.date, "\(name): solidEnd") }
        if let v = e["startPosition"]?.double { XCTAssertEqual(strip.startPosition, v, accuracy: 1e-6, "\(name): startPosition") }
        if let v = e["todayPosition"]?.double { XCTAssertEqual(strip.todayPosition, v, accuracy: 1e-6, "\(name): todayPosition") }
        if let v = e["targetPosition"] {
            if v.isNull {
                XCTAssertNil(strip.targetPosition, "\(name): targetPosition")
            } else {
                XCTAssertEqual(strip.targetPosition ?? .nan, v.double ?? .nan, accuracy: 1e-6, "\(name): targetPosition")
            }
        }
        if let v = e["tickCount"]?.int { XCTAssertEqual(strip.ticks.count, v, "\(name): tickCount") }
        if let v = e["firstTick"] { XCTAssertEqual(strip.ticks.first?.date, v.date, "\(name): firstTick") }
        if let v = e["lastTick"] { XCTAssertEqual(strip.ticks.last?.date, v.date, "\(name): lastTick") }
        if let v = e["yearTicks"]?.array {
            XCTAssertEqual(strip.ticks.filter(\.yearStart).map(\.date), v.compactMap(\.date), "\(name): yearTicks")
        }
    }

    /// Champs communs aux découpages : count, states, elapsed, first, last.
    private func checkSegments(_ segments: [ChartSegment], _ e: JSON, _ name: String) {
        if let count = e["count"]?.int { XCTAssertEqual(segments.count, count, "\(name): count") }
        if let states = e["states"]?.string {
            XCTAssertEqual(String(segments.map(\.state.letter)), states, "\(name): states")
        }
        if let elapsed = e["elapsed"]?.array {
            XCTAssertEqual(segments.map(\.elapsedDays), elapsed.compactMap(\.int), "\(name): elapsed")
        }
        if let first = e["first"] {
            XCTAssertEqual(segments.first?.start, first["start"]?.date, "\(name): first.start")
            XCTAssertEqual(segments.first?.end, first["end"]?.date, "\(name): first.end")
        }
        if let last = e["last"] {
            XCTAssertEqual(segments.last?.start, last["start"]?.date, "\(name): last.start")
            XCTAssertEqual(segments.last?.end, last["end"]?.date, "\(name): last.end")
        }
    }
}
