// Non vérifié : écrit sans Xcode (machine Linux).
import Foundation
import SwiftData
import SwiftUI
import UniformTypeIdentifiers
import GoalCore

/// Export/import des objectifs au format `goal-widgets-export` v1 (`docs/export-format.md`).
/// Couche mince : le format et la fusion sont dans `GoalCore` (`GoalExport`, `GoalImport`).
enum GoalTransfer {
    /// Taille maximale d'un fichier importé (comme Android).
    static let maxImportBytes = 5 * 1024 * 1024

    /// Résultat affiché à l'utilisateur après un export ou un import.
    enum Outcome: Equatable {
        case exported(count: Int)
        case exportFailed
        case imported(added: Int, skipped: Int)
        case emptyFile
        case rejected(ImportError)
        case unreadable

        var message: String {
            switch self {
            case let .exported(count):
                return plural("export_done", count)
            case .exportFailed:
                return String(localized: "export_failed")
            case let .imported(added, skipped):
                let addedText = plural("import_added", added)
                return skipped > 0 ? "\(addedText) · \(plural("import_skipped", skipped))" : addedText
            case .emptyFile:
                return String(localized: "import_empty")
            case let .rejected(error):
                switch error {
                case .notJson, .notAnExport:
                    return String(localized: "import_error_not_export")
                case let .unsupportedVersion(version):
                    return String(format: NSLocalizedString("import_error_unsupported_version", comment: ""), version)
                case let .invalidGoal(index, field):
                    // N° de l'objectif à partir de 1 pour l'utilisateur.
                    return String(format: NSLocalizedString("import_error_invalid_goal", comment: ""), index + 1, field)
                case .duplicateId:
                    return String(localized: "import_error_duplicate_id")
                }
            case .unreadable:
                return String(localized: "import_error_unreadable")
            }
        }

        var isError: Bool {
            switch self {
            case .exported, .imported, .emptyFile: return false
            case .exportFailed, .rejected, .unreadable: return true
            }
        }

        private func plural(_ key: String, _ count: Int) -> String {
            String.localizedStringWithFormat(NSLocalizedString(key, comment: ""), count)
        }
    }

    // MARK: Export

    static func exportedGoals(_ records: [GoalRecord]) -> [ExportedGoal] {
        records.compactMap(exportedGoal)
    }

    /// Un enregistrement au format d'export ; nil s'il est illisible (non exporté, non partageable).
    static func exportedGoal(_ record: GoalRecord) -> ExportedGoal? {
        guard let goal = record.goal else { return nil }
        let style = record.style
        return ExportedGoal(
            id: record.exportID,
            title: record.title,
            goal: goal,
            color: style.color,
            emoji: style.emoji,
            notificationsEnabled: record.notificationsEnabled,
            createdAt: record.createdAt
        )
    }

    static func suggestedFileName() -> String {
        GoalExport.suggestedFileName(today: LocalDate.today())
    }

    // MARK: Import

    /// Lit le fichier choisi, le valide en entier puis ajoute les objectifs absents.
    /// Rien n'est écrit si le fichier est refusé : aucune donnée existante n'est modifiée.
    @MainActor
    static func importFile(at url: URL, into context: ModelContext) -> Outcome {
        let accessing = url.startAccessingSecurityScopedResource()
        defer { if accessing { url.stopAccessingSecurityScopedResource() } }

        if let size = try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize, size > maxImportBytes {
            return .unreadable
        }
        guard let data = try? Data(contentsOf: url), data.count <= maxImportBytes else {
            return .unreadable
        }
        return importData(data, into: context)
    }

    @MainActor
    static func importData(_ data: Data, into context: ModelContext) -> Outcome {
        let incoming: [ExportedGoal]
        switch GoalExport.decode(data) {
        case let .success(goals): incoming = goals
        case let .failure(error): return .rejected(error)
        }
        if incoming.isEmpty { return .emptyFile }

        guard let existing = try? context.fetch(FetchDescriptor<GoalRecord>()) else { return .unreadable }
        let plan = GoalImport.plan(existingIds: Set(existing.map(\.exportID)), incoming: incoming)

        for goal in plan.toAdd {
            guard let id = UUID(uuidString: goal.id) else { continue } // déjà validé par le décodage
            context.insert(GoalRecord(
                id: id,
                title: goal.title,
                kind: goal.goal.kind,
                startDate: goal.goal.startDate,
                targetDate: goal.goal.targetDate,
                createdAt: goal.createdAt,
                // Non précisé par le fichier → valeur par défaut de l'app (désactivées).
                notificationsEnabled: goal.notificationsEnabled ?? false,
                style: GoalStyle(color: goal.color, emoji: goal.emoji)
            ))
        }
        if !plan.toAdd.isEmpty {
            do {
                try context.save()
            } catch {
                context.rollback()
                return .unreadable
            }
        }
        return .imported(added: plan.toAdd.count, skipped: plan.skipped)
    }

    /// Types acceptés par le sélecteur de fichiers (un export renommé ou reçu en pièce jointe
    /// peut perdre son type JSON).
    static let importTypes: [UTType] = [.json, .plainText, .data]
}

/// Document exporté via `fileExporter` (app Fichiers).
struct GoalExportDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }

    let data: Data

    init(data: Data) {
        self.data = data
    }

    init(configuration: ReadConfiguration) throws {
        guard let contents = configuration.file.regularFileContents else {
            throw CocoaError(.fileReadCorruptFile)
        }
        data = contents
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}

/// Fichier partagé via `ShareLink` (feuille de partage : AirDrop, Mail, « Enregistrer dans Fichiers »…).
/// Le JSON est produit au moment du partage, dans un fichier temporaire au nom `goal-widgets-AAAA-MM-JJ.json`.
struct GoalExportFile: Transferable, Sendable {
    let goals: [ExportedGoal]

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(exportedContentType: .json) { file in
            let directory = FileManager.default.temporaryDirectory
                .appendingPathComponent(UUID().uuidString, isDirectory: true)
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            let url = directory.appendingPathComponent(GoalTransfer.suggestedFileName())
            try GoalExport.encode(file.goals, exportedAt: Date()).write(to: url, options: .atomic)
            return SentTransferredFile(url)
        }
    }
}

/// Partage d'un seul objectif (t13) : fichier `<titre>.goalwidgets.json` (`GoalShare`), type JSON
/// pour passer par toutes les messageries ; le destinataire l'ouvre avec Goal Widgets (`onOpenURL`).
struct SharedGoalFile: Transferable, Sendable {
    let goal: ExportedGoal

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(exportedContentType: .json) { file in
            let directory = FileManager.default.temporaryDirectory
                .appendingPathComponent(UUID().uuidString, isDirectory: true)
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            let url = directory.appendingPathComponent(GoalShare.fileName(title: file.goal.title))
            try GoalShare.encode(file.goal, exportedAt: Date()).write(to: url, options: .atomic)
            return SentTransferredFile(url)
        }
    }
}
