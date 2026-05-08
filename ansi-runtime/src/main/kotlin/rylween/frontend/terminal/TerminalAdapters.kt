/**
 * Line-oriented terminal adapters over backend-neutral runtime sessions.
 *
 * Display and input policy belongs here so core runtime sessions can be shared
 * by ANSI, bitmap, server, tests, and future compiled-VM hosts.
 */

package rylween.frontend.terminal

import rylween.dsl.BattleOpeningKeysModel
import rylween.dsl.BuildingServiceModel
import rylween.dsl.CharacterClassOptionModel
import rylween.dsl.CharacterCreationPromptsModel
import rylween.dsl.CharacterRaceOptionModel
import rylween.dsl.DslDefaultBuildings
import rylween.dsl.Gender
import rylween.dsl.GridPoint
import rylween.dsl.InputBindingModel
import rylween.dsl.InventorySaveSlotMax
import rylween.dsl.InventorySlotChoiceFilter
import rylween.dsl.JournalEntryState
import rylween.dsl.MemberChoiceFilter
import rylween.dsl.MissionModel
import rylween.dsl.PartyPromptsModel
import rylween.dsl.PartyState
import rylween.dsl.PcClass
import rylween.dsl.Race
import rylween.dsl.RuntimeMode
import rylween.dsl.ShopPurchaseModel
import rylween.dsl.TextBlock
import rylween.dsl.TownKind
import rylween.dsl.ViewCommand
import rylween.dsl.WorldPosition
import rylween.dsl.combineTextBlocks
import rylween.dsl.inlineText
import rylween.dsl.inputKey
import rylween.dsl.keyAction
import rylween.dsl.matches
import rylween.engine.EngineTraceEvent
import rylween.engine.TraceEnginePort
import rylween.runtime.CharacterCreationInput
import rylween.runtime.EquipmentPromptInput
import rylween.runtime.EquipmentPromptSession
import rylween.runtime.EquipmentPromptStep
import rylween.runtime.InventoryItemUseInput
import rylween.runtime.PriestSpellInput
import rylween.runtime.RuntimeSessionState
import rylween.runtime.ScriptContinuation
import rylween.runtime.ScriptRootRef
import rylween.runtime.ShopMainChoice
import rylween.runtime.TownLeaveEquipmentInput
import rylween.runtime.TownSessionInput
import rylween.runtime.battle.BattleOpeningChoice
import rylween.runtime.battle.BattleSession
import rylween.runtime.battle.BattleSessionInput
import rylween.runtime.battle.BattleSessionStep
import rylween.runtime.commandBindings
import rylween.runtime.dungeon.DungeonSession
import rylween.runtime.dungeon.DungeonSessionInput
import rylween.runtime.dungeon.DungeonSessionStep
import rylween.runtime.dungeon.exploreCommands
import rylween.runtime.dungeon.toDungeonSnapshot
import rylween.runtime.party.InventoryDropInput
import rylween.runtime.party.InventoryDropSession
import rylween.runtime.party.InventoryDropStep
import rylween.runtime.party.InventoryItemUseSession
import rylween.runtime.party.InventoryItemUseStep
import rylween.runtime.party.PartyJournalInput
import rylween.runtime.party.PartyJournalSession
import rylween.runtime.party.PartyJournalStep
import rylween.runtime.party.PartyViewInput
import rylween.runtime.party.PartyViewModel
import rylween.runtime.party.PartyViewSession
import rylween.runtime.party.PartyViewStep
import rylween.runtime.party.PriestSpellSession
import rylween.runtime.party.PriestSpellStep
import rylween.runtime.scriptRootForDungeonEvent
import rylween.runtime.town.CharacterCreationSession
import rylween.runtime.town.CharacterCreationStep
import rylween.runtime.town.ShopSellQuoteStatus
import rylween.runtime.town.ShopServiceSession
import rylween.runtime.town.ShopServiceStep
import rylween.runtime.town.ShopTransactionStatus
import rylween.runtime.town.TownLeaveEquipmentSession
import rylween.runtime.town.TownLeaveEquipmentStep
import rylween.runtime.town.TownSession
import rylween.runtime.town.TownSessionStep
import rylween.runtime.wilderness.CampSession
import rylween.runtime.wilderness.CampSessionInput
import rylween.runtime.wilderness.CampSessionStep
import rylween.runtime.wilderness.HuntSession
import rylween.runtime.wilderness.HuntSessionInput
import rylween.runtime.wilderness.HuntSessionStep
import rylween.runtime.wilderness.MissionPostDungeonContinuation
import rylween.runtime.wilderness.WildernessSession
import rylween.runtime.wilderness.WildernessSessionResultStatus
import rylween.runtime.wilderness.WildernessSessionStep
import rylween.runtime.wilderness.caveCommands
import rylween.runtime.wilderness.hutService
import rylween.runtime.wilderness.outOfMovesCommands
import rylween.runtime.wilderness.quitConfirmCommands
import rylween.save.RuntimeSaveOutcome
import rylween.save.RuntimeSavePort
import rylween.save.RuntimeSaveRequest

private fun StatefulScriptResult.toBridgeResult(
    eventStart: Int,
    engine: TraceEnginePort,
): ScriptBridgeResult =
    ScriptBridgeResult(
        runtime = runtime,
        status = status,
        events = engine.events.drop(unwrittenEventStart.coerceAtLeast(eventStart)),
        continuation = continuation,
        rejection = rejection,
        dungeonHandoff = dungeonHandoff,
    )

private fun TerminalPort.writeKeyMenu(
    renderer: TerminalTextRenderer,
    block: TextBlock,
    keys: List<Char>,
    runtime: RuntimeSessionState? = null,
) {
    val context = runtime?.let { TerminalTextContext(runtime = it) } ?: TerminalTextContext()
    writeKeyPrompt(renderer, block, keys, context)
}

private fun TerminalPort.writeBindingMenu(
    renderer: TerminalTextRenderer,
    block: TextBlock,
    bindings: List<InputBindingModel>,
    context: TerminalTextContext = TerminalTextContext(),
) {
    writeBindingPrompt(renderer, block, bindings, context)
}

/** Terminal adapter for [rylween.runtime.wilderness.CampSession]. */
class CampSessionTerminalAdapter(
    private val terminal: TerminalPort,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(),
) {
    fun run(initial: CampSession): CampSession = runUntilBlocked(initial).session

    fun runUntilBlocked(initial: CampSession): TerminalAdapterResult<CampSession> {
        var session = initial
        repeat(64) {
            session =
                when (val step = session.step) {
                    is CampSessionStep.GuardChoice -> {
                        terminal.writeKeyMenu(renderer, step.prompt, listOf('Y', 'N'))
                        val choices = listOf(TerminalChoice(true, inputKey("Y")), TerminalChoice(false, inputKey("N")))
                        terminal
                            .readChoice(choices)
                            .sessionOrWait(session, { return it }) { session.apply(CampSessionInput.GuardAnswer(it)) }
                    }
                    is CampSessionStep.GuardMember -> {
                        val keys =
                            session.party.members.indices
                                .map { (it + 1).digitToChar() }
                        terminal.writeKeyMenu(renderer, step.prompt, keys)
                        terminal
                            .readMemberNumber(
                                session.party.members.indices
                                    .map { it + 1 },
                            ).sessionOrWait(session, { return it }) { session.apply(CampSessionInput.GuardMember(it)) }
                    }
                    is CampSessionStep.Messages -> {
                        terminal.writeBlocks(step.blocks, renderer, TerminalTextContext(runtime = session.runtime))
                        session.apply(CampSessionInput.Continue)
                    }
                    is CampSessionStep.Ambush -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            { BattleSessionTerminalAdapter(terminal, renderer).runUntilBlocked(it) },
                            CampSession::replaceAmbushSession,
                        ) { return it }
                    }
                    is CampSessionStep.Finished -> return finished(session)
                }
        }
        return TerminalAdapterResult(session, TerminalRunStatus.StepLimitReached)
    }
}

