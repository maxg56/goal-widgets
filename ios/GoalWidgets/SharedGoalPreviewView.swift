// Non vérifié : écrit sans Xcode (machine Linux).
import SwiftUI
import SwiftData
import WidgetKit
import GoalCore

/// Fichier reçu et ouvert avec Goal Widgets (t13) : `data` nil = fichier illisible (trop gros, accès refusé).
struct ReceivedShare: Identifiable {
    let id = UUID()
    let data: Data?

    /// Taille maximale lue (comme l'import, 5 Mo).
    static func read(_ url: URL) -> ReceivedShare {
        let accessing = url.startAccessingSecurityScopedResource()
        defer { if accessing { url.stopAccessingSecurityScopedResource() } }
        if let size = try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize, size > GoalTransfer.maxImportBytes {
            return ReceivedShare(data: nil)
        }
        guard let data = try? Data(contentsOf: url), data.count <= GoalTransfer.maxImportBytes else {
            return ReceivedShare(data: nil)
        }
        // LSSupportsOpeningDocumentsInPlace = NO : le système a copié le fichier dans Documents/Inbox,
        // copie propre à l'app, inutile une fois lue.
        if url.pathComponents.contains("Inbox") {
            try? FileManager.default.removeItem(at: url)
        }
        return ReceivedShare(data: data)
    }
}

/// Aperçu d'un fichier reçu (un objectif partagé, ou un export complet en liste) → « Ajouter » / « Annuler ».
/// Rien n'est écrit avant « Ajouter » ; l'ajout passe par l'import habituel (`GoalTransfer.importData`,
/// tout ou rien, objectifs déjà présents ignorés).
struct SharedGoalPreviewView: View {
    let share: ReceivedShare

    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    @Query private var records: [GoalRecord]
    @State private var errorMessage: String?
    private let today = LocalDate.today()

    private var result: Result<SharedGoalsPreview, ImportError>? {
        guard let data = share.data else { return nil }
        return GoalShare.preview(data, existingIds: Set(records.map(\.exportID)))
    }

    private var toAddCount: Int {
        if case let .success(preview) = result { return preview.toAddCount }
        return 0
    }

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("share_preview_title")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("action_cancel") { dismiss() }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("action_add") { add() }
                            .disabled(toAddCount == 0)
                    }
                }
                .alert(errorMessage ?? "", isPresented: isShowingError) {
                    Button("action_ok", role: .cancel) {}
                }
        }
    }

    @ViewBuilder
    private var content: some View {
        switch result {
        case .none:
            failure(GoalTransfer.Outcome.unreadable.message)
        case let .some(.failure(error)):
            failure(GoalTransfer.Outcome.rejected(error).message)
        case let .some(.success(preview)):
            if preview.items.isEmpty {
                failure(GoalTransfer.Outcome.emptyFile.message)
            } else {
                List {
                    Section {
                        ForEach(preview.items, id: \.goal.id) { item in
                            SharedGoalRow(item: item, today: today)
                        }
                    } footer: {
                        if preview.toAddCount == 0 {
                            Text("share_nothing_to_add")
                        }
                    }
                }
            }
        }
    }

    private func failure(_ message: String) -> some View {
        ContentUnavailableView {
            Label("share_error_title", systemImage: "exclamationmark.triangle")
        } description: {
            Text(message)
        }
    }

    private var isShowingError: Binding<Bool> {
        Binding(
            get: { errorMessage != nil },
            set: { if !$0 { errorMessage = nil } }
        )
    }

    private func add() {
        guard let data = share.data else { return }
        let outcome = GoalTransfer.importData(data, into: modelContext)
        guard case let .imported(added, _) = outcome else {
            errorMessage = outcome.message
            return
        }
        if added > 0 {
            // Comme l'import (t11) : appel explicite en plus de ModelContext.didSave.
            WidgetCenter.shared.reloadAllTimelines()
            let container = modelContext.container
            Task { @MainActor in
                await NotificationScheduler.reschedule(container: container)
            }
        }
        dismiss()
    }
}

/// Une ligne d'aperçu : titre (emoji), dates, état du jour, « Déjà dans tes objectifs ».
private struct SharedGoalRow: View {
    let item: SharedGoalsPreview.Item
    let today: LocalDate

    private var goal: ExportedGoal { item.goal }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(GoalStyle(color: goal.color, emoji: goal.emoji).decorate(goal.title))
                .font(.headline)
            switch goal.goal.kind {
            case .countdown:
                if let target = goal.goal.targetDate {
                    LabeledContent("goal_form_target_date", value: Self.format(target))
                }
                LabeledContent("goal_form_start_date", value: Self.format(goal.goal.startDate))
            case .since:
                LabeledContent("goal_form_since_date", value: Self.format(goal.goal.startDate))
            }
            Text(GoalLabels.status(for: CountdownEngine.compute(goal.goal, today: today)))
                .font(.subheadline)
                .foregroundStyle(GoalAppearance.statusStyle(goal.color))
            if item.alreadyPresent {
                Label("share_already_present", systemImage: "checkmark.circle")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 2)
    }

    private static func format(_ date: LocalDate) -> String {
        date.startOfDay(in: .current).formatted(date: .long, time: .omitted)
    }
}
