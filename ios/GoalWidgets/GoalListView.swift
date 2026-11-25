// Non vérifié : écrit sans Xcode (machine Linux).
import Combine
import SwiftUI
import SwiftData
import UniformTypeIdentifiers
import WidgetKit
import GoalCore

/// Écran « Mes objectifs » : liste des objectifs avec leur état du jour.
/// Toucher une ligne : modifier. Balayer vers la gauche : supprimer (avec confirmation).
/// Balayer vers la droite sur un compte à rebours atteint : « Continuer en compteur depuis ».
/// Menu ⋯ (t11) : tri mémorisé, export (Fichiers ou feuille de partage), import.
struct GoalListView: View {
    @Query(sort: \GoalRecord.createdAt, order: .forward) private var records: [GoalRecord]
    @Environment(\.modelContext) private var modelContext
    @Environment(\.scenePhase) private var scenePhase
    @State private var today = LocalDate.today()
    @State private var isCreating = false
    @State private var isShowingNotificationSettings = false
    @State private var editing: GoalRecord?
    @State private var pendingDeletion: GoalRecord?
    /// Ordre de tri choisi (t11), mémorisé dans UserDefaults (`goal_order`, comme Android).
    @AppStorage("goal_order") private var orderRaw = GoalOrder.defaultOrder.rawValue
    @State private var isExporting = false
    @State private var exportDocument: GoalExportDocument?
    @State private var exportCount = 0
    @State private var isImporting = false
    @State private var transferMessage: String?