/** Terminal adapter for [rylween.runtime.wilderness.HuntSession]. */
class HuntSessionTerminalAdapter(
    private val terminal: TerminalPort,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(),
) {
    fun run(initial: HuntSession): HuntSession = runUntilBlocked(initial).session

    fun runUntilBlocked(initial: HuntSession): TerminalAdapterResult<HuntSession> {
        var session = initial
        repeat(64) {
            session =
                when (val step = session.step) {
                    is HuntSessionStep.ChooseMember -> {
                        val choices = session.memberChoices()
                        terminal.writeKeyMenu(renderer, step.prompt, choices.map { it.digitToChar() }, session.runtime)
                        terminal
                            .readMemberNumber(choices)
                            .sessionOrWait(session, { return it }) { session.apply(HuntSessionInput.Member(it)) }
                    }
                    is HuntSessionStep.Messages -> {
                        val context =
                            TerminalTextContext(
                                runtime = session.runtime,
                                huntMemberNumber = step.memberNumber,
                                huntFoodGained = step.foodGained,
                            )
                        terminal.writeBlocks(step.blocks, renderer, context)
                        session.apply(HuntSessionInput.Continue)
                    }
                    is HuntSessionStep.Finished -> return finished(session)
                }
        }
        return TerminalAdapterResult(session, TerminalRunStatus.StepLimitReached)
    }
}

/** Terminal adapter for [rylween.runtime.town.CharacterCreationSession]. */
class CharacterCreationTerminalAdapter(
    private val terminal: TerminalPort,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(),
) {
    fun run(initial: CharacterCreationSession): CharacterCreationSession = runUntilBlocked(initial).session

    fun runUntilBlocked(initial: CharacterCreationSession): TerminalAdapterResult<CharacterCreationSession> {
        var session = initial
        repeat(256) {
            val prompts = session.content.prompts.characterCreation
            session =
                when (val step = session.step) {
                    CharacterCreationStep.PartyName -> {
                        terminal.write(renderer.lines(prompts.header) + renderer.lines(prompts.partyName))
                        terminal
                            .readTextLine()
                            .toTerminalInput()
                            .sessionOrWait(session, { return it }) { name ->
                                session.apply(CharacterCreationInput.PartyName(name))
                            }
                    }
                    is CharacterCreationStep.Stats -> {
                        terminal.write(
                            renderer.lines(
                                prompts.renderStatsPage(
                                    memberNumber = step.memberNumber,
                                    weaponSkill = step.stats.weaponSkill,
                                    health = step.stats.health,
                                    wisdom = step.stats.wisdom,
                                    charisma = step.stats.charisma,
                                ),
                            ) + renderer.lines(prompts.reroll),
                        )
                        terminal
                            .readReroll()
                            .sessionOrWait(session, { return it }) { reroll ->
                                session.apply(CharacterCreationInput.RerollStats(reroll))
                            }
                    }
                    is CharacterCreationStep.MemberName -> {
                        terminal.write(renderer.lines(prompts.renderMemberName(step.memberNumber)))
                        terminal
                            .readTextLine()
                            .toTerminalInput()
                            .sessionOrWait(session, { return it }) { name ->
                                session.apply(CharacterCreationInput.MemberName(name))
                            }
                    }
                    is CharacterCreationStep.GenderPrompt -> {
                        val choices = genderChoices(prompts)
                        terminal.writePromptWithChoices(prompts.renderGender(step.memberName), choices)
                        terminal
                            .readChoice(choices)
                            .sessionOrWait(session, { return it }) { gender ->
                                session.apply(CharacterCreationInput.GenderChoice(gender))
                            }
                    }
                    is CharacterCreationStep.RacePrompt -> {
                        val choices = raceChoices(step.options)
                        terminal.writePromptWithChoices(prompts.renderRace(step.memberName), choices)
                        terminal
                            .readChoice(choices)
                            .sessionOrWait(session, { return it }) { race ->
                                session.apply(CharacterCreationInput.RaceChoice(race))
                            }
                    }
                    is CharacterCreationStep.ClassPrompt -> {
                        val choices = classChoices(step.options)
                        terminal.writePromptWithChoices(prompts.classMenu, choices)
                        terminal
                            .readChoice(choices)
                            .sessionOrWait(session, { return it }) { pcClass ->
                                session.apply(CharacterCreationInput.ClassChoice(pcClass))
                            }
                    }
                    is CharacterCreationStep.ClassRejected -> {
                        terminal.write(renderer.lines(step.text))
                        session.apply(CharacterCreationInput.Continue)
                    }
                    is CharacterCreationStep.Finished -> {
                        terminal.write(renderer.lines(prompts.saving, TerminalTextContext(runtime = step.runtime)))
                        return finished(session)
                    }
                    CharacterCreationStep.Aborted -> return finished(session)
                }
        }
        return TerminalAdapterResult(session, TerminalRunStatus.StepLimitReached)
    }

    private fun TerminalPort.writePromptWithChoices(
        prompt: TextBlock,
        choices: List<TerminalChoice<*>>,
    ) {
        write(renderer.lines(prompt) + choices.map { choice -> choice.binding.label })
    }

    private fun String?.toTerminalInput(): TerminalInput<String> =
        when (this) {
            null -> TerminalInput.Unavailable
            else -> TerminalInput.Value(this)
        }

    private fun TerminalPort.readReroll(): TerminalInput<Boolean> {
        val raw = readLine() ?: return TerminalInput.Unavailable
        return when (raw.trim().uppercase()) {
            "", "N" -> TerminalInput.Value(false)
            "Y" -> TerminalInput.Value(true)
            else -> TerminalInput.Rejected(TerminalInputRejection(raw, listOf("Y", "N", "RETURN")))
        }
    }

    private fun genderChoices(prompts: CharacterCreationPromptsModel): List<TerminalChoice<Gender>> =
        listOf(
            TerminalChoice(Gender.Male, numberedBinding(1, prompts.genderMaleLabel)),
            TerminalChoice(Gender.Female, numberedBinding(2, prompts.genderFemaleLabel)),
        )

    private fun raceChoices(options: List<CharacterRaceOptionModel>): List<TerminalChoice<Race>> =
        options.mapIndexed { index, option ->
            TerminalChoice(option.race, numberedBinding(index + 1, option.label))
        }

    private fun classChoices(options: List<CharacterClassOptionModel>): List<TerminalChoice<PcClass>> =
        options.mapIndexed { index, option ->
            TerminalChoice(option.klass, numberedBinding(index + 1, option.label))
        }

    private fun numberedBinding(
        number: Int,
        label: String,
    ): InputBindingModel = inputKey(number.digitToChar(), label = "$number. $label")
}

/** Terminal adapter for reusable equipment prompt sessions. */
class EquipmentPromptTerminalAdapter(
    private val terminal: TerminalPort,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(),
) {
    fun run(initial: EquipmentPromptSession): EquipmentPromptSession = runUntilBlocked(initial).session

    fun runUntilBlocked(initial: EquipmentPromptSession): TerminalAdapterResult<EquipmentPromptSession> {
        var session = initial
        repeat(64) {
            session =
                when (val step = session.step) {
                    is EquipmentPromptStep.ChooseInventorySlot ->
                        readInventorySlot(session.party, step.prompt, step.filter)
                            .sessionOrWait(session, { return it }) {
                                session.apply(EquipmentPromptInput.InventorySlot(it))
                            }
                    is EquipmentPromptStep.ChooseMember ->
                        readMember(session.party, step.prompt, step.filter)
                            .sessionOrWait(session, { return it }) { session.apply(EquipmentPromptInput.Member(it)) }
                    is EquipmentPromptStep.Finished -> return finished(session)
                }
        }
        return TerminalAdapterResult(session, TerminalRunStatus.StepLimitReached)
    }

    private fun readInventorySlot(
        party: PartyState,
        prompt: TextBlock,
        filter: InventorySlotChoiceFilter,
    ): TerminalInput<Int> {
        val keys = party.inventoryKeys(filter)
        terminal.writeKeyMenu(renderer, prompt, keys)
        return terminal.readKey(keys).map { it - 'A' + 1 }
    }

    private fun readMember(
        party: PartyState,
        prompt: TextBlock,
        filter: MemberChoiceFilter,
    ): TerminalInput<Int> {
        val choices = party.memberChoices(filter)
        terminal.writeKeyMenu(renderer, prompt, choices.map { it.digitToChar() })
        return terminal.readMemberNumber(choices)
    }
}

