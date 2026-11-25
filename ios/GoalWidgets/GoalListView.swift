// Non vérifié : écrit sans Xcode (machine Linux).
import SwiftUI

/// Écran « Mes objectifs ». Pour l'instant : uniquement l'état vide.
struct GoalListView: View {
    var body: some View {
        NavigationStack {
            ContentUnavailableView("goal_list_empty", systemImage: "calendar")
                .navigationTitle("goal_list_title")
        }
    }
}

#Preview {
    GoalListView()
}
