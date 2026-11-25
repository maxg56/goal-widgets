// Non vérifié : écrit sans Xcode (machine Linux).
import Foundation
import GoalCore

/// Modifications d'un objectif stocké (couche app ; volontairement hors de `Storage/`,
/// partagé avec le widget, pour ne pas toucher au modèle SwiftData).
extension GoalRecord {
    /// Remplace titre, type et dates. Un objectif « depuis » n'a pas de date cible.
    func update(title: String, goal: Goal) {
        self.title = title
        kindRaw = goal.kind.rawValue
        startDateISO = goal.startDate.iso
        targetDateISO = goal.kind == .since ? nil : goal.targetDate?.iso
    }

    /// « Continuer en compteur depuis » ; renvoie false si l'objectif n'est pas un compte à rebours.
    @discardableResult
    func continueAsSince() -> Bool {
        guard let converted = goal?.continuedAsSince() else { return false }
        update(title: title, goal: converted)
        return true
    }
}