/** Terminal adapter for [rylween.runtime.party.InventoryItemUseSession]. */
class InventoryItemUseTerminalAdapter(
    private val terminal: TerminalPort,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(),
    private val scriptBridge: ScriptBridge = MissingScriptBridge("InventoryItemUseTerminalAdapter"),
) {
    fun run(initial: InventoryItemUseSession): InventoryItemUseSession = runUntilBlocked(initial).session

    fun runUntilBlocked(initial: InventoryItemUseSession): TerminalAdapterResult<InventoryItemUseSession> {
        var session = initial
        repeat(128) {
            session =
                when (val step = session.step) {
                    is InventoryItemUseStep.ChooseItem -> {
                        terminal.writeKeyMenu(renderer, step.prompt, step.choices.map { it.key }, session.runtime)
                        terminal
                            .readKey(step.choices.map { it.key })
                            .sessionOrWait(session, { return it }) { key ->
                                val slot = checkNotNull(step.choices.firstOrNull { it.key == key }).slot
                                session.apply(InventoryItemUseInput.InventorySlot(slot))
                            }
                    }
                    is InventoryItemUseStep.Equipment -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            { EquipmentPromptTerminalAdapter(terminal, renderer).runUntilBlocked(it) },
                            InventoryItemUseSession::replaceEquipmentSession,
                        ) { return it }
                    }
                    is InventoryItemUseStep.Script -> {
                        val root = ScriptRootRef.itemUse(step.use.id)
                        val result = runItemUseScript(step, root)
                        terminal.writeScriptEvents(result.events, renderer, result.runtime)
                        when (result.status) {
                            ScriptBridgeStatus.Finished ->
                                session.finishScript(result.runtime)
                            ScriptBridgeStatus.Waiting ->
                                return waiting(
                                    session.replaceScriptContinuation(
                                        checkNotNull(result.continuation) {
                                            "waiting inventory item-use script did not return continuation"
                                        },
                                    ),
                                    result.rejection,
                                )
                        }
                    }
                    is InventoryItemUseStep.Message -> {
                        terminal.write(renderer.lines(step.text, TerminalTextContext(runtime = session.runtime)))
                        session.apply(InventoryItemUseInput.Continue)
                    }
                    is InventoryItemUseStep.Finished -> return finished(session)
                }
        }
        return TerminalAdapterResult(session, TerminalRunStatus.StepLimitReached)
    }

    private fun runItemUseScript(
        step: InventoryItemUseStep.Script,
        root: ScriptRootRef,
    ): ScriptBridgeResult =
        scriptBridge.run(
            ScriptBridgeInvocation(
                root = root,
                rootOps = step.use.ops,
                continuation = step.continuation,
            ),
        )
}

/** Terminal adapter for [rylween.runtime.party.PartyViewSession]. */
class PartyViewTerminalAdapter(
    private val terminal: TerminalPort,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(),
) {
    fun runUntilBlocked(
        initial: PartyViewSession,
        runtime: RuntimeSessionState? = null,
    ): TerminalAdapterResult<PartyViewSession> {
        var session = initial
        repeat(16) {
            session =
                when (val step = session.step) {
                    PartyViewStep.ChooseMember -> {
                        terminal.write(partyOverviewLines(session.viewModel()))
                        terminal.write(renderer.lines(session.prompts.viewWho, TerminalTextContext(runtime = runtime)))
                        terminal
                            .readChoice(partyViewChoices(session.party))
                            .sessionOrWait(session, { return it }) { session.apply(it) }
                    }
                    is PartyViewStep.MemberDetails -> {
                        terminal.write(memberDetailLines(session.viewModel(), step.memberNumbers))
                        session.apply(PartyViewInput.Continue)
                    }
                    is PartyViewStep.Finished -> return finished(session)
                }
        }
        return TerminalAdapterResult(session, TerminalRunStatus.StepLimitReached)
    }
}

/** Terminal adapter for [rylween.runtime.party.PartyJournalSession]. */
class PartyJournalTerminalAdapter(
    private val terminal: TerminalPort,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(),
) {
    fun runUntilBlocked(
        initial: PartyJournalSession,
        runtime: RuntimeSessionState? = null,
    ): TerminalAdapterResult<PartyJournalSession> {
        var session = initial
        repeat(4) {
            session =
                when (val step = session.step) {
                    is PartyJournalStep.Show -> {
                        terminal.write(journalLines(session.prompts, step.entries, renderer, runtime))
                        terminal.readReturn()
                            ?: return waiting(session)
                        session.apply(PartyJournalInput.Continue)
                    }
                    is PartyJournalStep.Finished -> return finished(session)
                }
        }
        return TerminalAdapterResult(session, TerminalRunStatus.StepLimitReached)
    }
}

/** Terminal adapter for [rylween.runtime.party.InventoryDropSession]. */
class InventoryDropTerminalAdapter(
    private val terminal: TerminalPort,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(),
) {
    fun runUntilBlocked(initial: InventoryDropSession): TerminalAdapterResult<InventoryDropSession> {
        var session = initial
        repeat(16) {
            session =
                when (val step = session.step) {
                    InventoryDropStep.ChooseSlot -> {
                        terminal.write(inventoryLines(session.viewModel()))
                        terminal.write(renderer.lines(session.prompts.dropPrompt))
                        terminal
                            .readChoice(inventoryDropChoices())
                            .sessionOrWait(session, { return it }) { session.apply(it) }
                    }
                    is InventoryDropStep.Messages -> {
                        terminal.writeBlocks(step.blocks, renderer, TerminalTextContext())
                        session.apply(InventoryDropInput.Continue)
                    }
                    is InventoryDropStep.Finished -> return finished(session)
                }
        }
        return TerminalAdapterResult(session, TerminalRunStatus.StepLimitReached)
    }
}

private fun partyViewChoices(party: PartyState): List<TerminalChoice<PartyViewInput>> =
    party.members.indices.map { index ->
        val key = (index + 1).digitToChar()
        TerminalChoice(PartyViewInput.Member(index + 1), inputKey(key, label = key.toString()))
    } +
        listOf(
            TerminalChoice(PartyViewInput.Quit, inputKey("Q")),
            TerminalChoice(PartyViewInput.All, inputKey("A")),
        )

private fun inventoryDropChoices(): List<TerminalChoice<InventoryDropInput>> =
    (1..InventorySaveSlotMax).map { slot ->
        val key = 'A' + slot - 1
        TerminalChoice(InventoryDropInput.Slot(slot), inputKey(key, label = key.toString()))
    } + TerminalChoice(InventoryDropInput.Abort, inputKey("Q"))

private fun partyOverviewLines(model: PartyViewModel): List<String> =
    buildList {
        add("Torches: ${model.resources.torches}  Rations: ${model.resources.food}  Gold: ${model.resources.gold}")
        if (model.specials.isNotEmpty()) {
            addAll(model.specials.map { special -> "Special: ${special.itemName}" })
        }
        add("")
        addAll(inventoryLines(model))
        add("")
    }

private fun inventoryLines(model: PartyViewModel): List<String> =
    model.inventory.chunked(2).map { pair ->
        pair.joinToString("  ") { slot ->
            "${slot.key}. ${slot.itemName.take(12).padEnd(12)}"
        }
    }

