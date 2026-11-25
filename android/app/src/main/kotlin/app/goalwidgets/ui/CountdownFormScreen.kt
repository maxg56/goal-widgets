package app.goalwidgets.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.goalwidgets.R
import app.goalwidgets.core.CountdownDraft
import app.goalwidgets.core.DraftError
import app.goalwidgets.core.Goal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Formulaire de création d'un compte à rebours. Départ par défaut = [today]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownFormScreen(
    today: LocalDate,
    onSave: (Goal) -> Unit,
    onCancel: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var startDate by rememberSaveable { mutableStateOf(today) }
    var targetDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    // Les erreurs ne s'affichent qu'après une première tentative d'enregistrement.
    var attempted by rememberSaveable { mutableStateOf(false) }

    val draft = CountdownDraft(title, startDate, targetDate)
    val errors = if (attempted) draft.errors() else emptySet()

    BackHandler(onBack = onCancel)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.goal_form_new_countdown)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
                    }
                },
                actions = {
                    TextButton(onClick = {
                        attempted = true
                        if (draft.errors().isEmpty()) onSave(draft.toGoal())
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
            DateField(
                label = stringResource(R.string.goal_form_target_date),
                date = targetDate,
                error = when {
                    DraftError.TARGET_MISSING in errors -> stringResource(R.string.goal_form_error_target_missing)
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
    }
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
private fun CountdownFormScreenPreview() {
    GoalWidgetsTheme { CountdownFormScreen(today = LocalDate.of(2026, 1, 6), onSave = {}, onCancel = {}) }
}
