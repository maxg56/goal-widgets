package app.goalwidgets.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goalwidgets.core.GoalColor
import app.goalwidgets.core.GoalStyle
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Ressorts communs : tout le mouvement de l'app est doux (léger rebond, pas de durée fixe). */
object Motion {
    fun <T> soft(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
    fun <T> bouncy(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)
    fun <T> slow(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)
    val slide: FiniteAnimationSpec<IntOffset> = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)
}

/**
 * Couleurs d'un objectif. [deepColor] = teinte saturée des fonds (cartes, détail, widget), lisible sous
 * du texte blanc dans les deux thèmes ; sans couleur choisie, un gris-bleu neutre.
 */
object GoalTint {
    private val NEUTRAL_MIX = Color(0xFF37474F)

    fun deepColor(style: GoalStyle): Color =
        style.color?.let { GoalColors.light(it) } ?: lerp(GoalColors.light(GoalColor.INDIGO), NEUTRAL_MIX, 0.6f)

    /** Accent sur une surface claire/sombre (graphiques, frise). */
    fun accent(style: GoalStyle, dark: Boolean): Color =
        style.color?.let { GoalColors.of(it, dark) } ?: if (dark) Color(0xFFB0BEC5) else deepColor(style)

    /** Dégradé doux d'un fond : léger éclat en haut, plus sombre en bas. */
    fun gradient(base: Color, darken: Float = 0.45f): List<Color> =
        listOf(lerp(base, Color.White, 0.12f), base, lerp(base, Color.Black, darken))
}

/** Taille du grand chiffre selon sa longueur (« 12345 » doit tenir à l'écran). */
fun bigNumberSize(text: String, max: Int): TextUnit = when (text.length) {
    in 0..2 -> max
    3 -> max * 82 / 100
    4 -> max * 66 / 100
    else -> max * 54 / 100
}.sp

/** Formats de date dans la langue du téléphone. */
object DateFormats {
    val long: DateTimeFormatter get() = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
    val medium: DateTimeFormatter get() = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    fun pattern(pattern: String): DateTimeFormatter = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
    val dayMonth: DateTimeFormatter get() = pattern("d MMM")
    val monthShort: DateTimeFormatter get() = pattern("MMM")
    val monthYear: DateTimeFormatter get() = pattern("MMM yyyy")
    val weekdayLong: DateTimeFormatter get() = pattern("EEE d MMMM yyyy")
    val weekdayDayMonth: DateTimeFormatter get() = pattern("EEEE d MMMM")
}

fun LocalDate.formatCapitalized(formatter: DateTimeFormatter): String =
    format(formatter).replaceFirstChar { it.titlecase(Locale.getDefault()) }

/** Entrée décalée : fondu + glissement en ressort, retardée selon [index] ; rien si [index] est null. */
@Composable
fun Modifier.staggeredEnter(index: Int?, dx: Dp = 0.dp, dy: Dp = 28.dp): Modifier {
    if (index == null) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(70L * index.coerceAtMost(8))
        progress.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessVeryLow))
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationX = (1 - p) * dx.toPx()
        translationY = (1 - p) * dy.toPx()
    }
}

/** Révélation 0 → 1 en ressort, relancée quand [active] devient vrai (graphiques). */
@Composable
fun rememberReveal(active: Boolean, damping: Float = 0.7f): Float {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) reveal.animateTo(1f, spring(dampingRatio = damping, stiffness = Spring.StiffnessVeryLow))
    }
    return reveal.value
}
