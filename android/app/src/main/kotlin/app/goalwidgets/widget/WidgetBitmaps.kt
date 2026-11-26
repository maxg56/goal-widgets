package app.goalwidgets.widget

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import app.goalwidgets.core.ArcsChart
import app.goalwidgets.core.BarsChart
import app.goalwidgets.core.ChartKind
import app.goalwidgets.core.ChartUnit
import app.goalwidgets.core.DotGrid
import app.goalwidgets.core.DotGridLayout
import app.goalwidgets.core.DotPeriod
import app.goalwidgets.core.DotState
import app.goalwidgets.core.Goal
import app.goalwidgets.core.GoalStrip
import app.goalwidgets.core.GoalStyle
import app.goalwidgets.core.RingChart
import app.goalwidgets.core.SegmentState
import app.goalwidgets.core.WidgetTheme
import app.goalwidgets.ui.GoalTint
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Taille des bitmaps du widget. Ils transitent par RemoteViews (mémoire bornée par le système et
 * transaction Binder d'environ 1 Mo, une vue par taille en mode Exact) : on dessine à la densité de
 * l'écran tant que la zone tient dans [MAX_PIXELS], au-delà on réduit la résolution.
 * Avec des masques ALPHA_8 (1 octet par pixel), un graphique du grand widget fait au plus ~200 Ko : avec `SizeMode.Exact`, Glance envoie un
 * rendu par taille (portrait + paysage) dans le même RemoteViews, à garder loin de la limite Binder (1 Mo).
 */
object WidgetBitmapBudget {
    const val MAX_PIXELS = 200_000
    const val MAX_PX_PER_DP = 3f

    fun pxPerDp(widthDp: Float, heightDp: Float, screenDensity: Float): Float {
        val area = max(widthDp, 1f) * max(heightDp, 1f)
        return minOf(screenDensity, MAX_PX_PER_DP, sqrt(MAX_PIXELS / area))
    }

    fun pixels(dp: Float, pxPerDp: Float): Int = max(1, (dp * pxPerDp).toInt())
}

/**
 * Dessin des parties graphiques du widget à graphique (Glance n'a pas de canevas). Les textes restent
 * de vrais textes Glance (nets, accessibles) ; seuls le fond et les graphiques sont des bitmaps :
 * - fond : petit dégradé de la couleur de l'objectif, étiré (aucun coin dessiné : sur Android 12+
 *   le système arrondit le widget avec son propre rayon, pas de double coin) ;
 * - graphiques et mini frise : masques ALPHA_8 (opacités de blanc) teintés en blanc par Glance.
 * Mêmes calculs que le détail : tout vient de :core.
 */
object WidgetBitmaps {
    /** Un pixel du fond pour 4 dp : un dégradé étiré reste lisse. */
    private const val BACKGROUND_PX_PER_DP = 0.25f

    /**
     * Fond « Vif » : dégradé de la couleur de l'objectif façon « mesh » — éclat clair en haut à droite,
     * halo d'une teinte voisine en bas à gauche, ombre douce en bas. Fond « Nuit » : presque noir,
     * halo discret de la couleur de l'objectif. Petit bitmap étiré : un dégradé reste lisse.
     */
    fun background(style: GoalStyle, widthDp: Float, heightDp: Float, theme: WidgetTheme = WidgetTheme.VIVID): Bitmap {
        val w = WidgetBitmapBudget.pixels(widthDp, BACKGROUND_PX_PER_DP).coerceAtLeast(8)
        val h = WidgetBitmapBudget.pixels(heightDp, BACKGROUND_PX_PER_DP).coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val deep = GoalTint.deepColor(style).toArgb()
        val radius = max(w, h) * 0.75f
        if (theme == WidgetTheme.MIDNIGHT) {
            c.drawColor(MIDNIGHT_BASE)
            glow(c, w * 0.95f, h * 0.0f, radius * 1.1f, withAlpha(GoalTint.accent(style, dark = true).toArgb(), 0.30f))
            glow(c, w * 0.0f, h * 1.0f, radius * 0.9f, withAlpha(deep, 0.35f))
            return bmp
        }
        val stops = GoalTint.gradient(GoalTint.deepColor(style)).map { it.toArgb() }.toIntArray()
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, w * 0.25f, h.toFloat(), stops, null, Shader.TileMode.CLAMP)
        })
        glow(c, w * 0.05f, h * 0.95f, radius, withAlpha(shiftHue(deep, 28f), 0.55f))
        glow(c, w * 0.88f, h * 0.08f, radius, white(0.24f))
        return bmp
    }

    private fun glow(c: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        c.drawCircle(x, y, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(x, y, radius, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
        })
    }

    /** Teinte voisine (décalée de [degrees]) pour le halo du dégradé « Vif ». */
    private fun shiftHue(color: Int, degrees: Float): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        hsv[0] = (hsv[0] + degrees + 360f) % 360f
        hsv[1] = (hsv[1] * 1.1f).coerceAtMost(1f)
        return android.graphics.Color.HSVToColor(hsv)
    }

    private fun withAlpha(color: Int, alpha: Float): Int = (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)

    /** Fond du thème « Nuit » (proche du noir OLED, légèrement bleuté). */
    const val MIDNIGHT_BASE: Int = 0xFF0B0D12.toInt()

    /** Graphique [kind] dans une zone de [widthDp] × [heightDp], à teinter en blanc, découpé selon [unit]. */
    fun chart(
        kind: ChartKind,
        goal: Goal,
        today: LocalDate,
        widthDp: Float,
        heightDp: Float,
        screenDensity: Float,
        unit: ChartUnit = ChartUnit.AUTO,
    ): Bitmap {
        val square = kind == ChartKind.RING || kind == ChartKind.ARCS
        val wDp = if (square) min(widthDp, heightDp) else widthDp
        val hDp = if (square) min(widthDp, heightDp) else heightDp
        val scale = WidgetBitmapBudget.pxPerDp(wDp, hDp, screenDensity)
        val bmp = mask(wDp, hDp, scale)
        val c = Canvas(bmp)
        val r = RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
        when (kind) {
            ChartKind.RING -> ring(c, r, RingChart.compute(goal, today).fraction.toFloat())
            ChartKind.ARCS -> arcs(c, r, scale, ArcsChart.compute(goal, today, unit))
            ChartKind.BARS -> bars(c, r, scale, BarsChart.compute(goal, today, unit))
            ChartKind.DOTS -> dots(c, r, DotGrid.compute(goal, today, DotPeriod.WHOLE, unit))
        }
        return bmp
    }

    /** Mini frise départ → aujourd'hui → cible ([GoalStrip]), hauteur ~18 dp. */
    fun strip(goal: Goal, today: LocalDate, widthDp: Float, heightDp: Float, screenDensity: Float): Bitmap {
        val scale = WidgetBitmapBudget.pxPerDp(widthDp, heightDp, screenDensity)
        val bmp = mask(widthDp, heightDp, scale)
        val c = Canvas(bmp)
        val strip = GoalStrip.compute(goal, today)
        val thick = 6f * scale
        val y = bmp.height / 2f
        val x0 = thick * 1.6f
        val x1 = bmp.width - thick * 1.6f
        fun xOf(position: Double) = x0 + (x1 - x0) * position.toFloat()
        val todayX = xOf(strip.todayPosition)
        val solidEnd = xOf(strip.position(strip.solidEnd))
        c.drawLine(x0, y, solidEnd, y, stroke(0.25f, thick, round = true))
        if (x1 > solidEnd + thick) {
            val dash = stroke(0.3f, thick / 2.5f, round = true).apply { pathEffect = DashPathEffect(floatArrayOf(4f * scale, 4f * scale), 0f) }
            c.drawLine(solidEnd + thick, y, x1, y, dash)
        }
        c.drawLine(x0, y, todayX, y, stroke(1f, thick, round = true))
        strip.targetPosition?.let {
            val tx = xOf(it)
            c.drawCircle(tx, y, thick * 0.9f, fill(0.35f))
            c.drawCircle(tx, y, thick * 0.9f, stroke(1f, 2f * scale, round = false))
        }
        c.drawCircle(todayX, y, thick * 1.5f, fill(0.3f))
        c.drawCircle(todayX, y, thick * 0.8f, fill(1f))
        return bmp
    }

    private fun mask(widthDp: Float, heightDp: Float, scale: Float): Bitmap =
        Bitmap.createBitmap(WidgetBitmapBudget.pixels(widthDp, scale), WidgetBitmapBudget.pixels(heightDp, scale), Bitmap.Config.ALPHA_8)

    private fun ring(c: Canvas, r: RectF, fraction: Float) {
        val side = min(r.width(), r.height())
        val stroke = side * 0.11f
        // Marge pour le halo : il ne doit pas être coupé par le bord du bitmap.
        val inset = stroke / 2 + stroke * 0.45f
        val oval = RectF(r.centerX() - side / 2 + inset, r.centerY() - side / 2 + inset, r.centerX() + side / 2 - inset, r.centerY() + side / 2 - inset)
        c.drawArc(oval, 0f, 360f, false, stroke(0.2f, stroke, round = false))
        val sweep = 360f * fraction.coerceIn(0f, 1f)
        if (sweep > 0.5f) {
            // Halo flou sous l'arc, l'arc, puis une auréole à son extrémité (« où j'en suis »).
            c.drawArc(oval, -90f, sweep, false, stroke(0.45f, stroke, round = true).apply { maskFilter = BlurMaskFilter(stroke * 0.45f, BlurMaskFilter.Blur.NORMAL) })
            c.drawArc(oval, -90f, sweep, false, stroke(1f, stroke, round = true))
            if (sweep < 359.5f) {
                val angle = Math.toRadians((sweep - 90f).toDouble())
                val rad = oval.width() / 2
                val ex = oval.centerX() + rad * kotlin.math.cos(angle).toFloat()
                val ey = oval.centerY() + rad * kotlin.math.sin(angle).toFloat()
                c.drawCircle(ex, ey, stroke * 0.95f, fill(0.3f))
            }
        }
    }

    private fun arcs(c: Canvas, r: RectF, scale: Float, arcs: ArcsChart) {
        val n = arcs.segments.size
        val outer = min(r.width(), r.height()) / 2
        val inner = outer * 0.3f
        val pitch = (outer - inner) / n
        val stroke = min(pitch * 0.72f, 18f * scale)
        val current = arcs.currentIndex
        arcs.segments.forEachIndexed { i, seg ->
            val rad = outer - pitch * i - stroke / 2
            if (rad <= 0f) return@forEachIndexed
            val oval = RectF(r.centerX() - rad, r.centerY() - rad, r.centerX() + rad, r.centerY() + rad)
            c.drawArc(oval, 0f, 360f, false, stroke(if (seg.state == SegmentState.REMAINING) 0.1f else 0.18f, stroke, round = false))
            val sweep = 360f * seg.fill.toFloat()
            if (sweep > 0.5f) {
                val alpha = if (i == current) 1f else 0.5f + 0.4f * (i + 1) / n
                if (i == current) {
                    c.drawArc(oval, -90f, sweep, false, stroke(0.4f, stroke, round = true).apply { maskFilter = BlurMaskFilter(stroke * 0.5f, BlurMaskFilter.Blur.NORMAL) })
                }
                c.drawArc(oval, -90f, sweep, false, stroke(alpha, stroke, round = true))
            }
        }
    }

    private fun bars(c: Canvas, r: RectF, scale: Float, chart: BarsChart) {
        // Trop de barres pour la largeur : fenêtre autour d'aujourd'hui (règle de :core).
        val window = chart.window(max(1, (r.width() / (7f * scale)).toInt()))
        val n = window.count()
        val pitch = r.width() / n
        val bw = min(pitch * 0.68f, 22f * scale)
        val left = r.left + (r.width() - pitch * n) / 2
        val today = chart.todayIndex
        window.forEachIndexed { k, i ->
            val bar = chart.bars[i]
            val bh = bar.height.toFloat() * r.height()
            val x = left + k * pitch + (pitch - bw) / 2
            val alpha = when {
                i == today -> 1f
                bar.segment.state == SegmentState.ELAPSED -> 0.75f
                else -> 0.22f
            }
            c.drawRoundRect(RectF(x, r.bottom - bh, x + bw, r.bottom), bw / 2, bw / 2, fill(alpha))
        }
    }

    private fun dots(c: Canvas, r: RectF, grid: DotGrid) {
        if (grid.cells.isEmpty()) return
        val layout = DotGridLayout.fit(grid.cells.size, r.width(), r.height())
        val left = r.left + (r.width() - layout.columns * layout.pitch) / 2
        val top = r.top + (r.height() - layout.rows * layout.pitch) / 2
        val radius = layout.pitch * 0.34f
        grid.cells.forEachIndexed { i, cell ->
            val cx = left + (i % layout.columns + 0.5f) * layout.pitch
            val cy = top + (i / layout.columns + 0.5f) * layout.pitch
            when (cell.state) {
                DotState.ELAPSED -> c.drawCircle(cx, cy, radius, fill(0.9f))
                DotState.TODAY -> {
                    c.drawCircle(cx, cy, layout.pitch * 0.5f, fill(0.3f))
                    c.drawCircle(cx, cy, radius * 1.1f, fill(1f))
                }
                DotState.REMAINING, DotState.OUTSIDE -> c.drawCircle(cx, cy, radius, fill(0.22f))
            }
        }
    }

    private fun white(alpha: Float): Int = android.graphics.Color.argb((alpha.coerceIn(0f, 1f) * 255).toInt(), 255, 255, 255)

    private fun fill(alpha: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = white(alpha); style = Paint.Style.FILL }

    private fun stroke(alpha: Float, width: Float, round: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = white(alpha)
        style = Paint.Style.STROKE
        strokeWidth = width
        if (round) strokeCap = Paint.Cap.ROUND
    }
}
