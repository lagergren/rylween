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
    data class TownService(val service: BuildingServiceModel, val scene: SceneModel) : EngineTraceEvent
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

    private val evaluator = object : IntAstEvaluator() {
        override fun local(name: String): Int = intVars[name] ?: 0
        override fun random(lo: Int, hi: Int): Int = lo
        override fun memberField(member: Int, field: String): Int = 0
        override fun monsterField(monster: Int, field: String): Int = 0
        override fun realmScalar(name: String): Int = 0
        override fun partyScalar(name: String): Int = 0
        override fun nativeInt(path: String, args: List<String>): Int =
            engine.readInt(EngineRequest(path, args))
        override fun flagIs(slug: String, native: String): Boolean = false
        override fun memberFlag(member: Int, name: String): Boolean = false
        override fun monsterFlag(monster: Int, name: String): Boolean = false
    }

    private fun eval(ast: IntAst): Int = evaluator.int(ast)

    private fun eval(ast: BoolAst): Boolean = evaluator.bool(ast)

    private fun eval(ast: StrAst): String =
        when (ast) {
            is StrAst.NativeString -> engine.readString(EngineRequest(ast.path, ast.args))
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
    val runtime: RuntimeSessionState? = null,
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

sealed interface TownServiceBridgeStatus {
    object Finished : TownServiceBridgeStatus
    object Waiting : TownServiceBridgeStatus
}

data class TownServiceBridgeResult(
    val runtime: RuntimeSessionState,
    val status: TownServiceBridgeStatus,
    val events: List<EngineTraceEvent> = emptyList(),
    val continuation: TownServiceContinuation? = null,
    val rejection: TerminalInputRejection? = null,
) {
    val party: PartyState get() = runtime.activeParty
    val realm: RealmState get() = runtime.realm
}

interface TownServiceBridge {
    fun run(
        service: BuildingServiceModel,
        scene: SceneModel,
        continuation: TownServiceContinuation,
    ): TownServiceBridgeResult
}

class TraceTownServiceBridge(
    private val engine: TraceEnginePort = TraceEnginePort(),
) : TownServiceBridge {
    override fun run(
        service: BuildingServiceModel,
        scene: SceneModel,
        continuation: TownServiceContinuation,
    ): TownServiceBridgeResult {
        val eventStart = engine.events.size
        engine.trace(EngineTraceEvent.TownService(service, scene))
        ScriptTraceRunner(engine).run(scene.ops)
        return TownServiceBridgeResult(
            runtime = continuation.runtime,
            status = TownServiceBridgeStatus.Finished,
            events = engine.events.drop(eventStart),
        )
    }
}

class StatefulTownServiceBridge(
    private val terminal: TerminalPort,
    private val engine: TraceEnginePort = TraceEnginePort(),
    private val speakContent: SpeakRuntimeContent = SpeakRuntimeContent(),
) : TownServiceBridge {
    override fun run(
        service: BuildingServiceModel,
        scene: SceneModel,
        continuation: TownServiceContinuation,
    ): TownServiceBridgeResult {
        val eventStart = engine.events.size
        engine.trace(EngineTraceEvent.TownService(service, scene))
        val result = StatefulScriptServiceRunner(terminal, engine, scene.ops, continuation, speakContent).run()
        return TownServiceBridgeResult(
            runtime = result.runtime,
            status = result.status,
            events = engine.events.drop(eventStart),
            continuation = result.continuation,
            rejection = result.rejection,
        )
    }
}

private data class StatefulScriptResult(
    val runtime: RuntimeSessionState,
    val status: TownServiceBridgeStatus,
    val continuation: TownServiceContinuation? = null,
    val rejection: TerminalInputRejection? = null,
) {
    val party: PartyState get() = runtime.activeParty
}

private class StatefulScriptServiceRunner(
    private val terminal: TerminalPort,
    private val engine: TraceEnginePort,
    private val rootOps: List<Op>,
    continuation: TownServiceContinuation,
    private val speakContent: SpeakRuntimeContent,
) {
    private var runtime = continuation.runtime
    private var party = continuation.party
    private val locals = continuation.locals.toMutableMap()
    private val realmInts = continuation.realmScalars.toMutableMap()
    private val stack = continuation.stack.toMutableList()
    private var speakSession = continuation.speakSession
    private var randomItemOfferSession = continuation.randomItemOfferSession
    private var rewardGrantSession = continuation.rewardGrantSession
    private var rejection: TerminalInputRejection? = null
    private var waiting = false
    private var opBudget = 4096

    fun run(): StatefulScriptResult {
        runUntilBlockedOrFinished()
        val nextRuntime = currentRuntime()
        val nextContinuation = if (waiting) continuation(nextRuntime) else null
        return StatefulScriptResult(
            runtime = nextRuntime,
            status = if (waiting) TownServiceBridgeStatus.Waiting else TownServiceBridgeStatus.Finished,
            continuation = nextContinuation,
            rejection = rejection,
        )
    }

    private fun runUntilBlockedOrFinished() {
        while (stack.isNotEmpty() && !waiting) {
            if (--opBudget <= 0) {
                stack.clear()
                return
            }
            val frame = stack.last()
            frame.localName?.let { name -> locals[name] = checkNotNull(frame.localValue) }
            val ops = opsFor(frame.block)
            if (frame.nextIndex >= ops.size) {
                finishFrame()
                continue
            }
            run(ops[frame.nextIndex], frame)
        }
    }

    private fun run(op: Op, frame: TownServiceScriptFrame) {
        val opIndex = frame.nextIndex
        when (op) {
            is Op.Screen -> {
                advanceTop()
                engine.trace(EngineTraceEvent.Screen(op.text))
            }
            is Op.Emit -> {
                advanceTop()
                engine.trace(EngineTraceEvent.Emit(op.text))
            }
            is Op.EmitIndexed -> {
                advanceTop()
                engine.trace(EngineTraceEvent.EmitIndexed(op.pattern, eval(op.value)))
            }
            is Op.WaitInput -> waitInput(op.reason)
            is Op.Pause -> {
                advanceTop()
                engine.trace(EngineTraceEvent.Pause(op.millis))
            }
            is Op.LetInt -> {
                locals[op.name] = eval(op.value)
                advanceTop()
            }
            is Op.BindText -> advanceTop()
            is Op.Set -> {
                set(op.target, eval(op.value))
                advanceTop()
            }
            is Op.Add -> {
                set(op.target, read(op.target) + eval(op.value))
                advanceTop()
            }
            is Op.Sub -> {
                set(op.target, read(op.target) - eval(op.value))
                advanceTop()
            }
            is Op.AddClamped -> {
                set(op.target, minOf(read(op.target) + eval(op.value), eval(op.max)))
                advanceTop()
            }
            is Op.SetFlag -> {
                party = party.withFlagState(op.slug, op.nativeValue)
                advanceTop()
                engine.trace(EngineTraceEvent.SetFlag(op.slug, op.nativeValue))
            }
            is Op.If -> {
                val branch = if (eval(op.cond)) TownServiceScriptBranch.IfThen else TownServiceScriptBranch.IfElse
                advanceTop()
                pushChild(frame.block.child(opIndex, branch))
            }
            is Op.Loop -> {
                advanceTop()
                pushChild(frame.block.child(opIndex, TownServiceScriptBranch.LoopBody), loop = true)
            }
            is Op.Break -> {
                advanceTop()
                engine.trace(EngineTraceEvent.Break(op.reason, op.signal))
                handleControlSignal(op.signal)
            }
            is Op.Stop -> {
                advanceTop()
                engine.trace(EngineTraceEvent.Stop(op.reason, op.signal))
                handleControlSignal(op.signal)
            }
            is Op.Switch -> {
                val value = eval(op.expr)
                val caseIndex = op.cases.indexOfFirst { it.match == value || it.match == null }
                advanceTop()
                if (caseIndex >= 0) {
                    pushChild(
                        frame.block.child(
                            opIndex,
                            TownServiceScriptBranch.SwitchCase,
                            caseIndex = caseIndex,
                        ),
                    )
                }
            }
            is Op.Menu -> runMenu(op)
            is Op.AskMember -> askMember(op)
            is Op.AskInventorySlot -> askInventorySlot(op)
            is Op.AskMonster -> advanceTop()
            is Op.ForEachMember -> {
                advanceTop()
                op.memberFrames(party, frame, opIndex).asReversed().forEach { (memberNumber, block) ->
                    stack += TownServiceScriptFrame(
                        block = block,
                        nextIndex = 0,
                        localName = op.varName,
                        localValue = memberNumber,
                    )
                }
            }
            is Op.ForEachMonster -> advanceTop()
            is Op.Speak -> runSpeak(op)
            is Op.RunScene -> {
                runScene(op)
            }
            is Op.Encounter -> {
                advanceTop()
                engine.trace(EngineTraceEvent.Encounter(op.foe, op.mandatory, op.tier))
            }
            Op.ConsumeActiveItem -> advanceTop()
            Op.ConsumeTorch -> {
                advanceTop()
                engine.trace(EngineTraceEvent.ConsumeTorch)
            }
            is Op.GrantReward -> runGrantReward(op)
            is Op.RandomItemOffer -> runRandomItemOffer(op)
            is Op.ShopService -> {
                advanceTop()
                engine.trace(EngineTraceEvent.ShopService(op.shop))
            }
            is Op.AchieveMilestone -> {
                party = party.withMilestone(op.milestone)
                advanceTop()
                engine.trace(EngineTraceEvent.AchieveMilestone(op.milestone))
            }
            is Op.NativeCall -> {
                advanceTop()
                engine.effect(EngineRequest(op.path, op.args))
            }
        }
    }

    private fun waitInput(reason: String?) {
        engine.trace(EngineTraceEvent.WaitInput(reason))
        when (terminal.readLine()) {
            null -> wait()
            else -> advanceTop()
        }
    }

    private fun runMenu(op: Op.Menu) {
        val frame = stack.last()
        val opIndex = frame.nextIndex
        val keys = op.options.map { it.key }
        engine.trace(EngineTraceEvent.Menu(op.prompt, keys))
        when (val input = terminal.readKey(keys)) {
            is TerminalInput.Value -> {
                advanceTop()
                pushChild(
                    frame.block.child(opIndex, TownServiceScriptBranch.MenuOption, key = input.value),
                )
            }
            is TerminalInput.Rejected -> wait(input.rejection)
            TerminalInput.Unavailable -> wait()
        }
    }

    private fun askMember(op: Op.AskMember) {
        engine.trace(EngineTraceEvent.AskMember(op.prompt, op.filter))
        val choices = party.memberChoices(op.filter)
        when (val input = terminal.readMemberNumber(choices)) {
            is TerminalInput.Value -> {
                locals[op.varName] = input.value
                advanceTop()
            }
            is TerminalInput.Rejected -> wait(input.rejection)
            TerminalInput.Unavailable -> wait()
        }
    }

    private fun askInventorySlot(op: Op.AskInventorySlot) {
        engine.trace(EngineTraceEvent.AskInventorySlot(op.prompt, op.firstKey, op.slots, op.filter))
        val choices = party.inventorySlotChoices(op.filter, op.slots, op.firstKey)
        val keys = choices.map { it.first }
        when (val input = terminal.readKey(keys)) {
            is TerminalInput.Value -> {
                locals[op.varName] = checkNotNull(choices.firstOrNull { it.first == input.value }).second
                advanceTop()
            }
            is TerminalInput.Rejected -> wait(input.rejection)
            TerminalInput.Unavailable -> wait()
        }
    }

    private fun wait(inputRejection: TerminalInputRejection? = null) {
        waiting = true
        rejection = inputRejection
    }

    private fun advanceTop() {
        val index = stack.lastIndex
        stack[index] = stack[index].copy(nextIndex = stack[index].nextIndex + 1)
    }

    private fun pushChild(block: TownServiceScriptBlockRef, loop: Boolean = false) {
        if (opsFor(block).isNotEmpty() || loop) {
            stack += TownServiceScriptFrame(block, nextIndex = 0, loop = loop)
        }
    }

    private fun finishFrame() {
        val frame = stack.removeLast()
        if (frame.loop) {
            if (frame.iteration < 127) {
                stack += frame.copy(nextIndex = 0, iteration = frame.iteration + 1)
            } else {
                stack.clear()
            }
        }
    }

    private fun handleControlSignal(signal: ScriptControlSignal) {
        when (signal) {
            ScriptControlSignal.Continue -> Unit
            ScriptControlSignal.BreakNearestLoop -> breakNearestLoop()
            ScriptControlSignal.StopScript -> stopCurrentBranch()
        }
    }

    private fun breakNearestLoop() {
        while (stack.isNotEmpty()) {
            val frame = stack.removeLast()
            if (frame.loop) return
        }
    }

    private fun stopCurrentBranch() {
        val loopIndex = stack.indexOfLast { frame -> frame.loop }
        if (loopIndex < 0) {
            stack.clear()
            return
        }
        while (stack.lastIndex > loopIndex) {
            stack.removeLast()
        }
        val loopFrame = stack[loopIndex]
        stack[loopIndex] = loopFrame.copy(nextIndex = opsFor(loopFrame.block).size)
    }

    private fun currentRuntime(): RuntimeSessionState =
        runtime.withActiveParty(party)

    private fun continuation(runtime: RuntimeSessionState): TownServiceContinuation =
        TownServiceContinuation(
            runtime = runtime,
            locals = locals.toMap(),
            realmScalars = realmInts.toMap(),
            stack = stack.toList(),
            speakSession = speakSession,
            randomItemOfferSession = randomItemOfferSession,
            rewardGrantSession = rewardGrantSession,
        )

    private fun runSpeak(op: Op.Speak) {
        engine.trace(EngineTraceEvent.Speak(op.building))
        val initial = speakSession ?: SpeakSession.start(speakContent, currentRuntime(), op.building)
        val result = SpeakSessionTraceRunner(terminal, engine).runUntilBlocked(initial)
        runtime = result.session.runtime
        party = runtime.activeParty
        when (result.status) {
            TerminalRunStatus.Finished -> {
                speakSession = null
                advanceTop()
            }
            TerminalRunStatus.StepLimitReached -> {
                speakSession = null
                stack.clear()
            }
            is TerminalRunStatus.Waiting -> {
                speakSession = result.session
                wait(result.status.rejection)
            }
        }
    }

    private fun runScene(op: Op.RunScene) {
        advanceTop()
        val rumour = if (op.scene == Scenes.rumour) {
            speakContent.selectStandardRumour(currentRuntime())?.also { selection ->
                runtime = selection.runtime
                party = runtime.activeParty
            }?.rumour?.text
        } else {
            null
        }
        if (rumour != null) {
            engine.trace(EngineTraceEvent.Screen(rumour))
        } else {
            engine.trace(EngineTraceEvent.RunScene(op.scene, op.bindings))
        }
    }

    private fun runRandomItemOffer(op: Op.RandomItemOffer) {
        engine.trace(EngineTraceEvent.RandomItemOffer(op.offer))
        val initial = randomItemOfferSession ?: run {
            val draw = runtime.drawRandom(0, op.offer.pool.items.lastIndex)
            runtime = draw.runtime
            RandomItemOfferSession.start(op.offer, party, selectedIndex = draw.value)
        }
        val result = RandomItemOfferSessionTraceRunner(terminal, engine).runUntilBlocked(initial)
        party = result.session.party
        runtime = runtime.withActiveParty(party)
        when (result.status) {
            TerminalRunStatus.Finished -> {
                randomItemOfferSession = null
                advanceTop()
            }
            TerminalRunStatus.StepLimitReached -> {
                randomItemOfferSession = null
                stack.clear()
            }
            is TerminalRunStatus.Waiting -> {
                randomItemOfferSession = result.session
                wait(result.status.rejection)
            }
        }
    }

    private fun runGrantReward(op: Op.GrantReward) {
        engine.trace(EngineTraceEvent.GrantReward(op.reward))
        val initial = rewardGrantSession ?: RewardGrantSession.start(
            reward = op.reward,
            party = party,
            gold = op.reward.gold?.let(::eval) ?: 0,
        )
        val result = RewardGrantSessionTraceRunner(terminal, engine).runUntilBlocked(initial)
        party = result.session.party
        runtime = runtime.withActiveParty(party)
        when (result.status) {
            TerminalRunStatus.Finished -> {
                rewardGrantSession = null
                advanceTop()
            }
            TerminalRunStatus.StepLimitReached -> {
                rewardGrantSession = null
                stack.clear()
            }
            is TerminalRunStatus.Waiting -> {
                rewardGrantSession = result.session
                wait(result.status.rejection)
            }
        }
    }

    private fun opsFor(block: TownServiceScriptBlockRef): List<Op> {
        var ops = rootOps
        block.segments.forEach { segment ->
            val op = ops.getOrNull(segment.opIndex) ?: return emptyList()
            ops = when (segment.branch) {
                TownServiceScriptBranch.IfThen -> (op as? Op.If)?.thenOps.orEmpty()
                TownServiceScriptBranch.IfElse -> (op as? Op.If)?.elseOps.orEmpty()
                TownServiceScriptBranch.LoopBody -> (op as? Op.Loop)?.body.orEmpty()
                TownServiceScriptBranch.ForEachMemberBody -> (op as? Op.ForEachMember)?.body.orEmpty()
                TownServiceScriptBranch.ForEachMonsterBody -> (op as? Op.ForEachMonster)?.body.orEmpty()
                TownServiceScriptBranch.SwitchCase ->
                    (op as? Op.Switch)?.cases?.getOrNull(segment.caseIndex ?: -1)?.body.orEmpty()
                TownServiceScriptBranch.MenuOption ->
                    (op as? Op.Menu)?.options?.firstOrNull { it.key == segment.key }?.body.orEmpty()
                TownServiceScriptBranch.MenuDefault -> (op as? Op.Menu)?.defaultOps.orEmpty()
            }
        }
        return ops
    }

    private fun Op.ForEachMember.memberFrames(
        party: PartyState,
        frame: TownServiceScriptFrame,
        opIndex: Int,
    ): List<Pair<Int, TownServiceScriptBlockRef>> {
        val block = frame.block.child(opIndex, TownServiceScriptBranch.ForEachMemberBody)
        return party.members.mapIndexedNotNull { index, member ->
            val memberNumber = index + 1
            (memberNumber to block).takeIf { includeDead || !member.dead }
        }
    }

    private val evaluator = object : IntAstEvaluator() {
        override fun local(name: String): Int = locals[name] ?: 0
        override fun random(lo: Int, hi: Int): Int {
            val draw = runtime.drawRandom(lo, hi)
            runtime = draw.runtime
            return draw.value
        }
        override fun memberField(member: Int, field: String): Int =
            this@StatefulScriptServiceRunner.memberField(member, field)
        override fun monsterField(monster: Int, field: String): Int =
            error("town/service scripts cannot read combat monster fields")
        override fun realmScalar(name: String): Int = this@StatefulScriptServiceRunner.realmScalar(name)
        override fun partyScalar(name: String): Int = this@StatefulScriptServiceRunner.partyScalar(name)
        override fun nativeInt(path: String, args: List<String>): Int =
            engine.readInt(EngineRequest(path, args))
        override fun flagIs(slug: String, native: String): Boolean =
            party.flags.any { it.flag.slug == slug && it.native == native }
        override fun memberFlag(member: Int, name: String): Boolean =
            this@StatefulScriptServiceRunner.memberFlag(member, name)
        override fun monsterFlag(monster: Int, name: String): Boolean =
            error("town/service scripts cannot read combat monster flags")
    }

    private fun eval(ast: IntAst): Int = evaluator.int(ast)

    private fun eval(ast: BoolAst): Boolean = evaluator.bool(ast)

    private fun read(target: LValueAst): Int =
        when (target) {
            is LValueAst.PartyScalar -> partyScalar(target.name)
            is LValueAst.MemberField -> memberField(eval(target.member), target.field)
            is LValueAst.RealmScalar -> realmScalar(target.name)
            is LValueAst.MonsterField -> error("town/service scripts cannot read combat monster fields")
            is LValueAst.BattleFlag -> error("town/service scripts cannot read combat battle flags")
        }

    private fun set(target: LValueAst, value: Int) {
        when (target) {
            is LValueAst.PartyScalar -> party = party.withPartyScalar(target.name, value)
            is LValueAst.MemberField -> party = party.withMemberField(eval(target.member), target.field, value)
            is LValueAst.RealmScalar -> setRealmScalar(target.name, value)
            is LValueAst.MonsterField -> error("town/service scripts cannot write combat monster fields")
            is LValueAst.BattleFlag -> error("town/service scripts cannot write combat battle flags")
        }
    }

    private fun realmScalar(name: String): Int =
        when (name) {
            "day" -> runtime.realm.day
            "hour" -> runtime.realm.hour
            "minute" -> runtime.realm.minute
            "last_played_day" -> runtime.realm.lastPlayedDay
            "party_member_max" -> runtime.realm.partyMemberMax
            "banished" -> if (runtime.realm.banished) 1 else 0
            else -> realmInts[name] ?: 0
        }

    private fun setRealmScalar(name: String, value: Int) {
        runtime = when (name) {
            "day" -> runtime.withRealm(runtime.realm.copy(day = value))
            "hour" -> runtime.withRealm(runtime.realm.copy(hour = value.positiveMod(24)))
            "minute" -> runtime.withRealm(runtime.realm.copy(minute = value.positiveMod(60)))
            "last_played_day" -> runtime.withRealm(runtime.realm.copy(lastPlayedDay = value))
            "party_member_max" -> runtime.withRealm(runtime.realm.copy(partyMemberMax = value.coerceAtLeast(0)))
            "banished" -> runtime.withRealm(runtime.realm.copy(banished = value != 0))
            else -> runtime.also { realmInts[name] = value }
        }
    }

    private fun partyScalar(name: String): Int =
        when (name) {
            "gold" -> party.resources.gold
            "torches" -> party.resources.torches
            "food" -> party.resources.food
            "time_left" -> party.resources.timeLeft
            "rested_hours" -> party.resources.restedHours
            "member_count" -> party.counters.memberCount ?: party.members.count { !it.dead }
            else -> 0
        }

    private fun memberField(memberNumber: Int, field: String): Int {
        val member = party.members.getOrNull(memberNumber - 1) ?: return 0
        return when (field) {
            "hit_points" -> member.hitPoints.current
            "health" -> member.health
            "magic" -> member.mana.current
            "level" -> member.level
            "wisdom" -> member.wisdom
            "charisma" -> member.charisma
            "class" -> member.pcClass.legacyId
            "dead" -> if (member.dead) 1 else 0
            else -> 0
        }
    }

    private fun memberFlag(memberNumber: Int, name: String): Boolean {
        val member = party.members.getOrNull(memberNumber - 1) ?: return false
        return when (name) {
            "dead" -> member.dead
            "spellcaster" -> !member.dead && member.pcClass in setOf(PcClass.Mage, PcClass.Priest)
            else -> false
        }
    }
}

class TerminalTextRenderer(
    private val references: Map<String, List<String>> = emptyMap(),
    private val towns: List<TownModel> = emptyList(),
    private val characters: List<CharacterModel> = emptyList(),
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
            is TextPart.RoleName -> characterFor(part.role)?.name ?: "{${part.role.slug}.name}"
            is TextPart.RoleLocation -> roleLocation(part.role, context.runtime) ?: "{${part.role.slug}.location}"
            is TextPart.TownName -> townName(part.town)
            is TextPart.FuzzyTownLocation -> townLocation(part.town, part.radius) ?: "${part.town.slug}.location"
            TextPart.OfferedItemName -> context.offeredItemName ?: "{item_name}"
            TextPart.OfferedItemPrice -> context.offeredItemPrice?.toString() ?: "{item_price}"
            TextPart.ShopPrice -> context.shopPrice?.toString() ?: "{price}"
            TextPart.ShopCarryCap -> context.shopCarryCap?.toString() ?: "{cap}"
        }

    private fun characterFor(role: NpcRole): CharacterModel? =
        characters.firstOrNull { role in it.roles }

    private fun roleLocation(role: NpcRole, runtime: RuntimeSessionState?): String? {
        val character = characterFor(role) ?: return null
        val placement = runtime
            ?.npcs
            ?.firstOrNull { it.npc == character.id }
            ?.position
            ?: (character.placement as? CharacterPlacementModel.Fixed)?.position
            ?: return null
        return position(placement)
    }

    private fun position(position: WorldPosition): String =
        when (position) {
            is WorldPosition.Town -> "in ${townName(position.town)}"
            is WorldPosition.Wilderness -> "at ${point(position.point)}"
        }

    private fun townName(town: TownId): String =
        towns.firstOrNull { it.id == town }?.name ?: town.slug

    private fun townLocation(town: TownId, radius: Int): String? {
        val model = towns.firstOrNull { it.id == town } ?: return null
        return point(fuzzyPoint(model.position, town.slug, radius))
    }

    private fun fuzzyPoint(point: GridPoint, slug: String, radius: Int): GridPoint =
        if (radius <= 0) {
            point
        } else {
            GridPoint(
                x = point.x + stableOffset("$slug:x", radius),
                y = point.y + stableOffset("$slug:y", radius),
            )
        }

    private fun stableOffset(key: String, radius: Int): Int {
        val span = radius * 2 + 1
        val hash = key.fold(0) { acc, char -> acc * 31 + char.code }
        return Math.floorMod(hash, span) - radius
    }

    private fun point(point: GridPoint): String = "${point.x}x${point.y}"

    private fun render(ast: IntAst): String =
        when (ast) {
            is IntAst.Lit -> ast.value.toString()
            else -> "{int}"
        }
}

