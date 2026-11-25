// Non vérifié : écrit sans Xcode (machine Linux).
import SwiftUI
import GoalCore

/// Ligne de la liste : titre, « dans N jours », barre de progression et pourcentage.
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
                if let progress = state.progress {
                    HStack {
                        ProgressView(value: progress)
                        Text(GoalLabels.percent(progress))
                            .font(.caption)
                            .monospacedDigit()
                    }
                }
            }
        }
        .padding(.vertical, 4)
    }
}
