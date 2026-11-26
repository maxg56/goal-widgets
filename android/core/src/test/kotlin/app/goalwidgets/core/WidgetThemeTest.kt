package app.goalwidgets.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WidgetThemeTest {
    @Test
    fun `widgets placed before themes keep the vivid look`() {
        assertEquals(WidgetTheme.VIVID, WidgetTheme.fromWire(null))
        assertEquals(WidgetTheme.VIVID, WidgetTheme.fromWire("unknown"))
    }

    @Test
    fun `every theme round-trips through its wire value`() {
        WidgetTheme.entries.forEach { assertEquals(it, WidgetTheme.fromWire(it.wire)) }
    }
}
