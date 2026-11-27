package app.goalwidgets.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goalwidgets.R
import app.goalwidgets.core.GoalKind
import app.goalwidgets.core.MilestoneFamily
import app.goalwidgets.core.NotificationPrefs
import app.goalwidgets.notifications.NotificationSettings
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Rappels « N jours avant » proposés d'un appui ; un autre nombre se saisit avec « + ». */
private val PRESET_DAYS_BEFORE = listOf(1, 2, 3, 7, 14, 30, 60, 90)

/**
 * Personnalisation des notifications d'un objectif (sous l'interrupteur du formulaire) : familles de
 * jalons selon le type, rappels « N jours avant » d'un compte à rebours, heure d'envoi propre.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NotificationPrefsEditor(kind: GoalKind, prefs: NotificationPrefs, base: Color, onChange: (NotificationPrefs) -> Unit) {
    val context = LocalContext.current
    var addingDays by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider(Modifier.padding(bottom = 8.dp))
        when (kind) {
            GoalKind.SINCE -> {
                FamilyRow(R.string.notif_family_month, R.string.notif_family_month_hint, MilestoneFamily.MONTH_ANNIVERSARIES, prefs, base, onChange)
                FamilyRow(R.string.notif_family_year, R.string.notif_family_year_hint, MilestoneFamily.YEAR_ANNIVERSARIES, prefs, base, onChange)
                FamilyRow(R.string.notif_family_days, R.string.notif_family_days_hint, MilestoneFamily.DAY_MILESTONES, prefs, base, onChange)
            }
            GoalKind.COUNTDOWN -> {
                FamilyRow(R.string.notif_family_target, R.string.notif_family_target_hint, MilestoneFamily.TARGET_DAY, prefs, base, onChange)
                FamilyRow(R.string.notif_family_monthly, R.string.notif_family_monthly_hint, MilestoneFamily.MONTHLY_REMINDERS, prefs, base, onChange)
                Text(stringResource(R.string.notif_days_before_title), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (PRESET_DAYS_BEFORE + prefs.daysBefore).distinct().sorted().forEach { n ->
                        val on = n in prefs.daysBefore
                        FilterChip(
                            selected = on,
                            onClick = { onChange(prefs.copy(daysBefore = if (on) prefs.daysBefore - n else prefs.daysBefore + n)) },
                            label = { Text(daysBeforeLabel(n)) },
                            leadingIcon = if (on) ({ Icon(Icons.Filled.Check, contentDescription = null) }) else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = base.copy(alpha = 0.18f),
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = { addingDays = true },
                        label = { Text(stringResource(R.string.notif_days_before_other)) },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    )
                }
            }
        }
        // Heure d'envoi : générale ou propre à l'objectif.
        val format = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        val global = remember { NotificationSettings.sendTime(context) }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).clickable { pickingTime = true }.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.notif_time_title), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    prefs.sendTime?.let { stringResource(R.string.notif_time_custom) } ?: stringResource(R.string.notif_time_global),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text((prefs.sendTime ?: global).format(format), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }

    if (addingDays) {
        var text by remember { mutableStateOf("") }
        val value = text.toIntOrNull()?.takeIf { it in 1..NotificationPrefs.MAX_DAYS_BEFORE }
        AlertDialog(
            onDismissRequest = { addingDays = false },
            title = { Text(stringResource(R.string.notif_days_before_dialog)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(3) },
                    singleLine = true,
                    suffix = { Text(stringResource(R.string.notif_days_before_suffix)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(enabled = value != null, onClick = {
                    value?.let { onChange(prefs.copy(daysBefore = prefs.daysBefore + it)) }
                    addingDays = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { addingDays = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (pickingTime) {
        val initial = prefs.sendTime ?: NotificationSettings.sendTime(context)
        val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            title = { Text(stringResource(R.string.notif_time_title)) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    onChange(prefs.copy(sendTime = LocalTime.of(state.hour, state.minute)))
                    pickingTime = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    onChange(prefs.copy(sendTime = null))
                    pickingTime = false
                }) { Text(stringResource(R.string.notif_time_use_global)) }
            },
        )
    }
}

@Composable
private fun daysBeforeLabel(n: Int): String = when {
    n == 1 -> stringResource(R.string.notif_days_before_eve)
    n >= 14 && n % 7 == 0 && n != 30 -> pluralStringResource(R.plurals.notif_weeks_before, n / 7, n / 7)
    else -> pluralStringResource(R.plurals.notif_days_before, n, n)
}

@Composable
private fun FamilyRow(title: Int, hint: Int, family: MilestoneFamily, prefs: NotificationPrefs, base: Color, onChange: (NotificationPrefs) -> Unit) {
    val on = prefs.enabled(family)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Switch) { onChange(prefs.with(family, !on)) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(stringResource(hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = on,
            onCheckedChange = { onChange(prefs.with(family, it)) },
            colors = SwitchDefaults.colors(checkedTrackColor = base, checkedThumbColor = Color.White),
        )
    }
}
