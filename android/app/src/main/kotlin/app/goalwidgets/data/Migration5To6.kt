package app.goalwidgets.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schéma 5 → 6 : comptes à rebours « chaque année » (`repeatYearly`) et objectifs archivés
 * (`archived`), désactivés pour les objectifs existants. Aucune ligne recréée ni supprimée.
 */
val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Migration5To6.STATEMENTS.forEach(db::execSQL)
    }
}

object Migration5To6 {
    /** Exécutées dans l'ordre ; testées en JVM contre SQLite (`Migration5To6Test`). */
    val STATEMENTS: List<String> = listOf(
        "ALTER TABLE `goals` ADD COLUMN `repeatYearly` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `goals` ADD COLUMN `archived` INTEGER NOT NULL DEFAULT 0",
    )
}
