// Non vérifié : écrit sans Xcode (machine Linux).

/// Type de jalon. `rawValue` est la valeur utilisée dans `shared-cases/planner.json`.
public enum MilestoneKind: String, Hashable, Sendable, CaseIterable {
    /// « depuis » : n-ième mois-anniversaire (`value` = mois écoulés au total, jamais multiple de 12).
    case monthAnniversary = "month_anniversary"
    /// « depuis » : anniversaire annuel (`value` = années).
    case yearAnniversary = "year_anniversary"
    /// Compte à rebours : rappel mensuel (`value` = mois restants, ≥ 2).
    case monthlyReminder = "monthly_reminder"
    /// Compte à rebours : J-30, J-7, J-1 (`value` = 30, 7 ou 1).
    case daysBefore = "days_before"
    /// Compte à rebours : jour J (`value` = 0).
    case targetDay = "target_day"
}

/// Une notification à programmer. Aucune heure ni texte : la couche app ajoute l'heure d'envoi
/// (réglage global) et construit le texte à partir de `kind` + `value` (clés de localisation).
public struct Milestone: Hashable, Sendable {
    /// Identifiant stable `<goalID>:<kind>:<AAAA-MM-JJ>` : reprogrammer ne crée pas de doublon.
    public let id: String
    public let goalID: String
    public let date: LocalDate
    public let kind: MilestoneKind
    public let value: Int

    public init(goalID: String, date: LocalDate, kind: MilestoneKind, value: Int) {
        self.id = "\(goalID):\(kind.rawValue):\(date.iso)"
        self.goalID = goalID
        self.date = date
        self.kind = kind
        self.value = value
    }

    /// Force du jalon : si deux jalons d'un même objectif tombent le même jour, le plus fort l'emporte.
    public var strength: Int {
        switch kind {
        case .targetDay: return 100
        case .daysBefore: return value == 1 ? 90 : value == 7 ? 80 : 70
        case .yearAnniversary: return 60
        case .monthlyReminder: return 50
        case .monthAnniversary: return 40
        }
    }
}

/// Planificateur de jalons (pur) : objectif + date du jour + horizon → notifications à venir.
///
/// Règles (identiques à Android, ticket 08 ; figées par `shared-cases/planner.json`) :
/// - fenêtre : `today ≤ date ≤ today + horizonDays` (bornes incluses) ; objectif désactivé → rien ;
/// - « depuis » : `départ + n mois` pour n ≥ 1 (repli sur le dernier jour du mois, toujours calculé
///   depuis le départ, comme `LocalDate.adding(months:)`) ; n multiple de 12 → anniversaire annuel ;
/// - compte à rebours (cible T) : J-30, J-7, J-1, jour J, et rappels mensuels `T − m mois` pour
///   m ≥ 2 (J-30 prend le relais pour le dernier mois) ; rien après T ;
/// - aucune notification le jour de départ ni avant (s'applique aux deux types) ;
/// - au plus un jalon par jour : le plus fort l'emporte (`Milestone.strength`) ;
/// - résultat trié par date croissante.
public enum MilestonePlanner {

    public static func plan(
        goalID: String,
        goal: Goal,
        notificationsEnabled: Bool,
        today: LocalDate,
        horizonDays: Int
    ) -> [Milestone] {
        guard notificationsEnabled, horizonDays >= 0 else { return [] }
        let last = today.adding(days: horizonDays)

        var candidates: [Milestone] = []
        switch goal.kind {
        case .since:
            candidates = sinceCandidates(goalID: goalID, start: goal.startDate, upTo: last)
        case .countdown:
            guard let target = goal.targetDate else { return [] }
            candidates = countdownCandidates(goalID: goalID, target: target, start: goal.startDate, from: today)
        }

        var strongestByDate: [LocalDate: Milestone] = [:]
        for m in candidates where m.date >= today && m.date <= last && m.date > goal.startDate {
            if let existing = strongestByDate[m.date], existing.strength >= m.strength { continue }
            strongestByDate[m.date] = m
        }
        return strongestByDate.values.sorted { $0.date < $1.date }
    }

    /// Les `limit` notifications les plus proches, tous objectifs confondus (limite iOS : 64).
    /// Ordre : date, puis jalon le plus fort, puis identifiant (déterministe).
    public static func closest(_ milestones: [Milestone], limit: Int) -> [Milestone] {
        let sorted = milestones.sorted {
            if $0.date != $1.date { return $0.date < $1.date }
            if $0.strength != $1.strength { return $0.strength > $1.strength }
            return $0.id < $1.id
        }
        return Array(sorted.prefix(max(limit, 0)))
    }

    // Mois-anniversaires (et annuels) de n = 1 jusqu'au dernier jour de la fenêtre.
    private static func sinceCandidates(goalID: String, start: LocalDate, upTo last: LocalDate) -> [Milestone] {
        var result: [Milestone] = []
        var n = 1
        while true {
            let date = start.adding(months: n)
            if date > last { break }
            if n % 12 == 0 {
                result.append(Milestone(goalID: goalID, date: date, kind: .yearAnniversary, value: n / 12))
            } else {
                result.append(Milestone(goalID: goalID, date: date, kind: .monthAnniversary, value: n))
            }
            n += 1
        }
        return result
    }

    // Jalons d'un compte à rebours ; le filtrage final (fenêtre, départ) est fait par `plan`.
    private static func countdownCandidates(
        goalID: String,
        target: LocalDate,
        start: LocalDate,
        from today: LocalDate
    ) -> [Milestone] {
        var result: [Milestone] = [
            Milestone(goalID: goalID, date: target, kind: .targetDay, value: 0),
            Milestone(goalID: goalID, date: target.adding(days: -1), kind: .daysBefore, value: 1),
            Milestone(goalID: goalID, date: target.adding(days: -7), kind: .daysBefore, value: 7),
            Milestone(goalID: goalID, date: target.adding(days: -30), kind: .daysBefore, value: 30),
        ]
        // Rappels mensuels : T − m mois recule quand m grandit ; on s'arrête avant aujourd'hui ou au départ.
        var m = 2
        while true {
            let date = target.adding(months: -m)
            if date < today || date <= start { break }
            result.append(Milestone(goalID: goalID, date: date, kind: .monthlyReminder, value: m))
            m += 1
        }
        return result
    }
}
