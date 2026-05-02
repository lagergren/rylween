// Pure trace runners for scripts and reusable runtime sessions.

package rylween.engine

import rylween.dsl.*
import rylween.runtime.*

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
            is Op.AppendJournal -> ScriptControlSignal.Continue.also {
                trace(EngineTraceEvent.AppendJournal(op.text))
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
            is Op.AskKey -> ScriptControlSignal.Continue.also {
                trace(EngineTraceEvent.AskKey(op.prompt, op.bindings))
            }
            is Op.AskMember -> ScriptControlSignal.Continue.also {
                trace(EngineTraceEvent.AskMember(op.prompt, op.filter))
            }
            is Op.AskInventorySlot -> ScriptControlSignal.Continue.also {
                trace(EngineTraceEvent.AskInventorySlot(op.prompt, op.firstKey, op.slots, op.filter))
            }
            is Op.AskText -> ScriptControlSignal.Continue.also {
                trace(EngineTraceEvent.AskText(op.prompt, op.accepted, op.maxLength))
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
            is Op.EnterDungeon -> ScriptControlSignal.StopScript.also {
                trace(EngineTraceEvent.DungeonHandoff(WildernessDungeonHandoff(op.dungeon, op.levels)))
            }
            Op.ConsumeActiveItem -> ScriptControlSignal.Continue
            is Op.ClearInventorySlot -> ScriptControlSignal.Continue
            is Op.ConsumeInventoryPool -> ScriptControlSignal.Continue.also {
                trace(EngineTraceEvent.ConsumeInventoryPool(op.pool, eval(op.quantity)))
            }
            Op.ConsumeTorch -> ScriptControlSignal.Continue.also {
                trace(EngineTraceEvent.ConsumeTorch)
            }
            is Op.TeleportParty -> ScriptControlSignal.Continue.also {
                trace(EngineTraceEvent.TeleportParty(eval(op.directionKey), eval(op.distance)))
            }
            is Op.DiscoverCave -> ScriptControlSignal.StopScript.also {
                val handoff = WildernessDungeonHandoff(op.dungeon, eval(op.levels))
                trace(EngineTraceEvent.DiscoverCave(handoff, eval(op.moveCost)))
                trace(EngineTraceEvent.DungeonHandoff(handoff))
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
        override fun inventoryPoolCount(pool: ItemPoolId): Int = 0
        override fun inventorySlotCost(slot: Int): Int = 0
        override fun nativeInt(path: String, args: List<String>): Int =
            engine.readInt(EngineRequest(path, args))
        override fun flagIs(slug: String, native: String): Boolean = false
        override fun milestoneAchieved(milestone: GameMilestoneId): Boolean = false
        override fun memberFlag(member: Int, name: String): Boolean = false
        override fun monsterFlag(monster: Int, name: String): Boolean = false
        override fun runtimeFlag(name: String): Boolean = false
    }

    private fun eval(ast: IntAst): Int = evaluator.int(ast)

    private fun eval(ast: BoolAst): Boolean = evaluator.bool(ast)

    private fun eval(ast: StrAst): String =
        when (ast) {
            is StrAst.NativeString -> engine.readString(EngineRequest(ast.path, ast.args.map(::eval)))
        }

    private fun eval(arg: NativeStringArg): String =
        when (arg) {
            is NativeStringArg.IntValue -> eval(arg.value).toString()
            is NativeStringArg.Literal -> arg.value
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
        render(session)
        inputs.forEach { input ->
            val previous = session
            session = session.apply(input)
            traceResult(previous, session)
            render(session)
        }
        return session
    }

    private fun ShopServiceSession.apply(input: ShopServiceInput): ShopServiceSession =
        when (input) {
            ShopServiceInput.ContinueIntro -> continueIntro()
            is ShopServiceInput.Main -> chooseMain(input.choice)
            is ShopServiceInput.SelectCatalog -> chooseCatalog(input.catalogKey)
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

    private fun render(session: ShopServiceSession) {
        when (val step = session.step) {
            is ShopServiceStep.Intro -> screen(step.text)
            is ShopServiceStep.MainMenu -> menu(
                step.text,
                buildList {
                    if (step.canBuy) add(session.shop.keys.buy.primaryChar)
                    if (step.canSell) add(session.shop.keys.sell.primaryChar)
                    add(session.shop.keys.leave.primaryChar)
                },
            )
            is ShopServiceStep.SelectCatalog -> menu(
                step.prompt,
                step.catalogs.map { catalog -> catalog.key } +
                    checkNotNull(session.shop.buy).paymentKeys.decline.primaryChar,
            )
            is ShopServiceStep.BuyItem -> menu(
                step.prompt,
                step.catalogs.flatMap { catalog -> catalog.pages.flatMap { page -> page.entries } }
                    .map { entry -> entry.key },
            )
            is ShopServiceStep.Quantity -> screen(step.prompt)
            is ShopServiceStep.ConfirmPurchase -> menu(
                step.prompt,
                listOf(
                    checkNotNull(session.shop.buy).paymentKeys.pay.primaryChar,
                    checkNotNull(session.shop.buy).paymentKeys.decline.primaryChar,
                ),
            )
            is ShopServiceStep.DropSlot -> inventorySlot(step.prompt)
            is ShopServiceStep.SellSlot -> inventorySlot(step.prompt)
            is ShopServiceStep.ConfirmSale -> menu(
                step.prompt,
                listOf(
                    checkNotNull(session.shop.sell).saleKeys.accept.primaryChar,
                    checkNotNull(session.shop.sell).saleKeys.decline.primaryChar,
                ),
            )
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
            is TownLeaveEquipmentStep.ReadyQuestion -> engine.trace(EngineTraceEvent.Menu(step.prompt, yesNoKeys()))
            is TownLeaveEquipmentStep.Equipment -> renderEquipmentStep(engine, step.session.step)
            is TownLeaveEquipmentStep.Finished -> Unit
        }
    }
}

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

private fun yesNoKeys(keys: YesNoKeysModel = StandardYesNoKeys): List<Char> =
    listOf(keys.yes.primaryChar, keys.no.primaryChar)
