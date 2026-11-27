package app.goalwidgets.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WidgetThemeTest {
    @Test
    fun `widgets without a theme follow the system (soft)`() {
        assertEquals(WidgetTheme.SOFT, WidgetTheme.fromWire(null))
        assertEquals(WidgetTheme.SOFT, WidgetTheme.fromWire("unknown"))
    }

    @Test
    fun `every theme round-trips through its wire value`() {
        WidgetTheme.entries.forEach { assertEquals(it, WidgetTheme.fromWire(it.wire)) }
    }
}
