package app.goalwidgets.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [GoalEntity::class], version = 3, exportSchema = true)
abstract class GoalDatabase : RoomDatabase() {
    abstract fun goalDao(): GoalDao

    companion object {
        fun create(context: Context): GoalDatabase =
            Room.databaseBuilder(context.applicationContext, GoalDatabase::class.java, "goals.db")
                // Une migration par fichier et par ticket : 1 → 2 (t10), 2 → 3 (t08).
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
