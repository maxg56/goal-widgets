// Non vérifié : écrit sans Xcode (machine Linux).
import Foundation
import XCTest
import GoalCore

/// Rejoue `shared-cases/planner.json` (même fichier que les tests JUnit du ticket 08).
/// `expected.notifications` est la liste complète et ordonnée attendue.
final class PlannerSharedCasesTests: XCTestCase {

    private struct CaseFile: Decodable {
        let version: Int
        let cases: [PlannerCase]
    }

    private struct PlannerCase: Decodable {
        struct GoalJSON: Decodable {
            let kind: String
            let startDate: String
            let targetDate: String?
            /// Absent = true.
            let notificationsEnabled: Bool?
        }
        struct NotificationJSON: Decodable {
            let id: String
            let date: String
            let kind: String
            let value: Int
        }
        struct Expected: Decodable {
            let notifications: [NotificationJSON]
        }
        let name: String
        let goalId: String
        let goal: GoalJSON
        let today: String
        let horizonDays: Int
        let expected: Expected
    }

    func testPlannerSharedCases() throws {
        // Même dossier que le harnais du moteur (5 niveaux au-dessus de ce fichier).
        let url = EngineSharedCasesTests.sharedCasesDir.appendingPathComponent("planner.json")
        let file = try JSONDecoder().decode(CaseFile.self, from: Data(contentsOf: url))
        XCTAssertFalse(file.cases.isEmpty, "planner.json ne contient aucun cas")

        for c in file.cases {
            let kind = try XCTUnwrap(GoalKind(rawValue: c.goal.kind), "\(c.name): kind")
            let start = try XCTUnwrap(LocalDate(iso: c.goal.startDate), "\(c.name): startDate")
            let target = try c.goal.targetDate.map { try XCTUnwrap(LocalDate(iso: $0), "\(c.name): targetDate") }
            let today = try XCTUnwrap(LocalDate(iso: c.today), "\(c.name): today")
            let goal = Goal(kind: kind, startDate: start, targetDate: target)

            let plan = MilestonePlanner.plan(
                goalID: c.goalId,
                goal: goal,
                notificationsEnabled: c.goal.notificationsEnabled ?? true,
                today: today,
                horizonDays: c.horizonDays
            )

            let actual = plan.map { "\($0.id) \($0.date.iso) \($0.kind.rawValue) \($0.value)" }
            let expected = c.expected.notifications.map { "\($0.id) \($0.date) \($0.kind) \($0.value)" }
            XCTAssertEqual(actual, expected, c.name)
            for milestone in plan {
                XCTAssertEqual(milestone.goalID, c.goalId, "\(c.name): goalID")
            }

            // Stabilité : deux appels identiques donnent exactement la même liste.
            let again = MilestonePlanner.plan(
                goalID: c.goalId,
                goal: goal,
                notificationsEnabled: c.goal.notificationsEnabled ?? true,
                today: today,
                horizonDays: c.horizonDays
            )
            XCTAssertEqual(plan, again, "\(c.name): deux appels doivent donner la même liste")
        }
    }
}
