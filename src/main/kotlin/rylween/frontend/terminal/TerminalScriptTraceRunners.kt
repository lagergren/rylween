/**
 * Trace-producing terminal runners for child sessions used by authored scripts.
 *
 * `TerminalScriptBridge.kt` delegates nested reward, offer, and conversation
 * flows here so script execution can keep emitting [rylween.engine.EngineTraceEvent]
 * values without embedding terminal UI policy into the engine port.
 *
 * This file belongs to the authored interpreter/oracle surface. Compiled VM
 * hosts model the same boundaries through `CompiledVmChildRequest` instead.
 */
@file:rylween.runtime.AuthoredScriptOracleApi

package rylween.frontend.terminal

import rylween.dsl.CharacterModel
import rylween.dsl.ConversationMenuActionModel
import rylween.dsl.ConversationPromptTextModel
import rylween.dsl.ConversationTopic
import rylween.dsl.InventorySaveSlotMax
import rylween.dsl.InventorySlotChoiceFilter
import rylween.dsl.PartyState
import rylween.dsl.QuestionMenuActionModel
import rylween.dsl.RuntimeContent
import rylween.dsl.TextBlock
import rylween.dsl.combineTextBlocks
import rylween.dsl.inlineText
import rylween.engine.EngineTraceEvent
import rylween.engine.TraceEnginePort
import rylween.runtime.RandomItemOfferInput
import rylween.runtime.RewardGrantInput
import rylween.runtime.ScriptContinuation
import rylween.runtime.SpeakInput
import rylween.runtime.SpeakMainChoice
import rylween.runtime.reward.RandomItemOfferSession
import rylween.runtime.reward.RandomItemOfferStep
import rylween.runtime.reward.RewardGrantSession
import rylween.runtime.reward.RewardGrantStep
import rylween.runtime.scriptRootCatalog
import rylween.runtime.town.SpeakRuntimeContent
import rylween.runtime.town.SpeakSession
import rylween.runtime.town.SpeakStep

private inline fun <S> promptInventoryDropSlot(
    party: PartyState,
    prompt: TextBlock,
    terminal: TerminalPort,
    engine: TraceEnginePort,
    session: S,
    onWait: (TerminalAdapterResult<S>) -> Nothing,
    onChosen: (Int) -> S,
): S {
    val choices =
        party.inventorySlotChoices(
            InventorySlotChoiceFilter.NonEmpty,
            1..InventorySaveSlotMax,
            'A',
        )
    engine.trace(
        EngineTraceEvent.AskInventorySlot(
            prompt = prompt,
            firstKey = 'A',
            slots = 1..InventorySaveSlotMax,
            filter = InventorySlotChoiceFilter.NonEmpty,
        ),
    )
    return terminal
        .readKey(choices.map { it.first })
        .sessionOrWait(session, onWait) { key ->
            onChosen(checkNotNull(choices.firstOrNull { it.first == key }).second)
        }
}

internal class RandomItemOfferSessionTraceRunner(
    private val terminal: TerminalPort,
    private val engine: TraceEnginePort,
) {
    fun runUntilBlocked(initial: RandomItemOfferSession): TerminalAdapterResult<RandomItemOfferSession> {
        var session = initial
        repeat(64) {
            session =
                when (val step = session.step) {
                    is RandomItemOfferStep.Choice -> {
                        engine.trace(EngineTraceEvent.Screen(step.offerText))
                        engine.trace(EngineTraceEvent.Menu(step.prompt, listOf(step.buyKey, step.declineKey)))
                        val choices =
                            listOf(
                                TerminalChoice(true, step.buy),
                                TerminalChoice(false, step.decline),
                            )
                        terminal
                            .readChoice(choices)
                            .sessionOrWait(session, { return it }) {
                                session.apply(RandomItemOfferInput.Buy(it))
                            }
                    }
                    is RandomItemOfferStep.DropSlot ->
                        promptInventoryDropSlot(
                            party = session.party,
                            prompt = step.prompt,
                            terminal = terminal,
                            engine = engine,
                            session = session,
                            onWait = { return it },
                        ) { slot -> session.apply(RandomItemOfferInput.DropSlot(slot)) }
                    is RandomItemOfferStep.Message -> {
                        engine.trace(EngineTraceEvent.Screen(step.text))
                        session.apply(RandomItemOfferInput.Continue)
                    }
                    is RandomItemOfferStep.Finished -> return finished(session)
                }
        }
        return TerminalAdapterResult(session, TerminalRunStatus.StepLimitReached)
    }
}

internal class RewardGrantSessionTraceRunner(
    private val terminal: TerminalPort,
    private val engine: TraceEnginePort,
) {
    fun runUntilBlocked(initial: RewardGrantSession): TerminalAdapterResult<RewardGrantSession> {
        var session = initial
        repeat(128) {
            session =
                when (val step = session.step) {
                    is RewardGrantStep.DropSlot ->
                        promptInventoryDropSlot(
                            party = session.party,
                            prompt = step.prompt,
                            terminal = terminal,
                            engine = engine,
                            session = session,
                            onWait = { return it },
                        ) { slot -> session.apply(RewardGrantInput.DropSlot(slot)) }
                    is RewardGrantStep.Message -> {
                        engine.trace(EngineTraceEvent.Screen(step.text))
                        session.apply(RewardGrantInput.Continue)
                    }
                    is RewardGrantStep.Finished -> return finished(session)
                }
        }
        return TerminalAdapterResult(session, TerminalRunStatus.StepLimitReached)
    }
}

