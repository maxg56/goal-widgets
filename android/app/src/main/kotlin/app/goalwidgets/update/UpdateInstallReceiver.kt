package app.goalwidgets.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import app.goalwidgets.GoalWidgetsApplication

/**
 * Résultat de la session PackageInstaller ouverte par [AppUpdater]. `STATUS_PENDING_USER_ACTION`
 * (première mise à jour, ou Android < 12) : l'intent de confirmation du système est confié à
 * l'updater, que MainActivity lance dès qu'elle est visible.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val updater = (context.applicationContext as GoalWidgetsApplication).updater
        val versionCode = intent.getLongExtra(EXTRA_VERSION_CODE, -1)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm != null) updater.onUserActionRequired(versionCode, confirm)
                else updater.onInstallFailed(versionCode, FailureReason.OTHER)
            }
            PackageInstaller.STATUS_SUCCESS -> updater.onInstalled()
            PackageInstaller.STATUS_FAILURE_ABORTED -> updater.onDeclined(versionCode)
            else -> updater.onInstallFailed(versionCode, reasonOf(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)))
        }
    }

    companion object {
        const val EXTRA_VERSION_CODE = "app.goalwidgets.update.extra.VERSION_CODE"

        /** Signature différente = INSTALL_FAILED_UPDATE_INCOMPATIBLE, rapporté en STATUS_FAILURE_CONFLICT. */
        internal fun reasonOf(status: Int, message: String?): FailureReason {
            val text = message.orEmpty()
            return when {
                status == PackageInstaller.STATUS_FAILURE_CONFLICT ||
                    text.contains("UPDATE_INCOMPATIBLE") || text.contains("signature", ignoreCase = true) -> FailureReason.SIGNATURE
                status == PackageInstaller.STATUS_FAILURE_BLOCKED -> FailureReason.BLOCKED
                else -> FailureReason.OTHER
            }
        }
    }
}
