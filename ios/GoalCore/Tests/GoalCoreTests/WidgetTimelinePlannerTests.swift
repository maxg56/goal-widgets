// Non vérifié : écrit sans Xcode (machine Linux).
import XCTest
import GoalCore

/// Timeline du widget : une entrée par jour (minuit local), état « objectif supprimé ».
/// Valeurs attendues calculées à la main.
final class WidgetTimelinePlannerTests: XCTestCase {

    private let goalID = UUID(uuidString: "11111111-2222-3333-4444-555555555555")!

    private func date(_ iso: String) -> LocalDate {
        LocalDate(iso: iso)!
    }

    private func countdown(_ start: String, _ target: String) -> Goal {
        Goal(kind: .countdown, startDate: date(start), targetDate: date(target))
    }

    func testNoGoalSelected_isNotConfiguredAndNeverReloadsByItself() {
        let plan = WidgetTimelinePlanner.plan(selectedGoalID: nil, goals: [], today: date("2026-01-09"))
        XCTAssertEqual(plan.entries, [WidgetDayEntry(day: date("2026-01-09"), display: .notConfigured)])
        XCTAssertNil(plan.reloadOn)
    }

    func testSelectedGoalMissingFromStore_isGoalDeleted() {
        let other = WidgetGoal(id: UUID(), title: "Autre", goal: countdown("2026-01-01", "2026-02-01"))
        let plan = WidgetTimelinePlanner.plan(selectedGoalID: goalID, goals: [other], today: date("2026-01-09"))
        XCTAssertEqual(plan.entries, [WidgetDayEntry(day: date("2026-01-09"), display: .goalDeleted)])
        XCTAssertNil(plan.reloadOn)
    }

    func testSelectedGoalUnreadable_isGoalDeleted() {
        let corrupt = WidgetGoal(id: goalID, title: "Abîmé", goal: nil)
        let plan = WidgetTimelinePlanner.plan(selectedGoalID: goalID, goals: [corrupt], today: date("2026-01-09"))
        XCTAssertEqual(plan.entries.map(\.display), [.goalDeleted])
    }

    func testCountdown_oneEntryPerDayAcrossTargetDay_thenReloadsTheDayAfterTheLastEntry() {
        let exam = WidgetGoal(id: goalID, title: "Examen", goal: countdown("2026-01-01", "2026-01-11"))
        let plan = WidgetTimelinePlanner.plan(selectedGoalID: goalID, goals: [exam], today: date("2026-01-09"), days: 4)

        XCTAssertEqual(plan.entries, [
            WidgetDayEntry(day: date("2026-01-09"),
                           display: .goal(id: goalID, title: "Examen", state: DisplayState(phase: .upcoming, days: 2, progress: 0.8))),
            WidgetDayEntry(day: date("2026-01-10"),
                           display: .goal(id: goalID, title: "Examen", state: DisplayState(phase: .upcoming, days: 1, progress: 0.9))),
            WidgetDayEntry(day: date("2026-01-11"),
                           display: .goal(id: goalID, title: "Examen", state: DisplayState(phase: .today, days: 0, progress: 1.0))),
            WidgetDayEntry(day: date("2026-01-12"),
                           display: .goal(id: goalID, title: "Examen", state: DisplayState(phase: .reached, days: 1, progress: 1.0))),
        ])
        XCTAssertEqual(plan.reloadOn, date("2026-01-13"))
    }

    func testEntriesCrossMonthAndYearBoundaries() {
        let trip = WidgetGoal(id: goalID, title: "Voyage", goal: countdown("2026-12-01", "2027-02-01"))
        let plan = WidgetTimelinePlanner.plan(selectedGoalID: goalID, goals: [trip], today: date("2026-12-30"), days: 3)
        XCTAssertEqual(plan.entries.map(\.day), [date("2026-12-30"), date("2026-12-31"), date("2027-01-01")])
        XCTAssertEqual(plan.reloadOn, date("2027-01-02"))
    }

    func testDefaultHorizonIsOneWeek() {
        let trip = WidgetGoal(id: goalID, title: "Voyage", goal: countdown("2026-12-01", "2027-02-01"))
        let plan = WidgetTimelinePlanner.plan(selectedGoalID: goalID, goals: [trip], today: date("2026-12-30"))
        XCTAssertEqual(plan.entries.count, 7)
        XCTAssertEqual(plan.reloadOn, date("2027-01-06"))
    }
}
