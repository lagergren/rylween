package rylween.save

import rylween.dsl.StateId
import rylween.dsl.StateKind
import rylween.dsl.StateModel
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

fun interface RuntimeSavePort {
    fun save(runtime: RuntimeSessionState): RuntimeSaveOutcome
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
        runCatching {
            val snapshot = runtime.toRuntimeStateSnapshot(snapshotId)
            val rendered = renderRuntimeStateSnapshot(snapshot)
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile ?: File("."), "${file.name}.tmp")
            tmp.writeText(rendered)
            moveSaveFile(tmp, file)
            RuntimeSaveOutcome(saved = true, target = file.path)
        }.getOrElse { error ->
            RuntimeSaveOutcome(saved = false, target = file.path, error = error.message)
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
