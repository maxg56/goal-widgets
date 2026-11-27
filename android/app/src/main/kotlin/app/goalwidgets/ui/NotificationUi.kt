package app.goalwidgets.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.goalwidgets.BuildConfig
import app.goalwidgets.R
import app.goalwidgets.debug.NotificationDebugSection
import app.goalwidgets.notifications.MilestoneNotifications
import app.goalwidgets.notifications.WeeklySummary
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import app.goalwidgets.update.UpdateSettingsRow
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Réglages globaux des notifications : heure d'envoi, autorisation, aide batterie. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    sendTime: LocalTime,
    onSendTimeChange: (LocalTime) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var canPost by remember { mutableStateOf(MilestoneNotifications.canPost(context)) }
    LifecycleResumeEffect(Unit) {
        canPost = MilestoneNotifications.canPost(context)
        onPauseOrDispose { }
    }
    var picking by rememberSaveable { mutableStateOf(false) }

    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notifications_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_cancel))
                    }
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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { picking = true }.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.notifications_send_time))
                    Text(
                        sendTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    stringResource(R.string.notifications_send_time_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!canPost) DeniedHint(context)
            HorizontalDivider()
            WeeklySummarySection()
            HorizontalDivider()
            // Xiaomi, Huawei… : l'optimisation de batterie peut tuer les alarmes et la tâche quotidienne.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.notifications_battery_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.notifications_battery_hint), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { openBatterySettings(context) }) {
                    Text(stringResource(R.string.notifications_battery_open))
                }
            }
            HorizontalDivider()
            // Mises à jour automatiques : version installée et recherche manuelle.
            UpdateSettingsRow()
            // Outils de test (t14) : le code n'existe que dans les builds de debug (src/debug).
            if (BuildConfig.DEBUG) NotificationDebugSection()
        }
    }
    if (picking) {
        val state = rememberTimePickerState(initialHour = sendTime.hour, initialMinute = sendTime.minute)
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.notifications_send_time)) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    onSendTimeChange(LocalTime.of(state.hour, state.minute))
                    picking = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** Résumé hebdomadaire : interrupteur, jour, heure, et essai immédiat. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeeklySummarySection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(WeeklySummary.settings(context)) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    var pickingDay by rememberSaveable { mutableStateOf(false) }
    fun update(s: WeeklySummary.Settings) {
        settings = s
        WeeklySummary.save(context, s)
        WeeklySummary.schedule(context)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().clickable { update(settings.copy(enabled = !settings.enabled)) }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.weekly_settings_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.weekly_settings_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = settings.enabled, onCheckedChange = { update(settings.copy(enabled = it)) })
        }
        if (settings.enabled) {
            val dayName = settings.day.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }
            Row(Modifier.fillMaxWidth().clickable { pickingDay = true }.padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.weekly_settings_day))
                Box {
                    Text(dayName, color = MaterialTheme.colorScheme.primary)
                    DropdownMenu(expanded = pickingDay, onDismissRequest = { pickingDay = false }) {
                        DayOfWeek.entries.forEach { day ->
                            DropdownMenuItem(
                                text = { Text(day.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }) },
                                onClick = { pickingDay = false; update(settings.copy(day = day)) },
                            )
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().clickable { pickingTime = true }.padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.weekly_settings_time))
                Text(settings.time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)), color = MaterialTheme.colorScheme.primary)
            }
            OutlinedButton(onClick = {
                scope.launch {
                    if (!WeeklySummary.show(context)) {
                        Toast.makeText(context, context.getString(R.string.weekly_settings_nothing), Toast.LENGTH_SHORT).show()
                    }
                }
            }) { Text(stringResource(R.string.weekly_settings_try)) }
        }
    }
    if (pickingTime) {
        val state = rememberTimePickerState(initialHour = settings.time.hour, initialMinute = settings.time.minute)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            title = { Text(stringResource(R.string.weekly_settings_time)) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    update(settings.copy(time = LocalTime.of(state.hour, state.minute)))
                    pickingTime = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun DeniedHint(context: Context) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.notifications_denied_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = {
            context.startSafely(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }) { Text(stringResource(R.string.notifications_open_settings)) }
    }
}

private fun openBatterySettings(context: Context) {
    // Liste système des optimisations de batterie ; sinon, fiche de l'app (surcouches qui l'ont retirée).
    if (!context.startSafely(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) {
        context.startSafely(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)),
        )
    }
}

private fun Context.startSafely(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
}
