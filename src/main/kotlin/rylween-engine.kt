// Backend-neutral engine boundary for executing or tracing DSL scripts.
//
// The Kotlin DSL should not care whether the implementation behind this port is
// the current C engine, a pure Kotlin runtime, a server process, or a test
// tracer. Keep requests typed and small so every backend can implement the same
// contract.

data class EngineRequest(
    val path: String,
    val args: List<String> = emptyList(),
)

interface RylweenEnginePort {
    fun effect(request: EngineRequest)
    fun readInt(request: EngineRequest): Int
    fun readString(request: EngineRequest): String
}

sealed interface EngineTraceEvent {
    data class NativeEffect(val request: EngineRequest) : EngineTraceEvent
    data class NativeInt(val request: EngineRequest, val result: Int) : EngineTraceEvent
    data class NativeString(val request: EngineRequest, val result: String) : EngineTraceEvent
    data class Screen(val text: TextBlock) : EngineTraceEvent
    data class Emit(val id: TextId) : EngineTraceEvent
    data class EmitIndexed(val pattern: TextPattern, val value: Int) : EngineTraceEvent
    data class WaitInput(val reason: String?) : EngineTraceEvent
    data class Pause(val ms: Int) : EngineTraceEvent
    data class SetFlag(val flag: String, val state: String) : EngineTraceEvent
    data class Menu(val prompt: TextBlock, val keys: List<Char>) : EngineTraceEvent
    data class AskMember(val prompt: TextBlock, val filter: MemberChoiceFilter) : EngineTraceEvent
    data class AskInventorySlot(
        val prompt: TextBlock,
        val firstKey: Char,
        val slots: IntRange,
        val filter: InventorySlotChoiceFilter,
    ) : EngineTraceEvent
    data class GrantReward(val reward: ScriptRewardModel) : EngineTraceEvent
    data class AchieveMilestone(val milestone: GameMilestoneId) : EngineTraceEvent
    data class Speak(val building: BuildingId) : EngineTraceEvent
    data class RunScene(val scene: SceneId, val bindings: Map<String, String>) : EngineTraceEvent
    data class Encounter(val monster: MonsterId, val mandatory: Boolean, val tier: EncounterTier?) : EngineTraceEvent
    data class Stop(val reason: String?, val signal: ScriptControlSignal) : EngineTraceEvent
    data class Break(val reason: String?, val signal: ScriptControlSignal) : EngineTraceEvent
    data class RandomItemOffer(val offer: RandomItemOfferModel) : EngineTraceEvent
    data class ShopService(val shop: ShopServiceModel) : EngineTraceEvent
    data class ShopSellQuote(val quote: ShopSellQuoteResult) : EngineTraceEvent
    data class ShopTransaction(val result: ShopTransactionResult) : EngineTraceEvent
    data class EquipmentAction(val result: EquipmentActionResult) : EngineTraceEvent
    object ConsumeTorch : EngineTraceEvent
}

class TraceEnginePort(
    private val ints: Map<EngineRequest, Int> = emptyMap(),
    private val strings: Map<EngineRequest, String> = emptyMap(),
) : RylweenEnginePort {
    private val mutableEvents = mutableListOf<EngineTraceEvent>()
    val events: List<EngineTraceEvent> get() = mutableEvents.toList()

    override fun effect(request: EngineRequest) {
        mutableEvents += EngineTraceEvent.NativeEffect(request)
    }

    override fun readInt(request: EngineRequest): Int =
        ints.getOrElse(request) { 0 }.also { result ->
            mutableEvents += EngineTraceEvent.NativeInt(request, result)
        }

    override fun readString(request: EngineRequest): String =
        strings.getOrElse(request) { "" }.also { result ->
            mutableEvents += EngineTraceEvent.NativeString(request, result)
        }

    internal fun trace(event: EngineTraceEvent) {
        mutableEvents += event
    }
}

