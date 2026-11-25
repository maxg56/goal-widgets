package app.goalwidgets.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [GoalEntity::class], version = 1, exportSchema = true)
abstract class GoalDatabase : RoomDatabase() {
    abstract fun goalDao(): GoalDao

    companion object {
        fun create(context: Context): GoalDatabase =
            Room.databaseBuilder(context.applicationContext, GoalDatabase::class.java, "goals.db").build()
    }
}
