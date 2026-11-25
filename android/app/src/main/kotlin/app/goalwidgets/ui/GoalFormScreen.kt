package app.goalwidgets.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.goalwidgets.R
import app.goalwidgets.core.CountdownEngine
import app.goalwidgets.core.DraftError
import app.goalwidgets.core.Goal
import app.goalwidgets.core.GoalDraft
import app.goalwidgets.core.GoalColor
import app.goalwidgets.core.GoalKind
import app.goalwidgets.core.GoalStyle
import app.goalwidgets.core.Phase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Formulaire d'objectif : création ([existing] = null) ou modification d'un objectif stocké.
 * En création, le type se choisit en tête (compte à rebours / depuis) et la date de départ
 * vaut [today] par défaut. En modification : tous les champs, type compris, sont modifiables ;
 * « Continuer en compteur depuis » (compte à rebours atteint) et suppression confirmée.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalFormScreen(
    existing: StoredGoal?,
    today: LocalDate,
    /** Objectif validé, son style et l'état de l'interrupteur de notifications. */
    onSave: (Goal, GoalStyle, Boolean) -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit = {},
    onContinueAsSince: () -> Unit = {},
) {
    val initial = existing?.goal
    var kind by rememberSaveable { mutableStateOf(initial?.kind ?: GoalKind.COUNTDOWN) }
    var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }
    var startDate by rememberSaveable { mutableStateOf(initial?.startDate ?: today) }
    var targetDate by rememberSaveable { mutableStateOf(initial?.targetDate) }
    var color by rememberSaveable { mutableStateOf(existing?.style?.color) }
    var emoji by rememberSaveable { mutableStateOf(existing?.style?.emoji) }
    // Les erreurs ne s'affichent qu'après une première tentative d'enregistrement.
    var attempted by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    // Désactivées par défaut, y compris pour un nouvel objectif (même règle qu'iOS).
    var notificationsEnabled by rememberSaveable { mutableStateOf(existing?.notificationsEnabled ?: false) }

    val draft = GoalDraft(kind, title, startDate, targetDate, today)
    val errors = if (attempted) draft.errors() else emptySet()
    // Conversion proposée seulement sur un compte à rebours enregistré dont la cible est dépassée.
    val canContinueAsSince = initial != null && initial.kind == GoalKind.COUNTDOWN &&
        CountdownEngine.compute(initial, today).phase == Phase.REACHED

    BackHandler(onBack = onCancel)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (existing == null) R.string.goal_form_new else R.string.goal_form_edit))
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
                    }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { confirmingDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                    TextButton(onClick = {
                        attempted = true
                        if (draft.errors().isEmpty()) {
                            onSave(
                                draft.toGoal(),
                                GoalStyle(color, emoji?.trim()?.takeIf { it.isNotEmpty() }),
                                notificationsEnabled,
                            )
                        }
                    }) { Text(stringResource(R.string.action_save)) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            KindSelector(kind = kind, onSelect = { kind = it })
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.goal_form_title)) },
                singleLine = true,
                isError = DraftError.TITLE_BLANK in errors,
                supportingText = if (DraftError.TITLE_BLANK in errors) {
                    { Text(stringResource(R.string.goal_form_error_empty_title)) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )
            when (kind) {
                GoalKind.COUNTDOWN -> {
                    DateField(
                        label = stringResource(R.string.goal_form_target_date),
                        date = targetDate,
                        error = when {
                            DraftError.TARGET_MISSING in errors ->
                                stringResource(R.string.goal_form_error_target_missing)
                            DraftError.TARGET_NOT_AFTER_START in errors ->
                                stringResource(R.string.goal_form_error_target_not_after_start)
                            else -> null
                        },
                        onPick = { targetDate = it },
                    )
                    DateField(
                        label = stringResource(R.string.goal_form_start_date),
                        date = startDate,
                        error = null,
                        onPick = { startDate = it },
                    )
                }
                GoalKind.SINCE -> DateField(
                    label = stringResource(R.string.goal_form_since_date),
                    date = startDate,
                    error = if (DraftError.START_IN_FUTURE in errors) {
                        stringResource(R.string.goal_form_error_start_in_future)
                    } else null,
                    onPick = { startDate = it },
                )
            }
            ColorSelector(selected = color, onSelect = { color = it })
            EmojiSelector(selected = emoji, onSelect = { emoji = it })
            NotificationToggle(enabled = notificationsEnabled, onChange = { notificationsEnabled = it })
            if (canContinueAsSince) {
                OutlinedButton(onClick = onContinueAsSince, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.goal_action_continue_as_since))
                }
            }
        }
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.goal_delete_confirm_title)) },
            text = { Text(stringResource(R.string.goal_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KindSelector(kind: GoalKind, onSelect: (GoalKind) -> Unit) {
    val options = listOf(
        GoalKind.COUNTDOWN to stringResource(R.string.goal_kind_countdown),
        GoalKind.SINCE to stringResource(R.string.goal_kind_since),
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.goal_form_kind), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = kind == value,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) { Text(label) }
            }
        }
    }
}

