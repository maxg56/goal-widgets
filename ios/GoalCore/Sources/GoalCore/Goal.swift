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
