package app.goalwidgets

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.goalwidgets.widget.GoalListOrder
import app.goalwidgets.widget.GoalWidgets
import app.goalwidgets.share.GoalFiles
import app.goalwidgets.share.GoalImageShare
import app.goalwidgets.share.GoalSharing
import app.goalwidgets.ui.AppActions
import app.goalwidgets.ui.GoalWidgetsApp
import app.goalwidgets.ui.OpenGoalRequest
import app.goalwidgets.ui.GoalListViewModel
import app.goalwidgets.ui.GoalWidgetsTheme
import app.goalwidgets.ui.ImportOutcome
import app.goalwidgets.ui.rememberLocalToday
import app.goalwidgets.notifications.NotificationSettings
import app.goalwidgets.update.UpdateIndicator
import app.goalwidgets.update.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.time.Duration.Companion.minutes

class MainActivity : ComponentActivity() {
    /**
     * Objectif à ouvrir (appui sur un widget, une notification, objectif partagé reçu), `null` une
     * fois ouvert. Un nouvel appui sur le même objectif crée une nouvelle demande.
     */
    private var openRequest by mutableStateOf<OpenGoalRequest?>(null)
    private var openRequestCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestOpen(intent)
        checkForUpdatesWhileVisible()
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
                // Comptes à rebours « chaque année » passés : avancés dès que la date du jour change.
                LaunchedEffect(today) { vm.rollYearly(today) }
                var sendTime by remember { mutableStateOf(NotificationSettings.sendTime(this)) }
                // Confirmation d'installation demandée par le système : lancée seulement app visible.
                val updater = (application as GoalWidgetsApplication).updater
                val updateState by updater.state.collectAsStateWithLifecycle()
                LaunchedEffect(updateState) {
                    val confirm = updateState as? UpdateState.ConfirmationRequired ?: return@LaunchedEffect
                    runCatching { startActivity(confirm.intent) }
                    updater.onConfirmationShown(confirm)
                }
                Box(Modifier.fillMaxSize()) {
                    GoalWidgetsApp(
                        goals = goals,
                        order = order,
                        today = today,
                        sendTime = sendTime,
                        snackbarHostState = snackbar,
                        openRequest = openRequest,
                        onOpenRequestHandled = { openRequest = null },
                        actions = AppActions(
                            add = vm::add,
                            update = vm::update,
                            duplicate = vm::duplicate,
                            setArchived = vm::setArchived,
                            resetStreak = { id, on -> vm.resetStreak(id, on, today) },
                            delete = vm::delete,
                            continueAsSince = vm::continueAsSince,
                            setOrder = vm::setOrder,
                            export = { exportLauncher.launch("goal-widgets-$today.json") },
                            import = { importLauncher.launch(IMPORT_MIME_TYPES) },
                            share = { id -> scope.launch { GoalSharing.share(this@MainActivity, id) } },
                            shareImage = { id -> scope.launch { GoalImageShare.share(this@MainActivity, id) } },
                            setSendTime = {
                                NotificationSettings.setSendTime(this@MainActivity, it)
                                sendTime = it
                                (application as GoalWidgetsApplication).rescheduleNotifications()
                            },
                        ),
                    )
                    UpdateIndicator(Modifier.align(Alignment.TopCenter))
                }
            }
        }
    }

    /**
     * Mises à jour : une recherche à chaque retour au premier plan, puis toutes les 15 min tant que
     * l'app reste visible. Rien en arrière-plan (pas de service ni de WorkManager).
     */
    private fun checkForUpdatesWhileVisible() {
        val updater = (application as GoalWidgetsApplication).updater
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    updater.requestCheck()
                    delay(UPDATE_CHECK_INTERVAL)
                }
            }
        }
    }

    /**
     * App déjà ouverte (widget/notification : `CLEAR_TOP | SINGLE_TOP`) : l'activité est réutilisée ;
     * un formulaire en cours est conservé, le détail s'ouvre à sa fermeture.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestOpen(intent)
    }

    private fun requestOpen(intent: Intent) {
        if (intent.action == ACTION_NEW_GOAL) {
            openRequest = OpenGoalRequest(null, ++openRequestCount)
            return
        }
        val goalId = intent.getLongExtra(EXTRA_GOAL_ID, -1L).takeIf { it >= 0 } ?: return
        openRequest = OpenGoalRequest(goalId, ++openRequestCount)
    }

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
        private val UPDATE_CHECK_INTERVAL = 15.minutes

        /** Id (Long) de l'objectif dont le détail s'ouvre (appui sur un widget, une notification…). */
        const val EXTRA_GOAL_ID = "app.goalwidgets.extra.GOAL_ID"

        /** Raccourci « Nouvel objectif » : ouvre directement le formulaire de création. */
        const val ACTION_NEW_GOAL = "app.goalwidgets.action.NEW_GOAL"

        private val factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as GoalWidgetsApplication
                GoalListViewModel(
                    app.database.goalDao(),
                    onGoalsChanged = app::onGoalsChanged,
                    initialOrder = GoalListOrder.read(app),
                    onOrderChanged = {
                        GoalListOrder.write(app, it)
                        // Le widget « Liste » suit le tri de l'app.
                        app.scope.launch { GoalWidgets.refreshAll(app) }
                    },
                )
            }
        }
    }
}
