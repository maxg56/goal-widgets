package app.goalwidgets.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schéma 3 → 4 : unité préférée des graphiques par objectif (`ChartUnit.wire`). `NULL` = « comme le
 * réglage général », valeur des objectifs existants. Aucune ligne recréée ni supprimée.
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Migration3To4.STATEMENTS.forEach(db::execSQL)
    }
}

object Migration3To4 {
    /** Exécutées dans l'ordre ; testées en JVM contre SQLite (`Migration3To4Test`). */
    val STATEMENTS: List<String> = listOf(
        "ALTER TABLE `goals` ADD COLUMN `chartUnit` TEXT",
    )
}
