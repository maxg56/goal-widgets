// Non vérifié : écrit sans Xcode (machine Linux).
import SwiftUI
import SwiftData
import GoalCore

/// Formulaire de création d'un compte à rebours : titre, date cible, date de départ (défaut : aujourd'hui).
struct CreateCountdownView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss

    @State private var title = ""
    @State private var targetDate = Calendar.current.date(byAdding: .day, value: 30, to: Date()) ?? Date()
    @State private var startDate = Date()

    private var start: LocalDate { LocalDate.today(now: startDate) }
    private var target: LocalDate { LocalDate.today(now: targetDate) }
    private var validationError: CountdownValidationError? {
        CountdownValidation.check(title: title, startDate: start, targetDate: target)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("goal_form_title", text: $title)
                    DatePicker("goal_form_target_date", selection: $targetDate, displayedComponents: .date)
                    DatePicker("goal_form_start_date", selection: $startDate, displayedComponents: .date)
                } footer: {
                    // Le titre vide n'est pas signalé (bouton désactivé suffit) ; l'erreur de dates oui.
                    if validationError == .targetNotAfterStart {
                        Text(GoalLabels.validationMessage(.targetNotAfterStart))
                            .foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle("goal_form_new_countdown")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("action_cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("action_save") { save() }
                        .disabled(validationError != nil)
                }
            }
        }
    }

    private func save() {
        guard validationError == nil else { return }
        let record = GoalRecord(
            title: title.trimmingCharacters(in: .whitespacesAndNewlines),
            kind: .countdown,
            startDate: start,
            targetDate: target
        )
        modelContext.insert(record)
        try? modelContext.save()
        dismiss()
    }
}
