package rylween

import rylween.compiled.CompiledInstruction
import rylween.compiled.CompiledOperand
import rylween.compiled.CompiledViewCommand
import rylween.compiled.toCompiledGameBundle
import rylween.dsl.AudioCue
import rylween.dsl.MusicArea
import rylween.dsl.collectAuthoredContentDiagnostics
import rylween.dsl.gameSection
import rylween.dsl.impl.AreaMusicModel
import rylween.dsl.impl.AudioCueModel
import rylween.dsl.impl.AudioModel
import rylween.dsl.impl.ItemSoundsModel
import rylween.dsl.impl.SoundAssetModel
import rylween.dsl.item
import rylween.dsl.rylween
import rylween.dsl.soundId
import rylween.game.RylweenCompiledGame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioContentTest {
    @Test
    fun includedSectionsApplyInOrderAndAddUp() {
        val sounds =
            gameSection {
                audio {
                    sounds("audio/sfx/test") { sound(soundId("test.knock"), "knock.mp3") }
                }
            }
        val cues =
            gameSection {
                audio { cue(AudioCue.Drop, soundId("test.knock")) }
            }

        val audio = rylween { content { include(sounds, cues) } }.content.audio

        assertEquals(listOf("audio/sfx/test/knock.mp3"), audio.sounds.map { it.resource })
        assertEquals(listOf(AudioCueModel(AudioCue.Drop, listOf(soundId("test.knock")))), audio.cues)
    }

    @Test
    fun validationRejectsBrokenAudio() {
        val game = RylweenCompiledGame.nextDsl
        assertTrue(collectAuthoredContentDiagnostics(game).none { it.path == "audio" })

        fun audioErrors(audio: AudioModel): List<String> =
            collectAuthoredContentDiagnostics(game.copy(audio = audio)).filter { it.path == "audio" }.map { it.message }

        val undeclared =
            game.audio.copy(
                cues = game.audio.cues.map { cue -> if (cue.cue == AudioCue.Drop) cue.copy(sounds = listOf(soundId("missing"))) else cue },
            )
        assertTrue(audioErrors(undeclared).single().contains("undeclared sound missing"))

        val unknownItem = game.audio.copy(items = game.audio.items + ItemSoundsModel(item("no_such_item")))
        assertTrue(audioErrors(unknownItem).single().contains("unknown item no_such_item"))

        val badHours =
            game.audio.copy(
                areas =
                    game.audio.areas +
                        AreaMusicModel(
                            MusicArea.Town,
                            game.audio.music
                                .first()
                                .id,
                            20,
                            24,
                        ),
            )
        assertTrue(audioErrors(badHours).single().contains("outside 0..23"))

        val badFile = game.audio.copy(sounds = game.audio.sounds + SoundAssetModel(soundId("test.flac"), "audio/test.flac"))
        assertTrue(audioErrors(badFile).single().contains("must be one of"))
    }

    @Test
    fun scriptSoundsCompileToSoundHandles() {
        val content = RylweenCompiledGame.nextRylween.toCompiledGameBundle().content
        val slugs = content.handles.handles.associate { handle -> handle.ref to content.strings.value(handle.slugString) }
        val played =
            content.scripts.roots
                .flatMap { script -> script.instructions.allViewCommands() }
                .filterIsInstance<CompiledViewCommand.PlaySound>()
                .map { command -> slugs.getValue(command.sound) }
                .toSet()

        assertTrue("explore.chest_open" in played, "the dungeon chest plays its sound, found $played")
        assertTrue("town.crowd_cheer" in played)
    }
}

/** View commands anywhere in a script, including inside branches, loops, menus and text answers. */
private fun List<CompiledInstruction>.allViewCommands(): List<CompiledViewCommand> =
    flatMap { instruction ->
        instruction.operands.flatMap { operand ->
            when (operand) {
                is CompiledOperand.ViewCommandValue -> listOf(operand.value)
                is CompiledOperand.Instructions -> operand.instructions.allViewCommands()
                is CompiledOperand.SwitchCases -> operand.cases.flatMap { it.body.allViewCommands() }
                is CompiledOperand.MenuOptions -> operand.options.flatMap { it.body.allViewCommands() }
                is CompiledOperand.TextAnswer -> operand.answer.matchOps.allViewCommands() + operand.answer.missOps.allViewCommands()
                else -> emptyList()
            }
        }
    }
