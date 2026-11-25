// Non vérifié : écrit sans Xcode (machine Linux).
import SwiftUI
import GoalCore

/// Ligne de la liste : titre, statut (« dans N jours », « Atteint il y a N jours », « Depuis N jours »),
/// puis barre + pourcentage (compte à rebours) ou forme lisible années/mois/jours (« depuis »).
/// Emoji devant le titre, couleur de l'objectif sur le statut et la barre (t11).
struct GoalRowView: View {
    let record: GoalRecord
    let today: LocalDate

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            let color = record.style.color
            Text(record.displayTitle)
                .font(.headline)
            if let goal = record.goal {
                let state = CountdownEngine.compute(goal, today: today)
                Text(GoalLabels.status(for: state))
                    .font(.subheadline)
                    .foregroundStyle(GoalAppearance.statusStyle(color))
                if let percent = state.percent {
                    HStack {
                        ProgressView(value: Double(percent), total: 100)
                            .tint(GoalAppearance.tint(color))
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
