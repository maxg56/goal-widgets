package app.goalwidgets.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.goalwidgets.GoalWidgetsApplication
import app.goalwidgets.R
import app.goalwidgets.core.GoalShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

/**
 * « Partager » un objectif (ticket 13) : écrit `<titre>.goalwidgets.json` (export v1 à un objectif,
 * [GoalShare]) dans le cache de l'app et ouvre la feuille de partage du système (ACTION_SEND).
 * Le fichier est exposé en lecture seule via [FileProvider] (`cache/shared/`).
 */
object GoalSharing {
    private const val DIR = "shared"

    /** Partage la version **enregistrée** de l'objectif [goalId] (rien si introuvable). */
    suspend fun share(context: Context, goalId: Long) {
        val app = context.applicationContext as GoalWidgetsApplication
        val entity = app.database.goalDao().getById(goalId) ?: return
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, DIR)
            // Un seul fichier partagé à la fois : les précédents ne servent plus.
            dir.deleteRecursively()
            dir.mkdirs()
            File(dir, GoalShare.fileName(entity.title)).apply {
                writeText(GoalShare.encode(entity.toExported(), sharedAt = Instant.now()), Charsets.UTF_8)
            }
        }
        val uri = FileProvider.getUriForFile(context, authority(context), file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(GoalShare.MIME_TYPE)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, entity.title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // ClipData : nécessaire pour que la permission de lecture suive jusqu'à l'app choisie.
        send.clipData = ClipData.newUri(context.contentResolver, file.name, uri)
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_chooser_title)))
    }

    fun authority(context: Context) = "${context.packageName}.fileprovider"
}
