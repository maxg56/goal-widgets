package app.goalwidgets.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.goalwidgets.core.LocalMidnight
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Date du jour locale (fuseau du téléphone), tenue à jour tant que l'écran est visible :
 * relue à chaque passage au premier plan, au minuit local ([LocalMidnight.next]) et quand
 * l'heure, le fuseau ou la date changent (réglage manuel, voyage). La liste et la validation
 * du formulaire (`START_IN_FUTURE`) ne restent donc pas sur la veille si l'app reste ouverte.
 */
@Composable
fun rememberLocalToday(): State<LocalDate> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val today = remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(lifecycleOwner, context) {
        // Hors de l'état STARTED (écran éteint, app en arrière-plan) : ni minuterie ni récepteur.
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            clockChanges(context).onStart { emit(Unit) }.collectLatest {
                // Après un changement d'heure ou de fuseau, le minuit visé est recalculé.
                while (true) {
                    today.value = LocalDate.now()
                    val now = ZonedDateTime.now()
                    val untilMidnight = Duration.between(now, LocalMidnight.next(now)).toMillis()
                    // Petite marge : se réveiller juste après minuit, pas juste avant.
                    delay(untilMidnight.coerceAtLeast(0) + MIDNIGHT_MARGIN_MILLIS)
                }
            }
        }
    }
    return today
}

private const val MIDNIGHT_MARGIN_MILLIS = 500L

/** Diffusions système d'heure, de fuseau et de date, reçues seulement pendant la collecte. */
private fun clockChanges(context: Context) = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            trySend(Unit)
        }
    }
    val filter = IntentFilter().apply {
        addAction(Intent.ACTION_TIME_CHANGED)
        addAction(Intent.ACTION_TIMEZONE_CHANGED)
        addAction(Intent.ACTION_DATE_CHANGED)
    }
    // Diffusions du système : reçues même avec un récepteur non exporté.
    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    awaitClose { context.unregisterReceiver(receiver) }
}
