package rylween.save

import rylween.dsl.StateId
import rylween.dsl.StateKind
import rylween.dsl.StateModel
import rylween.dsl.RuntimeMode
import rylween.runtime.RuntimeModeSnapshot
import rylween.runtime.RuntimeSnapshotModel
import rylween.runtime.RuntimeSessionState

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class RuntimeSaveOutcome(
    val saved: Boolean,
    val target: String? = null,
    val error: String? = null,
)

data class RuntimeSaveRequest(
    val runtime: RuntimeSessionState,
    val mode: RuntimeModeSnapshot? = null,
)

fun interface RuntimeSavePort {
    fun save(runtime: RuntimeSessionState): RuntimeSaveOutcome

    fun save(request: RuntimeSaveRequest): RuntimeSaveOutcome =
        save(request.runtime)
}

fun RuntimeSessionState.toRuntimeStateSnapshot(id: StateId): StateModel =
    StateModel(
        id = id,
        kind = StateKind.RuntimeSnapshot,
        realm = realm,
        locations = locations,
        npcs = npcs,
        parties = parties,
    )

class FileRuntimeSavePort(
    private val file: File,
    private val snapshotId: StateId,
) : RuntimeSavePort {
    override fun save(runtime: RuntimeSessionState): RuntimeSaveOutcome =
        save(RuntimeSaveRequest(runtime))

    override fun save(request: RuntimeSaveRequest): RuntimeSaveOutcome =
        runCatching {
            checkYdargSnapshot(request)
            val snapshot = RuntimeSnapshotModel(
                state = request.runtime.toRuntimeStateSnapshot(snapshotId),
                mode = request.mode,
            )
            val rendered = renderRuntimeStateSnapshot(snapshot)
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile ?: File("."), "${file.name}.tmp")
            tmp.writeText(rendered)
            moveSaveFile(tmp, file)
            RuntimeSaveOutcome(saved = true, target = file.path)
        }.getOrElse { error ->
            RuntimeSaveOutcome(saved = false, target = file.path, error = error.message)
        }

    private fun checkYdargSnapshot(request: RuntimeSaveRequest) {
        if (
            request.runtime.realm.currentMode == RuntimeMode.YdargCastle &&
            request.mode !is RuntimeModeSnapshot.YdargCastle
        ) {
            error("YdargCastle runtime save requires Ydarg session snapshot")
        }
    }

    private fun moveSaveFile(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }
}
