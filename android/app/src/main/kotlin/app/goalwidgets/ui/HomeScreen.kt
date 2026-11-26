package app.goalwidgets.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goalwidgets.R
import app.goalwidgets.core.CountdownEngine
import app.goalwidgets.core.GoalKind
import app.goalwidgets.core.GoalOrder
import kotlinx.coroutines.delay
import java.time.LocalDate

/** Actions de l'accueil : ouverture d'un objectif, création, sous-écrans et menu ⋮. */
class HomeActions(
    val onOpen: (StoredGoal) -> Unit,
    val onAdd: () -> Unit,
    val onOpenTimeline: () -> Unit,
    val onOpenNotifications: () -> Unit,
    val onOrderChange: (GoalOrder) -> Unit,
    val onExport: () -> Unit,
    val onImport: () -> Unit,
)

/**
 * Accueil « Mes objectifs » : cartes compactes en dégradé de la couleur de l'objectif, grand chiffre,
 * anneau de progression ; appui → détail (transition partagée [sharedCard]). En-tête : frise de tous
 * les objectifs, réglages des notifications, menu ⋮ (tri, export, import).
 */
@Composable
fun HomeScreen(
    goals: List<StoredGoal>,
    today: LocalDate,
    order: GoalOrder,
    listState: LazyListState,
    /** Entrée décalée des cartes (premier affichage seulement). */
    animateEntrance: Boolean,
    sharedCard: @Composable (goalId: Long) -> Modifier,
    snackbarHostState: SnackbarHostState,
    actions: HomeActions,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.goal_list_title),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        pluralStringResource(R.plurals.home_goal_count, goals.size, goals.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(onClick = actions.onOpenTimeline) {
                    Icon(Icons.Filled.DateRange, contentDescription = stringResource(R.string.timeline_open))
                }
                IconButton(onClick = actions.onOpenNotifications) {
                    Icon(Icons.Filled.Notifications, contentDescription = stringResource(R.string.notifications_settings_title))
                }
                HomeMenu(order, actions)
            }
            if (goals.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.goal_list_empty), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.home_empty_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(goals, key = { _, g -> g.id }) { index, stored ->
                        HeroCard(
                            stored = stored,
                            today = today,
                            enterIndex = index.takeIf { animateEntrance },
                            onClick = { actions.onOpen(stored) },
                            modifier = Modifier.animateItem().then(sharedCard(stored.id)),
                        )
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = actions.onAdd,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.goal_add)) },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp),
        )
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp))
    }
}

/** Menu ⋮ : tri, export et import. */
@Composable
private fun HomeMenu(order: GoalOrder, actions: HomeActions) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                text = stringResource(R.string.goal_sort_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            listOf(
                GoalOrder.CLOSEST_DATE to R.string.goal_sort_closest_date,
                GoalOrder.CREATED to R.string.goal_sort_created,
            ).forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = { open = false; actions.onOrderChange(value) },
                    leadingIcon = { if (order == value) Icon(Icons.Filled.Check, contentDescription = null) },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text(stringResource(R.string.action_export)) }, onClick = { open = false; actions.onExport() })
            DropdownMenuItem(text = { Text(stringResource(R.string.action_import)) }, onClick = { open = false; actions.onImport() })
        }
    }
}

@Composable
private fun HeroCard(stored: StoredGoal, today: LocalDate, enterIndex: Int?, onClick: () -> Unit, modifier: Modifier) {
    val resources = LocalContext.current.resources
    val goal = stored.goal
    val state = CountdownEngine.compute(goal, today)
    val base = GoalTint.deepColor(stored.style)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), label = "press")
    // L'anneau se remplit après l'entrée de la carte.
    var ringShown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(150L + 70L * (enterIndex ?: 0).coerceAtMost(8)); ringShown = true }
    val emoji = stored.style.emoji?.takeIf { it.isNotBlank() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .staggeredEnter(enterIndex)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(GoalTint.gradient(base), start = Offset.Zero, end = Offset(900f, 600f)))
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick),
    ) {
        // Emoji en filigrane, débordant du coin.
        emoji?.let {
            Text(
                text = it,
                fontSize = 84.sp,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 20.dp, y = (-24).dp).graphicsLayer { alpha = 0.14f },
            )
        }
        // Carte compacte : emoji, titre + chiffre + légende, anneau à droite.
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            emoji?.let {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) { Text(it, fontSize = 20.sp) }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = goal.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val number = GoalLabels.bigNumber(resources, state)
                Row {
                    Text(
                        text = number,
                        color = Color.White,
                        fontSize = bigNumberSize(number, 40),
                        lineHeight = bigNumberSize(number, 40),
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1).sp,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        GoalLabels.caption(resources, state),
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
                val detail = GoalLabels.breakdown(resources, state.breakdown)
                    ?: when (goal.kind) {
                        GoalKind.COUNTDOWN -> goal.targetDate?.let { stringResource(R.string.goal_date_countdown, it.format(DateFormats.long)) }
                        GoalKind.SINCE -> stringResource(R.string.goal_date_since, goal.startDate.format(DateFormats.long))
                    }
                detail?.let {
                    Text(
                        it,
                        color = Color.White.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            state.progress?.let { progress ->
                Spacer(Modifier.width(12.dp))
                CardRing(progress = if (ringShown) progress.toFloat() else 0f, percent = state.percent ?: 0)
            }
        }
    }
}

/** Anneau de la carte : l'arc suit la progression en ressort, le pourcentage compte avec lui. */
@Composable
private fun CardRing(progress: Float, percent: Int) {
    val resources = LocalContext.current.resources
    val animated by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessVeryLow),
        label = "ring",
    )
    val settled = progress > 0f && kotlin.math.abs(animated - progress) < 0.005f
    Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
        MiniRing(animated.coerceAtLeast(0f), Modifier.fillMaxSize(), stroke = 6.dp)
        Text(
            text = GoalLabels.percent(resources, if (settled) percent else (animated * 100).toInt().coerceIn(0, percent)),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}
