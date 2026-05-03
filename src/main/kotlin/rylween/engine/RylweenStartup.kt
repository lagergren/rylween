package rylween.engine

import rylween.dsl.GameModel
import rylween.dsl.RuntimeMode
import rylween.dsl.RylweenModel
import rylween.dsl.RylweenValidationException
import rylween.dsl.StateId
import rylween.dsl.StateModel
import rylween.dsl.TownId
import rylween.dsl.TownModel
import rylween.dsl.ValidationDiagnostic
import rylween.dsl.validateStates
import rylween.dsl.withDiagnosticPathPrefix

enum class RuntimeStartupTarget {
    Wilderness,
    Town,
}

fun selectStartupState(
    game: RylweenModel,
    content: GameModel,
    state: StateId,
    target: RuntimeStartupTarget,
): StateModel {
    val selected = game.states.singleOrNull { it.id == state }
        ?: throw startupFailure(
            "startup.state",
            buildString {
                append("unknown state ${state.slug}")
                val available = game.states.map { it.id.slug }.sorted()
                if (available.isNotEmpty()) {
                    append("; available states: ")
                    append(available.joinToString(", "))
                }
            },
        )
    return validateStartupState(selected, content, target, "startup.state.${state.slug}")
}

fun validateStartupState(
    state: StateModel,
    content: GameModel,
    target: RuntimeStartupTarget,
    sourceName: String = "startup.state.${state.id.slug}",
): StateModel {
    try {
        validateStates(listOf(state), content)
    } catch (error: RylweenValidationException) {
        throw error.withDiagnosticPathPrefix(sourceName)
    } catch (error: IllegalStateException) {
        throw startupFailure(sourceName, error.message ?: "state is not valid")
    }

    if (
        target == RuntimeStartupTarget.Wilderness &&
        state.realm.currentMode !in setOf(RuntimeMode.Wilderness, RuntimeMode.Dungeon, RuntimeMode.YdargCastle)
    ) {
        throw startupFailure(
            sourceName,
            "wilderness startup can only resume ${RuntimeMode.Wilderness.name} or " +
                "${RuntimeMode.Dungeon.name} or ${RuntimeMode.YdargCastle.name} states for now; " +
                "state ${state.id.slug} is ${state.realm.currentMode.name}",
        )
    }
    return state
}

fun requireStartupTown(content: GameModel, town: TownId): TownModel =
    content.towns.singleOrNull { it.id == town }
        ?: throw startupFailure(
            "startup.town",
            buildString {
                append("unknown town ${town.slug}")
                val available = content.towns.map { it.id.slug }.sorted()
                if (available.isNotEmpty()) {
                    append("; available towns: ")
                    append(available.joinToString(", "))
                }
            },
        )

private fun startupFailure(path: String, message: String): RylweenValidationException =
    RylweenValidationException(listOf(ValidationDiagnostic(path, message)))
