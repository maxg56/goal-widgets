// Non vérifié : écrit sans Xcode (machine Linux).
import XCTest
import GoalCore

/// Grille de points (ticket 12) : plage, unité, état de chaque cellule.
/// Valeurs attendues calculées à la main à partir des règles du ticket.
final class DotGridTests: XCTestCase {

    private func date(_ iso: String) -> LocalDate {
        LocalDate(iso: iso)!
    }

    private func countdown(_ start: String, _ target: String) -> Goal {
        Goal(kind: .countdown, startDate: date(start), targetDate: date(target))
    }

    private func since(_ start: String) -> Goal {
        Goal(kind: .since, startDate: date(start))
    }

    private func grid(_ goal: Goal, _ today: String, _ period: DotGrid.Period = .whole) -> DotGrid {
        DotGrid.compute(goal, today: date(today), period: period)
    }

    // MARK: - Objectif entier

    func testShortCountdown_oneDayPerCell_pastTodayAndRemaining() {
        let g = grid(countdown("2026-01-01", "2026-01-10"), "2026-01-04")
        XCTAssertEqual(g.unit, .day)
        XCTAssertEqual(g.cells.count, 10)
        XCTAssertEqual(g.compactStates, "eeetrrrrrr")
        XCTAssertEqual(g.cells.first, DotGrid.Cell(start: date("2026-01-01"), end: date("2026-01-01"), state: .elapsed))
        XCTAssertEqual(g.cells.last, DotGrid.Cell(start: date("2026-01-10"), end: date("2026-01-10"), state: .remaining))
    }

    func testCountdownBeforeStart_allRemaining() {
        let g = grid(countdown("2026-01-01", "2026-01-05"), "2025-12-20")
        XCTAssertEqual(g.compactStates, "rrrrr")
    }

    func testCountdownAfterTarget_allElapsed() {
        let g = grid(countdown("2026-01-01", "2026-01-05"), "2026-02-01")
        XCTAssertEqual(g.compactStates, "eeeee")
    }

    func testCountdownOn366Days_staysInDays() {
        // 2026-01-01 … 2027-01-01 inclus = 366 jours.
        let g = grid(countdown("2026-01-01", "2027-01-01"), "2026-01-01")
        XCTAssertEqual(g.unit, .day)
        XCTAssertEqual(g.cells.count, 366)
    }

    func testCountdownOn367Days_switchesToWeeks_lastWeekShorter() {
        // 367 jours → 53 cellules de 7 jours, la dernière = 3 jours (2026-12-31 … 2027-01-02).
        let g = grid(countdown("2026-01-01", "2027-01-02"), "2026-01-01")
        XCTAssertEqual(g.unit, .week)
        XCTAssertEqual(g.cells.count, 53)
        XCTAssertEqual(g.cells.first?.start, date("2026-01-01"))
        XCTAssertEqual(g.cells.first?.end, date("2026-01-07"))
        XCTAssertEqual(g.cells.last?.start, date("2026-12-31"))
        XCTAssertEqual(g.cells.last?.end, date("2027-01-02"))
    }

    func testLongCountdownInWeeks_todayInsideSecondWeek() {
        // 730 jours → 105 semaines (la dernière : 2027-12-30 … 2027-12-31).
        let g = grid(countdown("2026-01-01", "2027-12-31"), "2026-01-10")
        XCTAssertEqual(g.unit, .week)
        XCTAssertEqual(g.cells.count, 105)
        XCTAssertEqual(g.compactStates, "et" + String(repeating: "r", count: 103))
        XCTAssertEqual(g.cells.last, DotGrid.Cell(start: date("2027-12-30"), end: date("2027-12-31"), state: .remaining))
    }

    func testCountdownOn366Weeks_staysInWeeks_oneMoreDaySwitchesToMonths() {
        // 366 semaines = 2562 jours : 2000-01-01 … 2007-01-05 inclus.
        let weeks = grid(countdown("2000-01-01", "2007-01-05"), "2000-01-01")
        XCTAssertEqual(weeks.unit, .week)
        XCTAssertEqual(weeks.cells.count, 366)
        XCTAssertEqual(weeks.cells.last?.end, date("2007-01-05"))

        let months = grid(countdown("2000-01-01", "2007-01-06"), "2000-01-01")
        XCTAssertEqual(months.unit, .month)
        // Janvier 2000 … janvier 2007 = 85 mois calendaires.
        XCTAssertEqual(months.cells.count, 85)
    }

    func testVeryLongCountdownInMonths_firstAndLastMonthTruncated() {
        let g = grid(countdown("2000-01-15", "2010-01-01"), "2000-02-29")
        XCTAssertEqual(g.unit, .month)
        XCTAssertEqual(g.cells.count, 121) // janvier 2000 … janvier 2010
        XCTAssertEqual(g.cells.first, DotGrid.Cell(start: date("2000-01-15"), end: date("2000-01-31"), state: .elapsed))
        // Février 2000 (bissextile) contient aujourd'hui.
        XCTAssertEqual(g.cells[1], DotGrid.Cell(start: date("2000-02-01"), end: date("2000-02-29"), state: .today))
        XCTAssertEqual(g.cells.last, DotGrid.Cell(start: date("2010-01-01"), end: date("2010-01-01"), state: .remaining))
    }

