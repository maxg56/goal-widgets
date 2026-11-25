// Non vérifié : écrit sans Xcode (machine Linux).

/// Phase d'un objectif à une date donnée. `rawValue` = valeur du jeu de cas partagé.
public enum Phase: String, Sendable {
    /// Compte à rebours, cible dans le futur : `days` = jours restants.
    case upcoming
    /// Compte à rebours, jour J : `days` = 0.
    case today
    /// Compte à rebours, cible dépassée : `days` = jours écoulés depuis la cible (« Atteint il y a N jours »).
    case reached
    /// Compteur « depuis » : `days` = jours écoulés depuis la date de départ (≥ 0).
    case elapsed
}

/// État d'affichage d'un objectif pour une date du jour donnée.
public struct DisplayState: Equatable, Sendable {
    public let phase: Phase
    /// Nombre de jours (toujours ≥ 0).
    public let days: Int
    /// Progression 0.0–1.0 (bornée), uniquement pour un compte à rebours ; nil pour « depuis ».
    public let progress: Double?
    /// Pourcentage affiché 0–100, uniquement pour un compte à rebours ; nil pour « depuis ».
    /// Calcul entier arrondi vers le bas (jamais `Int(progress * 100)`) : 100 seulement à partir du jour J.
    public let percent: Int?
    /// Temps écoulé en années/mois/jours, uniquement pour « depuis » ; nil pour un compte à rebours.
    public let breakdown: Breakdown?

    public init(phase: Phase, days: Int, progress: Double?, percent: Int? = nil, breakdown: Breakdown? = nil) {
        self.phase = phase
        self.days = days
        self.progress = progress
        self.percent = percent
        self.breakdown = breakdown
    }
}

/// Moteur pur : objectif + date du jour → état d'affichage. Aucune dépendance OS.
/// Les jours sont des jours calendaires (LocalDate), sans heure ni fuseau.
public enum CountdownEngine {
    public static func compute(_ goal: Goal, today: LocalDate) -> DisplayState {
        switch goal.kind {
        case .since:
            return computeSince(start: goal.startDate, today: today)
        case .countdown:
            guard let target = goal.targetDate else {
                preconditionFailure("Un compte à rebours exige une date cible")
            }
            return computeCountdown(start: goal.startDate, target: target, today: today)
        }
    }

    /// « Depuis » : jours écoulés depuis le départ (jamais négatifs ; départ futur → 0), sans progression.
    /// Une éventuelle date cible est ignorée.
    private static func computeSince(start: LocalDate, today: LocalDate) -> DisplayState {
        DisplayState(
            phase: .elapsed,
            days: max(start.days(until: today), 0),
            progress: nil,
            percent: nil,
            breakdown: Breakdown.between(start, today)
        )
    }

    private static func computeCountdown(start: LocalDate, target: LocalDate, today: LocalDate) -> DisplayState {
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
            progress: progress(start: start, target: target, today: today),
            percent: percent(start: start, target: target, today: today),
            breakdown: nil
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

    /// Pourcentage entier (même règle que Android, journal [t02]) :
    /// `clamped = min(max(aujourd'hui − départ, 0), total)` puis `clamped * 100 / total` (division entière).
    /// Départ ≥ cible : 0 avant la cible, 100 à partir d'elle.
    private static func percent(start: LocalDate, target: LocalDate, today: LocalDate) -> Int {
        let total = start.days(until: target)
        guard total > 0 else { return today >= target ? 100 : 0 }
        let clamped = min(max(start.days(until: today), 0), total)
        return clamped * 100 / total
    }
}
