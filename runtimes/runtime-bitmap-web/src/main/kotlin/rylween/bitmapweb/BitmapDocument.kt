/**
 * Static document layout for the bitmap runtime: the manual, score reports, and
 * the journal are laid out server side on the fixed monospace grid that the
 * canvas client draws document pages with, so columns, ASCII art, and page
 * breaks come out exactly as composed here.
 */

package rylween.bitmapweb

internal const val BITMAP_DOCUMENT_COLUMNS = 96
internal const val BITMAP_DOCUMENT_ROWS = 21

private const val MIN_TEXT_LINES_PER_PAGE = 2
private const val MIN_BLOCK_LINES_PER_PAGE = 3
private const val TAB_WIDTH = 4

private val markdownHeading = Regex("""^(#{1,6})\s+(.*?)(?:\s+#+)?\s*$""")
private val markdownListItem = Regex("""^\s*([-*+]|\d+[.)])\s+(.*)$""")

internal enum class BitmapDocumentLineKind {
    Text,
    Heading1,
    Heading2,
    Heading3,
    Quote,
    Code,
}

internal data class BitmapDocumentLine(
    val kind: BitmapDocumentLineKind,
    val text: String,
) {
    val isBlank: Boolean get() = kind == BitmapDocumentLineKind.Text && text.isBlank()

    val isHeading: Boolean
        get() =
            kind == BitmapDocumentLineKind.Heading1 ||
                kind == BitmapDocumentLineKind.Heading2 ||
                kind == BitmapDocumentLineKind.Heading3

    fun toJson(): String =
        jsonObject(
            "kind" to kind.name.json(),
            "text" to text.json(),
        )

    companion object {
        val blank = BitmapDocumentLine(BitmapDocumentLineKind.Text, "")
    }
}

/** One document page: at most [rows] lines of at most [columns] characters, plus the navigation prompt. */
internal data class BitmapDocument(
    val lines: List<BitmapDocumentLine>,
    val prompt: String,
    val columns: Int = BITMAP_DOCUMENT_COLUMNS,
    val rows: Int = BITMAP_DOCUMENT_ROWS,
) {
    fun toJson(): String =
        jsonObject(
            "columns" to columns.toString(),
            "rows" to rows.toString(),
            "lines" to lines.joinToString(prefix = "[", postfix = "]") { line -> line.toJson() },
            "prompt" to prompt.json(),
        )
}

/** A laid-out document; [title] is the leading `# Title` of a Markdown source, which is shown as the page title instead. */
internal data class BitmapDocumentLayout(
    val title: String?,
    val lines: List<BitmapDocumentLine>,
)

internal fun bitmapPlainDocumentLayout(
    source: List<String>,
    columns: Int = BITMAP_DOCUMENT_COLUMNS,
): BitmapDocumentLayout =
    BitmapDocumentLayout(
        title = null,
        lines =
            source.flatMap { line ->
                line
                    .expandTabs()
                    .trimEnd()
                    .wrap(columns)
                    .map { part -> BitmapDocumentLine(BitmapDocumentLineKind.Text, part) }
            },
    )

internal fun bitmapMarkdownDocumentLayout(
    source: List<String>,
    columns: Int = BITMAP_DOCUMENT_COLUMNS,
): BitmapDocumentLayout {
    val layout = MarkdownLayout(columns)
    source.forEach(layout::accept)
    val lines = layout.finish()
    val first = lines.firstOrNull()
    val second = lines.getOrNull(1)
    if (first?.kind != BitmapDocumentLineKind.Heading1 || second?.kind == BitmapDocumentLineKind.Heading1) {
        return BitmapDocumentLayout(title = null, lines = lines)
    }
    return BitmapDocumentLayout(title = first.text, lines = lines.drop(1).dropWhile(BitmapDocumentLine::isBlank))
}

/**
 * Splits laid-out lines into pages of [rows] lines. Pages never start with a
 * blank line, headings stay with the start of their section, a paragraph that
 * ends in a colon stays with what it introduces, and a paragraph, quote, or
 * code block only splits across pages when enough of it lands on both sides.
 */
