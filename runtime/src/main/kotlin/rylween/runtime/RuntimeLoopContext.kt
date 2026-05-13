package rylween.runtime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.ExternalRuntimeSystems
import java.io.File

internal data class RuntimeSaveContext(
    val file: File,
    val bundleContentHash: String,
    val userId: String,
) {
    fun write(
        runtime: RuntimeState,
        content: CompiledContentBundle,
    ) {
        SaveFile.write(file, runtime, bundleContentHash, content, userId)
    }
}

internal data class RuntimeLoopContext(
    val content: CompiledContentBundle,
    val systems: ExternalRuntimeSystems,
    val input: RuntimeInputPort,
    val screen: RuntimeScreenPort,
    val save: RuntimeSaveContext,
    val log: RuntimeLog,
)
