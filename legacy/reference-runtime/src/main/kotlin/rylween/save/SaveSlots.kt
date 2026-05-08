/**
 * Save-slot naming helpers for file and state ids.
 *
 * CLI launchers and terminal adapters use these helpers so file names,
 * generated [rylween.dsl.StateId] values, and validation errors follow one
 * slot-name policy.
 */
package rylween.save

import rylween.dsl.StateId
import rylween.dsl.stateId
import java.io.File

private val RuntimeSaveSlotPattern = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")

/** Returns the save file for [slot] under [directory] after validating the slot name. */
fun runtimeSaveSlotFile(
    slot: String,
    directory: File = File("build/saves"),
): File {
    val name = validateRuntimeSaveSlot(slot)
    return File(directory, "$name.kts")
}

/** Returns the synthetic runtime snapshot state id associated with [slot]. */
fun runtimeSaveSlotStateId(slot: String): StateId = stateId("save-${validateRuntimeSaveSlot(slot)}")

private fun validateRuntimeSaveSlot(slot: String): String {
    val name = slot.trim()
    require(RuntimeSaveSlotPattern.matches(name)) {
        "save slot must be 1..64 characters using letters, digits, '_' or '-', starting with a letter or digit"
    }
    return name
}
