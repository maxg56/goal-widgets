// Non vérifié : écrit sans Xcode (machine Linux).
import XCTest
import GoalCore

/// Objectifs « depuis » (phase `elapsed`), décomposition années/mois/jours,
/// conversion d'un compte à rebours atteint en compteur « depuis » (ticket 05).
/// Valeurs attendues calculées à la main (calendrier), pas par le code testé.
final class SinceEngineTests: XCTestCase {

    private func date(_ iso: String, file: StaticString = #filePath, line: UInt = #line) -> LocalDate {
        guard let d = LocalDate(iso: iso) else {
            XCTFail("date invalide \(iso)", file: file, line: line)
            return LocalDate(year: 1970, month: 1, day: 1)
        }
        return d
    }

    private func since(_ start: String) -> Goal {
        Goal(kind: .since, startDate: date(start))
    }

    private func breakdown(_ start: String, _ today: String) -> Breakdown? {
        CountdownEngine.compute(since(start), today: date(today)).breakdown
    }

    // MARK: - Phase elapsed

    func testSince_isElapsedWithDaysSinceStartAndNoProgress() {
        let state = CountdownEngine.compute(since("2026-01-01"), today: date("2026-01-16"))
        XCTAssertEqual(state.phase, .elapsed)
        XCTAssertEqual(state.days, 15)
        XCTAssertNil(state.progress)
    }

    func testSince_onStartDay_isZeroDays() {
        let state = CountdownEngine.compute(since("2026-01-01"), today: date("2026-01-01"))
        XCTAssertEqual(state.phase, .elapsed)
        XCTAssertEqual(state.days, 0)
        XCTAssertEqual(state.breakdown, Breakdown(years: 0, months: 0, days: 0))
    }

    func testSince_startInFuture_isClampedToZero() {
        // Le formulaire l'interdit ; le moteur ne renvoie jamais de nombre négatif.
        let state = CountdownEngine.compute(since("2026-02-01"), today: date("2026-01-25"))
        XCTAssertEqual(state.phase, .elapsed)
        XCTAssertEqual(state.days, 0)
        XCTAssertEqual(state.breakdown, Breakdown(years: 0, months: 0, days: 0))
    }

    func testSince_ignoresAStrayTargetDate() {
        let goal = Goal(kind: .since, startDate: date("2026-01-01"), targetDate: date("2026-12-31"))
        let state = CountdownEngine.compute(goal, today: date("2026-01-11"))
        XCTAssertEqual(state.phase, .elapsed)
        XCTAssertEqual(state.days, 10)
        XCTAssertNil(state.progress)
    }

    func testSince_overALeapYear_counts366Days() {
        let state = CountdownEngine.compute(since("2028-01-01"), today: date("2029-01-01"))
        XCTAssertEqual(state.days, 366)
        XCTAssertEqual(state.breakdown, Breakdown(years: 1, months: 0, days: 0))
    }

    func testCountdown_hasNoBreakdown() {
        let goal = Goal(kind: .countdown, startDate: date("2026-01-01"), targetDate: date("2026-01-11"))
        XCTAssertNil(CountdownEngine.compute(goal, today: date("2026-01-05")).breakdown)
    }

    // MARK: - Décomposition années / mois / jours

    func testBreakdown_yearsMonthsDays() {
        // 2025-01-01 → 2026-03-01 = 1 an 2 mois, puis 3 jours. 365 + 31 + 28 + 3 = 427 jours.
        let state = CountdownEngine.compute(since("2025-01-01"), today: date("2026-03-04"))
        XCTAssertEqual(state.days, 427)
        XCTAssertEqual(state.breakdown, Breakdown(years: 1, months: 2, days: 3))
    }

    func testBreakdown_lessThanAMonth_isOnlyDays() {
        XCTAssertEqual(breakdown("2026-01-10", "2026-02-09"), Breakdown(years: 0, months: 0, days: 30))
        XCTAssertEqual(breakdown("2026-01-10", "2026-02-10"), Breakdown(years: 0, months: 1, days: 0))
    }

