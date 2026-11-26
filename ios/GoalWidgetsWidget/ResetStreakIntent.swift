// Non vérifié : écrit sans Xcode (machine Linux).
import AppIntents
import Foundation
import SwiftData
import UserNotifications
import WidgetKit
import GoalCore

/// Bouton ↺ du widget « Objectif » (widget interactif, iOS 17) : le premier appui arme le bouton
/// (« Confirmer ? »), le second dans la fenêtre de `WidgetResetTap` remet le compteur « série » à zéro
/// aujourd'hui, sans ouvrir l'app. Exécuté par l'extension widget.
struct ResetStreakIntent: AppIntent {
    static var title: LocalizedStringResource = LocalizedStringResource("widget_reset_intent_title", table: "Widget")
    static var isDiscoverable = false

    @Parameter(title: LocalizedStringResource("widget_intent_goal", table: "Widget"))
    var goalID: String

    init() {}

    init(goalID: UUID) {
        self.goalID = goalID.uuidString
    }

    func perform() async throws -> some IntentResult {
        guard let id = UUID(uuidString: goalID) else { return .result() }
        let now = Date()
        switch WidgetResetTap.onTap(armedAt: ResetArming.armedAt(id), now: now) {
        case .arm:
            ResetArming.arm(id, at: now)
        case .reset:
            ResetArming.disarm(id)
            await Self.reset(id)
        }
        // Après un intent interactif, WidgetKit recharge la timeline ; rechargement explicite de tous
        // les widgets (plusieurs peuvent afficher le même objectif, la « Grille » aussi).
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }

    @MainActor
    private static func reset(_ id: UUID) async {
        guard let container = try? GoalStore.makeContainer() else { return }
        let context = container.mainContext
        let descriptor = FetchDescriptor<GoalRecord>(predicate: #Predicate { $0.id == id })
        guard let record = try? context.fetch(descriptor).first else { return }
        let today = LocalDate.today()
        guard record.resetStreak(on: today, today: today) else { return }
        try? context.save()
        // Les jalons de l'ancienne série (« Ça fait 3 mois… ») ne doivent plus partir. L'app
        // reprogramme ceux de la nouvelle série à sa prochaine ouverture (ou tâche de fond).
        let prefix = id.uuidString + ":"
        let center = UNUserNotificationCenter.current()
        let pending = await center.pendingNotificationRequests()
        let obsolete = pending.map(\.identifier).filter { $0.hasPrefix(prefix) }
        if !obsolete.isEmpty {
            center.removePendingNotificationRequests(withIdentifiers: obsolete)
        }
    }
}

/// Instant du premier appui sur ↺, par objectif, dans les préférences de l'App Group (lu par la
/// timeline pour afficher « Confirmer ? »).
enum ResetArming {
    private static var defaults: UserDefaults? { UserDefaults(suiteName: GoalStore.appGroupID) }
    private static func key(_ id: UUID) -> String { "reset_armed_at.\(id.uuidString)" }

    static func armedAt(_ id: UUID) -> Date? {
        guard let value = defaults?.object(forKey: key(id)) as? Double else { return nil }
        return Date(timeIntervalSince1970: value)
    }

    static func arm(_ id: UUID, at date: Date) {
        defaults?.set(date.timeIntervalSince1970, forKey: key(id))
    }

    static func disarm(_ id: UUID) {
        defaults?.removeObject(forKey: key(id))
    }
}