private fun memberDetailLines(
    model: PartyViewModel,
    memberNumbers: List<Int>,
): List<String> =
    memberNumbers.flatMap { number ->
        val view = model.members.firstOrNull { it.number == number } ?: return@flatMap emptyList()
        val member = view.member
        listOf(
            "${view.number}. ${member.name}",
            "${member.race.name} ${member.pcClass.name}",
            "Skill ${member.weaponSkill}  HP ${member.hitPoints.current}/${member.hitPoints.max}",
            "Health ${member.health}  Wisdom ${member.wisdom}",
            "Charisma ${member.charisma}  Magic ${member.mana.current}/${member.mana.max}",
            "Level ${member.level}  XP ${member.experience}",
            "Weapon: ${view.weaponName}",
            "Armor: ${view.armorName}",
            "Status: ${if (member.dead) "Dead" else "Alive"}",
            "",
        )
    }

private fun journalLines(
    prompts: PartyPromptsModel,
    entries: List<JournalEntryState>,
    renderer: TerminalTextRenderer,
    runtime: RuntimeSessionState?,
): List<String> {
    val context = TerminalTextContext(runtime = runtime)
    return renderer.lines(prompts.journalHeader, context) +
        entries.flatMap { entry -> journalEntryLines(entry, renderer, context) }
}

private fun journalEntryLines(
    entry: JournalEntryState,
    renderer: TerminalTextRenderer,
    context: TerminalTextContext,
): List<String> =
    when (entry) {
        is JournalEntryState.Line -> listOf("- ${entry.text}")
        is JournalEntryState.Text ->
            renderer.lines(TextBlock.Ref(entry.id), context).mapIndexed { index, line ->
                if (index == 0) "- $line" else "  $line"
            }
    }

/** Terminal adapter for non-combat priest spell selection. */
class PriestSpellTerminalAdapter(
    private val terminal: TerminalPort,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(),
    private val scriptBridge: ScriptBridge = MissingScriptBridge("PriestSpellTerminalAdapter"),
) {
    fun runUntilBlocked(initial: PriestSpellSession): TerminalAdapterResult<PriestSpellSession> {
        var session = initial
        repeat(128) {
            session =
                when (val step = session.step) {
                    is PriestSpellStep.ChooseCaster -> {
                        terminal.writeKeyMenu(renderer, step.prompt, step.casterKeys, session.runtime)
                        terminal
                            .readMemberNumber(step.casterKeys.map { it.digitToInt() })
                            .sessionOrWait(session, { return it }) { session.apply(PriestSpellInput.Caster(it)) }
                    }
                    is PriestSpellStep.ChooseSpell -> {
                        terminal.writeKeyMenu(renderer, step.prompt, step.spellKeys, session.runtime)
                        terminal
                            .readKey(step.spellKeys)
                            .sessionOrWait(session, { return it }) {
                                session.apply(PriestSpellInput.SpellSlot(it.digitToInt()))
                            }
                    }
                    is PriestSpellStep.Script -> {
                        val root = ScriptRootRef.spell(step.spell.id)
                        val result = runSpellScript(step, root)
                        terminal.writeScriptEvents(result.events, renderer, result.runtime)
                        when (result.status) {
                            ScriptBridgeStatus.Finished -> session.finishScript(result.runtime, result.dungeonHandoff)
                            ScriptBridgeStatus.Waiting ->
                                return waiting(
                                    session.replaceScriptContinuation(
                                        checkNotNull(result.continuation) {
                                            "waiting priest spell script did not return continuation"
                                        },
                                    ),
                                    result.rejection,
                                )
                        }
                    }
                    is PriestSpellStep.Message -> {
                        terminal.write(renderer.lines(step.text, TerminalTextContext(runtime = session.runtime)))
                        session.apply(PriestSpellInput.Continue)
                    }
                    is PriestSpellStep.Finished -> return finished(session)
                }
        }
        return TerminalAdapterResult(session, TerminalRunStatus.StepLimitReached)
    }

    private fun runSpellScript(
        step: PriestSpellStep.Script,
        root: ScriptRootRef,
    ): ScriptBridgeResult =
        scriptBridge.run(
            ScriptBridgeInvocation(
                root = root,
                rootOps = step.spell.ops,
                continuation = step.continuation,
            ),
        )
}

/** Terminal adapter for equipment prompts shown while leaving town. */
class TownLeaveEquipmentTerminalAdapter(
    private val terminal: TerminalPort,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(),
) {
    fun run(initial: TownLeaveEquipmentSession): TownLeaveEquipmentSession = runUntilBlocked(initial).session

    fun runUntilBlocked(initial: TownLeaveEquipmentSession): TerminalAdapterResult<TownLeaveEquipmentSession> {
        var session = initial
        repeat(128) {
            session =
                when (val step = session.step) {
                    is TownLeaveEquipmentStep.ReadyQuestion -> {
                        terminal.writeKeyMenu(renderer, step.prompt, yesNoKeys())
                        terminal
                            .readYesNo()
                            .sessionOrWait(session, { return it }) {
                                session.apply(TownLeaveEquipmentInput.ReadyAnswer(it))
                            }
                    }
                    is TownLeaveEquipmentStep.Equipment ->
                        readEquipmentInput(step.session)
                            .sessionOrWait(session, { return it }) {
                                session.apply(TownLeaveEquipmentInput.Equipment(it))
                            }
                    is TownLeaveEquipmentStep.Finished -> return finished(session)
                }
        }
        return TerminalAdapterResult(session, TerminalRunStatus.StepLimitReached)
    }

    private fun readEquipmentInput(session: EquipmentPromptSession): TerminalInput<EquipmentPromptInput> =
        when (val step = session.step) {
            is EquipmentPromptStep.ChooseInventorySlot -> {
                val keys = session.party.inventoryKeys(step.filter)
                terminal.writeKeyMenu(renderer, step.prompt, keys)
                terminal.readKey(keys).map { EquipmentPromptInput.InventorySlot(it - 'A' + 1) }
            }
            is EquipmentPromptStep.ChooseMember -> {
                val choices = session.party.memberChoices(step.filter)
                terminal.writeKeyMenu(renderer, step.prompt, choices.map { it.digitToChar() })
                terminal.readMemberNumber(choices).map { EquipmentPromptInput.Member(it) }
            }
            is EquipmentPromptStep.Finished -> TerminalInput.Unavailable
        }
}

