package app.goalwidgets.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schéma 6 → 7 : réglages de notification par objectif (`notificationPrefs`, `NotificationPrefs.encode`),
 * vides = réglages par défaut pour les objectifs existants. Aucune ligne recréée ni supprimée.
 */
val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Migration6To7.STATEMENTS.forEach(db::execSQL)
    }
}

object Migration6To7 {
    /** Exécutées dans l'ordre ; testées en JVM contre SQLite (`Migration6To7Test`). */
    val STATEMENTS: List<String> = listOf(
        "ALTER TABLE `goals` ADD COLUMN `notificationPrefs` TEXT NOT NULL DEFAULT ''",
    )
}
