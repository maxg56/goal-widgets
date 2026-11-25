package app.goalwidgets

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.goalwidgets.core.GoalOrder
import app.goalwidgets.share.GoalFiles
import app.goalwidgets.share.GoalSharing
import app.goalwidgets.ui.GoalFormScreen
import app.goalwidgets.ui.GoalListScreen
import app.goalwidgets.ui.GoalListViewModel
import app.goalwidgets.ui.GoalWidgetsTheme
import app.goalwidgets.ui.ImportOutcome
import app.goalwidgets.ui.NotificationSettingsScreen
import app.goalwidgets.ui.rememberLocalToday
import app.goalwidgets.notifications.NotificationSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class MainActivity : ComponentActivity() {
    /**
     * Objectif à montrer dans la liste (appui sur un widget ou une notification), `null` une fois
     * montré. Un nouvel appui sur le même objectif crée une nouvelle demande (défilement rejoué).
     */
    private var focusRequest by mutableStateOf<FocusRequest?>(null)
    private var focusRequestCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestFocus(intent)
        setContent {
            GoalWidgetsTheme {
                val vm: GoalListViewModel = viewModel(factory = factory)
                val goals by vm.goals.collectAsStateWithLifecycle()
                val order by vm.order.collectAsStateWithLifecycle()
                val scope = rememberCoroutineScope()
                val snackbar = remember { SnackbarHostState() }
                fun show(message: String) { scope.launch { snackbar.showSnackbar(message) } }
                // Export / import via le sélecteur de fichiers du système (SAF : stockage local, Drive…).
                val exportLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument(EXPORT_MIME_TYPE),
                ) { uri ->
                    if (uri != null) scope.launch { show(exportTo(uri, vm)) }
                }
                val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) scope.launch { show(importFrom(uri, vm)) }
                }
                // Date du jour locale : relue au retour au premier plan, à minuit et au changement d'heure.
                val today by rememberLocalToday()
                // Écran affiché : liste, création (NEW_GOAL) ou modification de l'objectif d'id donné.
                var editing by rememberSaveable { mutableStateOf<Long?>(null) }
                var showingNotificationSettings by rememberSaveable { mutableStateOf(false) }
                var sendTime by remember { mutableStateOf(NotificationSettings.sendTime(this)) }
                val current = editing
                val existing = goals?.firstOrNull { it.id == current }
                when {
                    showingNotificationSettings -> NotificationSettingsScreen(
                        sendTime = sendTime,
                        onSendTimeChange = {
                            NotificationSettings.setSendTime(this, it)
                            sendTime = it
                            (application as GoalWidgetsApplication).rescheduleNotifications()
                        },
                        onBack = { showingNotificationSettings = false },
                    )
                    current == NEW_GOAL -> GoalFormScreen(
                        existing = null,
                        today = today,
                        onSave = { goal, style, notify -> vm.add(goal, style, notify); editing = null },
                        onCancel = { editing = null },
                    )
                    current != null && existing != null -> key(current) {
                        GoalFormScreen(
                            existing = existing,
                            today = today,
                            onSave = { goal, style, notify -> vm.update(current, goal, style, notify); editing = null },
                            onCancel = { editing = null },
                            onDelete = { vm.delete(current); editing = null },
                            onContinueAsSince = { vm.continueAsSince(existing); editing = null },
                            onShare = { scope.launch { GoalSharing.share(this@MainActivity, current) } },
                        )
                    }
                    // Objectif introuvable (supprimé entre-temps) ou liste pas encore chargée.
                    else -> GoalListScreen(
                        goals = goals,
                        today = today,
                        onAdd = { editing = NEW_GOAL },
                        order = order,
                        onOrderChange = vm::setOrder,
                        onExport = { exportLauncher.launch("goal-widgets-$today.json") },
                        onImport = { importLauncher.launch(IMPORT_MIME_TYPES) },
                        snackbarHostState = snackbar,
                        onOpen = { editing = it.id },
                        onContinueAsSince = vm::continueAsSince,
                        focusGoalId = focusRequest?.goalId,
                        focusRequestKey = focusRequest,
                        onFocusHandled = { focusRequest = null },
                        onOpenNotificationSettings = { showingNotificationSettings = true },
                    )
                }
            }
        }
    }

    /**
     * App déjà ouverte (widget/notification : `CLEAR_TOP | SINGLE_TOP`) : l'activité est réutilisée,
     * un formulaire en cours est conservé ; l'objectif est montré dès que la liste est affichée.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestFocus(intent)
    }

    private fun requestFocus(intent: Intent) {
        val goalId = intent.getLongExtra(EXTRA_GOAL_ID, -1L).takeIf { it >= 0 } ?: return
        focusRequest = FocusRequest(goalId, ++focusRequestCount)
    }

    /** Demande de défilement vers [goalId] ; [serial] distingue deux appuis sur le même objectif. */
    private data class FocusRequest(val goalId: Long, val serial: Int)

    /** Écrit l'export dans [uri] ; renvoie le message à afficher. */
    private suspend fun exportTo(uri: Uri, vm: GoalListViewModel): String = try {
        val (text, count) = vm.exportText()
        withContext(Dispatchers.IO) {
            contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                ?: throw IOException("flux indisponible")
        }
        resources.getQuantityString(R.plurals.export_done, count, count)
    } catch (e: Exception) {
        getString(R.string.export_failed)
    }

    /** Lit puis importe [uri] ; un fichier invalide ne modifie rien. Renvoie le message à afficher. */
    private suspend fun importFrom(uri: Uri, vm: GoalListViewModel): String {
        val text = try {
            withContext(Dispatchers.IO) { GoalFiles.readText(contentResolver, uri) }
        } catch (e: Exception) {
            null
        } ?: return getString(R.string.import_error_unreadable)
        return when (val outcome = vm.import(text)) {
            is ImportOutcome.Rejected -> GoalFiles.errorMessage(resources, outcome.error)
            is ImportOutcome.Imported -> {
                val added = outcome.plan.toAdd.size
                val skipped = outcome.plan.skipped
                when {
                    added == 0 && skipped == 0 -> getString(R.string.import_empty)
                    skipped == 0 -> resources.getQuantityString(R.plurals.import_added, added, added)
                    else -> resources.getQuantityString(R.plurals.import_added, added, added) + " · " +
                        resources.getQuantityString(R.plurals.import_skipped, skipped, skipped)
                }
            }
        }
    }

    companion object {
        private const val EXPORT_MIME_TYPE = "application/json"

        /** Certains fournisseurs (Drive, messageries) ne déclarent pas le type JSON. */
        private val IMPORT_MIME_TYPES = arrayOf("application/json", "text/*", "application/octet-stream")
        private const val PREFS = "settings"
        private const val PREF_ORDER = "goal_order"

        /** Id (Long) de l'objectif à montrer à l'ouverture (appui sur un widget ou une notification). */
        const val EXTRA_GOAL_ID = "app.goalwidgets.extra.GOAL_ID"

        /** Valeur de `editing` pour un nouvel objectif (les id Room commencent à 1). */
        private const val NEW_GOAL = -1L

        private val factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as GoalWidgetsApplication
                val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                GoalListViewModel(
                    app.database.goalDao(),
                    onGoalsChanged = app::onGoalsChanged,
                    initialOrder = GoalOrder.fromWire(prefs.getString(PREF_ORDER, null)),
                    onOrderChanged = { prefs.edit().putString(PREF_ORDER, it.wire).apply() },
                )
            }
        }
    }
}
