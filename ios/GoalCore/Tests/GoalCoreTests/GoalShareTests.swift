// Non vérifié : écrit sans Xcode (machine Linux).
import Foundation
import XCTest
import GoalCore

/// Partage d'un objectif (ticket 13) : fichier `<titre>.goalwidgets.json` = export v1 d'un seul objectif.
final class GoalShareTests: XCTestCase {

    private let exam = ExportedGoal(
        id: "3f2b8c1e-4d5a-4e6f-9a7b-1c2d3e4f5a6b",
        title: "Examen",
        goal: Goal(kind: .countdown, startDate: LocalDate(year: 2026, month: 9, day: 1), targetDate: LocalDate(year: 2027, month: 6, day: 15)),
        color: .blue,
        emoji: "🎓",
        notificationsEnabled: true,
        createdAt: Date(timeIntervalSince1970: 1_788_246_765.123) // 2026-09-01T07:12:45.123Z
    )

    private let quit = ExportedGoal(
        id: "a1b2c3d4-0000-4000-8000-00000000abcd",
        title: "Arrêt du tabac",
        goal: Goal(kind: .since, startDate: LocalDate(year: 2024, month: 2, day: 29)),
        color: nil,
        emoji: nil,
        notificationsEnabled: nil,
        createdAt: Date(timeIntervalSince1970: 1_709_236_800)
    )

    private let sharedAt = Date(timeIntervalSince1970: 1_795_595_400)

    // MARK: Fichier

    func testShareFileIsAnExportOfOneGoalWithNotificationsLeftToTheRecipient() throws {
        let data = GoalShare.encode(exam, exportedAt: sharedAt)
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        XCTAssertEqual(root["format"] as? String, "goal-widgets-export")
        XCTAssertEqual(root["version"] as? Int, 1)
        let goals = try XCTUnwrap(root["goals"] as? [[String: Any]])
        XCTAssertEqual(goals.count, 1)
        XCTAssertTrue(goals[0]["notificationsEnabled"] is NSNull, "notificationsEnabled doit être écrit à null")
    }

    func testShareFileRoundTrip_sameGoalExceptNotifications() throws {
        let decoded = try GoalExport.decode(GoalShare.encode(exam, exportedAt: sharedAt)).get()
        XCTAssertEqual(decoded, [ExportedGoal(
            id: exam.id,
            title: exam.title,
            goal: exam.goal,
            color: exam.color,
            emoji: exam.emoji,
            notificationsEnabled: nil,
            createdAt: exam.createdAt
        )])
    }

    func testFileName_titleThenDoubleExtension() {
        XCTAssertEqual(GoalShare.fileName(title: "Examen"), "Examen.goalwidgets.json")
    }

    func testFileName_replacesPathAndReservedCharacters() {
        XCTAssertEqual(GoalShare.fileName(title: "Vacances 2027/2028"), "Vacances 2027-2028.goalwidgets.json")
        XCTAssertEqual(GoalShare.fileName(title: "a:b*c?\"d<e>f|g\\h"), "a-b-c--d-e-f-g-h.goalwidgets.json")
    }

    func testFileName_noHiddenFileAndNoEmptyName() {
        XCTAssertEqual(GoalShare.fileName(title: "  .cache "), "cache.goalwidgets.json")
        XCTAssertEqual(GoalShare.fileName(title: "   "), "objectif.goalwidgets.json")
        XCTAssertEqual(GoalShare.fileName(title: "a\nb"), "a b.goalwidgets.json")
    }

    func testFileName_longTitleTruncatedTo60Characters() {
        let name = GoalShare.fileName(title: String(repeating: "é", count: 80))
        XCTAssertEqual(name, String(repeating: "é", count: 60) + ".goalwidgets.json")
    }

    // MARK: Aperçu

    func testPreview_newGoalCanBeAdded() throws {
        let preview = try GoalShare.preview(GoalShare.encode(exam, exportedAt: sharedAt), existingIds: []).get()
        XCTAssertEqual(preview.items.map(\.goal.id), [exam.id])
        XCTAssertEqual(preview.items.map(\.alreadyPresent), [false])
        XCTAssertEqual(preview.toAddCount, 1)
    }

    func testPreview_goalAlreadyPresent_flaggedAndNothingToAdd() throws {
        // Comparaison sans casse, comme `GoalImport.plan`.
        let preview = try GoalShare.preview(
            GoalShare.encode(exam, exportedAt: sharedAt),
            existingIds: ["3F2B8C1E-4D5A-4E6F-9A7B-1C2D3E4F5A6B"]
        ).get()
        XCTAssertEqual(preview.items.map(\.alreadyPresent), [true])
        XCTAssertEqual(preview.toAddCount, 0)
    }

    func testPreview_fullExportListsEveryGoalInFileOrder() throws {
        let data = GoalExport.encode([exam, quit], exportedAt: sharedAt)
        let preview = try GoalShare.preview(data, existingIds: [quit.id]).get()
        XCTAssertEqual(preview.items.map(\.goal.title), ["Examen", "Arrêt du tabac"])
        XCTAssertEqual(preview.items.map(\.alreadyPresent), [false, true])
        XCTAssertEqual(preview.toAddCount, 1)
    }

    func testPreview_invalidFile_rejectedWithImportError() {
        XCTAssertEqual(GoalShare.preview(Data("pas du json".utf8), existingIds: []), .failure(.notJson))
        XCTAssertEqual(
            GoalShare.preview(Data(#"{"format":"autre","version":1,"goals":[]}"#.utf8), existingIds: []),
            .failure(.notAnExport)
        )
    }
}
