// Non vérifié : écrit sans Xcode (machine Linux).
import SwiftUI
import SwiftData
import GoalCore

/// Confirmation de la remise à zéro d'un compteur « série » (détail et carte de l'accueil). Le compteur
/// repart aujourd'hui par défaut ; une date passée peut être choisie (entre le départ de la série en
/// cours et aujourd'hui, cf. `Streaks.validate`). Même comportement qu'Android (`ResetStreakDialog`).
struct ResetStreakSheet: View {
    let record: GoalRecord
    let today: LocalDate

    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var modelContext
    @State private var date: Date

    init(record: GoalRecord, today: LocalDate) {
        self.record = record
        self.today = today
        _date = State(initialValue: today.noon())
    }

    private var on: LocalDate { LocalDate.today(now: date) }

    /// Dates proposées : du départ de la série en cours à aujourd'hui.
    private var range: ClosedRange<Date> {
        let start = record.goal?.startDate ?? today
        return min(start, today).noon()...today.noon()
    }

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                let days = record.streakSummary(today: on)?.currentDays ?? 0
                Text(GoalLabels.format("streak_reset_confirm_message", GoalLabels.plural("streak_days", days), record.title))
                    .font(.body)
                DatePicker("streak_reset_date", selection: $date, in: range, displayedComponents: .date)
                    .font(.body.weight(.semibold))
                Spacer(minLength: 0)
                Button(role: .destructive) {
                    if record.resetStreak(on: on, today: today) {
                        // La sauvegarde recharge les widgets et reprogramme les notifications
                        // (GoalWidgetsApp, ModelContext.didSave).
                        try? modelContext.save()
                    }
                    dismiss()
                } label: {
                    Label("streak_reset_action", systemImage: "arrow.counterclockwise")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 6)
                }
                .buttonStyle(.borderedProminent)
            }
            .padding(20)
            .navigationTitle(Text("streak_reset_confirm_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("action_cancel") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium])
    }
}

/// Bloc « série » du détail, sur fond coloré : record (mis en avant quand la série en cours l'égale ou
/// le bat), nombre de remises à zéro et bouton « Remettre à zéro ».
struct StreakBar: View {
    let record: GoalRecord
    let today: LocalDate
    let onReset: () -> Void

    var body: some View {
        VStack(spacing: 2) {
            if let summary = record.streakSummary(today: today), summary.resetCount > 0 {
                Text(summary.isRecord
                     ? "🏆 " + String(localized: "streak_new_record")
                     : String(localized: "streak_record") + " · " + GoalLabels.plural("streak_days", summary.bestDays))
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(.white)
                Text(GoalLabels.plural("streak_reset_count", summary.resetCount))
                    .font(.system(size: 13))
                    .foregroundStyle(.white.opacity(0.7))
            }
            Button(action: onReset) {
                Label("streak_reset_action", systemImage: "arrow.counterclockwise")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 10)
                    .background(Capsule().fill(.white.opacity(0.22)))
            }
            .buttonStyle(.plain)
            .padding(.top, 10)
        }
        .frame(maxWidth: .infinity)
    }
}

/// Séries passées, la plus récente d'abord (carte claire du détail). Rien si aucune.
struct StreakHistoryCard: View {
    let record: GoalRecord
    let card: Color

    var body: some View {
        let history = record.streakHistory
        if !history.isEmpty {
            let best = history.map(\.days).max() ?? 0
            VStack(alignment: .leading, spacing: 8) {
                Text("streak_history_title")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(.secondary)
                ForEach(Array(history.reversed().enumerated()), id: \.offset) { _, streak in
                    HStack {
                        Text(GoalLabels.format("streak_history_row", DateFormats.medium(streak.start), DateFormats.medium(streak.end)))
                            .font(.system(size: 14))
                        Spacer()
                        Text((streak.days == best && best > 0 ? "🏆 " : "") + GoalLabels.plural("streak_days", streak.days))
                            .font(.system(size: 14, weight: .bold))
                    }
                }
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 28, style: .continuous).fill(card))
        }
    }
}

private extension LocalDate {
    /// Midi local de ce jour : aucun décalage d'un jour possible avec le `DatePicker`.
    func noon(calendar: Calendar = .current) -> Date {
        calendar.date(from: DateComponents(year: year, month: month, day: day, hour: 12))!
    }
}
