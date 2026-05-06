package rylween.save

import rylween.dsl.StateId
import rylween.dsl.stateId
import java.io.File

private val RuntimeSaveSlotPattern = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")

fun runtimeSaveSlotFile(
    slot: String,
    directory: File = File("build/saves"),
): File {
    val name = validateRuntimeSaveSlot(slot)
    return File(directory, "$name.kts")
}

fun runtimeSaveSlotStateId(slot: String): StateId = stateId("save-${validateRuntimeSaveSlot(slot)}")

private fun validateRuntimeSaveSlot(slot: String): String {
    val name = slot.trim()
    require(RuntimeSaveSlotPattern.matches(name)) {
        "save slot must be 1..64 characters using letters, digits, '_' or '-', starting with a letter or digit"
    }
    return name
}
