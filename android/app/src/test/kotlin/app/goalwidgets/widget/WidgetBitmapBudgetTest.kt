package app.goalwidgets.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WidgetBitmapBudgetTest {

    @Test
    fun `small areas are drawn at the screen density, capped`() {
        assertEquals(2.75f, WidgetBitmapBudget.pxPerDp(widthDp = 100f, heightDp = 100f, screenDensity = 2.75f))
        assertEquals(WidgetBitmapBudget.MAX_PX_PER_DP, WidgetBitmapBudget.pxPerDp(100f, 100f, screenDensity = 4f))
    }

    @Test
    fun `a large chart stays under the pixel budget`() {
        val scale = WidgetBitmapBudget.pxPerDp(widthDp = 320f, heightDp = 240f, screenDensity = 3.5f)
        val pixels = (320f * scale).toInt() * (240f * scale).toInt()
        assertTrue(pixels <= WidgetBitmapBudget.MAX_PIXELS, "$pixels px")
        assertTrue(scale >= 1.8f, "encore net : $scale px/dp")
    }

    @Test
    fun `empty or negative areas still give a usable bitmap`() {
        assertEquals(1, WidgetBitmapBudget.pixels(0f, 2f))
        assertEquals(1, WidgetBitmapBudget.pixels(-5f, 2f))
    }
}
