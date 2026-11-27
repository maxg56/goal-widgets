package app.goalwidgets.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.goalwidgets.GoalWidgetsApplication
import app.goalwidgets.R
import app.goalwidgets.core.ChartUnit
import app.goalwidgets.core.DotGrid
import app.goalwidgets.core.DotGridLayout
import app.goalwidgets.core.DotPeriod
import app.goalwidgets.core.DotState
import app.goalwidgets.core.GoalStyle
import app.goalwidgets.core.WidgetContent
import app.goalwidgets.core.WidgetTheme
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.layout.padding
import app.goalwidgets.ui.GoalColors
import app.goalwidgets.ui.GoalLabels
import app.goalwidgets.ui.titleWithEmoji
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Widget « Grille » (ticket 12) : un point par jour/semaine/mois ([DotGrid], pur, figé par
 * `shared-cases/dotgrid.json`). Widget distinct de [GoalWidget] (entrée « Grille » dans le
 * sélecteur du lanceur, tailles moyenne et grande) ; même état Glance ([GoalWidgets.GOAL_ID]) plus
 * la période ([GoalWidgets.PERIOD]), choisis dans [GoalWidgetConfigureActivity].
 *
 * Glance n'a pas de canevas : la grille est dessinée en bitmaps-masques (un par couleur) superposés
 * et teintés par des [ColorProvider] jour/nuit → le mode clair/sombre suit le système sans redessin.
 */
class DotGridWidget : GlanceAppWidget() {