/** Terminal adapter for [rylween.runtime.town.ShopServiceSession]. */
class ShopServiceTerminalAdapter(
    private val terminal: TerminalPort,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(),
) {
    fun run(initial: ShopServiceSession): ShopServiceSession = runUntilBlocked(initial).session

    fun runUntilBlocked(initial: ShopServiceSession): TerminalAdapterResult<ShopServiceSession> {
        var session = initial
        repeat(256) {
            session =
                when (val step = session.step) {
                    is ShopServiceStep.Intro -> {
                        write(step.text)
                        session.continueIntro()
                    }
                    is ShopServiceStep.MainMenu -> {
                        val choices =
                            buildList {
                                if (step.canBuy) add(TerminalChoice(ShopMainChoice.Buy, session.shop.keys.buy))
                                if (step.canSell) add(TerminalChoice(ShopMainChoice.Sell, session.shop.keys.sell))
                                add(TerminalChoice(ShopMainChoice.Leave, session.shop.keys.leave))
                            }
                        terminal.writeBindingMenu(renderer, step.text, choices.map { it.binding })
                        terminal
                            .readChoice(choices)
                            .sessionOrWait(session, { return it }) { session.chooseMain(it) }
                    }
                    is ShopServiceStep.SelectCatalog -> {
                        val buy = checkNotNull(session.shop.buy)
                        val choices = step.catalogs.map { catalog -> TerminalChoice(catalog, catalog.binding) }
                        write(step.prompt)
                        when (val input = terminal.readChoice(choices)) {
                            is TerminalInput.Value -> session.chooseCatalog(input.value)
                            is TerminalInput.Rejected -> {
                                if (buy.paymentKeys.decline.matches(input.rejection.input)) {
                                    session.chooseCatalog(buy.paymentKeys.decline.primaryChar)
                                } else {
                                    return waiting(session, input.rejection)
                                }
                            }
                            TerminalInput.Unavailable -> return waiting(session)
                        }
                    }
                    is ShopServiceStep.BuyItem -> {
                        val entries = step.catalogs.flatMap { catalog -> catalog.pages.flatMap { page -> page.entries } }
                        terminal.write(renderer.lines(step.prompt) + entries.map { "(${it.key}) ${it.label ?: it.item.slug}" })
                        terminal
                            .readChoice(entries.map { TerminalChoice(it, it.binding) })
                            .sessionOrWait(session, { return it }) { entry ->
                                session.withFeedback { chooseBuyItem(entry.item) }
                            }
                    }
                    is ShopServiceStep.Quantity -> {
                        write(step.prompt)
                        terminal
                            .readInt()
                            .sessionOrWait(session, { return it }) { session.enterQuantity(it) }
                    }
                    is ShopServiceStep.ConfirmPurchase -> {
                        val buy = checkNotNull(session.shop.buy)
                        val priceText =
                            when (step.offer.purchase) {
                                is ShopPurchaseModel.Bulk -> buy.unitPrice
                                ShopPurchaseModel.InventoryItem -> buy.singlePrice
                            }
                        priceText?.let {
                            terminal.write(renderer.lines(it, TerminalTextContext(shopPrice = step.totalPrice)))
                        }
                        val choices =
                            listOf(
                                TerminalChoice(true, buy.paymentKeys.pay),
                                TerminalChoice(false, buy.paymentKeys.decline),
                            )
                        terminal.writeBindingMenu(renderer, step.prompt, choices.map { it.binding })
                        terminal
                            .readChoice(choices)
                            .sessionOrWait(session, { return it }) {
                                session.withFeedback { confirmPurchase(it) }
                            }
                    }
                    is ShopServiceStep.DropSlot -> {
                        write(step.prompt)
                        readInventorySlot()
                            .sessionOrWait(session, { return it }) {
                                session.withFeedback { chooseDropSlot(it) }
                            }
                    }
                    is ShopServiceStep.SellSlot -> {
                        write(step.prompt)
                        readInventorySlot()
                            .sessionOrWait(session, { return it }) {
                                session.withFeedback { chooseSellSlot(it) }
                            }
                    }
                    is ShopServiceStep.ConfirmSale -> {
                        val keys = checkNotNull(session.shop.sell).saleKeys
                        val choices =
                            listOf(
                                TerminalChoice(true, keys.accept),
                                TerminalChoice(false, keys.decline),
                            )
                        terminal.writeBindingMenu(
                            renderer,
                            step.prompt,
                            choices.map { it.binding },
                            TerminalTextContext(shopPrice = step.quote.price),
                        )
                        terminal
                            .readChoice(choices)
                            .sessionOrWait(session, { return it }) {
                                session.withFeedback { confirmSale(it) }
                            }
                    }
                    is ShopServiceStep.Finished -> {
                        write(step.text)
                        return finished(session)
                    }
                }
        }
        return TerminalAdapterResult(session, TerminalRunStatus.StepLimitReached)
    }

    private fun write(
        block: TextBlock,
        context: TerminalTextContext = TerminalTextContext(),
    ) {
        terminal.write(renderer.lines(block, context))
    }

    private fun readInventorySlot(): TerminalInput<Int> =
        terminal
            .readKey((0 until InventorySaveSlotMax).map { 'A' + it })
            .map { it - 'A' + 1 }

    private fun ShopServiceSession.withFeedback(update: ShopServiceSession.() -> ShopServiceSession): ShopServiceSession {
        val previous = this
        val updated = update()
        writeFeedback(previous, updated)
        return updated
    }

    private fun writeFeedback(
        previous: ShopServiceSession,
        updated: ShopServiceSession,
    ) {
        updated.lastSellQuote
            ?.takeUnless { it == previous.lastSellQuote }
            ?.let { quote ->
                when (quote.status) {
                    ShopSellQuoteStatus.ItemNotAcceptedHere -> updated.shop.sell?.wrongKind
                    ShopSellQuoteStatus.EmptySlot,
                    ShopSellQuoteStatus.Declined,
                    ShopSellQuoteStatus.Quoted,
                    -> null
                }?.let(::write)
            }

        updated.lastTransaction
            ?.takeUnless { it == previous.lastTransaction }
            ?.let { transaction ->
                when (transaction.status) {
                    ShopTransactionStatus.Bought -> updated.shop.buy?.deal
                    ShopTransactionStatus.Sold -> updated.shop.sell?.sold
                    ShopTransactionStatus.InventoryFull,
                    ShopTransactionStatus.InvalidDropSlot,
                    -> updated.shop.buy?.cantCarry
                    ShopTransactionStatus.NotEnoughGold -> updated.shop.buy?.tooExpensive
                    ShopTransactionStatus.ItemNotAcceptedHere -> updated.shop.sell?.wrongKind
                    ShopTransactionStatus.NeedsDropSlot,
                    ShopTransactionStatus.ItemNotSoldHere,
                    ShopTransactionStatus.EmptySlot,
                    ShopTransactionStatus.InvalidQuantity,
                    ShopTransactionStatus.Declined,
                    -> null
                }?.let(::write)
            }
    }
}

/** Terminal adapter for [rylween.runtime.town.TownSession]. */
class TownSessionTerminalAdapter(
    private val terminal: TerminalPort,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(),
    private val serviceBridge: ScriptBridge = MissingScriptBridge("TownSessionTerminalAdapter"),
) {
    fun run(initial: TownSession): TownSession = runUntilBlocked(initial).session

    fun runUntilBlocked(initial: TownSession): TerminalAdapterResult<TownSession> {
        var session = initial
        repeat(512) {
            session =
                when (val step = session.step) {
                    is TownSessionStep.EntryTax -> {
                        terminal.writeBindingMenu(renderer, step.taxPrompt(), listOf(step.pay, step.leave))
                        val choices =
                            listOf(
                                TerminalChoice(true, step.pay),
                                TerminalChoice(false, step.leave),
                            )
                        terminal
                            .readChoice(choices)
                            .sessionOrWait(session, { return it }) {
                                session.apply(TownSessionInput.EntryTax(it))
                            }
                    }
                    is TownSessionStep.EntryTaxThanks -> {
                        terminal.write(renderer.lines(step.text, TerminalTextContext(runtime = session.runtime)))
                        session.continueEntryTaxThanks()
                    }
                    is TownSessionStep.Menu -> {
                        val choices = step.actions.map { action -> TerminalChoice(action, action.binding) }
                        writeTownMenu(step)
                        terminal
                            .readChoice(choices)
                            .sessionOrWait(session, { return it }) {
                                session.apply(TownSessionInput.MenuKey(it.key))
                            }
                    }
                    is TownSessionStep.Notice -> {
                        terminal.write(renderer.lines(step.text, TerminalTextContext(runtime = session.runtime)))
                        session.continueNotice()
                    }
                    is TownSessionStep.Shop -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            { ShopServiceTerminalAdapter(terminal, renderer).runUntilBlocked(it) },
                            TownSession::replaceShopSession,
                        ) { return it }
                    }
                    is TownSessionStep.LeaveEquipment -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            { TownLeaveEquipmentTerminalAdapter(terminal, renderer).runUntilBlocked(it) },
                            TownSession::replaceLeaveEquipmentSession,
                        ) { return it }
                    }
                    is TownSessionStep.NativeService -> {
                        val continuation = step.continuation ?: ScriptContinuation.start(session.runtime)
                        val result = serviceBridge.run(step.service, step.scene, continuation)
                        terminal.writeScriptEvents(
                            result.events,
                            renderer,
                            result.runtime,
                            renderTownService = true,
                        )
                        when (result.status) {
                            ScriptBridgeStatus.Finished ->
                                session.apply(TownSessionInput.FinishNativeService(result.runtime))
                            ScriptBridgeStatus.Waiting ->
                                return waiting(
                                    session.replaceNativeServiceContinuation(
                                        checkNotNull(result.continuation) { "waiting service bridge did not return continuation" },
                                    ),
                                    result.rejection,
                                )
                        }
                    }
                    is TownSessionStep.Finished -> return finished(session)
                }
        }
        return TerminalAdapterResult(session, TerminalRunStatus.StepLimitReached)
    }

    private fun writeTownMenu(step: TownSessionStep.Menu) {
        val header =
            when (step.town.kind) {
                TownKind.City -> "City of ${step.town.name}"
                TownKind.Village -> "Village of ${step.town.name}"
            }
        val hasChurch = step.actions.any { it.building == DslDefaultBuildings.church }
        terminal.write(
            listOf(
                header,
                "=======================",
                "",
                "(M)erchant  (I)nn",
                "(T)avern    (G)uild",
                if (hasChurch) "(S)mith     (C)hurch" else "(S)mith",
                "(L)eave",
                ": ",
            ),
        )
    }
}

