// Non vérifié : écrit sans Xcode (machine Linux).
import Foundation
import GoalCore

/// Construit les libellés à partir de l'état du moteur (GoalCore ne produit pas de texte).
/// Pluriels : fr.lproj/Localizable.stringsdict (mêmes clés que les `plurals` Android).
enum GoalLabels {
    static func status(for state: DisplayState) -> String {
        switch state.phase {
        case .upcoming:
            return plural("countdown_in_days", state.days)
        case .today:
            return String(localized: "countdown_today")
        case .reached:
            return plural("countdown_reached_days_ago", state.days)
        }
    }

    /// Pourcentage entier, ex. « 42 % » (progress déjà borné 0–1 par le moteur).
    static func percent(_ progress: Double) -> String {
        progress.formatted(.percent.precision(.fractionLength(0)))
    }

    static func validationMessage(_ error: CountdownValidationError) -> String {
        switch error {
        case .emptyTitle:
            return String(localized: "goal_form_error_empty_title")
        case .targetNotAfterStart:
            return String(localized: "goal_form_error_target_not_after_start")
        }
    }

    private static func plural(_ key: String, _ count: Int) -> String {
        let format = NSLocalizedString(key, comment: "")
        return String.localizedStringWithFormat(format, count)
    }
}