class RylweenKotlinEngine(
    private val content: GameModel,
    private val itemRecords: List<ItemRecordModel> = ItemCatalog.all,
    private val npcPlacementConfig: NpcPlacementRealizerConfig = NpcPlacementRealizerConfig(),
) {
    fun initialRuntime(
        state: StateModel,
        rngState: Long? = state.realm.rngState,
        realizeNpcs: Boolean = true,
    ): RuntimeSessionState {
        val runtime = RuntimeSessionState(
            realm = state.realm.copy(rngState = rngState),
            parties = state.parties,
            npcs = state.npcs,
        )
        return if (realizeNpcs) {
            runtime.realizeNpcPlacements(content.characters, content.towns, npcPlacementConfig)
        } else {
            runtime
        }
    }

    fun initialRuntime(
        party: PartyState,
        hour: Int,
        currentTown: TownId?,
        rngState: Long? = null,
        realizeNpcs: Boolean = true,
    ): RuntimeSessionState {
        val runtime = RuntimeSessionState.singleParty(
            party = party,
            hour = hour,
            currentTown = currentTown,
            rngState = rngState,
        )
        return if (realizeNpcs) {
            runtime.realizeNpcPlacements(content.characters, content.towns, npcPlacementConfig)
        } else {
            runtime
        }
    }

    fun townSession(
        party: PartyState,
        town: TownId,
        hour: Int,
        rngState: Long? = null,
        realizeNpcs: Boolean = true,
    ): TownSession =
        TownSession.start(
            town = content.towns.single { it.id == town },
            townSystem = requireNotNull(content.townSystem) { "town system is not configured" },
            scenes = content.scenes,
            runtime = initialRuntime(party, hour, town, rngState, realizeNpcs),
            itemRecords = itemRecords,
        )

    fun wildernessSession(
        state: StateModel,
        worldMap: WorldMapGrid,
        rngState: Long? = state.realm.rngState,
        realizeNpcs: Boolean = true,
    ): WildernessSession {
        val runtime = initialRuntime(state, rngState, realizeNpcs)
        return WildernessSession.start(
            content = content,
            runtime = runtime,
            worldMap = worldMap.withNpcHuts(runtime.npcs),
            itemRecords = itemRecords,
        )
    }

    fun speakContent(): SpeakRuntimeContent =
        SpeakRuntimeContent(
            speak = content.speak,
            characters = content.characters,
            dialogue = content.dialogue,
        )

    fun textRenderer(references: Map<String, List<String>> = emptyMap()): TerminalTextRenderer =
        TerminalTextRenderer(references = references, towns = content.towns, characters = content.characters)
}

