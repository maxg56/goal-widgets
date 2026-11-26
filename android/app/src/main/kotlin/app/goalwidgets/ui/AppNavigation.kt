package app.goalwidgets.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import app.goalwidgets.core.Goal
import app.goalwidgets.core.GoalOrder
import app.goalwidgets.core.GoalStyle
import java.time.LocalDate
import java.time.LocalTime

/** Écrans de l'app ; la pile est sauvegardée en texte (rotation, mort du processus). */
sealed interface Screen {
    data object Home : Screen
    data object Timeline : Screen
    data object NotificationSettings : Screen

    /** Détail ouvert sur [goalId] (le pager peut ensuite passer aux voisins). */
    data class Detail(val goalId: Long) : Screen

    /** Formulaire : création ([goalId] = null) ou modification. */
    data class Form(val goalId: Long?) : Screen

    companion object {
        fun encode(screen: Screen): String = when (screen) {
            Home -> "home"
            Timeline -> "timeline"
            NotificationSettings -> "notifications"
            is Detail -> "detail:${screen.goalId}"
            is Form -> "form:${screen.goalId ?: "new"}"
        }

        fun decode(raw: String): Screen? = when {
            raw == "home" -> Home
            raw == "timeline" -> Timeline
            raw == "notifications" -> NotificationSettings
            raw.startsWith("detail:") -> raw.removePrefix("detail:").toLongOrNull()?.let(::Detail)
            raw == "form:new" -> Form(null)
            raw.startsWith("form:") -> raw.removePrefix("form:").toLongOrNull()?.let { Form(it) }
            else -> null
        }
    }
}

private val StackSaver = Saver<List<Screen>, ArrayList<String>>(
    save = { ArrayList(it.map(Screen::encode)) },
    restore = { saved -> saved.mapNotNull(Screen::decode).ifEmpty { listOf(Screen.Home) } },
)

/**
 * Demande d'ouverture d'un objectif (widget, notification, objectif partagé reçu, raccourci) ou du
 * formulaire de création ([goalId] = null, raccourci « Nouvel objectif ») ; [serial] distingue deux appuis.
 */
data class OpenGoalRequest(val goalId: Long?, val serial: Int)

/** Écritures et actions système, fournies par l'activité. */
class AppActions(
    val add: (Goal, GoalStyle, GoalOptions) -> Unit,
    val update: (Long, Goal, GoalStyle, GoalOptions) -> Unit,
    /** « Dupliquer » : copie de l'objectif avec le titre donné. */
    val duplicate: (StoredGoal, String) -> Unit,
    /** Archive un objectif (ou le sort de l'archive). */
    val setArchived: (Long, Boolean) -> Unit,
    /** Remise à zéro d'un compteur « série » à la date donnée (aujourd'hui par défaut). */
    val resetStreak: (Long, LocalDate) -> Unit,
    val delete: (Long) -> Unit,
    val continueAsSince: (StoredGoal) -> Unit,
    val setOrder: (GoalOrder) -> Unit,
    val export: () -> Unit,
    val import: () -> Unit,
    val share: (Long) -> Unit,
    /** « Partager en image » (carte PNG). */
    val shareImage: (Long) -> Unit,
    val setSendTime: (LocalTime) -> Unit,
)

@OptIn(ExperimentalSharedTransitionApi::class)
private val cardBounds = BoundsTransform { _, _ -> spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow) }

private fun cardKey(id: Long) = "card-$id"

