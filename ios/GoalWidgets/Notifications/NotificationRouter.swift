// Non vérifié : écrit sans Xcode (machine Linux).
import Combine
import Foundation
import UIKit
import UserNotifications
import GoalCore

/// Délégué du centre de notifications : affiche les notifications au premier plan et
/// transforme un appui en identifiant d'objectif (lien profond `GoalDeepLink`, comme le widget).
final class NotificationRouter: NSObject, UNUserNotificationCenterDelegate, ObservableObject {
    static let shared = NotificationRouter()

    /// Objectif à ouvrir après un appui sur une notification (consommé par `GoalWidgetsApp`).
    @Published var openedGoalID: UUID?

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo
        if let string = userInfo[NotificationScheduler.goalURLKey] as? String,
           let url = URL(string: string),
           let id = GoalDeepLink.goalID(from: url) {
            DispatchQueue.main.async {
                self.openedGoalID = id
            }
        }
        completionHandler()
    }
}

/// Le délégué doit être posé avant la fin du lancement pour recevoir l'appui qui a lancé l'app.
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = NotificationRouter.shared
        return true
    }
}
