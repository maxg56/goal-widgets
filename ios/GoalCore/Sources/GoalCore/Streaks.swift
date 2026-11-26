// Non vérifié : écrit sans Xcode (machine Linux).
import Foundation

/// Série terminée d'un compteur « depuis » remis à zéro : du `start` (départ de la série) au `end`
/// (jour de la remise à zéro). Sa durée est l'écart en jours entre les deux (0 si remise le jour même).
public struct PastStreak: Hashable, Sendable {
    public let start: LocalDate
    public let end: LocalDate

    public init(start: LocalDate, end: LocalDate) {
        self.start = start
        self.end = end
    }

    public var days: Int { start.days(until: end) }
}

/// Ce qu'affichent le détail et le widget d'un compteur « série ».
public struct StreakSummary: Equatable, Sendable {
    /// Jours de la série en cours (0 avant le départ).
    public let currentDays: Int
    /// Plus longue série, série en cours comprise.
    public let bestDays: Int
    /// Nombre de remises à zéro (= séries terminées).
    public let resetCount: Int
    /// La série en cours est le record : au moins une remise à zéro, au moins 1 jour, et aucune série
    /// passée plus longue (égalité comprise).
    public let isRecord: Bool
}

/// Raison pour laquelle une date de remise à zéro est refusée. `rawValue` = code des cas partagés.
public enum ResetDateError: String, Error, Sendable {
    /// Avant le départ de la série en cours.
    case beforeStart
    /// Dans le futur.
    case inFuture
}

/// Remise à zéro des compteurs « depuis » marqués « série » : la série en cours est archivée dans
/// l'historique et le compteur repart de la date choisie (aujourd'hui par défaut). Comportement figé
/// par `shared-cases/streaks.json` (même logique que `Streaks` côté Kotlin).
public enum Streaks {

    /// nil si `on` est une date de remise à zéro valide pour `goal` ce jour-là.
    public static func validate(goal: Goal, on: LocalDate, today: LocalDate) -> ResetDateError? {
        if on > today { return .inFuture }
        if on < goal.startDate { return .beforeStart }
        return nil
    }

    /// Remet `goal` à zéro le jour `on` : la série `startDate → on` rejoint `history` et le compteur
    /// repart de `on`. Seul un compteur « depuis » se remet à zéro.
    public static func reset(
        goal: Goal,
        history: [PastStreak],
        on: LocalDate,
        today: LocalDate
    ) -> Result<(goal: Goal, history: [PastStreak]), ResetDateError> {
        precondition(goal.kind == .since, "Seul un compteur « depuis » se remet à zéro")
        if let error = validate(goal: goal, on: on, today: today) { return .failure(error) }
        var reset = goal
        reset.startDate = on
        return .success((reset, history + [PastStreak(start: goal.startDate, end: on)]))
    }

    public static func summary(goal: Goal, history: [PastStreak], today: LocalDate) -> StreakSummary {
        let current = max(0, goal.startDate.days(until: today))
        let bestPast = history.map(\.days).max() ?? 0
        return StreakSummary(
            currentDays: current,
            bestDays: max(current, bestPast),
            resetCount: history.count,
            isRecord: !history.isEmpty && current > 0 && current >= bestPast
        )
    }

    /// Historique stocké en texte : `AAAA-MM-JJ/AAAA-MM-JJ` séparés par `;`, ordre chronologique.
    public static func encode(_ history: [PastStreak]) -> String {
        history.map { "\($0.start.iso)/\($0.end.iso)" }.joined(separator: ";")
    }

    /// Inverse de `encode` ; une entrée illisible est ignorée.
    public static func decode(_ raw: String?) -> [PastStreak] {
        (raw ?? "").split(separator: ";", omittingEmptySubsequences: true).compactMap { entry in
            let parts = entry.trimmingCharacters(in: .whitespaces).split(separator: "/", omittingEmptySubsequences: false)
            guard parts.count == 2,
                  let start = LocalDate(iso: String(parts[0])),
                  let end = LocalDate(iso: String(parts[1])),
                  end >= start else { return nil }
            return PastStreak(start: start, end: end)
        }
    }
}

/// Bouton ↺ du widget, sans boîte de dialogue possible : un premier appui « arme » le bouton
/// (« Confirmer ? »), un second appui dans les `window` secondes remet à zéro. Passé ce délai,
/// l'appui arme de nouveau.
public enum WidgetResetTap {
    public static let window: TimeInterval = 5

    public enum Outcome: Equatable, Sendable { case arm, reset }

    /// `armedAt` : instant du premier appui, nil si le bouton n'est pas armé.
    public static func onTap(armedAt: Date?, now: Date) -> Outcome {
        isArmed(armedAt: armedAt, now: now) ? .reset : .arm
    }

    public static func isArmed(armedAt: Date?, now: Date) -> Bool {
        guard let armedAt else { return false }
        let elapsed = now.timeIntervalSince(armedAt)
        return elapsed >= 0 && elapsed < window
    }
}
