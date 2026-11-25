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

    /** Longueur maximale du titre dans le nom de fichier (même règle qu'iOS `GoalShare.maxNameLength`). */
    private const val MAX_NAME_LENGTH = 60
    private val RESERVED = Regex("[/\\\\:*?\"<>|]")
    private val CONTROL = Regex("\\p{Cntrl}")

    fun encode(goal: ExportedGoal, sharedAt: Instant): String =
        GoalExport.encode(listOf(goal.copy(notificationsEnabled = null)), exportedAt = sharedAt)

    /**
     * `<titre>.goalwidgets.json` (même règle qu'iOS) : `/ \ : * ? " < > |` → `-`, retours à la ligne
     * et caractères de contrôle → espace, pas de point initial (fichier caché), 60 caractères au plus.
     */
    fun fileName(title: String): String {
        var cleaned = title.replace(RESERVED, "-").replace(CONTROL, " ").trim().trimStart('.')
        cleaned = cleaned.take(MAX_NAME_LENGTH)
        // Ne pas couper un emoji en deux (paire de substitution UTF-16).
        if (cleaned.lastOrNull()?.isHighSurrogate() == true) cleaned = cleaned.dropLast(1)
        return cleaned.trim().ifEmpty { "objectif" } + EXTENSION
    }
}
