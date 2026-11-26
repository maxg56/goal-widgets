package app.goalwidgets.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.goalwidgets.R
import app.goalwidgets.core.Breakdown
import app.goalwidgets.core.ChartUnit
import app.goalwidgets.core.CountdownEngine
import app.goalwidgets.core.DraftError
import app.goalwidgets.core.Goal
import app.goalwidgets.core.GoalColor
import app.goalwidgets.core.GoalDraft
import app.goalwidgets.core.GoalKind
import app.goalwidgets.core.GoalStyle
import app.goalwidgets.core.Phase
import app.goalwidgets.core.RingChart
import app.goalwidgets.notifications.MilestoneNotifications
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.min
import kotlin.math.sin

private enum class DateField { START, TARGET }
private enum class Confirm { DELETE, SINCE }

/**
 * Formulaire de création ([existing] = null) ou de modification : héros dans la couleur choisie avec
 * titre éditable et aperçu en direct, type en bascule, dates en cartes + feuille calendrier (suggestions,
 * durée), couleur, emoji, notifications, bouton collant au-dessus du clavier. En modification :
 * « Partager », « Continuer en compteur depuis » (cible dépassée) et « Supprimer », confirmés.
 * Validation par [GoalDraft] ; les erreurs n'apparaissent qu'après une tentative d'enregistrement.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GoalFormScreen(
    existing: StoredGoal?,
    today: LocalDate,
    /** Objectif validé, son style, l'état des notifications et l'option « série ». */
    onSave: (Goal, GoalStyle, Boolean, Boolean) -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit = {},
    onContinueAsSince: () -> Unit = {},
    /** « Partager » (objectif enregistré seulement). */
    onShare: (() -> Unit)? = null,
) {
    val initial = existing?.goal
    var kind by rememberSaveable { mutableStateOf(initial?.kind ?: GoalKind.COUNTDOWN) }
    var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }
    var startDate by rememberSaveable { mutableStateOf(initial?.startDate ?: today) }
    var targetDate by rememberSaveable { mutableStateOf(initial?.targetDate) }
    var color by rememberSaveable { mutableStateOf(existing?.style?.color) }
    var emoji by rememberSaveable { mutableStateOf(existing?.style?.emoji) }
    // `null` = comme le réglage général.
    var chartUnit by rememberSaveable { mutableStateOf(existing?.style?.chartUnit) }
    // Désactivées par défaut, y compris pour un nouvel objectif (même règle qu'iOS).
    var notify by rememberSaveable { mutableStateOf(existing?.notificationsEnabled ?: false) }
    // Option « série » : n'a d'effet (et n'est proposée) que pour un compteur « depuis ».
    var streak by rememberSaveable { mutableStateOf(existing?.streak ?: false) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf<DateField?>(null) }
    var confirm by rememberSaveable { mutableStateOf<Confirm?>(null) }

    val draft = GoalDraft(kind, title, startDate, targetDate, today)
    val errors = if (attempted) draft.errors() else emptySet()
    val canContinueAsSince = initial != null && initial.kind == GoalKind.COUNTDOWN &&
        CountdownEngine.compute(initial, today).phase == Phase.REACHED
    val style = GoalStyle(color, emoji?.trim()?.takeIf { it.isNotEmpty() }, chartUnit)
    val base by animateColorAsState(GoalTint.deepColor(style), spring(stiffness = Spring.StiffnessLow), label = "base")

    val scope = rememberCoroutineScope()
    val shake = remember { Animatable(0f) }
    val emojiSection = remember { BringIntoViewRequester() }
    fun save() {
        attempted = true
        if (draft.errors().isEmpty()) {
            onSave(draft.toGoal(), style, notify, streak)
        } else {
            scope.launch { for (x in listOf(22f, -18f, 12f, -8f, 4f, 0f)) shake.animateTo(x, tween(45)) }
        }
    }

    BackHandler(onBack = onCancel)
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
            FormHero(
                isNew = existing == null,
                base = base,
                kind = kind,
                title = title,
                onTitleChange = { title = it },
                emoji = style.emoji,
                titleError = DraftError.TITLE_BLANK in errors,
                preview = previewGoal(kind, title, startDate, targetDate),
                today = today,
                onClose = onCancel,
                onShare = if (existing != null) onShare else null,
                onEmojiClick = { scope.launch { emojiSection.bringIntoView() } },
            )
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
                Spacer(Modifier.height(0.dp))
                KindToggle(kind, base) { kind = it }
                DatesCard(kind, startDate, targetDate, today, base, errors) { sheet = it }
                Section(stringResource(R.string.goal_form_color)) { ColorSwatches(color) { color = it } }
                Section(stringResource(R.string.goal_form_emoji), Modifier.bringIntoViewRequester(emojiSection)) {
                    EmojiPicker(style.emoji, emoji, base) { emoji = it }
                }
                Section(stringResource(R.string.goal_form_chart_unit)) { ChartUnitPicker(chartUnit, base) { chartUnit = it } }
                AnimatedVisibility(kind == GoalKind.SINCE, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    StreakCard(streak, base) { streak = it }
                }
                NotificationCard(notify, base) { notify = it }
                if (existing != null) {
                    ManageCard(
                        canContinueAsSince = canContinueAsSince,
                        onContinue = { confirm = Confirm.SINCE },
                        onDelete = { confirm = Confirm.DELETE },
                    )
                }
                // Place pour le bouton collant.
                Spacer(Modifier.navigationBarsPadding().height(110.dp))
            }
        }
        SaveBar(
            label = stringResource(if (existing == null) R.string.form_create else R.string.action_save),
            base = base,
            shake = shake.value,
            onClick = ::save,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    sheet?.let { field ->
        DateSheet(
            field = field,
            kind = kind,
            current = if (field == DateField.TARGET) targetDate else startDate,
            other = if (field == DateField.TARGET) startDate else targetDate,
            today = today,
            base = base,
            onDismiss = { sheet = null },
            onPick = { d ->
                if (field == DateField.TARGET) targetDate = d else startDate = d
                sheet = null
            },
        )
    }
    confirm?.let { what ->
        val delete = what == Confirm.DELETE
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(if (delete) R.string.goal_delete_confirm_title else R.string.form_continue_confirm_title)) },
            text = {
                Text(
                    if (delete) stringResource(R.string.form_delete_confirm_message, initial?.title.orEmpty())
                    else stringResource(R.string.form_continue_confirm_message),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    if (delete) onDelete() else onContinueAsSince()
                }) {
                    Text(
                        stringResource(if (delete) R.string.action_delete else R.string.action_continue),
                        color = if (delete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.action_cancel)) } },
            shape = RoundedCornerShape(28.dp),
        )
    }
}

