// Non vérifié : écrit sans Xcode (machine Linux).
import Foundation
import XCTest
import GoalCore

/// Écriture du format `goal-widgets-export` v1 (la lecture est couverte par les cas partagés).
final class GoalExportTests: XCTestCase {

    private let exam = ExportedGoal(
        id: "3F2B8C1E-4D5A-4E6F-9A7B-1C2D3E4F5A6B",
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
        notificationsEnabled: false,
        createdAt: Date(timeIntervalSince1970: 1_709_236_800) // 2024-02-29T20:00:00Z
    )

    private func json(_ data: Data) throws -> [String: Any] {
        try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }

    func testEncodedFileHasFormatVersionAndAllFields() throws {
        let data = GoalExport.encode([exam, quit], exportedAt: Date(timeIntervalSince1970: 1_795_595_400)) // 2026-11-25T08:30:00Z
        let root = try json(data)
        XCTAssertEqual(root["format"] as? String, "goal-widgets-export")
        XCTAssertEqual(root["version"] as? Int, 1)
        XCTAssertEqual(root["exportedAt"] as? String, "2026-11-25T08:30:00.000Z")
        let goals = try XCTUnwrap(root["goals"] as? [[String: Any]])
        XCTAssertEqual(goals.count, 2)

        let first = goals[0]
        XCTAssertEqual(first["id"] as? String, "3f2b8c1e-4d5a-4e6f-9a7b-1c2d3e4f5a6b", "id écrit en minuscules")
        XCTAssertEqual(first["title"] as? String, "Examen")
        XCTAssertEqual(first["kind"] as? String, "countdown")
        XCTAssertEqual(first["startDate"] as? String, "2026-09-01")
        XCTAssertEqual(first["targetDate"] as? String, "2027-06-15")
        XCTAssertEqual(first["color"] as? String, "blue")
        XCTAssertEqual(first["emoji"] as? String, "🎓")
        XCTAssertEqual(first["notificationsEnabled"] as? Bool, true)
        XCTAssertEqual(first["createdAt"] as? String, "2026-09-01T07:12:45.123Z")

        // Les valeurs absentes sont écrites en `null` explicite, comme Android.
        let second = goals[1]
        for key in ["targetDate", "color", "emoji"] {
            XCTAssertTrue(second.keys.contains(key), "clé \(key) écrite")
            XCTAssertTrue(second[key] is NSNull, "\(key) = null")
        }
        XCTAssertEqual(second["createdAt"] as? String, "2024-02-29T20:00:00.000Z", "toujours 3 décimales")
    }

    func testEncodeThenDecodeGivesTheSameGoals() throws {
        let data = GoalExport.encode([exam, quit], exportedAt: Date())
        guard case let .success(decoded) = GoalExport.decode(data) else {
            return XCTFail("un fichier écrit par l'app doit se relire")
        }
        XCTAssertEqual(decoded.map(\.id), ["3f2b8c1e-4d5a-4e6f-9a7b-1c2d3e4f5a6b", "a1b2c3d4-0000-4000-8000-00000000abcd"])
        XCTAssertEqual(decoded.map(\.title), ["Examen", "Arrêt du tabac"])
        XCTAssertEqual(decoded.map(\.goal), [exam.goal, quit.goal])
        XCTAssertEqual(decoded.map(\.color), [.blue, nil])
        XCTAssertEqual(decoded.map(\.emoji), ["🎓", nil])
        XCTAssertEqual(decoded.map(\.notificationsEnabled), [true, false])
        XCTAssertEqual(decoded[0].createdAt.timeIntervalSince1970, 1_788_246_765.123, accuracy: 0.0005)
    }

    func testInstantReading() {
        XCTAssertEqual(GoalExport.parseInstant("2025-07-14T10:00:00+02:00")?.timeIntervalSince1970, 1_752_480_000)
        XCTAssertEqual(GoalExport.parseInstant("2025-07-14T08:00:00Z")?.timeIntervalSince1970, 1_752_480_000)
        XCTAssertEqual(GoalExport.parseInstant("2025-07-14T08:00:00.5Z")?.timeIntervalSince1970 ?? 0, 1_752_480_000.5, accuracy: 0.0005)
        XCTAssertEqual(GoalExport.parseInstant("2025-07-14T08:00:00.123456789Z")?.timeIntervalSince1970 ?? 0, 1_752_480_000.123, accuracy: 0.0005)
        XCTAssertNil(GoalExport.parseInstant("2025-07-14T08:00:00"), "décalage obligatoire")
        XCTAssertNil(GoalExport.parseInstant("2025-07-14"))
        XCTAssertNil(GoalExport.parseInstant("2025-02-30T08:00:00Z"))
        XCTAssertNil(GoalExport.parseInstant("2025-07-14T24:00:00Z"))
    }

    func testRejectsVersionWrittenAsString() {
        let data = Data(#"{ "format": "goal-widgets-export", "version": "1", "goals": [] }"#.utf8)
        guard case let .failure(error) = GoalExport.decode(data) else { return XCTFail("refus attendu") }
        XCTAssertEqual(error, .notAnExport)
    }

    func testGoalThatIsNotAnObject() {
        let data = Data(#"{ "format": "goal-widgets-export", "version": 1, "goals": [42] }"#.utf8)
        guard case let .failure(error) = GoalExport.decode(data) else { return XCTFail("refus attendu") }
        XCTAssertEqual(error, .invalidGoal(index: 0, field: "goal"))
    }

    func testNotificationsEnabledOfWrongType() {
        let data = Data(#"""
        { "format": "goal-widgets-export", "version": 1, "goals": [
          { "id": "11111111-1111-4111-8111-111111111111", "title": "A", "kind": "since",
            "startDate": "2026-01-01", "notificationsEnabled": 1, "createdAt": "2026-01-01T00:00:00Z" } ] }
        """#.utf8)
        guard case let .failure(error) = GoalExport.decode(data) else { return XCTFail("refus attendu") }
        XCTAssertEqual(error, .invalidGoal(index: 0, field: "notificationsEnabled"))
    }

    func testSuggestedFileName() {
        XCTAssertEqual(GoalExport.suggestedFileName(today: LocalDate(year: 2026, month: 11, day: 5)), "goal-widgets-2026-11-05.json")
    }
}

/// Couleur et emoji : normalisation partagée par le formulaire et l'import.
final class GoalStyleTests: XCTestCase {
    func testTitleWithEmoji() {
        XCTAssertEqual(GoalStyle(color: nil, emoji: "🎓").decorate("Examen"), "🎓 Examen")
        XCTAssertEqual(GoalStyle(color: .blue, emoji: nil).decorate("Examen"), "Examen")
    }

    func testEmojiNormalization() {
        XCTAssertNil(GoalStyle.normalizedEmoji("   "))
        XCTAssertNil(GoalStyle.normalizedEmoji(nil))
        XCTAssertEqual(GoalStyle.normalizedEmoji(" 👨‍👩‍👧 "), "👨‍👩‍👧")
    }

    func testColorWireValues() {
        XCTAssertEqual(GoalColor.allCases.map(\.rawValue),
                       ["red", "orange", "yellow", "green", "teal", "blue", "indigo", "purple", "pink", "gray"])
        XCTAssertNil(GoalColor(wire: "chartreuse"))
        XCTAssertNil(GoalColor(wire: nil))
        XCTAssertEqual(GoalColor(wire: "teal"), .teal)
    }
}
