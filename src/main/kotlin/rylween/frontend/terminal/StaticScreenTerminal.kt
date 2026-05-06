/**
 * Terminal static-screen rendering from authored visual resources.
 *
 * `ScoreTerminal.kt` supplies runtime-backed score pages, wilderness command
 * helpers supply generated help pages, and app launchers use the returned
 * [TerminalStaticScreen] to display resource-backed static screens.
 */
package rylween.frontend.terminal

import rylween.dsl.GameModel
import rylween.dsl.ImageId
import rylween.dsl.StaticScreenId
import rylween.runtime.wilderness.effectiveHelpPages

/** Rendered terminal static screen with optional image and paged text. */
data class TerminalStaticScreen(
    val id: StaticScreenId,
    val image: ImageId?,
    val pages: List<List<String>>,
)

/** Renders one authored static screen for terminal presentation. */
fun GameModel.renderTerminalStaticScreen(
    id: StaticScreenId,
    renderer: TerminalTextRenderer = terminalTextRenderer(),
    resourceText: (String) -> String,
    context: TerminalTextContext = TerminalTextContext(),
    maxLinesPerPage: Int = 22,
): TerminalStaticScreen? {
    require(maxLinesPerPage > 0) { "maxLinesPerPage must be positive" }
    val screen = visuals.staticScreens.singleOrNull { it.id == id } ?: return null
    val runtimePages =
        context.runtime?.let { runtime ->
            screen.runtimeReport?.renderRuntimeStaticScreenPages(
                content = this,
                runtime = runtime,
                maxLinesPerPage = maxLinesPerPage,
            )
        }
    val pages =
        runtimePages ?: when {
            screen.commandHelp ->
                wilderness.keys
                    .effectiveHelpPages(maxLinesPerPage)
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
            val lines =
                explicitPage
                    .lineSequence()
                    .map { line -> line.trimEnd('\r') }
                    .toList()
                    .dropTrailingEmptyLines()
            if (lines.isEmpty()) emptyList() else lines.chunked(maxLinesPerPage)
        }

private fun String.normalizeLineEndings(): String = replace("\r\n", "\n").replace('\r', '\n')

private fun List<String>.dropTrailingEmptyLines(): List<String> {
    val end = indexOfLast { it.isNotEmpty() }
    return if (end < 0) emptyList() else take(end + 1)
}