    func testCountdownStartEqualsTarget_singleCell() {
        let g = grid(countdown("2026-03-01", "2026-03-01"), "2026-03-01")
        XCTAssertEqual(g.compactStates, "t")
    }

    func testCountdownTargetBeforeStart_singleCellOnStart() {
        // Cas non couvert par le ticket : la plage est ramenée à la date de départ (jamais inversée).
        let g = grid(countdown("2026-03-10", "2026-03-01"), "2026-03-05")
        XCTAssertEqual(g.cells, [DotGrid.Cell(start: date("2026-03-10"), end: date("2026-03-10"), state: .remaining)])
    }

    func testSinceWhole_fromStartToToday() {
        let g = grid(since("2026-03-01"), "2026-03-05")
        XCTAssertEqual(g.unit, .day)
        XCTAssertEqual(g.compactStates, "eeeet")
    }

    func testSinceWithFutureStart_singleTodayCell() {
        let g = grid(since("2026-12-01"), "2026-11-25")
        XCTAssertEqual(g.cells, [DotGrid.Cell(start: date("2026-11-25"), end: date("2026-11-25"), state: .today)])
    }

    // MARK: - Mois en cours

    func testMonth_countdownInsideMonth_outsideBeforeStartAndAfterTarget() {
        // Novembre 2026 : 1–9 hors objectif, 10–14 écoulés, 15 aujourd'hui, 16–20 restants, 21–30 hors objectif.
        let g = grid(countdown("2026-11-10", "2026-11-20"), "2026-11-15", .month)
        XCTAssertEqual(g.unit, .day)
        XCTAssertEqual(g.cells.count, 30)
        XCTAssertEqual(
            g.compactStates,
            String(repeating: "o", count: 9) + "eeeee" + "t" + "rrrrr" + String(repeating: "o", count: 10)
        )
        XCTAssertEqual(g.cells.first?.start, date("2026-11-01"))
        XCTAssertEqual(g.cells.last?.end, date("2026-11-30"))
    }

    func testMonth_todayAfterTarget_todayCellIsOutside() {
        // `outside` est évalué en premier : un jour hors de l'objectif reste hors objectif, même aujourd'hui.
        let g = grid(countdown("2026-11-10", "2026-11-20"), "2026-11-25", .month)
        XCTAssertEqual(
            g.compactStates,
            String(repeating: "o", count: 9) + String(repeating: "e", count: 11) + String(repeating: "o", count: 10)
        )
    }

    func testMonth_leapFebruary_29Cells() {
        let g = grid(since("2020-01-01"), "2028-02-10", .month)
        XCTAssertEqual(g.cells.count, 29)
        XCTAssertEqual(g.compactStates, String(repeating: "e", count: 9) + "t" + String(repeating: "r", count: 19))
    }

    func testMonth_commonFebruary_28Cells() {
        let g = grid(since("2020-01-01"), "2026-02-28", .month)
        XCTAssertEqual(g.cells.count, 28)
        XCTAssertEqual(g.compactStates, String(repeating: "e", count: 27) + "t")
    }

    func testMonth_sinceStartedMidMonth_outsideBeforeStart() {
        let g = grid(since("2026-01-29"), "2026-01-30", .month)
        XCTAssertEqual(g.compactStates, String(repeating: "o", count: 28) + "etr")
    }

    // MARK: - Semaine en cours

    func testWeek_mondayToSunday_outsideBeforeSinceStart() {
        // Mercredi 2026-11-25 → semaine du lundi 23 au dimanche 29.
        let g = grid(since("2026-11-25"), "2026-11-27", .week)
        XCTAssertEqual(g.unit, .day)
        XCTAssertEqual(g.cells.count, 7)
        XCTAssertEqual(g.cells.first?.start, date("2026-11-23"))
        XCTAssertEqual(g.cells.last?.end, date("2026-11-29"))
        XCTAssertEqual(g.compactStates, "ooeetrr")
    }

    func testWeek_acrossNewYear() {
        // Jeudi 2026-12-31 → lundi 2026-12-28 … dimanche 2027-01-03 ; cible le 1er janvier.
        let g = grid(countdown("2026-12-01", "2027-01-01"), "2026-12-31", .week)
        XCTAssertEqual(g.cells.first?.start, date("2026-12-28"))
        XCTAssertEqual(g.cells.last?.end, date("2027-01-03"))
        XCTAssertEqual(g.compactStates, "eeetroo")
    }

    func testWeek_todayIsSunday_lastCell() {
        let g = grid(since("2020-01-01"), "2026-11-29", .week)
        XCTAssertEqual(g.compactStates, "eeeeeet")
    }

    func testWeek_futureCountdown_allOutsideButNotToday() {
        let g = grid(countdown("2027-01-01", "2027-02-01"), "2026-11-25", .week)
        XCTAssertEqual(g.compactStates, "ooooooo")
    }
}
