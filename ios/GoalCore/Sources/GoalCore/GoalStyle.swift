// Non vérifié : écrit sans Xcode (machine Linux).
import Foundation

/// Couleur d'un objectif : palette nommée commune Android/iOS (chaque app choisit ses teintes
/// claires/sombres). `rawValue` = valeur du format d'export (`docs/export-format.md`).
public enum GoalColor: String, CaseIterable, Sendable {
    case red, orange, yellow, green, teal, blue, indigo, purple, pink, gray

    /// nil pour une valeur absente ou inconnue (couleur ajoutée par une version future).
    public init?(wire: String?) {
        guard let wire, let color = GoalColor(rawValue: wire) else { return nil }
        self = color
    }
}

/// Personnalisation visuelle d'un objectif (liste et widgets) : couleur et/ou emoji, facultatifs.
public struct GoalStyle: Equatable, Sendable {
    public var color: GoalColor?
    public var emoji: String?

    public static let none = GoalStyle(color: nil, emoji: nil)

    /// Longueur maximale (unités UTF-16) d'un emoji saisi librement, comme Android.
    public static let emojiMaxLength = 16

    /// Emojis proposés par le formulaire (mêmes que sur Android).
    public static let suggestedEmojis = [
        "🎯", "🎓", "✈️", "🏖️", "🎂", "💍", "❤️", "👶",
        "🏃", "💪", "🚭", "🍷", "💼", "🏠", "📚", "🎉",
    ]

    public init(color: GoalColor?, emoji: String?) {
        self.color = color
        self.emoji = GoalStyle.normalizedEmoji(emoji)
    }

    /// Titre précédé de l'emoji éventuel : « 🎓 Examen ».
    public func decorate(_ title: String) -> String {
        guard let emoji else { return title }
        return "\(emoji) \(title)"
    }

    /// Espaces de bord retirés ; vide → nil. Pas de validation « est un emoji ».
    public static func normalizedEmoji(_ value: String?) -> String? {
        guard let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else {
            return nil
        }
        return trimmed
    }
}
