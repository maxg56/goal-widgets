package app.goalwidgets.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    /** Objectifs, cible la plus proche d'abord (les objectifs sans cible en dernier). */
    @Query("SELECT * FROM goals ORDER BY targetEpochDay IS NULL, targetEpochDay, id")
    fun observeAll(): Flow<List<GoalEntity>>

    @Insert
    suspend fun insert(goal: GoalEntity): Long
}
