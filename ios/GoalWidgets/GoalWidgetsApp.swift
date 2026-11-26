// Non vérifié : écrit sans Xcode (machine Linux).
import Combine
import SwiftUI
import SwiftData
import WidgetKit
import GoalCore

@main
struct GoalWidgetsApp: App {
    /// Pose le délégué des notifications au lancement (appui sur une notification → objectif).
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @Environment(\.scenePhase) private var scenePhase

    /// Statique : utilisé aussi par la tâche de fond de reprogrammation des notifications.
    private static let sharedContainer: ModelContainer = {
        do {
            return try GoalStore.makeContainer()
        } catch {
            fatalError("Impossible d'ouvrir le stockage des objectifs : \(error)")
        }
    }()

    private var container: ModelContainer { Self.sharedContainer }

    var body: some Scene {
        WindowGroup {
            // Accueil en cartes, détail, frise, formulaire ; liens profonds (widget, notification) et
            // fichiers reçus gérés par `RootView` (AppNavigation.swift).
            RootView()
                // Toute sauvegarde (création, modification, suppression) recharge les widgets
                // et reprogramme les notifications.
                .onReceive(NotificationCenter.default.publisher(for: ModelContext.didSave)) { _ in
                    WidgetCenter.shared.reloadAllTimelines()
                    rescheduleNotifications()
                }
                // Changement de fuseau : les jalons restent des dates locales, on reprogramme.
                .onReceive(NotificationCenter.default.publisher(for: .NSSystemTimeZoneDidChange)) { _ in
                    rescheduleNotifications()
                }
        }
        .modelContainer(container)
        .onChange(of: scenePhase) { _, phase in
            switch phase {
            case .active:
                // Lancement et retour au premier plan (ex. autorisation changée dans Réglages).
                rescheduleNotifications()
            case .background:
                // Filet de sécurité (ex. sauvegarde automatique SwiftData sans notification).
                WidgetCenter.shared.reloadAllTimelines()
                NotificationScheduler.scheduleBackgroundRefresh()
            default:
                break
            }
        }
        // Tâche de fond (BGAppRefreshTask) : fait glisser l'horizon même si l'app n'est pas ouverte.
        .backgroundTask(.appRefresh(NotificationScheduler.refreshTaskID)) {
            await NotificationScheduler.reschedule(container: GoalWidgetsApp.sharedContainer)
            NotificationScheduler.scheduleBackgroundRefresh()
        }
    }

    private func rescheduleNotifications() {
        Task { @MainActor in
            await NotificationScheduler.reschedule(container: Self.sharedContainer)
        }
    }
}
