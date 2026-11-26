package app.goalwidgets.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goalwidgets.R
import app.goalwidgets.core.Streaks
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Confirmation de la remise à zéro d'un compteur « série » (détail et carte de l'accueil). Le compteur
 * repart aujourd'hui par défaut ; « Changer la date » permet une date passée (entre le départ de la
 * série en cours et aujourd'hui, cf. [Streaks.validate]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetStreakDialog(stored: StoredGoal, today: LocalDate, onConfirm: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    var on by rememberSaveable { mutableStateOf(today) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val days = stored.streakSummary(on).currentDays

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
        title = { Text(stringResource(R.string.streak_reset_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(pluralStringResource(R.plurals.streak_reset_confirm_message, days, days, stored.goal.title))
                Text(
                    stringResource(R.string.streak_reset_from, on.format(DateFormats.long)),
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = { picking = true }) {
                    Text(stringResource(R.string.streak_reset_change_date))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(on) }) { Text(stringResource(R.string.streak_reset_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

    if (picking) {
        val start = stored.goal.startDate
        // Le DatePicker manipule des millisecondes à minuit UTC : conversion sans fuseau local.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = on.toUtcMillis(),
            yearRange = start.year..today.year,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val d = utcTimeMillis.utcMillisToLocalDate()
                    return Streaks.validate(stored.goal, d, today) == null
                }

                override fun isSelectableYear(year: Int): Boolean = year in start.year..today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.utcMillisToLocalDate()?.let { on = it }
                    picking = false
                }) { Text(stringResource(R.string.action_validate)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = state, showModeToggle = false) }
    }
}

/**
 * Bloc « série » du détail, sur fond coloré : record (mis en avant quand la série en cours l'égale ou
 * le bat), nombre de remises à zéro et bouton « Remettre à zéro ».
 */
@Composable
fun StreakBar(stored: StoredGoal, today: LocalDate, onReset: () -> Unit, modifier: Modifier = Modifier) {
    val summary = stored.streakSummary(today)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (summary.resetCount > 0) {
            Text(
                if (summary.isRecord) {
                    "🏆 " + stringResource(R.string.streak_new_record)
                } else {
                    stringResource(R.string.streak_record) + " · " + pluralStringResource(R.plurals.streak_days, summary.bestDays, summary.bestDays)
                },
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                pluralStringResource(R.plurals.streak_reset_count, summary.resetCount, summary.resetCount),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(
            Modifier
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.22f))
                .clickable(role = Role.Button, onClick = onReset)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.streak_reset_action), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Liste des séries passées, la plus récente d'abord (carte claire du détail). Rien si aucune. */
@Composable
fun StreakHistoryCard(stored: StoredGoal, card: Color, modifier: Modifier = Modifier) {
    if (stored.history.isEmpty()) return
    val resources = LocalContext.current.resources
    val best = stored.history.maxOf { it.days }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(card).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.streak_history_title),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        stored.history.asReversed().forEach { streak ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    resources.getString(R.string.streak_history_row, streak.start.format(DateFormats.medium), streak.end.format(DateFormats.medium)),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    (if (streak.days == best && best > 0) "🏆 " else "") +
                        resources.getQuantityString(R.plurals.streak_days, streak.days, streak.days),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

internal fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun Long.utcMillisToLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
