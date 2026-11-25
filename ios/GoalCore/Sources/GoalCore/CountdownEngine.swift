// Non vérifié : écrit sans Xcode (machine Linux).

/// Phase d'un objectif à une date donnée. `rawValue` = valeur du jeu de cas partagé.
public enum Phase: String, Sendable {
    /// Cible dans le futur : `days` = jours restants.
    case upcoming
    /// Jour J : `days` = 0.
    case today
    /// Cible dépassée : `days` = jours écoulés depuis la cible (« Atteint il y a N jours »).
    case reached
}

/// État d'affichage d'un objectif pour une date du jour donnée.
public struct DisplayState: Equatable, Sendable {
    public let phase: Phase
    /// Nombre de jours (toujours ≥ 0).
    public let days: Int
    /// Progression 0.0–1.0 (bornée), uniquement pour un compte à rebours.
    public let progress: Double?

    public init(phase: Phase, days: Int, progress: Double?) {
        self.phase = phase
        self.days = days
        self.progress = progress
    }
}

/// Moteur pur : objectif + date du jour → état d'affichage. Aucune dépendance OS.
/// Les jours sont des jours calendaires (LocalDate), sans heure ni fuseau.
public enum CountdownEngine {
    public static func compute(_ goal: Goal, today: LocalDate) -> DisplayState {
        guard let target = goal.targetDate else {
            preconditionFailure("Un compte à rebours exige une date cible")
        }
        let remaining = today.days(until: target)
        let phase: Phase
        if remaining > 0 {
            phase = .upcoming
        } else if remaining == 0 {
            phase = .today
        } else {
            phase = .reached
        }
        return DisplayState(
            phase: phase,
            days: abs(remaining),
            progress: progress(start: goal.startDate, target: target, today: today)
        )
    }

    /// (aujourd'hui − départ) / (cible − départ), borné à [0, 1].
    /// Départ ≥ cible (ne devrait pas arriver, le formulaire l'interdit) : 0 avant la cible, 1 à partir d'elle.
    private static func progress(start: LocalDate, target: LocalDate, today: LocalDate) -> Double {
        let total = start.days(until: target)
        guard total > 0 else { return today >= target ? 1.0 : 0.0 }
        let elapsed = Double(start.days(until: today)) / Double(total)
        return min(max(elapsed, 0.0), 1.0)
    }
}
