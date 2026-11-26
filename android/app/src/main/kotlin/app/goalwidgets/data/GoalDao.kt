package app.goalwidgets.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import app.goalwidgets.core.ExportedGoal
import app.goalwidgets.core.GoalImport
import app.goalwidgets.core.ImportPlan
import app.goalwidgets.core.Streaks
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

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

    /** Option « série » d'un objectif ; renvoie le nombre de lignes modifiées. */
    @Query("UPDATE goals SET streak = :enabled WHERE id = :id")
    suspend fun setStreak(id: Long, enabled: Boolean): Int

    @Query("UPDATE goals SET startEpochDay = :startEpochDay, streakHistory = :history WHERE id = :id")
    suspend fun updateStreak(id: Long, startEpochDay: Long, history: String): Int

    /**
     * Remise à zéro d'un compteur « série » le jour [on] : la série en cours rejoint l'historique et le
     * compteur repart de [on] ([Streaks.reset]). Lecture et écriture dans une même transaction (deux
     * appuis rapprochés, app + widget, n'archivent pas deux fois la même série en concurrence).
     * Renvoie `false` si l'objectif n'existe plus, n'est pas une série, ou si [on] est refusée.
     */
    @Transaction
    suspend fun resetStreak(id: Long, on: LocalDate, today: LocalDate): Boolean {
        val entity = getById(id)?.takeIf { it.offersStreakReset } ?: return false
        val goal = entity.toGoal()
        if (Streaks.validate(goal, on, today) != null) return false
        val reset = Streaks.reset(goal, entity.history(), on, today)
        return updateStreak(id, reset.goal.startDate.toEpochDay(), Streaks.encode(reset.history)) > 0
    }

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
     * Remplace le contenu modifiable d'un objectif (titre, type, dates, couleur, emoji, unité des graphiques) en conservant
     * son `id` (les widgets qui y sont liés restent valides), son `uid` et son `createdAtMillis`.
     * Renvoie le nombre de lignes modifiées (0 si l'objectif n'existe plus).
     */
    @Query(
        "UPDATE goals SET title = :title, kind = :kind, startEpochDay = :startEpochDay, " +
            "targetEpochDay = :targetEpochDay, color = :color, emoji = :emoji, chartUnit = :chartUnit WHERE id = :id",
    )
    suspend fun updateContent(
        id: Long,
        title: String,
        kind: String,
        startEpochDay: Long,
        targetEpochDay: Long?,
        color: String?,
        emoji: String?,
        chartUnit: String?,
    ): Int

    /**
     * Modification d'un objectif depuis l'app : contenu ([updateContent]) et, s'ils ne sont pas `null`,
     * interrupteur de notifications et option « série », en une seule
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
        chartUnit: String?,
        notificationsEnabled: Boolean?,
        streak: Boolean?,
    ): Int {
        val updated = updateContent(id, title, kind, startEpochDay, targetEpochDay, color, emoji, chartUnit)
        if (notificationsEnabled != null) setNotificationsEnabled(id, notificationsEnabled)
        if (streak != null) setStreak(id, streak)
        return updated
    }

    /** Supprime un objectif ; renvoie le nombre de lignes supprimées. */
    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
