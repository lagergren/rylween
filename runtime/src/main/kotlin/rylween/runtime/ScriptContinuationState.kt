/**
 * Saveable compiled-script continuation records for the runtime.
 *
 * `ScriptExecutor.kt` creates these records when an authored script
 * hands control to a dungeon. `DungeonRuntime.kt` stores the record
 * on the active dungeon state, and `SaveFile.kt` persists it so
 * a save taken inside the dungeon can still resume the parent authored script
 * after the party leaves.
 */

package rylween.runtime

import rylween.compiled.CompiledHandleRef

/** A resumable point inside one compiled script root. */
internal data class ScriptContinuationState(
    val root: CompiledHandleRef,
    val frames: List<ScriptFrameState>,
    val locals: Map<String, Int>,
    val textBindings: Map<String, String>,
    val activeInventorySlot: Int?,
)

/** One stack frame for a resumable compiled instruction block. */
internal data class ScriptFrameState(
    val blockPath: List<Int>,
    val nextIndex: Int,
    val insideLoop: Boolean,
)
