// Non vérifié : écrit sans Xcode (machine Linux).

/// Erreur de saisie du formulaire de création d'un compte à rebours.
public enum CountdownValidationError: Equatable, Sendable {
    /// Titre vide (ou uniquement des espaces).
    case emptyTitle
    /// La date cible doit être strictement postérieure à la date de départ.
    case targetNotAfterStart
}

/// Règles du formulaire de création (mêmes règles que l'app Android, ticket 02).
public enum CountdownValidation {
    /// Renvoie la première erreur rencontrée, ou nil si la saisie est valide.
    public static func check(title: String, startDate: LocalDate, targetDate: LocalDate) -> CountdownValidationError? {
        if title.allSatisfy(\.isWhitespace) {
            return .emptyTitle
        }
        if targetDate <= startDate {
            return .targetNotAfterStart
        }
        return nil
    }
}

/// Erreur de saisie du formulaire d'un objectif « depuis ».
public enum SinceValidationError: Equatable, Sendable {
    /// Titre vide (ou uniquement des espaces).
    case emptyTitle
    /// La date de départ ne peut pas être dans le futur (aujourd'hui est accepté).
    case startInFuture
}

/// Règles du formulaire d'un objectif « depuis » (mêmes règles que l'app Android, ticket 04).
public enum SinceValidation {
    /// Renvoie la première erreur rencontrée, ou nil si la saisie est valide.
    public static func check(title: String, startDate: LocalDate, today: LocalDate) -> SinceValidationError? {
        if title.allSatisfy(\.isWhitespace) {
            return .emptyTitle
        }
        if startDate > today {
            return .startInFuture
        }
        return nil
    }
}
