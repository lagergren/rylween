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
                    when (val input = terminal.readChoice(choices)) {
                        is TerminalInput.Value -> session.apply(CampSessionInput.GuardAnswer(input.value))
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                is CampSessionStep.GuardMember -> {
                    val keys = session.party.members.indices.map { (it + 1).digitToChar() }
                    terminal.writeKeyMenu(renderer, step.prompt, keys)
                    when (val input = terminal.readMemberNumber(session.party.members.indices.map { it + 1 })) {
                        is TerminalInput.Value -> session.apply(CampSessionInput.GuardMember(input.value))
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
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
                    when (val input = terminal.readMemberNumber(choices)) {
                        is TerminalInput.Value -> session.apply(HuntSessionInput.Member(input.value))
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
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
                is EquipmentPromptStep.ChooseInventorySlot -> when (
                    val input = readInventorySlot(session.party, step.prompt, step.filter)
                ) {
                    is TerminalInput.Value -> session.apply(EquipmentPromptInput.InventorySlot(input.value))
                    is TerminalInput.Rejected -> return waiting(session, input.rejection)
                    TerminalInput.Unavailable -> return waiting(session)
                }
                is EquipmentPromptStep.ChooseMember -> when (
                    val input = readMember(session.party, step.prompt, step.filter)
                ) {
                    is TerminalInput.Value -> session.apply(EquipmentPromptInput.Member(input.value))
                    is TerminalInput.Rejected -> return waiting(session, input.rejection)
                    TerminalInput.Unavailable -> return waiting(session)
                }
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
                    when (val input = terminal.readKey(step.choices.map { it.key })) {
                        is TerminalInput.Value -> {
                            val slot = checkNotNull(step.choices.firstOrNull { it.key == input.value }).slot
                            session.apply(InventoryItemUseInput.InventorySlot(slot))
                        }
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
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
                    when (val input = terminal.readMemberNumber(step.casterKeys.map { it.digitToInt() })) {
                        is TerminalInput.Value -> session.apply(PriestSpellInput.Caster(input.value))
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                is PriestSpellStep.ChooseSpell -> {
                    terminal.writeKeyMenu(renderer, step.prompt, step.spellKeys, session.runtime)
                    when (val input = terminal.readKey(step.spellKeys)) {
                        is TerminalInput.Value -> session.apply(PriestSpellInput.SpellSlot(input.value.digitToInt()))
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
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
                    when (val input = terminal.readYesNo()) {
                        is TerminalInput.Value ->
                            session.apply(TownLeaveEquipmentInput.ReadyAnswer(input.value))
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                is TownLeaveEquipmentStep.Equipment -> when (val input = readEquipmentInput(step.session)) {
                    is TerminalInput.Value -> session.apply(TownLeaveEquipmentInput.Equipment(input.value))
                    is TerminalInput.Rejected -> return waiting(session, input.rejection)
                    TerminalInput.Unavailable -> return waiting(session)
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
                    when (val input = terminal.readChoice(choices)) {
                        is TerminalInput.Value -> session.chooseMain(input.value)
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
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
                    val entry = when (val input = terminal.readChoice(entries.map { TerminalChoice(it, it.binding) })) {
                        is TerminalInput.Value -> input.value
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                    session.withFeedback { chooseBuyItem(entry.item) }
                }
                is ShopServiceStep.Quantity -> {
                    write(step.prompt)
                    when (val input = terminal.readInt()) {
                        is TerminalInput.Value -> session.enterQuantity(input.value)
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
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
                    when (val input = terminal.readChoice(choices)) {
                        is TerminalInput.Value -> session.withFeedback { confirmPurchase(input.value) }
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                is ShopServiceStep.DropSlot -> {
                    write(step.prompt)
                    when (val input = readInventorySlot()) {
                        is TerminalInput.Value -> session.withFeedback { chooseDropSlot(input.value) }
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                is ShopServiceStep.SellSlot -> {
                    write(step.prompt)
                    when (val input = readInventorySlot()) {
                        is TerminalInput.Value -> session.withFeedback { chooseSellSlot(input.value) }
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
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
                    when (val input = terminal.readChoice(choices)) {
                        is TerminalInput.Value -> session.withFeedback { confirmSale(input.value) }
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
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
                    when (val input = terminal.readChoice(choices)) {
                        is TerminalInput.Value -> session.apply(TownSessionInput.MenuKey(input.value.key))
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
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
                        battleContent = RuntimeContent(
                            itemRecords = session.itemRecords,
                            realm = session.realmContent,
                        ),
                        renderer = renderer,
                    )
                    val result = bridge.run(step.service, step.scene, continuation)
                    writeBridgeEvents(result.events, result.runtime)
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

    private fun writeBridgeEvents(events: List<EngineTraceEvent>, runtime: RuntimeSessionState) {
        val context = TerminalTextContext(runtime = runtime)
        events.forEach { event ->
            if (terminal.writeEngineEvent(event, renderer, context)) {
                return@forEach
            }
            if (terminal.writePauseEvent(event, renderer, context)) {
                return@forEach
            }
            when (event) {
                is EngineTraceEvent.View -> Unit
                is EngineTraceEvent.Screen -> terminal.write(renderer.lines(event.text, context))
                is EngineTraceEvent.Emit -> terminal.write(listOf("[${event.id.id}]"))
                is EngineTraceEvent.EmitIndexed -> terminal.write(listOf("[${event.pattern.pattern}: ${event.value}]"))
                is EngineTraceEvent.Menu -> terminal.writeKeyPrompt(renderer, event.prompt, event.keys, context)
                is EngineTraceEvent.AskKey -> terminal.writeBindingPrompt(renderer, event.prompt, event.bindings, context)
                is EngineTraceEvent.AskText -> terminal.write(renderer.lines(event.prompt, context))
                is EngineTraceEvent.AskMember -> terminal.write(renderer.lines(event.prompt, context))
                is EngineTraceEvent.AskInventorySlot -> terminal.write(renderer.lines(event.prompt, context))
                is EngineTraceEvent.NativeEffect -> terminal.write(listOf("Native: ${event.request.path}"))
                is EngineTraceEvent.NativeInt -> terminal.write(listOf("Native int: ${event.request.path} = ${event.result}"))
                is EngineTraceEvent.NativeString ->
                    terminal.write(listOf("Native string: ${event.request.path} = ${event.result}"))
                is EngineTraceEvent.RunScene -> terminal.write(listOf("Scene: ${event.scene.slug}"))
                is EngineTraceEvent.DungeonHandoff ->
                    terminal.write(
                        renderer.lines(WildernessPromptText.dungeonHandoffPending(event.handoff), context),
                    )
                is EngineTraceEvent.TeleportParty,
                is EngineTraceEvent.DiscoverCave -> Unit
                is EngineTraceEvent.Stop -> terminal.write(listOf(event.reason ?: "Stop"))
                is EngineTraceEvent.Break -> terminal.write(listOf(event.reason ?: "Break"))
                is EngineTraceEvent.TownService -> terminal.write(listOf("Service: ${event.service.building.slug}"))
                is EngineTraceEvent.WaitInput,
                is EngineTraceEvent.Pause,
                is EngineTraceEvent.SetFlag,
                is EngineTraceEvent.AppendJournal,
                is EngineTraceEvent.GrantReward,
                is EngineTraceEvent.AchieveMilestone,
                is EngineTraceEvent.Speak,
                is EngineTraceEvent.Encounter,
                is EngineTraceEvent.RandomItemOffer,
                is EngineTraceEvent.ShopService,
                is EngineTraceEvent.ShopSellQuote,
                is EngineTraceEvent.ShopTransaction,
                is EngineTraceEvent.EquipmentAction,
                is EngineTraceEvent.ConsumeInventoryPool,
                EngineTraceEvent.ConsumeTorch -> Unit
            }
        }
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
                    when (val input = terminal.readChoice(choices)) {
                        is TerminalInput.Value -> session.apply(input.value)
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
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
                    when (val input = terminal.readChoice(commands.terminalChoices())) {
                        is TerminalInput.Value ->
                            session.apply(input.value)
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
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
                    when (val input = terminal.readChoice(commands.terminalChoices())) {
                        is TerminalInput.Value ->
                            session.apply(input.value)
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
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
                    writeBridgeEvents(result.events, result.runtime)
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
                    writeBridgeEvents(result.events, result.runtime)
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
                    when (val input = terminal.readChoice(choices)) {
                        is TerminalInput.Value -> session.apply(input.value)
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                is WildernessSessionStep.Notice -> {
                    terminal.write(renderer.lines(step.text, TerminalTextContext(runtime = session.runtime)))
                    session.continueNotice()
                }
                is WildernessSessionStep.QuitConfirm -> {
                    val keys = session.wilderness.keys
                    val commands = keys.quitConfirmCommands()
                    terminal.writeBindingMenu(renderer, step.prompt, commands.commandBindings())
                    when (val input = terminal.readChoice(commands.terminalChoices())) {
                        is TerminalInput.Value ->
                            session.apply(input.value)
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
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

    private fun writeBridgeEvents(events: List<EngineTraceEvent>, runtime: RuntimeSessionState) {
        val context = TerminalTextContext(runtime = runtime)
        events.forEach { event ->
            if (terminal.writeEngineEvent(event, renderer, context)) {
                return@forEach
            }
            if (terminal.writePauseEvent(event, renderer, context)) {
                return@forEach
            }
            when (event) {
                is EngineTraceEvent.View -> Unit
                is EngineTraceEvent.Screen -> terminal.write(renderer.lines(event.text, context))
                is EngineTraceEvent.Emit -> terminal.write(listOf("[${event.id.id}]"))
                is EngineTraceEvent.EmitIndexed -> terminal.write(listOf("[${event.pattern.pattern}: ${event.value}]"))
                is EngineTraceEvent.Menu -> terminal.writeKeyPrompt(renderer, event.prompt, event.keys, context)
                is EngineTraceEvent.AskKey -> terminal.writeBindingPrompt(renderer, event.prompt, event.bindings, context)
                is EngineTraceEvent.AskText -> terminal.write(renderer.lines(event.prompt, context))
                is EngineTraceEvent.AskMember -> terminal.write(renderer.lines(event.prompt, context))
                is EngineTraceEvent.AskInventorySlot -> terminal.write(renderer.lines(event.prompt, context))
                is EngineTraceEvent.NativeEffect -> terminal.write(listOf("Native: ${event.request.path}"))
                is EngineTraceEvent.NativeInt -> terminal.write(listOf("Native int: ${event.request.path} = ${event.result}"))
                is EngineTraceEvent.NativeString ->
                    terminal.write(listOf("Native string: ${event.request.path} = ${event.result}"))
                is EngineTraceEvent.RunScene -> terminal.write(listOf("Scene: ${event.scene.slug}"))
                is EngineTraceEvent.DungeonHandoff -> Unit
                is EngineTraceEvent.TeleportParty,
                is EngineTraceEvent.DiscoverCave -> Unit
                is EngineTraceEvent.Stop -> terminal.write(listOf(event.reason ?: "Stop"))
                is EngineTraceEvent.Break -> terminal.write(listOf(event.reason ?: "Break"))
                is EngineTraceEvent.TownService -> Unit
                is EngineTraceEvent.WaitInput,
                is EngineTraceEvent.Pause,
                is EngineTraceEvent.SetFlag,
                is EngineTraceEvent.AppendJournal,
                is EngineTraceEvent.GrantReward,
                is EngineTraceEvent.AchieveMilestone,
                is EngineTraceEvent.Speak,
                is EngineTraceEvent.Encounter,
                is EngineTraceEvent.RandomItemOffer,
                is EngineTraceEvent.ShopService,
                is EngineTraceEvent.ShopSellQuote,
                is EngineTraceEvent.ShopTransaction,
                is EngineTraceEvent.EquipmentAction,
                is EngineTraceEvent.ConsumeInventoryPool,
                EngineTraceEvent.ConsumeTorch -> Unit
            }
        }
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
                    val choices = battleOpeningChoices(session.battle.openingKeys)
                    terminal.writeBindingMenu(renderer, step.prompt, choices.map { it.binding })
                    when (val input = terminal.readChoice(choices)) {
                        is TerminalInput.Value -> session.apply(BattleSessionInput.Opening(input.value))
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                is BattleSessionStep.RoundOpening -> {
                    val choices = battleOpeningChoices(session.battle.openingKeys)
                    terminal.writeBindingMenu(renderer, step.prompt, choices.map { it.binding })
                    when (val input = terminal.readChoice(choices)) {
                        is TerminalInput.Value -> session.apply(BattleSessionInput.Opening(input.value))
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
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
                    when (val input = terminal.readChoice(choices)) {
                        is TerminalInput.Value -> session.apply(
                            BattleSessionInput.PlayerAction(input.value.kind),
                        )
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                is BattleSessionStep.MonsterTarget -> {
                    terminal.writeKeyMenu(renderer, step.prompt, step.targetKeys)
                    when (val input = terminal.readKey(step.targetKeys)) {
                        is TerminalInput.Value -> session.apply(BattleSessionInput.TargetMonster(input.value - 'A'))
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                is BattleSessionStep.ScriptMonsterTarget -> {
                    terminal.writeKeyMenu(renderer, step.prompt, step.targetKeys)
                    when (val input = terminal.readKey(step.targetKeys)) {
                        is TerminalInput.Value -> session.apply(BattleSessionInput.TargetMonster(input.value - 'A'))
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                is BattleSessionStep.MemberTarget -> {
                    terminal.writeKeyMenu(renderer, step.prompt, step.targetKeys)
                    when (val input = terminal.readKey(step.targetKeys)) {
                        is TerminalInput.Value -> session.apply(BattleSessionInput.TargetMember(input.value - '1'))
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                is BattleSessionStep.MagicItemSlot -> {
                    terminal.writeKeyMenu(renderer, step.prompt, step.slotKeys)
                    when (val input = terminal.readKey(step.slotKeys)) {
                        is TerminalInput.Value -> session.apply(BattleSessionInput.InventorySlot(input.value - 'A' + 1))
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                is BattleSessionStep.SpellChoice -> {
                    terminal.writeKeyMenu(renderer, step.prompt, step.spellKeys)
                    when (val input = terminal.readKey(step.spellKeys)) {
                        is TerminalInput.Value -> session.apply(BattleSessionInput.SpellSlot(input.value - '0'))
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
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
}
