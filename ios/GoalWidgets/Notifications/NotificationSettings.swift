// Non vérifié : écrit sans Xcode (machine Linux).
import Foundation
import UserNotifications

/// Réglage global : heure d'envoi des notifications, en minutes depuis minuit (défaut 9 h 00).
/// Stocké dans `UserDefaults.standard` (l'extension widget n'en a pas besoin).
enum NotificationSettings {
    static let sendMinutesKey = "notification_send_minutes"
    static let defaultSendMinutes = 9 * 60

    static var sendMinutes: Int {
        let stored = UserDefaults.standard.object(forKey: sendMinutesKey) as? Int
        return min(max(stored ?? defaultSendMinutes, 0), 24 * 60 - 1)
    }
}

/// Autorisation de notifier, demandée seulement quand l'utilisateur active une notification (spec 44).
enum NotificationPermission {
    /// true si les notifications sont (ou viennent d'être) autorisées.
    static func requestIfNeeded() async -> Bool {
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        switch settings.authorizationStatus {
        case .notDetermined:
            return (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
        default:
            return NotificationScheduler.isAuthorized(settings.authorizationStatus)
        }
    }

    static func isDenied() async -> Bool {
        await UNUserNotificationCenter.current().notificationSettings().authorizationStatus == .denied
    }
}