/** Pastilles de la palette [GoalColor], plus « aucune couleur » (couleur du thème). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSelector(selected: GoalColor?, onSelect: (GoalColor?) -> Unit) {
    val dark = isSystemInDarkTheme()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.goal_form_color), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Swatch(
                fill = MaterialTheme.colorScheme.surfaceVariant,
                label = stringResource(R.string.goal_color_none),
                selected = selected == null,
                onClick = { onSelect(null) },
            )
            GoalColor.entries.forEach { c ->
                Swatch(
                    fill = GoalColors.of(c, dark),
                    label = stringResource(GoalColors.name(c)),
                    selected = selected == c,
                    onClick = { onSelect(c) },
                )
            }
        }
    }
}

@Composable
private fun Swatch(fill: androidx.compose.ui.graphics.Color, label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(fill)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = label
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
    }
}

/** Emojis suggérés, « aucun », ou un emoji libre (clavier emoji du système). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmojiSelector(selected: String?, onSelect: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.goal_form_emoji), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            EmojiChoice(
                selected = selected.isNullOrBlank(),
                label = stringResource(R.string.goal_emoji_none),
                onClick = { onSelect(null) },
            ) { Icon(Icons.Filled.Close, contentDescription = null) }
            SUGGESTED_EMOJIS.forEach { e ->
                EmojiChoice(selected = selected == e, label = e, onClick = { onSelect(e) }) {
                    Text(e, fontSize = 22.sp)
                }
            }
        }
        OutlinedTextField(
            value = selected.takeUnless { it in SUGGESTED_EMOJIS }.orEmpty(),
            onValueChange = { if (it.length <= EMOJI_MAX_LENGTH) onSelect(it) },
            label = { Text(stringResource(R.string.goal_form_emoji_custom)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EmojiChoice(selected: Boolean, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = label
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, date: LocalDate?, error: String?, onPick: (LocalDate) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(date?.format(formatter) ?: stringResource(R.string.goal_form_choose_date))
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (open) {
        // Le DatePicker Material manipule des millisecondes à minuit UTC : conversion sans fuseau local.
        val state = rememberDatePickerState(initialSelectedDateMillis = date?.toUtcMillis())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPick(it.utcMillisToLocalDate()) }
                    open = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) { DatePicker(state = state) }
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.utcMillisToLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Preview(showBackground = true)
@Composable
private fun GoalFormScreenNewPreview() {
    GoalWidgetsTheme { GoalFormScreen(existing = null, today = LocalDate.of(2026, 1, 6), onSave = { _, _, _ -> }, onCancel = {}) }
}

@Preview(showBackground = true)
@Composable
private fun GoalFormScreenEditReachedPreview() {
    val goal = Goal(GoalKind.COUNTDOWN, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 4), "Examen")
    GoalWidgetsTheme {
        GoalFormScreen(
            existing = StoredGoal(1, goal, GoalStyle(GoalColor.BLUE, "🎓")),
            today = LocalDate.of(2026, 1, 6),
            onSave = { _, _, _ -> },
            onCancel = {},
        )
    }
}
