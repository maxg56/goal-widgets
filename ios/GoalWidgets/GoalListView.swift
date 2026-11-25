// Non vérifié : écrit sans Xcode (machine Linux).
import Combine
import SwiftUI
import SwiftData
import GoalCore

/// Écran « Mes objectifs » : liste des objectifs avec leur état du jour.
/// Toucher une ligne : modifier. Balayer vers la gauche : supprimer (avec confirmation).
/// Balayer vers la droite sur un compte à rebours atteint : « Continuer en compteur depuis ».
struct GoalListView: View {
    @Query(sort: \GoalRecord.createdAt, order: .forward) private var records: [GoalRecord]
    @Environment(\.modelContext) private var modelContext
    @Environment(\.scenePhase) private var scenePhase
    @State private var today = LocalDate.today()
    @State private var isCreating = false
    @State private var editing: GoalRecord?
    @State private var pendingDeletion: GoalRecord?

    var body: some View {
        NavigationStack {
            Group {
                if records.isEmpty {
                    ContentUnavailableView("goal_list_empty", systemImage: "calendar")
                } else {
                    List {
                        ForEach(records) { record in
                            row(for: record)
                        }
                    }
                }
            }
            .navigationTitle("goal_list_title")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        isCreating = true
                    } label: {
                        Label("goal_add", systemImage: "plus")
                    }
                }
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
        // Recalcule « aujourd'hui » au retour au premier plan et au passage de minuit.
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { today = LocalDate.today() }
        }
        .onReceive(NotificationCenter.default.publisher(for: .NSCalendarDayChanged)) { _ in
            today = LocalDate.today()
        }
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
