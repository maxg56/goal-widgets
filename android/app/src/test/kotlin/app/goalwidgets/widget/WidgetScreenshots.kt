package app.goalwidgets.widget

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import app.goalwidgets.core.ChartKind
import app.goalwidgets.core.ChartUnit
import app.goalwidgets.core.CountdownEngine
import app.goalwidgets.core.DotGrid
import app.goalwidgets.core.DotPeriod
import app.goalwidgets.core.WidgetContent
import androidx.glance.GlanceModifier
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import app.goalwidgets.core.GoalOrder
import app.goalwidgets.data.GoalEntity
import app.goalwidgets.core.Goal
import app.goalwidgets.core.GoalColor
import app.goalwidgets.core.GoalKind
import app.goalwidgets.core.GoalStyle
import app.goalwidgets.core.WidgetChartConfig
import app.goalwidgets.core.WidgetSize
import app.goalwidgets.core.WidgetTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * Captures PNG des widgets (rendu réel des RemoteViews Glance, graphismes natifs Robolectric), pour
 * relire le design sans appareil. Lancé seulement avec `-Pscreenshots` (CI : artefact
 * « widget-screenshots ») ; ignoré sinon. Ne vérifie rien : c'est un outil de relecture.
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class WidgetScreenshots {
    private val today = LocalDate.of(2026, 11, 27)
    private val goals = listOf(
        Triple(Goal(GoalKind.COUNTDOWN, LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 8), "Examen final"), GoalStyle(GoalColor.INDIGO, "🎓"), "exam"),
        Triple(Goal(GoalKind.SINCE, LocalDate.of(2026, 3, 14), null, "Sans fumer"), GoalStyle(GoalColor.GREEN, "🚭"), "since"),
        Triple(Goal(GoalKind.COUNTDOWN, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 24), "Noël"), GoalStyle(GoalColor.RED, null), "noel"),
    )

    @Test
    fun render() {
        assumeTrue(System.getProperty("widgetScreenshots") == "true")
        val out = File(System.getProperty("widgetScreenshotsDir")!!).apply { mkdirs() }
        for (night in listOf(false, true)) {
            for (theme in WidgetTheme.entries) {
                for ((goal, style, name) in goals) {
                    for (size in WidgetSize.entries) {
                        val charts = if (size == WidgetSize.MEDIUM || size == WidgetSize.LARGE) ChartKind.entries else listOf(ChartKind.RING)
                        for (chart in charts) {
                            if (name != "exam" && chart != ChartKind.RING) continue
                            val file = File(out, "${if (night) "night" else "day"}-${theme.wire}-$name-${size.name.lowercase()}-${chart.wire}.png")
                            render(night, size.previewWidthDp, size.previewHeightDp, file) {
                                ChartWidgetContent(goal, style, today, WidgetChartConfig(chart, emptySet()), theme, click = null)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun renderList() {
        assumeTrue(System.getProperty("widgetScreenshots") == "true")
        val out = File(System.getProperty("widgetScreenshotsDir")!!).apply { mkdirs() }
        val rows = goals.mapIndexed { i, (goal, style, _) ->
            GoalEntity.from(goal, createdAtMillis = i.toLong(), style = style, id = i + 1L)
        }
        for (night in listOf(false, true)) {
            render(night, 320f, 230f, File(out, "${if (night) "night" else "day"}-list.png")) { ListBody(rows, GoalOrder.CLOSEST_DATE, today, scrollable = false) }
        }
    }

    @Test
    fun renderGrid() {
        assumeTrue(System.getProperty("widgetScreenshots") == "true")
        val out = File(System.getProperty("widgetScreenshotsDir")!!).apply { mkdirs() }
        val (goal, style, _) = goals[0]
        val content = WidgetContent.Showing(1, goal.title, CountdownEngine.compute(goal, today))
        for (night in listOf(false, true)) {
            for (theme in WidgetTheme.entries) {
                for (period in DotPeriod.entries) {
                    val file = File(out, "${if (night) "night" else "day"}-${theme.wire}-grid-${period.wire}.png")
                    render(night, 320f, 160f, file) {
                        val palette = WidgetPalette.of(theme, style)
                        Box(palette.background(GlanceModifier.fillMaxSize(), style, 320f, 160f).padding(12.dp)) {
                            GridBody(content, style, DotGrid.compute(goal, today, period, ChartUnit.AUTO), period, palette)
                        }
                    }
                }
            }
        }
    }

    private fun render(night: Boolean, widthDp: Float, heightDp: Float, file: File, content: @androidx.compose.runtime.Composable () -> Unit) {
        val app = RuntimeEnvironment.getApplication()
        val config = Configuration(app.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        val context = app.createConfigurationContext(config)
        val views = runBlocking {
            GlanceRemoteViews().compose(context, DpSize(widthDp.dp, heightDp.dp)) { GlanceTheme { content() } }.remoteViews
        }
        val density = context.resources.displayMetrics.density
        val w = (widthDp * density).toInt()
        val h = (heightDp * density).toInt()
        val frame = FrameLayout(context)
        frame.addView(views.apply(context, frame))
        frame.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        frame.layout(0, 0, w, h)
        // Fond « écran d'accueil » neutre derrière le widget (coins, transparence).
        val bmp = Bitmap.createBitmap(w + 48, h + 48, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(if (night) 0xFF30343C.toInt() else 0xFFB9C4D6.toInt())
        canvas.translate(24f, 24f)
        frame.draw(canvas)
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
