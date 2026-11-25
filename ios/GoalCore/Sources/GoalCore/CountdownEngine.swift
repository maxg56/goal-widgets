// Non vérifié : écrit sans Xcode (machine Linux).

public enum Phase: String, Sendable {
    case upcoming
}

/// État d'affichage d'un objectif pour une date du jour donnée.
public struct DisplayState: Equatable, Sendable {
    public let phase: Phase
    /// Nombre de jours (toujours ≥ 0).
    public let days: Int
    /// Progression 0.0–1.0, uniquement pour un compte à rebours.
    public let progress: Double?
}

/// Moteur pur : objectif + date du jour → état d'affichage. Aucune dépendance OS.
public enum CountdownEngine {
    public static func compute(_ goal: Goal, today: LocalDate) -> DisplayState {
        guard let target = goal.targetDate else {
            preconditionFailure("Un compte à rebours exige une date cible")
        }
        let total = Double(goal.startDate.days(until: target))
        let elapsed = Double(goal.startDate.days(until: today))
        return DisplayState(
            phase: .upcoming,
            days: today.days(until: target),
            progress: elapsed / total
        )
    }
}
