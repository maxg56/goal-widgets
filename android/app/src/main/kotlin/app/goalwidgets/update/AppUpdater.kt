package app.goalwidgets.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import app.goalwidgets.BuildConfig
import app.goalwidgets.core.LatestRelease
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Où en est la mise à jour ; l'UI n'affiche rien pour [Idle]. */
sealed interface UpdateState {
    data object Idle : UpdateState

    /** Recherche manuelle en cours (la recherche automatique reste invisible). */
    data object Checking : UpdateState

    /** Recherche manuelle : aucune version plus récente. */
    data object UpToDate : UpdateState

    /** Recherche manuelle : réseau ou API indisponible. */
    data object CheckFailed : UpdateState

    /** L'utilisateur doit autoriser l'app à installer des applications (sources inconnues). */
    data class NeedsPermission(val release: LatestRelease) : UpdateState

    /** L'utilisateur a refusé la confirmation : proposée à nouveau d'un appui, sans popup forcée. */
    data class Available(val release: LatestRelease) : UpdateState

    data class Downloading(val release: LatestRelease) : UpdateState

    /** APK confié au système (session PackageInstaller). */
    data class Installing(val release: LatestRelease) : UpdateState

    /** Le système demande une confirmation : l'activité lance [intent] dès qu'elle est visible. */
    data class ConfirmationRequired(val release: LatestRelease?, val intent: Intent) : UpdateState

    data class Failed(val reason: FailureReason) : UpdateState
}

enum class FailureReason {
    /** APK signé avec une autre clé que l'app installée : Android refuse la mise à jour. */
    SIGNATURE,

    /** Installation bloquée par le système (Samsung « Bloqueur automatique », politique…). */
    BLOCKED,

    /** Téléchargement ou installation échoués pour une autre raison. */
    OTHER,
}

/**
 * Mises à jour automatiques depuis les GitHub Releases publiées par la CI, **seulement quand l'app
 * est ouverte** : [requestCheck] est appelé au retour au premier plan et toutes les 15 min
 * (MainActivity). Aucun service ni WorkManager.
 *
 * Recherche → (autorisation « sources inconnues ») → téléchargement dans le cache (taille vérifiée)
 * → contrôle de la signature → session PackageInstaller. Les échecs de la recherche automatique sont
 * silencieux. Seule donnée envoyée : un GET anonyme sur l'API publique de GitHub.
 */
