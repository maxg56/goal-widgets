package app.goalwidgets.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [GoalEntity::class], version = 7, exportSchema = true)
abstract class GoalDatabase : RoomDatabase() {
    abstract fun goalDao(): GoalDao

    companion object {
        fun create(context: Context): GoalDatabase =
            Room.databaseBuilder(context.applicationContext, GoalDatabase::class.java, "goals.db")
                // Une migration par fichier et par ticket : 1 → 2 (t10), 2 → 3 (t08), 3 → 4 (unité des graphiques), 4 → 5 (séries), 5 → 6 (chaque année, archives), 6 → 7 (réglages de notification).
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
    }
}
