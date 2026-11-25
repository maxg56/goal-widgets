// Non vérifié : écrit sans Xcode (machine Linux).
import XCTest
import GoalCore

/// Minuit local d'une date calendaire (instant des entrées de la timeline du widget).
final class LocalDateMidnightTests: XCTestCase {

    private func calendar(_ tz: String) -> Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: tz)!
        return cal
    }

    private func instant(_ iso8601: String) -> Date {
        ISO8601DateFormatter().date(from: iso8601)!
    }

    func testMidnightInParisWinter() {
        XCTAssertEqual(LocalDate(year: 2026, month: 1, day: 15).startOfDay(in: calendar("Europe/Paris")),
                       instant("2026-01-14T23:00:00Z"))
    }

    func testMidnightInParisOnDaylightSavingDay() {
        // Passage à l'heure d'été le 29 mars 2026 à 2 h : minuit est encore en UTC+1.
        XCTAssertEqual(LocalDate(year: 2026, month: 3, day: 29).startOfDay(in: calendar("Europe/Paris")),
                       instant("2026-03-28T23:00:00Z"))
        XCTAssertEqual(LocalDate(year: 2026, month: 3, day: 30).startOfDay(in: calendar("Europe/Paris")),
                       instant("2026-03-29T22:00:00Z"))
    }

    func testDayWithoutMidnight_startsAtFirstExistingInstant() {
        // São Paulo, 4 novembre 2018 : minuit n'existe pas (on passe de 0 h à 1 h, UTC−2).
        XCTAssertEqual(LocalDate(year: 2018, month: 11, day: 4).startOfDay(in: calendar("America/Sao_Paulo")),
                       instant("2018-11-04T03:00:00Z"))
    }

    func testRoundTripWithToday() {
        let cal = calendar("Pacific/Auckland")
        let day = LocalDate(year: 2026, month: 6, day: 1)
        XCTAssertEqual(LocalDate.today(calendar: cal, now: day.startOfDay(in: cal)), day)
    }
}