    var body: some View {
        NavigationStack {
            Group {
                if records.isEmpty {
                    ContentUnavailableView("goal_list_empty", systemImage: "calendar")
                } else {
                    List {
                        ForEach(sortedRecords) { record in
                            row(for: record)
                        }
                    }
                }
            }
            .navigationTitle("goal_list_title")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        isShowingNotificationSettings = true
                    } label: {
                        Label("notifications_settings_title", systemImage: "bell")
                    }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        isCreating = true
                    } label: {
                        Label("goal_add", systemImage: "plus")
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    moreMenu
                }
            }
            // Import (app Fichiers) : attaché à un autre niveau que `fileExporter`
            // (deux sélecteurs sur la même vue peuvent s'empêcher l'un l'autre).
            .fileImporter(isPresented: $isImporting, allowedContentTypes: GoalTransfer.importTypes) { result in
                handleImport(result)
            }
            .alert(transferMessage ?? "", isPresented: isShowingTransferMessage) {
                Button("action_ok", role: .cancel) {}
            }
            .sheet(isPresented: $isShowingNotificationSettings) {
                NotificationSettingsView()
            }
            .sheet(isPresented: $isCreating) {
                GoalFormView(mode: .create)
            }
            .sheet(item: $editing) { record in
                GoalFormView(mode: .edit(record))
            }
            .confirmationDialog(
                "goal_delete_confirm_title",
                isPresented: isConfirmingDeletion,
                titleVisibility: .visible,
                presenting: pendingDeletion
            ) { record in
                Button("action_delete", role: .destructive) { delete(record) }
                Button("action_cancel", role: .cancel) {}
            } message: { _ in
                Text("goal_delete_confirm_message")
            }
        }
        // Export vers l'app Fichiers.
        .fileExporter(
            isPresented: $isExporting,
            document: exportDocument,
            contentType: .json,
            defaultFilename: GoalTransfer.suggestedFileName()
        ) { result in
            handleExport(result)
        }
        // Recalcule « aujourd'hui » au retour au premier plan et au passage de minuit.
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { today = LocalDate.today() }
        }
        .onReceive(NotificationCenter.default.publisher(for: .NSCalendarDayChanged)) { _ in
            today = LocalDate.today()
        }
    }

    private var order: GoalOrder { GoalOrder(storedValue: orderRaw) }

    /// Liste triée selon l'ordre choisi (`GoalOrder`, cas partagés `sort.json`).
    private var sortedRecords: [GoalRecord] {
        // Un enregistrement illisible (goal nil) est rangé en dernier.
        let unreadable = Goal(kind: .since, startDate: LocalDate(year: 1, month: 1, day: 1))
        return order.sort(records, today: today) { record in
            GoalSortKey(id: record.exportID, goal: record.goal ?? unreadable, createdAt: record.createdAt)
        }
    }

    private var moreMenu: some View {
        Menu {
            Picker(selection: $orderRaw) {
                Text("goal_sort_closest_date").tag(GoalOrder.closestDate.rawValue)
                Text("goal_sort_created").tag(GoalOrder.created.rawValue)
            } label: {
                Label("goal_sort_title", systemImage: "arrow.up.arrow.down")
            }
            .pickerStyle(.menu)

            Section {
                Button {
                    startExport()
                } label: {
                    Label("action_export", systemImage: "folder")
                }
                ShareLink(
                    item: GoalExportFile(goals: GoalTransfer.exportedGoals(records)),
                    preview: SharePreview(GoalTransfer.suggestedFileName())
                ) {
                    Label("action_share_export", systemImage: "square.and.arrow.up")
                }
                Button {
                    isImporting = true
                } label: {
                    Label("action_import", systemImage: "square.and.arrow.down")
                }
            }
        } label: {
            Label("action_more", systemImage: "ellipsis.circle")
        }
    }

    private var isShowingTransferMessage: Binding<Bool> {
        Binding(
            get: { transferMessage != nil },
            set: { if !$0 { transferMessage = nil } }
        )
    }

    private func startExport() {
        let goals = GoalTransfer.exportedGoals(records)
        exportCount = goals.count
        exportDocument = GoalExportDocument(data: GoalExport.encode(goals, exportedAt: Date()))
        isExporting = true
    }

    private func handleExport(_ result: Result<URL, Error>) {
        switch result {
        case .success:
            transferMessage = GoalTransfer.Outcome.exported(count: exportCount).message
        case let .failure(error):
            if (error as? CocoaError)?.code == .userCancelled { return }
            transferMessage = GoalTransfer.Outcome.exportFailed.message
        }
        exportDocument = nil
    }

    private func handleImport(_ result: Result<URL, Error>) {
        let outcome: GoalTransfer.Outcome
        switch result {
        case let .success(url):
            outcome = GoalTransfer.importFile(at: url, into: modelContext)
        case let .failure(error):
            if (error as? CocoaError)?.code == .userCancelled { return }
            outcome = .unreadable
        }
        if case let .imported(added, _) = outcome, added > 0 {
            // La sauvegarde déclenche déjà ces deux actions (GoalWidgetsApp, ModelContext.didSave) ;
            // appel explicite pour ne pas dépendre de cette notification.
            WidgetCenter.shared.reloadAllTimelines()
            let container = modelContext.container
            Task { @MainActor in
                await NotificationScheduler.reschedule(container: container)
            }
        }
        transferMessage = outcome.message
    }

    private func row(for record: GoalRecord) -> some View {
        Button {
            editing = record
        } label: {
            GoalRowView(record: record, today: today)
                .foregroundStyle(.primary)
        }
        // Pas de `role: .destructive` : il retirerait la ligne avant la confirmation.
        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
            Button {
                pendingDeletion = record
            } label: {
                Label("action_delete", systemImage: "trash")
            }
            .tint(.red)
        }
        .swipeActions(edge: .leading) {
            if isReached(record) {
                Button {
                    continueAsSince(record)
                } label: {
                    Label("goal_action_continue_as_since", systemImage: "arrow.forward.circle")
                }
                .tint(.blue)
            }
        }
        .contextMenu {
            if isReached(record) {
                Button {
                    continueAsSince(record)
                } label: {
                    Label("goal_action_continue_as_since", systemImage: "arrow.forward.circle")
                }
            }
            Button(role: .destructive) {
                pendingDeletion = record
            } label: {
                Label("action_delete", systemImage: "trash")
            }
        }
    }

    private var isConfirmingDeletion: Binding<Bool> {
        Binding(
            get: { pendingDeletion != nil },
            set: { if !$0 { pendingDeletion = nil } }
        )
    }

    private func isReached(_ record: GoalRecord) -> Bool {
        guard let goal = record.goal else { return false }
        return CountdownEngine.compute(goal, today: today).phase == .reached
    }

    private func continueAsSince(_ record: GoalRecord) {
        if record.continueAsSince() {
            try? modelContext.save()
        }
    }

    private func delete(_ record: GoalRecord) {
        modelContext.delete(record)
        try? modelContext.save()
        pendingDeletion = nil
    }
}

#Preview {
    GoalListView()
        .modelContainer(try! GoalStore.makeContainer(inMemory: true))
}
