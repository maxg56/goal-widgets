// Non vérifié : écrit sans Xcode (machine Linux).

/// Type d'objectif. `rawValue` est la valeur utilisée dans le jeu de cas partagé.
public enum GoalKind: String, Sendable {
    case countdown
    case since
}

/// Objectif daté (valeur pure, indépendante du stockage).
public struct Goal: Hashable, Sendable {
    public var kind: GoalKind
    public var startDate: LocalDate
    public var targetDate: LocalDate?

    public init(kind: GoalKind, startDate: LocalDate, targetDate: LocalDate? = nil) {
        self.kind = kind
        self.startDate = startDate
        self.targetDate = targetDate
    }
}

extension Goal {
    /// « Continuer en compteur depuis » : un compte à rebours devient un compteur « depuis »
    /// partant de sa date cible (la date cible est effacée). Le nombre de jours affiché est
    /// donc le même que « Atteint il y a N jours ». Renvoie nil si l'objectif n'est pas un
    /// compte à rebours avec une date cible. L'app ne propose l'action qu'en phase `reached`.
    public func continuedAsSince() -> Goal? {
        guard kind == .countdown, let target = targetDate else { return nil }
        return Goal(kind: .since, startDate: target, targetDate: nil)
    }
}
