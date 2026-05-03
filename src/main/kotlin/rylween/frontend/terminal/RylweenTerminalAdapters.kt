// Line-oriented terminal frontend adapters over backend-neutral runtime sessions.
// Keep display/input policy here so core engine code can be shared by ANSI, bitmap, server, and tests.

package rylween.frontend.terminal

import rylween.dsl.*
import rylween.engine.*
import rylween.frontend.terminal.ydarg.YdargSessionTerminalAdapter
import rylween.runtime.*

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

class CampSessionTerminalAdapter(
    private val terminal: TerminalPort,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(),
) {
    fun run(initial: CampSession): CampSession =
        runUntilBlocked(initial).session

    fun runUntilBlocked(initial: CampSession): TerminalAdapterResult<CampSession> {
        var session = initial
        repeat(64) {
            session = when (val step = session.step) {
                is CampSessionStep.GuardChoice -> {
                    terminal.writeKeyMenu(renderer, step.prompt, listOf('Y', 'N'))
                    val choices = listOf(TerminalChoice(true, inputKey("Y")), TerminalChoice(false, inputKey("N")))
                    terminal.readChoice(choices)
                        .sessionOrWait(session, { return it }) { session.apply(CampSessionInput.GuardAnswer(it)) }
                }
                is CampSessionStep.GuardMember -> {
                    val keys = session.party.members.indices.map { (it + 1).digitToChar() }
                    terminal.writeKeyMenu(renderer, step.prompt, keys)
                    terminal.readMemberNumber(session.party.members.indices.map { it + 1 })
                        .sessionOrWait(session, { return it }) { session.apply(CampSessionInput.GuardMember(it)) }
                }
                is CampSessionStep.Messages -> {
                    terminal.writeBlocks(step.blocks, renderer, TerminalTextContext(runtime = session.runtime))
                    session.apply(CampSessionInput.Continue)
                }
                is CampSessionStep.Ambush -> {
                    val battleResult = BattleSessionTerminalAdapter(terminal, renderer).runUntilBlocked(step.session)
                    val updated = session.replaceAmbushSession(battleResult.session)
                    continueOrBlock(updated, battleResult.status) { return it }
                }
                is CampSessionStep.Finished -> return finished(session)
            }
        }
        return TerminalAdapterResult(session, TerminalRunStatus.StepLimitReached)
    }
}