class ScriptTraceRunner(
    private val engine: RylweenEnginePort,
) {
    private val intVars = mutableMapOf<String, Int>()
    private val stringVars = mutableMapOf<String, String>()

    fun run(ops: List<Op>): List<EngineTraceEvent> {
        runAll(ops)
        return (engine as? TraceEnginePort)?.events.orEmpty()
    }

    private fun run(op: Op): ScriptControlSignal =
        when (op) {
            is Op.Screen -> ScriptControlSignal.Continue.also { trace(EngineTraceEvent.Screen(op.text)) }
            is Op.Emit -> ScriptControlSignal.Continue.also { trace(EngineTraceEvent.Emit(op.text)) }
            is Op.EmitIndexed -> ScriptControlSignal.Continue.also {
                trace(EngineTraceEvent.EmitIndexed(op.pattern, eval(op.value)))
            }
            is Op.WaitInput -> ScriptControlSignal.Continue.also { trace(EngineTraceEvent.WaitInput(op.reason)) }
            is Op.Pause -> ScriptControlSignal.Continue.also { trace(EngineTraceEvent.Pause(op.millis)) }
            is Op.LetInt -> ScriptControlSignal.Continue.also { intVars[op.name] = eval(op.value) }
            is Op.BindText -> ScriptControlSignal.Continue.also { stringVars[op.name] = eval(op.value) }
            is Op.Set -> ScriptControlSignal.Continue
            is Op.Add -> ScriptControlSignal.Continue
            is Op.Sub -> ScriptControlSignal.Continue
            is Op.AddClamped -> ScriptControlSignal.Continue
            is Op.SetFlag -> ScriptControlSignal.Continue.also {
                trace(EngineTraceEvent.SetFlag(op.slug, op.nativeValue))
            }
            is Op.If -> runAll(if (eval(op.cond)) op.thenOps else op.elseOps)
            is Op.Loop -> ScriptControlSignal.StopScript.also {
                trace(EngineTraceEvent.Stop("trace runner does not execute loops", ScriptControlSignal.StopScript))
            }
            is Op.Break -> op.signal.also { trace(EngineTraceEvent.Break(op.reason, op.signal)) }
            is Op.Stop -> op.signal.also { trace(EngineTraceEvent.Stop(op.reason, op.signal)) }
            is Op.Switch ->
                op.cases.firstOrNull { it.match == eval(op.expr) || it.match == null }?.let { runAll(it.body) }
                    ?: ScriptControlSignal.Continue
            is Op.Menu -> ScriptControlSignal.Continue.also {
                trace(EngineTraceEvent.Menu(op.prompt, op.options.map { option -> option.key }))
            }
            is Op.AskMember -> ScriptControlSignal.Continue.also {
                trace(EngineTraceEvent.AskMember(op.prompt, op.filter))
            }
            is Op.AskInventorySlot -> ScriptControlSignal.Continue.also {
                trace(EngineTraceEvent.AskInventorySlot(op.prompt, op.firstKey, op.slots, op.filter))
            }
            is Op.AskMonster -> ScriptControlSignal.Continue
            is Op.ForEachMember -> ScriptControlSignal.StopScript.also {
                trace(EngineTraceEvent.Stop("trace runner does not iterate party members", ScriptControlSignal.StopScript))
            }
            is Op.ForEachMonster -> ScriptControlSignal.StopScript.also {
                trace(EngineTraceEvent.Stop("trace runner does not iterate monsters", ScriptControlSignal.StopScript))
            }
            is Op.Speak -> ScriptControlSignal.Continue.also {
                trace(EngineTraceEvent.Speak(op.building))
            }
            is Op.RunScene -> ScriptControlSignal.Continue.also {
                trace(EngineTraceEvent.RunScene(op.scene, op.bindings))
            }
            is Op.Encounter -> ScriptControlSignal.Continue.also {
                trace(EngineTraceEvent.Encounter(op.foe, op.mandatory, op.tier))
            }
            Op.ConsumeActiveItem -> ScriptControlSignal.Continue
            Op.ConsumeTorch -> ScriptControlSignal.Continue.also {
                trace(EngineTraceEvent.ConsumeTorch)
            }
            is Op.GrantReward -> ScriptControlSignal.Continue.also {
                trace(EngineTraceEvent.GrantReward(op.reward))
            }
            is Op.RandomItemOffer -> ScriptControlSignal.Continue.also {
                trace(EngineTraceEvent.RandomItemOffer(op.offer))
            }
            is Op.ShopService -> ScriptControlSignal.Continue.also {
                trace(EngineTraceEvent.ShopService(op.shop))
            }
            is Op.AchieveMilestone -> ScriptControlSignal.Continue.also {
                trace(EngineTraceEvent.AchieveMilestone(op.milestone))
            }
            is Op.NativeCall -> ScriptControlSignal.Continue.also {
                engine.effect(EngineRequest(op.path, op.args))
            }
        }

    private fun runAll(ops: List<Op>): ScriptControlSignal {
        ops.forEach { op ->
            val signal = run(op)
            if (signal != ScriptControlSignal.Continue) return signal
        }
        return ScriptControlSignal.Continue
    }

    private fun eval(ast: IntAst): Int =
        when (ast) {
            is IntAst.Lit -> ast.value
            is IntAst.Var -> intVars[ast.name] ?: 0
            is IntAst.Binary -> evalBinary(eval(ast.left), ast.op, eval(ast.right))
            is IntAst.Random -> eval(ast.lo)
            is IntAst.MemberField -> 0
            is IntAst.MonsterField -> 0
            is IntAst.RealmScalar -> 0
            is IntAst.PartyScalar -> 0
            is IntAst.NativeInt -> engine.readInt(EngineRequest(ast.path, ast.args))
        }

    private fun eval(ast: BoolAst): Boolean =
        when (ast) {
            is BoolAst.Cmp -> compare(eval(ast.left), ast.op, eval(ast.right))
            is BoolAst.Not -> !eval(ast.expr)
            is BoolAst.And -> eval(ast.left) && eval(ast.right)
            is BoolAst.Or -> eval(ast.left) || eval(ast.right)
            is BoolAst.FlagIs -> false
            is BoolAst.MemberFlag -> false
            is BoolAst.MonsterFlag -> false
        }

    private fun eval(ast: StrAst): String =
        when (ast) {
            is StrAst.NativeString -> engine.readString(EngineRequest(ast.path, ast.args))
        }

    private fun evalBinary(left: Int, op: String, right: Int): Int =
        when (op) {
            "add" -> left + right
            "sub" -> left - right
            "mul" -> left * right
            "div" -> if (right == 0) 0 else left / right
            else -> 0
        }

    private fun compare(left: Int, op: String, right: Int): Boolean =
        when (op) {
            "eq" -> left == right
            "ne" -> left != right
            "lt" -> left < right
            "lte" -> left <= right
            "gt" -> left > right
            "gte" -> left >= right
            else -> false
        }

    private fun trace(event: EngineTraceEvent) {
        (engine as? TraceEnginePort)?.trace(event)
    }
}

