/**
 * Semantic command bindings shared by runtime sessions and frontends.
 *
 * Sessions expose [InputCommand] lists; terminal adapters in
 * `frontend/terminal` render their bindings, and command parsers map raw input
 * back to session-specific input values.
 */
package rylween.runtime

import rylween.dsl.InputBindingModel
import rylween.dsl.TextBlock
import rylween.dsl.inlineText
import rylween.dsl.matches

/** One user-facing binding mapped to a typed session input. */
data class InputCommand<out T>(
    val input: T,
    val binding: InputBindingModel,
    val help: String,
)

/** Resolves a raw input [token] to the first command input it matches. */
fun <T> List<InputCommand<T>>.inputForToken(token: String): T? = firstOrNull { command -> command.binding.matches(token) }?.input

/** Extracts just the input bindings for prompt rendering. */
fun List<InputCommand<*>>.commandBindings(): List<InputBindingModel> = map { command -> command.binding }

/** Builds the standard north/south/west/east command list for map-like sessions. */
fun <T> cardinalDirectionCommands(
    moveNorth: InputBindingModel,
    moveSouth: InputBindingModel,
    moveWest: InputBindingModel,
    moveEast: InputBindingModel,
    input: (WildernessDirection) -> T,
): List<InputCommand<T>> =
    listOf(
        InputCommand(input(WildernessDirection.North), moveNorth, "Move North"),
        InputCommand(input(WildernessDirection.South), moveSouth, "Move South"),
        InputCommand(input(WildernessDirection.West), moveWest, "Move West"),
        InputCommand(input(WildernessDirection.East), moveEast, "Move East"),
    )

/** Generates default help pages from command labels when authored help text is absent. */
fun List<InputCommand<*>>.generatedHelpPages(maxLinesPerPage: Int = 7): List<TextBlock> {
    require(maxLinesPerPage > 0) { "maxLinesPerPage must be positive" }
    return chunked(maxLinesPerPage).map { page ->
        inlineText(*page.map { command -> "${command.binding.label} = ${command.help}" }.toTypedArray())
    }
}