class HuntSessionTerminalAdapter(
    private val terminal: TerminalPort,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(),
) {
    fun run(initial: HuntSession): HuntSession =
        runUntilBlocked(initial).session

    fun runUntilBlocked(initial: HuntSession): TerminalAdapterResult<HuntSession> {
        var session = initial
        repeat(64) {
            session = when (val step = session.step) {
                is HuntSessionStep.ChooseMember -> {
                    val choices = session.memberChoices()
                    terminal.writeKeyMenu(renderer, step.prompt, choices.map { it.digitToChar() }, session.runtime)
                    terminal.readMemberNumber(choices)
                        .sessionOrWait(session, { return it }) { session.apply(HuntSessionInput.Member(it)) }
                }
                is HuntSessionStep.Messages -> {
                    val context = TerminalTextContext(
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

class EquipmentPromptTerminalAdapter(
    private val terminal: TerminalPort,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(),
) {
    fun run(initial: EquipmentPromptSession): EquipmentPromptSession =
        runUntilBlocked(initial).session

    fun runUntilBlocked(initial: EquipmentPromptSession): TerminalAdapterResult<EquipmentPromptSession> {
        var session = initial
        repeat(64) {
            session = when (val step = session.step) {
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

class InventoryItemUseTerminalAdapter(
    private val terminal: TerminalPort,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(),
    private val engine: TraceEnginePort = TraceEnginePort(),
    private val speakContent: SpeakRuntimeContent = SpeakRuntimeContent(),
) {
    fun run(initial: InventoryItemUseSession): InventoryItemUseSession =
        runUntilBlocked(initial).session

    fun runUntilBlocked(initial: InventoryItemUseSession): TerminalAdapterResult<InventoryItemUseSession> {
        var session = initial
        repeat(128) {
            session = when (val step = session.step) {
                is InventoryItemUseStep.ChooseItem -> {
                    terminal.writeKeyMenu(renderer, step.prompt, step.choices.map { it.key }, session.runtime)
                    terminal.readKey(step.choices.map { it.key })
                        .sessionOrWait(session, { return it }) { key ->
                            val slot = checkNotNull(step.choices.firstOrNull { it.key == key }).slot
                            session.apply(InventoryItemUseInput.InventorySlot(slot))
                        }
                    }
                is InventoryItemUseStep.Equipment -> {
                    val result = EquipmentPromptTerminalAdapter(terminal, renderer).runUntilBlocked(step.session)
                    val updated = session.replaceEquipmentSession(result.session)
                    continueOrBlock(updated, result.status) { return it }
                }
                is InventoryItemUseStep.Script -> {
                    val eventStart = engine.events.size
                    val result = StatefulScriptServiceRunner(
                        terminal = terminal,
                        engine = engine,
                        rootOps = step.use.ops,
                        continuation = step.continuation,
                        speakContent = speakContent,
                        battleContent = RuntimeContent(
                            itemRecords = session.itemRecords,
                            realm = session.realmContent,
                        ),
                        textRenderer = renderer,
                    ).run()
                    terminal.writeScriptEvents(
                        engine.events.drop(result.unwrittenEventStart.coerceAtLeast(eventStart)),
                        renderer,
                        result.runtime,
                    )
                    when (result.status) {
                        TownServiceBridgeStatus.Finished ->
                            session.finishScript(result.runtime)
                        TownServiceBridgeStatus.Waiting ->
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
}

class PriestSpellTerminalAdapter(
    private val terminal: TerminalPort,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(),
    private val engine: TraceEnginePort = TraceEnginePort(),
    private val speakContent: SpeakRuntimeContent = SpeakRuntimeContent(),
    private val worldMap: WorldMapGrid? = null,
) {
    fun runUntilBlocked(initial: PriestSpellSession): TerminalAdapterResult<PriestSpellSession> {
        var session = initial
        repeat(128) {
            session = when (val step = session.step) {
                is PriestSpellStep.ChooseCaster -> {
                    terminal.writeKeyMenu(renderer, step.prompt, step.casterKeys, session.runtime)
                    terminal.readMemberNumber(step.casterKeys.map { it.digitToInt() })
                        .sessionOrWait(session, { return it }) { session.apply(PriestSpellInput.Caster(it)) }
                }
                is PriestSpellStep.ChooseSpell -> {
                    terminal.writeKeyMenu(renderer, step.prompt, step.spellKeys, session.runtime)
                    terminal.readKey(step.spellKeys)
                        .sessionOrWait(session, { return it }) {
                            session.apply(PriestSpellInput.SpellSlot(it.digitToInt()))
                        }
                }
                is PriestSpellStep.Script -> {
                    val eventStart = engine.events.size
                    val result = StatefulScriptServiceRunner(
                        terminal = terminal,
                        engine = engine,
                        rootOps = step.spell.ops,
                        continuation = step.continuation,
                        speakContent = speakContent,
                        battleContent = RuntimeContent(itemRecords = emptyList()),
                        worldMap = worldMap,
                        textRenderer = renderer,
                    ).run()
                    terminal.writeScriptEvents(
                        engine.events.drop(result.unwrittenEventStart.coerceAtLeast(eventStart)),
                        renderer,
                        result.runtime,
                    )
                    when (result.status) {
                        TownServiceBridgeStatus.Finished -> session.finishScript(result.runtime, result.dungeonHandoff)
                        TownServiceBridgeStatus.Waiting ->
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
}

class TownLeaveEquipmentTerminalAdapter(
    private val terminal: TerminalPort,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(),
) {
    fun run(initial: TownLeaveEquipmentSession): TownLeaveEquipmentSession =
        runUntilBlocked(initial).session

    fun runUntilBlocked(initial: TownLeaveEquipmentSession): TerminalAdapterResult<TownLeaveEquipmentSession> {
        var session = initial
        repeat(128) {
            session = when (val step = session.step) {
                is TownLeaveEquipmentStep.ReadyQuestion -> {
                    terminal.writeKeyMenu(renderer, step.prompt, yesNoKeys())
                    terminal.readYesNo()
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

class ShopServiceTerminalAdapter(
    private val terminal: TerminalPort,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(),
) {
    fun run(initial: ShopServiceSession): ShopServiceSession =
        runUntilBlocked(initial).session

    fun runUntilBlocked(initial: ShopServiceSession): TerminalAdapterResult<ShopServiceSession> {
        var session = initial
        repeat(256) {
            session = when (val step = session.step) {
                is ShopServiceStep.Intro -> {
                    write(step.text)
                    session.continueIntro()
                }
                is ShopServiceStep.MainMenu -> {
                    val choices = buildList {
                        if (step.canBuy) add(TerminalChoice(ShopMainChoice.Buy, session.shop.keys.buy))
                        if (step.canSell) add(TerminalChoice(ShopMainChoice.Sell, session.shop.keys.sell))
                        add(TerminalChoice(ShopMainChoice.Leave, session.shop.keys.leave))
                    }
                    terminal.writeBindingMenu(renderer, step.text, choices.map { it.binding })
                    terminal.readChoice(choices)
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
                    terminal.readChoice(entries.map { TerminalChoice(it, it.binding) })
                        .sessionOrWait(session, { return it }) { entry ->
                            session.withFeedback { chooseBuyItem(entry.item) }
                        }
                }
                is ShopServiceStep.Quantity -> {
                    write(step.prompt)
                    terminal.readInt()
                        .sessionOrWait(session, { return it }) { session.enterQuantity(it) }
                }
                is ShopServiceStep.ConfirmPurchase -> {
                    val buy = checkNotNull(session.shop.buy)
                    val priceText = when (step.offer.purchase) {
                        is ShopPurchaseModel.Bulk -> buy.unitPrice
                        ShopPurchaseModel.InventoryItem -> buy.singlePrice
                    }
                    priceText?.let {
                        terminal.write(renderer.lines(it, TerminalTextContext(shopPrice = step.totalPrice)))
                    }
                    val choices = listOf(
                        TerminalChoice(true, buy.paymentKeys.pay),
                        TerminalChoice(false, buy.paymentKeys.decline),
                    )
                    terminal.writeBindingMenu(renderer, step.prompt, choices.map { it.binding })
                    terminal.readChoice(choices)
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
                    val choices = listOf(
                        TerminalChoice(true, keys.accept),
                        TerminalChoice(false, keys.decline),
                    )
                    terminal.writeBindingMenu(
                        renderer,
                        step.prompt,
                        choices.map { it.binding },
                        TerminalTextContext(shopPrice = step.quote.price),
                    )
                    terminal.readChoice(choices)
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

    private fun write(block: TextBlock, context: TerminalTextContext = TerminalTextContext()) {
        terminal.write(renderer.lines(block, context))
    }

    private fun readInventorySlot(): TerminalInput<Int> =
        terminal.readKey((0 until InventorySaveSlotMax).map { 'A' + it })
            .map { it - 'A' + 1 }

    private fun ShopServiceSession.withFeedback(update: ShopServiceSession.() -> ShopServiceSession): ShopServiceSession {
        val previous = this
        val updated = update()
        writeFeedback(previous, updated)
        return updated
    }

    private fun writeFeedback(previous: ShopServiceSession, updated: ShopServiceSession) {
        updated.lastSellQuote
            ?.takeUnless { it == previous.lastSellQuote }
            ?.let { quote ->
                when (quote.status) {
                    ShopSellQuoteStatus.ItemNotAcceptedHere -> updated.shop.sell?.wrongKind
                    ShopSellQuoteStatus.EmptySlot,
                    ShopSellQuoteStatus.Declined,
                    ShopSellQuoteStatus.Quoted -> null
                }?.let(::write)
            }

        updated.lastTransaction
            ?.takeUnless { it == previous.lastTransaction }
            ?.let { transaction ->
                when (transaction.status) {
                    ShopTransactionStatus.Bought -> updated.shop.buy?.deal
                    ShopTransactionStatus.Sold -> updated.shop.sell?.sold
                    ShopTransactionStatus.InventoryFull,
                    ShopTransactionStatus.InvalidDropSlot -> updated.shop.buy?.cantCarry
                    ShopTransactionStatus.NotEnoughGold -> updated.shop.buy?.tooExpensive
                    ShopTransactionStatus.ItemNotAcceptedHere -> updated.shop.sell?.wrongKind
                    ShopTransactionStatus.NeedsDropSlot,
                    ShopTransactionStatus.ItemNotSoldHere,
                    ShopTransactionStatus.EmptySlot,
                    ShopTransactionStatus.InvalidQuantity,
                    ShopTransactionStatus.Declined -> null
                }?.let(::write)
            }
    }
}

class TownSessionTerminalAdapter(
    private val terminal: TerminalPort,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(),
    private val serviceBridge: TownServiceBridge? = null,
) {
    fun run(initial: TownSession): TownSession =
        runUntilBlocked(initial).session

    fun runUntilBlocked(initial: TownSession): TerminalAdapterResult<TownSession> {
        var session = initial
        repeat(512) {
            session = when (val step = session.step) {
                is TownSessionStep.Menu -> {
                    val choices = step.actions.map { action -> TerminalChoice(action, action.binding) }
                    writeTownMenu(step)
                    terminal.readChoice(choices)
                        .sessionOrWait(session, { return it }) {
                            session.apply(TownSessionInput.MenuKey(it.key))
                        }
                }
                is TownSessionStep.Notice -> {
                    terminal.write(renderer.lines(step.text, TerminalTextContext(runtime = session.runtime)))
                    session.continueNotice()
                }
                is TownSessionStep.Shop -> {
                    val result = ShopServiceTerminalAdapter(terminal, renderer).runUntilBlocked(step.session)
                    val updated = session.replaceShopSession(result.session)
                    continueOrBlock(updated, result.status) { return it }
                }
                is TownSessionStep.LeaveEquipment -> {
                    val result = TownLeaveEquipmentTerminalAdapter(terminal, renderer).runUntilBlocked(step.session)
                    val updated = session.replaceLeaveEquipmentSession(result.session)
                    continueOrBlock(updated, result.status) { return it }
                }
                is TownSessionStep.NativeService -> {
                    val continuation = step.continuation ?: TownServiceContinuation.start(session.runtime)
                    val bridge = serviceBridge ?: StatefulTownServiceBridge(
                        terminal,
                        speakContent = session.content.speakRuntimeContent(),
                        battleContent = session.content,
                        renderer = renderer,
                    )
                    val result = bridge.run(step.service, step.scene, continuation)
                    terminal.writeScriptEvents(
                        result.events,
                        renderer,
                        result.runtime,
                        renderTownService = true,
                    )
                    when (result.status) {
                        TownServiceBridgeStatus.Finished ->
                            session.apply(TownSessionInput.FinishNativeService(result.runtime))
                        TownServiceBridgeStatus.Waiting ->
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
        val header = when (step.town.kind) {
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

class DungeonSessionTerminalAdapter(
    private val terminal: TerminalPort,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(),
) {
    fun runUntilBlocked(initial: DungeonSession): TerminalAdapterResult<DungeonSession> {
        var session = initial
        repeat(128) {
            session = when (val step = session.step) {
                DungeonSessionStep.Explore -> {
                    writeExplore(session)
                    val choices = session.keys.exploreCommands().terminalChoices()
                    terminal.readChoice(choices)
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
                is DungeonSessionStep.Equipment -> {
                    val result = EquipmentPromptTerminalAdapter(terminal, renderer).runUntilBlocked(step.session)
                    val updated = session.replaceEquipmentSession(result.session)
                    continueOrBlock(updated, result.status) { return it }
                }
                is DungeonSessionStep.InventoryItemUse -> {
                    val result = InventoryItemUseTerminalAdapter(terminal, renderer).runUntilBlocked(step.session)
                    val updated = session.replaceInventoryItemUseSession(result.session)
                    continueOrBlock(updated, result.status) { return it }
                }
                is DungeonSessionStep.PriestSpell -> {
                    val result = PriestSpellTerminalAdapter(
                        terminal = terminal,
                        renderer = renderer,
                        worldMap = session.worldMap,
                    ).runUntilBlocked(step.session)
                    val updated = session.replacePriestSpellSession(result.session)
                    continueOrBlock(updated, result.status) { return it }
                }
                is DungeonSessionStep.Event -> {
                    val result = runEventScript(session, step)
                    terminal.writeScriptEvents(result.events, renderer, result.runtime)
                    when (result.status) {
                        TownServiceBridgeStatus.Finished -> session.finishEvent(result.runtime)
                        TownServiceBridgeStatus.Waiting -> {
                            val continuation = checkNotNull(result.continuation) {
                                "waiting dungeon event did not return continuation"
                            }
                            val battle = continuation.battleSession
                            if (battle == null) {
                                return waiting(session.replaceEventContinuation(continuation), result.rejection)
                            }
                            val battleResult = BattleSessionTerminalAdapter(terminal, renderer).runUntilBlocked(battle)
                            val updated = session.replaceEventContinuation(
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
                    val battle = step.session ?: BattleSession.start(
                        runtime = session.runtime,
                        tier = checkNotNull(step.event.randomEncounterTier) {
                            "dungeon random encounter event ${step.event.id.slug} has no tier"
                        },
                        content = session.content,
                    )
                    val result = BattleSessionTerminalAdapter(terminal, renderer).runUntilBlocked(battle)
                    val updated = session.copy(step = step.copy(session = battle))
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
    ): TownServiceBridgeResult {
        val engine = TraceEnginePort()
        val eventStart = engine.events.size
        val continuation = step.continuation ?: TownServiceContinuation.start(session.runtime).copy(
            locals = mapOf("dungeon_level" to session.depth),
        )
        val result = StatefulScriptServiceRunner(
            terminal = terminal,
            engine = engine,
            rootOps = step.event.ops,
            continuation = continuation,
            speakContent = SpeakRuntimeContent(),
            battleContent = RuntimeContent(
                combat = session.combat,
                monsters = session.monsters,
                formulas = session.formulas,
                rewards = session.rewards,
                leveling = session.leveling,
                spells = session.spells,
                itemUses = session.itemUses,
                itemRecords = session.itemRecords,
                realm = session.realm,
            ),
            worldMap = session.worldMap,
            textRenderer = renderer,
            resumeModeAfterBattle = RuntimeMode.Dungeon,
        ).run()
        return TownServiceBridgeResult(
            runtime = result.runtime,
            status = result.status,
            events = engine.events.drop(result.unwrittenEventStart.coerceAtLeast(eventStart)),
            continuation = result.continuation,
            rejection = result.rejection,
            dungeonHandoff = result.dungeonHandoff,
        )
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
                DungeonPromptText.levelMenu(session.depth),
                session.keys.exploreCommands().commandBindings(),
                TerminalTextContext(runtime = session.runtime),
            ),
        )
    }
}

class WildernessSessionTerminalAdapter(
    private val terminal: TerminalPort,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(),
    private val serviceBridge: TownServiceBridge? = null,
) {
    fun run(initial: WildernessSession): WildernessSession =
        runUntilBlocked(initial).session

    fun runUntilBlocked(initial: WildernessSession): TerminalAdapterResult<WildernessSession> {
        var session = initial
        repeat(512) {
            session = when (val step = session.step) {
                WildernessSessionStep.TurnStart -> session.beginTurn()
                is WildernessSessionStep.Messages -> {
                    terminal.writeBlocks(step.blocks, renderer, TerminalTextContext(runtime = session.runtime))
                    session.continueMessages()
                }
                is WildernessSessionStep.OutOfMoves -> {
                    val keys = session.wilderness.keys
                    val commands = keys.outOfMovesCommands()
                    terminal.writeBindingMenu(renderer, step.prompt, commands.commandBindings())
                    terminal.readChoice(commands.terminalChoices())
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
                    terminal.readChoice(commands.terminalChoices())
                        .sessionOrWait(session, { return it }) { session.apply(it) }
                }
                is WildernessSessionStep.Mission -> {
                    val continuation = step.continuation ?: TownServiceContinuation.start(session.runtime)
                    val bridge = StatefulTownServiceBridge(
                        terminal = terminal,
                        speakContent = session.content.speakRuntimeContent(),
                        battleContent = session.content,
                        worldMap = session.worldMap,
                        renderer = renderer,
                    )
                    val result = bridge.run(step.mission.service(), step.scene, continuation)
                    terminal.writeScriptEvents(
                        result.events,
                        renderer,
                        result.runtime,
                        renderDungeonHandoff = false,
                    )
                    when (result.status) {
                        TownServiceBridgeStatus.Finished ->
                            result.dungeonHandoff?.let { handoff ->
                                session.handOffMissionDungeon(result.runtime, handoff)
                            } ?: session.finishMission(result.runtime)
                        TownServiceBridgeStatus.Waiting -> {
                            val nextContinuation = checkNotNull(result.continuation) {
                                "waiting mission bridge did not return continuation"
                            }
                            val battle = nextContinuation.battleSession
                                ?: return waiting(
                                    session.replaceMissionContinuation(nextContinuation),
                                    result.rejection,
                                )
                            val battleResult = BattleSessionTerminalAdapter(terminal, renderer).runUntilBlocked(battle)
                            val updated = session.replaceMissionContinuation(
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
                    val continuation = step.continuation ?: TownServiceContinuation.start(session.runtime)
                    val bridge = serviceBridge ?: StatefulTownServiceBridge(
                        terminal = terminal,
                        speakContent = session.content.speakRuntimeContent(),
                        battleContent = session.content,
                        worldMap = session.worldMap,
                        renderer = renderer,
                    )
                    val result = bridge.run(step.scene.hutService(), step.scene, continuation)
                    terminal.writeScriptEvents(result.events, renderer, result.runtime)
                    when (result.status) {
                        TownServiceBridgeStatus.Finished -> session.finishHut(result.runtime)
                        TownServiceBridgeStatus.Waiting -> {
                            val nextContinuation = checkNotNull(result.continuation) {
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
                            WildernessPromptText.dungeonHandoffPending(step.handoff),
                            TerminalTextContext(runtime = session.runtime),
                        ),
                    )
                    session.startDungeonFromHandoff()
                }
                is WildernessSessionStep.Dungeon -> {
                    val result = DungeonSessionTerminalAdapter(terminal, renderer).runUntilBlocked(step.session)
                    val updated = session.replaceDungeonSession(result.session)
                    continueOrBlock(updated, result.status) { return it }
                }
                is WildernessSessionStep.Ydarg -> {
                    val result = YdargSessionTerminalAdapter(terminal, renderer).runUntilBlocked(step.session)
                    val updated = session.replaceYdargSession(result.session)
                    continueOrBlock(updated, result.status) { return it }
                }
                WildernessSessionStep.Explore -> {
                    writeExplore(session)
                    val keys = session.wilderness.keys
                    val choices = keys.exploreCommands().terminalChoices()
                    terminal.readChoice(choices)
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
                    terminal.readChoice(commands.terminalChoices())
                        .sessionOrWait(session, { return it }) { session.apply(it) }
                }
                is WildernessSessionStep.Town -> {
                    val result = TownSessionTerminalAdapter(terminal, renderer, serviceBridge).runUntilBlocked(step.session)
                    val updated = session.replaceTownSession(result.session)
                    continueOrBlock(updated, result.status) { return it }
                }
                is WildernessSessionStep.Battle -> {
                    val result = BattleSessionTerminalAdapter(terminal, renderer).runUntilBlocked(step.session)
                    val updated = session.replaceBattleSession(result.session)
                    continueOrBlock(updated, result.status) { return it }
                }
                is WildernessSessionStep.Camp -> {
                    val result = CampSessionTerminalAdapter(terminal, renderer).runUntilBlocked(step.session)
                    val updated = session.replaceCampSession(result.session)
                    continueOrBlock(updated, result.status) { return it }
                }
                is WildernessSessionStep.Hunt -> {
                    val result = HuntSessionTerminalAdapter(terminal, renderer).runUntilBlocked(step.session)
                    val updated = session.replaceHuntSession(result.session)
                    continueOrBlock(updated, result.status) { return it }
                }
                is WildernessSessionStep.InventoryItemUse -> {
                    val result = InventoryItemUseTerminalAdapter(terminal, renderer).runUntilBlocked(step.session)
                    val updated = session.replaceInventoryItemUseSession(result.session)
                    continueOrBlock(updated, result.status) { return it }
                }
                is WildernessSessionStep.Equipment -> {
                    val result = EquipmentPromptTerminalAdapter(terminal, renderer).runUntilBlocked(step.session)
                    val updated = session.replaceEquipmentSession(result.session)
                    continueOrBlock(updated, result.status) { return it }
                }
                is WildernessSessionStep.PriestSpell -> {
                    val result = PriestSpellTerminalAdapter(terminal, renderer, worldMap = session.worldMap)
                        .runUntilBlocked(step.session)
                    val updated = session.replacePriestSpellSession(result.session)
                    continueOrBlock(updated, result.status) { return it }
                }
                is WildernessSessionStep.Finished -> return finished(session)
            }
        }
        return TerminalAdapterResult(session, TerminalRunStatus.StepLimitReached)
    }

    private fun writeExplore(session: WildernessSession) {
        val point = (session.party.position as? WorldPosition.Wilderness)?.point
        val lines = buildList {
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

    private fun statusLine(session: WildernessSession, point: GridPoint?): String {
        val terrain = point?.let { session.worldMap.tileAt(it) } ?: ' '
        val position = point?.let { "${it.x}x${it.y}" } ?: "not in wilderness"
        return "Position: $position  Moves: ${session.party.resources.timeLeft}  Tile: $terrain"
    }
}

class BattleSessionTerminalAdapter(
    private val terminal: TerminalPort,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(),
) {
    fun run(initial: BattleSession): BattleSession =
        runUntilBlocked(initial).session

    fun runUntilBlocked(initial: BattleSession): TerminalAdapterResult<BattleSession> {
        var session = initial
        repeat(64) {
            session = when (val step = session.step) {
                is BattleSessionStep.Opening -> {
                    readOpening(step.prompt, session.battle.openingKeys)
                        .sessionOrWait(session, { return it }) { session.apply(BattleSessionInput.Opening(it)) }
                }
                is BattleSessionStep.RoundOpening -> {
                    readOpening(step.prompt, session.battle.openingKeys)
                        .sessionOrWait(session, { return it }) { session.apply(BattleSessionInput.Opening(it)) }
                }
                is BattleSessionStep.Message -> {
                    step.texts.forEach { text ->
                        terminal.write(renderer.lines(text, TerminalTextContext(runtime = session.runtime)))
                    }
                    session.apply(BattleSessionInput.Continue)
                }
                is BattleSessionStep.PlayerAction -> {
                    val choices = step.actions.map { action -> TerminalChoice(action, action.binding) }
                    terminal.writeBindingMenu(renderer, step.prompt, choices.map { it.binding })
                    terminal.readChoice(choices)
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

    private fun readKeyOffset(prompt: TextBlock, keys: List<Char>, offset: Char): TerminalInput<Int> {
        terminal.writeKeyMenu(renderer, prompt, keys)
        return terminal.readKey(keys).map { key -> key - offset }
    }
}
