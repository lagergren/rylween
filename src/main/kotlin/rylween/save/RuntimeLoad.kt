/**
 * Runtime save loading, parsing diagnostics, and compatibility checks.
 *
 * This is the read-side counterpart to `RuntimeSave.kt`; it uses
 * `StateSnapshot.kt` to parse saves, [validateRuntimeStateSnapshotForLoad] to
 * check authored-content references, and `SaveCompatibility.kt` metadata to
 * reject stale snapshots.
 */
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

/** Loads a save file and returns its validated runtime state. */
fun loadRuntimeStateSnapshot(
    file: File,
    content: GameModel,
    sourceName: String = "save file ${file.path}",
    compiledBundleHash: String? = null,
): StateModel = parseRuntimeStateSnapshotForLoad(file.readText(), content, sourceName, compiledBundleHash)

/** Loads a save file and returns its validated full runtime snapshot. */
fun loadRuntimeSnapshot(
    file: File,
    content: GameModel,
    sourceName: String = "save file ${file.path}",
    compiledBundleHash: String? = null,
): RuntimeSnapshotModel = parseRuntimeSnapshotForLoad(file.readText(), content, sourceName, compiledBundleHash)

/** Parses snapshot text for loading and returns only the validated state. */
fun parseRuntimeStateSnapshotForLoad(
    source: String,
    content: GameModel,
    sourceName: String = "runtime snapshot",
    compiledBundleHash: String? = null,
): StateModel = parseRuntimeSnapshotForLoad(source, content, sourceName, compiledBundleHash).state

/** Parses snapshot text for loading and returns the validated full snapshot. */
fun parseRuntimeSnapshotForLoad(
    source: String,
    content: GameModel,
    sourceName: String = "runtime snapshot",
    compiledBundleHash: String? = null,
): RuntimeSnapshotModel =
    validateRuntimeStateSnapshotForLoad(
        snapshot = parseRuntimeSnapshotWithDiagnostics(source, sourceName),
        content = content,
        sourceName = sourceName,
        compiledBundleHash = compiledBundleHash,
    )

/** Validates a parsed runtime state against current authored [content]. */
fun validateRuntimeStateSnapshotForLoad(
    state: StateModel,
    content: GameModel,
    sourceName: String = "runtime snapshot",
): StateModel = validateRuntimeStateSnapshotForLoad(RuntimeSnapshotModel(state), content, sourceName).state

/** Validates a full runtime snapshot, including metadata and nested mode state. */
fun validateRuntimeStateSnapshotForLoad(
    snapshot: RuntimeSnapshotModel,
    content: GameModel,
    sourceName: String = "runtime snapshot",
    compiledBundleHash: String? = null,
): RuntimeSnapshotModel {
    try {
        validateStates(listOf(snapshot.state), content)
    } catch (error: RylweenValidationException) {
        throw error.withDiagnosticPathPrefix(sourceName)
    } catch (error: IllegalStateException) {
        throw runtimeLoadFailure(sourceName, error.message ?: "runtime snapshot is not valid")
    }
    validateSnapshotMetadata(snapshot, content, sourceName, compiledBundleHash)
    validateCompiledContinuationBundle(snapshot, sourceName, compiledBundleHash)
    validateModeSnapshot(snapshot, content, sourceName)
    return snapshot
}

private fun parseRuntimeSnapshotWithDiagnostics(
    source: String,
    sourceName: String,
): RuntimeSnapshotModel =
    try {
        parseRuntimeSnapshot(source)
    } catch (error: RuntimeException) {
        throw runtimeLoadFailure(sourceName, "could not parse runtime snapshot: ${error.message ?: error::class.simpleName}")
    }

private fun validateSnapshotMetadata(
    snapshot: RuntimeSnapshotModel,
    content: GameModel,
    sourceName: String,
    compiledBundleHash: String?,
) {
    val metadata = snapshot.metadata ?: return
    if (metadata.formatVersion != RUNTIME_SNAPSHOT_FORMAT_VERSION) {
        throw runtimeLoadFailure(
            sourceName,
            "runtime snapshot format version ${metadata.formatVersion} is not supported; " +
                "expected $RUNTIME_SNAPSHOT_FORMAT_VERSION",
        )
    }
    val universe = content.metadata.universe
    if (metadata.universeId != null && metadata.universeId != universe.compatibilityId) {
        throw runtimeLoadFailure(
            sourceName,
            "runtime snapshot universe ${metadata.universeId} does not match this game universe " +
                universe.compatibilityId,
        )
    }
    if (metadata.universeVersion != null && metadata.universeVersion != universe.version) {
        throw runtimeLoadFailure(
            sourceName,
            "runtime snapshot universe version ${metadata.universeVersion} does not match this game version " +
                universe.version,
        )
    }
    val expectedHash = content.runtimeContentHash()
    if (metadata.contentHash != null && metadata.contentHash != expectedHash) {
        throw runtimeLoadFailure(sourceName, "runtime snapshot content hash does not match this game build")
    }
}

