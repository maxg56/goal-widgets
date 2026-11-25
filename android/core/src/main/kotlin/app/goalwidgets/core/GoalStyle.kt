package app.goalwidgets.core

/** Personnalisation visuelle d'un objectif (liste et widgets) : couleur et/ou emoji, facultatifs. */
data class GoalStyle(val color: GoalColor? = null, val emoji: String? = null) {
    companion object {
        val NONE = GoalStyle()
    }
}
