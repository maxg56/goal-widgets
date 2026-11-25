package app.goalwidgets.core

import java.time.Instant

/**
 * Partage d'un objectif avec une autre personne (ticket 13) : un export `goal-widgets-export` v1
 * à **un seul** objectif, relu par l'import habituel ([GoalExport.decode] + [GoalImport.plan]).
 * Le réglage de notifications n'est pas transmis (`null` : le destinataire décide).
 */
object GoalShare {
    const val EXTENSION = ".goalwidgets.json"
    const val MIME_TYPE = "application/json"

    /** Longueur maximale du titre dans le nom de fichier (les systèmes de fichiers limitent à ~255 octets). */
    private const val MAX_NAME_LENGTH = 80
    private val FORBIDDEN = Regex("[/\\\\:*?\"<>|\\p{Cntrl}]")

    fun encode(goal: ExportedGoal, sharedAt: Instant): String =
        GoalExport.encode(listOf(goal.copy(notificationsEnabled = null)), exportedAt = sharedAt)

    /** `<titre>.goalwidgets.json`, caractères interdits remplacés par `_`. */
    fun fileName(title: String): String {
        val cut = title.trim().replace(FORBIDDEN, "_").take(MAX_NAME_LENGTH)
        // Ne pas couper un emoji en deux (paire de substitution UTF-16).
        val whole = if (cut.lastOrNull()?.isHighSurrogate() == true) cut.dropLast(1) else cut
        val base = whole.trim().ifEmpty { "objectif" }
        return base + EXTENSION
    }
}
