// Non vérifié : écrit sans Xcode (machine Linux).
// Mode debug des notifications (t14) : compilé uniquement en configuration Debug (`#if DEBUG`),
// absent du binaire de release. Textes en dur (outil de développement, non localisé).
#if DEBUG
import SwiftUI
import SwiftData
import UserNotifications
import GoalCore

/// Section « Debug » des réglages de notifications : état de l'autorisation, demandes en attente
/// avec leur heure d'envoi réelle, « Envoyer maintenant », et un vrai test dans 10 secondes.
@MainActor
struct NotificationDebugSection: View {
    @Query(sort: \GoalRecord.createdAt) private var records: [GoalRecord]
    @State private var authorization: UNAuthorizationStatus = .notDetermined
    @State private var pending: [PendingItem] = []
    @State private var testGoalID: UUID?
    @State private var lastAction: String?

    struct PendingItem: Identifiable {
        let id: String
        let fireDate: Date?
        let title: String
        let body: String
        let request: UNNotificationRequest
    }

    var body: some View {
        Section {
            LabeledContent("Autorisation", value: Self.describe(authorization))

            Picker("Objectif de test", selection: $testGoalID) {
                Text(verbatim: "—").tag(UUID?.none)
                ForEach(records) { record in
                    Text(verbatim: record.displayTitle).tag(UUID?.some(record.id))
                }
            }
            Button("Programmer un jalon de test dans 10 secondes") {
                Task { await scheduleTest() }
            }
            .disabled(testGoalID == nil)

            Button("Rafraîchir la liste") {
                Task { await refresh() }
            }
            if let lastAction {
                Text(verbatim: lastAction)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        } header: {
            Text(verbatim: "Debug")
        } footer: {
            Text(verbatim: "Visible seulement dans les builds de debug.")
        }

        Section {
            if pending.isEmpty {
                Text(verbatim: "Aucune notification programmée")
                    .foregroundStyle(.secondary)
            }
            ForEach(pending) { item in
                VStack(alignment: .leading, spacing: 4) {
                    Text(verbatim: item.fireDate.map { $0.formatted(date: .abbreviated, time: .shortened) } ?? "Date inconnue")
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(.secondary)
                    Text(verbatim: item.title).font(.headline)
                    Text(verbatim: item.body).font(.subheadline)
                    Text(verbatim: item.id)
                        .font(.caption2.monospaced())
                        .foregroundStyle(.tertiary)
                        .lineLimit(1)
                    Button("Envoyer maintenant") {
                        Task { await sendNow(item) }
                    }
                    .buttonStyle(.borderless)
                }
            }
        } header: {
            Text(verbatim: "Debug — en attente (\(pending.count))")
        }
        .task { await refresh() }
    }

    // MARK: Actions

    private func refresh() async {
        let center = UNUserNotificationCenter.current()
        authorization = await center.notificationSettings().authorizationStatus
        let requests = await center.pendingNotificationRequests()
        pending = requests
            .map { request in
                PendingItem(
                    id: request.identifier,
                    fireDate: Self.nextFireDate(request.trigger),
                    title: request.content.title,
                    body: request.content.body,
                    request: request
                )
            }
            .sorted { ($0.fireDate ?? .distantFuture) < ($1.fireDate ?? .distantFuture) }
    }

    /// Même contenu que la demande programmée (texte, `userInfo` → appui = ouverture de l'objectif),
    /// livré tout de suite (`trigger` nil) sous un autre identifiant : la demande d'origine reste en attente.
    /// Aucune vérification « jour même » sur iOS : le contenu est figé à la programmation.
    private func sendNow(_ item: PendingItem) async {
        let request = UNNotificationRequest(
            identifier: NotificationScheduler.debugIDPrefix + "now-" + UUID().uuidString,
            content: item.request.content,
            trigger: nil
        )
        do {
            try await UNUserNotificationCenter.current().add(request)
            lastAction = "Envoyée : \(item.id)"
        } catch {
            lastAction = "Échec : \(error.localizedDescription)"
        }
    }

    /// Vraie demande `UNTimeIntervalNotificationTrigger` (10 s) avec le contenu du prochain jalon de
    /// l'objectif choisi (même chemin `NotificationScheduler.content`), pour vérifier la livraison
    /// de bout en bout (app en arrière-plan ou verrouillée).
    private func scheduleTest() async {
        guard let id = testGoalID, let record = records.first(where: { $0.id == id }), let goal = record.goal else { return }
        guard await NotificationPermission.requestIfNeeded() else {
            lastAction = "Notifications refusées"
            await refresh()
            return
        }
        let today = LocalDate.today()
        let goalID = record.id.uuidString
        let planned = MilestonePlanner.plan(
            goalID: goalID,
            goal: goal,
            notificationsEnabled: true,
            today: today,
            horizonDays: NotificationScheduler.horizonDays
        )
        let milestone = planned.first ?? Milestone(
            goalID: goalID,
            date: today,
            kind: goal.kind == .countdown ? .targetDay : .monthAnniversary,
            value: goal.kind == .countdown ? 0 : 1
        )
        let content = NotificationScheduler.content(for: milestone, title: record.title, goalUUID: record.id)
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 10, repeats: false)
        let request = UNNotificationRequest(
            identifier: NotificationScheduler.debugIDPrefix + "test-" + UUID().uuidString,
            content: content,
            trigger: trigger
        )
        do {
            try await UNUserNotificationCenter.current().add(request)
            lastAction = "Test programmé dans 10 s (\(milestone.id))"
        } catch {
            lastAction = "Échec : \(error.localizedDescription)"
        }
        await refresh()
    }

    // MARK: Utilitaires

    private static func nextFireDate(_ trigger: UNNotificationTrigger?) -> Date? {
        if let calendar = trigger as? UNCalendarNotificationTrigger {
            return calendar.nextTriggerDate()
        }
        if let interval = trigger as? UNTimeIntervalNotificationTrigger {
            return interval.nextTriggerDate()
        }
        return nil
    }

    private static func describe(_ status: UNAuthorizationStatus) -> String {
        switch status {
        case .notDetermined: return "Non demandée"
        case .denied: return "Refusée"
        case .authorized: return "Autorisée"
        case .provisional: return "Provisoire"
        case .ephemeral: return "Éphémère"
        @unknown default: return "Inconnue (\(status.rawValue))"
        }
    }
}
#endif
