// Non vérifié : écrit sans Xcode (machine Linux).
import Foundation

/// Date calendaire locale (sans heure ni fuseau), équivalent de java.time.LocalDate.
public struct LocalDate: Hashable, Comparable, Sendable, CustomStringConvertible {
    public let year: Int
    public let month: Int
    public let day: Int

    public init(year: Int, month: Int, day: Int) {
        self.year = year
        self.month = month
        self.day = day
    }

    /// Analyse le format ISO `AAAA-MM-JJ` ; renvoie nil si la date est invalide.
    public init?(iso: String) {
        let parts = iso.split(separator: "-").map { Int($0) }
        guard parts.count == 3, let y = parts[0], let m = parts[1], let d = parts[2] else { return nil }
        var comps = DateComponents()
        comps.year = y
        comps.month = m
        comps.day = d
        guard comps.isValidDate(in: LocalDate.utcCalendar) else { return nil }
        self.init(year: y, month: m, day: d)
    }

    /// La date du jour dans le fuseau de l'appareil (à utiliser par la couche app/widget uniquement).
    public static func today(calendar: Calendar = .current, now: Date = Date()) -> LocalDate {
        let c = calendar.dateComponents([.year, .month, .day], from: now)
        return LocalDate(year: c.year!, month: c.month!, day: c.day!)
    }

    /// Nombre de jours calendaires de `self` à `other` (négatif si `other` est avant).
    public func days(until other: LocalDate) -> Int {
        LocalDate.utcCalendar.dateComponents([.day], from: anchor, to: other.anchor).day!
    }

    /// Date décalée de `days` jours calendaires (négatif = dans le passé).
    public func adding(days: Int) -> LocalDate {
        let shifted = LocalDate.utcCalendar.date(byAdding: .day, value: days, to: anchor)!
        let c = LocalDate.utcCalendar.dateComponents([.year, .month, .day], from: shifted)
        return LocalDate(year: c.year!, month: c.month!, day: c.day!)
    }

    /// Représentation ISO `AAAA-MM-JJ` (format de stockage et du jeu de cas partagé).
    public var iso: String { description }

    public var description: String {
        String(format: "%04d-%02d-%02d", year, month, day)
    }

    public static func < (lhs: LocalDate, rhs: LocalDate) -> Bool {
        (lhs.year, lhs.month, lhs.day) < (rhs.year, rhs.month, rhs.day)
    }

    // Calculs en UTC grégorien : aucune heure d'été ne peut décaler un jour.
    static let utcCalendar: Calendar = {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        return cal
    }()

    private var anchor: Date {
        LocalDate.utcCalendar.date(from: DateComponents(year: year, month: month, day: day))!
    }
}