/** Objectif de l'aperçu : seulement quand les dates sont cohérentes (le titre peut être vide). */
private fun previewGoal(kind: GoalKind, title: String, start: LocalDate, target: LocalDate?): Goal? = when (kind) {
    GoalKind.COUNTDOWN -> target?.takeIf { it.isAfter(start) }?.let { Goal(GoalKind.COUNTDOWN, start, it, title) }
    GoalKind.SINCE -> Goal(GoalKind.SINCE, start, null, title)
}

// --- Héros ---------------------------------------------------------------------------------------

@Composable
private fun FormHero(
    isNew: Boolean,
    base: Color,
    kind: GoalKind,
    title: String,
    onTitleChange: (String) -> Unit,
    emoji: String?,
    titleError: Boolean,
    preview: Goal?,
    today: LocalDate,
    onClose: () -> Unit,
    onShare: (() -> Unit)?,
    onEmojiClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
            .background(Brush.verticalGradient(GoalTint.gradient(base))),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val c = Offset(size.width * 0.85f, size.height * 0.1f)
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), center = c, radius = size.width * 0.7f),
                radius = size.width * 0.7f,
                center = c,
            )
        }
        Column(Modifier.statusBarsPadding().padding(start = 8.dp, end = 8.dp, bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel), tint = Color.White)
                }
                Text(
                    stringResource(if (isNew) R.string.goal_form_new else R.string.goal_form_edit),
                    color = Color.White.copy(alpha = 0.85f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                )
                if (onShare != null) {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share), tint = Color.White)
                    }
                }
            }
            Row(Modifier.padding(horizontal = 12.dp).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Bulle d'emoji : rebondit quand il change ; appui → section Emoji.
                val bounce = remember { Animatable(1f) }
                LaunchedEffect(emoji) {
                    bounce.snapTo(0.6f)
                    bounce.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
                }
                val chooseEmoji = stringResource(R.string.form_choose_emoji)
                Box(
                    Modifier
                        .size(60.dp)
                        .scale(bounce.value)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .clickable(onClickLabel = chooseEmoji, onClick = onEmojiClick),
                    contentAlignment = Alignment.Center,
                ) {
                    if (emoji != null) Text(emoji, fontSize = 30.sp) else Text("+", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
                }
                Spacer(Modifier.width(14.dp))
                val titleLabel = stringResource(R.string.goal_form_title)
                BasicTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    textStyle = TextStyle(color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp),
                    cursorBrush = SolidColor(Color.White),
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.weight(1f).semantics { contentDescription = titleLabel },
                    decorationBox = { inner ->
                        Box {
                            if (title.isEmpty()) {
                                Text(
                                    stringResource(if (kind == GoalKind.COUNTDOWN) R.string.form_title_placeholder_countdown else R.string.form_title_placeholder_since),
                                    color = Color.White.copy(alpha = 0.45f),
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            inner()
                        }
                    },
                )
            }
            AnimatedVisibility(
                visible = titleError,
                enter = expandVertically(spring(dampingRatio = 0.7f)) + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Text(
                    stringResource(R.string.goal_form_error_empty_title),
                    color = base,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(start = 86.dp, top = 8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
            Spacer(Modifier.height(22.dp))
            LivePreview(preview, today, kind, Modifier.padding(horizontal = 12.dp))
        }
    }
}

/** Aperçu en direct : grand nombre qui défile au changement + mini anneau en ressort. */
@Composable
private fun LivePreview(goal: Goal?, today: LocalDate, kind: GoalKind, modifier: Modifier) {
    val resources = LocalContext.current.resources
    val state = goal?.let { CountdownEngine.compute(it, today) }
    val ring = goal?.let { RingChart.compute(it, today) }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            val number = state?.let { GoalLabels.bigNumber(resources, it) } ?: "—"
            AnimatedContent(
                targetState = number,
                transitionSpec = {
                    val up = (targetState.toIntOrNull() ?: 0) >= (initialState.toIntOrNull() ?: 0)
                    (slideInVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) { if (up) it else -it } + fadeIn()) togetherWith
                        (slideOutVertically(spring(stiffness = Spring.StiffnessMediumLow)) { if (up) -it else it } + fadeOut())
                },
                label = "previewNumber",
            ) { n ->
                Text(n, color = Color.White, fontSize = bigNumberSize(n, 72), lineHeight = bigNumberSize(n, 72), fontWeight = FontWeight.Black, letterSpacing = (-2).sp)
            }
            Text(
                when {
                    state != null -> GoalLabels.caption(resources, state)
                    kind == GoalKind.COUNTDOWN -> stringResource(R.string.goal_form_error_target_missing)
                    else -> ""
                },
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            state?.let { GoalLabels.breakdown(resources, it.breakdown) }?.let {
                Text(it, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        }
        val fraction by animateFloatAsState(ring?.fraction?.toFloat() ?: 0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow), label = "ring")
        Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
            MiniRing(fraction, Modifier.fillMaxSize(), track = Color.White.copy(alpha = 0.2f))
            if (ring != null) {
                val texts = ringTexts(ring)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(texts.center, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
                    Text(texts.caption, color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp, maxLines = 1)
                }
            }
        }
    }
}

