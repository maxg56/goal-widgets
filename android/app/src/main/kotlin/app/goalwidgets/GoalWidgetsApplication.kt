package app.goalwidgets

import android.app.Application
import app.goalwidgets.data.GoalDatabase
import app.goalwidgets.notifications.DailyRescheduleWorker
import app.goalwidgets.notifications.MilestoneNotifications
import app.goalwidgets.update.AppUpdater
import app.goalwidgets.widget.GoalWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GoalWidgetsApplication : Application() {
    val database: GoalDatabase by lazy { GoalDatabase.create(this) }

    /** Travail de fond lié au processus (reprogrammation des notifications). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Mises à jour depuis les GitHub Releases, déclenchées par MainActivity quand l'app est ouverte. */
    val updater: AppUpdater by lazy { AppUpdater(this, scope) }

    override fun onCreate() {
        super.onCreate()
        // Démarrage de l'app : canal, tâche quotidienne et reprogrammation de l'horizon.
        MilestoneNotifications.createChannel(this)
        DailyRescheduleWorker.enqueue(this)
        rescheduleNotifications()
    }

    /**
     * Après toute écriture d'objectifs (ajout, modification, suppression, import, objectif partagé
     * reçu) : widgets redessinés et notifications reprogrammées.
     */
    suspend fun onGoalsChanged() {
        GoalWidgets.refreshAll(this)
        rescheduleNotifications()
    }

    /** À appeler après toute modification d'objectif ou de l'heure d'envoi. */
    fun rescheduleNotifications() {
        scope.launch { MilestoneNotifications.reschedule(this@GoalWidgetsApplication) }
    }
}
