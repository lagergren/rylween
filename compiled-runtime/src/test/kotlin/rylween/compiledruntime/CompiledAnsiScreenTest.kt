package rylween.compiledruntime

import rylween.compiled.ExternalScreenRegion
import rylween.frontend.ansi.AnsiCodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompiledAnsiScreenTest {
    @Test
    fun mapRowsUseLegacyFiveTileStrideInsideDeclaredRegion() {
        val mapWindow = ExternalScreenRegion(id = "map_window", x = 8, y = 6, width = 16, height = 9)
        val statusTop = ExternalScreenRegion(id = "statusline_top", x = 3, y = 21, width = null, height = null)

        val rows = (0 until 5).map { row -> mapWindowCursorRow(mapWindow, row) }

        assertEquals(listOf(6, 8, 10, 12, 14), rows)
        assertTrue(rows.last() < statusTop.y, "map window rows must not overlap the status line")
    }

    @Test
    fun mapRowsStartOneColumnInsideClearableRegion() {
        val mapWindow = ExternalScreenRegion(id = "map_window", x = 8, y = 6, width = 16, height = 9)

        assertEquals("${AnsiCodes.CSI}6;1H\r${AnsiCodes.CSI}9C", mapWindowCursorSequence(mapWindow, row = 0))
    }
}