class ShopServiceTraceRunner(
    private val engine: TraceEnginePort,
) {
    fun run(
        initial: ShopServiceSession,
        inputs: List<ShopServiceInput>,
    ): ShopServiceSession {
        var session = initial
        render(session.step)
        inputs.forEach { input ->
            val previous = session
            session = session.apply(input)
            traceResult(previous, session)
            render(session.step)
        }
        return session
    }

    private fun ShopServiceSession.apply(input: ShopServiceInput): ShopServiceSession =
        when (input) {
            ShopServiceInput.ContinueIntro -> continueIntro()
            is ShopServiceInput.Main -> chooseMain(input.choice)
            is ShopServiceInput.BuyItem -> chooseBuyItem(input.item)
            is ShopServiceInput.Quantity -> enterQuantity(input.quantity)
            is ShopServiceInput.ConfirmPurchase -> confirmPurchase(input.accept)
            is ShopServiceInput.DropSlot -> chooseDropSlot(input.slot)
            is ShopServiceInput.SellSlot -> chooseSellSlot(input.slot, input.bargainRolls)
            is ShopServiceInput.ConfirmSale -> confirmSale(input.accept)
        }

    private fun traceResult(previous: ShopServiceSession, session: ShopServiceSession) {
        session.lastSellQuote
            ?.takeUnless { it == previous.lastSellQuote }
            ?.let { engine.trace(EngineTraceEvent.ShopSellQuote(it)) }
        session.lastTransaction
            ?.takeUnless { it == previous.lastTransaction }
            ?.let { engine.trace(EngineTraceEvent.ShopTransaction(it)) }
    }

    private fun render(step: ShopServiceStep) {
        when (step) {
            is ShopServiceStep.Intro -> screen(step.text)
            is ShopServiceStep.MainMenu -> menu(
                step.text,
                buildList {
                    if (step.canBuy) add('B')
                    if (step.canSell) add('S')
                    add('L')
                },
            )
            is ShopServiceStep.BuyItem -> menu(
                step.prompt,
                step.catalogs.flatMap { catalog -> catalog.pages.flatMap { page -> page.entries } }
                    .map { entry -> entry.key },
            )
            is ShopServiceStep.Quantity -> screen(step.prompt)
            is ShopServiceStep.ConfirmPurchase -> menu(step.prompt, listOf('P', 'N'))
            is ShopServiceStep.DropSlot -> inventorySlot(step.prompt)
            is ShopServiceStep.SellSlot -> inventorySlot(step.prompt)
            is ShopServiceStep.ConfirmSale -> menu(step.prompt, listOf('Y', 'N'))
            is ShopServiceStep.Finished -> screen(step.text)
        }
    }

    private fun screen(text: TextBlock) {
        engine.trace(EngineTraceEvent.Screen(text))
    }

    private fun menu(prompt: TextBlock, keys: List<Char>) {
        engine.trace(EngineTraceEvent.Menu(prompt, keys))
    }

    private fun inventorySlot(prompt: TextBlock) {
        engine.trace(
            EngineTraceEvent.AskInventorySlot(
                prompt = prompt,
                firstKey = 'A',
                slots = 1..InventorySaveSlotMax,
                filter = InventorySlotChoiceFilter.NonEmpty,
            ),
        )
    }
}

