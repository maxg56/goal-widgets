// Non vérifié : écrit sans Xcode (machine Linux).
import Foundation
import GoalCore

/// Compteurs « série » (remise à zéro). Dans `Storage/` : partagé avec l'extension widget, dont le
/// bouton ↺ remet le compteur à zéro sans ouvrir l'app (`ResetStreakIntent`).
extension GoalRecord {
    /// Remise à zéro proposée : compteur « depuis » marqué « série ».
    var isStreak: Bool { streak && kindRaw == GoalKind.since.rawValue }

    /// Séries terminées, ordre chronologique.
    var streakHistory: [PastStreak] { Streaks.decode(streakHistoryRaw) }

    func streakSummary(today: LocalDate) -> StreakSummary? {
        guard let goal else { return nil }
        return Streaks.summary(goal: goal, history: streakHistory, today: today)
    }

    /// Remet le compteur à zéro le jour `on` : la série en cours rejoint l'historique et le compteur
    /// repart de `on`. Renvoie false (rien n'est modifié) si l'objectif n'est pas une série ou si la
    /// date est refusée. L'appelant sauvegarde le contexte.
    @discardableResult
    func resetStreak(on: LocalDate, today: LocalDate) -> Bool {
        guard isStreak, let goal else { return false }
        guard case let .success(reset) = Streaks.reset(goal: goal, history: streakHistory, on: on, today: today) else {
            return false
        }
        startDateISO = reset.goal.startDate.iso
        streakHistoryRaw = Streaks.encode(reset.history)
        return true
    }
}
