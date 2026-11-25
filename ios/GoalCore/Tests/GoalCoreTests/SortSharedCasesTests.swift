// Non vérifié : écrit sans Xcode (machine Linux).
import Foundation
import XCTest
import GoalCore

/// Rejoue `shared-cases/sort.json` (même fichier que `SortSharedCasesTest` côté JUnit).
final class SortSharedCasesTests: XCTestCase {

    private struct SortFile: Decodable {
        struct GoalJSON: Decodable {
            let id: String
            let kind: String
            let startDate: String
            let targetDate: String?
            let createdAt: String
        }
        struct SortCase: Decodable {
            let name: String
            let order: String
            let expected: [String]
        }
        let version: Int
        let today: String
        let goals: [GoalJSON]
        let cases: [SortCase]
    }

    func testSortSharedCases() throws {
        let url = EngineSharedCasesTests.sharedCasesDir.appendingPathComponent("sort.json")
        let file = try JSONDecoder().decode(SortFile.self, from: Data(contentsOf: url))
        XCTAssertFalse(file.cases.isEmpty, "sort.json ne contient aucun cas")
        let today = try XCTUnwrap(LocalDate(iso: file.today))

        let keys: [GoalSortKey] = try file.goals.map { g in
            let kind = try XCTUnwrap(GoalKind(rawValue: g.kind), "\(g.id): kind")
            let start = try XCTUnwrap(LocalDate(iso: g.startDate), "\(g.id): startDate")
            let target = try g.targetDate.map { try XCTUnwrap(LocalDate(iso: $0), "\(g.id): targetDate") }
            let createdAt = try XCTUnwrap(GoalExport.parseInstant(g.createdAt), "\(g.id): createdAt")
            return GoalSortKey(id: g.id, goal: Goal(kind: kind, startDate: start, targetDate: target), createdAt: createdAt)
        }

        for c in file.cases {
            let order = try XCTUnwrap(GoalOrder(rawValue: c.order), "\(c.name): ordre inconnu")
            let sorted = order.sort(keys, today: today) { $0 }
            XCTAssertEqual(sorted.map(\.id), c.expected, c.name)

            // Le résultat ne dépend pas de l'ordre d'entrée.
            let reversed = order.sort(Array(keys.reversed()), today: today) { $0 }
            XCTAssertEqual(reversed.map(\.id), c.expected, "\(c.name) (entrée inversée)")
        }
    }

    func testUnknownStoredOrderFallsBackToClosestDate() {
        XCTAssertEqual(GoalOrder(storedValue: nil), .closestDate)
        XCTAssertEqual(GoalOrder(storedValue: "bientôt"), .closestDate)
        XCTAssertEqual(GoalOrder(storedValue: "created"), .created)
        XCTAssertEqual(GoalOrder.defaultOrder, .closestDate)
    }
}
