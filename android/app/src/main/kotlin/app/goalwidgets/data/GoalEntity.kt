package app.goalwidgets.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.goalwidgets.core.Goal
import app.goalwidgets.core.GoalKind
import java.time.LocalDate

/**
 * Ligne Room d'un objectif. Distinct de [Goal] (valeur pure de `:core`) : les dates sont
 * stockées en jours epoch (`LocalDate.toEpochDay()`), calendaires et sans fuseau.
 */
@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** Valeur `GoalKind.wire` (`countdown` / `since`). */
    val kind: String,
    val startEpochDay: Long,
    val targetEpochDay: Long?,
    /** Instant de création, en millisecondes epoch. */
    val createdAtMillis: Long,
    /**
     * Notifications de jalons activées pour cet objectif (t08). Désactivées par défaut, y compris
     * pour les nouveaux objectifs : l'autorisation est demandée quand l'utilisateur les active.
     */
    @ColumnInfo(defaultValue = "0") val notificationsEnabled: Boolean = false,
) {
    fun toGoal(): Goal = Goal(
        kind = GoalKind.fromWire(kind),
        startDate = LocalDate.ofEpochDay(startEpochDay),
        targetDate = targetEpochDay?.let(LocalDate::ofEpochDay),
        title = title,
    )

    companion object {
        fun from(goal: Goal, createdAtMillis: Long, id: Long = 0, notificationsEnabled: Boolean = false) = GoalEntity(
            id = id,
            title = goal.title,
            kind = goal.kind.wire,
            startEpochDay = goal.startDate.toEpochDay(),
            targetEpochDay = goal.targetDate?.toEpochDay(),
            createdAtMillis = createdAtMillis,
            notificationsEnabled = notificationsEnabled,
        )
    }
}
