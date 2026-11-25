package app.goalwidgets.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import app.goalwidgets.core.ExportedGoal
import app.goalwidgets.core.GoalImport
import app.goalwidgets.core.ImportPlan
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    /**
     * Objectifs, cible la plus proche d'abord (les objectifs sans cible en dernier). La liste
     * de l'app les retrie selon le choix de l'utilisateur (`GoalOrder`, t10).
     */
    @Query("SELECT * FROM goals ORDER BY targetEpochDay IS NULL, targetEpochDay, id")
    fun observeAll(): Flow<List<GoalEntity>>

    /** Un objectif, ou `null` s'il n'existe pas (ou plus). */
    @Query("SELECT * FROM goals WHERE id = :id")
    fun observeById(id: Long): Flow<GoalEntity?>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getById(id: Long): GoalEntity?

    /** Interrupteur de notifications d'un objectif ; renvoie le nombre de lignes modifiées. */
    @Query("UPDATE goals SET notificationsEnabled = :enabled WHERE id = :id")
    suspend fun setNotificationsEnabled(id: Long, enabled: Boolean): Int

    @Insert
    suspend fun insert(goal: GoalEntity): Long

    /** Tous les objectifs, dans l'ordre de création (export, reprogrammation des notifications). */
    @Query("SELECT * FROM goals ORDER BY createdAtMillis, id")
    suspend fun getAll(): List<GoalEntity>

    /** Identifiants portables (UUID) déjà présents : base du dédoublonnage à l'import. */
    @Query("SELECT uid FROM goals")
    suspend fun allUids(): List<String>

    @Insert
    suspend fun insertAll(goals: List<GoalEntity>): List<Long>

    /**
     * Ajoute les objectifs importés absents de la base (comparaison sur l'UUID, cf. [GoalImport]).
     * Une seule transaction : en cas d'échec rien n'est écrit ; aucun objectif existant n'est modifié.
     */
    @Transaction
    suspend fun importGoals(goals: List<ExportedGoal>): ImportPlan {
        val plan = GoalImport.plan(existingIds = allUids().toSet(), incoming = goals)
        insertAll(plan.toAdd.map(GoalEntity::fromExported))
        return plan
    }

    /**
     * Remplace le contenu modifiable d'un objectif (titre, type, dates, couleur, emoji) en conservant
     * son `id` (les widgets qui y sont liés restent valides), son `uid` et son `createdAtMillis`.
     * Renvoie le nombre de lignes modifiées (0 si l'objectif n'existe plus).
     */
    @Query(
        "UPDATE goals SET title = :title, kind = :kind, startEpochDay = :startEpochDay, " +
            "targetEpochDay = :targetEpochDay, color = :color, emoji = :emoji WHERE id = :id",
    )
    suspend fun updateContent(
        id: Long,
        title: String,
        kind: String,
        startEpochDay: Long,
        targetEpochDay: Long?,
        color: String?,
        emoji: String?,
    ): Int

    /**
     * Modification d'un objectif depuis l'app : contenu ([updateContent]) et, si
     * [notificationsEnabled] n'est pas `null`, interrupteur de notifications, en une seule
     * transaction (aucun lecteur — widget, reprogrammation — ne voit un état à moitié écrit).
     * Renvoie le nombre de lignes modifiées par [updateContent] (0 si l'objectif n'existe plus).
     */
    @Transaction
    suspend fun updateGoal(
        id: Long,
        title: String,
        kind: String,
        startEpochDay: Long,
        targetEpochDay: Long?,
        color: String?,
        emoji: String?,
        notificationsEnabled: Boolean?,
    ): Int {
        val updated = updateContent(id, title, kind, startEpochDay, targetEpochDay, color, emoji)
        if (notificationsEnabled != null) setNotificationsEnabled(id, notificationsEnabled)
        return updated
    }

    /** Supprime un objectif ; renvoie le nombre de lignes supprimées. */
    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