// --- Type ----------------------------------------------------------------------------------------

@Composable
private fun KindToggle(kind: GoalKind, base: Color, onSelect: (GoalKind) -> Unit) {
    val options = listOf(GoalKind.COUNTDOWN to R.string.goal_kind_countdown, GoalKind.SINCE to R.string.goal_kind_since)
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(5.dp),
    ) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * options.indexOfFirst { it.first == kind }, spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow), label = "kind")
        Box(
            Modifier.offset(x = x).width(w).fillMaxHeight().clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(lerp(base, Color.White, 0.12f), base))),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEach { (value, label) ->
                val selected = value == kind
                val fg by animateColorAsState(if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, spring(stiffness = Spring.StiffnessLow), label = "kindText")
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton) { onSelect(value) }
                        .semantics { this.selected = selected },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(label), color = fg, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 15.sp)
                }
            }
        }
    }
}

// --- Dates ---------------------------------------------------------------------------------------

@Composable
private fun DatesCard(
    kind: GoalKind,
    start: LocalDate,
    target: LocalDate?,
    today: LocalDate,
    base: Color,
    errors: Set<DraftError>,
    onPick: (DateField) -> Unit,
) {
    val accent = if (isSystemInDarkTheme()) lerp(base, Color.White, 0.45f) else base
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp)) {
        AnimatedContent(
            targetState = kind,
            transitionSpec = {
                (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + slideInVertically(spring(dampingRatio = 0.8f)) { it / 6 }) togetherWith fadeOut()
            },
            label = "dates",
        ) { k ->
            Column {
                when (k) {
                    GoalKind.COUNTDOWN -> {
                        DateChip(stringResource(R.string.form_date_start), start, accent, error = false) { onPick(DateField.START) }
                        val days = target?.let { ChronoUnit.DAYS.between(start, it) }
                        Connector(
                            accent = accent,
                            text = when {
                                days == null -> stringResource(R.string.goal_form_error_target_missing)
                                days <= 0 -> stringResource(R.string.goal_form_error_target_not_after_start)
                                else -> durationText(days, Breakdown.between(start, target))
                            },
                            error = days != null && days <= 0,
                        )
                        DateChip(
                            stringResource(R.string.form_date_target),
                            target,
                            accent,
                            error = DraftError.TARGET_MISSING in errors || DraftError.TARGET_NOT_AFTER_START in errors,
                        ) { onPick(DateField.TARGET) }
                    }
                    GoalKind.SINCE -> {
                        DateChip(stringResource(R.string.goal_form_since_date), start, accent, error = DraftError.START_IN_FUTURE in errors) { onPick(DateField.START) }
                        val days = ChronoUnit.DAYS.between(start, today)
                        Connector(
                            accent = accent,
                            text = if (days < 0) stringResource(R.string.goal_form_error_start_in_future)
                            else stringResource(R.string.form_duration_until_today, durationText(days, Breakdown.between(start, today))),
                            error = days < 0,
                        )
                    }
                }
            }
        }
        val message = when {
            DraftError.TARGET_MISSING in errors -> R.string.goal_form_error_target_missing
            DraftError.TARGET_NOT_AFTER_START in errors -> R.string.goal_form_error_target_not_after_start
            DraftError.START_IN_FUTURE in errors -> R.string.goal_form_error_start_in_future
            else -> null
        }
        AnimatedVisibility(visible = message != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Text(message?.let { stringResource(it) }.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp, start = 6.dp))
        }
    }
}