    func testBreakdown_from31st_monthIsCompleteOnLastDayOfFebruary() {
        // Même règle que les mois-anniversaires (spec, story 40) : le 31 → dernier jour du mois.
        XCTAssertEqual(breakdown("2026-01-31", "2026-02-27"), Breakdown(years: 0, months: 0, days: 27))
        XCTAssertEqual(breakdown("2026-01-31", "2026-02-28"), Breakdown(years: 0, months: 1, days: 0))
        XCTAssertEqual(breakdown("2026-01-31", "2026-03-01"), Breakdown(years: 0, months: 1, days: 1))
        XCTAssertEqual(breakdown("2026-01-31", "2026-03-30"), Breakdown(years: 0, months: 1, days: 30))
        XCTAssertEqual(breakdown("2026-01-31", "2026-03-31"), Breakdown(years: 0, months: 2, days: 0))
    }

    func testBreakdown_from31st_monthIsCompleteOn30th() {
        XCTAssertEqual(breakdown("2026-03-31", "2026-04-29"), Breakdown(years: 0, months: 0, days: 29))
        XCTAssertEqual(breakdown("2026-03-31", "2026-04-30"), Breakdown(years: 0, months: 1, days: 0))
        XCTAssertEqual(breakdown("2026-03-31", "2026-05-01"), Breakdown(years: 0, months: 1, days: 1))
    }

    func testBreakdown_from30th_elevenMonthsOnFebruary28() {
        // 2026-03-30 + 11 mois = 30 février 2027 → ramené au 28.
        XCTAssertEqual(breakdown("2026-03-30", "2027-02-28"), Breakdown(years: 0, months: 11, days: 0))
        XCTAssertEqual(breakdown("2026-03-30", "2027-03-01"), Breakdown(years: 0, months: 11, days: 1))
        XCTAssertEqual(breakdown("2026-03-30", "2027-03-30"), Breakdown(years: 1, months: 0, days: 0))
    }

    func testBreakdown_from31stJanuary_inLeapYear_monthIsCompleteOnFebruary29() {
        XCTAssertEqual(breakdown("2028-01-31", "2028-02-28"), Breakdown(years: 0, months: 0, days: 28))
        XCTAssertEqual(breakdown("2028-01-31", "2028-02-29"), Breakdown(years: 0, months: 1, days: 0))
    }

    func testBreakdown_fromFebruary29_anniversaryOnFebruary28InCommonYears() {
        XCTAssertEqual(breakdown("2024-02-29", "2025-02-27"), Breakdown(years: 0, months: 11, days: 29))
        XCTAssertEqual(breakdown("2024-02-29", "2025-02-28"), Breakdown(years: 1, months: 0, days: 0))
        XCTAssertEqual(breakdown("2024-02-29", "2025-03-01"), Breakdown(years: 1, months: 0, days: 1))
        XCTAssertEqual(breakdown("2024-02-29", "2028-02-28"), Breakdown(years: 3, months: 11, days: 30))
        XCTAssertEqual(breakdown("2024-02-29", "2028-02-29"), Breakdown(years: 4, months: 0, days: 0))
    }

    func testBreakdown_acrossYearEnd() {
        // 2025-12-15 → 2026-01-15 = 1 mois ; → 2026-01-14 = 30 jours.
        XCTAssertEqual(breakdown("2025-12-15", "2026-01-15"), Breakdown(years: 0, months: 1, days: 0))
        XCTAssertEqual(breakdown("2025-12-15", "2026-01-14"), Breakdown(years: 0, months: 0, days: 30))
    }

    // MARK: - Conversion d'un compte à rebours atteint

    func testReachedCountdown_continuesAsSinceFromTargetDate() throws {
        let countdown = Goal(kind: .countdown, startDate: date("2026-01-01"), targetDate: date("2026-01-11"))
        let today = date("2026-01-16")
        XCTAssertEqual(CountdownEngine.compute(countdown, today: today).phase, .reached)

        let converted = countdown.continuedAsSince()
        XCTAssertEqual(converted, Goal(kind: .since, startDate: date("2026-01-11")))

        let state = CountdownEngine.compute(try XCTUnwrap(converted), today: today)
        XCTAssertEqual(state.phase, .elapsed)
        XCTAssertEqual(state.days, 5) // même nombre que « Atteint il y a 5 jours »
        XCTAssertNil(state.progress)
        XCTAssertEqual(state.breakdown, Breakdown(years: 0, months: 0, days: 5))
    }

    func testSinceGoal_cannotBeConvertedAgain() {
        XCTAssertNil(since("2026-01-01").continuedAsSince())
    }
}
