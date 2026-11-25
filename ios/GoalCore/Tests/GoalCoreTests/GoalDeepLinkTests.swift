// Non vérifié : écrit sans Xcode (machine Linux).
import XCTest
import GoalCore

/// Lien profond `goalwidgets://goal/<id>` ouvert par un appui sur le widget.
final class GoalDeepLinkTests: XCTestCase {

    private let id = UUID(uuidString: "11111111-2222-3333-4444-555555555555")!

    func testURLForGoal() {
        XCTAssertEqual(GoalDeepLink.url(forGoal: id).absoluteString,
                       "goalwidgets://goal/11111111-2222-3333-4444-555555555555")
    }

    func testParsesGoalURL_caseInsensitiveUUID() {
        let url = URL(string: "goalwidgets://goal/11111111-2222-3333-4444-555555555555")!
        XCTAssertEqual(GoalDeepLink.goalID(from: url), id)
        let lower = URL(string: "goalwidgets://goal/11111111-2222-3333-4444-555555555555".lowercased())!
        XCTAssertEqual(GoalDeepLink.goalID(from: lower), id)
    }

    func testRejectsOtherURLs() {
        for string in [
            "https://goal/11111111-2222-3333-4444-555555555555",
            "goalwidgets://other/11111111-2222-3333-4444-555555555555",
            "goalwidgets://goal/pas-un-uuid",
            "goalwidgets://goal/",
            "goalwidgets://goal/11111111-2222-3333-4444-555555555555/extra",
        ] {
            XCTAssertNil(GoalDeepLink.goalID(from: URL(string: string)!), string)
        }
    }
}
