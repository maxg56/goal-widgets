package app.goalwidgets.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schéma 3 → 4 : compteurs « série » remis à zéro. `streak` = remise à zéro possible (désactivée
 * pour les objectifs existants), `streakHistory` = séries terminées (`Streaks.encode`, vide).
 * Aucune ligne recréée ni supprimée.
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Migration3To4.STATEMENTS.forEach(db::execSQL)
    }
}

object Migration3To4 {
    /** Exécutées dans l'ordre ; testées en JVM contre SQLite (`Migration3To4Test`). */
    val STATEMENTS: List<String> = listOf(
        "ALTER TABLE `goals` ADD COLUMN `streak` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `goals` ADD COLUMN `streakHistory` TEXT NOT NULL DEFAULT ''",
    )
}
