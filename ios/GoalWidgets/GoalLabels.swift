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
        case .elapsed:
            return state.days == 0
                ? String(localized: "since_today")
                : plural("since_days", state.days)
        }
    }

    /// Forme lisible « 1 an 2 mois 3 jours » (composantes nulles omises).
    /// nil en dessous d'un mois : le nombre de jours du statut suffit.
    static func breakdown(_ value: Breakdown) -> String? {
        guard value.years > 0 || value.months > 0 else { return nil }
        var parts: [String] = []
        if value.years > 0 { parts.append(plural("breakdown_years", value.years)) }
        if value.months > 0 { parts.append(plural("breakdown_months", value.months)) }
        if value.days > 0 { parts.append(plural("breakdown_days", value.days)) }
        return parts.joined(separator: " ")
    }

    /// Pourcentage entier calculé par le moteur (`DisplayState.percent`), ex. « 42 % ».
    static func percent(_ value: Int) -> String {
        let format = NSLocalizedString("goal_progress_percent", comment: "")
        return String.localizedStringWithFormat(format, value)
    }

    static func validationMessage(_ error: CountdownValidationError) -> String {
        switch error {
        case .emptyTitle:
            return String(localized: "goal_form_error_empty_title")
        case .targetNotAfterStart:
            return String(localized: "goal_form_error_target_not_after_start")
        }
    }

    static func sinceValidationMessage(_ error: SinceValidationError) -> String {
        switch error {
        case .emptyTitle:
            return String(localized: "goal_form_error_empty_title")
        case .startInFuture:
            return String(localized: "goal_form_error_start_in_future")
        }
    }

    private static func plural(_ key: String, _ count: Int) -> String {
        let format = NSLocalizedString(key, comment: "")
        return String.localizedStringWithFormat(format, count)
    }
}
