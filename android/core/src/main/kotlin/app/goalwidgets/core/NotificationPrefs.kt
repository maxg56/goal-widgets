package app.goalwidgets.core

import java.time.LocalTime

/** Familles de jalons qu'un objectif peut couper individuellement. `wire` = valeur stockée. */
enum class MilestoneFamily(val wire: String) {
    /** « depuis » : chaque mois-anniversaire. */
    MONTH_ANNIVERSARIES("month"),

    /** « depuis » : chaque anniversaire annuel. */
    YEAR_ANNIVERSARIES("year"),

    /** « depuis » : paliers de jours (7, 14, 50, 100, 200, 300, 500, 1000 jours…). */
    DAY_MILESTONES("days"),

    /** Compte à rebours : rappels mensuels (« Plus que 4 mois »). */
    MONTHLY_REMINDERS("monthly"),

    /** Compte à rebours : jour J. */
    TARGET_DAY("target");

    companion object {
        fun fromWire(value: String): MilestoneFamily? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * Réglages de notification d'un objectif (Android) : familles de jalons coupées, rappels « N jours
 * avant » choisis librement (compte à rebours) et heure d'envoi propre (`null` = heure générale).
 *
 * Stockés en texte (`off=days;before=14,3,1;time=1200`) : chaîne vide = [DEFAULT]. Une partie
 * illisible est ignorée (valeur par défaut), jamais une erreur.
 */
data class NotificationPrefs(
    val disabled: Set<MilestoneFamily> = emptySet(),
    /** Rappels avant la cible, en jours (≥ 1), d'un compte à rebours. */
    val daysBefore: Set<Int> = DEFAULT_DAYS_BEFORE,
    val sendTime: LocalTime? = null,
) {
    fun enabled(family: MilestoneFamily): Boolean = family !in disabled

    fun with(family: MilestoneFamily, on: Boolean) = copy(disabled = if (on) disabled - family else disabled + family)

    fun encode(): String = listOfNotNull(
        disabled.takeIf { it.isNotEmpty() }?.let { off -> "off=" + MilestoneFamily.entries.filter { it in off }.joinToString(",") { it.wire } },
        daysBefore.takeIf { it != DEFAULT_DAYS_BEFORE }?.let { "before=" + it.sortedDescending().joinToString(",") },
        sendTime?.let { "time=${it.hour * 60 + it.minute}" },
    ).joinToString(";")

    companion object {
        val DEFAULT_DAYS_BEFORE: Set<Int> = setOf(30, 7, 1)

        /** Plus grand rappel « N jours avant » accepté (≈ 1 an). */
        const val MAX_DAYS_BEFORE = 366

        /** Réglages d'un objectif qui n'a rien personnalisé. */
        val DEFAULT = NotificationPrefs()

        /**
         * Règles d'origine, communes avec iOS et figées par `shared-cases/planner.json` : pas de
         * paliers de jours.
         */
        val CLASSIC = NotificationPrefs(disabled = setOf(MilestoneFamily.DAY_MILESTONES))

        fun decode(raw: String?): NotificationPrefs {
            if (raw.isNullOrBlank()) return DEFAULT
            var prefs = DEFAULT
            for (part in raw.split(';')) {
                val key = part.substringBefore('=', "").trim()
                val value = part.substringAfter('=', "").trim()
                when (key) {
                    "off" -> prefs = prefs.copy(disabled = value.split(',').mapNotNull { MilestoneFamily.fromWire(it.trim()) }.toSet())
                    "before" -> prefs = prefs.copy(
                        daysBefore = value.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..MAX_DAYS_BEFORE }.toSet(),
                    )
                    "time" -> value.toIntOrNull()?.takeIf { it in 0 until 24 * 60 }?.let {
                        prefs = prefs.copy(sendTime = LocalTime.of(it / 60, it % 60))
                    }
                }
            }
            return prefs
        }
    }
}

/** Paliers de jours d'un compteur « depuis » : 7, 14, 50, 100, 200, 300, 500, 1000, puis tous les 500. */
object DayMilestones {
    private val FIRST = listOf(7, 14, 50, 100, 200, 300, 500, 1000)

    /** Paliers croissants jusqu'à [max] inclus. */
    fun upTo(max: Long): List<Int> {
        val result = FIRST.filter { it <= max }.toMutableList()
        var next = 1500L
        while (next <= max) {
            result += next.toInt()
            next += 500
        }
        return result
    }
}
