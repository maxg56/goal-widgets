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
    /** Titre affiché (déjà nettoyé des espaces de bord). */
    val title: String = "",
) {
    /**
     * « Continuer en compteur depuis » : le compte à rebours devient un objectif « depuis »
     * sa date cible (même titre). L'app ne le propose qu'une fois la cible atteinte
     * (phase `today` ou `reached`), mais la conversion elle-même ne dépend pas du jour.
     */
    fun continueAsSince(): Goal {
        require(kind == GoalKind.COUNTDOWN) { "Seul un compte à rebours se convertit" }
        val target = requireNotNull(targetDate) { "Un compte à rebours exige une date cible" }
        return copy(kind = GoalKind.SINCE, startDate = target, targetDate = null)
    }
}
