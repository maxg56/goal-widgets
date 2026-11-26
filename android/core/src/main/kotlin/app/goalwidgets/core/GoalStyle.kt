package app.goalwidgets.core

/**
 * Personnalisation d'un objectif (liste et widgets) : couleur et/ou emoji, facultatifs, et unité
 * préférée des graphiques ([chartUnit] `null` = comme le réglage général, cf. [ChartUnit.resolve]).
 */
data class GoalStyle(val color: GoalColor? = null, val emoji: String? = null, val chartUnit: ChartUnit? = null) {
    companion object {
        val NONE = GoalStyle()
    }
}
