// Non vérifié : écrit sans Xcode (machine Linux).
import Foundation

extension LocalDate {
    /// Premier instant de ce jour dans le calendrier donné (minuit local, ou le premier instant
    /// existant si minuit est sauté par un changement d'heure). Utilisé par la timeline du widget.
    public func startOfDay(in calendar: Calendar = .current) -> Date {
        // Midi existe toujours : on part de là puis on remonte au début du jour.
        let noon = calendar.date(from: DateComponents(year: year, month: month, day: day, hour: 12))!
        return calendar.startOfDay(for: noon)
    }
}
