// Non vérifié : écrit sans Xcode (machine Linux).
import Foundation
import XCTest
import GoalCore

/// Rejoue `shared-cases/dotgrid.json` (même fichier que les tests JUnit, ticket 12).
/// Format : `{ version, cases: [ { name, goal, today, period, expected: { unit, count, states?, first?, last? } } ] }`.
/// `states` = une lettre par cellule (`e`/`t`/`r`/`o`), `first`/`last` = `{ start, end }` ISO.
/// Seuls les champs présents dans `expected` sont vérifiés.
final class DotGridSharedCasesTests: XCTestCase {

    private struct CaseFile: Decodable {
        let version: Int
        let cases: [GridCase]
    }

    private struct GridCase: Decodable {
        struct GoalJSON: Decodable {
            let kind: String
            let startDate: String
            let targetDate: String?
        }
        struct Bounds: Decodable {
            let start: String
            let end: String
        }
        struct Expected: Decodable {
            let unit: String?
            let count: Int?
            let states: String?
            let first: Bounds?
            let last: Bounds?
        }
        let name: String
        let goal: GoalJSON
        let today: String
        let period: String
        let expected: Expected
    }

    func testDotGridSharedCases() throws {
        let url = EngineSharedCasesTests.sharedCasesDir.appendingPathComponent("dotgrid.json")
        guard FileManager.default.fileExists(atPath: url.path) else {
            throw XCTSkip("shared-cases/dotgrid.json absent (écrit par le ticket Android pair)")
        }
        let file = try JSONDecoder().decode(CaseFile.self, from: Data(contentsOf: url))
        XCTAssertFalse(file.cases.isEmpty, "dotgrid.json ne contient aucun cas")

        for c in file.cases {
            let kind = try XCTUnwrap(GoalKind(rawValue: c.goal.kind), "\(c.name): kind")
            let start = try XCTUnwrap(LocalDate(iso: c.goal.startDate), "\(c.name): startDate")
            let target = try c.goal.targetDate.map { try XCTUnwrap(LocalDate(iso: $0), "\(c.name): targetDate") }
            let today = try XCTUnwrap(LocalDate(iso: c.today), "\(c.name): today")
            let period = try XCTUnwrap(DotGrid.Period(rawValue: c.period), "\(c.name): period")

            let grid = DotGrid.compute(Goal(kind: kind, startDate: start, targetDate: target), today: today, period: period)

            if let unit = c.expected.unit {
                XCTAssertEqual(grid.unit.rawValue, unit, "\(c.name): unit")
            }
            if let count = c.expected.count {
                XCTAssertEqual(grid.cells.count, count, "\(c.name): count")
            }
            if let states = c.expected.states {
                XCTAssertEqual(grid.compactStates, states, "\(c.name): states")
            }
            if let first = c.expected.first {
                XCTAssertEqual(grid.cells.first?.start.iso, first.start, "\(c.name): first.start")
                XCTAssertEqual(grid.cells.first?.end.iso, first.end, "\(c.name): first.end")
            }
            if let last = c.expected.last {
                XCTAssertEqual(grid.cells.last?.start.iso, last.start, "\(c.name): last.start")
                XCTAssertEqual(grid.cells.last?.end.iso, last.end, "\(c.name): last.end")
            }
        }
    }
}
