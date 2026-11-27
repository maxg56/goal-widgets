package app.goalwidgets.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.goalwidgets.core.ChartUnit
import app.goalwidgets.core.ExportedGoal
import app.goalwidgets.core.ExportedOptions
import app.goalwidgets.core.Goal
import app.goalwidgets.core.GoalColor
import app.goalwidgets.core.GoalKind
import app.goalwidgets.core.GoalStyle
import app.goalwidgets.core.NotificationPrefs
import app.goalwidgets.core.PastStreak
import app.goalwidgets.core.Streaks
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Ligne Room d'un objectif. Distinct de [Goal] (valeur pure de `:core`) : les dates sont
 * stockées en jours epoch (`LocalDate.toEpochDay()`), calendaires et sans fuseau.
 *
 * [id] (entier) reste la clé locale (widgets, liens) ; [uid] (UUID) est l'identifiant portable
 * du format d'export, qui évite les doublons à l'import (schéma v2, `MIGRATION_1_2`).
 */
@Entity(tableName = "goals", indices = [Index(value = ["uid"], unique = true)])
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** Valeur `GoalKind.wire` (`countdown` / `since`). */
    val kind: String,
    val startEpochDay: Long,
    val targetEpochDay: Long?,
    /** Instant de création, en millisecondes epoch. */
    val createdAtMillis: Long,
    /** UUID en minuscules (v2). Le défaut SQL '' n'existe que pour l'`ALTER TABLE` de la migration. */
    @ColumnInfo(defaultValue = "") val uid: String = UUID.randomUUID().toString(),
    /** Valeur `GoalColor.wire`, ou `null` (couleur par défaut du thème) (v2). */
    val color: String? = null,
    /** Emoji libre, ou `null` (v2). */
    val emoji: String? = null,
    /**
     * Notifications de jalons activées pour cet objectif (v3, t08). Désactivées par défaut, y compris
     * pour les nouveaux objectifs : l'autorisation est demandée quand l'utilisateur les active.
     */
    @ColumnInfo(defaultValue = "0") val notificationsEnabled: Boolean = false,
    /** Valeur `ChartUnit.wire`, ou `null` = comme le réglage général (v4). Non exportée. */
    val chartUnit: String? = null,
    /**
     * Compteur « depuis » marqué « série » : bouton de remise à zéro, record et historique (v5).
     * Ignoré pour un compte à rebours.
     */
    @ColumnInfo(defaultValue = "0") val streak: Boolean = false,
    /** Séries terminées, ordre chronologique (`Streaks.encode`, v5). Conservé si [streak] est désactivé. */
    @ColumnInfo(defaultValue = "") val streakHistory: String = "",
    /** Compte à rebours « chaque année » : repart vers l'occurrence suivante une fois la cible passée (v6). */
    @ColumnInfo(defaultValue = "0") val repeatYearly: Boolean = false,
    /** Archivé : hors de l'accueil (section « Archivés »), sans notification ; widgets inchangés (v6). */
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
    /** Réglages de notification de l'objectif (`NotificationPrefs.encode`, vide = défaut) (v7). */
    @ColumnInfo(defaultValue = "") val notificationPrefs: String = "",
) {
    fun notificationSettings(): NotificationPrefs = NotificationPrefs.decode(notificationPrefs)

    fun toGoal(): Goal = Goal(
        kind = GoalKind.fromWire(kind),
        startDate = LocalDate.ofEpochDay(startEpochDay),
        targetDate = targetEpochDay?.let(LocalDate::ofEpochDay),
        title = title,
    )

    fun style(): GoalStyle = GoalStyle(GoalColor.fromWire(color), emoji, ChartUnit.fromWire(chartUnit))

    /**
     * Remise à zéro proposée : compteur « depuis » marqué « série ». Pas de nom en `isStreak` : Room
     * y verrait un second getter de la colonne `streak` (« Ambiguous getter », échec de KSP).
     */
    val offersStreakReset: Boolean get() = streak && kind == GoalKind.SINCE.wire

    fun history(): List<PastStreak> = Streaks.decode(streakHistory)

    fun toExported(): ExportedGoal = ExportedGoal(
        id = uid,
        goal = toGoal(),
        color = GoalColor.fromWire(color),
        emoji = emoji,
        notificationsEnabled = notificationsEnabled,
        createdAt = Instant.ofEpochMilli(createdAtMillis),
        options = ExportedOptions(
            chartUnit = ChartUnit.fromWire(chartUnit),
            streak = streak,
            streakHistory = history(),
            repeatYearly = repeatYearly,
            archived = archived,
            notificationPrefs = notificationPrefs,
        ),
    )

    companion object {
        fun from(
            goal: Goal,
            createdAtMillis: Long,
            style: GoalStyle = GoalStyle.NONE,
            id: Long = 0,
            notificationsEnabled: Boolean = false,
            streak: Boolean = false,
            repeatYearly: Boolean = false,
            notificationPrefs: NotificationPrefs = NotificationPrefs.DEFAULT,
        ) = GoalEntity(
            id = id,
            title = goal.title,
            kind = goal.kind.wire,
            startEpochDay = goal.startDate.toEpochDay(),
            targetEpochDay = goal.targetDate?.toEpochDay(),
            createdAtMillis = createdAtMillis,
            color = style.color?.wire,
            emoji = style.emoji,
            notificationsEnabled = notificationsEnabled,
            chartUnit = style.chartUnit?.wire,
            streak = streak,
            repeatYearly = repeatYearly && goal.kind == GoalKind.COUNTDOWN,
            notificationPrefs = notificationPrefs.encode(),
        )

        /**
         * Objectif importé : garde son UUID et sa date de création d'origine.
         * `notificationsEnabled` absent/null dans le fichier → défaut (désactivées).
         */
        fun fromExported(e: ExportedGoal) = from(
            goal = e.goal,
            createdAtMillis = e.createdAt.toEpochMilli(),
            style = GoalStyle(e.color, e.emoji, e.options.chartUnit),
            notificationsEnabled = e.notificationsEnabled ?: false,
            streak = e.options.streak,
            repeatYearly = e.options.repeatYearly,
        ).copy(
            uid = e.id,
            streakHistory = Streaks.encode(e.options.streakHistory),
            archived = e.options.archived,
            notificationPrefs = NotificationPrefs.decode(e.options.notificationPrefs).encode(),
        )
    }
}
