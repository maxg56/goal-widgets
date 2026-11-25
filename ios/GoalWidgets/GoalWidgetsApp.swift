// Non vérifié : écrit sans Xcode (machine Linux).
import SwiftUI
import SwiftData
import WidgetKit
import GoalCore

@main
struct GoalWidgetsApp: App {
    @Environment(\.scenePhase) private var scenePhase
    /// Objectif ouvert par un lien profond `goalwidgets://goal/<id>` (appui sur un widget).
    @State private var deepLinkedGoal: DeepLinkedGoal?

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
                .onOpenURL { url in
                    if let id = GoalDeepLink.goalID(from: url) {
                        deepLinkedGoal = DeepLinkedGoal(id: id)
                    }
                }
                .sheet(item: $deepLinkedGoal) { link in
                    GoalDeepLinkView(goalID: link.id)
                }
                // Toute sauvegarde (création, modification, suppression) recharge les widgets.
                .onReceive(NotificationCenter.default.publisher(for: ModelContext.didSave)) { _ in
                    WidgetCenter.shared.reloadAllTimelines()
                }
        }
        .modelContainer(container)
        .onChange(of: scenePhase) { _, phase in
            // Filet de sécurité (ex. sauvegarde automatique SwiftData sans notification).
            if phase == .background {
                WidgetCenter.shared.reloadAllTimelines()
            }
        }
    }
}

struct DeepLinkedGoal: Identifiable {
    let id: UUID
}
