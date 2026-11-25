// Non vérifié : écrit sans Xcode (machine Linux).
import SwiftUI
import GoalCore

/// Ligne de la liste : titre, statut (« dans N jours », « Atteint il y a N jours », « Depuis N jours »),
/// puis barre + pourcentage (compte à rebours) ou forme lisible années/mois/jours (« depuis »).
struct GoalRowView: View {
    let record: GoalRecord
    let today: LocalDate

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(record.title)
                .font(.headline)
            if let goal = record.goal {
                let state = CountdownEngine.compute(goal, today: today)
                Text(GoalLabels.status(for: state))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                if let percent = state.percent {
                    HStack {
                        ProgressView(value: Double(percent), total: 100)
                        Text(GoalLabels.percent(percent))
                            .font(.caption)
                            .monospacedDigit()
                    }
                }
                if let breakdown = state.breakdown, let text = GoalLabels.breakdown(breakdown) {
                    Text(text)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .padding(.vertical, 4)
    }
}