private fun TownSessionStep.EntryTax.taxPrompt(): TextBlock =
    combineTextBlocks(
        prompt,
        inlineText(
            bindingMenuLine(pay, pay.label),
            bindingMenuLine(leave, leave.label),
        ),
    )

/** Terminal adapter for [rylween.runtime.dungeon.DungeonSession]. */
class DungeonSessionTerminalAdapter(
    private val terminal: TerminalPort,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(),
    private val savePort: RuntimeSavePort? = null,
    private val postDungeon: MissionPostDungeonContinuation? = null,
    private val scriptBridge: ScriptBridge = MissingScriptBridge("DungeonSessionTerminalAdapter"),
) {
    fun runUntilBlocked(initial: DungeonSession): TerminalAdapterResult<DungeonSession> {
        var session = initial
        repeat(128) {
            session =
                when (val step = session.step) {
                    DungeonSessionStep.Explore -> {
                        writeExplore(session)
                        val choices = session.keys.exploreCommands().terminalChoices()
                        terminal
                            .readChoice(choices)
                            .sessionOrWait(session, { return it }) { session.apply(it) }
                    }
                    is DungeonSessionStep.Messages -> {
                        terminal.writeBlocks(step.blocks, renderer, TerminalTextContext(runtime = session.runtime))
                        session.apply(DungeonSessionInput.Continue)
                    }
                    is DungeonSessionStep.Notice -> {
                        terminal.write(renderer.lines(step.text, TerminalTextContext(runtime = session.runtime)))
                        session.apply(DungeonSessionInput.Continue)
                    }
                    is DungeonSessionStep.Save -> {
                        val outcome = saveRuntime(session)
                        terminal.writeBlocks(
                            listOf(if (outcome.saved) session.content.prompts.save.done else session.content.prompts.save.failed),
                            renderer,
                            TerminalTextContext(runtime = session.runtime),
                        )
                        session.completeSave(outcome.saved)
                    }
                    is DungeonSessionStep.Equipment -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            { EquipmentPromptTerminalAdapter(terminal, renderer).runUntilBlocked(it) },
                            DungeonSession::replaceEquipmentSession,
                        ) { return it }
                    }
                    is DungeonSessionStep.InventoryItemUse -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            { InventoryItemUseTerminalAdapter(terminal, renderer, scriptBridge = scriptBridge).runUntilBlocked(it) },
                            DungeonSession::replaceInventoryItemUseSession,
                        ) { return it }
                    }
                    is DungeonSessionStep.InventoryDrop -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            { InventoryDropTerminalAdapter(terminal, renderer).runUntilBlocked(it) },
                            DungeonSession::replaceInventoryDropSession,
                        ) { return it }
                    }
                    is DungeonSessionStep.PartyView -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            { PartyViewTerminalAdapter(terminal, renderer).runUntilBlocked(it, session.runtime) },
                            DungeonSession::replacePartyViewSession,
                        ) { return it }
                    }
                    is DungeonSessionStep.PriestSpell -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            {
                                PriestSpellTerminalAdapter(
                                    terminal = terminal,
                                    renderer = renderer,
                                    scriptBridge = scriptBridge,
                                ).runUntilBlocked(it)
                            },
                            DungeonSession::replacePriestSpellSession,
                        ) { return it }
                    }
                    is DungeonSessionStep.Event -> {
                        val result = runEventScript(session, step)
                        terminal.writeScriptEvents(result.events, renderer, result.runtime)
                        when (result.status) {
                            ScriptBridgeStatus.Finished -> session.finishEvent(result.runtime)
                            ScriptBridgeStatus.Waiting -> {
                                val continuation =
                                    checkNotNull(result.continuation) {
                                        "waiting dungeon event did not return continuation"
                                    }
                                val battle =
                                    continuation.battleSession ?: return waiting(
                                        session.replaceEventContinuation(continuation),
                                        result.rejection,
                                    )
                                val battleResult = BattleSessionTerminalAdapter(terminal, renderer).runUntilBlocked(battle)
                                val updated =
                                    session.replaceEventContinuation(
                                        continuation.copy(
                                            runtime = battleResult.session.runtime,
                                            battleSession = battleResult.session,
                                        ),
                                    )
                                continueOrBlock(updated, battleResult.status) { return it }
                            }
                        }
                    }
                    is DungeonSessionStep.RandomEncounter -> {
                        val battle =
                            step.session ?: BattleSession.start(
                                runtime = session.runtime,
                                tier =
                                    checkNotNull(step.event.randomEncounterTier) {
                                        "dungeon random encounter event ${step.event.id.slug} has no tier"
                                    },
                                content = session.content,
                            )
                        val result = BattleSessionTerminalAdapter(terminal, renderer).runUntilBlocked(battle)
                        val updated =
                            session
                                .copy(step = step.copy(session = battle))
                                .replaceRandomEncounterSession(result.session)
                        continueOrBlock(updated, result.status) { return it }
                    }
                    is DungeonSessionStep.Finished -> return finished(session)
                }
        }
        return TerminalAdapterResult(session, TerminalRunStatus.StepLimitReached)
    }

    private fun runEventScript(
        session: DungeonSession,
        step: DungeonSessionStep.Event,
    ): ScriptBridgeResult {
        val root = session.content.scriptRootForDungeonEvent(step.event.id)
        val continuation =
            step.continuation ?: ScriptContinuation.start(session.runtime).copy(
                locals = mapOf("dungeon_level" to session.depth),
            )
        return scriptBridge.run(
            ScriptBridgeInvocation(
                root = root,
                rootOps = step.event.ops,
                continuation = continuation,
                resumeModeAfterBattle = RuntimeMode.Dungeon,
            ),
        )
    }

    private fun saveRuntime(session: DungeonSession): RuntimeSaveOutcome {
        terminal.write(
            renderer.lines(session.content.prompts.save.saving, TerminalTextContext(runtime = session.runtime)),
        )
        val port = savePort ?: return RuntimeSaveOutcome(saved = false, error = "no save port configured")
        return runCatching {
            port.save(RuntimeSaveRequest(session.runtime, session.toDungeonSnapshot(postDungeon)))
        }.getOrElse { error ->
            RuntimeSaveOutcome(saved = false, error = error.message)
        }
    }

    private fun writeExplore(session: DungeonSession) {
        session.dungeons.backdrop?.let { image ->
            terminal.writeEngineEvent(
                EngineTraceEvent.View(ViewCommand.ShowImage(image)),
                renderer,
                TerminalTextContext(runtime = session.runtime),
            )
        }
        terminal.write(
            renderer.bindingPromptLines(
                session.prompts.renderLevelMenu(session.depth),
                session.keys.exploreCommands().commandBindings(),
                TerminalTextContext(runtime = session.runtime),
            ),
        )
    }
}

