/**
 * Terminal adapter for the Castle runtime session.
 *
 * [rylween.runtime.castle.CastleSession] owns pure state transitions; this file
 * renders prompts through `TerminalFrontend.kt`, dispatches child-session
 * adapters, and writes saves through `save/RuntimeSave.kt`.
 */
package rylween.frontend.terminal

import rylween.dsl.RuntimeMode
import rylween.dsl.ViewCommand
import rylween.dsl.image
import rylween.dsl.inputKey
import rylween.dsl.item
import rylween.dsl.lines
import rylween.dsl.text
import rylween.runtime.castle.CastleSession
import rylween.runtime.castle.CastleSessionInput
import rylween.runtime.castle.CastleSessionStep
import rylween.runtime.castle.navigationCommands
import rylween.runtime.castle.stairsCommands
import rylween.runtime.castle.toCastleSnapshot
import rylween.runtime.castle.treasureCommands
import rylween.runtime.commandBindings
import rylween.runtime.scriptRootForScene
import rylween.runtime.town.speakRuntimeContent
import rylween.save.RuntimeSaveOutcome
import rylween.save.RuntimeSavePort
import rylween.save.RuntimeSaveRequest

/** Drives a [CastleSession] against a line-oriented [TerminalPort]. */
class CastleSessionTerminalAdapter(
    private val terminal: TerminalPort,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(),
    private val savePort: RuntimeSavePort? = null,
    private val scriptBridge: ScriptBridge? = null,
) {
    fun run(initial: CastleSession): CastleSession = runUntilBlocked(initial).session

    fun runUntilBlocked(initial: CastleSession): TerminalAdapterResult<CastleSession> {
        var session = initial
        repeat(512) {
            session =
                when (val step = session.step) {
                    is CastleSessionStep.Messages -> {
                        step.image?.let { image ->
                            terminal.writeViewCommand(
                                ViewCommand.ShowImage(image),
                                renderer,
                                TerminalTextContext(runtime = session.runtime),
                            )
                        }
                        terminal.writeBlocks(step.blocks, renderer, TerminalTextContext(runtime = session.runtime))
                        session.apply(CastleSessionInput.Continue)
                    }
                    is CastleSessionStep.Save -> {
                        val outcome = saveRuntime(session)
                        terminal.writeBlocks(
                            listOf(if (outcome.saved) session.content.prompts.save.done else session.content.prompts.save.failed),
                            renderer,
                            TerminalTextContext(runtime = session.runtime),
                        )
                        session.completeSave(outcome.saved)
                    }
                    CastleSessionStep.RoomEntry -> session.enterRoom()
                    CastleSessionStep.Navigate -> {
                        writeNavigation(session)
                        terminal
                            .readChoice(
                                session.castleContent.keys
                                    .navigationCommands()
                                    .terminalChoices(),
                            ).sessionOrWait(session, { return it }) { session.apply(it) }
                    }
                    is CastleSessionStep.TreasureOffer -> {
                        val keys = session.castleContent.keys
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
                    CastleSessionStep.StairsMenu -> {
                        val keys = session.castleContent.keys
                        val commands = keys.stairsCommands()
                        terminal.writeBindingPrompt(
                            renderer,
                            session.castleContent.text.stairsMenu,
                            commands.commandBindings(),
                            TerminalTextContext(runtime = session.runtime),
                        )
                        terminal
                            .readChoice(commands.terminalChoices())
                            .sessionOrWait(session, { return it }) { session.apply(it) }
                    }
                    CastleSessionStep.StairGive -> {
                        val choices =
                            (1..5).map { digit ->
                                TerminalChoice(CastleSessionInput.StairItem(digit), inputKey(digit.digitToChar(), label = digit.toString()))
                            }
                        terminal.writeBindingPrompt(
                            renderer,
                            session.castleContent.text.giveMenu,
                            choices.map { it.binding },
                            TerminalTextContext(runtime = session.runtime),
                        )
                        terminal
                            .readChoice(choices)
                            .sessionOrWait(session, { return it }) { session.apply(it) }
                    }
                    is CastleSessionStep.Battle -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            { BattleSessionTerminalAdapter(terminal, renderer).runUntilBlocked(it) },
                            CastleSession::replaceBattleSession,
                        ) { return it }
                    }
                    is CastleSessionStep.Script -> {
                        val result = runCastleScript(session, step)
                        terminal.writeScriptEvents(result.events, renderer, result.runtime)
                        when (result.status) {
                            ScriptBridgeStatus.Finished -> session.finishScript(result.runtime)
                            ScriptBridgeStatus.Waiting -> {
                                val continuation =
                                    checkNotNull(result.continuation) {
                                        "waiting castle script did not return continuation"
                                    }
                                val battle =
                                    continuation.battleSession ?: return waiting(
                                        session.replaceScriptContinuation(continuation),
                                        result.rejection,
                                    )
                                val battleResult = BattleSessionTerminalAdapter(terminal, renderer).runUntilBlocked(battle)
                                val updated =
                                    session.replaceScriptContinuation(
                                        continuation.copy(
                                            runtime = battleResult.session.runtime,
                                            battleSession = battleResult.session,
                                        ),
                                    )
                                continueOrBlock(updated, battleResult.status) { return it }
                            }
                        }
                    }
                    is CastleSessionStep.PartyView -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            { PartyViewTerminalAdapter(terminal, renderer).runUntilBlocked(it, session.runtime) },
                            CastleSession::replacePartyViewSession,
                        ) { return it }
                    }
                    is CastleSessionStep.InventoryDrop -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            { InventoryDropTerminalAdapter(terminal, renderer).runUntilBlocked(it) },
                            CastleSession::replaceInventoryDropSession,
                        ) { return it }
                    }
                    is CastleSessionStep.Equipment -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            { EquipmentPromptTerminalAdapter(terminal, renderer).runUntilBlocked(it) },
                            CastleSession::replaceEquipmentSession,
                        ) { return it }
                    }
                    is CastleSessionStep.InventoryItemUse -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            { InventoryItemUseTerminalAdapter(terminal, renderer, scriptBridge = scriptBridge).runUntilBlocked(it) },
                            CastleSession::replaceInventoryItemUseSession,
                        ) { return it }
                    }
                    is CastleSessionStep.PriestSpell -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            {
                                PriestSpellTerminalAdapter(
                                    terminal = terminal,
                                    renderer = renderer,
                                    content = session.content,
                                    scriptBridge = scriptBridge,
                                ).runUntilBlocked(it)
                            },
                            CastleSession::replacePriestSpellSession,
                        ) { return it }
                    }
                    is CastleSessionStep.Finished -> return finished(session)
                }
        }
        return TerminalAdapterResult(session, TerminalRunStatus.StepLimitReached)
    }

    private fun runCastleScript(
        session: CastleSession,
        step: CastleSessionStep.Script,
    ): ScriptBridgeResult {
        val root = session.content.scriptRootForScene(step.scene.id)
        val invocation =
            ScriptBridgeInvocation(
                root = root,
                rootOps = step.scene.ops,
                continuation = step.continuation,
                resumeModeAfterBattle = RuntimeMode.Castle,
            )
        scriptBridge?.let { bridge -> return bridge.run(invocation) }
        return StatefulScriptBridge(
            terminal = terminal,
            speakContent = session.content.speakRuntimeContent(),
            battleContent = session.content,
            renderer = renderer,
        ).run(invocation)
    }

    private fun saveRuntime(session: CastleSession): RuntimeSaveOutcome {
        terminal.write(
            renderer.lines(session.content.prompts.save.saving, TerminalTextContext(runtime = session.runtime)),
        )
        val port = savePort ?: return RuntimeSaveOutcome(saved = false, error = "no save port configured")
        return runCatching {
            port.save(RuntimeSaveRequest(session.runtime, session.toCastleSnapshot()))
        }.getOrElse { error ->
            RuntimeSaveOutcome(saved = false, error = error.message)
        }
    }

    private fun writeNavigation(session: CastleSession) {
        val context = TerminalTextContext(runtime = session.runtime)
        session.castleContent.image?.let { image ->
            terminal.writeViewCommand(ViewCommand.ShowImage(image), renderer, context)
        }
        terminal.writeViewCommand(ViewCommand.StatusLine, renderer, context)
        session.navigationBlocks().forEach { block ->
            terminal.write(renderer.lines(block, context))
        }
    }
}
