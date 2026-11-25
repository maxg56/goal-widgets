package app.goalwidgets.core

import java.time.LocalDate

/** Type d'objectif. `wire` est la valeur utilisée dans le jeu de cas partagé. */
enum class GoalKind(val wire: String) {
    COUNTDOWN("countdown"),
    SINCE("since");

    companion object {
        fun fromWire(value: String): GoalKind =
            entries.firstOrNull { it.wire == value } ?: error("GoalKind inconnu : $value")
    }
}

/** Objectif daté. Les dates sont des dates calendaires locales (sans heure ni fuseau). */
data class Goal(
    val kind: GoalKind,
    val startDate: LocalDate,
    val targetDate: LocalDate? = null,
)
