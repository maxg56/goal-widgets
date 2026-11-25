// Non vérifié : écrit sans Xcode (machine Linux).
import Foundation
import XCTest
import GoalCore

/// Rejoue `shared-cases/export/cases.json` (même fichier que `ExportSharedCasesTest` côté JUnit) :
/// décodage du fichier d'exemple, puis fusion avec les identifiants `existingIds`.
final class ExportSharedCasesTests: XCTestCase {

    private static let exportDir = EngineSharedCasesTests.sharedCasesDir
        .appendingPathComponent("export", isDirectory: true)

    private struct CaseFile: Decodable {
        let version: Int
        let cases: [ExportCase]
    }

    private struct GoalJSON: Decodable {
        let id: String
        let title: String
        let kind: String
        let startDate: String
        let targetDate: String?
        let color: String?
        let emoji: String?
        let notificationsEnabled: Bool?
        let createdAt: String
    }

    private struct Expected: Decodable {
        // Erreur attendue (fichier refusé en entier).
        let error: String?
        let index: Int?
        let field: String?
        let version: Int?
        let id: String?
        // Succès.
        let goals: [GoalJSON]?
        let added: [String]?
        let skipped: Int?
    }

    private struct ExportCase: Decodable {
        let name: String
        let file: String
        let existingIds: [String]
        let expected: Expected
    }

    func testExportSharedCases() throws {
        let data = try Data(contentsOf: Self.exportDir.appendingPathComponent("cases.json"))
        let file = try JSONDecoder().decode(CaseFile.self, from: data)
        XCTAssertFalse(file.cases.isEmpty, "export/cases.json ne contient aucun cas")

        for c in file.cases {
            let bytes = try Data(contentsOf: Self.exportDir.appendingPathComponent(c.file))
            let result = GoalExport.decode(bytes)

            if let code = c.expected.error {
                guard case let .failure(error) = result else {
                    XCTFail("\(c.name): erreur \(code) attendue, fichier accepté")
                    continue
                }
                XCTAssertEqual(error.code, code, "\(c.name): code")
                switch error {
                case let .unsupportedVersion(version):
                    if let expected = c.expected.version { XCTAssertEqual(version, expected, "\(c.name): version") }
                case let .invalidGoal(index, field):
                    if let expected = c.expected.index { XCTAssertEqual(index, expected, "\(c.name): index") }
                    if let expected = c.expected.field { XCTAssertEqual(field, expected, "\(c.name): field") }
                case let .duplicateId(id):
                    if let expected = c.expected.id { XCTAssertEqual(id, expected, "\(c.name): id") }
                case .notJson, .notAnExport:
                    break
                }
                continue
            }

            let goals: [ExportedGoal]
            switch result {
            case let .success(decoded):
                goals = decoded
            case let .failure(error):
                XCTFail("\(c.name): fichier refusé (\(error.code)), succès attendu")
                continue
            }

            if let expectedGoals = c.expected.goals {
                XCTAssertEqual(goals.count, expectedGoals.count, "\(c.name): nombre d'objectifs")
                for (actual, expected) in zip(goals, expectedGoals) {
                    try assertGoal(actual, equals: expected, caseName: c.name)
                }
            }

            let plan = GoalImport.plan(existingIds: Set(c.existingIds), incoming: goals)
            if let added = c.expected.added {
                XCTAssertEqual(plan.toAdd.map(\.id), added, "\(c.name): added")
            }
            if let skipped = c.expected.skipped {
                XCTAssertEqual(plan.skipped, skipped, "\(c.name): skipped")
            }

            // Idempotence : réimporter le même fichier après l'import n'ajoute rien.
            let afterImport = Set(c.existingIds).union(plan.toAdd.map(\.id))
            let again = GoalImport.plan(existingIds: afterImport, incoming: goals)
            XCTAssertTrue(again.toAdd.isEmpty, "\(c.name): un second import ne doit rien ajouter")
            XCTAssertEqual(again.skipped, goals.count, "\(c.name): second import, tout est ignoré")
        }
    }

    private func assertGoal(_ actual: ExportedGoal, equals expected: GoalJSON, caseName: String) throws {
        let label = "\(caseName) [\(expected.id)]"
        XCTAssertEqual(actual.id, expected.id, "\(label): id")
        XCTAssertEqual(actual.title, expected.title, "\(label): title")
        XCTAssertEqual(actual.goal.kind.rawValue, expected.kind, "\(label): kind")
        XCTAssertEqual(actual.goal.startDate.iso, expected.startDate, "\(label): startDate")
        XCTAssertEqual(actual.goal.targetDate?.iso, expected.targetDate, "\(label): targetDate")
        XCTAssertEqual(actual.color?.rawValue, expected.color, "\(label): color")
        XCTAssertEqual(actual.emoji, expected.emoji, "\(label): emoji")
        XCTAssertEqual(actual.notificationsEnabled, expected.notificationsEnabled, "\(label): notificationsEnabled")
        // `createdAt` comparé comme instant (l'écriture du fichier peut différer : décalage, fractions).
        let expectedInstant = try XCTUnwrap(GoalExport.parseInstant(expected.createdAt), "\(label): createdAt attendu illisible")
        XCTAssertEqual(
            actual.createdAt.timeIntervalSince1970,
            expectedInstant.timeIntervalSince1970,
            accuracy: 0.0005,
            "\(label): createdAt"
        )
    }
}