class EquipmentPromptTraceRunner(
    private val engine: TraceEnginePort,
) {
    fun run(
        initial: EquipmentPromptSession,
        inputs: List<EquipmentPromptInput>,
    ): EquipmentPromptSession {
        var session = initial
        renderEquipmentStep(engine, session.step)
        inputs.forEach { input ->
            session = session.apply(input)
            (session.step as? EquipmentPromptStep.Finished)?.let {
                engine.trace(EngineTraceEvent.EquipmentAction(it.result))
            }
            renderEquipmentStep(engine, session.step)
        }
        return session
    }
}

class TownLeaveEquipmentTraceRunner(
    private val engine: TraceEnginePort,
) {
    fun run(
        initial: TownLeaveEquipmentSession,
        inputs: List<TownLeaveEquipmentInput>,
    ): TownLeaveEquipmentSession {
        var session = initial
        render(session.step)
        inputs.forEach { input ->
            val previousResult = session.lastResult
            session = session.apply(input)
            session.lastResult
                ?.takeUnless { it == previousResult }
                ?.let { engine.trace(EngineTraceEvent.EquipmentAction(it)) }
            render(session.step)
        }
        return session
    }

    private fun render(step: TownLeaveEquipmentStep) {
        when (step) {
            is TownLeaveEquipmentStep.ReadyQuestion -> engine.trace(EngineTraceEvent.Menu(step.prompt, listOf('Y', 'N')))
            is TownLeaveEquipmentStep.Equipment -> renderEquipmentStep(engine, step.session.step)
            is TownLeaveEquipmentStep.Finished -> Unit
        }
    }
}

interface TerminalPort {
    fun write(lines: List<String>)
    fun readLine(): String?
}

data class TerminalTextContext(
    val shopPrice: Int? = null,
    val shopCarryCap: Int? = null,
    val offeredItemName: String? = null,
    val offeredItemPrice: Int? = null,
)

data class TerminalInputRejection(
    val input: String,
    val expected: List<String>,
)

sealed interface TerminalRunStatus {
    object Finished : TerminalRunStatus
    data class Waiting(val rejection: TerminalInputRejection? = null) : TerminalRunStatus
    object StepLimitReached : TerminalRunStatus
}