private fun validateCompiledContinuationBundle(
    snapshot: RuntimeSnapshotModel,
    sourceName: String,
    compiledBundleHash: String?,
) {
    if (!snapshot.hasCompiledVmContinuation()) {
        return
    }
    val savedHash =
        snapshot.metadata?.compiledBundleHash
            ?: throw runtimeLoadFailure(sourceName, "compiled VM continuation requires compiled bundle hash metadata")
    if (compiledBundleHash == null) {
        throw runtimeLoadFailure(sourceName, "compiled VM continuation requires a verified compiled bundle hash")
    }
    if (savedHash != compiledBundleHash) {
        throw runtimeLoadFailure(sourceName, "runtime snapshot compiled bundle hash does not match this game bundle")
    }
}

private fun RuntimeSnapshotModel.hasCompiledVmContinuation(): Boolean =
    when (val activeMode = mode) {
        is RuntimeModeSnapshot.Dungeon -> activeMode.postDungeon?.continuation?.compiledVmContinuation != null
        is RuntimeModeSnapshot.Castle,
        null,
        -> false
    }

private fun validateModeSnapshot(
    snapshot: RuntimeSnapshotModel,
    content: GameModel,
    sourceName: String,
) {
    when (snapshot.state.realm.currentMode) {
        RuntimeMode.Castle -> {
            if (snapshot.mode !is RuntimeModeSnapshot.Castle) {
                throw runtimeLoadFailure(sourceName, "Castle runtime snapshot requires sessions { castle { ... } }")
            }
        }
        RuntimeMode.Dungeon -> {
            val dungeon =
                snapshot.mode as? RuntimeModeSnapshot.Dungeon
                    ?: throw runtimeLoadFailure(sourceName, "Dungeon runtime snapshot requires sessions { dungeon { ... } }")
            validateDungeonSnapshot(dungeon, content, sourceName)
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

private fun validateDungeonSnapshot(
    dungeon: RuntimeModeSnapshot.Dungeon,
    content: GameModel,
    sourceName: String,
) {
    val session = dungeon.session
    if (session.maxDepth < 1) {
        throw runtimeLoadFailure(sourceName, "Dungeon session maxDepth must be at least 1")
    }
    if (session.depth !in 1..session.maxDepth) {
        throw runtimeLoadFailure(sourceName, "Dungeon session depth must be between 1 and maxDepth")
    }
    if (content.dungeons.scenarios.none { it.dungeon == session.dungeon }) {
        throw runtimeLoadFailure(sourceName, "Dungeon session references unknown dungeon ${session.dungeon.slug}")
    }
    val eventIds =
        content.dungeons.events
            .map { it.id }
            .toSet()
    session.levelEvents.forEach { (level, event) ->
        if (level !in 1..session.maxDepth) {
            throw runtimeLoadFailure(sourceName, "Dungeon level event $level is outside maxDepth ${session.maxDepth}")
        }
        if (event !in eventIds) {
            throw runtimeLoadFailure(sourceName, "Dungeon level event references unknown event ${event.slug}")
        }
    }
    dungeon.postDungeon?.let { postDungeon ->
        if (content.missions.none { it.id == postDungeon.mission }) {
            throw runtimeLoadFailure(sourceName, "post-dungeon continuation references unknown mission ${postDungeon.mission.slug}")
        }
        if (content.scenes.none { it.id == postDungeon.scene }) {
            throw runtimeLoadFailure(sourceName, "post-dungeon continuation references unknown scene ${postDungeon.scene.slug}")
        }
        if (postDungeon.continuation.stack.isEmpty()) {
            throw runtimeLoadFailure(sourceName, "post-dungeon continuation stack must not be empty")
        }
    }
}

private fun runtimeLoadFailure(
    sourceName: String,
    message: String,
): RylweenValidationException = RylweenValidationException(listOf(ValidationDiagnostic(sourceName, message)))
