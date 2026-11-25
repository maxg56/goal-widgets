// Non vérifié : écrit sans Xcode (machine Linux).
import SwiftUI
import SwiftData

@main
struct GoalWidgetsApp: App {
    private let container: ModelContainer = {
        do {
            return try GoalStore.makeContainer()
        } catch {
            fatalError("Impossible d'ouvrir le stockage des objectifs : \(error)")
        }
    }()

    var body: some Scene {
        WindowGroup {
            GoalListView()
        }
        .modelContainer(container)
    }
}
