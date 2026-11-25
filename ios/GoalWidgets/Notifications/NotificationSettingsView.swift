// Non vérifié : écrit sans Xcode (machine Linux).
import SwiftUI
import SwiftData
import UIKit

/// Réglages des notifications : heure d'envoi globale (spec 39) et rappel si iOS les bloque.
struct NotificationSettingsView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    @AppStorage(NotificationSettings.sendMinutesKey) private var sendMinutes = NotificationSettings.defaultSendMinutes
    @State private var isDenied = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    DatePicker("notifications_send_time", selection: sendTime, displayedComponents: .hourAndMinute)
                } footer: {
                    Text("notifications_send_time_footer")
                }
                if isDenied {
                    NotificationsDeniedSection()
                }
                #if DEBUG
                // Builds de debug uniquement (t14) : absent du code compilé en release.
                NotificationDebugSection()
                #endif
            }
            .navigationTitle("notifications_settings_title")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("action_ok") { dismiss() }
                }
            }
            .task {
                isDenied = await NotificationPermission.isDenied()
            }
            .onChange(of: sendMinutes) { _, _ in
                let container = modelContext.container
                Task { await NotificationScheduler.reschedule(container: container) }
            }
        }
    }

    /// Minutes depuis minuit ↔ `Date` du jour pour le DatePicker.
    private var sendTime: Binding<Date> {
        Binding(
            get: {
                Calendar.current.date(
                    bySettingHour: sendMinutes / 60, minute: sendMinutes % 60, second: 0, of: Date()
                ) ?? Date()
            },
            set: { date in
                let c = Calendar.current.dateComponents([.hour, .minute], from: date)
                sendMinutes = (c.hour ?? 9) * 60 + (c.minute ?? 0)
            }
        )
    }
}

/// Notifications refusées dans les Réglages iOS : explication + lien vers les réglages de l'app.
struct NotificationsDeniedSection: View {
    @Environment(\.openURL) private var openURL

    var body: some View {
        Section {
            Button("notifications_open_settings") {
                if let url = URL(string: UIApplication.openNotificationSettingsURLString) {
                    openURL(url)
                }
            }
        } footer: {
            Text("notifications_denied_hint")
        }
    }
}
