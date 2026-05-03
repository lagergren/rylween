// Trace-producing terminal runners for child sessions used by authored scripts.

package rylween.frontend.terminal

import rylween.dsl.*
import rylween.engine.*
import rylween.runtime.*

internal class RandomItemOfferSessionTraceRunner(
    private val terminal: TerminalPort,
    private val engine: TraceEnginePort,
) {
    fun runUntilBlocked(initial: RandomItemOfferSession): TerminalAdapterResult<RandomItemOfferSession> {
        var session = initial
        repeat(64) {
            session = when (val step = session.step) {
                is RandomItemOfferStep.Choice -> {
                    engine.trace(EngineTraceEvent.Screen(step.offerText))
                    engine.trace(EngineTraceEvent.Menu(step.prompt, listOf(step.buyKey, step.declineKey)))
                    val choices = listOf(
                        TerminalChoice(true, step.buy),
                        TerminalChoice(false, step.decline),
                    )
                    terminal.readChoice(choices)
                        .sessionOrWait(session, { return it }) {
                            session.apply(RandomItemOfferInput.Buy(it))
                        }
                }
                is RandomItemOfferStep.DropSlot -> {
                    val choices = session.party.inventorySlotChoices(
                        InventorySlotChoiceFilter.NonEmpty,
                        1..InventorySaveSlotMax,
                        'A',
                    )
                    engine.trace(
                        EngineTraceEvent.AskInventorySlot(
                            prompt = step.prompt,
                            firstKey = 'A',
                            slots = 1..InventorySaveSlotMax,
                            filter = InventorySlotChoiceFilter.NonEmpty,
                        ),
                    )
                    terminal.readKey(choices.map { it.first })
                        .sessionOrWait(session, { return it }) { key ->
                            session.apply(
                                RandomItemOfferInput.DropSlot(
                                    checkNotNull(choices.firstOrNull { it.first == key }).second,
                                ),
                            )
                        }
                }
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
            session = when (val step = session.step) {
                is RewardGrantStep.DropSlot -> {
                    val choices = session.party.inventorySlotChoices(
                        InventorySlotChoiceFilter.NonEmpty,
                        1..InventorySaveSlotMax,
                        'A',
                    )
                    engine.trace(
                        EngineTraceEvent.AskInventorySlot(
                            prompt = step.prompt,
                            firstKey = 'A',
                            slots = 1..InventorySaveSlotMax,
                            filter = InventorySlotChoiceFilter.NonEmpty,
                        ),
                    )
                    terminal.readKey(choices.map { it.first })
                        .sessionOrWait(session, { return it }) { key ->
                            session.apply(
                                RewardGrantInput.DropSlot(
                                    checkNotNull(choices.firstOrNull { it.first == key }).second,
                                ),
                            )
                        }
                }
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
            session = when (val step = session.step) {
                is SpeakStep.ChooseNpc -> {
                    val keys = step.npcs.indices.map { index -> '1' + index }
                    traceMenu(npcPrompt(session.content, step.npcs), keys)
                    terminal.readKey(keys)
                        .sessionOrWait(session, { return it }) { key ->
                            val npc = step.npcs[key - '1']
                            session.apply(SpeakInput.Npc(npc.id))
                        }
                }
                is SpeakStep.ScriptBranch -> {
                    val continuation = step.continuation ?: ScriptContinuation.start(session.runtime)
                    val result = StatefulScriptServiceRunner(
                        terminal = terminal,
                        engine = engine,
                        rootOps = step.ops,
                        continuation = continuation,
                        speakContent = session.content,
                        battleContent = battleContent,
                    ).run()
                    val nextSession = session.copy(runtime = result.runtime)
                    when (result.status) {
                        ScriptBridgeStatus.Finished ->
                            nextSession.copy(step = step.next)
                        ScriptBridgeStatus.Waiting ->
                            return waiting(
                                nextSession.copy(
                                    step = step.copy(
                                        continuation = checkNotNull(result.continuation) {
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
                    terminal.readChoice(choices)
                        .sessionOrWait(session, { return it }) { action ->
                            session.apply(SpeakInput.Main(action.topic.toMainChoice()))
                        }
                }
                is SpeakStep.QuestionMenu -> {
                    val choices = step.actions.map { action -> TerminalChoice(action, action.binding) }
                    traceMenu(questionPrompt(step.actions), step.actions.map { it.key })
                    terminal.readChoice(choices)
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

    private fun traceMenu(prompt: TextBlock, keys: List<Char>) {
        engine.trace(EngineTraceEvent.Menu(prompt, keys))
    }

    private fun npcPrompt(content: SpeakRuntimeContent, npcs: List<CharacterModel>): TextBlock =
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

    private fun ConversationMenuActionModel.promptLine(): String =
        bindingMenuLine(binding, label)

    private fun QuestionMenuActionModel.promptLine(): String =
        bindingMenuLine(binding, label)

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