class AppUpdater(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val installedVersionName: String = BuildConfig.VERSION_NAME
    private val installedVersionCode: Long = BuildConfig.VERSION_CODE.toLong()

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var job: Job? = null
    private var cached: LatestRelease? = null
    private var cachedAt = 0L

    /**
     * Lance une recherche (et la suite) si aucune n'est en cours. [manual] : bouton « Rechercher une
     * mise à jour » — affiche le résultat, ignore le cache, réessaie une version refusée ou bloquée.
     */
    fun requestCheck(manual: Boolean = false) {
        if (!BuildConfig.AUTO_UPDATE) {
            // Build « preview » (autre applicationId) : la release publiée n'est pas pour lui.
            if (manual) _state.value = UpdateState.UpToDate
            return
        }
        if (job?.isActive == true) return
        // Installation en attente (confirmation, résultat du système) : pas de 2e session en parallèle.
        val pending = _state.value is UpdateState.ConfirmationRequired || _state.value is UpdateState.Installing
        if (!manual && pending) return
        job = scope.launch { run(manual) }
    }

    /** L'activité a lancé l'écran de confirmation du système. */
    fun onConfirmationShown(state: UpdateState.ConfirmationRequired) {
        if (_state.value == state) _state.value = state.release?.let(UpdateState::Installing) ?: UpdateState.Idle
    }

    /** Ferme le message affiché (échec, « à jour »). */
    fun dismiss() {
        when (_state.value) {
            is UpdateState.Failed, UpdateState.UpToDate, UpdateState.CheckFailed -> _state.value = UpdateState.Idle
            else -> Unit
        }
    }

    /** « Mise à jour disponible » touché après un refus : relance l'installation. */
    fun installAvailable() {
        val release = (_state.value as? UpdateState.Available)?.release ?: return
        prefs.edit().remove(PREF_DECLINED).apply()
        if (job?.isActive == true) return
        job = scope.launch { downloadAndInstall(release, manual = true) }
    }

    /** Écran système « Installer des applis inconnues » pour cette app. */
    fun openInstallPermissionSettings(from: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        runCatching { from.startActivity(intent) }
    }

    private suspend fun run(manual: Boolean) {
        if (manual) _state.value = UpdateState.Checking
        val release = latestRelease(fresh = manual)
        if (release == null) {
            _state.value = if (manual) UpdateState.CheckFailed else idleUnlessShowing()
            return
        }
        if (!release.isNewerThan(installedVersionCode)) {
            _state.value = if (manual) UpdateState.UpToDate else UpdateState.Idle
            return
        }
        if (manual) {
            prefs.edit().remove(PREF_DECLINED).remove(PREF_BLOCKED).apply()
        } else {
            // Version déjà refusée par l'utilisateur ou impossible à installer : pas de nouvelle tentative auto.
            if (prefs.getLong(PREF_BLOCKED, -1) == release.versionCode) return
            if (prefs.getLong(PREF_DECLINED, -1) == release.versionCode) {
                _state.value = UpdateState.Available(release)
                return
            }
        }
        downloadAndInstall(release, manual)
    }

    /** Recherche automatique sans résultat : on garde un message en cours (échec signature…). */
    private fun idleUnlessShowing(): UpdateState = when (val s = _state.value) {
        is UpdateState.Failed, is UpdateState.Available, is UpdateState.NeedsPermission -> s
        else -> UpdateState.Idle
    }

    private suspend fun downloadAndInstall(release: LatestRelease, manual: Boolean) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            // Bandeau discret : l'utilisateur autorise, revient, et la recherche suivante continue.
            _state.value = UpdateState.NeedsPermission(release)
            return
        }
        _state.value = UpdateState.Downloading(release)
        val apk = try {
            download(release)
        } catch (e: Exception) {
            _state.value = if (manual) UpdateState.Failed(FailureReason.OTHER) else UpdateState.Idle
            return
        }
        // Build de debug d'une autre machine, ou release signée autrement : Android refuserait.
        if (withContext(Dispatchers.IO) { signatureMatches(apk) } == false) {
            apk.delete()
            onInstallFailed(release.versionCode, FailureReason.SIGNATURE)
            return
        }
        _state.value = UpdateState.Installing(release)
        try {
            withContext(Dispatchers.IO) { commitSession(apk, release) }
        } catch (e: Exception) {
            _state.value = if (manual) UpdateState.Failed(FailureReason.OTHER) else UpdateState.Idle
        }
    }

    // --- Résultats de la session (UpdateInstallReceiver) ---------------------------------------

    internal fun onUserActionRequired(versionCode: Long, intent: Intent) {
        val release = currentRelease()?.takeIf { it.versionCode == versionCode }
        _state.value = UpdateState.ConfirmationRequired(release, intent)
    }

    internal fun onDeclined(versionCode: Long) {
        prefs.edit().putLong(PREF_DECLINED, versionCode).apply()
        val release = currentRelease()?.takeIf { it.versionCode == versionCode }
        _state.value = release?.let(UpdateState::Available) ?: UpdateState.Idle
    }

    internal fun onInstallFailed(versionCode: Long, reason: FailureReason) {
        if (reason != FailureReason.OTHER) prefs.edit().putLong(PREF_BLOCKED, versionCode).apply()
        _state.value = UpdateState.Failed(reason)
    }

    internal fun onInstalled() {
        // En pratique le processus est arrêté par le système juste après le remplacement.
        _state.value = UpdateState.Idle
        cacheDir().listFiles()?.forEach { it.delete() }
    }

    private fun currentRelease(): LatestRelease? = when (val s = _state.value) {
        is UpdateState.Installing -> s.release
        is UpdateState.Downloading -> s.release
        is UpdateState.ConfirmationRequired -> s.release
        else -> cached
    }

    // --- Réseau ---------------------------------------------------------------------------------

    private suspend fun latestRelease(fresh: Boolean): LatestRelease? {
        val now = System.currentTimeMillis()
        // Retours au premier plan rapprochés : pas de nouvel appel (limite anonyme de GitHub : 60/h).
        if (!fresh && cached != null && now - cachedAt < MIN_CHECK_INTERVAL_MS) return cached
        val json = withContext(Dispatchers.IO) {
            runCatching { get(LATEST_RELEASE_URL) }.getOrNull()
        } ?: return null
        return LatestRelease.parse(json)?.also {
            cached = it
            cachedAt = now
        }
    }

    private fun get(url: String): String? {
        val connection = open(url)
        try {
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** Télécharge l'APK dans le cache de l'app ; échoue si la taille diffère de celle annoncée. */
    private suspend fun download(release: LatestRelease): File = withContext(Dispatchers.IO) {
        val dir = cacheDir().apply { mkdirs() }
        val target = File(dir, "goal-widgets-${release.versionCode}.apk")
        dir.listFiles()?.filter { it != target }?.forEach { it.delete() }
        if (target.length() == release.apkSize) return@withContext target // déjà téléchargé
        val part = File(dir, target.name + ".part")
        val connection = open(release.apkUrl)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            var written = 0L
            connection.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        written += n
                        if (written > release.apkSize) throw IOException("APK plus gros qu'annoncé")
                        output.write(buffer, 0, n)
                    }
                }
            }
            if (written != release.apkSize) throw IOException("APK tronqué ($written / ${release.apkSize})")
            if (!part.renameTo(target)) throw IOException("renommage impossible")
            target
        } catch (e: Exception) {
            part.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true // l'asset redirige vers objects.githubusercontent.com (https → https)
        setRequestProperty("User-Agent", USER_AGENT)
    }

    private fun cacheDir() = File(context.cacheDir, "updates")

    // --- Installation ---------------------------------------------------------------------------

    /**
     * `false` si l'APK est signé par une autre clé (ou est une autre app) ; `null` si le système ne
     * sait pas le dire (on laisse alors PackageInstaller trancher).
     */
    @Suppress("DEPRECATION")
    private fun signatureMatches(apk: File): Boolean? {
        val pm = context.packageManager
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val flags = PackageManager.GET_SIGNING_CERTIFICATES
                val archive = pm.getPackageArchiveInfo(apk.path, flags) ?: return null
                if (archive.packageName != context.packageName) return false
                val archiveSigners = archive.signingInfo?.apkContentsSigners?.toSet() ?: return null
                val installedSigners = pm.getPackageInfo(context.packageName, flags).signingInfo?.apkContentsSigners?.toSet()
                    ?: return null
                archiveSigners == installedSigners
            } else {
                val flags = PackageManager.GET_SIGNATURES
                val archive = pm.getPackageArchiveInfo(apk.path, flags) ?: return null
                if (archive.packageName != context.packageName) return false
                val archiveSigners = archive.signatures?.toSet() ?: return null
                archiveSigners == pm.getPackageInfo(context.packageName, flags).signatures?.toSet()
            }
        }.getOrNull()
    }

    private fun commitSession(apk: File, release: LatestRelease) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            // Android 12+ : silencieux si l'app est déjà l'« installeur » d'elle-même (après la 1re fois).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val callback = Intent(context, UpdateInstallReceiver::class.java)
                    .setPackage(context.packageName)
                    .putExtra(UpdateInstallReceiver.EXTRA_VERSION_CODE, release.versionCode)
                // Mutable : le système y ajoute le statut et l'intent de confirmation.
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(context, sessionId, callback, flags)
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }
    }

    companion object {
        /**
         * Au démarrage : supprime les APK déjà installés ou abandonnés (`cache/updates`). Après une
         * mise à jour réussie, le système arrête le processus avant [onInstalled] : sans ce ménage,
         * l'APK téléchargé (plusieurs Mo) resterait dans le cache indéfiniment.
         */
        fun purgeStaleDownloads(context: Context) {
            val installed = BuildConfig.VERSION_CODE.toLong()
            File(context.cacheDir, "updates").listFiles()?.forEach { file ->
                val code = APK_NAME.matchEntire(file.name)?.groupValues?.get(1)?.toLongOrNull()
                if (code == null || code <= installed) file.delete()
            }
        }

        private val APK_NAME = Regex("""goal-widgets-(\d+)\.apk""")
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/maxg56/goal-widgets/releases/latest"
        private const val USER_AGENT = "GoalWidgets-Updater"
        private const val MIN_CHECK_INTERVAL_MS = 60_000L
        private const val PREFS = "updates"
        private const val PREF_DECLINED = "declined_version"
        private const val PREF_BLOCKED = "blocked_version"
    }
}
