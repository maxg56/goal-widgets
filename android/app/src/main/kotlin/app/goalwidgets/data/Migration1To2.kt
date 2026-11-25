package app.goalwidgets.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schéma 1 → 2 (ticket 10) : couleur, emoji et identifiant portable `uid` (UUID v4 aléatoire
 * généré pour chaque objectif existant). Aucune ligne n'est recréée ni supprimée.
 * Ticket 08 : sa colonne `notificationsEnabled` va dans une migration séparée 2 → 3.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Migration1To2.STATEMENTS.forEach(db::execSQL)
    }
}

object Migration1To2 {
    /** Exécutées dans l'ordre ; testées en JVM contre SQLite (`Migration1To2Test`). */
    val STATEMENTS: List<String> = listOf(
        "ALTER TABLE `goals` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE `goals` ADD COLUMN `color` TEXT",
        "ALTER TABLE `goals` ADD COLUMN `emoji` TEXT",
        // UUID v4 en minuscules : 8-4-4-4-12, version 4, variante 8/9/a/b.
        "UPDATE `goals` SET `uid` = " +
            "lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || " +
            "substr(lower(hex(randomblob(2))), 2) || '-' || " +
            "substr('89ab', 1 + (abs(random()) % 4), 1) || substr(lower(hex(randomblob(2))), 2) || '-' || " +
            "lower(hex(randomblob(6)))",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_goals_uid` ON `goals` (`uid`)",
    )
}
