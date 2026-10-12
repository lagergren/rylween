package rylween.bitmapweb

import rylween.bitmapweb.BitmapDocumentLineKind.Code
import rylween.bitmapweb.BitmapDocumentLineKind.Heading2
import rylween.bitmapweb.BitmapDocumentLineKind.Heading3
import rylween.bitmapweb.BitmapDocumentLineKind.Quote
import rylween.bitmapweb.BitmapDocumentLineKind.Text
import rylween.runtime.runtimeResourceText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BitmapDocumentTest {
    @Test
    fun leadingTitleBecomesPageTitleAndHeadingsAreSpacedConsistently() {
        val layout =
            bitmapMarkdownDocumentLayout(
                """
                # Manual

                ## Section
                Intro text.
                ### Sub
                Sub text.

                ### Next
                More text.
                """.trimIndent().lines(),
            )

        assertEquals("Manual", layout.title)
        assertEquals(
            listOf(
                line(Heading2, "Section"),
                blank(),
                line(Text, "Intro text."),
                blank(),
                line(Heading3, "Sub"),
                line(Text, "Sub text."),
                blank(),
                line(Heading3, "Next"),
                line(Text, "More text."),
            ),
            layout.lines,
        )
    }

    @Test
    fun paragraphsAreReflowedSeparatedAndStrippedOfInlineMarkup() {
        val layout =
            bitmapMarkdownDocumentLayout(
                """
                He yelled: *"I declare
                war."* Use `S` **often**.
                Second  line &mdash; same paragraph.

                New paragraph.
                """.trimIndent().lines(),
                columns = 40,
            )

        assertNull(layout.title)
        assertEquals(
            listOf(
                line(Text, "He yelled: \"I declare war.\" Use S often."),
                line(Text, "Second line - same paragraph."),
                blank(),
                line(Text, "New paragraph."),
            ),
            layout.lines,
        )
    }

    @Test
    fun codeBlocksKeepTheirSpacingAndQuotesKeepTheirLineBreaks() {
        val art = "   ######             #######"
        val layout =
            bitmapMarkdownDocumentLayout(
                listOf(
                    "> First verse line",
                    "> second verse line",
                    "",
                    "```text",
                    art,
                    "",
                    "  ###  ### *not emphasis*",
                    "```",
                    "After.",
                ),
            )

        assertEquals(
            listOf(
                line(Quote, "First verse line"),
                line(Quote, "second verse line"),
                blank(),
                line(Code, art),
                line(Code, ""),
                line(Code, "  ###  ### *not emphasis*"),
                blank(),
                line(Text, "After."),
            ),
            layout.lines,
        )
    }

    @Test
    fun listItemsWrapWithHangingIndent() {
        val layout = bitmapMarkdownDocumentLayout(listOf("- alpha beta gamma", "- delta"), columns = 12)

        assertEquals(
            listOf(line(Text, "- alpha beta"), line(Text, "  gamma"), line(Text, "- delta")),
            layout.lines,
        )
    }

    @Test
    fun headingsMoveToTheNextPageRatherThanEndingOne() {
        val lines = texts(5) + blank() + line(Heading3, "Sub") + texts(3)

        val pages = lines.toBitmapDocumentPages(rows = 7)

        assertEquals(listOf(texts(5), listOf(line(Heading3, "Sub")) + texts(3)), pages)
    }

    @Test
    fun shortCodeBlocksAreNotSplitButLongOnesSplitWithEnoughLinesOnBothSides() {
        val short = texts(3) + blank() + code(4)
        assertEquals(listOf(texts(3), code(4)), short.toBitmapDocumentPages(rows = 6))

        val long = texts(1) + blank() + code(10)
        val pages = long.toBitmapDocumentPages(rows = 6)
        assertEquals(listOf(texts(1) + blank() + code(10).take(4), code(10).drop(4)), pages)

        val nearlyFits = texts(1) + blank() + code(5)
        assertEquals(listOf(texts(1), code(5)), nearlyFits.toBitmapDocumentPages(rows = 6))
    }

    @Test
    fun paragraphEndingInColonStaysWithTheCodeBlockItIntroduces() {
        val intro = listOf(line(Text, "one"), line(Text, "two"), line(Text, "three"), line(Text, "Terrain types are:"))
        val lines = texts(2) + blank() + intro + blank() + code(4)

        val pages = lines.toBitmapDocumentPages(rows = 8)

        assertEquals(listOf(texts(2) + blank() + intro.take(2), intro.drop(2) + blank() + code(4)), pages)
    }

    @Test
    fun paragraphEndingInColonStaysWithTheHeadingItIntroduces() {
        val lines = texts(4) + blank() + line(Text, "There are four towns:") + blank() + line(Heading3, "Thord") + texts(2)

        val pages = lines.toBitmapDocumentPages(rows = 7)

        assertEquals(listOf(texts(4), listOf(line(Text, "There are four towns:"), blank(), line(Heading3, "Thord")) + texts(2)), pages)
    }

    @Test
    fun codeBlocksDoNotSplitBeforeAnIndentedContinuationLine() {
        val table = listOf("Ann - one", "Bob - two", "Cid - three", "Dan - four,", "      continued", "Eve - five", "Fay - six")
        val lines = table.map { text -> line(Code, text) }

        val pages = lines.toBitmapDocumentPages(rows = 5)

        assertEquals(listOf(lines.take(3), lines.drop(3)), pages)
    }

    @Test
    fun manualPagesFitTheGridAndNeverStrandHeadingsOrLeadIns() {
        val source = runtimeResourceText("adventurers-of-rylween.md").lines()
        val layout = bitmapMarkdownDocumentLayout(source)
        val pages = layout.lines.toBitmapDocumentPages()

        assertEquals("Adventurers of Rylween", layout.title)
        val codeLines = codeBlockLines(source)
        assertEquals(codeLines, layout.lines.filter { line -> line.kind == Code }.map(BitmapDocumentLine::text))
        assertEquals(layout.lines.filterNot(BitmapDocumentLine::isBlank), pages.flatten().filterNot(BitmapDocumentLine::isBlank))
        pages.forEachIndexed { index, page ->
            val where = "manual page ${index + 1}"
            assertTrue(page.isNotEmpty() && page.size <= BITMAP_DOCUMENT_ROWS, "$where has ${page.size} lines")
            assertTrue(page.none { line -> line.text.length > BITMAP_DOCUMENT_COLUMNS }, "$where has an overlong line")
            assertTrue(!page.first().isBlank && !page.last().isBlank, "$where starts or ends with a blank line")
            assertTrue(!page.last().isHeading, "$where ends with a heading")
            val last = page.last()
            assertTrue(last.kind != Text || !last.text.endsWith(":"), "$where ends with a lead-in: ${last.text}")
            val first = page.first()
            val continuesCode = pages.getOrNull(index - 1)?.last()?.kind == Code
            assertTrue(!continuesCode || !first.text.startsWith(" "), "$where starts inside a table entry: ${first.text}")
        }
    }

    private fun codeBlockLines(source: List<String>): List<String> {
        var inCode = false
        return source.mapNotNull { line ->
            if (line.startsWith("```")) {
                inCode = !inCode
                null
            } else {
                line.trimEnd().takeIf { inCode }
            }
        }
    }

    private fun line(
        kind: BitmapDocumentLineKind,
        text: String,
    ) = BitmapDocumentLine(kind, text)

    private fun blank() = BitmapDocumentLine.blank

    private fun texts(count: Int) = (1..count).map { index -> line(Text, "text $index") }

    private fun code(count: Int) = (1..count).map { index -> line(Code, "code $index") }
}
