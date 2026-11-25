package app.goalwidgets.debug

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.goalwidgets.GoalWidgetsApplication
import app.goalwidgets.R
import app.goalwidgets.core.GoalKind
import app.goalwidgets.core.Milestone
import app.goalwidgets.core.MilestoneKind
import app.goalwidgets.core.MilestonePlanner
import app.goalwidgets.data.GoalEntity
import app.goalwidgets.notifications.MilestoneNotifications
import app.goalwidgets.notifications.NotificationTexts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Section « Debug » des réglages de notifications (t14, builds de debug uniquement) : état de
 * l'autorisation, jalons programmés avec leur heure d'envoi réelle (« Envoyer maintenant »), et
 * vraie alarme de test dans 10 secondes pour un objectif choisi.
 */
@Composable
fun NotificationDebugSection() {
    val context = LocalContext.current
    val app = context.applicationContext as GoalWidgetsApplication
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var scheduled by remember { mutableStateOf<List<MilestoneNotifications.Scheduled>?>(null) }
    var goals by remember { mutableStateOf<List<GoalEntity>>(emptyList()) }
    var testGoalId by remember { mutableStateOf<Long?>(null) }
    var status by remember { mutableStateOf(AuthStatus.read(context)) }
    LifecycleResumeEffect(Unit) {
        status = AuthStatus.read(context)
        reload++
        onPauseOrDispose { }
    }
    LaunchedEffect(reload) {
        goals = app.database.goalDao().getAll()
        scheduled = MilestoneNotifications.wanted(app)
        if (testGoalId == null || goals.none { it.id == testGoalId }) testGoalId = goals.firstOrNull()?.id
    }
    val titles = goals.associate { it.id to it.title }
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider()
        Text(stringResource(R.string.debug_title), style = MaterialTheme.typography.titleMedium)

        // État de l'autorisation.
        Text(stringResource(R.string.debug_permission, yesNo(status.permission)), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.debug_app_enabled, yesNo(status.appEnabled)), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.debug_channel, status.channelImportance), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.debug_exact_alarms, yesNo(status.exactAlarms)), style = MaterialTheme.typography.bodySmall)

        // Jalon de test dans 10 secondes.
        Text(stringResource(R.string.debug_test_goal), style = MaterialTheme.typography.titleSmall)
        GoalChoice(goals, testGoalId) { testGoalId = it }
        Button(
            enabled = testGoalId != null,
            onClick = {
                val goal = goals.firstOrNull { it.id == testGoalId } ?: return@Button
                val exact = DebugAlarms.scheduleIn10Seconds(context, testMilestone(goal))
                val how = context.getString(if (exact) R.string.debug_exact else R.string.debug_inexact)
                Toast.makeText(context, context.getString(R.string.debug_scheduled_test, how), Toast.LENGTH_SHORT).show()
            },
        ) { Text(stringResource(R.string.debug_schedule_test)) }

        // Jalons programmés.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.debug_scheduled_title, scheduled?.size ?: 0),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { reload++ }) { Text(stringResource(R.string.debug_refresh)) }
        }
        val list = scheduled
        if (list != null && list.isEmpty()) {
            Text(stringResource(R.string.debug_scheduled_empty), style = MaterialTheme.typography.bodySmall)
        }
        list?.forEach { s ->
            val title = titles[s.milestone.goalId.toLong()] ?: s.milestone.goalId
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        formatter.format(Instant.ofEpochMilli(s.triggerAtMillis).atZone(ZoneId.systemDefault())),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(NotificationTexts.body(context.resources, s.milestone), style = MaterialTheme.typography.bodyMedium)
                    Text(s.milestone.id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = {
                        // Même chemin d'affichage que l'alarme, sans la vérification « jour même ».
                        if (!MilestoneNotifications.show(context, title, s.milestone)) {
                            Toast.makeText(context, context.getString(R.string.debug_not_sent), Toast.LENGTH_SHORT).show()
                        }
                    }) { Text(stringResource(R.string.debug_send_now)) }
                }
            }
        }
    }
}

@Composable
private fun GoalChoice(goals: List<GoalEntity>, selected: Long?, onSelect: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, enabled = goals.isNotEmpty()) {
            Text(goals.firstOrNull { it.id == selected }?.title ?: stringResource(R.string.debug_no_goal))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            goals.forEach { goal ->
                DropdownMenuItem(text = { Text(goal.title) }, onClick = { onSelect(goal.id); open = false })
            }
        }
    }
}

@Composable
private fun yesNo(value: Boolean) = stringResource(if (value) R.string.debug_yes else R.string.debug_no)

/**
 * Jalon de test pour [goal] : le prochain jalon réel de l'objectif (planificateur, notifications
 * considérées actives), daté d'aujourd'hui ; à défaut un jalon type (jour J / 1er mois-anniversaire).
 */
private fun testMilestone(goal: GoalEntity): Milestone {
    val today = LocalDate.now()
    val next = MilestonePlanner.plan(
        goalId = goal.id.toString(),
        goal = goal.toGoal(),
        notificationsEnabled = true,
        today = today,
        horizonDays = 3660,
    ).firstOrNull()
    return when {
        next != null -> next.copy(date = today)
        goal.toGoal().kind == GoalKind.COUNTDOWN -> Milestone(goal.id.toString(), today, MilestoneKind.TARGET_DAY, 0)
        else -> Milestone(goal.id.toString(), today, MilestoneKind.MONTH_ANNIVERSARY, 1)
    }
}

private data class AuthStatus(
    val permission: Boolean,
    val appEnabled: Boolean,
    val channelImportance: Int,
    val exactAlarms: Boolean,
) {
    companion object {
        fun read(context: Context) = AuthStatus(
            permission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
            appEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            channelImportance = context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(MilestoneNotifications.CHANNEL_ID)?.importance ?: -1,
            exactAlarms = DebugAlarms.canExact(context),
        )
    }
}

/** Vraie alarme de test (AlarmManager), livrée par [DebugMilestoneReceiver]. */
private object DebugAlarms {
    fun canExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Renvoie `true` si l'alarme est exacte (sinon Android peut la différer d'un peu). */
    fun scheduleIn10Seconds(context: Context, milestone: Milestone): Boolean {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val at = System.currentTimeMillis() + 10_000
        val intent = PendingIntent.getBroadcast(
            context,
            0,
            MilestoneNotifications.putMilestone(
                Intent(context, DebugMilestoneReceiver::class.java)
                    .setData(Uri.parse("goalwidgets://debug-milestone/" + Uri.encode(milestone.id))),
                milestone,
            ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return if (canExact(context)) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            true
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            false
        }
    }
}

/** Alarme de test : même affichage que l'alarme réelle, sans les vérifications de date. */
class DebugMilestoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val milestone = MilestoneNotifications.milestoneOf(intent) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val app = context.applicationContext as GoalWidgetsApplication
                val title = app.database.goalDao().getById(milestone.goalId.toLong())?.title ?: return@launch
                MilestoneNotifications.show(app, title, milestone)
            } finally {
                pending.finish()
            }
        }
    }
}
