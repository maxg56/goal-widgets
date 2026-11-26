// Non vérifié : écrit sans Xcode (machine Linux).
import Foundation
import SwiftData
import GoalCore

/// Modèle de stockage SwiftData d'un objectif, distinct de la valeur pure `GoalCore.Goal`.
/// Les dates calendaires sont stockées en texte ISO `AAAA-MM-JJ` (aucun fuseau horaire en jeu).
/// Partagé avec l'extension widget (t07) : ce dossier `Storage/` est à ajouter aux sources du widget.
@Model
final class GoalRecord {
    @Attribute(.unique) var id: UUID
    var title: String
    /// `GoalKind.rawValue` (`countdown` | `since`).
    var kindRaw: String
    var startDateISO: String
    var targetDateISO: String?
    var createdAt: Date
    /// Notifications de jalons (t09). Valeur par défaut à la déclaration : migration légère
    /// automatique de SwiftData pour les objectifs existants (désactivées, cf. spec 44 : l'autorisation
    /// est demandée quand l'utilisateur active une notification).
    var notificationsEnabled: Bool = false
    /// Couleur (t11) : `GoalColor.rawValue` ou nil (couleur du thème). Optionnelle avec valeur par
    /// défaut : migration légère automatique, comme `notificationsEnabled`.
    var colorRaw: String? = nil
    /// Emoji affiché devant le titre (t11), nil = aucun.
    var emoji: String? = nil
    /// Compteur « depuis » marqué « série » : bouton de remise à zéro, record et historique. Valeur par
    /// défaut à la déclaration : migration légère automatique (désactivée pour les objectifs existants).
    var streak: Bool = false
    /// Séries terminées, ordre chronologique (`Streaks.encode`, même texte qu'Android). Gardé si
    /// `streak` est désactivé.
    var streakHistoryRaw: String = ""

    init(
        id: UUID = UUID(),
        title: String,
        kind: GoalKind,
        startDate: LocalDate,
        targetDate: LocalDate?,
        createdAt: Date = Date(),
        notificationsEnabled: Bool = false,
        style: GoalStyle = .none
    ) {
        self.id = id
        self.title = title
        self.kindRaw = kind.rawValue
        self.startDateISO = startDate.iso
        self.targetDateISO = targetDate?.iso
        self.createdAt = createdAt
        self.notificationsEnabled = notificationsEnabled
        self.colorRaw = style.color?.rawValue
        self.emoji = style.emoji
    }
}

extension GoalRecord {
    /// Conversion vers la valeur pure utilisée par le moteur ; nil si l'enregistrement est corrompu.
    var goal: Goal? {
        guard let kind = GoalKind(rawValue: kindRaw),
              let start = LocalDate(iso: startDateISO) else { return nil }
        let target = targetDateISO.flatMap { LocalDate(iso: $0) }
        if kind == .countdown && target == nil { return nil }
        return Goal(kind: kind, startDate: start, targetDate: target)
    }

    /// Couleur et emoji (t11) ; une couleur inconnue est ignorée.
    var style: GoalStyle {
        get { GoalStyle(color: GoalColor(wire: colorRaw), emoji: emoji) }
        set {
            colorRaw = newValue.color?.rawValue
            emoji = GoalStyle.normalizedEmoji(newValue.emoji)
        }
    }

    /// Titre précédé de l'emoji éventuel (liste, widget, choix du widget).
    var displayTitle: String { style.decorate(title) }

    /// Identifiant du format d'export : `id` en minuscules (`docs/export-format.md`).
    var exportID: String { id.uuidString.lowercased() }
}
