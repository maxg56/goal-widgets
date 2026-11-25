// Non vérifié : écrit sans Xcode (machine Linux).
import XCTest
import GoalCore

/// Limite iOS de 64 notifications en attente : `MilestonePlanner.closest` garde les plus proches,
/// tous objectifs confondus (propre à iOS, donc hors du jeu de cas partagé).
final class MilestoneSelectionTests: XCTestCase {

    private func d(_ iso: String) -> LocalDate { LocalDate(iso: iso)! }

    func testKeepsClosestAcrossGoals() {
        let today = d("2026-01-01")
        // 40 objectifs « depuis » démarrés le 1er décembre 2025 → 12 jalons chacun sur 364 jours (01/01/2026 → 01/12/2026).
        let plans = (0..<40).flatMap { i in
            MilestonePlanner.plan(
                goalID: "g\(i)",
                goal: Goal(kind: .since, startDate: d("2025-12-01")),
                notificationsEnabled: true,
                today: today,
                horizonDays: 364
            )
        }
        XCTAssertEqual(plans.count, 480)

        let kept = MilestonePlanner.closest(plans, limit: 64)

        XCTAssertEqual(kept.count, 64)
        // 01/01 (40 objectifs) puis 01/02 : les 24 restantes sont les premières par identifiant.
        XCTAssertEqual(kept.filter { $0.date == d("2026-01-01") }.count, 40)
        XCTAssertEqual(kept.filter { $0.date == d("2026-02-01") }.count, 24)
        XCTAssertEqual(kept.last?.date, d("2026-02-01"))
    }

    func testSameDayStrongerMilestoneFirst() {
        let target = Milestone(goalID: "b", date: d("2026-03-01"), kind: .targetDay, value: 0)
        let monthly = Milestone(goalID: "a", date: d("2026-03-01"), kind: .monthAnniversary, value: 2)
        let later = Milestone(goalID: "a", date: d("2026-03-02"), kind: .monthAnniversary, value: 3)

        XCTAssertEqual(MilestonePlanner.closest([later, monthly, target], limit: 1), [target])
        XCTAssertEqual(MilestonePlanner.closest([later, monthly, target], limit: 5), [target, monthly, later])
    }

    func testFewerThanLimitKeepsEverything() {
        let one = Milestone(goalID: "a", date: d("2026-03-01"), kind: .targetDay, value: 0)
        XCTAssertEqual(MilestonePlanner.closest([one], limit: 64), [one])
        XCTAssertEqual(MilestonePlanner.closest([one], limit: 0), [])
    }
}
