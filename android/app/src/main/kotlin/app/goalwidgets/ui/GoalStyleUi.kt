package app.goalwidgets.ui

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import app.goalwidgets.R
import app.goalwidgets.core.GoalColor

/**
 * Teintes de la palette [GoalColor] : une teinte foncée pour le thème clair, une claire pour le
 * thème sombre (contraste suffisant sur les surfaces Material dans les deux cas). Partagées par
 * la liste et le widget.
 */
object GoalColors {
    fun light(color: GoalColor): Color = Color(palette(color).first)
    fun dark(color: GoalColor): Color = Color(palette(color).second)
    fun of(color: GoalColor, darkTheme: Boolean): Color = if (darkTheme) dark(color) else light(color)

    private fun palette(color: GoalColor): Pair<Long, Long> = when (color) {
        GoalColor.RED -> 0xFFC62828 to 0xFFEF9A9A
        GoalColor.ORANGE -> 0xFFE65100 to 0xFFFFB74D
        GoalColor.YELLOW -> 0xFF8D6E00 to 0xFFFFE082
        GoalColor.GREEN -> 0xFF2E7D32 to 0xFFA5D6A7
        GoalColor.TEAL -> 0xFF00796B to 0xFF80CBC4
        GoalColor.BLUE -> 0xFF1565C0 to 0xFF90CAF9
        GoalColor.INDIGO -> 0xFF3949AB to 0xFF9FA8DA
        GoalColor.PURPLE -> 0xFF7B1FA2 to 0xFFCE93D8
        GoalColor.PINK -> 0xFFC2185B to 0xFFF48FB1
        GoalColor.GRAY -> 0xFF616161 to 0xFFBDBDBD
    }

    /** Nom lisible (accessibilité du sélecteur). */
    @StringRes
    fun name(color: GoalColor): Int = when (color) {
        GoalColor.RED -> R.string.goal_color_red
        GoalColor.ORANGE -> R.string.goal_color_orange
        GoalColor.YELLOW -> R.string.goal_color_yellow
        GoalColor.GREEN -> R.string.goal_color_green
        GoalColor.TEAL -> R.string.goal_color_teal
        GoalColor.BLUE -> R.string.goal_color_blue
        GoalColor.INDIGO -> R.string.goal_color_indigo
        GoalColor.PURPLE -> R.string.goal_color_purple
        GoalColor.PINK -> R.string.goal_color_pink
        GoalColor.GRAY -> R.string.goal_color_gray
    }
}

/** Titre précédé de l'emoji éventuel : « 🎓 Examen ». */
fun titleWithEmoji(title: String, emoji: String?): String = if (emoji.isNullOrBlank()) title else "$emoji $title"

/** Emojis proposés dans le formulaire (un emoji libre peut aussi être saisi). */
val SUGGESTED_EMOJIS = listOf(
    "🎯", "🎓", "✈️", "🏖️", "🎂", "💍", "❤️", "👶",
    "🏃", "💪", "🚭", "🍷", "💼", "🏠", "📚", "🎉",
)

/** Longueur maximale (unités UTF-16) d'un emoji saisi librement : un emoji composé tient dedans. */
const val EMOJI_MAX_LENGTH = 16
