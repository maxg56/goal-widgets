// Non vérifié : écrit sans Xcode (machine Linux).
import SwiftUI
import GoalCore

/// Ressorts communs (mêmes intentions qu'Android `Motion`) : tout le mouvement de l'app est doux.
enum Motion {
    static let soft = Animation.spring(response: 0.45, dampingFraction: 0.8)
    static let bouncy = Animation.spring(response: 0.45, dampingFraction: 0.6)
    static let slow = Animation.spring(response: 0.7, dampingFraction: 0.85)
    /// Apparition des graphiques (lente, rebond réglable).
    static func reveal(damping: Double) -> Animation { .spring(response: 1.1, dampingFraction: damping) }
}

/// Taille du grand chiffre selon sa longueur (« 12345 » doit tenir à l'écran).
func bigNumberSize(_ text: String, max: CGFloat) -> CGFloat {
    switch text.count {
    case 0...2: return max
    case 3: return max * 0.82
    case 4: return max * 0.66
    default: return max * 0.54
    }
}

extension View {
    /// Entrée décalée : fondu + glissement en ressort, retardée selon `index` ; rien si `index` est nil.
    func staggeredEnter(_ index: Int?, dx: CGFloat = 0, dy: CGFloat = 28) -> some View {
        modifier(StaggeredEnter(index: index, dx: dx, dy: dy))
    }

    /// Léger tremblement tant que `active` (personnalisation « façon écran d'accueil »).
    func wobble(_ active: Bool, seed: Int, amplitude: Double = 1.1) -> some View {
        modifier(Wobble(active: active, seed: seed, amplitude: amplitude))
    }
}

private struct StaggeredEnter: ViewModifier {
    let index: Int?
    let dx: CGFloat
    let dy: CGFloat
    @State private var shown = false

    func body(content: Content) -> some View {
        let hidden = index != nil && !shown
        content
            .opacity(hidden ? 0 : 1)
            .offset(x: hidden ? dx : 0, y: hidden ? dy : 0)
            .onAppear {
                guard let index, !shown else { return }
                withAnimation(.spring(response: 0.75, dampingFraction: 0.7).delay(0.07 * Double(min(index, 8)))) {
                    shown = true
                }
            }
    }
}

private struct Wobble: ViewModifier {
    let active: Bool
    let seed: Int
    let amplitude: Double

    func body(content: Content) -> some View {
        // Structure constante (pas de `if`) : l'identité du contenu ne change pas en entrant en personnalisation.
        content.phaseAnimator([-1.0, 1.0]) { view, phase in
            view
                .rotationEffect(.degrees(active ? phase * amplitude : 0))
                .offset(y: active ? phase * Double(seed % 2 * 2 - 1) * 0.6 : 0)
        } animation: { _ in
            .easeInOut(duration: 0.13 + Double(seed % 3) * 0.018)
        }
    }
}

/// Bouton qui s'enfonce en ressort (cartes, puces de date, bouton collant).
struct PressableStyle: ButtonStyle {
    var scale: CGFloat = 0.96

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? scale : 1)
            .animation(.spring(response: 0.3, dampingFraction: 0.55), value: configuration.isPressed)
    }
}

/// Bloc masquable : tremble en personnalisation, petit × rond en haut à droite.
struct EditableBlock<Content: View>: View {
    let editing: Bool
    let seed: Int
    let label: String
    var badgeOffset: CGFloat = 6
    let onRemove: () -> Void
    @ViewBuilder let content: () -> Content

    var body: some View {
        ZStack(alignment: .topTrailing) {
            content()
            if editing {
                RemoveBadge(label: label, action: onRemove)
                    .offset(x: badgeOffset, y: -badgeOffset)
                    .transition(.scale.combined(with: .opacity))
            }
        }
        .wobble(editing, seed: seed)
        .animation(.spring(response: 0.4, dampingFraction: 0.45), value: editing)
    }
}

/// Petit × rond sombre (masquer un bloc ou un graphique).
struct RemoveBadge: View {
    let label: String
    var size: CGFloat = 26
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "xmark")
                .font(.system(size: size * 0.42, weight: .bold))
                .foregroundStyle(.white)
                .frame(width: size, height: size)
                .background(Circle().fill(Color(white: 0.15)))
                .shadow(color: .black.opacity(0.3), radius: 4, y: 2)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(GoalLabels.format("action_hide", label))
    }
}

/// Rangée « Masqués » : un + par élément retiré. Rien si la liste est vide.
struct HiddenRow: View {
    let items: [(label: String, restore: () -> Void)]
    var foreground: Color = .white
    var chipBackground: Color = .white.opacity(0.18)

    var body: some View {
        if !items.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                Text("detail_hidden_title")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(foreground.opacity(0.7))
                FlowLayout(spacing: 8) {
                    ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                        Button(action: item.restore) {
                            HStack(spacing: 6) {
                                Image(systemName: "plus")
                                    .font(.system(size: 11, weight: .bold))
                                    .frame(width: 20, height: 20)
                                    .background(Circle().fill(foreground.opacity(0.2)))
                                Text(item.label)
                                    .font(.system(size: 13, weight: .semibold))
                            }
                            .foregroundStyle(foreground)
                            .padding(.leading, 8)
                            .padding(.trailing, 14)
                            .padding(.vertical, 7)
                            .background(Capsule().fill(chipBackground))
                        }
                        .buttonStyle(PressableStyle())
                        .accessibilityLabel(GoalLabels.format("action_restore", item.label))
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.vertical, 6)
            .transition(.move(edge: .top).combined(with: .opacity))
        }
    }
}

/// Disposition en lignes qui passent à la ligne (puces).
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = arrange(width: proposal.width ?? .infinity, subviews: subviews)
        let height = rows.map(\.height).reduce(0, +) + spacing * CGFloat(max(rows.count - 1, 0))
        let width = rows.map(\.width).max() ?? 0
        return CGSize(width: proposal.width ?? width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for row in arrange(width: bounds.width, subviews: subviews) {
            var x = bounds.minX
            for index in row.indices {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
                x += size.width + spacing
            }
            y += row.height + spacing
        }
    }

    private struct Row {
        var indices: [Int] = []
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    private func arrange(width: CGFloat, subviews: Subviews) -> [Row] {
        var rows: [Row] = []
        var current = Row()
        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(.unspecified)
            let needed = current.indices.isEmpty ? size.width : current.width + spacing + size.width
            if needed > width && !current.indices.isEmpty {
                rows.append(current)
                current = Row()
            }
            current.width = current.indices.isEmpty ? size.width : current.width + spacing + size.width
            current.height = max(current.height, size.height)
            current.indices.append(index)
        }
        if !current.indices.isEmpty { rows.append(current) }
        return rows
    }
}

/// Fond plein écran en dégradé de la couleur d'un objectif, avec halo doux (détail, formulaire).
struct GoalBackdrop: View {
    let base: Color
    var darken: Double = 0.65
    /// Position du halo (0–1).
    var halo: UnitPoint = UnitPoint(x: 0.5, y: 0.22)

    var body: some View {
        ZStack {
            base
            GoalTint.sheen(darken: darken)
            RadialGradient(colors: [.white.opacity(0.16), .clear], center: halo, startRadius: 0, endRadius: 420)
        }
    }
}
