package app.goalwidgets.core

/**
 * Habillage d'un widget, choisi par instance dans la configuration. `wire` = valeur stockée dans
 * l'état du widget ; absente (widgets posés avant les thèmes) ou inconnue → [VIVID], l'habillage
 * historique.
 */
enum class WidgetTheme(val wire: String) {
    /** Dégradé saturé de la couleur de l'objectif, texte blanc (identique en clair et en sombre). */
    VIVID("vivid"),

    /** Surface du thème système (clair/sombre, Material You), accents de la couleur de l'objectif. */
    SOFT("soft"),

    /** Fond presque noir, graphiques lumineux de la couleur de l'objectif (écrans OLED). */
    MIDNIGHT("midnight");

    companion object {
        val DEFAULT = VIVID

        fun fromWire(value: String?): WidgetTheme = entries.firstOrNull { it.wire == value } ?: DEFAULT
    }
}
