// Non vérifié : écrit sans Xcode (machine Linux).
import Foundation
import GoalCore

/// Construit les libellés à partir de l'état du moteur (GoalCore ne produit pas de texte).
/// Pluriels : fr.lproj/Localizable.stringsdict (mêmes clés que les `plurals` Android).
enum GoalLabels {
    static func status(for state: DisplayState) -> String {
        switch state.phase {
        case .upcoming:
            return plural("countdown_in_days", state.days)
        case .today:
            return String(localized: "countdown_today")
        case .reached:
            return plural("countdown_reached_days_ago", state.days)
        case .elapsed:
            return state.days == 0
                ? String(localized: "since_today")
                : plural("since_days", state.days)
        }
    }

    /// Forme lisible « 1 an 2 mois 3 jours » (composantes nulles omises).
    /// nil en dessous d'un mois : le nombre de jours du statut suffit.
    static func breakdown(_ value: Breakdown) -> String? {
        guard value.years > 0 || value.months > 0 else { return nil }
        var parts: [String] = []
        if value.years > 0 { parts.append(plural("breakdown_years", value.years)) }
        if value.months > 0 { parts.append(plural("breakdown_months", value.months)) }
        if value.days > 0 { parts.append(plural("breakdown_days", value.days)) }
        return parts.joined(separator: " ")
    }

    /// Pourcentage entier calculé par le moteur (`DisplayState.percent`), ex. « 42 % ».
    static func percent(_ value: Int) -> String {
        let format = NSLocalizedString("goal_progress_percent", comment: "")
        return String.localizedStringWithFormat(format, value)
    }

    /// Grand nombre des cartes, du détail et du widget : jours, ou « J » le jour J.
    static func bigNumber(_ state: DisplayState) -> String {
        state.phase == .today ? String(localized: "big_number_today") : "\(state.days)"
    }

    /// Légende sous le grand nombre (« jours restants », « jours depuis »…).
    static func caption(_ state: DisplayState) -> String {
        switch state.phase {
        case .upcoming: return plural("caption_days_left", state.days)
        case .today: return String(localized: "caption_today")
        case .reached: return plural("caption_days_after_target", state.days)
        case .elapsed: return plural("caption_days_since", state.days)
        }
    }

    /// Légende courte (petits widgets).
    static func shortCaption(_ state: DisplayState) -> String {
        switch state.phase {
        case .upcoming, .elapsed: return plural("caption_short_days", state.days)
        case .today: return String(localized: "caption_short_today")
        case .reached: return plural("caption_short_after", state.days)
        }
    }

    /// Écart avec aujourd'hui : « dans 3 j », « il y a 2 j », « aujourd'hui ».
    static func relative(_ daysFromToday: Int) -> String {
        if daysFromToday > 0 { return plural("relative_in_days", daysFromToday) }
        if daysFromToday < 0 { return plural("relative_days_ago", -daysFromToday) }
        return String(localized: "relative_today")
    }

    /// Cap de l'anneau « depuis » : « 1 mois », « 7 ans ».
    static func milestone(_ milestone: RingMilestone) -> String {
        switch milestone.unit {
        case .month: return plural("breakdown_months", milestone.count)
        case .year: return plural("breakdown_years", milestone.count)
        }
    }

    static func chartName(_ chart: ChartKind) -> String {
        switch chart {
        case .dots: return String(localized: "chart_dots")
        case .ring: return String(localized: "chart_ring")
        case .arcs: return String(localized: "chart_arcs")
        case .bars: return String(localized: "chart_bars")
        }
    }

    /// « Arrivée le … » / « vers 1 an » : textes de l'anneau (détail, aperçu du formulaire, widget).
    struct RingTexts {
        let center: String
        let caption: String
        let footnote: String
    }

    static func ringTexts(_ ring: RingChart) -> RingTexts {
        let center = percent(ring.percent)
        let date = DateFormats.medium(ring.date)
        guard let milestone = ring.milestone else {
            return RingTexts(
                center: center,
                caption: String(localized: "ring_caption_countdown"),
                footnote: ring.daysLeft > 0 ? format("ring_arrival", date) : String(localized: "ring_reached")
            )
        }
        let label = self.milestone(milestone)
        return RingTexts(
            center: center,
            caption: format("ring_caption_since", label),
            footnote: format("ring_milestone_footnote", label, date, plural("days_short", ring.daysLeft))
        )
    }

    /// « a · b ».
    static func joined(_ a: String, _ b: String) -> String { format("joined_dot", a, b) }

    /// Chaîne localisée avec arguments texte (`%@`, `%1$@`…).
    static func format(_ key: String, _ args: String...) -> String {
        String(format: NSLocalizedString(key, comment: ""), arguments: args.map { $0 as CVarArg })
    }

    static func plural(_ key: String, _ count: Int) -> String {
        let format = NSLocalizedString(key, comment: "")
        return String.localizedStringWithFormat(format, count)
    }
}

/// Formats de date dans la langue du téléphone (mêmes gabarits qu'Android, `DateFormats`).
/// `LocalDate` → `Date` à midi local (aucun décalage possible d'un jour).
enum DateFormats {
    static func long(_ date: LocalDate) -> String { styled(date, .long) }
    static func medium(_ date: LocalDate) -> String { styled(date, .medium) }
    /// « 3 févr. »
    static func dayMonth(_ date: LocalDate) -> String { template("d MMM", date) }
    /// « févr. »
    static func monthShort(_ date: LocalDate) -> String { template("MMM", date) }
    /// « févr. 2027 »
    static func monthYear(_ date: LocalDate) -> String { template("MMM yyyy", date) }
    /// « Jeu. 26 novembre 2026 » (première lettre en capitale)
    static func weekdayLong(_ date: LocalDate) -> String { template("EEE d MMMM yyyy", date).capitalizedFirst }
    /// « jeudi 26 novembre »
    static func weekdayDayMonth(_ date: LocalDate) -> String { template("EEEE d MMMM", date) }

    static func date(_ local: LocalDate) -> Date {
        Calendar.current.date(from: DateComponents(year: local.year, month: local.month, day: local.day, hour: 12)) ?? Date()
    }

    private static func styled(_ local: LocalDate, _ style: DateFormatter.Style) -> String {
        let f = DateFormatter()
        f.dateStyle = style
        f.timeStyle = .none
        return f.string(from: date(local))
    }

    private static func template(_ template: String, _ local: LocalDate) -> String {
        let f = DateFormatter()
        f.setLocalizedDateFormatFromTemplate(template)
        return f.string(from: date(local))
    }
}

extension String {
    var capitalizedFirst: String { prefix(1).uppercased() + dropFirst() }
}
