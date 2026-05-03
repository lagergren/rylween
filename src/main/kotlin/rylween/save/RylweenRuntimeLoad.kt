package rylween.save

import rylween.dsl.GameModel
import rylween.dsl.RylweenValidationException
import rylween.dsl.StateModel
import rylween.dsl.ValidationDiagnostic
import rylween.dsl.validateStates
import rylween.dsl.withDiagnosticPathPrefix

import java.io.File

fun loadRuntimeStateSnapshot(
    file: File,
    content: GameModel,
    sourceName: String = "save file ${file.path}",
): StateModel =
    parseRuntimeStateSnapshotForLoad(file.readText(), content, sourceName)

fun parseRuntimeStateSnapshotForLoad(
    source: String,
    content: GameModel,
    sourceName: String = "runtime snapshot",
): StateModel =
    validateRuntimeStateSnapshotForLoad(
        state = parseRuntimeStateSnapshotWithDiagnostics(source, sourceName),
        content = content,
        sourceName = sourceName,
    )

fun validateRuntimeStateSnapshotForLoad(
    state: StateModel,
    content: GameModel,
    sourceName: String = "runtime snapshot",
): StateModel {
    try {
        validateStates(listOf(state), content)
    } catch (error: RylweenValidationException) {
        throw error.withDiagnosticPathPrefix(sourceName)
    } catch (error: IllegalStateException) {
        throw runtimeLoadFailure(sourceName, error.message ?: "runtime snapshot is not valid")
    }
    return state
}

private fun parseRuntimeStateSnapshotWithDiagnostics(source: String, sourceName: String): StateModel =
    try {
        parseRuntimeStateSnapshot(source)
    } catch (error: RuntimeException) {
        throw runtimeLoadFailure(sourceName, "could not parse runtime snapshot: ${error.message ?: error::class.simpleName}")
    }

private fun runtimeLoadFailure(sourceName: String, message: String): RylweenValidationException =
    RylweenValidationException(listOf(ValidationDiagnostic(sourceName, message)))