internal fun List<BitmapDocumentLine>.toBitmapDocumentPages(rows: Int = BITMAP_DOCUMENT_ROWS): List<List<BitmapDocumentLine>> {
    val pages = mutableListOf<List<BitmapDocumentLine>>()
    var page = mutableListOf<BitmapDocumentLine>()

    fun closePage() {
        pages += page.dropLastWhile(BitmapDocumentLine::isBlank)
        page = mutableListOf()
    }

    var index = 0
    while (index < size) {
        if (this[index].isBlank) {
            if (page.isNotEmpty()) page += this[index]
            index += 1
            if (page.size >= rows) closePage()
            continue
        }
        val take = linesToPlace(index, free = rows - page.size, pageEmpty = page.isEmpty())
        if (take == 0) {
            closePage()
            continue
        }
        page += subList(index, index + take)
        index += take
        val runSplit = index < size && !this[index].isBlank && this[index].kind == this[index - 1].kind
        if (page.size >= rows || runSplit) closePage()
    }
    if (page.isNotEmpty() || pages.isEmpty()) closePage()
    return pages
}

/**
 * How many lines of the run starting at [index] go on a page with [free] rows
 * left; 0 means the run should start on the next page instead.
 */
private fun List<BitmapDocumentLine>.linesToPlace(
    index: Int,
    free: Int,
    pageEmpty: Boolean,
): Int {
    val run = runLength(index)
    if (this[index].isHeading) {
        if (pageEmpty) return minOf(run, free)
        val next = nextContentIndex(index + run) ?: return if (run <= free) run else 0
        val followingFree = free - (next - index)
        return if (followingFree > 0 && linesToPlace(next, followingFree, pageEmpty = false) > 0) run else 0
    }
    val minLines = if (this[index].kind == BitmapDocumentLineKind.Text) MIN_TEXT_LINES_PER_PAGE else MIN_BLOCK_LINES_PER_PAGE
    var take =
        when {
            run <= free -> run
            else -> splitPoint(index, (if (run - free < minLines) run - minLines else free), minLines)
        }
    val introduced = if (take == run) introducedBlockIndex(index, run) else null
    if (introduced != null) {
        val followingFree = free - (introduced - index)
        if (followingFree <= 0 || linesToPlace(introduced, followingFree, pageEmpty = false) == 0) {
            take = splitPoint(index, run - minLines, minLines)
        }
    }
    return if (take == 0 && pageEmpty) minOf(run, free) else take
}

/**
 * Places a page break [head] lines into the run at [index], or earlier so an
 * indented code line (such as a wrapped table entry) stays with the line it
 * continues; 0 when fewer than [minLines] would remain on the page.
 */
private fun List<BitmapDocumentLine>.splitPoint(
    index: Int,
    head: Int,
    minLines: Int,
): Int {
    if (head < minLines) return 0
    if (this[index].kind != BitmapDocumentLineKind.Code) return head
    return (head downTo minLines).firstOrNull { candidate -> !this[index + candidate].text.startsWith(" ") } ?: head
}

/** Index of the block that the paragraph at [index] introduces with a trailing colon, if any. */
private fun List<BitmapDocumentLine>.introducedBlockIndex(
    index: Int,
    run: Int,
): Int? {
    if (this[index].kind != BitmapDocumentLineKind.Text || !this[index + run - 1].text.endsWith(":")) return null
    return nextContentIndex(index + run)
}

private fun List<BitmapDocumentLine>.nextContentIndex(from: Int): Int? = (from until size).firstOrNull { index -> !this[index].isBlank }

private fun List<BitmapDocumentLine>.runLength(index: Int): Int {
    val kind = this[index].kind
    var end = index
    while (end < size && this[end].kind == kind && !this[end].isBlank) end += 1
    return (end - index).coerceAtLeast(1)
}

/**
 * Lays out the Markdown subset used by game documents: headings, paragraphs,
 * lists, quotes (keeping their line breaks, as they usually hold verse), and
 * fenced code blocks, which keep their exact spacing.
 */