/** Terminal adapter for [rylween.runtime.wilderness.WildernessSession]. */
class WildernessSessionTerminalAdapter(
    private val terminal: TerminalPort,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(),
    private val serviceBridge: ScriptBridge = MissingScriptBridge("WildernessSessionTerminalAdapter"),
    private val savePort: RuntimeSavePort? = null,
) {
    fun run(initial: WildernessSession): WildernessSession = runUntilBlocked(initial).session

    fun runUntilBlocked(initial: WildernessSession): TerminalAdapterResult<WildernessSession> {
        var session = initial
        repeat(512) {
            session =
                when (val step = session.step) {
                    WildernessSessionStep.TurnStart -> session.beginTurn()
                    is WildernessSessionStep.Messages -> {
                        terminal.writeBlocks(step.blocks, renderer, TerminalTextContext(runtime = session.runtime))
                        session.continueMessages()
                    }
                    is WildernessSessionStep.Save -> {
                        val outcome = saveRuntime(session)
                        terminal.writeBlocks(
                            listOf(if (outcome.saved) session.content.prompts.save.done else session.content.prompts.save.failed),
                            renderer,
                            TerminalTextContext(runtime = session.runtime),
                        )
                        session.completeSave(outcome.saved)
                    }
                    is WildernessSessionStep.OutOfMoves -> {
                        val keys = session.wilderness.keys
                        val commands = keys.outOfMovesCommands()
                        terminal.writeBindingMenu(renderer, step.prompt, commands.commandBindings())
                        terminal
                            .readChoice(commands.terminalChoices())
                            .sessionOrWait(session, { return it }) { session.apply(it) }
                    }
                    is WildernessSessionStep.CaveFound -> {
                        val keys = session.wilderness.keys
                        step.image?.let { image ->
                            terminal.writeEngineEvent(
                                EngineTraceEvent.View(ViewCommand.ShowImage(image)),
                                renderer,
                                TerminalTextContext(runtime = session.runtime),
                            )
                        }
                        val commands = keys.caveCommands()
                        terminal.writeBindingMenu(renderer, step.prompt, commands.commandBindings())
                        terminal
                            .readChoice(commands.terminalChoices())
                            .sessionOrWait(session, { return it }) { session.apply(it) }
                    }
                    is WildernessSessionStep.Mission -> {
                        val continuation = step.continuation ?: ScriptContinuation.start(session.runtime)
                        val result = serviceBridge.run(step.mission.service(), step.scene, continuation)
                        terminal.writeScriptEvents(
                            result.events,
                            renderer,
                            result.runtime,
                            renderDungeonHandoff = false,
                        )
                        when (result.status) {
                            ScriptBridgeStatus.Finished ->
                                result.dungeonHandoff?.let { handoff ->
                                    session.handOffMissionDungeon(
                                        result.runtime,
                                        handoff,
                                        checkNotNull(result.continuation) {
                                            "mission dungeon handoff did not return post-dungeon continuation"
                                        },
                                    )
                                } ?: session.finishMission(result.runtime)
                            ScriptBridgeStatus.Waiting -> {
                                val nextContinuation =
                                    checkNotNull(result.continuation) {
                                        "waiting mission bridge did not return continuation"
                                    }
                                val battle =
                                    nextContinuation.battleSession
                                        ?: return waiting(
                                            session.replaceMissionContinuation(nextContinuation),
                                            result.rejection,
                                        )
                                val battleResult = BattleSessionTerminalAdapter(terminal, renderer).runUntilBlocked(battle)
                                val updated =
                                    session.replaceMissionContinuation(
                                        nextContinuation.copy(
                                            runtime = battleResult.session.runtime,
                                            battleSession = battleResult.session,
                                        ),
                                    )
                                continueOrBlock(updated, battleResult.status) { return it }
                            }
                        }
                    }
                    is WildernessSessionStep.Hut -> {
                        val continuation = step.continuation ?: ScriptContinuation.start(session.runtime)
                        val result = serviceBridge.run(step.scene.hutService(), step.scene, continuation)
                        terminal.writeScriptEvents(result.events, renderer, result.runtime)
                        when (result.status) {
                            ScriptBridgeStatus.Finished -> session.finishHut(result.runtime)
                            ScriptBridgeStatus.Waiting -> {
                                val nextContinuation =
                                    checkNotNull(result.continuation) {
                                        "waiting hut bridge did not return continuation"
                                    }
                                return waiting(
                                    session.replaceHutContinuation(nextContinuation),
                                    result.rejection,
                                )
                            }
                        }
                    }
                    is WildernessSessionStep.DungeonHandoff -> {
                        terminal.write(
                            renderer.lines(
                                session.prompts.renderDungeonHandoff(
                                    session.dungeons.displayName(step.handoff.dungeon),
                                    step.handoff.levels,
                                ),
                                TerminalTextContext(runtime = session.runtime),
                            ),
                        )
                        session.startDungeonFromHandoff()
                    }
                    is WildernessSessionStep.Dungeon -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            {
                                DungeonSessionTerminalAdapter(
                                    terminal = terminal,
                                    renderer = renderer,
                                    savePort = savePort,
                                    postDungeon = step.postDungeon,
                                    scriptBridge = serviceBridge,
                                ).runUntilBlocked(it)
                            },
                            WildernessSession::replaceDungeonSession,
                        ) { return it }
                    }
                    is WildernessSessionStep.Castle -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            {
                                CastleSessionTerminalAdapter(
                                    terminal = terminal,
                                    renderer = renderer,
                                    savePort = savePort,
                                    scriptBridge = serviceBridge,
                                ).runUntilBlocked(it)
                            },
                            WildernessSession::replaceCastleSession,
                        ) { return it }
                    }
                    WildernessSessionStep.Explore -> {
                        writeExplore(session)
                        val choices = session.exploreCommands().terminalChoices()
                        terminal
                            .readChoice(choices)
                            .sessionOrWait(session, { return it }) { session.apply(it) }
                    }
                    is WildernessSessionStep.Notice -> {
                        terminal.write(renderer.lines(step.text, TerminalTextContext(runtime = session.runtime)))
                        session.continueNotice()
                    }
                    is WildernessSessionStep.QuitConfirm -> {
                        val keys = session.wilderness.keys
                        val commands = keys.quitConfirmCommands()
                        terminal.writeBindingMenu(renderer, step.prompt, commands.commandBindings())
                        terminal
                            .readChoice(commands.terminalChoices())
                            .sessionOrWait(session, { return it }) { session.apply(it) }
                    }
                    is WildernessSessionStep.Town -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            { TownSessionTerminalAdapter(terminal, renderer, serviceBridge).runUntilBlocked(it) },
                            WildernessSession::replaceTownSession,
                        ) { return it }
                    }
                    is WildernessSessionStep.Battle -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            { BattleSessionTerminalAdapter(terminal, renderer).runUntilBlocked(it) },
                            WildernessSession::replaceBattleSession,
                        ) { return it }
                    }
                    is WildernessSessionStep.Camp -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            { CampSessionTerminalAdapter(terminal, renderer).runUntilBlocked(it) },
                            WildernessSession::replaceCampSession,
                        ) { return it }
                    }
                    is WildernessSessionStep.Hunt -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            { HuntSessionTerminalAdapter(terminal, renderer).runUntilBlocked(it) },
                            WildernessSession::replaceHuntSession,
                        ) { return it }
                    }
                    is WildernessSessionStep.InventoryItemUse -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            { InventoryItemUseTerminalAdapter(terminal, renderer, scriptBridge = serviceBridge).runUntilBlocked(it) },
                            WildernessSession::replaceInventoryItemUseSession,
                        ) { return it }
                    }
                    is WildernessSessionStep.InventoryDrop -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            { InventoryDropTerminalAdapter(terminal, renderer).runUntilBlocked(it) },
                            WildernessSession::replaceInventoryDropSession,
                        ) { return it }
                    }
                    is WildernessSessionStep.PartyView -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            { PartyViewTerminalAdapter(terminal, renderer).runUntilBlocked(it, session.runtime) },
                            WildernessSession::replacePartyViewSession,
                        ) { return it }
                    }
                    is WildernessSessionStep.PartyJournal -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            { PartyJournalTerminalAdapter(terminal, renderer).runUntilBlocked(it, session.runtime) },
                            WildernessSession::replacePartyJournalSession,
                        ) { return it }
                    }
                    is WildernessSessionStep.Equipment -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            { EquipmentPromptTerminalAdapter(terminal, renderer).runUntilBlocked(it) },
                            WildernessSession::replaceEquipmentSession,
                        ) { return it }
                    }
                    is WildernessSessionStep.PriestSpell -> {
                        runChildOrBlock(
                            session,
                            step.session,
                            {
                                PriestSpellTerminalAdapter(
                                    terminal = terminal,
                                    renderer = renderer,
                                    scriptBridge = serviceBridge,
                                ).runUntilBlocked(it)
                            },
                            WildernessSession::replacePriestSpellSession,
                        ) { return it }
                    }
                    is WildernessSessionStep.Finished -> return finished(session)
                }
        }
        return TerminalAdapterResult(session, TerminalRunStatus.StepLimitReached)
    }

    private fun saveRuntime(session: WildernessSession): RuntimeSaveOutcome {
        terminal.writeBlocks(
            listOf(session.content.prompts.save.saving),
            renderer,
            TerminalTextContext(runtime = session.runtime),
        )
        val port = savePort ?: return RuntimeSaveOutcome(saved = false, error = "no save port configured")
        return runCatching { port.save(session.runtime) }
            .getOrElse { error -> RuntimeSaveOutcome(saved = false, error = error.message) }
    }

    private fun writeExplore(session: WildernessSession) {
        val point = (session.party.position as? WorldPosition.Wilderness)?.point
        val lines =
            buildList {
                add("")
                add("Wilderness")
                addAll(point?.let { renderer.worldMapViewportLines(session.worldMap.viewport(it)) }.orEmpty())
                add(statusLine(session, point))
                point?.let { current ->
                    session.towns.firstOrNull { town -> town.position == current }?.let { town ->
                        add("At: ${town.name}")
                    }
                }
                session.lastResult?.takeIf { it.status == WildernessSessionResultStatus.Blocked }?.let {
                    add("Blocked.")
                }
                addAll(renderer.lines(session.wilderness.keys.commandHint, TerminalTextContext(runtime = session.runtime)))
            }
        terminal.write(lines)
    }

    private fun MissionModel.service(): BuildingServiceModel =
        BuildingServiceModel(
            building = DslDefaultBuildings.none,
            action = keyAction("mission.${id.slug}"),
            scene = action,
            opensAt = null,
            closesAt = null,
            nativeServices = emptyList(),
        )

    private fun statusLine(
        session: WildernessSession,
        point: GridPoint?,
    ): String {
        val terrain = point?.let { session.worldMap.tileAt(it) } ?: ' '
        val position = point?.let { "${it.x}x${it.y}" } ?: "not in wilderness"
        return "Position: $position  Moves: ${session.party.resources.timeLeft}  Tile: $terrain"
    }
}

