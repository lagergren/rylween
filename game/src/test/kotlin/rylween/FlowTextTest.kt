package rylween

import rylween.dsl.TextBlock
import rylween.dsl.TextLine
import rylween.dsl.TextPart
import rylween.dsl.flowText
import kotlin.test.Test
import kotlin.test.assertEquals

/** How flow text turns its strings and runtime values into paragraphs. */
class FlowTextTest {
    @Test
    fun aBlankLineStartsAParagraphAndTheSpacesBesideAValueStay() {
        assertEquals(
            TextBlock.Flow(
                listOf(
                    TextLine(listOf(TextPart.Literal("Welcome to "), TextPart.CurrentTownName, TextPart.Literal("."))),
                    TextLine(listOf(TextPart.Literal("Do you want supper?"))),
                ),
            ),
            flowText("Welcome to ", TextPart.CurrentTownName, ".\n\nDo you want supper?"),
        )
    }

    @Test
    fun aNewlineAndTheIndentationAroundItAreOneSpace() {
        assertEquals(
            flowText("He quickly fled, but I know it's him."),
            flowText(
                """
                He quickly fled,
                    but I know it's him.
                """,
            ),
        )
    }
}
