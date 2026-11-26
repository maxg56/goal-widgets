package app.goalwidgets.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.goalwidgets.GoalWidgetsApplication
import app.goalwidgets.R

@Composable
private fun rememberUpdater(): AppUpdater {
    val context = LocalContext.current
    return remember(context) { (context.applicationContext as GoalWidgetsApplication).updater }
}

/**
 * Petite pastille en haut de l'écran, au-dessus de tous les écrans : téléchargement en cours,
 * autorisation à donner, mise à jour refusée ou impossible. Rien sinon (recherche silencieuse).
 */
@Composable
fun UpdateIndicator(modifier: Modifier = Modifier) {
    val updater = rememberUpdater()
    val context = LocalContext.current
    val state by updater.state.collectAsStateWithLifecycle()
    val pill: Pair<Int, () -> Unit>? = when (val s = state) {
        is UpdateState.Downloading, is UpdateState.Installing -> R.string.update_in_progress to {}
        is UpdateState.NeedsPermission -> R.string.update_needs_permission to { updater.openInstallPermissionSettings(context) }
        is UpdateState.Available -> R.string.update_available_install to updater::installAvailable
        is UpdateState.Failed -> s.reason.message to updater::dismiss
        else -> null
    }
    AnimatedVisibility(
        visible = pill != null,
        modifier = modifier.statusBarsPadding().padding(top = 8.dp, start = 16.dp, end = 16.dp),
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        val (text, onClick) = pill ?: return@AnimatedVisibility
        val busy = state is UpdateState.Downloading || state is UpdateState.Installing
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 4.dp,
            modifier = Modifier.clickable(enabled = !busy, onClick = onClick),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
                Text(stringResource(text), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Ligne « Version x · Rechercher une mise à jour » (réglages), avec le résultat de la recherche. */
@Composable
fun UpdateSettingsRow() {
    val updater = rememberUpdater()
    val state by updater.state.collectAsStateWithLifecycle()
    val status: Int? = when (val s = state) {
        UpdateState.Checking -> R.string.update_checking
        UpdateState.UpToDate -> R.string.update_up_to_date
        UpdateState.CheckFailed -> R.string.update_check_failed
        is UpdateState.Downloading, is UpdateState.Installing, is UpdateState.ConfirmationRequired -> R.string.update_in_progress
        is UpdateState.NeedsPermission -> R.string.update_needs_permission
        is UpdateState.Available -> R.string.update_available_install
        is UpdateState.Failed -> s.reason.message
        UpdateState.Idle -> null
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { updater.requestCheck(manual = true) }
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(stringResource(R.string.update_version_check, updater.installedVersionName))
        if (status != null) {
            Text(
                stringResource(status),
                style = MaterialTheme.typography.bodySmall,
                color = if (state is UpdateState.Failed || state is UpdateState.CheckFailed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

private val FailureReason.message: Int
    get() = when (this) {
        FailureReason.SIGNATURE -> R.string.update_failed_signature
        FailureReason.BLOCKED -> R.string.update_failed_blocked
        FailureReason.OTHER -> R.string.update_failed
    }
