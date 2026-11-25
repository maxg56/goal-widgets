package app.goalwidgets.share

import android.content.ContentResolver
import android.content.res.Resources
import android.net.Uri
import app.goalwidgets.R
import app.goalwidgets.core.ImportError
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Lecture d'un fichier d'objectifs (import SAF, fichier partagé reçu) et messages d'erreur. */
object GoalFiles {
    /** Au-delà, ce n'est pas un export plausible. */
    const val MAX_BYTES = 5 * 1024 * 1024

    /** Contenu UTF-8 de [uri], `null` s'il dépasse [MAX_BYTES]. [IOException] si illisible. */
    fun readText(resolver: ContentResolver, uri: Uri): String? {
        val input = resolver.openInputStream(uri) ?: throw IOException("flux indisponible")
        val out = ByteArrayOutputStream()
        input.use {
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = it.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > MAX_BYTES) return null
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }

    /** Message clair pour un fichier refusé (rien n'est écrit). */
    fun errorMessage(resources: Resources, error: ImportError): String = when (error) {
        ImportError.NotJson, ImportError.NotAnExport -> resources.getString(R.string.import_error_not_export)
        is ImportError.UnsupportedVersion -> resources.getString(R.string.import_error_unsupported_version, error.version)
        is ImportError.InvalidGoal -> resources.getString(R.string.import_error_invalid_goal, error.index + 1, error.field)
        is ImportError.DuplicateId -> resources.getString(R.string.import_error_duplicate_id)
    }
}
