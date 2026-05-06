package rylween.runtime

import rylween.dsl.InputBindingModel
import rylween.dsl.TextBlock
import rylween.dsl.inlineText
import rylween.dsl.matches

data class InputCommand<out T>(
    val input: T,
    val binding: InputBindingModel,
    val help: String,
)

fun <T> List<InputCommand<T>>.inputForToken(token: String): T? = firstOrNull { command -> command.binding.matches(token) }?.input

fun List<InputCommand<*>>.commandBindings(): List<InputBindingModel> = map { command -> command.binding }

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

fun List<InputCommand<*>>.generatedHelpPages(maxLinesPerPage: Int = 7): List<TextBlock> {
    require(maxLinesPerPage > 0) { "maxLinesPerPage must be positive" }
    return chunked(maxLinesPerPage).map { page ->
        inlineText(*page.map { command -> "${command.binding.label} = ${command.help}" }.toTypedArray())
    }
}
