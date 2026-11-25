// Non vérifié : écrit sans Xcode (machine Linux).
// Partagé avec l'extension widget (project.yml) : pas d'API réservée à l'app ici.
import SwiftUI
import UIKit
import GoalCore

/// Teintes de la palette `GoalColor` (mêmes valeurs qu'Android, `GoalColors.kt`) : une teinte foncée
/// en mode clair, une claire en mode sombre, pour rester lisible sur le fond système dans les deux cas.
enum GoalAppearance {
    /// Couleur adaptative (clair/sombre) d'une couleur d'objectif.
    static func color(_ color: GoalColor) -> Color {
        let (light, dark) = hex(color)
        return Color(UIColor { traits in
            traits.userInterfaceStyle == .dark ? uiColor(dark) : uiColor(light)
        })
    }

    /// Couleur du statut : celle de l'objectif, sinon le style secondaire habituel.
    static func statusStyle(_ color: GoalColor?) -> AnyShapeStyle {
        if let color { return AnyShapeStyle(self.color(color)) }
        return AnyShapeStyle(.secondary)
    }

    /// Teinte de la barre de progression : celle de l'objectif, sinon la teinte de l'app.
    static func tint(_ color: GoalColor?) -> Color {
        color.map(self.color) ?? .accentColor
    }

    /// Nom lisible (accessibilité du sélecteur, clés `goal_color_<couleur>`).
    static func nameKey(_ color: GoalColor) -> LocalizedStringKey {
        LocalizedStringKey("goal_color_\(color.rawValue)")
    }

    private static func hex(_ color: GoalColor) -> (UInt32, UInt32) {
        switch color {
        case .red: return (0xC62828, 0xEF9A9A)
        case .orange: return (0xE65100, 0xFFB74D)
        case .yellow: return (0x8D6E00, 0xFFE082)
        case .green: return (0x2E7D32, 0xA5D6A7)
        case .teal: return (0x00796B, 0x80CBC4)
        case .blue: return (0x1565C0, 0x90CAF9)
        case .indigo: return (0x3949AB, 0x9FA8DA)
        case .purple: return (0x7B1FA2, 0xCE93D8)
        case .pink: return (0xC2185B, 0xF48FB1)
        case .gray: return (0x616161, 0xBDBDBD)
        }
    }

    private static func uiColor(_ rgb: UInt32) -> UIColor {
        UIColor(
            red: CGFloat((rgb >> 16) & 0xFF) / 255,
            green: CGFloat((rgb >> 8) & 0xFF) / 255,
            blue: CGFloat(rgb & 0xFF) / 255,
            alpha: 1
        )
    }
}