/**
 * Navigation de l'app : accueil en cartes → détail (transition partagée carte ↔ plein écran),
 * frise de tous les objectifs, formulaire et réglages des notifications en sous-écrans.
 * [openRequest] (widget, notification) ouvre le détail de l'objectif — après le formulaire s'il est
 * en cours, pour ne pas perdre la saisie.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GoalWidgetsApp(
    goals: List<StoredGoal>?,
    order: GoalOrder,
    today: LocalDate,
    sendTime: LocalTime,
    snackbarHostState: SnackbarHostState,
    openRequest: OpenGoalRequest?,
    onOpenRequestHandled: () -> Unit,
    actions: AppActions,
) {
    var stack by rememberSaveable(stateSaver = StackSaver) { mutableStateOf(listOf<Screen>(Screen.Home)) }
    // Objectif affiché par le pager du détail : cible de la transition retour vers sa carte.
    var focusedGoalId by rememberSaveable { mutableStateOf<Long?>(null) }
    var homeAnimated by rememberSaveable { mutableStateOf(false) }
    val homeListState = rememberLazyListState()
    // Objectifs archivés à la fin (même ordre entre eux) : section repliable de l'accueil.
    val sorted = remember(goals, order, today) { goals?.let { order.sort(it, today) { g -> g.sortKey }.sortedBy(StoredGoal::archived) } }
    var showArchived by rememberSaveable { mutableStateOf(false) }

    if (sorted == null) {
        // Base pas encore lue (quelques ms) : fond seul.
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    fun exists(id: Long?) = id != null && sorted.any { it.id == id }
    // Objectif supprimé entre-temps : son détail / son formulaire disparaissent de la pile.
    val valid = stack.filter {
        when (it) {
            is Screen.Detail -> exists(it.goalId)
            is Screen.Form -> it.goalId == null || exists(it.goalId)
            else -> true
        }
    }.ifEmpty { listOf(Screen.Home) }
    val screen = valid.last()

    fun push(s: Screen) {
        if (s is Screen.Detail) focusedGoalId = s.goalId
        stack = valid + s
    }
    fun pop() {
        val leaving = valid.last()
        val next = valid.dropLast(1).ifEmpty { listOf(Screen.Home) }
        if (leaving is Screen.Detail && next.last() == Screen.Home) {
            // La carte de l'objectif affiché doit être visible pour que la transition la retrouve.
            // Index dans la liste de l'accueil : actifs, en-tête « Archivés », archivés.
            val position = sorted.indexOfFirst { it.id == focusedGoalId }
            val index = if (position >= 0 && sorted[position].archived) position + 1 else position
            val visible = homeListState.layoutInfo.visibleItemsInfo.map { it.index }
            if (index >= 0 && index !in visible) homeListState.requestScrollToItem(index)
        }
        stack = next
    }

    LaunchedEffect(openRequest, screen is Screen.Form) {
        val request = openRequest ?: return@LaunchedEffect
        if (screen is Screen.Form) return@LaunchedEffect // repris à la fermeture du formulaire
        val id = request.goalId
        if (id == null) {
            stack = listOf(Screen.Home, Screen.Form(null))
        } else if (exists(id)) {
            focusedGoalId = id
            stack = listOf(Screen.Home, Screen.Detail(id))
        }
        onOpenRequestHandled()
    }

    BackHandler(enabled = valid.size > 1) { pop() }
    SystemBarIcons(lightIcons = screen is Screen.Detail || screen is Screen.Form || isSystemInDarkTheme())

    SharedTransitionLayout(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                val from = initialState
                val to = targetState
                val slide = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)
                when {
                    // Carte ↔ détail : la carte s'agrandit (sharedBounds), le reste fond enchaîné.
                    from == Screen.Home && to is Screen.Detail -> fadeIn(tween(260, delayMillis = 60)) togetherWith fadeOut(tween(220))
                    from is Screen.Detail && to == Screen.Home -> fadeIn(tween(260)) togetherWith fadeOut(tween(220, delayMillis = 40))
                    // Sous-écrans modaux : montent du bas, redescendent au retour.
                    to is Screen.Form || to == Screen.NotificationSettings ->
                        (slideInVertically(slide) { it / 3 } + fadeIn()) togetherWith (fadeOut() + scaleOut(Motion.soft(), targetScale = 0.96f))
                    from is Screen.Form || from == Screen.NotificationSettings ->
                        (fadeIn() + scaleIn(Motion.soft(), initialScale = 0.96f)) togetherWith (slideOutVertically(slide) { it / 3 } + fadeOut())
                    from == Screen.Home && to == Screen.Timeline ->
                        (slideInHorizontally(slide) { it } + fadeIn()) togetherWith (slideOutHorizontally(slide) { -it / 4 } + fadeOut())
                    from == Screen.Timeline && to == Screen.Home ->
                        (slideInHorizontally(slide) { -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally(slide) { it } + fadeOut())
                    // Frise → détail (pas de carte d'origine) : zoom + fondu en ressort.
                    to is Screen.Detail ->
                        (scaleIn(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow), initialScale = 0.88f) + fadeIn()) togetherWith fadeOut()
                    else -> fadeIn() togetherWith (scaleOut(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium), targetScale = 0.9f) + fadeOut())
                }
            },
            label = "navigation",
        ) { s ->
            val visibility: AnimatedVisibilityScope = this
            val shared: @Composable (Long) -> Modifier = { id -> sharedCardModifier(id, visibility) }
            when (s) {
                Screen.Home -> {
                    val animate = remember { !homeAnimated }
                    LaunchedEffect(Unit) { homeAnimated = true }
                    HomeScreen(
                        goals = sorted,
                        today = today,
                        order = order,
                        listState = homeListState,
                        animateEntrance = animate,
                        showArchived = showArchived,
                        onShowArchivedChange = { showArchived = it },
                        sharedCard = shared,
                        snackbarHostState = snackbarHostState,
                        actions = HomeActions(
                            onOpen = { push(Screen.Detail(it.id)) },
                            onAdd = { push(Screen.Form(null)) },
                            onOpenTimeline = { push(Screen.Timeline) },
                            onOpenNotifications = { push(Screen.NotificationSettings) },
                            onOrderChange = actions.setOrder,
                            onResetStreak = actions.resetStreak,
                            onExport = actions.export,
                            onImport = actions.import,
                        ),
                    )
                }
                Screen.Timeline -> GoalTimelineScreen(sorted.filterNot(StoredGoal::archived), today, onOpen = { push(Screen.Detail(it.id)) }, onBack = ::pop)
                Screen.NotificationSettings -> NotificationSettingsScreen(sendTime, actions.setSendTime, onBack = ::pop)
                is Screen.Detail -> GoalDetailScreen(
                    goals = sorted,
                    today = today,
                    initialGoalId = focusedGoalId?.takeIf(::exists) ?: s.goalId,
                    sharedCard = shared,
                    onFocusChange = { focusedGoalId = it },
                    onBack = ::pop,
                    onEdit = { push(Screen.Form(it.id)) },
                    onShare = { actions.share(it.id) },
                    onShareImage = { actions.shareImage(it.id) },
                    onResetStreak = actions.resetStreak,
                )
                is Screen.Form -> {
                    val existing = s.goalId?.let { id -> sorted.firstOrNull { it.id == id } }
                    GoalFormScreen(
                        existing = existing,
                        today = today,
                        onSave = { goal, style, options ->
                            if (existing == null) actions.add(goal, style, options) else actions.update(existing.id, goal, style, options)
                            pop()
                        },
                        onArchive = existing?.let { e ->
                            {
                                actions.setArchived(e.id, !e.archived)
                                if (!e.archived) {
                                    // Archivé : retour à l'accueil, section « Archivés » ouverte.
                                    showArchived = true
                                    stack = listOf(Screen.Home)
                                } else {
                                    pop()
                                }
                            }
                        },
                        onDuplicate = existing?.let { e ->
                            { title: String ->
                                actions.duplicate(e, title)
                                // Retour à l'accueil : la copie y apparaît.
                                stack = listOf(Screen.Home)
                            }
                        },
                        onCancel = ::pop,
                        onDelete = {
                            existing?.id?.let { id ->
                                actions.delete(id)
                                stack = valid.filterNot { it == Screen.Detail(id) || it == Screen.Form(id) }.ifEmpty { listOf(Screen.Home) }
                            }
                        },
                        onContinueAsSince = { existing?.let(actions.continueAsSince); pop() },
                        onShare = existing?.let { e -> { actions.share(e.id) } },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.sharedCardModifier(id: Long, visibility: AnimatedVisibilityScope): Modifier =
    Modifier.sharedBounds(
        sharedContentState = rememberSharedContentState(cardKey(id)),
        animatedVisibilityScope = visibility,
        boundsTransform = cardBounds,
        clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(32.dp)),
    )

/** Icônes de la barre d'état claires sur les écrans à fond coloré (détail, formulaire) et en thème sombre. */
@Composable
private fun SystemBarIcons(lightIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !lightIcons
    }
}
