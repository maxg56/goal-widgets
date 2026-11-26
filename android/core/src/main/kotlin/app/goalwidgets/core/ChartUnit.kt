package app.goalwidgets.core

/**
 * Unité préférée des graphiques découpés en périodes (barres, arcs, grille de points).
 * [AUTO] = choix selon la durée de l'objectif (comportement historique). `wire` = valeur stockée.
 *
 * Héritage ([resolve]) : le réglage d'un objectif (`null` = « comme le réglage général ») l'emporte
 * sur le réglage général. Une unité trop fine pour rester lisible passe à l'unité au-dessus
 * (jour → semaine → mois), cf. [ArcsChart], [BarsChart], [DotGrid].
 */
enum class ChartUnit(val wire: String) {
    AUTO("auto"),
    DAY("day"),
    WEEK("week"),
    MONTH("month");

    companion object {
        fun fromWire(value: String?): ChartUnit? = entries.firstOrNull { it.wire == value }

        /** Réglage de l'objectif s'il en a un, sinon réglage général. */
        fun resolve(goal: ChartUnit?, global: ChartUnit): ChartUnit = goal ?: global
    }
}
