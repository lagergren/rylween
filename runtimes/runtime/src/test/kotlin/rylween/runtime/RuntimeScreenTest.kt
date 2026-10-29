package rylween.runtime

import rylween.compiled.ExternalScreenRegion
import rylween.dsl.TextColor
import rylween.terminal.AnsiCodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuntimeScreenTest {
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

    @Test
    fun fullScreenTextStartsAtBeginningInsteadOfTail() {
        val lines = (1..30).map { index -> "line $index" }

        assertEquals((1..24).map { index -> "line $index" }, fullScreenTextFirstPage(lines))
    }

    @Test
    fun fullScreenPagingLeavesRoomForPrompt() {
        val lines = (1..50).map { index -> "line $index" }

        val pages = fullScreenTextPages(lines)

        assertEquals(listOf(23, 23, 4), pages.map(List<String>::size))
        assertEquals("line 1", pages.first().first())
        assertEquals("line 50", pages.last().last())
    }

    @Test
    fun textWindowRowsAlignColumnsAcrossTheMenuAndWrapProseInsteadOfCuttingIt() {
        val sep = TEXT_COLUMN_SEPARATOR
        assertEquals(
            listOf("A. Long sword B. Ration", "C. Torch      D. Rope"),
            textWindowRows(listOf("A. Long sword${sep}B. Ration", "C. Torch${sep}D. Rope"), width = 26),
        )
        assertEquals(
            listOf("A. Staff of T B. Fire Wand", "C. Torch      D. Rope"),
            textWindowRows(listOf("A. Staff of Thun${sep}B. Fire Wand", "C. Torch${sep}D. Rope"), width = 26),
        )
        assertEquals(listOf("There is nothing to enter", "here."), textWindowRows(listOf("There is nothing to enter here."), width = 26))
        assertEquals(listOf("Short line"), textWindowRows(listOf("Short line"), width = 26))
    }

    @Test
    fun aColoredLineKeepsItsColorOnEveryRowItWrapsTo() {
        val red = TextColor.Red.escape

        assertEquals(
            listOf("${red}Walter the Brave and", "${red}Mighty DIES!"),
            textWindowRows(listOf("${red}Walter the Brave and Mighty DIES!"), width = 20),
        )
    }
}
