/**
 * Saveable compiled-script continuation records for the standalone runtime.
 *
 * `StandaloneScriptExecutor.kt` creates these records when an authored script
 * hands control to a dungeon. `StandaloneDungeonRuntime.kt` stores the record
 * on the active dungeon state, and `StandaloneCompiledSave.kt` persists it so
 * a save taken inside the dungeon can still resume the parent authored script
 * after the party leaves.
 */

package rylween.compiledruntime

import rylween.compiled.CompiledHandleRef

/** A resumable point inside one compiled script root. */
internal data class StandaloneScriptContinuation(
    val root: CompiledHandleRef,
    val frames: List<StandaloneScriptFrame>,
    val locals: Map<String, Int>,
    val textBindings: Map<String, String>,
    val activeInventorySlot: Int?,
)

/** One stack frame for a resumable compiled instruction block. */
internal data class StandaloneScriptFrame(
    val blockPath: List<Int>,
    val nextIndex: Int,
    val insideLoop: Boolean,
)