/** « → 45 jours · 1 mois 14 jours ». */
@Composable
private fun durationText(days: Long, breakdown: Breakdown): String {
    val resources = LocalContext.current.resources
    val count = pluralStringResource(R.plurals.form_duration, days.toInt(), days.toInt())
    return GoalLabels.breakdown(resources, breakdown)?.let { stringResource(R.string.joined_dot, count, it) } ?: count
}

@Composable
private fun DateChip(label: String, date: LocalDate?, accent: Color, error: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "chip")
    val border by animateColorAsState(if (error) MaterialTheme.colorScheme.error else Color.Transparent, label = "chipBorder")
    Row(
        Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(2.dp, border, RoundedCornerShape(22.dp))
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.DateRange, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            AnimatedContent(targetState = date, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "chipDate") { d ->
                Text(
                    d?.formatCapitalized(DateFormats.weekdayLong) ?: stringResource(R.string.goal_form_choose_date),
                    color = if (d == null) accent else MaterialTheme.colorScheme.onSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Liaison verticale entre deux dates, avec la durée calculée dans une pastille. */
@Composable
private fun Connector(accent: Color, text: String, error: Boolean) {
    Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(68.dp).fillMaxHeight()) {
            val x = 34.dp.toPx()
            val dash = 4.dp.toPx()
            var y = 2.dp.toPx()
            while (y < size.height - 2.dp.toPx()) {
                drawLine(accent.copy(alpha = 0.45f), Offset(x, y), Offset(x, min(y + dash, size.height)), 2.5.dp.toPx(), StrokeCap.Round)
                y += dash * 2
            }
        }
        AnimatedContent(
            targetState = text,
            transitionSpec = { (fadeIn() + scaleIn(spring(dampingRatio = 0.6f), initialScale = 0.85f)) togetherWith fadeOut() },
            label = "duration",
        ) { t ->
            val tint = if (error) MaterialTheme.colorScheme.error else accent
            Text(
                t,
                color = tint,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(tint.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

/**
 * Feuille du bas : suggestions rapides, calendrier Material 3, date choisie et écart. Défilable : le
 * calendrier ne tient pas en entier sur les petits écrans (et en paysage).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateSheet(
    field: DateField,
    kind: GoalKind,
    current: LocalDate?,
    other: LocalDate?,
    today: LocalDate,
    base: Color,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    val resources = LocalContext.current.resources
    // Le DatePicker manipule des millisecondes à minuit UTC : conversion sans fuseau local.
    val state = rememberDatePickerState(initialSelectedDateMillis = (current ?: today).toUtcMillis())
    val selected = state.selectedDateMillis?.utcMillisToLocalDate()
    val suggestions: List<Pair<Int, LocalDate>> = if (field == DateField.TARGET) {
        listOf(
            R.string.form_suggest_in_week to today.plusWeeks(1),
            R.string.form_suggest_month to today.plusMonths(1),
            R.string.form_suggest_3_months to today.plusMonths(3),
            R.string.form_suggest_year to today.plusYears(1),
            R.string.form_suggest_new_year to LocalDate.of(today.year + 1, 1, 1),
            R.string.countdown_today to today,
        )
    } else {
        listOf(
            R.string.countdown_today to today,
            R.string.form_suggest_week_ago to today.minusWeeks(1),
            R.string.form_suggest_month to today.minusMonths(1),
            R.string.form_suggest_year to today.minusYears(1),
            R.string.form_suggest_jan_first to LocalDate.of(today.year, 1, 1),
        )
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text(
                stringResource(
                    when {
                        field == DateField.TARGET -> R.string.goal_form_target_date
                        kind == GoalKind.SINCE -> R.string.form_sheet_since
                        else -> R.string.goal_form_start_date
                    },
                ),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            val summary = selected?.let { d ->
                val parts = mutableListOf(
                    d.formatCapitalized(DateFormats.weekdayLong),
                    GoalLabels.relative(resources, ChronoUnit.DAYS.between(today, d)),
                )
                if (kind == GoalKind.COUNTDOWN && other != null) {
                    val n = (if (field == DateField.TARGET) ChronoUnit.DAYS.between(other, d) else ChronoUnit.DAYS.between(d, other)).toInt()
                    parts += resources.getQuantityString(R.plurals.form_sheet_duration, n, n)
                }
                parts.joinToString(" · ")
            }
            AnimatedContent(targetState = summary, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "sheetSummary") { r ->
                Text(
                    r ?: stringResource(R.string.goal_form_choose_date),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(suggestions) { (label, date) ->
                    val on = selected == date
                    val bg by animateColorAsState(if (on) base else MaterialTheme.colorScheme.surfaceContainerHigh, label = "sugg")
                    val fg by animateColorAsState(if (on) Color.White else MaterialTheme.colorScheme.onSurface, label = "suggText")
                    Text(
                        stringResource(label),
                        color = fg,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(bg)
                            .clickable(role = Role.Button) {
                                state.selectedDateMillis = date.toUtcMillis()
                                state.displayedMonthMillis = date.withDayOfMonth(1).toUtcMillis()
                            }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                }
            }
            DatePicker(
                state = state,
                title = null,
                headline = null,
                showModeToggle = false,
                colors = DatePickerDefaults.colors(
                    containerColor = Color.Transparent,
                    selectedDayContainerColor = base,
                    selectedDayContentColor = Color.White,
                    todayDateBorderColor = base,
                    selectedYearContainerColor = base,
                ),
            )
            Button(
                onClick = { selected?.let(onPick) },
                enabled = selected != null,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = base, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(54.dp),
            ) { Text(stringResource(R.string.action_validate), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

// --- Couleur, emoji, notifications ---------------------------------------------------------------

@Composable
private fun Section(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSwatches(selected: GoalColor?, onSelect: (GoalColor?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Swatch(GoalTint.deepColor(GoalStyle.NONE), stringResource(R.string.goal_color_none), selected == null) { onSelect(null) }
        GoalColor.entries.forEach { c -> Swatch(GoalColors.light(c), stringResource(GoalColors.name(c)), selected == c) { onSelect(c) } }
    }
}

@Composable
private fun Swatch(fill: Color, label: String, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1.12f else 1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow), label = "swatch")
    val ring by animateDpAsState(if (selected) 3.dp else 0.dp, spring(dampingRatio = 0.6f), label = "swatchRing")
    Box(
        Modifier
            .size(46.dp)
            .scale(scale)
            .border(ring, MaterialTheme.colorScheme.onSurface, CircleShape)
            .padding(5.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(lerp(fill, Color.White, 0.25f), fill, lerp(fill, Color.Black, 0.3f))))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = label
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(selected, enter = scaleIn(spring(dampingRatio = 0.45f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

/** Unité des graphiques de cet objectif : « Général (…) » (hérite du menu ⋮) ou une unité propre. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartUnitPicker(selected: ChartUnit?, base: Color, onSelect: (ChartUnit?) -> Unit) {
    val resources = LocalContext.current.resources
    val global = globalChartUnit()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val options = listOf<ChartUnit?>(null) + ChartUnit.entries
            options.forEach { unit ->
                val label = if (unit == null) {
                    stringResource(R.string.chart_unit_inherit, GoalLabels.chartUnit(resources, global))
                } else {
                    GoalLabels.chartUnit(resources, unit)
                }
                FilterChip(
                    selected = selected == unit,
                    onClick = { onSelect(unit) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = base.copy(alpha = 0.22f),
                    ),
                )
            }
        }
        Text(
            stringResource(R.string.goal_form_chart_unit_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Emojis suggérés (le choisi rebondit) + champ libre pour n'importe quel emoji. */
@Composable
private fun EmojiPicker(selected: String?, raw: String?, base: Color, onSelect: (String?) -> Unit) {
    val none = stringResource(R.string.goal_emoji_none)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            item {
                EmojiBubble(selected == null, base, none, onClick = { onSelect(null) }) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(SUGGESTED_EMOJIS) { e ->
                EmojiBubble(selected == e, base, e, onClick = { onSelect(e) }) { Text(e, fontSize = 24.sp) }
            }
        }
        OutlinedTextField(
            value = raw.takeUnless { it in SUGGESTED_EMOJIS }.orEmpty(),
            onValueChange = { if (it.length <= EMOJI_MAX_LENGTH) onSelect(it) },
            placeholder = { Text(stringResource(R.string.goal_form_emoji_custom)) },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EmojiBubble(selected: Boolean, base: Color, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1.14f else 1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow), label = "emoji")
    val bg by animateColorAsState(if (selected) base.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHigh, label = "emojiBg")
    Box(
        Modifier
            .padding(4.dp)
            .size(50.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(if (selected) 2.dp else 0.dp, if (selected) base else Color.Transparent, RoundedCornerShape(18.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = label
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Carte « Notifications de jalons ». L'autorisation (Android 13+) est demandée à l'activation (spec 44) ;
 * refusée, l'interrupteur reste actif (préférence de l'objectif) et un lien mène aux paramètres.
 */
@Composable
private fun NotificationCard(enabled: Boolean, base: Color, onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    var canPost by remember { mutableStateOf(MilestoneNotifications.canPost(context)) }
    LifecycleResumeEffect(Unit) {
        canPost = MilestoneNotifications.canPost(context)
        onPauseOrDispose { }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canPost = MilestoneNotifications.canPost(context)
    }
    fun toggle(on: Boolean) {
        onChange(on)
        if (on && !canPost && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val tint by animateColorAsState(if (enabled) base else MaterialTheme.colorScheme.onSurfaceVariant, label = "bell")
    val dark = isSystemInDarkTheme()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Switch) { toggle(!enabled) }
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val wiggle by animateFloatAsState(if (enabled) 1f else 0f, spring(dampingRatio = 0.25f, stiffness = Spring.StiffnessLow), label = "wiggle")
            Box(Modifier.size(48.dp).clip(CircleShape).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = if (dark && enabled) lerp(base, Color.White, 0.45f) else tint,
                    modifier = Modifier.graphicsLayer { rotationZ = sin(wiggle * 12f) * 14f * (1f - wiggle) },
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.goal_form_notifications), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.goal_form_notifications_footer), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = enabled,
                onCheckedChange = ::toggle,
                colors = SwitchDefaults.colors(checkedTrackColor = base, checkedThumbColor = Color.White),
            )
        }
        AnimatedVisibility(enabled && !canPost, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 12.dp)) {
                Text(stringResource(R.string.notifications_denied_hint), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                TextButton(onClick = {
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } catch (_: ActivityNotFoundException) {
                    }
                }) { Text(stringResource(R.string.notifications_open_settings)) }
            }
        }
    }
}

/** Carte « Série » : remise à zéro possible, record et historique (compteur « depuis » seulement). */
@Composable
private fun StreakCard(enabled: Boolean, base: Color, onChange: (Boolean) -> Unit) {
    val tint by animateColorAsState(if (enabled) base else MaterialTheme.colorScheme.onSurfaceVariant, label = "streak")
    val dark = isSystemInDarkTheme()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Switch) { onChange(!enabled) }
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Refresh, contentDescription = null, tint = if (dark && enabled) lerp(base, Color.White, 0.45f) else tint)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.goal_form_streak), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.goal_form_streak_footer), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = enabled,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = base, checkedThumbColor = Color.White),
        )
    }
}

