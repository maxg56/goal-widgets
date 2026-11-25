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

    /** Un objectif, ou `null` s'il n'existe pas (ou plus). */
    @Query("SELECT * FROM goals WHERE id = :id")
    fun observeById(id: Long): Flow<GoalEntity?>

    /** Instantané de tous les objectifs (reprogrammation des notifications). */
    @Query("SELECT * FROM goals")
    suspend fun getAll(): List<GoalEntity>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getById(id: Long): GoalEntity?

    /** Interrupteur de notifications d'un objectif ; renvoie le nombre de lignes modifiées. */
    @Query("UPDATE goals SET notificationsEnabled = :enabled WHERE id = :id")
    suspend fun setNotificationsEnabled(id: Long, enabled: Boolean): Int

    @Insert
    suspend fun insert(goal: GoalEntity): Long

    /**
     * Remplace le contenu modifiable d'un objectif (titre, type, dates) en conservant
     * son `id` (les widgets qui y sont liés restent valides) et son `createdAtMillis`.
     * Renvoie le nombre de lignes modifiées (0 si l'objectif n'existe plus).
     */
    @Query(
        "UPDATE goals SET title = :title, kind = :kind, startEpochDay = :startEpochDay, " +
            "targetEpochDay = :targetEpochDay WHERE id = :id",
    )
    suspend fun updateContent(id: Long, title: String, kind: String, startEpochDay: Long, targetEpochDay: Long?): Int

    /** Supprime un objectif ; renvoie le nombre de lignes supprimées. */
    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
