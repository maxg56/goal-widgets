package app.goalwidgets.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * t08 : interrupteur de notifications par objectif. Les objectifs existants restent
 * sans notifications (défaut 0), comme les nouveaux.
 *
 * Version 1 → 2 (t10, développé en parallèle, n'était pas fusionné). Si t10 (uid/color/emoji)
 * prend la version 2 avant, renuméroter en `Migration(2, 3)`, `version = 3`, régénérer le schéma.
 * Nom volontairement distinct de `MIGRATION_1_2` de t10 (pas de déclaration en conflit).
 */
val MIGRATION_NOTIFICATIONS = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE goals ADD COLUMN notificationsEnabled INTEGER NOT NULL DEFAULT 0")
    }
}
