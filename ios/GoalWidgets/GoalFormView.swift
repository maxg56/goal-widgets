// Non vérifié : écrit sans Xcode (machine Linux).
import SwiftUI
import SwiftData
import GoalCore

/// Formulaire de création / modification d'un objectif (compte à rebours ou « depuis »).
/// En modification : suppression avec confirmation, et « Continuer en compteur depuis »
/// si le compte à rebours est atteint (appliqué au formulaire, enregistré avec « Enregistrer »).
struct GoalFormView: View {
    enum Mode {
        case create
        case edit(GoalRecord)
    }

    let mode: Mode

    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss

    @State private var title: String
    @State private var kind: GoalKind
    @State private var startDate: Date
    @State private var targetDate: Date
    @State private var isConfirmingDelete = false
    /// Notifications de jalons de cet objectif (t09) ; l'autorisation iOS est demandée à l'activation.
    @State private var notificationsEnabled: Bool
    @State private var notificationsDenied = false
    /// Couleur et emoji (t11).
    @State private var color: GoalColor?
    @State private var emoji: String

    init(mode: Mode) {
        self.mode = mode
        let now = Date()
        let inThirtyDays = Calendar.current.date(byAdding: .day, value: 30, to: now) ?? now
        switch mode {
        case .create:
            _title = State(initialValue: "")
            _kind = State(initialValue: .countdown)
            _startDate = State(initialValue: now)
            _targetDate = State(initialValue: inThirtyDays)
            _notificationsEnabled = State(initialValue: false)
            _color = State(initialValue: nil)
            _emoji = State(initialValue: "")
        case .edit(let record):
            let goal = record.goal
            _title = State(initialValue: record.title)
            _kind = State(initialValue: goal?.kind ?? .countdown)
            _startDate = State(initialValue: goal.map { Self.date(from: $0.startDate) } ?? now)
            _targetDate = State(initialValue: goal?.targetDate.map { Self.date(from: $0) } ?? inThirtyDays)
            _notificationsEnabled = State(initialValue: record.notificationsEnabled)
            _color = State(initialValue: record.style.color)
            _emoji = State(initialValue: record.style.emoji ?? "")
        }
    }

    private var today: LocalDate { LocalDate.today() }
    private var start: LocalDate { LocalDate.today(now: startDate) }
    private var target: LocalDate { LocalDate.today(now: targetDate) }

    private var isEditing: Bool {
        if case .edit = mode { return true }
        return false
    }

    /// Message d'erreur de dates à afficher (le titre vide n'est pas signalé : le bouton est désactivé).
    private var dateErrorMessage: String? {
        switch kind {
        case .countdown:
            if CountdownValidation.check(title: title, startDate: start, targetDate: target) == .targetNotAfterStart {
                return GoalLabels.validationMessage(CountdownValidationError.targetNotAfterStart)
            }
        case .since:
            if SinceValidation.check(title: title, startDate: start, today: today) == .startInFuture {
                return GoalLabels.sinceValidationMessage(SinceValidationError.startInFuture)
            }
        }
        return nil
    }

    private var isValid: Bool {
        switch kind {
        case .countdown:
            return CountdownValidation.check(title: title, startDate: start, targetDate: target) == nil
        case .since:
            return SinceValidation.check(title: title, startDate: start, today: today) == nil
        }
    }

    /// Compte à rebours dont la cible saisie est dépassée : la conversion est proposée.
    private var canContinueAsSince: Bool {
        isEditing && kind == .countdown && target < today
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("goal_form_kind", selection: $kind) {
                        Text("goal_kind_countdown").tag(GoalKind.countdown)
                        Text("goal_kind_since").tag(GoalKind.since)
                    }
                    .pickerStyle(.segmented)
                }

