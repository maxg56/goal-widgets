// Non vérifié : écrit sans Xcode (machine Linux).
import XCTest
import GoalCore

/// Règles du formulaire de création d'un compte à rebours (ticket 02/03).
final class CountdownValidationTests: XCTestCase {

    private let jan1 = LocalDate(year: 2026, month: 1, day: 1)
    private let jan2 = LocalDate(year: 2026, month: 1, day: 2)

    func testValidDraft_hasNoError() {
        XCTAssertNil(CountdownValidation.check(title: "Examen", startDate: jan1, targetDate: jan2))
    }

    func testBlankTitle_isRejected() {
        XCTAssertEqual(CountdownValidation.check(title: "   ", startDate: jan1, targetDate: jan2), .emptyTitle)
    }

    func testTargetEqualToStart_isRejected() {
        XCTAssertEqual(CountdownValidation.check(title: "Examen", startDate: jan1, targetDate: jan1), .targetNotAfterStart)
    }

    func testTargetBeforeStart_isRejected() {
        XCTAssertEqual(CountdownValidation.check(title: "Examen", startDate: jan2, targetDate: jan1), .targetNotAfterStart)
    }
}
