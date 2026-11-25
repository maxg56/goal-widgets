// Non vérifié : écrit sans Xcode (machine Linux).
import SwiftUI
import GoalCore

/// Pastilles de couleur du formulaire : « aucune » (couleur du thème) + les 10 couleurs nommées.
struct ColorSwatchPicker: View {
    @Binding var selection: GoalColor?

    private let columns = [GridItem(.adaptive(minimum: 40), spacing: 12)]

    var body: some View {
        LazyVGrid(columns: columns, spacing: 12) {
            swatch(nil)
            ForEach(GoalColor.allCases, id: \.self) { color in
                swatch(color)
            }
        }
        .padding(.vertical, 4)
    }

    private func swatch(_ color: GoalColor?) -> some View {
        let isSelected = selection == color
        return Button {
            selection = color
        } label: {
            ZStack {
                if let color {
                    Circle().fill(GoalAppearance.color(color))
                } else {
                    Circle().strokeBorder(.secondary, lineWidth: 1.5)
                    Image(systemName: "slash.circle")
                        .foregroundStyle(.secondary)
                }
                if isSelected {
                    Image(systemName: "checkmark")
                        .font(.caption.bold())
                        .foregroundStyle(color == nil ? Color.primary : Color.white)
                }
            }
            .frame(width: 36, height: 36)
            .overlay {
                if isSelected {
                    Circle().strokeBorder(Color.primary, lineWidth: 2).padding(-4)
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(color.map(GoalAppearance.nameKey) ?? LocalizedStringKey("goal_color_none"))
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

/// Emojis suggérés, « aucun », ou un emoji libre saisi au clavier (≤ 16 unités UTF-16, comme Android).
struct EmojiPicker: View {
    @Binding var selection: String

    private let columns = [GridItem(.adaptive(minimum: 40), spacing: 8)]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            LazyVGrid(columns: columns, spacing: 8) {
                choice(nil)
                ForEach(GoalStyle.suggestedEmojis, id: \.self) { emoji in
                    choice(emoji)
                }
            }
            TextField("goal_form_emoji_custom", text: customBinding)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
        }
        .padding(.vertical, 4)
    }

    /// Le champ libre n'affiche que ce qui n'est pas une suggestion.
    private var customBinding: Binding<String> {
        Binding(
            get: { GoalStyle.suggestedEmojis.contains(selection) ? "" : selection },
            set: { newValue in
                if newValue.utf16.count <= GoalStyle.emojiMaxLength {
                    selection = newValue
                }
            }
        )
    }

    private func choice(_ emoji: String?) -> some View {
        let isSelected = emoji.map { $0 == selection }
            ?? (GoalStyle.normalizedEmoji(selection) == nil)
        return Button {
            selection = emoji ?? ""
        } label: {
            Group {
                if let emoji {
                    Text(emoji).font(.title2)
                } else {
                    Image(systemName: "xmark").foregroundStyle(.secondary)
                }
            }
            .frame(width: 40, height: 40)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .fill(isSelected ? Color.accentColor.opacity(0.2) : Color.clear)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .strokeBorder(isSelected ? Color.accentColor : Color.secondary.opacity(0.3), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel(emoji.map { LocalizedStringKey($0) } ?? LocalizedStringKey("goal_emoji_none"))
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}