private class MarkdownLayout(
    private val columns: Int,
) {
    private enum class Block {
        None,
        Heading,
        Subheading,
        Paragraph,
        ListItem,
        Quote,
        Code,
    }

    private val lines = mutableListOf<BitmapDocumentLine>()
    private val paragraph = mutableListOf<String>()
    private var listItem: Pair<String, StringBuilder>? = null
    private var lastBlock = Block.None
    private var inCode = false

    fun accept(rawLine: String) {
        val line = rawLine.expandTabs().trimEnd()
        if (line.trimStart().startsWith("```")) {
            flush()
            if (!inCode) startBlock(Block.Code)
            inCode = !inCode
            return
        }
        if (inCode) {
            line.chunkedOrBlank(columns).forEach { part -> lines += BitmapDocumentLine(BitmapDocumentLineKind.Code, part) }
            return
        }
        val heading = markdownHeading.matchEntire(line)
        val item = markdownListItem.matchEntire(line)
        val pendingItem = listItem
        when {
            line.isBlank() -> flush()
            heading != null -> {
                flush()
                addHeading(heading.groupValues[1].length, heading.groupValues[2])
            }
            line.startsWith(">") -> {
                flushParagraph()
                flushListItem()
                if (lastBlock != Block.Quote) startBlock(Block.Quote)
                val text = line.removePrefix(">").trim().markdownInline()
                text.wrap(columns - 2).forEach { part -> lines += BitmapDocumentLine(BitmapDocumentLineKind.Quote, part) }
            }
            item != null -> {
                flushParagraph()
                flushListItem()
                startBlock(Block.ListItem)
                listItem = item.groupValues[1] to StringBuilder(item.groupValues[2].trim())
            }
            pendingItem != null && line.startsWith(" ") -> pendingItem.second.append(' ').append(line.trim())
            else -> {
                flushListItem()
                if (lastBlock == Block.Quote) lastBlock = Block.None
                paragraph += line.trim()
            }
        }
    }

    fun finish(): List<BitmapDocumentLine> {
        flush()
        return lines.dropLastWhile(BitmapDocumentLine::isBlank)
    }

    private fun addHeading(
        level: Int,
        text: String,
    ) {
        val kind =
            when (level) {
                1 -> BitmapDocumentLineKind.Heading1
                2 -> BitmapDocumentLineKind.Heading2
                else -> BitmapDocumentLineKind.Heading3
            }
        startBlock(if (level <= 2) Block.Heading else Block.Subheading)
        text
            .markdownInline()
            .collapseSpaces()
            .wrap(columns)
            .forEach { part -> lines += BitmapDocumentLine(kind, part) }
        // Top-level headings are underlined by the client and get a blank line
        // below; subheadings sit directly on top of their text.
        if (level <= 2) lines += BitmapDocumentLine.blank
    }

    private fun startBlock(block: Block) {
        val continuesList = block == Block.ListItem && lastBlock == Block.ListItem
        if (lines.isNotEmpty() && lastBlock != Block.Subheading && !continuesList && !lines.last().isBlank) {
            lines += BitmapDocumentLine.blank
        }
        lastBlock = block
    }

    private fun flush() {
        flushParagraph()
        flushListItem()
        if (lastBlock == Block.Quote) lastBlock = Block.None
    }

    private fun flushParagraph() {
        if (paragraph.isEmpty()) return
        startBlock(Block.Paragraph)
        paragraph
            .joinToString(" ")
            .markdownInline()
            .collapseSpaces()
            .wrap(columns)
            .forEach { part -> lines += BitmapDocumentLine(BitmapDocumentLineKind.Text, part) }
        paragraph.clear()
    }

    private fun flushListItem() {
        val (marker, text) = listItem ?: return
        val indent = " ".repeat(marker.length + 1)
        text
            .toString()
            .markdownInline()
            .collapseSpaces()
            .wrap(columns - indent.length)
            .forEachIndexed { index, part ->
                lines += BitmapDocumentLine(BitmapDocumentLineKind.Text, (if (index == 0) "$marker " else indent) + part)
            }
        listItem = null
    }
}

private fun String.markdownInline(): String =
    replace(Regex("""\*\*([^*]+)\*\*"""), "$1")
        .replace(Regex("""\*([^*]+)\*"""), "$1")
        .replace(Regex("""`([^`]+)`"""), "$1")
        .replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
        .replace("&mdash;", "-")
        .replace("&ndash;", "-")
        .replace("&amp;", "&")

private fun String.collapseSpaces(): String = trim().replace(Regex("""\s+"""), " ")

private fun String.expandTabs(): String = replace("\t", " ".repeat(TAB_WIDTH))

private fun String.chunkedOrBlank(width: Int): List<String> = if (isEmpty()) listOf("") else chunked(width)