                Section {
                    TextField("goal_form_title", text: $title)
                    switch kind {
                    case .countdown:
                        DatePicker("goal_form_target_date", selection: $targetDate, displayedComponents: .date)
                        DatePicker("goal_form_start_date", selection: $startDate, displayedComponents: .date)
                    case .since:
                        DatePicker("goal_form_since_date", selection: $startDate, in: ...Date(), displayedComponents: .date)
                    }
                } footer: {
                    if let message = dateErrorMessage {
                        Text(message)
                            .foregroundStyle(.red)
                    }
                }

                Section("goal_form_color") {
                    ColorSwatchPicker(selection: $color)
                }

                Section("goal_form_emoji") {
                    EmojiPicker(selection: $emoji)
                }

                Section {
                    Toggle("goal_form_notifications", isOn: $notificationsEnabled)
                } footer: {
                    Text("goal_form_notifications_footer")
                }

                if notificationsEnabled && notificationsDenied {
                    NotificationsDeniedSection()
                }

                if canContinueAsSince {
                    Section {
                        Button("goal_action_continue_as_since") {
                            continueAsSince()
                        }
                    }
                }

                if isEditing {
                    Section {
                        Button("action_delete", role: .destructive) {
                            isConfirmingDelete = true
                        }
                    }
                }
            }
            .navigationTitle(isEditing ? LocalizedStringKey("goal_form_edit") : LocalizedStringKey("goal_form_new"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("action_cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("action_save") { save() }
                        .disabled(!isValid)
                }
            }
            .onChange(of: notificationsEnabled) { _, enabled in
                // Spec 44 : l'autorisation est demandée au moment où l'utilisateur active une notification.
                guard enabled else { return }
                Task {
                    let granted = await NotificationPermission.requestIfNeeded()
                    notificationsDenied = !granted
                }
            }
            .task {
                if notificationsEnabled {
                    notificationsDenied = await NotificationPermission.isDenied()
                }
            }
            .confirmationDialog("goal_delete_confirm_title", isPresented: $isConfirmingDelete, titleVisibility: .visible) {
                Button("action_delete", role: .destructive) { delete() }
                Button("action_cancel", role: .cancel) {}
            } message: {
                Text("goal_delete_confirm_message")
            }
        }
    }

    /// Même règle que `Goal.continuedAsSince()` : le compteur part de la date cible.
    private func continueAsSince() {
        let countdown = Goal(kind: .countdown, startDate: start, targetDate: target)
        guard let converted = countdown.continuedAsSince() else { return }
        startDate = Self.date(from: converted.startDate)
        kind = converted.kind
    }

    private var style: GoalStyle { GoalStyle(color: color, emoji: emoji) }

    private func save() {
        guard isValid else { return }
        let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let goal: Goal
        switch kind {
        case .countdown:
            goal = Goal(kind: .countdown, startDate: start, targetDate: target)
        case .since:
            goal = Goal(kind: .since, startDate: start, targetDate: nil)
        }
        switch mode {
        case .create:
            modelContext.insert(GoalRecord(
                title: cleanTitle,
                kind: goal.kind,
                startDate: goal.startDate,
                targetDate: goal.targetDate,
                notificationsEnabled: notificationsEnabled,
                style: style
            ))
        case .edit(let record):
            record.update(title: cleanTitle, goal: goal)
            record.notificationsEnabled = notificationsEnabled
            record.style = style
        }
        // La sauvegarde déclenche la reprogrammation des notifications (GoalWidgetsApp, ModelContext.didSave).
        try? modelContext.save()
        dismiss()
    }

    private func delete() {
        guard case .edit(let record) = mode else { return }
        modelContext.delete(record)
        try? modelContext.save()
        dismiss()
    }

    /// LocalDate → Date (minuit local) pour le DatePicker ; l'inverse est `LocalDate.today(now:)`.
    private static func date(from localDate: LocalDate) -> Date {
        let components = DateComponents(year: localDate.year, month: localDate.month, day: localDate.day)
        return Calendar.current.date(from: components) ?? Date()
    }
}
