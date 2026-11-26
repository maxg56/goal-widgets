package app.goalwidgets.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goalwidgets.R

/*
 * Personnalisation « façon écran d'accueil » (détail et configuration du widget) : les blocs tremblent,
 * un petit × rond les masque, la rangée « Masqués » les remet avec un +.
 */

/** Léger tremblement tant que [active] ; [seed] décale la phase d'un bloc à l'autre. */
@Composable
fun Modifier.wobble(active: Boolean, seed: Int, amplitude: Float = 1.1f): Modifier {
    if (!active) return this
    // Lu dans graphicsLayer seulement : pas de recomposition à chaque image.
    val t = rememberInfiniteTransition(label = "wobble").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 130 + (seed % 3) * 18), RepeatMode.Reverse),
        label = "wobbleAngle",
    )
    return graphicsLayer {
        rotationZ = t.value * amplitude
        translationY = t.value * (seed % 2 * 2 - 1) * 0.6f * density
    }
}

/** Bloc masquable : tremble en mode personnalisation, × en haut à droite. */
@Composable
fun EditableBlock(
    editing: Boolean,
    seed: Int,
    label: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    badgeOffset: Dp = 6.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.wobble(editing, seed)) {
        content()
        AnimatedVisibility(
            visible = editing,
            enter = scaleIn(spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).offset(x = badgeOffset, y = -badgeOffset),
        ) { RemoveBadge(label, onRemove) }
    }
}

@Composable
fun RemoveBadge(label: String, onClick: () -> Unit, size: Dp = 26.dp) {
    val description = stringResource(R.string.action_hide, label)
    Box(
        Modifier
            .size(size)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(Color(0xFF26262B))
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.Close, contentDescription = description, tint = Color.White, modifier = Modifier.size(size * 0.6f)) }
}

/** Rangée « Masqués » : un + par élément retiré. Visible si [visible] et s'il y a quelque chose à remettre. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HiddenRow(
    visible: Boolean,
    items: List<Pair<String, () -> Unit>>,
    fg: Color,
    chipBg: Color,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible && items.isNotEmpty(),
        enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
        exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(),
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(stringResource(R.string.detail_hidden_title), color = fg.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items.forEach { (label, restore) ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(chipBg)
                            .clickable(onClickLabel = stringResource(R.string.action_restore, label), onClick = restore)
                            .padding(start = 8.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(20.dp).clip(CircleShape).background(fg.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
