// Non vérifié : écrit sans Xcode (machine Linux).
import Foundation
import GoalCore

/// Texte d'une notification à partir du jalon (GoalCore ne produit pas de texte).
/// Le titre de la notification est celui de l'objectif.
enum NotificationLabels {
    static func body(for milestone: Milestone) -> String {
        switch milestone.kind {
        case .monthAnniversary, .yearAnniversary:
            let months = milestone.kind == .yearAnniversary ? milestone.value * 12 : milestone.value
            return format("notif_since_elapsed", duration(months: months))
        case .monthlyReminder:
            return format("notif_countdown_remaining", duration(months: milestone.value))
        case .daysBefore:
            if milestone.value == 1 {
                return String(localized: "notif_countdown_tomorrow")
            }
            return String.localizedStringWithFormat(
                NSLocalizedString("notif_countdown_days_left", comment: ""),
                milestone.value
            )
        case .targetDay:
            return String(localized: "notif_countdown_today")
        }
    }

    /// « 3 mois », « 1 an », « 1 an 2 mois » (mêmes pluriels que la forme lisible de la liste).
    private static func duration(months: Int) -> String {
        GoalLabels.breakdown(Breakdown(years: months / 12, months: months % 12, days: 0)) ?? ""
    }

    private static func format(_ key: String, _ argument: String) -> String {
        String(format: NSLocalizedString(key, comment: ""), argument)
    }
}
