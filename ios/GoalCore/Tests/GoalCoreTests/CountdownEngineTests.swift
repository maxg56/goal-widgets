// Non vérifié : écrit sans Xcode (machine Linux).
import XCTest
import GoalCore

/// Cas limites du moteur de compte à rebours (complètent shared-cases/engine.json).
/// Valeurs attendues calculées à la main à partir de la spec.
final class CountdownEngineTests: XCTestCase {

    private func date(_ iso: String, file: StaticString = #filePath, line: UInt = #line) -> LocalDate {
        guard let d = LocalDate(iso: iso) else {
            XCTFail("date invalide \(iso)", file: file, line: line)
            return LocalDate(year: 1970, month: 1, day: 1)
        }
        return d
    }

    private func countdown(_ start: String, _ target: String) -> Goal {
        Goal(kind: .countdown, startDate: date(start), targetDate: date(target))
    }

    func testBeforeStart_isUpcomingWithZeroProgress() {
        let state = CountdownEngine.compute(countdown("2026-01-10", "2026-01-20"), today: date("2026-01-05"))
        XCTAssertEqual(state.phase, .upcoming)
        XCTAssertEqual(state.days, 15)
        XCTAssertEqual(state.progress, 0.0)
    }

    func testMidway_isHalfProgress() {
        let state = CountdownEngine.compute(countdown("2026-01-01", "2026-01-11"), today: date("2026-01-06"))
        XCTAssertEqual(state.phase, .upcoming)
        XCTAssertEqual(state.days, 5)
        XCTAssertEqual(state.progress ?? -1, 0.5, accuracy: 1e-9)
    }

    func testDayBefore_isOneDayLeft() {
        let state = CountdownEngine.compute(countdown("2026-01-01", "2026-01-11"), today: date("2026-01-10"))
        XCTAssertEqual(state.phase, .upcoming)
        XCTAssertEqual(state.days, 1)
        XCTAssertEqual(state.progress ?? -1, 0.9, accuracy: 1e-9)
    }

    func testTargetDay_isTodayWithFullProgress() {
        let state = CountdownEngine.compute(countdown("2026-01-01", "2026-01-11"), today: date("2026-01-11"))
        XCTAssertEqual(state.phase, .today)
        XCTAssertEqual(state.days, 0)
        XCTAssertEqual(state.progress, 1.0)
    }

    func testPastTarget_isReachedWithDaysSinceAndProgressClamped() {
        let state = CountdownEngine.compute(countdown("2026-01-01", "2026-01-11"), today: date("2026-01-16"))
        XCTAssertEqual(state.phase, .reached)
        XCTAssertEqual(state.days, 5)
        XCTAssertEqual(state.progress, 1.0)
    }

    func testCrossingFebruary29_countsTheLeapDay() {
        // Février 2028 a 29 jours : du 1er février au 1er mars = 29 jours.
        let goal = countdown("2028-02-01", "2028-03-01")
        let onLeapDay = CountdownEngine.compute(goal, today: date("2028-02-29"))
        XCTAssertEqual(onLeapDay.days, 1)
        XCTAssertEqual(onLeapDay.progress ?? -1, 28.0 / 29.0, accuracy: 1e-9)

        let dayBeforeLeapDay = CountdownEngine.compute(goal, today: date("2028-02-28"))
        XCTAssertEqual(dayBeforeLeapDay.days, 2)
    }

    func testTargetOnFebruary29() {
        let goal = countdown("2028-01-01", "2028-02-29")
        XCTAssertEqual(CountdownEngine.compute(goal, today: date("2028-02-28")).days, 1)
        XCTAssertEqual(CountdownEngine.compute(goal, today: date("2028-02-29")).phase, .today)
        let after = CountdownEngine.compute(goal, today: date("2028-03-01"))
        XCTAssertEqual(after.phase, .reached)
        XCTAssertEqual(after.days, 1)
    }

    func testFebruary29InNonLeapYear_isInvalid() {
        XCTAssertNil(LocalDate(iso: "2027-02-29"))
        XCTAssertNotNil(LocalDate(iso: "2028-02-29"))
    }

    func testProgressIsAlwaysWithinBounds() {
        let goal = countdown("2026-03-01", "2026-03-04")
        var day = date("2026-02-20")
        for _ in 0..<30 {
            let p = CountdownEngine.compute(goal, today: day).progress ?? -1
            XCTAssertGreaterThanOrEqual(p, 0.0, "\(day)")
            XCTAssertLessThanOrEqual(p, 1.0, "\(day)")
            day = day.adding(days: 1)
        }
    }
}
