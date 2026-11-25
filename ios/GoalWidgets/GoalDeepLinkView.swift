// Non vérifié : écrit sans Xcode (machine Linux).
import SwiftUI
import SwiftData
import GoalCore

/// Feuille ouverte par un appui sur un widget : l'objectif concerné et son état du jour.
/// Réutilise `GoalRowView` ; l'écran d'édition (t05) pourra y être branché.
struct GoalDeepLinkView: View {
    @Environment(\.dismiss) private var dismiss
    @Query private var records: [GoalRecord]

    init(goalID: UUID) {
        _records = Query(filter: #Predicate<GoalRecord> { $0.id == goalID })
    }

    var body: some View {
        NavigationStack {
            Group {
                if let record = records.first {
                    List {
                        GoalRowView(record: record, today: LocalDate.today())
                    }
                } else {
                    ContentUnavailableView {
                        Label {
                            Text("deeplink_goal_not_found", tableName: "Widget")
                        } icon: {
                            Image(systemName: "calendar.badge.exclamationmark")
                        }
                    }
                }
            }
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        dismiss()
                    } label: {
                        Text("deeplink_close", tableName: "Widget")
                    }
                }
            }
        }
    }
}
