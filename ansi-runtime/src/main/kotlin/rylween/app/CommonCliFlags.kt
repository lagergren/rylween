/**
 * Shared command-line flag contracts for app launchers.
 *
 * The line-terminal and ANSI frontends both need state, save-slot, seed, hour,
 * and static-screen selection. This file keeps those parse/apply rules aligned
 * with startup validation in `engine/Startup.kt` and save slot handling in
 * `save/SaveSlots.kt`.
 */
package rylween.app

import rylween.dsl.StateId
import rylween.dsl.StaticScreenId
import rylween.dsl.stateId
import rylween.save.runtimeSaveSlotFile
import rylween.save.runtimeSaveSlotStateId
import java.io.File

internal interface CommonCliFlagHandler {
    fun applyHour(value: Int)

    fun applySeed(value: Long)

    fun applyState(value: StateId)

    fun applyLoadFile(value: File)

    fun applySaveFile(
        file: File,
        state: StateId? = null,
    )

    fun applyStaticScreen(value: StaticScreenId)

    fun applyNewGame()
}

internal fun commonCliFlagHandler(
    setHour: (Int) -> Unit,
    setSeed: (Long) -> Unit,
    setState: (StateId) -> Unit,
    setLoadFile: (File) -> Unit,
    setSaveFile: (File, StateId?) -> Unit,
    setStaticScreen: (StaticScreenId) -> Unit,
    setNewGame: () -> Unit,
): CommonCliFlagHandler =
    object : CommonCliFlagHandler {
        override fun applyHour(value: Int) = setHour(value)

        override fun applySeed(value: Long) = setSeed(value)

        override fun applyState(value: StateId) = setState(value)

        override fun applyLoadFile(value: File) = setLoadFile(value)

        override fun applySaveFile(
            file: File,
            state: StateId?,
        ) = setSaveFile(file, state)

        override fun applyStaticScreen(value: StaticScreenId) = setStaticScreen(value)

        override fun applyNewGame() = setNewGame()
    }

internal fun List<String>.consumeCommonCliFlag(
    index: Int,
    handler: CommonCliFlagHandler,
): Int? {
    val arg = this[index]
    return when (arg) {
        "--hour" -> {
            handler.applyHour(valueAfter(index, arg).toInt().mod(24))
            index + 2
        }
        "--seed" -> {
            handler.applySeed(valueAfter(index, arg).toLong())
            index + 2
        }
        "--state" -> {
            handler.applyState(stateId(valueAfter(index, arg)))
            index + 2
        }
        "--load" -> {
            handler.applyLoadFile(File(valueAfter(index, arg)))
            index + 2
        }
        "--load-slot" -> {
            handler.applyLoadFile(runtimeSaveSlotFile(valueAfter(index, arg)))
            index + 2
        }
        "--save" -> {
            handler.applySaveFile(File(valueAfter(index, arg)))
            index + 2
        }
        "--save-slot" -> {
            val slot = valueAfter(index, arg)
            handler.applySaveFile(runtimeSaveSlotFile(slot), runtimeSaveSlotStateId(slot))
            index + 2
        }
        "--screen" -> {
            handler.applyStaticScreen(staticScreenArg(valueAfter(index, arg)))
            index + 2
        }
        "--new", "--new-game" -> {
            handler.applyNewGame()
            index + 1
        }
        else -> null
    }
}
