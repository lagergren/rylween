package rylween

import rylween.dsl.TextBlock
import rylween.dsl.TextColor
import rylween.dsl.TextLine
import rylween.dsl.TextPart
import rylween.dsl.flowText
import rylween.dsl.literalText
import kotlin.test.Test
import kotlin.test.assertEquals

/** Markers written inside game text: `{pause}` beats and `{red}`-style line colors. */
class TextMarkerTest {
    @Test
    fun aColorMarkerStartsItsLine() {
        assertEquals(
            TextBlock.Structured(listOf(TextLine(listOf(TextPart.LineColor(TextColor.Red), TextPart.Literal("Walter DIES!"))))),
            literalText("{red}Walter DIES!"),
        )
    }

    @Test
    fun aPauseMarkerSetsItsLength() {
        assertEquals(
            TextBlock.Flow(listOf(TextLine(listOf(TextPart.Literal("Ahh...! "), TextPart.Pause(1_000))))),
            flowText("Ahh...! {pause 1s}"),
        )
        assertEquals(
            TextPart.Pause(1_500),
            (flowText("{pause}") as TextBlock.Flow)
                .paragraphs
                .single()
                .parts
                .single(),
        )
        assertEquals(
            TextPart.Pause(600),
            (flowText("{pause 600ms}") as TextBlock.Flow)
                .paragraphs
                .single()
                .parts
                .single(),
        )
    }

    @Test
    fun textWithoutMarkersStaysOneLiteral() {
        assertEquals(TextBlock.Inline(listOf("No {marker} here.")), literalText("No {marker} here."))
    }
}
