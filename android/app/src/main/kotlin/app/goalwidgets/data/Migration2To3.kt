package app.goalwidgets.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schéma 2 → 3 (ticket 08) : interrupteur de notifications de jalons par objectif. Les objectifs
 * existants restent sans notifications (0), comme les nouveaux. Aucune ligne recréée ni supprimée.
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Migration2To3.STATEMENTS.forEach(db::execSQL)
    }
}

object Migration2To3 {
    /** Exécutées dans l'ordre ; testées en JVM contre SQLite (`Migration2To3Test`). */
    val STATEMENTS: List<String> = listOf(
        "ALTER TABLE `goals` ADD COLUMN `notificationsEnabled` INTEGER NOT NULL DEFAULT 0",
    )
}