/** Terminal adapter for [rylween.runtime.battle.BattleSession]. */
class BattleSessionTerminalAdapter(
    private val terminal: TerminalPort,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(),
) {
    fun run(initial: BattleSession): BattleSession = runUntilBlocked(initial).session

    fun runUntilBlocked(initial: BattleSession): TerminalAdapterResult<BattleSession> {
        var session = initial
        repeat(64) {
            session =
                when (val step = session.step) {
                    is BattleSessionStep.Opening -> {
                        writeStepViewCommands(step, session.runtime)
                        readOpening(step.prompt, session.battle.openingKeys)
                            .sessionOrWait(session, { return it }) { session.apply(BattleSessionInput.Opening(it)) }
                    }
                    is BattleSessionStep.RoundOpening -> {
                        readOpening(step.prompt, session.battle.openingKeys)
                            .sessionOrWait(session, { return it }) { session.apply(BattleSessionInput.Opening(it)) }
                    }
                    is BattleSessionStep.Message -> {
                        writeStepViewCommands(step, session.runtime)
                        val context = TerminalTextContext(runtime = session.runtime)
                        step.texts.forEach { text ->
                            terminal.write(renderer.lines(text, context))
                        }
                        terminal.finishOutputFrame()
                        session.apply(BattleSessionInput.Continue)
                    }
                    is BattleSessionStep.PlayerAction -> {
                        val choices = step.actions.map { action -> TerminalChoice(action, action.binding) }
                        terminal.writeBindingMenu(renderer, step.prompt, choices.map { it.binding })
                        terminal
                            .readChoice(choices)
                            .sessionOrWait(session, { return it }) {
                                session.apply(BattleSessionInput.PlayerAction(it.kind))
                            }
                    }
                    is BattleSessionStep.MonsterTarget -> {
                        readKeyOffset(step.prompt, step.targetKeys, 'A')
                            .sessionOrWait(session, { return it }) { session.apply(BattleSessionInput.TargetMonster(it)) }
                    }
                    is BattleSessionStep.ScriptMonsterTarget -> {
                        readKeyOffset(step.prompt, step.targetKeys, 'A')
                            .sessionOrWait(session, { return it }) { session.apply(BattleSessionInput.TargetMonster(it)) }
                    }
                    is BattleSessionStep.MemberTarget -> {
                        readKeyOffset(step.prompt, step.targetKeys, '1')
                            .sessionOrWait(session, { return it }) { session.apply(BattleSessionInput.TargetMember(it)) }
                    }
                    is BattleSessionStep.MagicItemSlot -> {
                        readKeyOffset(step.prompt, step.slotKeys, 'A' - 1)
                            .sessionOrWait(session, { return it }) { session.apply(BattleSessionInput.InventorySlot(it)) }
                    }
                    is BattleSessionStep.SpellChoice -> {
                        readKeyOffset(step.prompt, step.spellKeys, '0')
                            .sessionOrWait(session, { return it }) { session.apply(BattleSessionInput.SpellSlot(it)) }
                    }
                    BattleSessionStep.MonsterRound -> session.apply(BattleSessionInput.Continue)
                    is BattleSessionStep.Finished -> return finished(session)
                }
        }
        return TerminalAdapterResult(session, TerminalRunStatus.StepLimitReached)
    }

    private fun battleOpeningChoices(keys: BattleOpeningKeysModel): List<TerminalChoice<BattleOpeningChoice>> =
        listOf(
            TerminalChoice(BattleOpeningChoice.Fight, keys.fight),
            TerminalChoice(BattleOpeningChoice.Run, keys.run),
        )

    private fun readOpening(
        prompt: TextBlock,
        keys: BattleOpeningKeysModel,
    ): TerminalInput<BattleOpeningChoice> {
        val choices = battleOpeningChoices(keys)
        terminal.writeBindingMenu(renderer, prompt, choices.map { it.binding })
        return terminal.readChoice(choices)
    }

    private fun readKeyOffset(
        prompt: TextBlock,
        keys: List<Char>,
        offset: Char,
    ): TerminalInput<Int> {
        terminal.writeKeyMenu(renderer, prompt, keys)
        return terminal.readKey(keys).map { key -> key - offset }
    }

    private fun writeStepViewCommands(
        step: BattleSessionStep,
        runtime: RuntimeSessionState,
    ) {
        val context = TerminalTextContext(runtime = runtime)
        step.viewCommands.forEach { command ->
            terminal.writeViewCommand(command, renderer, context)
        }
    }
}
