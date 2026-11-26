// Non vérifié : écrit sans Xcode (machine Linux).
import Foundation
import XCTest
import GoalCore

/// Rejoue `shared-cases/streaks.json` (même fichier que `StreaksSharedCasesTest` côté JUnit).
final class StreaksSharedCasesTests: XCTestCase {

    /// Une série du fichier : `[start, end]` ou `[start, end, jours]`.
    private struct StreakJSON: Decodable {
        let start: String
        let end: String
        let days: Int?

        init(from decoder: Decoder) throws {
            var c = try decoder.unkeyedContainer()
            start = try c.decode(String.self)
            end = try c.decode(String.self)
            days = c.isAtEnd ? nil : try c.decode(Int.self)
        }
    }

    private struct StreaksFile: Decodable {
        struct Expected: Decodable {
            let error: String?
            let startDate: String?
            let history: [StreakJSON]?
            let currentDays: Int?
            let bestDays: Int?
            let resetCount: Int?
            let isRecord: Bool?
            let encoded: String?
        }
        struct StreakCase: Decodable {
            let name: String
            let startDate: String
            let history: [StreakJSON]
            let today: String
            let resetOn: String?
            let expected: Expected
        }
        struct DecodingCase: Decodable {
            let name: String
            let raw: String
            let expected: [StreakJSON]
        }
        let version: Int
        let cases: [StreakCase]
        let decoding: [DecodingCase]
    }

    private func load() throws -> StreaksFile {
        let url = EngineSharedCasesTests.sharedCasesDir.appendingPathComponent("streaks.json")
        return try JSONDecoder().decode(StreaksFile.self, from: Data(contentsOf: url))
    }

    private func streaks(_ list: [StreakJSON], _ name: String) throws -> [PastStreak] {
        try list.map {
            PastStreak(
                start: try XCTUnwrap(LocalDate(iso: $0.start), name),
                end: try XCTUnwrap(LocalDate(iso: $0.end), name)
            )
        }
    }

    func testStreaksSharedCases() throws {
        let file = try load()
        XCTAssertFalse(file.cases.isEmpty, "streaks.json ne contient aucun cas")

        for c in file.cases {
            let today = try XCTUnwrap(LocalDate(iso: c.today), c.name)
            var goal = Goal(kind: .since, startDate: try XCTUnwrap(LocalDate(iso: c.startDate), c.name))
            var history = try streaks(c.history, c.name)
            let expected = c.expected

            if let raw = c.resetOn {
                let on = try XCTUnwrap(LocalDate(iso: raw), c.name)
                XCTAssertEqual(Streaks.validate(goal: goal, on: on, today: today)?.rawValue, expected.error, "\(c.name): error")
                switch Streaks.reset(goal: goal, history: history, on: on, today: today) {
                case .success(let reset):
                    goal = reset.goal
                    history = reset.history
                case .failure(let error):
                    XCTAssertEqual(error.rawValue, expected.error, "\(c.name): error")
                }
            }

            let summary = Streaks.summary(goal: goal, history: history, today: today)
            if let start = expected.startDate {
                XCTAssertEqual(goal.startDate, LocalDate(iso: start), "\(c.name): startDate")
            }
            if let rows = expected.history {
                XCTAssertEqual(history, try streaks(rows, c.name), "\(c.name): history")
                XCTAssertEqual(history.map(\.days), rows.compactMap(\.days), "\(c.name): history days")
            }
            if let v = expected.currentDays { XCTAssertEqual(summary.currentDays, v, "\(c.name): currentDays") }
            if let v = expected.bestDays { XCTAssertEqual(summary.bestDays, v, "\(c.name): bestDays") }
            if let v = expected.resetCount { XCTAssertEqual(summary.resetCount, v, "\(c.name): resetCount") }
            if let v = expected.isRecord { XCTAssertEqual(summary.isRecord, v, "\(c.name): isRecord") }
            if let v = expected.encoded {
                XCTAssertEqual(Streaks.encode(history), v, "\(c.name): encoded")
                XCTAssertEqual(Streaks.decode(Streaks.encode(history)), history, "\(c.name): aller-retour")
            }
        }
    }

    func testDecodingSharedCases() throws {
        for c in try load().decoding {
            XCTAssertEqual(Streaks.decode(c.raw), try streaks(c.expected, c.name), c.name)
        }
        XCTAssertEqual(Streaks.decode(nil), [])
    }

    func testWidgetDoubleTap() {
        let t0 = Date(timeIntervalSince1970: 1_000_000)
        XCTAssertEqual(WidgetResetTap.onTap(armedAt: nil, now: t0), .arm)
        XCTAssertEqual(WidgetResetTap.onTap(armedAt: t0, now: t0.addingTimeInterval(1)), .reset)
        XCTAssertEqual(WidgetResetTap.onTap(armedAt: t0, now: t0.addingTimeInterval(WidgetResetTap.window - 0.001)), .reset)
        XCTAssertEqual(WidgetResetTap.onTap(armedAt: t0, now: t0.addingTimeInterval(WidgetResetTap.window)), .arm)
        // Horloge reculée : on réarme plutôt que de remettre à zéro.
        XCTAssertEqual(WidgetResetTap.onTap(armedAt: t0, now: t0.addingTimeInterval(-1)), .arm)
    }
}