internal class SpeakSessionTraceRunner(
    private val terminal: TerminalPort,
    private val engine: TraceEnginePort,
    private val battleContent: RuntimeContent = RuntimeContent(),
) {
    fun runUntilBlocked(initial: SpeakSession): TerminalAdapterResult<SpeakSession> {
        var session = initial
        repeat(128) {
            session =
                when (val step = session.step) {
                    is SpeakStep.ChooseNpc -> {
                        val keys = step.npcs.indices.map { index -> '1' + index }
                        traceMenu(npcPrompt(session.content, step.npcs), keys)
                        terminal
                            .readKey(keys)
                            .sessionOrWait(session, { return it }) { key ->
                                val npc = step.npcs[key - '1']
                                session.apply(SpeakInput.Npc(npc.id))
                            }
                    }
                    is SpeakStep.ScriptBranch -> {
                        val root = battleContent.scriptRootCatalog().rootFor(step.root)
                        val continuation = step.continuation ?: ScriptContinuation.start(session.runtime, root)
                        val result =
                            StatefulScriptServiceRunner(
                                terminal = terminal,
                                engine = engine,
                                rootOps = step.ops,
                                continuation = continuation,
                                speakContent = session.content,
                                battleContent = battleContent,
                                scriptRoot = root,
                            ).run()
                        val nextSession = session.copy(runtime = result.runtime)
                        when (result.status) {
                            ScriptBridgeStatus.Finished ->
                                nextSession.copy(step = step.next)
                            ScriptBridgeStatus.Waiting ->
                                return waiting(
                                    nextSession.copy(
                                        step =
                                            step.copy(
                                                continuation =
                                                    checkNotNull(result.continuation) {
                                                        "waiting dialogue branch did not return continuation"
                                                    },
                                            ),
                                    ),
                                    result.rejection,
                                )
                        }
                    }
                    is SpeakStep.MainMenu -> {
                        val choices = step.actions.map { action -> TerminalChoice(action, action.binding) }
                        traceMenu(mainPrompt(session.content, step.npc, step.actions), step.actions.map { it.key })
                        terminal
                            .readChoice(choices)
                            .sessionOrWait(session, { return it }) { action ->
                                session.apply(SpeakInput.Main(action.topic.toMainChoice()))
                            }
                    }
                    is SpeakStep.QuestionMenu -> {
                        val choices = step.actions.map { action -> TerminalChoice(action, action.binding) }
                        traceMenu(questionPrompt(step.actions), step.actions.map { it.key })
                        terminal
                            .readChoice(choices)
                            .sessionOrWait(session, { return it }) { action ->
                                session.apply(SpeakInput.Question(action.topic))
                            }
                    }
                    is SpeakStep.Message -> {
                        step.blocks.forEach { block -> engine.trace(EngineTraceEvent.Screen(block)) }
                        session.apply(SpeakInput.Continue)
                    }
                    is SpeakStep.Finished -> return finished(session)
                }
        }
        return TerminalAdapterResult(session, TerminalRunStatus.StepLimitReached)
    }

    private fun traceMenu(
        prompt: TextBlock,
        keys: List<Char>,
    ) {
        engine.trace(EngineTraceEvent.Menu(prompt, keys))
    }

    private fun npcPrompt(
        content: SpeakRuntimeContent,
        npcs: List<CharacterModel>,
    ): TextBlock =
        combineTextBlocks(
            content.conversationPrompts().chooseNpc,
            inlineText(*npcs.mapIndexed { index, npc -> "${index + 1}. ${npc.name}, ${npc.job.slug}" }.toTypedArray()),
        )

    private fun mainPrompt(
        content: SpeakRuntimeContent,
        npc: CharacterModel,
        actions: List<ConversationMenuActionModel>,
    ): TextBlock =
        combineTextBlocks(
            content.conversationPrompts().renderMainMenu(npc.name),
            inlineText(*actions.map { action -> action.promptLine() }.toTypedArray()),
        )

    private fun questionPrompt(actions: List<QuestionMenuActionModel>): TextBlock =
        inlineText(*actions.map { action -> action.promptLine() }.toTypedArray())

    private fun ConversationMenuActionModel.promptLine(): String = bindingMenuLine(binding, label)

    private fun QuestionMenuActionModel.promptLine(): String = bindingMenuLine(binding, label)

    private fun SpeakRuntimeContent.conversationPrompts(): ConversationPromptTextModel =
        checkNotNull(speak.standardConversation) {
            "standard conversation system is not configured"
        }.prompts

    private fun ConversationTopic.toMainChoice(): SpeakMainChoice =
        when (this) {
            ConversationTopic.Chitchat -> SpeakMainChoice.Chitchat
            ConversationTopic.Question -> SpeakMainChoice.Question
            ConversationTopic.Rumour -> SpeakMainChoice.Rumour
            ConversationTopic.Bye -> SpeakMainChoice.Bye
        }
}
