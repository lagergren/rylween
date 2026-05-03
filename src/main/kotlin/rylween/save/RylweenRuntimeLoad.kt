package rylween.save

import rylween.dsl.GameModel
import rylween.dsl.RuntimeMode
import rylween.dsl.RylweenValidationException
import rylween.dsl.StateModel
import rylween.dsl.ValidationDiagnostic
import rylween.dsl.validateStates
import rylween.dsl.withDiagnosticPathPrefix
import rylween.runtime.RuntimeModeSnapshot
import rylween.runtime.RuntimeSnapshotModel

import java.io.File

fun loadRuntimeStateSnapshot(
    file: File,
    content: GameModel,
    sourceName: String = "save file ${file.path}",
): StateModel =
    parseRuntimeStateSnapshotForLoad(file.readText(), content, sourceName)

fun loadRuntimeSnapshot(
    file: File,
    content: GameModel,
    sourceName: String = "save file ${file.path}",
): RuntimeSnapshotModel =
    parseRuntimeSnapshotForLoad(file.readText(), content, sourceName)

fun parseRuntimeStateSnapshotForLoad(
    source: String,
    content: GameModel,
    sourceName: String = "runtime snapshot",
): StateModel =
    parseRuntimeSnapshotForLoad(source, content, sourceName).state

fun parseRuntimeSnapshotForLoad(
    source: String,
    content: GameModel,
    sourceName: String = "runtime snapshot",
): RuntimeSnapshotModel =
    validateRuntimeStateSnapshotForLoad(
        snapshot = parseRuntimeSnapshotWithDiagnostics(source, sourceName),
        content = content,
        sourceName = sourceName,
    )

fun validateRuntimeStateSnapshotForLoad(
    state: StateModel,
    content: GameModel,
    sourceName: String = "runtime snapshot",
): StateModel =
    validateRuntimeStateSnapshotForLoad(RuntimeSnapshotModel(state), content, sourceName).state

fun validateRuntimeStateSnapshotForLoad(
    snapshot: RuntimeSnapshotModel,
    content: GameModel,
    sourceName: String = "runtime snapshot",
): RuntimeSnapshotModel {
    try {
        validateStates(listOf(snapshot.state), content)
    } catch (error: RylweenValidationException) {
        throw error.withDiagnosticPathPrefix(sourceName)
    } catch (error: IllegalStateException) {
        throw runtimeLoadFailure(sourceName, error.message ?: "runtime snapshot is not valid")
    }
    validateModeSnapshot(snapshot, sourceName)
    return snapshot
}

private fun parseRuntimeSnapshotWithDiagnostics(source: String, sourceName: String): RuntimeSnapshotModel =
    try {
        parseRuntimeSnapshot(source)
    } catch (error: RuntimeException) {
        throw runtimeLoadFailure(sourceName, "could not parse runtime snapshot: ${error.message ?: error::class.simpleName}")
    }

private fun validateModeSnapshot(snapshot: RuntimeSnapshotModel, sourceName: String) {
    when (snapshot.state.realm.currentMode) {
        RuntimeMode.YdargCastle -> {
            if (snapshot.mode !is RuntimeModeSnapshot.YdargCastle) {
                throw runtimeLoadFailure(sourceName, "YdargCastle runtime snapshot requires sessions { ydarg { ... } }")
            }
        }
        else -> {
            if (snapshot.mode != null) {
                throw runtimeLoadFailure(
                    sourceName,
                    "session snapshot ${snapshot.mode.javaClass.simpleName} does not match " +
                        "runtime mode ${snapshot.state.realm.currentMode.name}",
                )
            }
        }
    }
}

private fun runtimeLoadFailure(sourceName: String, message: String): RylweenValidationException =
    RylweenValidationException(listOf(ValidationDiagnostic(sourceName, message)))
