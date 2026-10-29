package rylween.bitmapweb

import rylween.dsl.TextColor
import kotlin.test.Test
import kotlin.test.assertTrue

class BitmapFrameTest {
    /** The browser draws text itself, so a colored line arrives as plain text with its color named beside it. */
    @Test
    fun aColoredLineReachesTheBrowserAsPlainTextWithItsColor() {
        val json = frame(listOf("${TextColor.Red.escape}Walter DIES!", "Fine.")).toJson()

        assertTrue(""""textLines":["Walter DIES!", "Fine."]""" in json, json)
        assertTrue(""""textLineColors":["red", ""]""" in json, json)
    }

    @Test
    fun plainTextNamesNoColors() {
        assertTrue(""""textLineColors":[]""" in frame(listOf("Fine.")).toJson())
    }

    private fun frame(textLines: List<String>): BitmapFrame =
        BitmapFrame(
            mode = "script",
            backdrop = "",
            title = "",
            subtitle = "",
            statusLines = emptyList(),
            textLines = textLines,
            commands = emptyList(),
        )
}
