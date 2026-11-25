// Non vérifié : écrit sans Xcode (machine Linux).
import Combine
import SwiftUI
import SwiftData
import GoalCore

/// Écran « Mes objectifs » : liste des objectifs avec leur état du jour.
struct GoalListView: View {
    @Query(sort: \GoalRecord.createdAt, order: .forward) private var records: [GoalRecord]
    @Environment(\.scenePhase) private var scenePhase
    @State private var today = LocalDate.today()
    @State private var isCreating = false

    var body: some View {
        NavigationStack {
            Group {
                if records.isEmpty {
                    ContentUnavailableView("goal_list_empty", systemImage: "calendar")
                } else {
                    List(records) { record in
                        GoalRowView(record: record, today: today)
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
                CreateCountdownView()
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
}

#Preview {
    GoalListView()
        .modelContainer(try! GoalStore.makeContainer(inMemory: true))
}
