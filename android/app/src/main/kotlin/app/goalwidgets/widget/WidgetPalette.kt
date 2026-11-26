package app.goalwidgets.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.ContentScale
import androidx.glance.text.FontFamily
import androidx.glance.unit.ColorProvider
import app.goalwidgets.core.GoalStyle
import app.goalwidgets.core.WidgetTheme
import app.goalwidgets.ui.GoalColors
import app.goalwidgets.ui.GoalTint

/**
 * Couleurs d'un widget selon son habillage ([WidgetTheme]) et la couleur de l'objectif.
 * - Vif : dégradé de la couleur, tout en blanc ;
 * - Doux : surface du thème système (suit clair/sombre et Material You), accents de la couleur ;
 * - Nuit : fond presque noir, graphiques et nombre lumineux de la couleur.
 */
internal class WidgetPalette(
    val theme: WidgetTheme,
    /** Titre. */
    val primary: ColorProvider,
    /** Légendes, dates. */
    val secondary: ColorProvider,
    /** Grand nombre. */
    val number: ColorProvider,
    /** Teinte des graphiques (masques ALPHA_8). */
    val accent: ColorProvider,
    /** Pastilles (bouton ↺, badge emoji, pourcentage). */
    val chip: ColorProvider,
    /** Bouton « Confirmer ? » armé : fond et texte. */
    val armedBg: ColorProvider,
    val armedFg: ColorProvider,
    /** Fond uni du thème « Doux » (surface du widget selon le système) ; `null` = bitmap de dégradé. */
    private val surface: ColorProvider? = null,
) {
    /** Fond du widget : bitmap de dégradé (Vif, Nuit) ou couleur du thème système (Doux). */
    fun background(modifier: GlanceModifier, style: GoalStyle, widthDp: Float, heightDp: Float): GlanceModifier =
        if (surface != null) {
            modifier.background(surface)
        } else {
            modifier.background(ImageProvider(WidgetBitmaps.background(style, widthDp, heightDp, theme)), ContentScale.FillBounds)
        }

    companion object {
        /** Grands nombres : condensé gras, plus d'impact et plus de chiffres dans la largeur. */
        val NUMBER_FONT = FontFamily("sans-serif-condensed")

        @Composable
        fun of(theme: WidgetTheme, style: GoalStyle): WidgetPalette {
            val white = ColorProvider(Color.White)
            fun whiteAlpha(alpha: Float) = ColorProvider(Color.White.copy(alpha = alpha))
            return when (theme) {
                WidgetTheme.VIVID -> WidgetPalette(
                    theme = theme,
                    primary = whiteAlpha(0.94f),
                    secondary = whiteAlpha(0.8f),
                    number = white,
                    accent = white,
                    chip = whiteAlpha(0.22f),
                    armedBg = white,
                    armedFg = ColorProvider(Color(0xFF1C1B1F)),
                )
                WidgetTheme.SOFT -> {
                    val accent = style.color
                        ?.let { ColorProvider(day = GoalColors.light(it), night = GoalColors.dark(it)) }
                        ?: GlanceTheme.colors.primary
                    WidgetPalette(
                        theme = theme,
                        primary = GlanceTheme.colors.onSurface,
                        secondary = GlanceTheme.colors.onSurfaceVariant,
                        number = accent,
                        accent = accent,
                        chip = GlanceTheme.colors.secondaryContainer,
                        armedBg = accent,
                        armedFg = ColorProvider(day = Color.White, night = Color(0xFF1C1B1F)),
                        surface = GlanceTheme.colors.widgetBackground,
                    )
                }
                WidgetTheme.MIDNIGHT -> {
                    val glow = ColorProvider(GoalTint.accent(style, dark = true))
                    WidgetPalette(
                        theme = theme,
                        primary = whiteAlpha(0.9f),
                        secondary = whiteAlpha(0.6f),
                        number = white,
                        accent = glow,
                        chip = whiteAlpha(0.12f),
                        armedBg = glow,
                        armedFg = ColorProvider(Color(WidgetBitmaps.MIDNIGHT_BASE)),
                    )
                }
            }
        }
    }
}