data class TerminalAdapterResult<S>(
    val session: S,
    val status: TerminalRunStatus,
)

class TerminalTextRenderer(
    private val references: Map<String, List<String>> = emptyMap(),
) {
    fun lines(block: TextBlock, context: TerminalTextContext = TerminalTextContext()): List<String> =
        when (block) {
            is TextBlock.Inline -> block.lines
            is TextBlock.Ref -> references[block.id.id] ?: listOf("[${block.id.id}]")
            is TextBlock.Structured -> block.lines.map { line ->
                line.parts.joinToString(separator = "") { part -> render(part, context) }
            }
        }

    private fun render(part: TextPart, context: TerminalTextContext): String =
        when (part) {
            is TextPart.Literal -> part.text
            is TextPart.IntValue -> render(part.value)
            is TextPart.StringValue -> "{string}"
            is TextPart.RoleName -> "{${part.role.slug}.name}"
            is TextPart.RoleLocation -> "{${part.role.slug}.location}"
            is TextPart.TownName -> part.town.slug
            is TextPart.FuzzyTownLocation -> "${part.town.slug} near +/-${part.radius}"
            TextPart.OfferedItemName -> context.offeredItemName ?: "{item_name}"
            TextPart.OfferedItemPrice -> context.offeredItemPrice?.toString() ?: "{item_price}"
            TextPart.ShopPrice -> context.shopPrice?.toString() ?: "{price}"
            TextPart.ShopCarryCap -> context.shopCarryCap?.toString() ?: "{cap}"
        }

    private fun render(ast: IntAst): String =
        when (ast) {
            is IntAst.Lit -> ast.value.toString()
            else -> "{int}"
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
        writeMenu(prompt, keys)
        return terminal.readKey(keys).map { it - 'A' + 1 }
    }

    private fun readMember(
        party: PartyState,
        prompt: TextBlock,
        filter: MemberChoiceFilter,
    ): TerminalInput<Int> {
        val choices = party.memberChoices(filter)
        writeMenu(prompt, choices.map { it.digitToChar() })
        return terminal.readMemberNumber(choices)
    }

    private fun writeMenu(block: TextBlock, keys: List<Char>) {
        terminal.write(renderer.lines(block) + "Options: ${keys.joinToString("/")}")
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
                    writeMenu(step.prompt, listOf('Y', 'N'))
                    when (val input = terminal.readKey(listOf('Y', 'N'))) {
                        is TerminalInput.Value ->
                            session.apply(TownLeaveEquipmentInput.ReadyAnswer(input.value == 'Y'))
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
                writeMenu(step.prompt, keys)
                terminal.readKey(keys).map { EquipmentPromptInput.InventorySlot(it - 'A' + 1) }
            }
            is EquipmentPromptStep.ChooseMember -> {
                val choices = session.party.memberChoices(step.filter)
                writeMenu(step.prompt, choices.map { it.digitToChar() })
                terminal.readMemberNumber(choices).map { EquipmentPromptInput.Member(it) }
            }
            is EquipmentPromptStep.Finished -> TerminalInput.Unavailable
        }

    private fun writeMenu(block: TextBlock, keys: List<Char>) {
        terminal.write(renderer.lines(block) + "Options: ${keys.joinToString("/")}")
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
                    val keys = buildList {
                        if (step.canBuy) add('B')
                        if (step.canSell) add('S')
                        add('L')
                    }
                    writeMenu(step.text, keys)
                    when (val input = terminal.readKey(keys)) {
                        is TerminalInput.Value -> when (input.value) {
                            'B' -> session.chooseMain(ShopMainChoice.Buy)
                            'S' -> session.chooseMain(ShopMainChoice.Sell)
                            else -> session.chooseMain(ShopMainChoice.Leave)
                        }
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                is ShopServiceStep.BuyItem -> {
                    val entries = step.catalogs.flatMap { catalog -> catalog.pages.flatMap { page -> page.entries } }
                    val keys = entries.map { it.key }
                    terminal.write(renderer.lines(step.prompt) + entries.map { "(${it.key}) ${it.label ?: it.item.slug}" })
                    val key = when (val input = terminal.readKey(keys)) {
                        is TerminalInput.Value -> input.value
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                    val item = checkNotNull(entries.firstOrNull { it.key == key }) { "missing buy item for key $key" }.item
                    session.chooseBuyItem(item)
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
                    writeMenu(step.prompt, listOf('P', 'N'))
                    when (val input = terminal.readKey(listOf('P', 'N'))) {
                        is TerminalInput.Value -> session.confirmPurchase(input.value == 'P')
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                is ShopServiceStep.DropSlot -> {
                    write(step.prompt)
                    when (val input = readInventorySlot()) {
                        is TerminalInput.Value -> session.chooseDropSlot(input.value)
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                is ShopServiceStep.SellSlot -> {
                    write(step.prompt)
                    when (val input = readInventorySlot()) {
                        is TerminalInput.Value -> session.chooseSellSlot(input.value)
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                is ShopServiceStep.ConfirmSale -> {
                    writeMenu(step.prompt, listOf('Y', 'N'), TerminalTextContext(shopPrice = step.quote.price))
                    when (val input = terminal.readKey(listOf('Y', 'N'))) {
                        is TerminalInput.Value -> session.confirmSale(input.value == 'Y')
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

    private fun writeMenu(
        block: TextBlock,
        keys: List<Char>,
        context: TerminalTextContext = TerminalTextContext(),
    ) {
        terminal.write(renderer.lines(block, context) + "Options: ${keys.joinToString("/")}")
    }

    private fun readInventorySlot(): TerminalInput<Int> =
        terminal.readKey((0 until InventorySaveSlotMax).map { 'A' + it })
            .map { it - 'A' + 1 }
}

class TownSessionTerminalAdapter(
    private val terminal: TerminalPort,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(),
) {
    fun run(initial: TownSession): TownSession =
        runUntilBlocked(initial).session

    fun runUntilBlocked(initial: TownSession): TerminalAdapterResult<TownSession> {
        var session = initial
        repeat(512) {
            session = when (val step = session.step) {
                is TownSessionStep.Menu -> {
                    val keys = step.actions.map { it.key }
                    writeTownMenu(step)
                    when (val input = terminal.readKey(keys)) {
                        is TerminalInput.Value -> session.apply(TownSessionInput.MenuKey(input.value))
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                is TownSessionStep.Notice -> {
                    terminal.write(renderer.lines(step.text))
                    session.continueNotice()
                }
                is TownSessionStep.Shop -> {
                    val result = ShopServiceTerminalAdapter(terminal, renderer).runUntilBlocked(step.session)
                    val updated = session.replaceShopSession(result.session)
                    when (result.status) {
                        TerminalRunStatus.Finished -> updated
                        TerminalRunStatus.StepLimitReached ->
                            return TerminalAdapterResult(updated, TerminalRunStatus.StepLimitReached)
                        is TerminalRunStatus.Waiting ->
                            return TerminalAdapterResult(updated, result.status)
                    }
                }
                is TownSessionStep.LeaveEquipment -> {
                    val result = TownLeaveEquipmentTerminalAdapter(terminal, renderer).runUntilBlocked(step.session)
                    val updated = session.replaceLeaveEquipmentSession(result.session)
                    when (result.status) {
                        TerminalRunStatus.Finished -> updated
                        TerminalRunStatus.StepLimitReached ->
                            return TerminalAdapterResult(updated, TerminalRunStatus.StepLimitReached)
                        is TerminalRunStatus.Waiting ->
                            return TerminalAdapterResult(updated, result.status)
                    }
                }
                is TownSessionStep.NativeService -> {
                    terminal.write(listOf("Run scene: ${step.service.scene.slug}"))
                    return waiting(session)
                }
                is TownSessionStep.Finished -> return finished(session)
            }
        }
        return TerminalAdapterResult(session, TerminalRunStatus.StepLimitReached)
    }

    private fun writeTownMenu(step: TownSessionStep.Menu) {
        terminal.write(
            renderer.lines(TownPromptText.menu(step.town)) +
                step.actions.map { action -> "(${action.key}) ${action.label()}" },
        )
    }

    private fun TownMenuActionModel.label(): String =
        if (endsMenu) {
            "Leave"
        } else {
            checkNotNull(building).slug.replaceFirstChar { it.uppercaseChar() }
        }
}

private sealed interface TerminalInput<out T> {
    data class Value<T>(val value: T) : TerminalInput<T>
    data class Rejected(val rejection: TerminalInputRejection) : TerminalInput<Nothing>
    object Unavailable : TerminalInput<Nothing>
}

private fun <T, R> TerminalInput<T>.map(transform: (T) -> R): TerminalInput<R> =
    when (this) {
        is TerminalInput.Value -> TerminalInput.Value(transform(value))
        is TerminalInput.Rejected -> this
        TerminalInput.Unavailable -> TerminalInput.Unavailable
    }

private fun TerminalPort.readKey(allowed: List<Char>): TerminalInput<Char> {
    val raw = readLine() ?: return TerminalInput.Unavailable
    val key = raw.trim().firstOrNull()?.uppercaseChar()
    return if (key != null && key in allowed) {
        TerminalInput.Value(key)
    } else {
        TerminalInput.Rejected(TerminalInputRejection(raw, allowed.map { it.toString() }))
    }
}

private fun TerminalPort.readInt(): TerminalInput<Int> {
    val raw = readLine() ?: return TerminalInput.Unavailable
    return raw.trim().toIntOrNull()
        ?.let { TerminalInput.Value(it) }
        ?: TerminalInput.Rejected(TerminalInputRejection(raw, listOf("integer")))
}

private fun TerminalPort.readMemberNumber(allowed: List<Int>): TerminalInput<Int> {
    val raw = readLine() ?: return TerminalInput.Unavailable
    val memberNumber = raw.trim().toIntOrNull()
    return if (memberNumber != null && memberNumber in allowed) {
        TerminalInput.Value(memberNumber)
    } else {
        TerminalInput.Rejected(TerminalInputRejection(raw, allowed.map { it.toString() }))
    }
}

private fun <S> finished(session: S): TerminalAdapterResult<S> =
    TerminalAdapterResult(session, TerminalRunStatus.Finished)

private fun <S> waiting(
    session: S,
    rejection: TerminalInputRejection? = null,
): TerminalAdapterResult<S> =
    TerminalAdapterResult(session, TerminalRunStatus.Waiting(rejection))

private fun renderEquipmentStep(engine: TraceEnginePort, step: EquipmentPromptStep) {
    when (step) {
        is EquipmentPromptStep.ChooseInventorySlot -> engine.trace(
            EngineTraceEvent.AskInventorySlot(
                prompt = step.prompt,
                firstKey = 'A',
                slots = 1..InventorySaveSlotMax,
                filter = step.filter,
            ),
        )
        is EquipmentPromptStep.ChooseMember -> engine.trace(EngineTraceEvent.AskMember(step.prompt, step.filter))
        is EquipmentPromptStep.Finished -> Unit
    }
}

private fun PartyState.inventoryKeys(filter: InventorySlotChoiceFilter): List<Char> =
    inventory
        .filter { slot ->
            slot.slot in 1..InventorySaveSlotMax && when (filter) {
                InventorySlotChoiceFilter.Any -> true
                InventorySlotChoiceFilter.NonEmpty -> slot.item != null
                InventorySlotChoiceFilter.Empty -> slot.item == null
            }
        }
        .map { slot -> 'A' + (slot.slot - 1) }

private fun PartyState.memberChoices(filter: MemberChoiceFilter): List<Int> =
    members
        .mapIndexedNotNull { index, member ->
            (index + 1).takeIf { member.matches(filter) }
        }

private fun PartyMemberState.matches(filter: MemberChoiceFilter): Boolean =
    when (filter) {
        MemberChoiceFilter.Any -> true
        MemberChoiceFilter.AliveOnly -> !dead
        MemberChoiceFilter.DeadOnly -> dead
        MemberChoiceFilter.SpellcasterOnly -> !dead && pcClass in setOf(PcClass.Mage, PcClass.Priest)
    }