/** Modification : « Continuer en compteur depuis » (cible dépassée) et « Supprimer », tous deux confirmés. */
@Composable
private fun ManageCard(canContinueAsSince: Boolean, onContinue: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(vertical = 6.dp)) {
        if (canContinueAsSince) {
            ManageRow(
                Icons.Filled.Refresh,
                stringResource(R.string.goal_action_continue_as_since),
                stringResource(R.string.form_continue_hint),
                MaterialTheme.colorScheme.primary,
                onContinue,
            )
        }
        ManageRow(Icons.Filled.Delete, stringResource(R.string.form_delete), stringResource(R.string.form_delete_hint), MaterialTheme.colorScheme.error, onDelete)
    }
}

@Composable
private fun ManageRow(icon: ImageVector, title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

/** Gros bouton collant, au-dessus du clavier, dans la couleur de l'objectif ; secousse si le brouillon est invalide. */
@Composable
private fun SaveBar(label: String, base: Color, shake: Float, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "save")
    val bg = MaterialTheme.colorScheme.background
    Box(
        modifier
            .fillMaxWidth()
            .imePadding()
            .background(Brush.verticalGradient(listOf(bg.copy(alpha = 0f), bg.copy(alpha = 0.92f), bg)))
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 14.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(62.dp)
                .graphicsLayer {
                    translationX = shake * density
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(lerp(base, Color.White, 0.15f), base, lerp(base, Color.Black, 0.2f))))
                .clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(10.dp))
            Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}
