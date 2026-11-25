// Non vérifié : écrit sans Xcode (machine Linux).
import XCTest
import GoalCore

/// Règles du formulaire d'un objectif « depuis » (ticket 05, mêmes règles qu'Android 04).
final class SinceValidationTests: XCTestCase {

    private let today = LocalDate(year: 2026, month: 3, day: 15)

    func testPastStart_isValid() {
        XCTAssertNil(SinceValidation.check(title: "Arrêt du tabac", startDate: LocalDate(year: 2025, month: 1, day: 1), today: today))
    }

    func testStartToday_isValid() {
        XCTAssertNil(SinceValidation.check(title: "Arrêt du tabac", startDate: today, today: today))
    }

    func testBlankTitle_isRejected() {
        XCTAssertEqual(SinceValidation.check(title: "  ", startDate: today, today: today), .emptyTitle)
    }

    func testStartInFuture_isRejected() {
        XCTAssertEqual(
            SinceValidation.check(title: "Arrêt du tabac", startDate: LocalDate(year: 2026, month: 3, day: 16), today: today),
            .startInFuture
        )
    }
}
