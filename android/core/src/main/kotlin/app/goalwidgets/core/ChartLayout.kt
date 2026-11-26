package app.goalwidgets.core

import java.time.LocalDate

/** Grille de points : [columns] × [rows] emplacements carrés de [pitch] (unité de l'appelant). */
data class DotGridLayout(val columns: Int, val rows: Int, val pitch: Float) {
    companion object {
        /** Nombre de colonnes qui donne les plus gros points pour [count] points dans [width] × [height]. */
        fun fit(count: Int, width: Float, height: Float): DotGridLayout {
            val n = count.coerceAtLeast(1)
            var best = DotGridLayout(1, n, minOf(width, height / n))
            for (columns in 2..n) {
                val rows = (n + columns - 1) / columns
                val pitch = minOf(width / columns, height / rows)
                if (pitch > best.pitch) best = DotGridLayout(columns, rows, pitch)
            }
            return best
        }
    }
}

/** Ligne de la frise de tous les objectifs : un objectif à sa date, ou le repère du jour. */
sealed interface TimelineRow<out T> {
    data class Node<T>(val item: T, val date: LocalDate) : TimelineRow<T>
    data object Today : TimelineRow<Nothing>
}

/** Frise de tous les objectifs : ordre chronologique, repère « Aujourd'hui » avant la première date ≥ aujourd'hui. */
object GoalsTimeline {
    /** Date portée sur la frise : cible d'un compte à rebours, départ d'un « depuis ». */
    fun dateOf(goal: Goal): LocalDate = when (goal.kind) {
        GoalKind.COUNTDOWN -> goal.targetDate ?: goal.startDate
        GoalKind.SINCE -> goal.startDate
    }

    fun <T> arrange(items: List<T>, today: LocalDate, goalOf: (T) -> Goal): List<TimelineRow<T>> {
        val nodes = items.map { TimelineRow.Node(it, dateOf(goalOf(it))) }.sortedBy { it.date }
        val split = nodes.indexOfFirst { it.date >= today }.let { if (it < 0) nodes.size else it }
        return nodes.take(split) + TimelineRow.Today + nodes.drop(split)
    }
}

/** Bloc masquable du détail. `wire` = valeur stockée. */
enum class DetailPart(val wire: String) {
    NUMBER("number"),
    CAPTION("caption"),
    CHART("chart"),
    TIMELINE("timeline"),
}

/**
 * Personnalisation du détail (commune à tous les objectifs) : blocs et graphiques masqués. Il reste
 * toujours au moins un graphique. Stockée en texte : clés séparées par des virgules, `chart:<graphique>`
 * pour un graphique (`number,chart:bars`).
 */
data class DetailLayout(val hiddenParts: Set<DetailPart>, val hiddenCharts: Set<ChartKind>) {
    fun shows(part: DetailPart): Boolean = part !in hiddenParts

    /** Graphiques proposés (non masqués), dans l'ordre du sélecteur. */
    val charts: List<ChartKind> get() = ChartKind.entries.filter { it !in hiddenCharts }

    fun hide(part: DetailPart) = copy(hiddenParts = hiddenParts + part)
    fun restore(part: DetailPart) = copy(hiddenParts = hiddenParts - part)
    fun hide(chart: ChartKind) = if (charts.size > 1) copy(hiddenCharts = hiddenCharts + chart) else this
    fun restore(chart: ChartKind) = copy(hiddenCharts = hiddenCharts - chart)

    /** [preferred] s'il est encore proposé, sinon le premier graphique proposé. */
    fun chartToShow(preferred: ChartKind?): ChartKind = preferred?.takeIf { it in charts } ?: charts.first()

    fun encode(): String =
        (DetailPart.entries.filter { it in hiddenParts }.map { it.wire } +
            ChartKind.entries.filter { it in hiddenCharts }.map { CHART_PREFIX + it.wire }).joinToString(",")

    companion object {
        private const val CHART_PREFIX = "chart:"
        val DEFAULT = DetailLayout(emptySet(), emptySet())

        fun decode(raw: String?): DetailLayout {
            val keys = raw.orEmpty().split(',').map { it.trim() }.toSet()
            val charts = keys.mapNotNull { k -> k.removePrefix(CHART_PREFIX).takeIf { k.startsWith(CHART_PREFIX) } }
                .mapNotNull(ChartKind::fromWire).toSet()
            return DetailLayout(
                hiddenParts = DetailPart.entries.filter { it.wire in keys }.toSet(),
                // Données corrompues (tous masqués) : on n'en garde aucun de masqué.
                hiddenCharts = charts.takeIf { it.size < ChartKind.entries.size } ?: emptySet(),
            )
        }
    }
}

/** Élément retirable du widget à graphique. `wire` = valeur stockée dans l'état du widget. */
enum class WidgetPart(val wire: String) {
    TITLE("title"),
    NUMBER("number"),
    CAPTION("caption"),
    CHART("chart"),
    TIMELINE("timeline"),
}

/**
 * Réglages d'un widget à graphique : graphique choisi et éléments retirés (au moins un reste).
 * Absents (widget posé avant l'arrivée des graphiques) : anneau, tout affiché.
 */
data class WidgetChartConfig(val chart: ChartKind, val hidden: Set<WidgetPart>) {
    fun shows(part: WidgetPart): Boolean = part !in hidden

    fun hide(part: WidgetPart): WidgetChartConfig =
        if (WidgetPart.entries.count { shows(it) } > 1) copy(hidden = hidden + part) else this

    fun restore(part: WidgetPart) = copy(hidden = hidden - part)

    fun encodeHidden(): String = WidgetPart.entries.filter { it in hidden }.joinToString(",") { it.wire }

    companion object {
        val DEFAULT = WidgetChartConfig(ChartKind.RING, emptySet())

        fun decode(chart: String?, hidden: String?): WidgetChartConfig {
            val keys = hidden.orEmpty().split(',').map { it.trim() }.toSet()
            val parts = WidgetPart.entries.filter { it.wire in keys }.toSet()
            return WidgetChartConfig(
                chart = ChartKind.fromWire(chart) ?: DEFAULT.chart,
                hidden = parts.takeIf { it.size < WidgetPart.entries.size } ?: emptySet(),
            )
        }
    }
}

/**
 * Gabarit du widget selon sa taille réelle (dp). [previewWidthDp]/[previewHeightDp] = taille de l'aperçu
 * de la configuration. Compact = une seule rangée (widgets 2×1 / 4×1, dont ceux posés avant les graphiques).
 */
enum class WidgetSize(val previewWidthDp: Float, val previewHeightDp: Float) {
    COMPACT(250f, 70f),
    SMALL(150f, 150f),
    MEDIUM(320f, 150f),
    LARGE(320f, 320f);

    companion object {
        fun of(widthDp: Float, heightDp: Float): WidgetSize = when {
            heightDp < 100f -> COMPACT
            widthDp < 200f -> SMALL
            heightDp < 200f -> MEDIUM
            else -> LARGE
        }
    }
}
