/**
 * Wilderness command bindings for the runtime.
 *
 * The keys come from `runtimeSystems.commands.wilderness` in the J1/J2 bundle,
 * not from Kotlin constants in the old runtime. Physical key tokens are
 * normalized before this matcher sees them, so movement can be driven by either
 * numpad digits or terminal arrows.
 */

package rylween.runtime

import rylween.compiled.ExternalRuntimeCommand
import rylween.compiled.ExternalRuntimeInputBinding
import rylween.compiled.ExternalRuntimeSystems
import rylween.compiled.RuntimeCommandActionId
import rylween.compiled.RuntimeCommandGroupId

private val wildernessCommandGroup = RuntimeCommandGroupId.Wilderness

/** Matches runtime wilderness input against runtime-system bindings. */
internal class WildernessCommands private constructor(
    private val commands: Map<String, ExternalRuntimeCommand>,
) {
    fun matches(
        action: RuntimeCommandActionId,
        command: Char,
    ): Boolean = commands[action.id]?.binding?.matches(command) == true

    companion object {
        fun from(
            systems: ExternalRuntimeSystems,
            group: RuntimeCommandGroupId = wildernessCommandGroup,
        ): WildernessCommands =
            WildernessCommands(
                (
                    if (group == wildernessCommandGroup) {
                        systems.commands.getValue(group.id)
                    } else {
                        systems.commands[group.id].orEmpty()
                    }
                ).associateBy(ExternalRuntimeCommand::action),
            )
    }
}

internal fun ExternalRuntimeInputBinding.matches(command: Char): Boolean =
    (listOf(primary) + aliases).any { token -> token.matchesCommand(command) }

internal fun List<ExternalRuntimeCommand>.matches(
    action: RuntimeCommandActionId,
    command: Char,
): Boolean =
    firstOrNull { candidate -> candidate.action == action.id }
        ?.binding
        ?.matches(command)
        ?: false

private fun String.matchesCommand(command: Char): Boolean =
    when (this) {
        "SPACE" -> command == ' '
        else -> equals(command.toString(), ignoreCase = true)
    }
