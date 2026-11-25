// Non vérifié : écrit sans Xcode (machine Linux).
import Foundation
import XCTest
import GoalCore

/// Rejoue `shared-cases/engine.json` (même fichier que les tests JUnit).
/// Seuls les champs présents dans `expected` sont vérifiés.
final class EngineSharedCasesTests: XCTestCase {

    /// ios/GoalCore/Tests/GoalCoreTests/<ce fichier> → racine du dépôt (5 niveaux plus haut).
    static let sharedCasesDir: URL = {
        var url = URL(fileURLWithPath: #filePath)
        for _ in 0..<5 { url.deleteLastPathComponent() }
        return url.appendingPathComponent("shared-cases", isDirectory: true)
    }()

    private struct CaseFile: Decodable {
        let version: Int
        let cases: [EngineCase]
    }

    private struct EngineCase: Decodable {
        struct GoalJSON: Decodable {
            let kind: String
            let startDate: String
            let targetDate: String?
        }
        /// Chaque champ est vérifié seulement s'il est présent.
        /// `progress` distingue « absent » (non vérifié) de `null` explicite (doit valoir nil).
        struct Expected: Decodable {
            let phase: String?
            let days: Int?
            let progress: Double??

            private enum CodingKeys: String, CodingKey { case phase, days, progress }

            init(from decoder: Decoder) throws {
                let c = try decoder.container(keyedBy: CodingKeys.self)
                phase = try c.decodeIfPresent(String.self, forKey: .phase)
                days = try c.decodeIfPresent(Int.self, forKey: .days)
                if c.contains(.progress) {
                    let value: Double? = try c.decodeNil(forKey: .progress)
                        ? nil
                        : try c.decode(Double.self, forKey: .progress)
                    progress = .some(value)
                } else {
                    progress = .none
                }
            }
        }
        let name: String
        let goal: GoalJSON
        let today: String
        let expected: Expected
    }

    func testEngineSharedCases() throws {
        let data = try Data(contentsOf: Self.sharedCasesDir.appendingPathComponent("engine.json"))
        let file = try JSONDecoder().decode(CaseFile.self, from: data)
        XCTAssertFalse(file.cases.isEmpty, "engine.json ne contient aucun cas")

        for c in file.cases {
            let kind = try XCTUnwrap(GoalKind(rawValue: c.goal.kind), "\(c.name): kind")
            let start = try XCTUnwrap(LocalDate(iso: c.goal.startDate), "\(c.name): startDate")
            let target = try c.goal.targetDate.map { try XCTUnwrap(LocalDate(iso: $0), "\(c.name): targetDate") }
            let today = try XCTUnwrap(LocalDate(iso: c.today), "\(c.name): today")

            let state = CountdownEngine.compute(Goal(kind: kind, startDate: start, targetDate: target), today: today)

            if let phase = c.expected.phase {
                XCTAssertEqual(state.phase.rawValue, phase, "\(c.name): phase")
            }
            if let days = c.expected.days {
                XCTAssertEqual(state.days, days, "\(c.name): days")
            }
            switch c.expected.progress {
            case .none:
                break // champ absent : non vérifié
            case .some(.none):
                XCTAssertNil(state.progress, "\(c.name): progress devrait être absent (null)")
            case .some(.some(let progress)):
                let actual = try XCTUnwrap(state.progress, "\(c.name): progress attendu mais absent")
                XCTAssertEqual(actual, progress, accuracy: 1e-6, "\(c.name): progress")
            }
        }
    }
}
