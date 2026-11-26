package app.goalwidgets.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schéma 4 → 5 : compteurs « série » remis à zéro. `streak` = remise à zéro possible (désactivée
 * pour les objectifs existants), `streakHistory` = séries terminées (`Streaks.encode`, vide).
 * Aucune ligne recréée ni supprimée.
 */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Migration4To5.STATEMENTS.forEach(db::execSQL)
    }
}

object Migration4To5 {
    /** Exécutées dans l'ordre ; testées en JVM contre SQLite (`Migration4To5Test`). */
    val STATEMENTS: List<String> = listOf(
        "ALTER TABLE `goals` ADD COLUMN `streak` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `goals` ADD COLUMN `streakHistory` TEXT NOT NULL DEFAULT ''",
    )
}
