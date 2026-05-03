package rylween.frontend.terminal

import rylween.dsl.GameModel
import rylween.dsl.ImageId
import rylween.dsl.StaticScreenId
import rylween.runtime.effectiveHelpPages

data class TerminalStaticScreen(
    val id: StaticScreenId,
    val image: ImageId?,
    val pages: List<List<String>>,
)

fun GameModel.renderTerminalStaticScreen(
    id: StaticScreenId,
    renderer: TerminalTextRenderer = terminalTextRenderer(),
    resourceText: (String) -> String,
    context: TerminalTextContext = TerminalTextContext(),
    maxLinesPerPage: Int = 22,
): TerminalStaticScreen? {
    require(maxLinesPerPage > 0) { "maxLinesPerPage must be positive" }
    val screen = visuals.staticScreens.singleOrNull { it.id == id } ?: return null
    val pages = when {
        screen.commandHelp -> wilderness.keys.effectiveHelpPages(maxLinesPerPage)
            .map { page -> renderer.lines(page, context) }
        screen.resource != null -> resourceText(screen.resource).documentPages(maxLinesPerPage)
        else -> emptyList()
    }
    return TerminalStaticScreen(
        id = screen.id,
        image = screen.image,
        pages = pages,
    )
}

private fun String.documentPages(maxLinesPerPage: Int): List<List<String>> =
    normalizeLineEndings()
        .split('\u000C')
        .flatMap { explicitPage ->
            val lines = explicitPage
                .lineSequence()
                .map { line -> line.trimEnd('\r') }
                .toList()
                .dropTrailingEmptyLines()
            if (lines.isEmpty()) emptyList() else lines.chunked(maxLinesPerPage)
        }

private fun String.normalizeLineEndings(): String =
    replace("\r\n", "\n").replace('\r', '\n')

private fun List<String>.dropTrailingEmptyLines(): List<String> {
    val end = indexOfLast { it.isNotEmpty() }
    return if (end < 0) emptyList() else take(end + 1)
}
