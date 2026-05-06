/**
 * Terminal adapter for the Ydarg castle runtime session.
 *
 * [rylween.runtime.ydarg.YdargSession] owns pure state transitions; this file
 * renders prompts through `TerminalFrontend.kt`, dispatches child-session
 * adapters, and writes saves through `save/RuntimeSave.kt`.
 */
package rylween.frontend.terminal

import rylween.dsl.ViewCommand
import rylween.dsl.image
import rylween.dsl.inputKey
import rylween.dsl.item
import rylween.dsl.lines
import rylween.dsl.text
import rylween.runtime.commandBindings
import rylween.runtime.ydarg.YdargSession
import rylween.runtime.ydarg.YdargSessionInput
import rylween.runtime.ydarg.YdargSessionStep
import rylween.runtime.ydarg.navigationCommands
import rylween.runtime.ydarg.stairsCommands
import rylween.runtime.ydarg.toYdargSnapshot
import rylween.runtime.ydarg.treasureCommands
import rylween.save.RuntimeSaveOutcome
import rylween.save.RuntimeSavePort
import rylween.save.RuntimeSaveRequest

/** Drives a [YdargSession] against a line-oriented [TerminalPort]. */
class YdargSessionTerminalAdapter(
    private val terminal: TerminalPort,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(),
    private val savePort: RuntimeSavePort? = null,
) {
    fun run(initial: YdargSession): YdargSession = runUntilBlocked(initial).session

    fun runUntilBlocked(initial: YdargSession): TerminalAdapterResult<YdargSession> {
        var session = initial
        repeat(512) {
            session =
                when (val step = session.step) {
                    is YdargSessionStep.Messages -> {
                        step.image?.let { image ->
                            terminal.writeViewCommand(
                                ViewCommand.ShowImage(image),
                                renderer,
                                TerminalTextContext(runtime = session.runtime),
                            )
                        }
                        terminal.writeBlocks(step.blocks, renderer, TerminalTextContext(runtime = session.runtime))
                        session.apply(YdargSessionInput.Continue)
                    }
                    is YdargSessionStep.Save -> {
                        val outcome = saveRuntime(session)
                        terminal.writeBlocks(
                            listOf(if (outcome.saved) session.content.prompts.save.done else session.content.prompts.save.failed),
                            renderer,
                            TerminalTextContext(runtime = session.runtime),
                        )
                        session.completeSave(outcome.saved)
                    }
                    YdargSessionStep.RoomEntry -> session.enterRoom()
                    YdargSessionStep.Navigate -> {
                        writeNavigation(session)
                        terminal
                            .readChoice(
                                session.ydarg.keys
                                    .navigationCommands()
                                    .terminalChoices(),
                            ).sessionOrWait(session, { return it }) { session.apply(it) }
                    }
                    is YdargSessionStep.TreasureOffer -> {
                        val keys = session.ydarg.keys
                        val commands = keys.treasureCommands()
                        terminal.writeBindingPrompt(
                            renderer,
                            session.treasureOfferBlock(step.item),
                            commands.commandBindings(),
                            TerminalTextContext(runtime = session.runtime),
                        )
                        terminal
                            .readChoice(commands.terminalChoices())
                            .sessionOrWait(session, { return it }) { session.apply(it) }
                    }
                    YdargSessionStep.StairsMenu -> {
                        val keys = session.ydarg.keys
                        val commands = keys.stairsCommands()
                        terminal.writeBindingPrompt(
                            renderer,
                            session.ydarg.text.stairsMenu,
                            commands.commandBindings(),
                            TerminalTextContext(runtime = session.runtime),
                        )
                        terminal
                            .readChoice(commands.terminalChoices())
                            .sessionOrWait(session, { return it }) { session.apply(it) }
                    }
                    YdargSessionStep.StairGive -> {
                        val choices =
                            (1..5).map { digit ->
                                TerminalChoice(YdargSessionInput.StairItem(digit), inputKey(digit.digitToChar(), label = digit.toString()))
                            }
                        terminal.writeBindingPrompt(
                            renderer,
                            session.ydarg.text.giveMenu,
                            choices.map { it.binding },
                            TerminalTextContext(runtime = session.runtime),
                        )
                        terminal
                            .readChoice(choices)
                            .sessionOrWait(session, { return it }) { session.apply(it) }
                    }
                    is YdargSessionStep.Battle -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            { BattleSessionTerminalAdapter(terminal, renderer).runUntilBlocked(it) },
                            YdargSession::replaceBattleSession,
                        ) { return it }
                    }
                    is YdargSessionStep.PartyView -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            { PartyViewTerminalAdapter(terminal, renderer).runUntilBlocked(it, session.runtime) },
                            YdargSession::replacePartyViewSession,
                        ) { return it }
                    }
                    is YdargSessionStep.InventoryDrop -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            { InventoryDropTerminalAdapter(terminal, renderer).runUntilBlocked(it) },
                            YdargSession::replaceInventoryDropSession,
                        ) { return it }
                    }
                    is YdargSessionStep.Equipment -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            { EquipmentPromptTerminalAdapter(terminal, renderer).runUntilBlocked(it) },
                            YdargSession::replaceEquipmentSession,
                        ) { return it }
                    }
                    is YdargSessionStep.InventoryItemUse -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            { InventoryItemUseTerminalAdapter(terminal, renderer).runUntilBlocked(it) },
                            YdargSession::replaceInventoryItemUseSession,
                        ) { return it }
                    }
                    is YdargSessionStep.PriestSpell -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            {
                                PriestSpellTerminalAdapter(
                                    terminal = terminal,
                                    renderer = renderer,
                                    content = session.content,
                                ).runUntilBlocked(it)
                            },
                            YdargSession::replacePriestSpellSession,
                        ) { return it }
                    }
                    is YdargSessionStep.Finished -> return finished(session)
                }
        }
        return TerminalAdapterResult(session, TerminalRunStatus.StepLimitReached)
    }

    private fun saveRuntime(session: YdargSession): RuntimeSaveOutcome {
        terminal.write(
            renderer.lines(session.content.prompts.save.saving, TerminalTextContext(runtime = session.runtime)),
        )
        val port = savePort ?: return RuntimeSaveOutcome(saved = false, error = "no save port configured")
        return runCatching {
            port.save(RuntimeSaveRequest(session.runtime, session.toYdargSnapshot()))
        }.getOrElse { error ->
            RuntimeSaveOutcome(saved = false, error = error.message)
        }
    }

    private fun writeNavigation(session: YdargSession) {
        val context = TerminalTextContext(runtime = session.runtime)
        session.ydarg.image?.let { image ->
            terminal.writeViewCommand(ViewCommand.ShowImage(image), renderer, context)
        }
        terminal.writeViewCommand(ViewCommand.StatusLine, renderer, context)
        session.navigationBlocks().forEach { block ->
            terminal.write(renderer.lines(block, context))
        }
    }
}
