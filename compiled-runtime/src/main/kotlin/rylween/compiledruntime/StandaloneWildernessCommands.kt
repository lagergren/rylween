/**
 * Wilderness command bindings for the standalone compiled-bundle runtime.
 *
 * The keys come from `runtimeSystems.commands.wilderness` in the J1/J2 bundle,
 * not from Kotlin constants in the old runtime. Raw arrow escape sequences are
 * normalized by `:ansi-terminal` before this matcher sees them, so movement can
 * be driven by either numpad digits or terminal arrows.
 */

package rylween.compiledruntime

import rylween.compiled.ExternalRuntimeCommand
import rylween.compiled.ExternalRuntimeInputBinding
import rylween.compiled.ExternalRuntimeSystems

private const val WILDERNESS_COMMAND_GROUP = "wilderness"

/** Matches standalone wilderness input against compiled runtime-system bindings. */
internal class StandaloneWildernessCommands private constructor(
    private val commands: Map<String, ExternalRuntimeCommand>,
) {
    fun matches(
        action: String,
        command: Char,
    ): Boolean = commands[action]?.binding?.matches(command) == true

    companion object {
        fun from(systems: ExternalRuntimeSystems): StandaloneWildernessCommands =
            StandaloneWildernessCommands(
                systems.commands
                    .getValue(WILDERNESS_COMMAND_GROUP)
                    .associateBy(ExternalRuntimeCommand::action),
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
