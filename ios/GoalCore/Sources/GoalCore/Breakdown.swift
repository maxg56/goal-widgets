// Non vérifié : écrit sans Xcode (machine Linux).

/// Temps écoulé décomposé en années / mois / jours (« 1 an 2 mois 3 jours »).
///
/// Règle (identique à Android, ticket 04) : `n` = plus grand nombre de mois tel que
/// `départ + n mois ≤ aujourd'hui`, où « + n mois » est toujours calculé depuis la date de départ
/// et ramené au dernier jour du mois si le jour n'existe pas (31 janvier + 1 mois = 28/29 février),
/// comme `java.time.LocalDate.plusMonths`. Puis `years = n / 12`, `months = n % 12`,
/// `days` = jours de `départ + n mois` à aujourd'hui. Même règle que les mois-anniversaires
/// du planificateur : un mois est complet le 28 février pour un départ le 31 janvier.
/// Aujourd'hui avant le départ → 0/0/0.
public struct Breakdown: Hashable, Sendable {
    public let years: Int
    public let months: Int
    public let days: Int

    public init(years: Int, months: Int, days: Int) {
        self.years = years
        self.months = months
        self.days = days
    }

    public static let zero = Breakdown(years: 0, months: 0, days: 0)

    /// Décomposition du temps écoulé de `start` à `end`.
    public static func between(_ start: LocalDate, _ end: LocalDate) -> Breakdown {
        guard start < end else { return .zero }
        var totalMonths = (end.year - start.year) * 12 + (end.month - start.month)
        if start.adding(months: totalMonths) > end {
            totalMonths -= 1
        }
        let anniversary = start.adding(months: totalMonths)
        return Breakdown(
            years: totalMonths / 12,
            months: totalMonths % 12,
            days: anniversary.days(until: end)
        )
    }
}

extension LocalDate {
    /// Date décalée de `months` mois, jour ramené au dernier jour du mois si nécessaire
    /// (2026-01-31 + 1 mois = 2026-02-28 ; 2024-02-29 + 12 mois = 2025-02-28).
    public func adding(months: Int) -> LocalDate {
        let zeroBased = year * 12 + (month - 1) + months
        // Division euclidienne (fonctionne aussi pour un décalage négatif).
        var newYear = zeroBased / 12
        var newMonthIndex = zeroBased % 12
        if newMonthIndex < 0 {
            newMonthIndex += 12
            newYear -= 1
        }
        let newMonth = newMonthIndex + 1
        let newDay = min(day, LocalDate.daysInMonth(year: newYear, month: newMonth))
        return LocalDate(year: newYear, month: newMonth, day: newDay)
    }

    /// Nombre de jours du mois (calendrier grégorien).
    public static func daysInMonth(year: Int, month: Int) -> Int {
        switch month {
        case 2:
            return isLeapYear(year) ? 29 : 28
        case 4, 6, 9, 11:
            return 30
        default:
            return 31
        }
    }

    /// Année bissextile grégorienne.
    public static func isLeapYear(_ year: Int) -> Bool {
        (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
    }
}