    // Taille exacte : le bitmap est dessiné à la taille réelle du widget.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = (context.applicationContext as GoalWidgetsApplication).database.goalDao()
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            val goals by dao.observeAll().collectAsState(initial = null)
            val goalId = currentState(GoalWidgets.GOAL_ID)
            val period = DotPeriod.fromWire(currentState(GoalWidgets.PERIOD)) ?: DotPeriod.WHOLE
            val theme = WidgetTheme.fromWire(currentState(GoalWidgets.THEME))
            val globalUnit by (context.applicationContext as GoalWidgetsApplication).chartSettings.global.collectAsState()
            GlanceTheme {
                val rows = goals
                if (rows == null) {
                    Background(GlanceModifier) {}
                } else {
                    val today = LocalDate.now()
                    val content = WidgetContent.resolve(goalId, today) { wanted ->
                        rows.firstOrNull { it.id == wanted }?.toGoal()
                    }
                    val row = rows.firstOrNull { it.id == goalId }
                    val style = row?.style() ?: GoalStyle.NONE
                    when (content) {
                        is WidgetContent.Showing -> {
                            val palette = WidgetPalette.of(theme, style)
                            val size = LocalSize.current
                            Box(
                                palette.background(GlanceModifier.fillMaxSize().appWidgetBackground().systemCorners(), style, size.width.value, size.height.value)
                                    .clickable(openGoal(context, content.goalId))
                                    .padding(12.dp),
                            ) {
                                GridBody(content, style, DotGrid.compute(row!!.toGoal(), today, period, ChartUnit.resolve(style.chartUnit, globalUnit)), period, palette)
                            }
                        }
                        is WidgetContent.GoalDeleted -> Background(GlanceModifier.clickable(chooseGoal(context, appWidgetId))) {
                            Message(context.getString(R.string.widget_goal_deleted))
                        }
                        WidgetContent.NotConfigured -> Background(GlanceModifier.clickable(chooseGoal(context, appWidgetId))) {
                            Message(context.getString(R.string.widget_not_configured))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun GridBody(content: WidgetContent.Showing, style: GoalStyle, grid: DotGrid, period: DotPeriod, palette: WidgetPalette) {
    val context = LocalContext.current
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Text(
            text = GoalLabels.status(context.resources, content.state),
            style = TextStyle(color = palette.number, fontSize = 20.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Text(
            text = titleWithEmoji(content.title, style.emoji),
            style = TextStyle(color = palette.primary, fontSize = 14.sp),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(8.dp))

        // Place restante pour la grille : taille du widget − marges (2 × 12 dp) − en-tête (~52 dp).
        val size = LocalSize.current
        val density = min(context.resources.displayMetrics.density, MAX_DENSITY)
        val widthPx = ((size.width.value - 24f) * density).toInt().coerceAtLeast(1)
        val heightPx = ((size.height.value - 24f - HEADER_DP) * density).toInt().coerceAtLeast(1)
        val layers = DotGridBitmaps.draw(grid, period, widthPx, heightPx)
        val description = context.getString(R.string.widget_grid_description_a11y)
        Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            Layer(layers.accent, palette.accent, description)
            Layer(layers.todayRing, palette.primary, null)
            Layer(layers.outside, palette.secondary, null)
        }
    }
}

@Composable
private fun Layer(bitmap: Bitmap, color: ColorProvider, description: String?) {
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = description,
        modifier = GlanceModifier.fillMaxSize(),
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(color),
    )
}

/** Hauteur réservée à l'en-tête (statut + titre + espace), en dp. */
private const val HEADER_DP = 52f

/** Résolution plafonnée : les bitmaps transitent par RemoteViews (taille limitée). */
private const val MAX_DENSITY = 2f

/**
 * Dessin de la grille en trois masques ALPHA_8 (1 octet par pixel au lieu de 4, teintés ensuite) :
 * - [accent] : points écoulés (pleins), aujourd'hui (plein, plus gros), restants (anneaux) ;
 * - [todayRing] : anneau autour du point du jour (mise en valeur, couleur du texte) ;
 * - [outside] : petits points hors objectif (périodes mois/semaine).
 */
internal object DotGridBitmaps {
    class Layers(val accent: Bitmap, val todayRing: Bitmap, val outside: Bitmap)

    fun draw(grid: DotGrid, period: DotPeriod, widthPx: Int, heightPx: Int): Layers {
        val layout = layout(grid, period, widthPx, heightPx)
        val w = max(1, ceil(layout.columns * layout.pitch).toInt())
        val h = max(1, ceil(layout.rows * layout.pitch).toInt())
        val accent = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val ring = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val outside = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val accentCanvas = Canvas(accent)
        val ringCanvas = Canvas(ring)
        val outsideCanvas = Canvas(outside)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; style = Paint.Style.FILL }
        val radius = layout.pitch * 0.36f
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = max(1f, layout.pitch * 0.09f)
        }
        grid.cells.forEachIndexed { i, cell ->
            val slot = i + layout.offset
            val cx = (slot % layout.columns + 0.5f) * layout.pitch
            val cy = (slot / layout.columns + 0.5f) * layout.pitch
            when (cell.state) {
                DotState.ELAPSED -> accentCanvas.drawCircle(cx, cy, radius, fill)
                DotState.REMAINING -> accentCanvas.drawCircle(cx, cy, radius - stroke.strokeWidth / 2, stroke)
                DotState.TODAY -> {
                    accentCanvas.drawCircle(cx, cy, radius * 0.8f, fill)
                    ringCanvas.drawCircle(cx, cy, layout.pitch * 0.5f - stroke.strokeWidth / 2, stroke)
                }
                DotState.OUTSIDE -> outsideCanvas.drawCircle(cx, cy, radius * 0.4f, fill)
            }
        }
        return Layers(accent, ring, outside)
    }

    /** Grille : [columns] × [rows] emplacements carrés de [pitch] px ; [offset] emplacements vides au début. */
    class Layout(val columns: Int, val rows: Int, val pitch: Float, val offset: Int)

    fun layout(grid: DotGrid, period: DotPeriod, widthPx: Int, heightPx: Int): Layout {
        val n = max(1, grid.cells.size)
        return when (period) {
            // Calendrier : 7 colonnes (lundi → dimanche), le 1er sous son jour de semaine.
            DotPeriod.MONTH -> {
                val offset = grid.cells.first().start.dayOfWeek.value - 1
                val rows = (offset + n + 6) / 7
                Layout(7, rows, min(widthPx / 7f, heightPx / rows.toFloat()), offset)
            }
            DotPeriod.WEEK -> Layout(7, 1, min(widthPx / 7f, heightPx.toFloat()), 0)
            // Objectif entier : nombre de colonnes qui donne les plus gros points.
            DotPeriod.WHOLE -> DotGridLayout.fit(n, widthPx.toFloat(), heightPx.toFloat()).let { Layout(it.columns, it.rows, it.pitch, 0) }
        }
    }
}