private class RandomItemOfferSessionTraceRunner(
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
                    when (val input = terminal.readKey(listOf(step.buyKey, step.declineKey))) {
                        is TerminalInput.Value ->
                            session.apply(RandomItemOfferInput.Buy(input.value == step.buyKey))
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
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
                    when (val input = terminal.readKey(choices.map { it.first })) {
                        is TerminalInput.Value ->
                            session.apply(
                                RandomItemOfferInput.DropSlot(
                                    checkNotNull(choices.firstOrNull { it.first == input.value }).second,
                                ),
                            )
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
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

private class RewardGrantSessionTraceRunner(
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
                    when (val input = terminal.readKey(choices.map { it.first })) {
                        is TerminalInput.Value ->
                            session.apply(
                                RewardGrantInput.DropSlot(
                                    checkNotNull(choices.firstOrNull { it.first == input.value }).second,
                                ),
                            )
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
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

private class SpeakSessionTraceRunner(
    private val terminal: TerminalPort,
    private val engine: TraceEnginePort,
) {
    fun runUntilBlocked(initial: SpeakSession): TerminalAdapterResult<SpeakSession> {
        var session = initial
        repeat(128) {
            session = when (val step = session.step) {
                is SpeakStep.ChooseNpc -> {
                    val keys = step.npcs.indices.map { index -> '1' + index }
                    traceMenu(npcPrompt(step.npcs), keys)
                    when (val input = terminal.readKey(keys)) {
                        is TerminalInput.Value -> {
                            val npc = step.npcs[input.value - '1']
                            session.apply(SpeakInput.Npc(npc.id))
                        }
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                is SpeakStep.DialogueBranch -> {
                    val continuation = step.continuation ?: TownServiceContinuation.start(session.runtime)
                    val result = StatefulScriptServiceRunner(
                        terminal = terminal,
                        engine = engine,
                        rootOps = step.branch.ops,
                        continuation = continuation,
                        speakContent = session.content,
                    ).run()
                    val nextSession = session.copy(runtime = result.runtime)
                    when (result.status) {
                        TownServiceBridgeStatus.Finished ->
                            nextSession.copy(step = step.next)
                        TownServiceBridgeStatus.Waiting ->
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
                    val keys = step.actions.map { it.key }
                    traceMenu(mainPrompt(step.npc), keys)
                    when (val input = terminal.readKey(keys)) {
                        is TerminalInput.Value -> {
                            val action = checkNotNull(step.actions.firstOrNull { it.key == input.value })
                            session.apply(SpeakInput.Main(action.topic.toMainChoice()))
                        }
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                is SpeakStep.QuestionMenu -> {
                    val keys = step.actions.map { it.key }
                    traceMenu(questionPrompt(), keys)
                    when (val input = terminal.readKey(keys)) {
                        is TerminalInput.Value -> {
                            val action = checkNotNull(step.actions.firstOrNull { it.key == input.value })
                            session.apply(SpeakInput.Question(action.topic))
                        }
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
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

    private fun npcPrompt(npcs: List<CharacterModel>): TextBlock =
        inlineText(
            *(
                listOf("Speak with whom?", "") +
                    npcs.mapIndexed { index, npc -> "${index + 1}. ${npc.name}, ${npc.job.slug}" }
                ).toTypedArray(),
        )

    private fun mainPrompt(npc: CharacterModel): TextBlock =
        inlineText("Chatting with", "${npc.name}:", "", "(C)hitchat", "(Q)uestion", "(R)umors", "(B)ye")

    private fun questionPrompt(): TextBlock =
        inlineText("(L)odging", "(M)agic", "(P)eople", "(N)othing")

    private fun ConversationTopic.toMainChoice(): SpeakMainChoice =
        when (this) {
            ConversationTopic.Chitchat -> SpeakMainChoice.Chitchat
            ConversationTopic.Question -> SpeakMainChoice.Question
            ConversationTopic.Rumour -> SpeakMainChoice.Rumour
            ConversationTopic.Bye -> SpeakMainChoice.Bye
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
    private val serviceBridge: TownServiceBridge = StatefulTownServiceBridge(terminal),
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
                    terminal.write(renderer.lines(step.text, TerminalTextContext(runtime = session.runtime)))
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
                    val continuation = step.continuation ?: TownServiceContinuation.start(session.runtime)
                    val result = serviceBridge.run(step.service, step.scene, continuation)
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

    private fun writeBridgeEvents(events: List<EngineTraceEvent>, runtime: RuntimeSessionState) {
        val context = TerminalTextContext(runtime = runtime)
        events.forEach { event ->
            when (event) {
                is EngineTraceEvent.Screen -> terminal.write(renderer.lines(event.text, context))
                is EngineTraceEvent.Emit -> terminal.write(listOf("[${event.id.id}]"))
                is EngineTraceEvent.EmitIndexed -> terminal.write(listOf("[${event.pattern.pattern}: ${event.value}]"))
                is EngineTraceEvent.Menu -> terminal.write(
                    renderer.lines(event.prompt, context) + "Options: ${event.keys.joinToString("/")}",
                )
                is EngineTraceEvent.AskMember -> terminal.write(renderer.lines(event.prompt, context))
                is EngineTraceEvent.AskInventorySlot -> terminal.write(renderer.lines(event.prompt, context))
                is EngineTraceEvent.NativeEffect -> terminal.write(listOf("Native: ${event.request.path}"))
                is EngineTraceEvent.NativeInt -> terminal.write(listOf("Native int: ${event.request.path} = ${event.result}"))
                is EngineTraceEvent.NativeString ->
                    terminal.write(listOf("Native string: ${event.request.path} = ${event.result}"))
                is EngineTraceEvent.RunScene -> terminal.write(listOf("Scene: ${event.scene.slug}"))
                is EngineTraceEvent.Stop -> terminal.write(listOf(event.reason ?: "Stop"))
                is EngineTraceEvent.Break -> terminal.write(listOf(event.reason ?: "Break"))
                is EngineTraceEvent.TownService -> terminal.write(listOf("Service: ${event.service.building.slug}"))
                is EngineTraceEvent.WaitInput,
                is EngineTraceEvent.Pause,
                is EngineTraceEvent.SetFlag,
                is EngineTraceEvent.GrantReward,
                is EngineTraceEvent.AchieveMilestone,
                is EngineTraceEvent.Speak,
                is EngineTraceEvent.Encounter,
                is EngineTraceEvent.RandomItemOffer,
                is EngineTraceEvent.ShopService,
                is EngineTraceEvent.ShopSellQuote,
                is EngineTraceEvent.ShopTransaction,
                is EngineTraceEvent.EquipmentAction,
                EngineTraceEvent.ConsumeTorch -> Unit
            }
        }
    }
}

class WildernessSessionTerminalAdapter(
    private val terminal: TerminalPort,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(),
    private val serviceBridge: TownServiceBridge = StatefulTownServiceBridge(terminal),
) {
    fun run(initial: WildernessSession): WildernessSession =
        runUntilBlocked(initial).session

    fun runUntilBlocked(initial: WildernessSession): TerminalAdapterResult<WildernessSession> {
        var session = initial
        repeat(512) {
            session = when (val step = session.step) {
                WildernessSessionStep.TurnStart -> session.beginTurn()
                is WildernessSessionStep.Messages -> {
                    step.blocks.forEach { block ->
                        terminal.write(renderer.lines(block, TerminalTextContext(runtime = session.runtime)))
                    }
                    session.continueMessages()
                }
                is WildernessSessionStep.OutOfMoves -> {
                    writeMenu(step.prompt, listOf('S', 'Q'))
                    when (val input = terminal.readKey(listOf('S', 'Q'))) {
                        is TerminalInput.Value ->
                            session.apply(WildernessSessionInput.OutOfMovesAnswer(input.value == 'S'))
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                is WildernessSessionStep.CaveFound -> {
                    writeMenu(step.prompt, listOf('E', 'L'))
                    when (val input = terminal.readKey(listOf('E', 'L'))) {
                        is TerminalInput.Value ->
                            session.apply(WildernessSessionInput.CaveAnswer(input.value == 'E'))
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                is WildernessSessionStep.DungeonHandoff -> {
                    terminal.write(renderer.lines(WildernessPromptText.dungeonHandoffPending(step.handoff)))
                    session.completeDungeonHandoff()
                }
                WildernessSessionStep.Explore -> {
                    writeExplore(session)
                    when (val input = terminal.readKey(WildernessCommandKeys)) {
                        is TerminalInput.Value -> applyCommand(session, input.value)
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                is WildernessSessionStep.Notice -> {
                    terminal.write(renderer.lines(step.text, TerminalTextContext(runtime = session.runtime)))
                    session.continueNotice()
                }
                is WildernessSessionStep.QuitConfirm -> {
                    writeMenu(step.prompt, listOf('Y', 'N'))
                    when (val input = terminal.readKey(listOf('Y', 'N'))) {
                        is TerminalInput.Value ->
                            session.apply(WildernessSessionInput.QuitAnswer(input.value == 'Y'))
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                is WildernessSessionStep.Town -> {
                    val result = TownSessionTerminalAdapter(terminal, renderer, serviceBridge).runUntilBlocked(step.session)
                    val updated = session.replaceTownSession(result.session)
                    when (result.status) {
                        TerminalRunStatus.Finished -> updated
                        TerminalRunStatus.StepLimitReached ->
                            return TerminalAdapterResult(updated, TerminalRunStatus.StepLimitReached)
                        is TerminalRunStatus.Waiting ->
                            return TerminalAdapterResult(updated, result.status)
                    }
                }
                is WildernessSessionStep.Battle -> {
                    val result = BattleSessionTerminalAdapter(terminal, renderer).runUntilBlocked(step.session)
                    val updated = session.replaceBattleSession(result.session)
                    when (result.status) {
                        TerminalRunStatus.Finished -> updated
                        TerminalRunStatus.StepLimitReached ->
                            return TerminalAdapterResult(updated, TerminalRunStatus.StepLimitReached)
                        is TerminalRunStatus.Waiting ->
                            return TerminalAdapterResult(updated, result.status)
                    }
                }
                is WildernessSessionStep.Finished -> return finished(session)
            }
        }
        return TerminalAdapterResult(session, TerminalRunStatus.StepLimitReached)
    }

    private fun applyCommand(session: WildernessSession, key: Char): WildernessSession =
        when (key) {
            ' ' -> session.apply(WildernessSessionInput.Pass)
            'E' -> session.apply(WildernessSessionInput.Enter)
            'X' -> session.apply(WildernessSessionInput.Quit)
            else -> session.apply(
                WildernessSessionInput.Move(
                    checkNotNull(wildernessDirectionForKey(key)) { "unsupported wilderness command $key" },
                ),
            )
        }

    private fun writeExplore(session: WildernessSession) {
        val point = (session.party.position as? WorldPosition.Wilderness)?.point
        val lines = buildList {
            add("")
            add("Wilderness")
            addAll(point?.let { session.worldMap.viewport(it) }.orEmpty())
            add(statusLine(session, point))
            point?.let { current ->
                session.towns.firstOrNull { town -> town.position == current }?.let { town ->
                    add("At: ${town.name}")
                }
            }
            session.lastResult?.takeIf { it.status == WildernessSessionResultStatus.Blocked }?.let {
                add("Blocked.")
            }
            add("Commands: 8/2/4/6 move, Space pass, E enter, X quit")
        }
        terminal.write(lines)
    }

    private fun writeMenu(block: TextBlock, keys: List<Char>) {
        terminal.write(renderer.lines(block) + "Options: ${keys.joinToString("/")}")
    }

    private fun statusLine(session: WildernessSession, point: GridPoint?): String {
        val terrain = point?.let { session.worldMap.tileAt(it) } ?: ' '
        val position = point?.let { "${it.x}x${it.y}" } ?: "not in wilderness"
        return "Position: $position  Moves: ${session.party.resources.timeLeft}  Tile: $terrain"
    }

    private companion object {
        val WildernessCommandKeys = listOf('8', '2', '4', '6', ' ', 'E', 'X')
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
                    writeMenu(step.prompt, listOf('F', 'R'))
                    when (val input = terminal.readKey(listOf('F', 'R'))) {
                        is TerminalInput.Value -> session.apply(
                            BattleSessionInput.Opening(
                                if (input.value == 'F') BattleOpeningChoice.Fight else BattleOpeningChoice.Run,
                            ),
                        )
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                is BattleSessionStep.RoundOpening -> {
                    writeMenu(step.prompt, listOf('F', 'R'))
                    when (val input = terminal.readKey(listOf('F', 'R'))) {
                        is TerminalInput.Value -> session.apply(
                            BattleSessionInput.Opening(
                                if (input.value == 'F') BattleOpeningChoice.Fight else BattleOpeningChoice.Run,
                            ),
                        )
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
                    writeMenu(step.prompt, step.actions.map { it.key })
                    when (val input = terminal.readKey(step.actions.map { it.key })) {
                        is TerminalInput.Value -> session.apply(
                            BattleSessionInput.PlayerAction(
                                checkNotNull(step.actions.firstOrNull { it.key == input.value }).kind,
                            ),
                        )
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                is BattleSessionStep.MonsterTarget -> {
                    writeMenu(step.prompt, step.targetKeys)
                    when (val input = terminal.readKey(step.targetKeys)) {
                        is TerminalInput.Value -> session.apply(BattleSessionInput.TargetMonster(input.value - 'A'))
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                is BattleSessionStep.ScriptMonsterTarget -> {
                    writeMenu(step.prompt, step.targetKeys)
                    when (val input = terminal.readKey(step.targetKeys)) {
                        is TerminalInput.Value -> session.apply(BattleSessionInput.TargetMonster(input.value - 'A'))
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                is BattleSessionStep.MemberTarget -> {
                    writeMenu(step.prompt, step.targetKeys)
                    when (val input = terminal.readKey(step.targetKeys)) {
                        is TerminalInput.Value -> session.apply(BattleSessionInput.TargetMember(input.value - '1'))
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                is BattleSessionStep.MagicItemSlot -> {
                    writeMenu(step.prompt, step.slotKeys)
                    when (val input = terminal.readKey(step.slotKeys)) {
                        is TerminalInput.Value -> session.apply(BattleSessionInput.InventorySlot(input.value - 'A' + 1))
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                is BattleSessionStep.SpellChoice -> {
                    writeMenu(step.prompt, step.spellKeys)
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

    private fun writeMenu(block: TextBlock, keys: List<Char>) {
        terminal.write(renderer.lines(block) + "Options: ${keys.joinToString("/")}")
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
    val key = if (' ' in allowed && raw.firstOrNull() == ' ') {
        ' '
    } else {
        raw.trim().firstOrNull()?.uppercaseChar()
    }
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
    inventorySlotChoices(filter, 1..InventorySaveSlotMax, 'A').map { it.first }

private fun PartyState.inventorySlotChoices(
    filter: InventorySlotChoiceFilter,
    slots: IntRange,
    firstKey: Char,
): List<Pair<Char, Int>> {
    val itemsBySlot = inventory.associate { it.slot to it.item }
    return slots
        .filter { slot ->
            slot in 1..InventorySaveSlotMax && when (filter) {
                InventorySlotChoiceFilter.Any -> true
                InventorySlotChoiceFilter.NonEmpty -> itemsBySlot[slot] != null
                InventorySlotChoiceFilter.Empty -> itemsBySlot[slot] == null
            }
        }
        .map { slot -> firstKey + (slot - slots.first) to slot }
}

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

private fun Int.positiveMod(divisor: Int): Int =
    ((this % divisor) + divisor) % divisor

private fun PartyState.withPartyScalar(name: String, value: Int): PartyState =
    when (name) {
        "gold" -> copy(resources = resources.copy(gold = value.coerceAtLeast(0)))
        "torches" -> copy(resources = resources.copy(torches = value.coerceAtLeast(0)))
        "food" -> copy(resources = resources.copy(food = value.coerceAtLeast(0)))
        "time_left" -> copy(resources = resources.copy(timeLeft = value))
        "rested_hours" -> copy(resources = resources.copy(restedHours = value))
        "member_count" -> copy(counters = counters.copy(memberCount = value.coerceAtLeast(0)))
        else -> this
    }

private fun PartyState.withMemberField(memberNumber: Int, field: String, value: Int): PartyState {
    val index = memberNumber - 1
    val member = members.getOrNull(index) ?: return this
    val updated = when (field) {
        "hit_points" -> member.copy(hitPoints = member.hitPoints.copy(current = value.coerceAtLeast(0)))
        "health" -> member.copy(
            health = value.coerceAtLeast(0),
            hitPoints = member.hitPoints.copy(max = value.coerceAtLeast(0)),
        )
        "magic" -> member.copy(mana = member.mana.copy(current = value.coerceAtLeast(0)))
        "wisdom" -> member.copy(wisdom = value.coerceAtLeast(0))
        "charisma" -> member.copy(charisma = value.coerceAtLeast(0))
        "level" -> member.copy(level = value.coerceAtLeast(1))
        "dead" -> member.copy(dead = value != 0)
        else -> member
    }
    return copy(members = members.mapIndexed { i, existing -> if (i == index) updated else existing })
}
