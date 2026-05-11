/**
 * Wilderness command bindings for the runtime.
 *
 * The keys come from `runtimeSystems.commands.wilderness` in the J1/J2 bundle,
 * not from Kotlin constants in the old runtime. Raw arrow escape sequences are
 * normalized by `:terminal` before this matcher sees them, so movement can
 * be driven by either numpad digits or terminal arrows.
 */

package rylween.runtime

import rylween.compiled.ExternalRuntimeCommand
import rylween.compiled.ExternalRuntimeInputBinding
import rylween.compiled.ExternalRuntimeSystems

private const val WILDERNESS_COMMAND_GROUP = "wilderness"

/** Matches runtime wilderness input against runtime-system bindings. */
internal class WildernessCommands private constructor(
    private val commands: Map<String, ExternalRuntimeCommand>,
) {
    fun matches(
        action: String,
        command: Char,
    ): Boolean = commands[action]?.binding?.matches(command) == true

    companion object {
        fun from(
            systems: ExternalRuntimeSystems,
            group: String = WILDERNESS_COMMAND_GROUP,
        ): WildernessCommands =
            WildernessCommands(
                (
                    if (group == WILDERNESS_COMMAND_GROUP) {
                        systems.commands.getValue(group)
                    } else {
                        systems.commands[group].orEmpty()
                    }
                ).associateBy(ExternalRuntimeCommand::action),
            )
    }
}

internal fun ExternalRuntimeInputBinding.matches(command: Char): Boolean =
    (listOf(primary) + aliases).any { token -> token.matchesCommand(command) }

private fun String.matchesCommand(command: Char): Boolean =
    when (this) {
        "SPACE" -> command == ' '
        else -> equals(command.toString(), ignoreCase = true)
    }
