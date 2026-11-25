// Non vérifié : écrit sans Xcode (machine Linux).
import Foundation
import BackgroundTasks
import SwiftData
import UserNotifications
import GoalCore

/// Couche OS mince : reprogramme toutes les notifications de jalons à partir de `MilestonePlanner`.
///
/// Horizon glissant de 365 jours pour tous les objectifs, puis seulement les 64 plus proches
/// (limite iOS des notifications en attente). Identifiants stables (`Milestone.id`) : une
/// reprogrammation remplace les demandes existantes au lieu de les dupliquer, et retire celles
/// qui ne sont plus voulues (objectif supprimé ou désactivé, heure changée…).
/// Appelée au lancement / retour au premier plan, après chaque sauvegarde, au changement de fuseau,
/// au changement d'heure d'envoi et par la tâche de fond `refreshTaskID`.
@MainActor
enum NotificationScheduler {
    nonisolated static let maxPending = 64
    nonisolated static let horizonDays = 365
    /// Déclaré dans `project.yml` (`BGTaskSchedulerPermittedIdentifiers`).
    nonisolated static let refreshTaskID = "app.goalwidgets.reschedule-notifications"
    /// Clé `userInfo` portant le lien profond `goalwidgets://goal/<id>`.
    nonisolated static let goalURLKey = "goalURL"

    private static var isRunning = false
    private static var needsRerun = false

    static func reschedule(container: ModelContainer, now: Date = Date()) async {
        // Plusieurs déclencheurs peuvent arriver ensemble : une seule passe à la fois, plus une
        // passe finale si quelque chose a changé pendant la première.
        if isRunning {
            needsRerun = true
            return
        }
        isRunning = true
        defer { isRunning = false }
        repeat {
            needsRerun = false
            await run(container: container, now: now)
        } while needsRerun
    }

    private static func run(container: ModelContainer, now: Date) async {
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        let calendar = Calendar.current
        let today = LocalDate.today(calendar: calendar, now: now)
        let sendMinutes = NotificationSettings.sendMinutes

        var titles: [String: String] = [:]
        var goalUUIDs: [String: UUID] = [:]
        var candidates: [Milestone] = []
        if isAuthorized(settings.authorizationStatus) {
            let records = (try? container.mainContext.fetch(FetchDescriptor<GoalRecord>())) ?? []
            for record in records where record.notificationsEnabled {
                guard let goal = record.goal else { continue }
                let goalID = record.id.uuidString
                titles[goalID] = record.title
                goalUUIDs[goalID] = record.id
                candidates += MilestonePlanner.plan(
                    goalID: goalID,
                    goal: goal,
                    notificationsEnabled: true,
                    today: today,
                    horizonDays: horizonDays
                )
            }
        }

        // Le jalon du jour n'est programmé que si l'heure d'envoi n'est pas encore passée.
        let upcoming = candidates.filter { fireDate(for: $0.date, sendMinutes: sendMinutes, calendar: calendar) > now }
        let selected = MilestonePlanner.closest(upcoming, limit: maxPending)
        let wanted = Set(selected.map(\.id))

        let pending = await center.pendingNotificationRequests()
        let obsolete = pending.map(\.identifier).filter { !wanted.contains($0) }
        if !obsolete.isEmpty {
            center.removePendingNotificationRequests(withIdentifiers: obsolete)
        }

        for milestone in selected {
            guard let title = titles[milestone.goalID], let uuid = goalUUIDs[milestone.goalID] else { continue }
            let content = UNMutableNotificationContent()
            content.title = title
            content.body = NotificationLabels.body(for: milestone)
            content.sound = .default
            content.threadIdentifier = milestone.goalID
            content.userInfo = [goalURLKey: GoalDeepLink.url(forGoal: uuid).absoluteString]

            // Composantes sans fuseau : heure « flottante », qui suit le fuseau courant de l'appareil.
            let components = DateComponents(
                year: milestone.date.year,
                month: milestone.date.month,
                day: milestone.date.day,
                hour: sendMinutes / 60,
                minute: sendMinutes % 60
            )
            let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
            let request = UNNotificationRequest(identifier: milestone.id, content: content, trigger: trigger)
            // Même identifiant qu'une demande en attente : elle est remplacée, pas dupliquée.
            try? await center.add(request)
        }
    }

    /// Demande à iOS de relancer la reprogrammation en arrière-plan (au plus tôt dans ~12 h).
    /// iOS décide du moment réel ; l'horizon de 64 notifications couvre les périodes sans exécution.
    nonisolated static func scheduleBackgroundRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: refreshTaskID)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 12 * 60 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }

    nonisolated static func isAuthorized(_ status: UNAuthorizationStatus) -> Bool {
        switch status {
        case .authorized, .provisional, .ephemeral:
            return true
        case .denied, .notDetermined:
            return false
        @unknown default:
            return false
        }
    }

    private static func fireDate(for date: LocalDate, sendMinutes: Int, calendar: Calendar) -> Date {
        let components = DateComponents(
            year: date.year, month: date.month, day: date.day,
            hour: sendMinutes / 60, minute: sendMinutes % 60
        )
        return calendar.date(from: components) ?? date.startOfDay(in: calendar)
    }
}
