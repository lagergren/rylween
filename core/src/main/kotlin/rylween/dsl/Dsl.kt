@file:Suppress("ktlint:standard:no-wildcard-imports")

/**
 * Kotlin implementation for the Rylween authoring DSL.
 *
 * This file is implementation/compiler scaffolding: typed handles, immutable
 * models, builders, and recorded script ASTs. Current-game handles are
 * generated from `RylweenGame.dsl.kts` by the Gradle content-generation task.
 * Game-specific content belongs in `RylweenGame.dsl.kts`, validation helpers
 * live in `DslValidation.kt`, and compiled J1 emission lives in
 * `compiled/CompiledBundle.kt`.
 *
 * Authoring goals:
 * 1. Kotlin is the source format.
 * 2. Scripts record a real AST instead of being Unit-shaped notes.
 * 3. Flags, monsters, items, scenes, text ids, NPC roles, and spells are typed handles.
 * 4. Engine-native calls stay behind typed DSL verbs or explicit allowlist specs.
 * 5. Game-specific declarations should read like game data, not Kotlin programming.
 */

package rylween.dsl

import rylween.dsl.impl.*

// =============================================================================
// 05. DSL kernel: builders and validation
// =============================================================================

class ScriptContext {
    private var nextVar = 0
    private val opTargets = mutableListOf<MutableList<Op>>()

    fun fresh(prefix: String): String = "${prefix}_${nextVar++}"

    fun record(
        fallback: MutableList<Op>,
        op: Op,
    ) {
        (opTargets.lastOrNull() ?: fallback) += op
    }

    fun <T> recording(
        target: MutableList<Op>,
        body: () -> T,
    ): T {
        opTargets += target
        return try {
            body()
        } finally {
            opTargets.removeAt(opTargets.lastIndex)
        }
    }
}

@DslMarker
annotation class RylNextDsl

@RylNextDsl
@Suppress("unused", "MemberVisibilityCanBePrivate")
class ScriptScope(
    private val ctx: ScriptContext,
    private val ops: MutableList<Op>,
) {
    val realm: RealmRef = RealmRef(this)
    val context: RuntimeContextRef = RuntimeContextRef()
    val party: PartyRef = PartyRef(this)
    val battle: BattleRef = BattleRef(this)
    val ask: AskScope = AskScope(this)
    val choice: ChoiceScope = ChoiceScope(this)
    val view: ViewScope = ViewScope(this)

    val currentCaster: MemberRef
        get() = party.member(v("caster_idx"))

    fun record(op: Op) {
        ctx.record(ops, op)
    }

    fun fresh(prefix: String): String = ctx.fresh(prefix)

    fun screen(text: TextBlock): Unit = record(Op.Screen(text))

    fun screen(id: TextId): Unit = screen(TextBlock.Ref(id))

    fun screen(vararg lines: String): Unit = screen(textLines(*lines))

    fun screen(body: StructuredTextBlockBuilder.() -> Unit): Unit = screen(structuredText(body))

    fun readScreen(vararg lines: String) {
        readScreen(textLines(*lines))
    }

    fun readScreen(text: TextBlock) {
        screen(text)
        waitInput()
        clearText()
    }

    fun readScreen(body: StructuredTextBlockBuilder.() -> Unit) {
        readScreen(structuredText(body))
    }

    fun say(text: TextBlock): Unit = screen(text)

    fun say(id: TextId): Unit = screen(id)

    fun say(vararg lines: String): Unit = screen(*lines)

    fun say(body: StructuredTextBlockBuilder.() -> Unit): Unit = screen(body)

    fun emit(id: TextId): Unit = record(Op.Emit(id))

    fun journal(text: TextBlock): Unit = record(Op.AppendJournal(text))

    fun journal(vararg lines: String): Unit = journal(textLines(*lines))

    fun journal(body: StructuredTextBlockBuilder.() -> Unit): Unit = journal(structuredText(body))

    fun waitInput(reason: String? = null): Unit = record(Op.WaitInput(reason))

    fun pause(ms: Int): Unit = record(Op.Pause(ms))

    fun pause600ms(): Unit = pause(600)

    fun pause1s(): Unit = pause(1_000)

    fun pause1500ms(): Unit = pause(1_500)

    fun pause2s(): Unit = pause(2_000)

    fun pause3s(): Unit = pause(3_000)

    fun pause4s(): Unit = pause(4_000)

    fun clearText(): Unit = view.clearText()

    fun clearMapWindow(): Unit = view.clearMapWindow()

    fun renderMap(): Unit = view.renderMap()

    fun showBackdrop(image: ImageId): Unit = view.showBackdrop(image)

    fun statusLine(): Unit = view.statusLine()

    fun let(
        name: String,
        value: IntExpr,
    ): IntExpr {
        record(Op.LetInt(name, value.ast))
        return v(name)
    }

    fun let(
        name: String,
        value: Int,
    ): IntExpr = let(name, lit(value))

    fun localInt(
        value: IntExpr,
        hint: String = "local",
    ): LocalIntRef {
        val ref = LocalIntRef(fresh(hint))
        record(Op.LetInt(ref.name, value.ast))
        return ref
    }

    fun localInt(
        value: Int,
        hint: String = "local",
    ): LocalIntRef = localInt(lit(value), hint)

    infix fun LocalIntRef.set(value: IntExpr) {
        record(Op.LetInt(name, value.ast))
    }

    infix fun LocalIntRef.set(value: Int): Unit = set(lit(value))

    operator fun LocalIntRef.plusAssign(value: IntExpr) {
        record(Op.LetInt(name, (this + value).ast))
    }

    operator fun LocalIntRef.plusAssign(value: Int): Unit = plusAssign(lit(value))

    operator fun LocalIntRef.minusAssign(value: IntExpr) {
        record(Op.LetInt(name, (this - value).ast))
    }

    operator fun LocalIntRef.minusAssign(value: Int): Unit = minusAssign(lit(value))

    fun mark(state: FlagState) {
        record(Op.SetFlag(state.flag.slug, state.native))
    }

    fun whenever(
        cond: BoolExpr,
        then: ScriptScope.() -> Unit,
    ): ElseBranch {
        val thenOps = capture(then)
        val index = ops.size
        ops += Op.If(cond.ast, thenOps, emptyList())
        return ElseBranch(ctx) { elseOps -> ops[index] = Op.If(cond.ast, thenOps, elseOps) }
    }

    fun stop(reason: String? = null): Unit = record(Op.Stop(reason))

    fun breakLoop(reason: String? = null): Unit = record(Op.Break(reason))

    fun loop(body: ScriptScope.() -> Unit) {
        record(Op.Loop(capture(body)))
    }

    fun choose(
        expr: IntExpr,
        body: SwitchBuilder.() -> Unit,
    ) {
        val builder = SwitchBuilder(ctx)
        builder.body()
        record(Op.Switch(expr.ast, builder.cases))
    }

    fun menu(
        prompt: TextId,
        body: MenuBuilder.() -> Unit,
    ) {
        val builder = MenuBuilder(ctx)
        builder.body()
        record(builder.build(TextBlock.Ref(prompt)))
    }

    fun menu(
        vararg promptLines: String,
        body: MenuBuilder.() -> Unit,
    ) {
        val builder = MenuBuilder(ctx)
        builder.body()
        record(builder.build(textLines(*promptLines)))
    }

    fun menu(
        prompt: TextBlock,
        body: MenuBuilder.() -> Unit,
    ) {
        val builder = MenuBuilder(ctx)
        builder.body()
        record(builder.build(prompt))
    }

    fun forEachMonster(
        includeDead: Boolean = false,
        body: ScriptScope.(MonsterRef) -> Unit,
    ) {
        val name = ctx.fresh("monster")
        val childOps =
            capture {
                body(MonsterRef(this, v(name)))
            }
        record(Op.ForEachMonster(name, includeDead, childOps))
    }

    fun forEachMember(
        includeDead: Boolean = false,
        body: ScriptScope.(MemberRef) -> Unit,
    ) {
        val name = ctx.fresh("member")
        val childOps =
            capture {
                body(MemberRef(this, v(name)))
            }
        record(Op.ForEachMember(name, includeDead, childOps))
    }

    fun healLivingMembers(amount: IntExpr) {
        forEachMember {
            it.hp.addClamped(amount, it.maxHp)
        }
    }

    fun healLivingMembers(amount: Int): Unit = healLivingMembers(lit(amount))

    fun runScene(
        scene: SceneId,
        vararg bindings: Pair<String, Any>,
    ) {
        record(Op.RunScene(scene, bindings.associate { it.first to render(it.second) }))
    }

    fun speak(building: BuildingId) {
        record(Op.Speak(building))
    }

    fun tellRumor(table: RumorTableId = DslDefaultRumorTables.standardTown) {
        record(Op.TellRumor(table))
    }

    fun encounter(
        monster: MonsterId,
        mandatory: Boolean = false,
        tier: EncounterTier? = null,
    ) {
        record(Op.Encounter(foe = monster, mandatory = mandatory, tier = tier))
    }

    fun enterDungeon(
        dungeon: DungeonId,
        levels: Int,
    ) {
        record(Op.EnterDungeon(dungeon, levels))
    }

    fun dungeonEntrance(
        dungeon: DungeonId,
        levels: Int,
        leaveClearsText: Boolean = true,
        prompt: StructuredTextBlockBuilder.() -> Unit,
    ) {
        clearText()
        choice.enterOrLeave(
            structuredText(prompt),
            enter = {
                enterDungeon(dungeon, levels)
                clearMapWindow()
                renderMap()
            },
            leave = {
                if (leaveClearsText) clearText()
            },
        )
    }

    fun dungeonItemFind(pool: ItemPoolId) {
        choice.randomItemOffer(pool) {
            free()
            choiceKeys(buy = inputKey("T"), decline = inputKey("L"))
            offer {
                line("You find a")
                line(itemName, ".")
                line("")
            }
            choicePrompt("(T)ake it", "(L)eave it")
            broke("No gold changes hands.")
            full("You can't carry more.")
            exactFullInventoryReject()
            deal("", "Ok.")
        }
    }

    fun randomDungeonItemFind(
        commonPools: List<ItemPoolId>,
        rarePool: ItemPoolId,
        rareRoll: IntRange = 0..4,
        rareValue: Int = 0,
    ) {
        require(commonPools.isNotEmpty()) { "random dungeon item find needs at least one common item pool" }
        val roll = localInt(random(rareRoll))
        whenever(roll eq rareValue) {
            dungeonItemFind(rarePool)
        } otherwise {
            choose(random(commonPools.indices)) {
                commonPools.forEachIndexed { index, pool ->
                    case(index) { dungeonItemFind(pool) }
                }
            }
        }
    }

    fun riddleGate(
        intro: TextBlock,
        riddle: TextBlock,
        answers: List<String>,
        guardian: MonsterId,
        success: TextBlock,
        failure: TextBlock,
    ) {
        readScreen(intro)
        screen(riddle)
        choice.textAnswer(
            prompt = "Answer: ",
            accepting = answers,
            maxLength = 15,
            match = {
                readScreen(success)
            },
            miss = {
                readScreen(failure)
                encounter(guardian, mandatory = true)
            },
        )
        clearText()
    }

    fun teleportParty(
        directionKey: IntExpr,
        distance: IntExpr,
    ) {
        record(Op.TeleportParty(directionKey.ast, distance.ast))
    }

    fun teleportParty(
        directionKey: Int,
        distance: Int,
    ) {
        teleportParty(lit(directionKey), lit(distance))
    }

    fun discoverCave(
        levels: IntExpr,
        dungeon: DungeonId = DslDefaultDungeons.randomCave,
        moveCost: IntExpr = lit(10),
    ) {
        record(Op.DiscoverCave(dungeon, levels.ast, moveCost.ast))
    }

    fun discoverCave(
        levels: Int,
        dungeon: DungeonId = DslDefaultDungeons.randomCave,
        moveCost: Int = 10,
    ) {
        discoverCave(lit(levels), dungeon, lit(moveCost))
    }

    fun consumeActiveItem(): Unit = record(Op.ConsumeActiveItem)

    fun reward(body: ScriptRewardBuilder.() -> Unit) {
        record(Op.GrantReward(ScriptRewardBuilder().apply(body).build()))
    }

    fun shopService(
        building: BuildingId,
        body: ShopServiceBuilder.() -> Unit,
    ) {
        record(Op.ShopService(ShopServiceBuilder(building).apply(body).build()))
    }

    fun achieve(milestone: GameMilestoneId) {
        record(Op.AchieveMilestone(milestone))
    }

    fun pay(
        amount: IntExpr,
        broke: ScriptScope.() -> Unit,
        ok: ScriptScope.() -> Unit,
    ) {
        whenever(party.gold lt amount) {
            broke()
        } otherwise {
            party.gold -= amount
            ok()
        }
    }

    internal fun capture(body: ScriptScope.() -> Unit): List<Op> = recordScript(ctx, body)
}

@RylNextDsl
@Suppress("unused")
class ScriptRewardBuilder {
    private var goldAst: IntAst? = null
    private val itemIds = mutableListOf<ItemId>()
    private var applicationModel = RewardApplicationBuilder().build()
    private var messagesModel = RewardMessageModel()

    fun gold(amount: Int) {
        goldAst = IntAst.Lit(amount)
    }

    fun gold(amount: IntExpr) {
        goldAst = amount.ast
    }

    fun items(vararg items: ItemId) {
        itemIds += items
    }

    fun placement(body: RewardApplicationBuilder.() -> Unit) {
        applicationModel = RewardApplicationBuilder(applicationModel).apply(body).build()
    }

    fun promptDrop(
        cantCarry: TextBlock,
        pickedUp: TextBlock? = null,
    ) {
        applicationModel = applicationModel.copy(overflow = RewardOverflowPolicy.PromptDropThenPlace)
        messagesModel = RewardMessageModel(cantCarry, pickedUp)
    }

    fun promptDrop(
        cantCarry: String,
        pickedUp: String? = null,
    ) {
        promptDrop(
            TextBlock.Inline(listOf(cantCarry)),
            pickedUp?.let { TextBlock.Inline(listOf(it)) },
        )
    }

    fun promptDrop(
        cantCarry: TextId,
        pickedUp: TextId? = null,
    ) {
        promptDrop(TextBlock.Ref(cantCarry), pickedUp?.let(TextBlock::Ref))
    }

    fun build(): ScriptRewardModel = ScriptRewardModel(goldAst, itemIds.toList(), applicationModel, messagesModel)
}

@RylNextDsl
class RandomItemOfferTextBuilder : TextLineSink {
    private val text = StructuredTextBlockBuilder()

    val itemName: TextPart = TextPart.OfferedItemName
    val itemPrice: TextPart = TextPart.OfferedItemPrice

    override fun addTextLine(line: TextLine) {
        text.addTextLine(line)
    }

    fun build(): TextBlock = text.build()
}

@RylNextDsl
@Suppress("unused")
class RandomItemOfferBuilder(
    private val pool: ItemPoolId,
) {
    private var offerText: TextBlock? = null
    private var choicePromptText: TextBlock = inlineText("(B)uy it", "(N)o thanks")
    private var brokeText: TextBlock? = null
    private var dealText: TextBlock? = null
    private var declinedText: TextBlock? = null
    private var applicationModel =
        RewardApplicationBuilder()
            .apply {
                overflow = RewardOverflowPolicy.PromptDropThenPlace
            }.build()
    private var messagesModel = RewardMessageModel()
    private var buyBinding: InputBindingModel = inputKey('B')
    private var declineBinding: InputBindingModel = inputKey('N')
    private var priceOverrideModel: Int? = null

    private var brokePauseMillis: Int = 800
    private var fullPauseMillis: Int = 1_000
    private var dealPauseMillis: Int = 800
    private var declinedPauseMillis: Int = 1_000

    fun offer(vararg lines: String) {
        offerText = inlineText(*lines)
    }

    fun offer(body: RandomItemOfferTextBuilder.() -> Unit) {
        offerText = RandomItemOfferTextBuilder().apply(body).build()
    }

    fun choicePrompt(vararg lines: String) {
        choicePromptText = inlineText(*lines)
    }

    fun choicePrompt(body: RandomItemOfferTextBuilder.() -> Unit) {
        choicePromptText = RandomItemOfferTextBuilder().apply(body).build()
    }

    fun choiceKeys(
        buy: InputBindingModel = buyBinding,
        decline: InputBindingModel = declineBinding,
    ) {
        buyBinding = buy
        declineBinding = decline
    }

    fun free() {
        priceOverrideModel = 0
    }

    fun price(amount: Int) {
        priceOverrideModel = amount.coerceAtLeast(0)
    }

    fun broke(vararg lines: String) {
        brokeText = inlineText(*lines)
    }

    fun broke(body: RandomItemOfferTextBuilder.() -> Unit) {
        brokeText = RandomItemOfferTextBuilder().apply(body).build()
    }

    fun full(vararg lines: String) {
        messagesModel = messagesModel.copy(cantCarry = inlineText(*lines))
    }

    fun full(body: RandomItemOfferTextBuilder.() -> Unit) {
        messagesModel = messagesModel.copy(cantCarry = RandomItemOfferTextBuilder().apply(body).build())
    }

    fun promptDrop(pickedUp: TextBlock? = null) {
        applicationModel = applicationModel.copy(overflow = RewardOverflowPolicy.PromptDropThenPlace)
        messagesModel = messagesModel.copy(pickedUp = pickedUp)
    }

    fun exactFullInventoryReject() {
        applicationModel = applicationModel.copy(overflow = RewardOverflowPolicy.SkipIfInventoryFull)
    }

    fun promptDrop(pickedUp: String) {
        promptDrop(inlineText(pickedUp))
    }

    fun promptDrop(pickedUp: TextId) {
        promptDrop(TextBlock.Ref(pickedUp))
    }

    fun deal(vararg lines: String) {
        dealText = inlineText(*lines)
    }

    fun deal(body: RandomItemOfferTextBuilder.() -> Unit) {
        dealText = RandomItemOfferTextBuilder().apply(body).build()
    }

    fun declined(vararg lines: String) {
        declinedText = inlineText(*lines)
    }

    fun declined(body: RandomItemOfferTextBuilder.() -> Unit) {
        declinedText = RandomItemOfferTextBuilder().apply(body).build()
    }

    fun build(): RandomItemOfferModel =
        RandomItemOfferModel(
            pool = pool,
            offer = requireNotNull(offerText) { "random item offer requires offer text" },
            choicePrompt = choicePromptText,
            broke = requireNotNull(brokeText) { "random item offer requires broke text" },
            deal = requireNotNull(dealText) { "random item offer requires deal text" },
            declined = declinedText,
            application = applicationModel,
            messages =
                messagesModel.copy(
                    cantCarry =
                        requireNotNull(messagesModel.cantCarry) {
                            "random item offer requires full/cant-carry text"
                        },
                ),
            buy = buyBinding,
            decline = declineBinding,
            priceOverride = priceOverrideModel,
            brokePauseMillis = brokePauseMillis,
            fullPauseMillis = fullPauseMillis,
            dealPauseMillis = dealPauseMillis,
            declinedPauseMillis = declinedPauseMillis,
        )
}

class ElseBranch(
    private val ctx: ScriptContext,
    private val finish: (List<Op>) -> Unit,
) {
    infix fun otherwise(body: ScriptScope.() -> Unit) {
        finish(recordScript(ctx, body))
    }
}

@RylNextDsl
class SwitchBuilder(
    private val ctx: ScriptContext,
) {
    val cases: MutableList<SwitchCase> = mutableListOf()

    fun case(
        value: Int,
        body: ScriptScope.() -> Unit,
    ) {
        cases += SwitchCase(value, recordScript(ctx, body))
    }

    fun default(body: ScriptScope.() -> Unit) {
        cases += SwitchCase(null, recordScript(ctx, body))
    }
}

@RylNextDsl
@Suppress("unused", "MemberVisibilityCanBePrivate")
class MenuBuilder(
    private val ctx: ScriptContext,
) {
    private val options = mutableListOf<MenuOption>()
    private var defaultOps = emptyList<Op>()

    fun option(
        key: Char,
        body: ScriptScope.() -> Unit,
    ) {
        option(inputKey(key), body)
    }

    fun option(
        binding: InputBindingModel,
        body: ScriptScope.() -> Unit,
    ) {
        options += MenuOption(binding, recordScript(ctx, body))
    }

    fun defaultOption(
        key: Char,
        body: ScriptScope.() -> Unit,
    ) {
        defaultOption(inputKey(key), body)
    }

    fun defaultOption(
        binding: InputBindingModel,
        body: ScriptScope.() -> Unit,
    ) {
        val bodyOps = recordScript(ctx, body)
        options += MenuOption(binding, bodyOps)
        defaultOps = bodyOps
    }

    fun default(body: ScriptScope.() -> Unit) {
        defaultOps = recordScript(ctx, body)
    }

    fun build(prompt: TextBlock): Op.Menu = Op.Menu(prompt, options.toList(), defaultOps)
}

@Suppress("unused")
class AskScope(
    private val script: ScriptScope,
) {
    fun key(
        prompt: TextBlock,
        vararg bindings: InputBindingModel,
    ): IntExpr {
        val uniqueBindings = bindings.toList()
        require(uniqueBindings.isNotEmpty()) { "key prompt must accept at least one key" }
        val name = script.fresh("key")
        script.record(Op.AskKey(prompt, name, uniqueBindings))
        return v(name)
    }

    fun key(
        prompt: TextBlock,
        vararg keys: Char,
    ): IntExpr = key(prompt, *keys.map(::inputKey).toTypedArray())

    fun digit(
        prompt: TextBlock,
        range: IntRange,
    ): IntExpr = key(prompt, *range.map { inputKey(it.toString()) }.toTypedArray())

    fun wildernessDirection(prompt: TextBlock): IntExpr {
        val keys = WildernessKeysModel()
        return key(prompt, keys.moveSouth, keys.moveWest, keys.moveEast, keys.moveNorth)
    }

    fun member(
        prompt: TextId,
        filter: MemberChoiceFilter = MemberChoiceFilter.Any,
        accepting: MemberChoiceFilter = filter,
    ): MemberRef = member(TextBlock.Ref(prompt), filter, accepting)

    fun member(
        prompt: TextBlock,
        filter: MemberChoiceFilter = MemberChoiceFilter.Any,
        accepting: MemberChoiceFilter = filter,
    ): MemberRef {
        val name = script.fresh("member")
        script.record(Op.AskMember(prompt, name, filter, accepting))
        return script.party.member(v(name))
    }

    fun inventorySlot(
        prompt: TextBlock,
        firstKey: Char = 'A',
        slots: IntRange = 1..InventorySaveSlotMax,
        filter: InventorySlotChoiceFilter = InventorySlotChoiceFilter.Any,
    ): InventorySlotRef {
        val name = script.fresh("inventory_slot")
        script.record(Op.AskInventorySlot(prompt, name, firstKey.uppercaseChar(), slots, filter))
        return InventorySlotRef(v(name))
    }

    fun monsterAlive(prompt: TextId): MonsterRef = monsterAlive(TextBlock.Ref(prompt))

    fun monsterAlive(prompt: TextBlock): MonsterRef {
        val name = script.fresh("monster")
        script.record(Op.AskMonster(prompt, name))
        return MonsterRef(script, v(name))
    }
}

@Suppress("unused", "MemberVisibilityCanBePrivate")
class ChoiceScope(
    private val script: ScriptScope,
) {
    fun yesNo(
        prompt: TextBlock,
        yes: ScriptScope.() -> Unit,
        no: ScriptScope.() -> Unit = {},
    ) {
        yesNo(prompt, StandardYesNoKeys, yes, no)
    }

    fun yesNo(
        prompt: TextBlock,
        keys: YesNoKeysModel,
        yes: ScriptScope.() -> Unit,
        no: ScriptScope.() -> Unit = {},
    ) {
        script.menu(prompt) {
            option(keys.yes, yes)
            option(keys.no, no)
            default(no)
        }
    }

    fun yesNo(
        vararg promptLines: String,
        yes: ScriptScope.() -> Unit,
        no: ScriptScope.() -> Unit = {},
    ) {
        yesNo(TextBlock.Inline(promptLines.toList()), yes, no)
    }

    fun takeOrLeave(
        prompt: TextBlock,
        take: ScriptScope.() -> Unit,
        leave: ScriptScope.() -> Unit,
    ) {
        script.menu(prompt) {
            option('T', take)
            option('L', leave)
        }
    }

    fun enterOrLeave(
        prompt: TextBlock,
        enter: ScriptScope.() -> Unit,
        leave: ScriptScope.() -> Unit = {},
    ) {
        script.menu(prompt) {
            option('E', enter)
            option('L', leave)
        }
    }

    fun payNoThanks(
        amount: IntExpr,
        broke: ScriptScope.() -> Unit,
        paid: ScriptScope.() -> Unit,
    ) {
        script.menu("(P)ay", "(N)o thanks") {
            option('P') {
                pay(amount, broke, paid)
            }
            option('N') {}
        }
    }

    fun randomItemOffer(
        pool: ItemPoolId,
        body: RandomItemOfferBuilder.() -> Unit,
    ) {
        script.record(Op.RandomItemOffer(RandomItemOfferBuilder(pool).apply(body).build()))
    }

    fun textAnswer(
        prompt: TextBlock,
        accepting: List<String>,
        caseSensitive: Boolean = false,
        trim: Boolean = true,
        maxLength: Int? = null,
        match: ScriptScope.() -> Unit,
        miss: ScriptScope.() -> Unit,
    ) {
        require(accepting.isNotEmpty()) { "text answer prompt must accept at least one value" }
        script.record(
            Op.AskText(
                prompt = prompt,
                accepted = accepting,
                matchOps = script.capture(match),
                missOps = script.capture(miss),
                caseSensitive = caseSensitive,
                trim = trim,
                maxLength = maxLength,
            ),
        )
    }

    fun textAnswer(
        prompt: String,
        accepting: List<String>,
        caseSensitive: Boolean = false,
        trim: Boolean = true,
        maxLength: Int? = null,
        match: ScriptScope.() -> Unit,
        miss: ScriptScope.() -> Unit,
    ) {
        textAnswer(inlineText(prompt), accepting, caseSensitive, trim, maxLength, match, miss)
    }

    fun member(
        prompt: TextId,
        filter: MemberChoiceFilter = MemberChoiceFilter.Any,
        accepting: MemberChoiceFilter = filter,
    ): MemberRef = script.ask.member(prompt, filter, accepting)

    fun member(
        prompt: TextBlock,
        filter: MemberChoiceFilter = MemberChoiceFilter.Any,
        accepting: MemberChoiceFilter = filter,
    ): MemberRef = script.ask.member(prompt, filter, accepting)

    fun member(
        prompt: TextId,
        filter: MemberChoiceFilter = MemberChoiceFilter.Any,
        accepting: MemberChoiceFilter = filter,
        body: ScriptScope.(MemberRef) -> Unit,
    ) {
        script.body(member(prompt, filter, accepting))
    }

    fun inventorySlot(
        prompt: TextBlock,
        firstKey: Char = 'A',
        slots: IntRange = 1..InventorySaveSlotMax,
        filter: InventorySlotChoiceFilter = InventorySlotChoiceFilter.Any,
        body: ScriptScope.(InventorySlotRef) -> Unit,
    ) {
        script.body(script.ask.inventorySlot(prompt, firstKey, slots, filter))
    }
}

data class InventorySlotRef(
    val index: IntExpr,
)

class ViewScope(
    private val script: ScriptScope,
) {
    fun clearText(): Unit = script.record(Op.View(ViewCommand.ClearText))

    fun clearMapWindow(): Unit = script.record(Op.View(ViewCommand.ClearMapWindow))

    fun renderMap(): Unit = script.record(Op.View(ViewCommand.RenderMap))

    fun showBackdrop(image: ImageId): Unit = showImage(image)

    private fun showImage(image: ImageId): Unit = script.record(Op.View(ViewCommand.ShowImage(image)))

    fun statusLine(): Unit = script.record(Op.View(ViewCommand.StatusLine))
}

internal fun recordScript(body: ScriptScope.() -> Unit): List<Op> = recordScript(ScriptContext(), body)

internal fun recordScript(
    ctx: ScriptContext,
    body: ScriptScope.() -> Unit,
): List<Op> =
    buildList {
        ctx.recording(this) {
            ScriptScope(ctx, this).body()
        }
    }

data class ItemCatalogDeclaration(
    val records: List<ItemRecordModel>,
    val pools: List<ItemPoolId>,
)

@RylNextDsl
class ItemPoolBuilder {
    private val items = mutableListOf<ItemId>()

    fun item(id: ItemId) {
        items += id
    }

    fun items(vararg ids: ItemId) {
        items += ids
    }

    internal fun build(): List<ItemId> = items.toList()
}

@RylNextDsl
class CampProtectionBuilder {
    private val bands = mutableSetOf<TemperatureBand>()

    fun protects(vararg bands: TemperatureBand) {
        this.bands += bands
    }

    internal fun build(): Set<TemperatureBand> = bands.toSet()
}

@RylNextDsl
class ItemRecordBuilder {
    private var image: ImageId? = null
    private var classes: Set<PcClass> = emptySet()
    private var protection: Set<TemperatureBand> = emptySet()
    private val capacityBonuses = mutableMapOf<Resource, Int>()

    fun equippableBy(vararg classes: PcClass) {
        this.classes = classes.toSet()
    }

    fun campImage(image: ImageId) {
        this.image = image
    }

    fun campProtection(body: CampProtectionBuilder.() -> Unit) {
        protection = CampProtectionBuilder().apply(body).build()
    }

    fun resourceCapacityBonus(
        resource: Resource,
        amount: Int,
    ) {
        capacityBonuses[resource] = amount
    }

    internal fun equippableByValue(): Set<PcClass> = classes

    internal fun campImageValue(): ImageId? = image

    internal fun campProtectionValue(): Set<TemperatureBand> = protection

    internal fun resourceCapacityBonusValue(): Map<Resource, Int> = capacityBonuses.toMap()
}

@RylNextDsl
class RealmContentBuilder {
    private val baseCapacity = mutableMapOf<Resource, Int>()
    private var upkeep = WildernessUpkeepRules()
    private var camp = CampRulesModel()
    private var hunt: HuntRulesModel? = null
    private var timeOfDayBands = defaultTimeOfDayBands()

    fun baseResourceCapacity(
        resource: Resource,
        amount: Int,
    ) {
        baseCapacity[resource] = amount
    }

    fun upkeep(body: WildernessUpkeepRulesBuilder.() -> Unit) {
        upkeep = WildernessUpkeepRulesBuilder(upkeep).apply(body).build()
    }

    fun camp(body: CampRulesBuilder.() -> Unit) {
        camp = CampRulesBuilder().apply(body).build()
    }

    fun hunt(body: HuntRulesBuilder.() -> Unit) {
        hunt = HuntRulesBuilder().apply(body).build()
    }

    fun timeOfDay(body: TimeOfDayBandsBuilder.() -> Unit) {
        timeOfDayBands = TimeOfDayBandsBuilder().apply(body).build()
    }

    internal fun build(): RealmContentModel =
        RealmContentModel(
            baseResourceCapacity = baseCapacity.toMap(),
            upkeep = upkeep,
            camp = camp,
            hunt = hunt,
            timeOfDay = timeOfDayBands,
        )
}

@RylNextDsl
class TimeOfDayBandsBuilder {
    private val bands = mutableListOf<TimeOfDayBandModel>()

    fun band(
        hours: IntRange,
        label: String,
    ) {
        bands += TimeOfDayBandModel(hours, label)
    }

    internal fun build(): List<TimeOfDayBandModel> = bands.toList()
}

@RylNextDsl
class WildernessUpkeepRulesBuilder(
    initial: WildernessUpkeepRules = WildernessUpkeepRules(),
) {
    var foodCostLeaderDiv: Int = initial.foodCostLeaderDiv
    var foodTickEvery: Int = initial.foodTickEvery
    var starveFoodThreshold: Int = initial.starveFoodThreshold
    var starveDamage: Int = initial.starveDamage
    var starvationDeath: TextBlock = initial.starvationDeath
    var manaRegenChance: Int = initial.manaRegenChance
    var restedHoursCostPerTurn: Int = initial.restedHoursCostPerTurn
    private val fatigue = initial.fatigue.toMutableList()

    /** Defines authored prose for a member death caused by wilderness starvation upkeep. */
    fun starvationDeath(body: StructuredTextBlockBuilder.() -> Unit) {
        starvationDeath = structuredText(body)
    }

    fun fatigueAt(
        restedHours: Int,
        text: TextBlock,
        drain: Boolean = false,
        statusLabel: String? = null,
    ) {
        fatigue +=
            WildernessFatigueRule(
                threshold = restedHours,
                text = text,
                drain = drain,
                statusLabel = statusLabel,
            )
    }

    fun fatigueBelow(
        restedHours: Int,
        text: TextBlock,
        drain: Boolean = false,
        statusLabel: String? = null,
    ) {
        fatigue +=
            WildernessFatigueRule(
                threshold = restedHours,
                text = text,
                match = WildernessFatigueMatch.LessThan,
                drain = drain,
                statusLabel = statusLabel,
            )
    }

    internal fun build(): WildernessUpkeepRules =
        WildernessUpkeepRules(
            foodCostLeaderDiv = foodCostLeaderDiv,
            foodTickEvery = foodTickEvery,
            starveFoodThreshold = starveFoodThreshold,
            starveDamage = starveDamage,
            starvationDeath = starvationDeath,
            manaRegenChance = manaRegenChance,
            restedHoursCostPerTurn = restedHoursCostPerTurn,
            fatigue = fatigue.toList(),
        )
}

@RylNextDsl
class CampRulesBuilder {
    var healthGain: Int = 0
    var foodCostPerMember: Int = 0
    var hoursAdvanced: Int = 0
    var hoursRestedReset: Int = 0
    var temperatureMax: Int = 0
    var ambushChancePercent: Int = 0
    var ambushTier: EncounterTier? = null
    var notTired: TextBlock = inlineText()
    var notEnoughFood: TextBlock = inlineText()
    var guardPrompt: TextBlock = inlineText()
    var guardMemberPrompt: TextBlock = inlineText()
    var guardMemberDead: TextBlock = inlineText()
    var standsGuard: TextBlock = inlineText()
    var sleeping: TextBlock = inlineText()
    var rested: TextBlock = inlineText()
    var ambush: TextBlock = inlineText()
    var surpriseKill: TextBlock = inlineText()
    private val bands = mutableListOf<TemperatureBandRange>()
    private val damage = mutableMapOf<TemperatureBand, Int>()
    private val temperatureMessage = mutableMapOf<TemperatureBand, TextBlock>()
    private val noShelterMessage = mutableMapOf<TemperatureBand, TextBlock>()

    fun temperatureBand(
        band: TemperatureBand,
        range: IntRange,
        damage: Int,
        message: TextBlock,
        noShelter: TextBlock,
    ) {
        bands += TemperatureBandRange(band, range)
        this.damage[band] = damage
        temperatureMessage[band] = message
        noShelterMessage[band] = noShelter
    }

    internal fun build(): CampRulesModel =
        CampRulesModel(
            healthGain = healthGain,
            foodCostPerMember = foodCostPerMember,
            hoursAdvanced = hoursAdvanced,
            hoursRestedReset = hoursRestedReset,
            temperatureMax = temperatureMax,
            temperatureBands = bands.toList(),
            temperatureDamage = damage.toMap(),
            ambushChancePercent = ambushChancePercent,
            ambushTier = ambushTier,
            notTired = notTired,
            notEnoughFood = notEnoughFood,
            guardPrompt = guardPrompt,
            guardMemberPrompt = guardMemberPrompt,
            guardMemberDead = guardMemberDead,
            standsGuard = standsGuard,
            sleeping = sleeping,
            rested = rested,
            ambush = ambush,
            surpriseKill = surpriseKill,
            temperatureMessage = temperatureMessage.toMap(),
            noShelterMessage = noShelterMessage.toMap(),
        )
}

@RylNextDsl
class HuntRulesBuilder {
    var successChancePercent: Int = 0
    var timeCost: Int = 0
    var foodMin: Int = 0
    var foodMax: Int = 0
    var whoPrompt: TextBlock = inlineText()
    var dead: TextBlock = inlineText()
    var notRanger: TextBlock = inlineText()
    var tooFewMoves: TextBlock = inlineText()
    var tries: TextBlock = inlineText()
    var succeeds: TextBlock = inlineText()
    var fails: TextBlock = inlineText()

    internal fun build(): HuntRulesModel =
        HuntRulesModel(
            successChancePercent = successChancePercent,
            timeCost = timeCost,
            foodMin = foodMin,
            foodMax = foodMax,
            whoPrompt = whoPrompt,
            dead = dead,
            notRanger = notRanger,
            tooFewMoves = tooFewMoves,
            tries = tries,
            succeeds = succeeds,
            fails = fails,
        )
}

@RylNextDsl
class ItemCatalogBuilder {
    private val records = mutableListOf<ItemRecordModel>()
    private val pools = mutableListOf<ItemPoolId>()

    fun empty(
        id: ItemId,
        name: String = "-",
        weaponDamage: Int = 1,
    ) {
        record(
            id = id,
            kind = ItemKind.Empty,
            name = name,
            cost = 0,
            storage = ItemStorageKind.EmptySentinel,
            weaponDamage = weaponDamage,
        )
    }

    fun weapon(
        id: ItemId,
        name: String,
        cost: Int,
        damage: Int,
    ) {
        record(id, ItemKind.Weapon, name, cost, weaponDamage = damage)
    }

    fun weapon(
        id: ItemId,
        name: String,
        cost: Int,
        damage: Int,
        body: ItemRecordBuilder.() -> Unit,
    ) {
        customizable(id, ItemKind.Weapon, name, cost, body, weaponDamage = damage)
    }

    fun armor(
        id: ItemId,
        name: String,
        cost: Int,
        armorClass: Int,
    ) {
        record(id, ItemKind.Armor, name, cost, armorClass = armorClass)
    }

    fun armor(
        id: ItemId,
        name: String,
        cost: Int,
        armorClass: Int,
        body: ItemRecordBuilder.() -> Unit,
    ) {
        customizable(id, ItemKind.Armor, name, cost, body, armorClass = armorClass)
    }

    fun gear(
        id: ItemId,
        name: String,
        cost: Int,
    ) {
        record(id, ItemKind.Gear, name, cost)
    }

    fun gear(
        id: ItemId,
        name: String,
        cost: Int,
        body: ItemRecordBuilder.() -> Unit,
    ) {
        customizable(id, ItemKind.Gear, name, cost, body)
    }

    fun potion(
        id: ItemId,
        name: String,
        cost: Int,
    ) {
        record(
            id = id,
            kind = ItemKind.Potion,
            name = name,
            cost = cost,
        )
    }

    fun treasure(
        id: ItemId,
        name: String,
        cost: Int,
    ) {
        record(id, ItemKind.Treasure, name, cost)
    }

    fun treasure(
        id: ItemId,
        name: String,
        cost: Int,
        body: ItemRecordBuilder.() -> Unit,
    ) {
        customizable(id, ItemKind.Treasure, name, cost, body)
    }

    private fun customizable(
        id: ItemId,
        kind: ItemKind,
        name: String,
        cost: Int,
        body: ItemRecordBuilder.() -> Unit,
        weaponDamage: Int? = null,
        armorClass: Int? = null,
    ) {
        val builder = ItemRecordBuilder().apply(body)
        record(
            id = id,
            kind = kind,
            name = name,
            cost = cost,
            weaponDamage = weaponDamage,
            armorClass = armorClass,
            equippableBy = builder.equippableByValue(),
            campImage = builder.campImageValue(),
            campProtection = builder.campProtectionValue(),
            resourceCapacityBonus = builder.resourceCapacityBonusValue(),
        )
    }

    fun special(
        id: ItemId,
        name: String,
    ) {
        record(id, ItemKind.Special, name, cost = 0, storage = ItemStorageKind.SpecialMilestone)
    }

    fun magicItem(
        id: ItemId,
        name: String,
        cost: Int,
    ) {
        record(id, ItemKind.MagicItem, name, cost)
    }

    fun pool(
        id: ItemPoolId,
        vararg items: ItemId,
    ) {
        pool(id, items.toList())
    }

    fun pool(
        id: ItemPoolId,
        items: List<ItemId>,
    ) {
        pools += itemPool(id.slug, items)
    }

    fun pool(
        id: ItemPoolId,
        body: ItemPoolBuilder.() -> Unit,
    ) {
        pool(id, ItemPoolBuilder().apply(body).build())
    }

    private fun record(
        id: ItemId,
        kind: ItemKind,
        name: String,
        cost: Int,
        storage: ItemStorageKind = ItemStorageKind.SlottedInventory,
        weaponDamage: Int? = null,
        armorClass: Int? = null,
        equippableBy: Set<PcClass> = emptySet(),
        campImage: ImageId? = null,
        campProtection: Set<TemperatureBand> = emptySet(),
        resourceCapacityBonus: Map<Resource, Int> = emptyMap(),
    ) {
        records +=
            ItemRecordModel(
                id = id,
                kind = kind,
                displayName = name,
                cost = cost,
                storage = storage,
                weaponDamage = weaponDamage,
                armorClass = armorClass,
                equippableBy = equippableBy,
                campImage = campImage,
                campProtection = campProtection,
                resourceCapacityBonus = resourceCapacityBonus,
            )
    }

    internal fun build(): ItemCatalogDeclaration = ItemCatalogDeclaration(records.toList(), pools.toList())
}

@RylNextDsl
class TerminalPresentationBuilder {
    private val terrainStyles = mutableListOf<TerminalTerrainStyleModel>()

    fun terrain(
        id: TerrainId,
        glyph: String,
        color: PaletteColorId,
    ) {
        terrainStyles += TerminalTerrainStyleModel(id, glyph, color)
    }

    fun build(): TerminalPresentationModel = TerminalPresentationModel(terrain = terrainStyles.toList())
}

@RylNextDsl
class LayoutBuilder(
    initial: LayoutModel = LayoutModel(),
) {
    private val screenRegions = initial.regions.toMutableList()

    fun region(
        id: ScreenRegionId,
        x: Int,
        y: Int,
        width: Int? = null,
        height: Int? = null,
    ) {
        screenRegions += ScreenRegionModel(id, x, y, width, height)
    }

    fun build(): LayoutModel = LayoutModel(regions = screenRegions.toList())
}

@RylNextDsl
class GameMetadataBuilder(
    initial: GameMetadataModel = GameMetadataModel(),
) {
    private var universeModel = initial.universe

    fun universe(
        name: String,
        version: String,
    ) {
        universeModel = GameUniverseModel(name = name, version = version)
    }

    internal fun build(): GameMetadataModel = GameMetadataModel(universe = universeModel)
}

@RylNextDsl
class GameLimitsBuilder(
    initial: GameLimitsModel = GameLimitsModel(),
) {
    var maxWorldMaps: Int? = initial.maxWorldMaps

    internal fun build(): GameLimitsModel =
        GameLimitsModel(
            maxWorldMaps = maxWorldMaps,
        )
}

@RylNextDsl
class TerrainCatalogBuilder(
    initial: TerrainCatalogModel = TerrainCatalogModel(),
) {
    private val terrainModels = initial.terrain.toMutableList()

    fun terrain(
        id: TerrainId,
        mapCode: Char? = id.sourceMapCode,
        body: TerrainSemanticsBuilder.() -> Unit = {},
    ) {
        val tile = requireNotNull(mapCode) { "terrain ${id.slug} requires a map code" }
        terrainModels += TerrainSemanticsBuilder(id, tile).apply(body).build()
    }

    fun build(): TerrainCatalogModel = TerrainCatalogModel(terrain = terrainModels.toList())
}

@RylNextDsl
class TerrainSemanticsBuilder(
    private val id: TerrainId,
    private val mapCode: Char,
) {
    var passable: Boolean = true
    private var movementCostModel: Int = 1
    private var allowsNpcPlacementModel: Boolean? = null
    private var settlementModel: TerrainSettlement? = null

    fun blocksMovement() {
        passable = false
        movementCostModel = 0
        allowsNpcPlacementModel = false
    }

    fun settlement(kind: TerrainSettlement) {
        settlementModel = kind
        allowsNpcPlacementModel = false
    }

    fun build(): TerrainSemanticsModel {
        val placement = allowsNpcPlacementModel ?: (passable && settlementModel == null)
        return TerrainSemanticsModel(
            id = id,
            mapCode = mapCode,
            passable = passable,
            movementCost = movementCostModel,
            allowsNpcPlacement = placement,
            settlement = settlementModel,
            tags = emptySet(),
        )
    }
}

private fun RewardBundleModel.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): RewardBundleModel =
    copy(loot = loot.copy(treasure = resolve(loot.treasure), magic = resolve(loot.magic)))

private fun SceneModel.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): SceneModel = copy(ops = ops.resolveItemPools(resolve))

private fun DialogueModel.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): DialogueModel =
    copy(branches = branches.map { it.copy(ops = it.ops.resolveItemPools(resolve)) })

private fun SpeakSystemModel.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): SpeakSystemModel =
    copy(questionActions = questionActions.map { it.copy(ops = it.ops.resolveItemPools(resolve)) })

private fun SpellModel.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): SpellModel = copy(ops = ops.resolveItemPools(resolve))

private fun ItemUseModel.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): ItemUseModel = copy(ops = ops.resolveItemPools(resolve))

private fun DungeonSystemModel.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): DungeonSystemModel =
    copy(events = events.map { it.copy(ops = it.ops.resolveItemPools(resolve)) })

private fun List<Op>.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): List<Op> = map { it.resolveItemPools(resolve) }

private fun List<SwitchCase>.resolveSwitchItemPools(resolve: (ItemPoolId) -> ItemPoolId): List<SwitchCase> =
    map { it.copy(body = it.body.resolveItemPools(resolve)) }

private fun List<MenuOption>.resolveMenuItemPools(resolve: (ItemPoolId) -> ItemPoolId): List<MenuOption> =
    map { it.copy(body = it.body.resolveItemPools(resolve)) }

private fun Op.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): Op =
    when (this) {
        is Op.LetInt -> copy(value = value.resolveItemPools(resolve))
        is Op.Set -> copy(target = target.resolveItemPools(resolve), value = value.resolveItemPools(resolve))
        is Op.Add -> copy(target = target.resolveItemPools(resolve), value = value.resolveItemPools(resolve))
        is Op.Sub -> copy(target = target.resolveItemPools(resolve), value = value.resolveItemPools(resolve))
        is Op.AddClamped ->
            copy(
                target = target.resolveItemPools(resolve),
                value = value.resolveItemPools(resolve),
                max = max.resolveItemPools(resolve),
            )
        is Op.If ->
            copy(
                cond = cond.resolveItemPools(resolve),
                thenOps = thenOps.resolveItemPools(resolve),
                elseOps = elseOps.resolveItemPools(resolve),
            )
        is Op.Loop -> copy(body = body.resolveItemPools(resolve))
        is Op.Switch -> copy(expr = expr.resolveItemPools(resolve), cases = cases.resolveSwitchItemPools(resolve))
        is Op.Menu ->
            copy(
                options = options.resolveMenuItemPools(resolve),
                defaultOps = defaultOps.resolveItemPools(resolve),
            )
        is Op.AskKey -> copy()
        is Op.AskMember -> copy()
        is Op.AskInventorySlot -> copy()
        is Op.AskText ->
            copy(
                matchOps = matchOps.resolveItemPools(resolve),
                missOps = missOps.resolveItemPools(resolve),
            )
        is Op.ForEachMember -> copy(body = body.resolveItemPools(resolve))
        is Op.ForEachMonster -> copy(body = body.resolveItemPools(resolve))
        is Op.ConsumeInventoryPool -> copy(pool = resolve(pool), quantity = quantity.resolveItemPools(resolve))
        is Op.TeleportParty ->
            copy(
                directionKey = directionKey.resolveItemPools(resolve),
                distance = distance.resolveItemPools(resolve),
            )
        is Op.DiscoverCave ->
            copy(
                levels = levels.resolveItemPools(resolve),
                moveCost = moveCost.resolveItemPools(resolve),
            )
        is Op.GrantReward -> copy(reward = reward.resolveItemPools(resolve))
        is Op.RandomItemOffer -> copy(offer = offer.copy(pool = resolve(offer.pool)))
        else -> this
    }

private fun ScriptRewardModel.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): ScriptRewardModel =
    copy(gold = gold?.resolveItemPools(resolve))

private fun LValueAst.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): LValueAst =
    when (this) {
        is LValueAst.MemberField -> copy(member = member.resolveItemPools(resolve))
        is LValueAst.MonsterField -> copy(monster = monster.resolveItemPools(resolve))
        else -> this
    }

private fun IntAst.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): IntAst =
    when (this) {
        is IntAst.Binary -> copy(left = left.resolveItemPools(resolve), right = right.resolveItemPools(resolve))
        is IntAst.If ->
            copy(
                cond = cond.resolveItemPools(resolve),
                thenValue = thenValue.resolveItemPools(resolve),
                elseValue = elseValue.resolveItemPools(resolve),
            )
        is IntAst.Random -> copy(lo = lo.resolveItemPools(resolve), hi = hi.resolveItemPools(resolve))
        is IntAst.MemberField -> copy(member = member.resolveItemPools(resolve))
        is IntAst.MonsterField -> copy(monster = monster.resolveItemPools(resolve))
        is IntAst.InventoryPoolCount -> copy(pool = resolve(pool))
        is IntAst.InventorySlotCost -> copy(slot = slot.resolveItemPools(resolve))
        else -> this
    }

private fun BoolAst.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): BoolAst =
    when (this) {
        is BoolAst.Cmp -> copy(left = left.resolveItemPools(resolve), right = right.resolveItemPools(resolve))
        is BoolAst.Not -> copy(expr = expr.resolveItemPools(resolve))
        is BoolAst.And -> copy(left = left.resolveItemPools(resolve), right = right.resolveItemPools(resolve))
        is BoolAst.Or -> copy(left = left.resolveItemPools(resolve), right = right.resolveItemPools(resolve))
        is BoolAst.MemberFlag -> copy(member = member.resolveItemPools(resolve))
        is BoolAst.MonsterFlag -> copy(monster = monster.resolveItemPools(resolve))
        is BoolAst.RuntimeFlag -> this
        else -> this
    }

@RylNextDsl
class GameBuilder {
    private var metadataModel = GameMetadataModel()
    private var limitsModel = GameLimitsModel()
    private val itemRecords = mutableListOf<ItemRecordModel>()
    private val itemPools = mutableListOf<ItemPoolId>()
    private var realmModel = RealmContentModel()
    private var promptsModel = GamePromptsModel()
    private var pacingModel = GamePacingModel()
    private var visualsModel = VisualsModel()
    private val worldMaps = mutableListOf<WorldMapModel>()
    private var terrainModel = TerrainCatalogModel()
    private var terminalPresentationModel = TerminalPresentationModel()
    private var layoutModel = LayoutModel()
    private val authoredFlags = mutableListOf<FlagContentModel>()
    private var progressModel = GameProgressModel(emptyList(), null)
    private var levelingModel: LevelingModel? = null
    private val formulasModel = mutableListOf<FormulaModel>()
    private val rewardsModel = mutableListOf<RewardBundleModel>()
    private var combatModel = CombatSystemModel(null)
    private var characterCreationModel: CharacterCreationModel? = null
    private var wildernessModel = WildernessSystemModel()
    private var dungeonsModel = DungeonSystemModel()
    private val castleModels = mutableListOf<CastleContentModel>()
    private var speakModel = SpeakSystemModel(null)
    private val towns = mutableListOf<TownModel>()
    private var townSystemModel: TownSystemModel? = null
    private val characters = mutableListOf<CharacterModel>()
    private val monsters = mutableListOf<MonsterModel>()
    private val scenes = mutableListOf<SceneModel>()
    private val dialogue = mutableListOf<DialogueModel>()
    private val spells = mutableListOf<SpellModel>()
    private val itemUses = mutableListOf<ItemUseModel>()
    private val missions = mutableListOf<MissionModel>()
    private var secretsModel = SecretSystemModel()

    fun metadata(body: GameMetadataBuilder.() -> Unit) {
        metadataModel = GameMetadataBuilder(metadataModel).apply(body).build()
    }

    fun limits(body: GameLimitsBuilder.() -> Unit) {
        limitsModel = GameLimitsBuilder(limitsModel).apply(body).build()
    }

    fun flags(body: FlagContentBuilder.() -> Unit) {
        authoredFlags += FlagContentBuilder().apply(body).build()
    }

    fun items(body: ItemCatalogBuilder.() -> Unit) {
        val declaration = ItemCatalogBuilder().apply(body).build()
        itemRecords += declaration.records
        itemPools += declaration.pools
    }

    fun realm(body: RealmContentBuilder.() -> Unit) {
        realmModel = RealmContentBuilder().apply(body).build()
    }

    fun prompts(body: GamePromptsBuilder.() -> Unit) {
        promptsModel = GamePromptsBuilder(promptsModel).apply(body).build()
    }

    fun pacing(body: GamePacingBuilder.() -> Unit) {
        pacingModel = GamePacingBuilder(pacingModel).apply(body).build()
    }

    fun visuals(body: VisualsBuilder.() -> Unit) {
        visualsModel = VisualsBuilder(visualsModel).apply(body).build()
    }

    fun worldMap(
        id: WorldMapId,
        body: WorldMapBuilder.() -> Unit,
    ) {
        worldMaps += WorldMapBuilder(id).apply(body).build()
    }

    fun terrain(body: TerrainCatalogBuilder.() -> Unit) {
        terrainModel = TerrainCatalogBuilder(TerrainCatalogModel(emptyList())).apply(body).build()
    }

    fun terminal(body: TerminalPresentationBuilder.() -> Unit) {
        terminalPresentationModel = TerminalPresentationBuilder().apply(body).build()
    }

    fun layouts(body: LayoutBuilder.() -> Unit) {
        layoutModel = LayoutBuilder(layoutModel).apply(body).build()
    }

    fun gameProgress(body: GameProgressBuilder.() -> Unit) {
        progressModel = GameProgressBuilder().apply(body).build()
    }

    fun leveling(body: LevelingBuilder.() -> Unit) {
        levelingModel = LevelingBuilder().apply(body).build()
    }

    fun formulas(body: FormulaCatalogBuilder.() -> Unit) {
        formulasModel += FormulaCatalogBuilder().apply(body).build()
    }

    fun rewards(body: RewardCatalogBuilder.() -> Unit) {
        rewardsModel += RewardCatalogBuilder().apply(body).build()
    }

    fun combat(body: CombatBuilder.() -> Unit) {
        combatModel = CombatBuilder().apply(body).build()
    }

    fun characterCreation(body: CharacterCreationBuilder.() -> Unit) {
        characterCreationModel = CharacterCreationBuilder().apply(body).build()
    }

    fun wilderness(body: WildernessSystemBuilder.() -> Unit) {
        wildernessModel = WildernessSystemBuilder().apply(body).build()
    }

    fun secrets(body: SecretSystemBuilder.() -> Unit) {
        secretsModel = SecretSystemBuilder(secretsModel).apply(body).build()
    }

    fun dungeons(body: DungeonSystemBuilder.() -> Unit) {
        dungeonsModel = DungeonSystemBuilder().apply(body).build()
    }

    fun castle(
        id: CastleId,
        body: CastleBuilder.() -> Unit,
    ) {
        castleModels += CastleBuilder(id).apply(body).build()
    }

    fun speak(body: SpeakBuilder.() -> Unit) {
        speakModel = SpeakBuilder().apply(body).build()
    }

    fun towns(body: TownCatalogBuilder.() -> Unit) {
        towns += TownCatalogBuilder().apply(body).build()
    }

    fun townSystem(body: TownSystemBuilder.() -> Unit) {
        townSystemModel = TownSystemBuilder().apply(body).build()
    }

    fun characters(body: CharacterCatalogBuilder.() -> Unit) {
        characters += CharacterCatalogBuilder().apply(body).build()
    }

    fun monsters(body: MonsterCatalogBuilder.() -> Unit) {
        monsters += MonsterCatalogBuilder().apply(body).build()
    }

    fun missions(body: MissionCatalogBuilder.() -> Unit) {
        missions += MissionCatalogBuilder().apply(body).build()
    }

    fun scene(
        id: SceneId,
        body: ScriptScope.() -> Unit,
    ) {
        scenes += SceneModel(id, recordScript(body))
    }

    fun dialogue(body: DialogueCatalogBuilder.() -> Unit) {
        dialogue += DialogueCatalogBuilder().apply(body).build()
    }

    fun spell(
        id: SpellId,
        slot: Int,
        baseCost: Int,
        name: String,
        body: ScriptScope.() -> Unit,
    ) {
        spells += SpellModel(id, slot, baseCost, name, recordScript(body))
    }

    fun itemUse(
        id: ItemId,
        body: ItemUseBuilder.() -> Unit,
    ) {
        itemUses += ItemUseBuilder(id).apply(body).build()
    }

    fun build(): GameModel {
        val itemPoolBySlug = itemPools.associateBy { it.slug }

        fun resolveItemPool(pool: ItemPoolId): ItemPoolId = itemPoolBySlug[pool.slug] ?: pool

        val castles = castleModels.toList()
        return GameModel(
            metadata = metadataModel,
            limits = limitsModel,
            itemCatalog = itemRecords.toList(),
            itemPools = itemPools.toList(),
            realm = realmModel,
            prompts = promptsModel,
            pacing = pacingModel,
            visuals = visualsModel,
            worldMaps = worldMaps.toList(),
            terrain = terrainModel,
            terminal = terminalPresentationModel,
            layouts = layoutModel,
            flags = declaredFlags.toList(),
            authoredFlags = authoredFlags.toList(),
            progress = progressModel,
            leveling = levelingModel,
            formulas = formulasModel.toList(),
            rewards = rewardsModel.map { it.resolveItemPools(::resolveItemPool) },
            combat = combatModel,
            characterCreation = characterCreationModel,
            wilderness = wildernessModel,
            dungeons = dungeonsModel.resolveItemPools(::resolveItemPool),
            castles = castles,
            speak = speakModel.resolveItemPools(::resolveItemPool),
            towns = towns.toList(),
            townSystem = townSystemModel,
            characters = characters.toList(),
            monsters = monsters.toList(),
            scenes = scenes.map { it.resolveItemPools(::resolveItemPool) },
            dialogue = dialogue.map { it.resolveItemPools(::resolveItemPool) },
            spells = spells.map { it.resolveItemPools(::resolveItemPool) },
            itemUses = itemUses.map { it.resolveItemPools(::resolveItemPool) },
            missions = missions.toList(),
            secrets = secretsModel,
        )
    }
}

@RylNextDsl
@Suppress("unused")
class SecretSystemBuilder(
    initial: SecretSystemModel = SecretSystemModel(),
) {
    private val players = initial.players.toMutableList()

    fun playerNamed(
        name: String,
        body: SecretPlayerBuilder.() -> Unit,
    ) {
        players += SecretPlayerBuilder(SecretPlayerMatcherModel.Named(listOf(name))).apply(body).build()
    }

    fun playersNamed(
        vararg names: String,
        body: SecretPlayerBuilder.() -> Unit,
    ) {
        players += SecretPlayerBuilder(SecretPlayerMatcherModel.Named(names.toList())).apply(body).build()
    }

    fun playerAny(body: SecretPlayerBuilder.() -> Unit) {
        players += SecretPlayerBuilder(SecretPlayerMatcherModel.Any).apply(body).build()
    }

    fun build(): SecretSystemModel = SecretSystemModel(players = players.toList())
}

@RylNextDsl
class SecretPlayerBuilder(
    private val matcher: SecretPlayerMatcherModel,
) {
    private val keyActions = mutableListOf<SecretKeyActionModel>()
    private var stepTrigger: SecretStepTriggerModel? = null
    private var alwaysRunFromNonMandatoryEncounters: Boolean = false

    fun addTimeLeft(
        key: Char,
        amount: Int,
        refreshStatus: Boolean = false,
    ) {
        onKey(key, SecretActionModel.AddTimeLeft(amount, refreshStatus))
    }

    fun addGold(
        key: Char,
        amount: Int,
        refreshStatus: Boolean = false,
    ) {
        onKey(key, SecretActionModel.AddGold(amount, refreshStatus))
    }

    fun setInventory(
        key: Char,
        slot: Int,
        item: ItemId,
    ) {
        onKey(key, SecretActionModel.SetInventory(slot, item))
    }

    fun spawnEncounterEvery(
        everySteps: Int,
        monster: MonsterId,
    ) {
        stepTrigger = SecretStepTriggerModel(everySteps, SecretActionModel.SpawnEncounter(monster))
    }

    fun alwaysRunFromNonMandatoryEncounters(enabled: Boolean = true) {
        alwaysRunFromNonMandatoryEncounters = enabled
    }

    private fun onKey(
        key: Char,
        action: SecretActionModel,
    ) {
        keyActions += SecretKeyActionModel(inputKey(key, label = key.toString()), action)
    }

    fun build(): SecretPlayerModel =
        SecretPlayerModel(
            matcher = matcher,
            keyActions = keyActions.toList(),
            stepTrigger = stepTrigger,
            alwaysRunFromNonMandatoryEncounters = alwaysRunFromNonMandatoryEncounters,
        )
}

@RylNextDsl
class WorldMapBuilder(
    private val id: WorldMapId,
) {
    private val layerModels = mutableListOf<WorldMapLayerModel>()

    fun layer(
        id: MapLayerId,
        body: WorldMapLayerBuilder.() -> Unit,
    ) {
        layerModels += WorldMapLayerBuilder(id).apply(body).build()
    }

    fun build(): WorldMapModel =
        WorldMapModel(
            id = id,
            layers = layerModels.toList(),
        )
}

@RylNextDsl
class WorldMapLayerBuilder(
    private val id: MapLayerId,
) {
    private var rowModels = emptyList<String>()

    fun rows(vararg rows: String) {
        rowModels = rows.toList()
    }

    fun rows(rows: String) {
        rowModels =
            rows
                .trimIndent()
                .lines()
                .dropWhile(String::isBlank)
                .dropLastWhile(String::isBlank)
    }

    fun rows(rows: List<String>) {
        rowModels = rows.toList()
    }

    fun build(): WorldMapLayerModel = WorldMapLayerModel(id = id, rows = rowModels)
}

@RylNextDsl
class VisualsBuilder(
    initial: VisualsModel = VisualsModel(),
) {
    private val imageModels = initial.images.toMutableList()
    private val staticScreenModels = initial.staticScreens.toMutableList()

    fun images(body: ImageAssetsBuilder.() -> Unit) {
        imageModels += ImageAssetsBuilder().apply(body).build()
    }

    fun staticScreens(body: StaticScreensBuilder.() -> Unit) {
        staticScreenModels += StaticScreensBuilder().apply(body).build()
    }

    fun build(): VisualsModel =
        VisualsModel(
            images = imageModels.toList(),
            staticScreens = staticScreenModels.toList(),
        )
}

@RylNextDsl
class ImageAssetsBuilder {
    private val images = mutableListOf<ImageAssetModel>()

    fun ansi(
        id: ImageId,
        resource: String,
        placement: ImagePlacement,
        transition: ImageTransition = ImageTransition.None,
    ) {
        images +=
            ImageAssetModel(
                id = id,
                kind = ImageAssetKind.Ansi,
                resource = resource,
                placement = placement,
                transition = transition,
            )
    }

    fun bitmap(
        id: ImageId,
        resource: String,
        placement: ImagePlacement,
        transition: ImageTransition = ImageTransition.None,
    ) {
        images +=
            ImageAssetModel(
                id = id,
                kind = ImageAssetKind.Bitmap,
                resource = resource,
                placement = placement,
                transition = transition,
            )
    }

    fun build(): List<ImageAssetModel> = images.toList()
}

@RylNextDsl
class StaticScreensBuilder {
    private val screens = mutableListOf<StaticScreenModel>()

    fun screen(
        id: StaticScreenId,
        body: StaticScreenBuilder.() -> Unit,
    ) {
        screens += StaticScreenBuilder(id).apply(body).build()
    }

    fun image(
        id: StaticScreenId,
        staticImage: ImageId,
    ) {
        screen(id) { image(staticImage) }
    }

    fun resource(
        id: StaticScreenId,
        path: String,
    ) {
        screen(id) { resource(path) }
    }

    fun commandHelp(id: StaticScreenId) {
        screen(id) { commandHelp() }
    }

    fun build(): List<StaticScreenModel> = screens.toList()
}

@RylNextDsl
class StaticScreenBuilder(
    private val id: StaticScreenId,
) {
    private var imageModel: ImageId? = null
    private var resourcePath: String? = null
    private var usesCommandHelp = false
    private var runtimeReportValue: StaticScreenRuntimeReport? = null

    fun image(image: ImageId) {
        imageModel = image
    }

    fun resource(path: String) {
        resourcePath = path
    }

    fun commandHelp() {
        usesCommandHelp = true
    }

    fun scoreReport() {
        runtimeReportValue = StaticScreenRuntimeReport.Score
    }

    fun verboseScoreReport() {
        runtimeReportValue = StaticScreenRuntimeReport.VerboseScore
    }

    fun hallOfHeroesReport() {
        runtimeReportValue = StaticScreenRuntimeReport.HallOfHeroes
    }

    fun build(): StaticScreenModel =
        StaticScreenModel(
            id = id,
            image = imageModel,
            resource = resourcePath,
            commandHelp = usesCommandHelp,
            runtimeReport = runtimeReportValue,
        )
}

@RylNextDsl
class GamePromptsBuilder(
    initial: GamePromptsModel = GamePromptsModel(),
) {
    private var pauseVerbList = initial.pauseVerbs
    private var returnKeyName: String = initial.returnKeyName
    private var titleMenu = initial.titleMenu
    private var priest = initial.priest
    private var inventoryUse = initial.inventoryUse
    private var equipment = initial.equipment
    private var save = initial.save
    private var town = initial.town
    private var wilderness = initial.wilderness
    private var dungeon = initial.dungeon
    private var party = initial.party
    private var characterCreation = initial.characterCreation

    fun pauseVerbs(vararg verbs: String) {
        pauseVerbList = verbs.toList()
    }

    fun pauseVerbs(verbs: List<String>) {
        pauseVerbList = verbs.toList()
    }

    fun titleMenu(body: TitleMenuPromptsBuilder.() -> Unit) {
        titleMenu = TitleMenuPromptsBuilder(titleMenu).apply(body).build()
    }

    fun priest(body: PriestSpellPromptsBuilder.() -> Unit) {
        priest = PriestSpellPromptsBuilder(priest).apply(body).build()
    }

    fun inventoryUse(body: InventoryUsePromptsBuilder.() -> Unit) {
        inventoryUse = InventoryUsePromptsBuilder(inventoryUse).apply(body).build()
    }

    fun equipment(body: EquipmentPromptsBuilder.() -> Unit) {
        equipment = EquipmentPromptsBuilder(equipment).apply(body).build()
    }

    fun save(body: SavePromptsBuilder.() -> Unit) {
        save = SavePromptsBuilder(save).apply(body).build()
    }

    fun town(body: TownPromptsBuilder.() -> Unit) {
        town = TownPromptsBuilder(town).apply(body).build()
    }

    fun wilderness(body: WildernessPromptsBuilder.() -> Unit) {
        wilderness = WildernessPromptsBuilder(wilderness).apply(body).build()
    }

    fun dungeon(body: DungeonPromptsBuilder.() -> Unit) {
        dungeon = DungeonPromptsBuilder(dungeon).apply(body).build()
    }

    fun party(body: PartyPromptsBuilder.() -> Unit) {
        party = PartyPromptsBuilder(party).apply(body).build()
    }

    fun characterCreation(body: CharacterCreationPromptsBuilder.() -> Unit) {
        characterCreation = CharacterCreationPromptsBuilder(characterCreation).apply(body).build()
    }

    fun build(): GamePromptsModel =
        GamePromptsModel(
            pauseVerbs = pauseVerbList,
            returnKeyName = returnKeyName,
            titleMenu = titleMenu,
            priest = priest,
            inventoryUse = inventoryUse,
            equipment = equipment,
            save = save,
            town = town,
            wilderness = wilderness,
            dungeon = dungeon,
            party = party,
            characterCreation = characterCreation,
        )
}

@RylNextDsl
class GamePacingBuilder(
    initial: GamePacingModel = GamePacingModel(),
) {
    private var battle = initial.battle
    private var characterCreation = initial.characterCreation
    private var shop = initial.shop
    private var speak = initial.speak
    private var wilderness = initial.wilderness

    fun battle(body: BattlePacingBuilder.() -> Unit) {
        battle = BattlePacingBuilder(battle).apply(body).build()
    }

    fun characterCreation(body: CharacterCreationPacingBuilder.() -> Unit) {
        characterCreation = CharacterCreationPacingBuilder(characterCreation).apply(body).build()
    }

    fun shop(body: ShopPacingBuilder.() -> Unit) {
        shop = ShopPacingBuilder(shop).apply(body).build()
    }

    fun speak(body: SpeakPacingBuilder.() -> Unit) {
        speak = SpeakPacingBuilder(speak).apply(body).build()
    }

    fun wilderness(body: WildernessPacingBuilder.() -> Unit) {
        wilderness = WildernessPacingBuilder(wilderness).apply(body).build()
    }

    fun build(): GamePacingModel =
        GamePacingModel(
            battle = battle,
            characterCreation = characterCreation,
            shop = shop,
            speak = speak,
            wilderness = wilderness,
        )
}

@RylNextDsl
class BattlePacingBuilder(
    initial: BattlePacingModel = BattlePacingModel(),
) {
    var messagePauseMs: Int = initial.messagePauseMs
    var openingPauseMs: Int = initial.openingPauseMs
    var victoryPauseMs: Int = initial.victoryPauseMs
    var battleCryPauseMs: Int = initial.battleCryPauseMs

    fun build(): BattlePacingModel =
        BattlePacingModel(
            messagePauseMs = messagePauseMs,
            openingPauseMs = openingPauseMs,
            victoryPauseMs = victoryPauseMs,
            battleCryPauseMs = battleCryPauseMs,
        )
}

@RylNextDsl
class CharacterCreationPacingBuilder(
    initial: CharacterCreationPacingModel = CharacterCreationPacingModel(),
) {
    var classRejectionPauseMs: Int = initial.classRejectionPauseMs
    var saveConfirmationPauseMs: Int = initial.saveConfirmationPauseMs

    fun build(): CharacterCreationPacingModel =
        CharacterCreationPacingModel(
            classRejectionPauseMs = classRejectionPauseMs,
            saveConfirmationPauseMs = saveConfirmationPauseMs,
        )
}

@RylNextDsl
class ShopPacingBuilder(
    initial: ShopPacingModel = ShopPacingModel(),
) {
    var interludePagePauseMs: Int = initial.interludePagePauseMs

    fun build(): ShopPacingModel = ShopPacingModel(interludePagePauseMs = interludePagePauseMs)
}

@RylNextDsl
class SpeakPacingBuilder(
    initial: SpeakPacingModel = SpeakPacingModel(),
) {
    var promptReplyPauseMs: Int = initial.promptReplyPauseMs
    var responsePauseMs: Int = initial.responsePauseMs

    fun build(): SpeakPacingModel =
        SpeakPacingModel(
            promptReplyPauseMs = promptReplyPauseMs,
            responsePauseMs = responsePauseMs,
        )
}

@RylNextDsl
class WildernessPacingBuilder(
    initial: WildernessPacingModel = WildernessPacingModel(),
) {
    var campMessagePauseMs: Int = initial.campMessagePauseMs

    fun build(): WildernessPacingModel = WildernessPacingModel(campMessagePauseMs = campMessagePauseMs)
}

@RylNextDsl
class TitleMenuPromptsBuilder(
    initial: TitleMenuPromptsModel = TitleMenuPromptsModel(),
) {
    var header: TextBlock = initial.header
    var createNewParty: InputBindingModel = initial.createNewParty
    var continueSavedGame: InputBindingModel = initial.continueSavedGame
    var terminateParty: InputBindingModel = initial.terminateParty
    var scoreboard: InputBindingModel = initial.scoreboard
    var hallOfFame: InputBindingModel = initial.hallOfFame
    var documentation: InputBindingModel = initial.documentation
    var leave: InputBindingModel = initial.leave
    var continueUnavailable: TextBlock = initial.continueUnavailable
    var overwriteConfirm: TextBlock = initial.overwriteConfirm
    var terminateConfirm: TextBlock = initial.terminateConfirm
    var terminated: TextBlock = initial.terminated
    var terminateFailed: TextBlock = initial.terminateFailed
    var invalidInput: TextBlock = initial.invalidInput

    fun build(): TitleMenuPromptsModel =
        TitleMenuPromptsModel(
            header = header,
            createNewParty = createNewParty,
            continueSavedGame = continueSavedGame,
            terminateParty = terminateParty,
            scoreboard = scoreboard,
            hallOfFame = hallOfFame,
            documentation = documentation,
            leave = leave,
            continueUnavailable = continueUnavailable,
            overwriteConfirm = overwriteConfirm,
            terminateConfirm = terminateConfirm,
            terminated = terminated,
            terminateFailed = terminateFailed,
            invalidInput = invalidInput,
        )
}

@RylNextDsl
class PriestSpellPromptsBuilder(
    initial: PriestSpellPromptsModel = PriestSpellPromptsModel(),
) {
    var who: TextBlock = initial.who
    var none: TextBlock = initial.none
    var noneKey: Char = initial.noneKey
    var firstSpellKey: Char = initial.firstSpellKey
    var mageCombatOnly: TextBlock = initial.mageCombatOnly
    var wrongClass: TextBlock = initial.wrongClass
    var dead: TextBlock = initial.dead
    var invalidCaster: TextBlock = initial.invalidCaster
    var invalidSpell: TextBlock = initial.invalidSpell
    var noSpells: TextBlock = initial.noSpells

    fun build(): PriestSpellPromptsModel =
        PriestSpellPromptsModel(
            who = who,
            none = none,
            noneKey = noneKey,
            firstSpellKey = firstSpellKey,
            mageCombatOnly = mageCombatOnly,
            wrongClass = wrongClass,
            dead = dead,
            invalidCaster = invalidCaster,
            invalidSpell = invalidSpell,
            noSpells = noSpells,
        )
}

@RylNextDsl
class InventoryUsePromptsBuilder(
    initial: InventoryUsePromptsModel = InventoryUsePromptsModel(),
) {
    var usePrompt: TextBlock = initial.usePrompt
    var quaff: TextBlock = initial.quaff
    var noItems: TextBlock = initial.noItems
    var noItem: TextBlock = initial.noItem
    var invalidSlot: TextBlock = initial.invalidSlot
    var notUsable: TextBlock = initial.notUsable

    fun invalidSlot(body: StructuredTextBlockBuilder.() -> Unit) {
        invalidSlot = structuredText(body)
    }

    fun notUsable(body: StructuredTextBlockBuilder.() -> Unit) {
        notUsable = structuredText(body)
    }

    fun build(): InventoryUsePromptsModel =
        InventoryUsePromptsModel(
            usePrompt = usePrompt,
            quaff = quaff,
            noItems = noItems,
            noItem = noItem,
            invalidSlot = invalidSlot,
            notUsable = notUsable,
        )
}

@RylNextDsl
@Suppress("unused")
class EquipmentPromptsBuilder(
    initial: EquipmentPromptsModel = EquipmentPromptsModel(),
) {
    var inventory: TextBlock = initial.inventory
    var who: TextBlock = initial.who
    private var noAvailableText: TextBlock = initial.noAvailable
    private var equippedText: TextBlock = initial.equipped
    var readyWeapons: TextBlock = initial.readyWeapons
    var readyArmors: TextBlock = initial.readyArmors

    fun noAvailable(body: EquipmentKindPromptBuilder.() -> Unit) {
        noAvailableText = EquipmentKindPromptBuilder().apply(body).build()
    }

    fun equipped(body: SelectedItemPromptBuilder.() -> Unit) {
        equippedText = SelectedItemPromptBuilder().apply(body).build()
    }

    fun build(): EquipmentPromptsModel =
        EquipmentPromptsModel(
            inventory = inventory,
            who = who,
            noAvailable = noAvailableText,
            equipped = equippedText,
            readyWeapons = readyWeapons,
            readyArmors = readyArmors,
        )
}

@RylNextDsl
class SavePromptsBuilder(
    initial: SavePromptsModel = SavePromptsModel(),
) {
    var saving: TextBlock = initial.saving
    var done: TextBlock = initial.done
    var failed: TextBlock = initial.failed

    fun build(): SavePromptsModel =
        SavePromptsModel(
            saving = saving,
            done = done,
            failed = failed,
        )
}

@RylNextDsl
class CharacterCreationPromptsBuilder(
    initial: CharacterCreationPromptsModel = CharacterCreationPromptsModel(),
) {
    var header: TextBlock = initial.header
    var partyName: TextBlock = initial.partyName
    private var statsPageText: TextBlock = initial.statsPage
    var reroll: TextBlock = initial.reroll
    private var memberNameText: TextBlock = initial.memberName
    private var genderText: TextBlock = initial.gender
    var genderMaleLabel: String = initial.genderMaleLabel
    var genderFemaleLabel: String = initial.genderFemaleLabel
    private var raceText: TextBlock = initial.race
    var classMenu: TextBlock = initial.classMenu
    var saving: TextBlock = initial.saving
    var saveConfirmationSuffix: String = initial.saveConfirmationSuffix

    fun statsPage(body: CharacterCreationStatsPromptBuilder.() -> Unit) {
        statsPageText = CharacterCreationStatsPromptBuilder().apply(body).build()
    }

    fun memberName(body: CharacterCreationMemberNumberPromptBuilder.() -> Unit) {
        memberNameText = CharacterCreationMemberNumberPromptBuilder().apply(body).build()
    }

    fun gender(body: CharacterCreationMemberNamePromptBuilder.() -> Unit) {
        genderText = CharacterCreationMemberNamePromptBuilder().apply(body).build()
    }

    fun race(body: CharacterCreationMemberNamePromptBuilder.() -> Unit) {
        raceText = CharacterCreationMemberNamePromptBuilder().apply(body).build()
    }

    fun build(): CharacterCreationPromptsModel =
        CharacterCreationPromptsModel(
            header = header,
            partyName = partyName,
            statsPage = statsPageText,
            reroll = reroll,
            memberName = memberNameText,
            gender = genderText,
            genderMaleLabel = genderMaleLabel,
            genderFemaleLabel = genderFemaleLabel,
            race = raceText,
            classMenu = classMenu,
            saving = saving,
            saveConfirmationSuffix = saveConfirmationSuffix,
        )
}

@RylNextDsl
class TownPromptsBuilder(
    initial: TownPromptsModel = TownPromptsModel(),
) {
    var unavailableSuffix: String = initial.unavailableSuffix
    var closedSuffix: String = initial.closedSuffix
    private var menuTitleText: TextBlock = initial.menuTitle
    var entryWelcome: TextBlock = initial.entryWelcome
    var entryTax: TextBlock = initial.entryTax
    var entryTaxThanks: TextBlock = initial.entryTaxThanks

    fun menuTitle(body: StructuredTextBlockBuilder.() -> Unit) {
        menuTitleText = structuredText(body)
    }

    fun build(): TownPromptsModel =
        TownPromptsModel(
            unavailableSuffix = unavailableSuffix,
            closedSuffix = closedSuffix,
            menuTitle = menuTitleText,
            entryWelcome = entryWelcome,
            entryTax = entryTax,
            entryTaxThanks = entryTaxThanks,
        )
}

@RylNextDsl
class PartyPromptsBuilder(
    initial: PartyPromptsModel = PartyPromptsModel(),
) {
    var viewWho: TextBlock = initial.viewWho
    var journalHeader: TextBlock = initial.journalHeader
    var journalEmpty: TextBlock = initial.journalEmpty
    var inventoryHeader: TextBlock = initial.inventoryHeader
    var dropPrompt: TextBlock = initial.dropPrompt
    var dropEmptySlot: TextBlock = initial.dropEmptySlot
    var dropAborted: TextBlock = initial.dropAborted
    private var dropDroppedText: TextBlock = initial.dropDropped
    var dropCapacityReduced: TextBlock = initial.dropCapacityReduced
    var dropOverflowConfirm: TextBlock = initial.dropOverflowConfirm
    var dropOverflowPrompt: TextBlock = initial.dropOverflowPrompt

    fun dropped(body: SelectedItemPromptBuilder.() -> Unit) {
        dropDroppedText = SelectedItemPromptBuilder().apply(body).build()
    }

    fun build(): PartyPromptsModel =
        PartyPromptsModel(
            viewWho = viewWho,
            journalHeader = journalHeader,
            journalEmpty = journalEmpty,
            inventoryHeader = inventoryHeader,
            dropPrompt = dropPrompt,
            dropEmptySlot = dropEmptySlot,
            dropAborted = dropAborted,
            dropDropped = dropDroppedText,
            dropCapacityReduced = dropCapacityReduced,
            dropOverflowConfirm = dropOverflowConfirm,
            dropOverflowPrompt = dropOverflowPrompt,
        )
}

@RylNextDsl
@Suppress("MemberVisibilityCanBePrivate")
class WildernessPromptsBuilder(
    initial: WildernessPromptsModel = WildernessPromptsModel(),
) {
    var pass: TextBlock = initial.pass
    var noMoves: TextBlock = initial.noMoves
    var outOfMoves: TextBlock = initial.outOfMoves
    var notInWilderness: TextBlock = initial.notInWilderness
    var nothingToEnter: TextBlock = initial.nothingToEnter
    var impassable: TextBlock = initial.impassable
    var quitConfirm: TextBlock = initial.quitConfirm
    private var dungeonHandoffText: TextBlock = initial.dungeonHandoff

    fun dungeonHandoff(body: WildernessDungeonEntryPromptBuilder.() -> Unit) {
        dungeonHandoffText = WildernessDungeonEntryPromptBuilder().apply(body).build()
    }

    fun build(): WildernessPromptsModel =
        WildernessPromptsModel(
            pass = pass,
            noMoves = noMoves,
            outOfMoves = outOfMoves,
            notInWilderness = notInWilderness,
            nothingToEnter = nothingToEnter,
            impassable = impassable,
            quitConfirm = quitConfirm,
            dungeonHandoff = dungeonHandoffText,
        )
}

@RylNextDsl
class DungeonPromptsBuilder(
    initial: DungeonPromptsModel = DungeonPromptsModel(),
) {
    private var levelMenuText: TextBlock = initial.levelMenu
    private var enterLevelText: TextBlock = initial.enterLevel
    var atMaxDepth: TextBlock = initial.atMaxDepth
    var noTorches: TextBlock = initial.noTorches
    var leaveCavern: TextBlock = initial.leaveCavern

    fun levelMenu(body: DungeonLevelPromptBuilder.() -> Unit) {
        levelMenuText = DungeonLevelPromptBuilder().apply(body).build()
    }

    fun enterLevel(body: DungeonLevelPromptBuilder.() -> Unit) {
        enterLevelText = DungeonLevelPromptBuilder().apply(body).build()
    }

    fun build(): DungeonPromptsModel =
        DungeonPromptsModel(
            levelMenu = levelMenuText,
            enterLevel = enterLevelText,
            atMaxDepth = atMaxDepth,
            noTorches = noTorches,
            leaveCavern = leaveCavern,
        )
}

@RylNextDsl
class WildernessDungeonEntryPromptBuilder : TextLineSink {
    private val text = StructuredTextBlockBuilder()

    val dungeonName: TextPart = TextPart.WildernessDungeonName
    val levels: TextPart = TextPart.WildernessDungeonLevels

    override fun addTextLine(line: TextLine) {
        text.addTextLine(line)
    }

    fun build(): TextBlock = text.build()
}

@RylNextDsl
class DungeonLevelPromptBuilder : TextLineSink {
    private val text = StructuredTextBlockBuilder()

    val level: TextPart = TextPart.DungeonDepth

    override fun addTextLine(line: TextLine) {
        text.addTextLine(line)
    }

    fun build(): TextBlock = text.build()
}

@RylNextDsl
class SelectedItemPromptBuilder : TextLineSink {
    private val text = StructuredTextBlockBuilder()

    val itemName: TextPart = TextPart.SelectedItemName

    override fun addTextLine(line: TextLine) {
        text.addTextLine(line)
    }

    fun build(): TextBlock = text.build()
}

@RylNextDsl
@Suppress("unused", "MemberVisibilityCanBePrivate")
class EquipmentKindPromptBuilder : TextLineSink {
    private val text = StructuredTextBlockBuilder()

    val equipmentKind: TextPart = TextPart.EquipmentKindName

    override fun addTextLine(line: TextLine) {
        text.addTextLine(line)
    }

    fun build(): TextBlock = text.build()
}

@RylNextDsl
class CharacterCreationStatsPromptBuilder : TextLineSink {
    private val text = StructuredTextBlockBuilder()

    val memberNumber: TextPart = TextPart.CreationMemberNumber
    val weaponSkill: TextPart = TextPart.CreationWeaponSkill
    val health: TextPart = TextPart.CreationHealth
    val wisdom: TextPart = TextPart.CreationWisdom
    val charisma: TextPart = TextPart.CreationCharisma

    override fun addTextLine(line: TextLine) {
        text.addTextLine(line)
    }

    fun build(): TextBlock = text.build()
}

@RylNextDsl
class CharacterCreationMemberNumberPromptBuilder : TextLineSink {
    private val text = StructuredTextBlockBuilder()

    val memberNumber: TextPart = TextPart.CreationMemberNumber

    override fun addTextLine(line: TextLine) {
        text.addTextLine(line)
    }

    fun build(): TextBlock = text.build()
}

@RylNextDsl
class CharacterCreationMemberNamePromptBuilder : TextLineSink {
    private val text = StructuredTextBlockBuilder()

    val memberName: TextPart = TextPart.CreationMemberName

    override fun addTextLine(line: TextLine) {
        text.addTextLine(line)
    }

    fun build(): TextBlock = text.build()
}

@RylNextDsl
class WildernessSystemBuilder {
    private var keysModel = WildernessKeysBuilder().build()
    private var eventsModel: WildernessEventModel? = null
    private var terrainTransitionModel = TerrainTransitionBuilder().build()

    fun keys(body: WildernessKeysBuilder.() -> Unit) {
        keysModel = WildernessKeysBuilder(keysModel).apply(body).build()
    }

    fun events(body: WildernessEventBuilder.() -> Unit) {
        eventsModel = WildernessEventBuilder().apply(body).build()
    }

    fun terrainTransitions(body: TerrainTransitionBuilder.() -> Unit) {
        terrainTransitionModel = TerrainTransitionBuilder().apply(body).build()
    }

    fun build(): WildernessSystemModel =
        WildernessSystemModel(
            keys = keysModel,
            events = checkNotNull(eventsModel) { "wilderness requires events { ... }" },
            terrainTransitions = terrainTransitionModel,
        )
}

@RylNextDsl
class WildernessKeysBuilder(
    initial: WildernessKeysModel = WildernessKeysModel(),
) {
    private var moveNorthKey = initial.moveNorth
    private var moveSouthKey = initial.moveSouth
    private var moveWestKey = initial.moveWest
    private var moveEastKey = initial.moveEast
    var pass: InputBindingModel = initial.pass
    var save: InputBindingModel = initial.save
    var readyWeapon: InputBindingModel = initial.readyWeapon
    var readyArmor: InputBindingModel = initial.readyArmor
    var redraw: InputBindingModel = initial.redraw
    var journal: InputBindingModel = initial.journal
    var help: InputBindingModel = initial.help
    var hunt: InputBindingModel = initial.hunt
    var viewParty: InputBindingModel = initial.viewParty
    var removeArmor: InputBindingModel = initial.removeArmor
    var removeWeapon: InputBindingModel = initial.removeWeapon
    var enter: InputBindingModel = initial.enter
    var drop: InputBindingModel = initial.drop
    var camp: InputBindingModel = initial.camp
    var castSpell: InputBindingModel = initial.castSpell
    var quaff: InputBindingModel = initial.quaff
    var quit: InputBindingModel = initial.quit
    private var debugForceCave: InputBindingModel = initial.debugForceCave
    private var debugForceEncounter: InputBindingModel = initial.debugForceEncounter
    private var debugShowMap: InputBindingModel = initial.debugShowMap
    private var caveEnterKey = initial.caveEnter
    private var caveLeaveKey = initial.caveLeave
    private var outOfMovesSaveKey = initial.outOfMovesSave
    private var outOfMovesQuitKey = initial.outOfMovesQuit
    private var quitYesKey = initial.quitYes
    private var quitNoKey = initial.quitNo
    private var commandHintText = initial.commandHint
    private var helpPageText = initial.helpPages

    private fun movement(
        north: InputBindingModel = moveNorthKey,
        south: InputBindingModel = moveSouthKey,
        west: InputBindingModel = moveWestKey,
        east: InputBindingModel = moveEastKey,
    ) {
        moveNorthKey = north
        moveSouthKey = south
        moveWestKey = west
        moveEastKey = east
    }

    fun numberPadMovementWithArrows() {
        val defaultKeys = WildernessKeysModel()
        movement(
            north = defaultKeys.moveNorth,
            south = defaultKeys.moveSouth,
            west = defaultKeys.moveWest,
            east = defaultKeys.moveEast,
        )
    }

    fun debug(
        forceCave: InputBindingModel = debugForceCave,
        forceEncounter: InputBindingModel = debugForceEncounter,
        showMap: InputBindingModel = debugShowMap,
    ) {
        debugForceCave = forceCave
        debugForceEncounter = forceEncounter
        debugShowMap = showMap
    }

    fun cave(
        enter: InputBindingModel = caveEnterKey,
        leave: InputBindingModel = caveLeaveKey,
    ) {
        caveEnterKey = enter
        caveLeaveKey = leave
    }

    fun cave(
        enter: Char,
        leave: Char,
    ) {
        cave(inputKey(enter), inputKey(leave))
    }

    fun outOfMoves(
        save: InputBindingModel = outOfMovesSaveKey,
        quit: InputBindingModel = outOfMovesQuitKey,
    ) {
        outOfMovesSaveKey = save
        outOfMovesQuitKey = quit
    }

    fun outOfMoves(
        save: Char,
        quit: Char,
    ) {
        outOfMoves(inputKey(save), inputKey(quit))
    }

    fun quitConfirm(
        yes: InputBindingModel = quitYesKey,
        no: InputBindingModel = quitNoKey,
    ) {
        quitYesKey = yes
        quitNoKey = no
    }

    fun quitConfirm(
        yes: Char,
        no: Char,
    ) {
        quitConfirm(inputKey(yes), inputKey(no))
    }

    fun commandHint(vararg lines: String) {
        commandHintText = inlineText(*lines)
    }

    fun commandHint(body: StructuredTextBlockBuilder.() -> Unit) {
        commandHintText = structuredText(body)
    }

    fun helpPages(body: WildernessHelpPagesBuilder.() -> Unit) {
        helpPageText = WildernessHelpPagesBuilder().apply(body).build()
    }

    fun build(): WildernessKeysModel =
        WildernessKeysModel(
            moveNorth = moveNorthKey,
            moveSouth = moveSouthKey,
            moveWest = moveWestKey,
            moveEast = moveEastKey,
            pass = pass,
            save = save,
            readyWeapon = readyWeapon,
            readyArmor = readyArmor,
            redraw = redraw,
            journal = journal,
            help = help,
            hunt = hunt,
            viewParty = viewParty,
            removeArmor = removeArmor,
            removeWeapon = removeWeapon,
            enter = enter,
            drop = drop,
            camp = camp,
            castSpell = castSpell,
            quaff = quaff,
            quit = quit,
            debugForceCave = debugForceCave,
            debugForceEncounter = debugForceEncounter,
            debugShowMap = debugShowMap,
            caveEnter = caveEnterKey,
            caveLeave = caveLeaveKey,
            outOfMovesSave = outOfMovesSaveKey,
            outOfMovesQuit = outOfMovesQuitKey,
            quitYes = quitYesKey,
            quitNo = quitNoKey,
            commandHint = commandHintText,
            helpPages = helpPageText,
        )
}

@RylNextDsl
class WildernessHelpPagesBuilder {
    private val pages = mutableListOf<TextBlock>()

    fun page(vararg lines: String) {
        pages += inlineText(*lines)
    }

    fun page(body: StructuredTextBlockBuilder.() -> Unit) {
        pages += structuredText(body)
    }

    fun build(): List<TextBlock> = pages.toList()
}

@RylNextDsl
class DungeonSystemBuilder {
    private val events = mutableListOf<DungeonEventModel>()
    private val scenarios = mutableListOf<DungeonScenarioModel>()
    private var backdrop: ImageId? = null
    private var keys = DungeonKeysModel()

    fun backdrop(image: ImageId?) {
        backdrop = image
    }

    fun keys(body: DungeonKeysBuilder.() -> Unit) {
        keys = DungeonKeysBuilder(keys).apply(body).build()
    }

    fun event(
        id: DungeonEventId,
        body: DungeonEventBuilder.() -> Unit,
    ) {
        events += DungeonEventBuilder(id).apply(body).build()
    }

    fun scenario(
        dungeon: DungeonId,
        body: DungeonScenarioBuilder.() -> Unit,
    ) {
        scenarios += DungeonScenarioBuilder(dungeon).apply(body).build()
    }

    fun build(): DungeonSystemModel =
        DungeonSystemModel(
            events = events.toList(),
            scenarios = scenarios.toList(),
            backdrop = backdrop,
            keys = keys,
        )
}

@RylNextDsl
class DungeonKeysBuilder(
    initial: DungeonKeysModel = DungeonKeysModel(),
) {
    var descend: InputBindingModel = initial.descend
    var ascend: InputBindingModel = initial.ascend
    var help: InputBindingModel = initial.help
    var drop: InputBindingModel = initial.drop
    var removeArmor: InputBindingModel = initial.removeArmor
    var removeWeapon: InputBindingModel = initial.removeWeapon
    var readyWeapon: InputBindingModel = initial.readyWeapon
    var readyArmor: InputBindingModel = initial.readyArmor
    var quaff: InputBindingModel = initial.quaff
    var castSpell: InputBindingModel = initial.castSpell
    var viewParty: InputBindingModel = initial.viewParty
    var save: InputBindingModel = initial.save
    var commandLabels: DungeonCommandLabelsModel = initial.commandLabels
    private var commandHintText = initial.commandHint
    private var helpPageText = initial.helpPages

    fun commandHint(vararg lines: String) {
        commandHintText = inlineText(*lines)
    }

    fun commandHint(body: StructuredTextBlockBuilder.() -> Unit) {
        commandHintText = structuredText(body)
    }

    fun helpPages(body: WildernessHelpPagesBuilder.() -> Unit) {
        helpPageText = WildernessHelpPagesBuilder().apply(body).build()
    }

    fun build(): DungeonKeysModel =
        DungeonKeysModel(
            descend = descend,
            ascend = ascend,
            help = help,
            drop = drop,
            removeArmor = removeArmor,
            removeWeapon = removeWeapon,
            readyWeapon = readyWeapon,
            readyArmor = readyArmor,
            quaff = quaff,
            castSpell = castSpell,
            viewParty = viewParty,
            save = save,
            commandHint = commandHintText,
            helpPages = helpPageText,
            commandLabels = commandLabels,
        )
}

@RylNextDsl
class DungeonEventBuilder(
    private val id: DungeonEventId,
) {
    private var disposition = DungeonEventDisposition.ClearAfterRun
    private var randomEncounterTier: EncounterTier? = null
    private var clearWhenFlag: FlagState? = null
    private val ops = mutableListOf<Op>()

    fun recurring() {
        disposition = DungeonEventDisposition.Recurring
        clearWhenFlag = null
    }

    fun recurringUntil(flag: FlagState) {
        disposition = DungeonEventDisposition.Recurring
        clearWhenFlag = flag
    }

    fun randomEncounter(tier: EncounterTier) {
        randomEncounterTier = tier
    }

    fun script(body: ScriptScope.() -> Unit) {
        ops += recordScript(body)
    }

    fun build(): DungeonEventModel =
        DungeonEventModel(
            id = id,
            disposition = disposition,
            ops = ops.toList(),
            randomEncounterTier = randomEncounterTier,
            clearWhenFlag = clearWhenFlag,
        )
}

@RylNextDsl
class DungeonScenarioBuilder(
    private val dungeon: DungeonId,
) {
    private var name: String? = null
    private var levelRange: IntRange? = null
    private val fixedLevelEvents = linkedMapOf<Int, DungeonEventId>()
    private val randomEvents = mutableListOf<DungeonRandomEventModel>()

    fun name(value: String) {
        name = value
    }

    fun levels(count: Int) {
        levelRange = count..count
    }

    fun level(
        number: Int,
        event: DungeonEventId,
    ) {
        fixedLevelEvents[number] = event
    }

    fun randomEvents(body: DungeonRandomEventPoolBuilder.() -> Unit) {
        randomEvents += DungeonRandomEventPoolBuilder().apply(body).build()
    }

    fun randomEvents(vararg events: Pair<DungeonEventId, Int>) {
        randomEvents {
            events.forEach { (eventId, weight) ->
                event(eventId, weight)
            }
        }
    }

    fun build(): DungeonScenarioModel =
        DungeonScenarioModel(
            dungeon = dungeon,
            name = name,
            levelRange = levelRange,
            fixedLevelEvents = fixedLevelEvents.toMap(),
            randomEvents = randomEvents.toList(),
        )
}

@RylNextDsl
class DungeonRandomEventPoolBuilder {
    private val events = mutableListOf<DungeonRandomEventModel>()

    fun event(
        event: DungeonEventId,
        weight: Int = 1,
    ) {
        events += DungeonRandomEventModel(event, weight)
    }

    fun build(): List<DungeonRandomEventModel> = events.toList()
}

@RylNextDsl
class WildernessEventBuilder {
    var caveChancePercent: Int = 3
    var quietChancePercent: Int = 91
    var encounterSplitPercent: Int = 50
    var easyEncounterTier: EncounterTier = DslDefaultEncounterTiers.weak
    var hardEncounterTier: EncounterTier = DslDefaultEncounterTiers.wild
    var caveDungeon: DungeonId = DslDefaultDungeons.randomCave
    var caveDepthRange: IntRange = 3..16
    var caveEntryCost: Int = 10
    var caveImage: ImageId? = null
    private var caveFoundPromptText: TextBlock? = null
    private var caveNoMovesText: TextBlock? = null

    fun caveFoundPrompt(vararg lines: String) {
        caveFoundPromptText = inlineText(*lines)
    }

    fun caveFoundPrompt(body: StructuredTextBlockBuilder.() -> Unit) {
        caveFoundPromptText = StructuredTextBlockBuilder().apply(body).build()
    }

    fun caveNoMoves(vararg lines: String) {
        caveNoMovesText = inlineText(*lines)
    }

    fun caveNoMoves(body: StructuredTextBlockBuilder.() -> Unit) {
        caveNoMovesText = StructuredTextBlockBuilder().apply(body).build()
    }

    fun build(): WildernessEventModel =
        WildernessEventModel(
            caveChancePercent = caveChancePercent,
            quietChancePercent = quietChancePercent,
            encounterSplitPercent = encounterSplitPercent,
            easyEncounterTier = easyEncounterTier,
            hardEncounterTier = hardEncounterTier,
            caveDungeon = caveDungeon,
            caveDepthRange = caveDepthRange,
            caveEntryCost = caveEntryCost,
            caveImage = caveImage,
            caveFoundPrompt = requireNotNull(caveFoundPromptText) { "wilderness events require caveFoundPrompt text" },
            caveNoMoves = requireNotNull(caveNoMovesText) { "wilderness events require caveNoMoves text" },
        )
}

@RylNextDsl
class TerrainTransitionBuilder {
    var chancePercent: Int = 9
    private val entries = mutableListOf<TerrainTransitionTextModel>()

    fun terrain(
        id: TerrainId,
        vararg lines: String,
    ) {
        entries += TerrainTransitionTextModel(id, inlineText(*lines))
    }

    fun terrain(
        id: TerrainId,
        body: StructuredTextBlockBuilder.() -> Unit,
    ) {
        entries += TerrainTransitionTextModel(id, StructuredTextBlockBuilder().apply(body).build())
    }

    fun build(): TerrainTransitionModel = TerrainTransitionModel(chancePercent = chancePercent, entries = entries.toList())
}

@RylNextDsl
class DialogueCatalogBuilder {
    private val dialogue = mutableListOf<DialogueModel>()

    fun npc(
        id: NpcId,
        body: NpcDialogueBuilder.() -> Unit,
    ) {
        dialogue += NpcDialogueBuilder(id).apply(body).build()
    }

    fun build(): List<DialogueModel> = dialogue.toList()
}

@RylNextDsl
class NpcDialogueBuilder(
    private val id: NpcId,
) {
    private val branches = mutableListOf<DialogueBranchModel>()

    fun whenState(
        state: FlagState,
        body: ScriptScope.() -> Unit,
    ) {
        branches += DialogueBranchModel(state, recordScript(body))
    }

    fun default(body: ScriptScope.() -> Unit) {
        branches += DialogueBranchModel(null, recordScript(body))
    }

    fun build(): DialogueModel = DialogueModel(id, branches.toList())
}

@RylNextDsl
class TownCatalogBuilder {
    private val towns = mutableListOf<TownModel>()

    fun town(
        slug: String,
        body: TownBuilder.() -> Unit,
    ) {
        towns += TownBuilder(town(slug)).apply(body).build()
    }

    fun town(
        ref: TownHandle,
        body: TownBuilder.() -> Unit,
    ) {
        towns += TownBuilder(ref.id).apply(body).build()
    }

    fun build(): List<TownModel> = towns.toList()
}

@RylNextDsl
class TownBuilder(
    private val id: TownId,
) {
    var name: String = ""
    var position: GridPoint = xy(0, 0)
    var taxPercent: Int = 0
    private var kindModel: TownKind = TownKind.Village
    private val serviceModels = mutableListOf<TownServiceModel>()

    fun village() {
        kindModel = TownKind.Village
    }

    fun city() {
        kindModel = TownKind.City
    }

    fun services(body: TownServicesBuilder.() -> Unit) {
        serviceModels += TownServicesBuilder().apply(body).build()
    }

    fun build(): TownModel = TownModel(id, name, position, taxPercent, kindModel, serviceModels.toList())
}

@RylNextDsl
class TownServicesBuilder {
    private val services = mutableListOf<TownServiceModel>()

    fun merchant() {
        service(DslDefaultBuildings.merchant)
    }

    fun inn() {
        service(DslDefaultBuildings.inn)
    }

    fun tavern() {
        service(DslDefaultBuildings.tavern)
    }

    fun smith() {
        service(DslDefaultBuildings.smith)
    }

    fun church() {
        service(DslDefaultBuildings.church)
    }

    fun guild() {
        service(DslDefaultBuildings.guild)
    }

    fun service(building: BuildingId) {
        services += TownServiceModel(building)
    }

    fun build(): List<TownServiceModel> = services.toList()
}

@RylNextDsl
class TownSystemBuilder {
    private var menuModel = TownMenuBuilder().build()
    private var buildingServiceModels = BuildingServiceCatalogBuilder().build()
    private var leavePromptCommands: List<EquipmentCommand> = emptyList()
    private var entryTaxModel: TownEntryTaxModel = TownEntryTaxModel()

    fun menu(body: TownMenuBuilder.() -> Unit) {
        menuModel = TownMenuBuilder().apply(body).build()
    }

    fun leavePrompts(vararg commands: EquipmentCommand) {
        leavePrompts(commands.toList())
    }

    fun leavePrompts(commands: List<EquipmentCommand>) {
        leavePromptCommands = commands.toList()
    }

    fun entryTax(body: TownEntryTaxBuilder.() -> Unit) {
        entryTaxModel = TownEntryTaxBuilder(entryTaxModel).apply(body).build()
    }

    fun buildingServices(body: BuildingServiceCatalogBuilder.() -> Unit) {
        buildingServiceModels = BuildingServiceCatalogBuilder().apply(body).build()
    }

    fun build(): TownSystemModel =
        TownSystemModel(
            menu = menuModel,
            buildingServices = buildingServiceModels,
            leavePrompts = leavePromptCommands,
            entryTax = entryTaxModel,
            nativeServices =
                listOf(
                    EngineServiceUse(EngineService.KeyboardInput, "town menu command selection"),
                    EngineServiceUse(EngineService.Rendering, "town entry art, text panes, and status refresh"),
                    EngineServiceUse(EngineService.TownStateStorage, "current town lookup and available-service checks"),
                    EngineServiceUse(EngineService.TimeState, "service opening-hour checks"),
                    EngineServiceUse(EngineService.PartyStateMutation, "tax, service costs, lodging, healing, and time passage"),
                    EngineServiceUse(EngineService.InventoryStorage, "merchant, smith, and service inventory effects"),
                    EngineServiceUse(EngineService.NpcStateStorage, "building NPC lookup and town conversations"),
                    EngineServiceUse(EngineService.TextFormatting, "town, building, NPC, item, price, and tax bindings"),
                ),
        )
}

@RylNextDsl
class TownEntryTaxBuilder(
    initial: TownEntryTaxModel = TownEntryTaxModel(),
) {
    var minimumLeaderLevel: Int = initial.minimumLeaderLevel
    var pay: InputBindingModel = initial.pay
    var leave: InputBindingModel = initial.leave

    fun build(): TownEntryTaxModel =
        TownEntryTaxModel(
            minimumLeaderLevel = minimumLeaderLevel,
            pay = pay,
            leave = leave,
        )
}

@RylNextDsl
class TownMenuBuilder {
    private val actions = mutableListOf<TownMenuActionModel>()

    fun church(key: Char = 'C') {
        service(inputKey(key, label = "Church"), DslDefaultBuildings.church, DslDefaultKeyActions.enterChurch)
    }

    fun church(binding: InputBindingModel) {
        service(binding, DslDefaultBuildings.church, DslDefaultKeyActions.enterChurch)
    }

    fun smith(key: Char = 'S') {
        service(inputKey(key, label = "Smith"), DslDefaultBuildings.smith, DslDefaultKeyActions.enterSmith)
    }

    fun smith(binding: InputBindingModel) {
        service(binding, DslDefaultBuildings.smith, DslDefaultKeyActions.enterSmith)
    }

    fun inn(key: Char = 'I') {
        service(inputKey(key, label = "Inn"), DslDefaultBuildings.inn, DslDefaultKeyActions.enterInn)
    }

    fun inn(binding: InputBindingModel) {
        service(binding, DslDefaultBuildings.inn, DslDefaultKeyActions.enterInn)
    }

    fun merchant(key: Char = 'M') {
        service(inputKey(key, label = "Merchant"), DslDefaultBuildings.merchant, DslDefaultKeyActions.enterMerchant)
    }

    fun merchant(binding: InputBindingModel) {
        service(binding, DslDefaultBuildings.merchant, DslDefaultKeyActions.enterMerchant)
    }

    fun tavern(key: Char = 'T') {
        service(inputKey(key, label = "Tavern"), DslDefaultBuildings.tavern, DslDefaultKeyActions.enterTavern)
    }

    fun tavern(binding: InputBindingModel) {
        service(binding, DslDefaultBuildings.tavern, DslDefaultKeyActions.enterTavern)
    }

    fun guild(key: Char = 'G') {
        service(inputKey(key, label = "Guild"), DslDefaultBuildings.guild, DslDefaultKeyActions.enterGuild)
    }

    fun guild(binding: InputBindingModel) {
        service(binding, DslDefaultBuildings.guild, DslDefaultKeyActions.enterGuild)
    }

    fun leave(
        key: Char = 'L',
        action: KeyActionId = DslDefaultKeyActions.leaveTown,
    ) {
        leave(inputKey(key, label = "Leave"), action)
    }

    fun leave(
        binding: InputBindingModel,
        action: KeyActionId = DslDefaultKeyActions.leaveTown,
    ) {
        actions += TownMenuActionModel(binding, action, building = null, endsMenu = true)
    }

    fun service(
        key: Char,
        building: BuildingId,
        action: KeyActionId,
    ) {
        service(inputKey(key), building, action)
    }

    fun service(
        binding: InputBindingModel,
        building: BuildingId,
        action: KeyActionId,
    ) {
        actions += TownMenuActionModel(binding, action, building, endsMenu = false)
    }

    fun build(): List<TownMenuActionModel> = actions.toList()
}

@RylNextDsl
class BuildingServiceCatalogBuilder {
    private val services = mutableListOf<BuildingServiceModel>()

    fun church(body: BuildingServiceBuilder.() -> Unit = {}) {
        building(DslDefaultBuildings.church, DslDefaultKeyActions.enterChurch, DslDefaultScenes.Towne.church, body)
    }

    fun smith(body: BuildingServiceBuilder.() -> Unit = {}) {
        building(DslDefaultBuildings.smith, DslDefaultKeyActions.enterSmith, DslDefaultScenes.Towne.smith, body)
    }

    fun inn(body: BuildingServiceBuilder.() -> Unit = {}) {
        building(DslDefaultBuildings.inn, DslDefaultKeyActions.enterInn, DslDefaultScenes.Towne.inn, body)
    }

    fun merchant(body: BuildingServiceBuilder.() -> Unit = {}) {
        building(DslDefaultBuildings.merchant, DslDefaultKeyActions.enterMerchant, DslDefaultScenes.Towne.merchant, body)
    }

    fun tavern(body: BuildingServiceBuilder.() -> Unit = {}) {
        building(DslDefaultBuildings.tavern, DslDefaultKeyActions.enterTavern, DslDefaultScenes.Towne.tavern, body)
    }

    fun guild(body: BuildingServiceBuilder.() -> Unit = {}) {
        building(DslDefaultBuildings.guild, DslDefaultKeyActions.enterGuild, DslDefaultScenes.Towne.guild, body)
    }

    fun building(
        building: BuildingId,
        action: KeyActionId,
        scene: SceneId,
        body: BuildingServiceBuilder.() -> Unit = {},
    ) {
        services += BuildingServiceBuilder(building, action, scene).apply(body).build()
    }

    fun build(): List<BuildingServiceModel> = services.toList()
}

@RylNextDsl
class BuildingServiceBuilder(
    private val building: BuildingId,
    defaultAction: KeyActionId,
    defaultScene: SceneId,
) {
    var action: KeyActionId = defaultAction
    var scene: SceneId = defaultScene
    private var opensAt: Int? = null
    private var closesAt: Int? = null

    var hours: IntRange?
        get() {
            val open = opensAt
            val close = closesAt
            if (open == null || close == null) return null
            return open..close
        }
        set(value) {
            opensAt = value?.first
            closesAt = value?.last
        }

    /** Declares service opening and closing hours, including overnight spans such as 6 to 1. */
    fun openHours(
        opensAt: Int,
        closesAt: Int,
    ) {
        this.opensAt = opensAt
        this.closesAt = closesAt
    }

    fun alwaysOpen() {
        opensAt = null
        closesAt = null
    }

    fun build(): BuildingServiceModel =
        BuildingServiceModel(
            building = building,
            action = action,
            scene = scene,
            opensAt = opensAt,
            closesAt = closesAt,
            nativeServices =
                listOf(
                    EngineServiceUse(EngineService.Rendering, "${building.slug} service screens and map-window art"),
                    EngineServiceUse(EngineService.KeyboardInput, "${building.slug} service menu input"),
                    EngineServiceUse(EngineService.TimeState, "${building.slug} opening-hour checks"),
                    EngineServiceUse(EngineService.PartyStateMutation, "${building.slug} cost, time, and resource effects"),
                ),
        )
}

@RylNextDsl
class CharacterCatalogBuilder {
    private val characters = mutableListOf<CharacterModel>()
    private var randomPlacementPolicy = RandomNpcPlacementPolicyModel()

    /**
     * Defines the default generated placement policy for later `randomPlacement()` NPCs.
     *
     * @param body placement choices shared by NPCs that do not override their
     * own random placement policy.
     */
    fun randomPlacementPolicy(body: RandomNpcPlacementPolicyBuilder.() -> Unit) {
        randomPlacementPolicy = RandomNpcPlacementPolicyBuilder().apply(body).build()
    }

    /**
     * Adds an NPC using explicit assignments inside [body].
     *
     * @param id stable authored NPC id.
     * @param body builder body that supplies name, job, placement, roles, and
     * optional gender.
     */
    fun character(
        id: NpcId,
        body: CharacterBuilder.() -> Unit,
    ) {
        characters += CharacterBuilder(id, randomPlacementPolicy).apply(body).build()
    }

    /**
     * Adds an NPC with the common name/job fields supplied as parameters.
     *
     * @param id stable authored NPC id.
     * @param name display name used in text and logs.
     * @param job authored NPC job, used by conversation and town service text.
     * @param body optional builder body for placement, roles, and gender.
     */
    fun character(
        id: NpcId,
        name: String,
        job: NpcJobId,
        body: CharacterBuilder.() -> Unit = {},
    ) {
        characters +=
            CharacterBuilder(id, randomPlacementPolicy)
                .apply {
                    this.name = name
                    this.job = job
                    body()
                }.build()
    }

    fun build(): List<CharacterModel> = characters.toList()
}

/** Collects the chance-share choices used by `randomPlacement`. */
@RylNextDsl
class RandomNpcPlacementPolicyBuilder {
    private val options = mutableListOf<RandomNpcPlacementOptionModel>()

    /**
     * Adds one town bucket whose result is one of the named services offered by [town].
     *
     * @param town generated town handle, such as `Towns.thord`.
     * @param services named services that exist in that town, such as `Buildings.inn`.
     * @param chanceShares relative share for choosing this town bucket. It is not a
     * percentage; `1` means one share among all options in the same policy.
     */
    fun townServices(
        town: TownHandle,
        vararg services: BuildingId,
        chanceShares: Int = 1,
    ) {
        townServices(town.id, *services, chanceShares = chanceShares)
    }

    /**
     * Adds one town bucket whose result is one of the named services offered by [town].
     *
     * @param town stable town id for DSL code that is not using generated handles.
     * @param services named services that exist in that town, such as `Buildings.inn`.
     * @param chanceShares relative share for choosing this town bucket. It is not a
     * percentage; `1` means one share among all options in the same policy.
     */
    fun townServices(
        town: TownId,
        vararg services: BuildingId,
        chanceShares: Int = 1,
    ) {
        options += RandomNpcPlacementOptionModel.TownServices(town, services.toList(), chanceShares)
    }

    /**
     * Adds one wilderness bucket over [x] and [y], restricted at runtime to terrain that allows NPCs.
     *
     * @param x inclusive x-coordinate range authored in map coordinates.
     * @param y inclusive y-coordinate range authored in map coordinates.
     * @param chanceShares relative share for choosing this wilderness bucket. It is
     * not a percentage; `1` means one share among all options in the same policy.
     * @param body optional coordinate rewrite rules for legacy placement quirks.
     */
    fun wilderness(
        x: IntRange,
        y: IntRange,
        chanceShares: Int = 1,
        body: RandomWildernessPlacementBuilder.() -> Unit = {},
    ) {
        val replacements = RandomWildernessPlacementBuilder().apply(body).build()
        options += RandomNpcPlacementOptionModel.WildernessRect(x, y, replacements, chanceShares)
    }

    fun build(): RandomNpcPlacementPolicyModel = RandomNpcPlacementPolicyModel(options.toList())
}

/** Optional coordinate adjustments for a wilderness placement bucket. */
@RylNextDsl
class RandomWildernessPlacementBuilder {
    private val xReplacements = linkedMapOf<Int, Int>()

    /**
     * Rewrites a drawn x coordinate before the runtime checks terrain and occupancy.
     *
     * @param from authored x coordinate selected from the wilderness range.
     * @param to x coordinate used instead when building the placement candidate.
     */
    fun replaceX(
        from: Int,
        to: Int,
    ) {
        xReplacements[from] = to
    }

    fun build(): Map<Int, Int> = xReplacements.toMap()
}

/**
 * Builder for one authored NPC.
 *
 * @param id stable authored NPC id.
 * @param randomPlacementPolicy current catalog-level default for `randomPlacement()`.
 */
@RylNextDsl
@Suppress("unused", "MemberVisibilityCanBePrivate")
class CharacterBuilder(
    private val id: NpcId,
    private val randomPlacementPolicy: RandomNpcPlacementPolicyModel,
) {
    /** Display name used in text, save/log output, and NPC menus. */
    var name: String = ""

    /** NPC job used by conversation text, generated job names, and authored role logic. */
    var job: NpcJobId = DslDefaultNpcJobs.merchant

    /** NPC gender used by authored text and character metadata. */
    var gender: Gender = Gender.Male

    private var placementModel: CharacterPlacementModel = CharacterPlacementModel.Randomized(randomPlacementPolicy)
    private val roleModels = mutableListOf<NpcRole>()

    /**
     * Places this NPC at a fixed wilderness coordinate or named town service.
     *
     * @param position authored location, usually `Wilderness.xy(x, y)` or a
     * generated town service handle such as `Towns.thord.inn`.
     */
    fun fixed(position: WorldPosition) {
        placementModel = CharacterPlacementModel.Fixed(position)
    }

    /**
     * Places this NPC at a fixed wilderness coordinate relative to a generated town handle.
     *
     * @param town base town whose map coordinate anchors the placement.
     * @param offset coordinate delta from the town position.
     * @param requiredTerrain terrain required at the resolved wilderness tile.
     */
    fun fixedNear(
        town: TownHandle,
        offset: GridOffset,
        requiredTerrain: TerrainId,
    ) {
        fixedNear(town.id, offset, requiredTerrain)
    }

    /**
     * Places this NPC at a fixed wilderness coordinate relative to a town id.
     *
     * @param town base town whose map coordinate anchors the placement.
     * @param offset coordinate delta from the town position.
     * @param requiredTerrain terrain required at the resolved wilderness tile.
     */
    fun fixedNear(
        town: TownId,
        offset: GridOffset,
        requiredTerrain: TerrainId,
    ) {
        placementModel = CharacterPlacementModel.FixedNearTown(town, offset, requiredTerrain)
    }

    /** Uses the catalog-level default generated placement policy for this NPC. */
    fun randomPlacement() {
        placementModel = CharacterPlacementModel.Randomized(randomPlacementPolicy)
    }

    /**
     * Uses an NPC-specific generated placement policy.
     *
     * @param body placement choices used only by this NPC.
     */
    fun randomPlacement(body: RandomNpcPlacementPolicyBuilder.() -> Unit) {
        placementModel = CharacterPlacementModel.Randomized(RandomNpcPlacementPolicyBuilder().apply(body).build())
    }

    /** Keeps this NPC record out of the playable world. */
    fun unplaced() {
        placementModel = CharacterPlacementModel.Unplaced
    }

    /**
     * Assigns story roles to this NPC.
     *
     * @param roles stable role handles consumed by authored scripts and dynamic text.
     */
    fun role(vararg roles: NpcRole) {
        roleModels += roles
    }

    fun build(): CharacterModel =
        CharacterModel(
            id = id,
            name = name,
            job = job,
            gender = gender,
            placement = placementModel,
            roles = roleModels.toList(),
        )
}

@RylNextDsl
class FlagContentBuilder {
    private val flags = mutableListOf<FlagContentModel>()

    fun story(
        flag: FlagRef,
        body: FlagContentEntryBuilder.() -> Unit = {},
    ) {
        require(flag.scope == FlagScope.Party) { "story flag ${flag.slug} must have party scope" }
        flags += FlagContentEntryBuilder(flag).apply(body).build()
    }

    fun encounter(
        flag: FlagRef,
        body: FlagContentEntryBuilder.() -> Unit = {},
    ) {
        require(flag.scope == FlagScope.Encounter) { "encounter flag ${flag.slug} must have encounter scope" }
        flags += FlagContentEntryBuilder(flag).apply(body).build()
    }

    fun build(): List<FlagContentModel> = flags.toList()
}

@RylNextDsl
class FlagContentEntryBuilder(
    private val flag: FlagRef,
) {
    private val explicitStates = mutableListOf<FlagState>()

    fun states(vararg states: FlagState) {
        explicitStates += states
    }

    fun build(): FlagContentModel = FlagContentModel(flag, explicitStates.ifEmpty { flag.states })
}

@RylNextDsl
class MonsterCatalogBuilder {
    private val monsters = mutableListOf<MonsterModel>()

    fun monster(
        id: MonsterId,
        body: MonsterBuilder.() -> Unit,
    ) {
        monsters += MonsterBuilder(id).apply(body).build()
    }

    fun build(): List<MonsterModel> = monsters.toList()
}

@RylNextDsl
class MonsterBuilder(
    private val id: MonsterId,
) {
    var name: String = ""
    var count: IntRange = 1..1
    var countMaxLevelDivisor: Int? = null
    var hitPoints: IntRange = 1..1
    var weaponSkill: Int = 0
    var spellPower: Int = 0
    var tier: EncounterTier? = null
    var immune: Boolean = false
    private var encounterImage: ImageId? = null

    fun encounterImage(image: ImageId?) {
        encounterImage = image
    }

    fun build(): MonsterModel =
        MonsterModel(id, name, count, countMaxLevelDivisor, hitPoints, weaponSkill, spellPower, tier, immune, encounterImage)
}

@RylNextDsl
class MissionCatalogBuilder {
    private val missions = mutableListOf<MissionModel>()

    fun mission(
        id: MissionId,
        body: MissionBuilder.() -> Unit,
    ) {
        missions += MissionBuilder(id).apply(body).build()
    }

    fun build(): List<MissionModel> = missions.toList()
}

@RylNextDsl
class MissionBuilder(
    private val id: MissionId,
) {
    private var trigger: Trigger? = null
    private var requires: BoolAst? = null
    private var action: SceneId? = null

    fun randomStep(percent: Int) {
        trigger = Trigger.RandomStep(percent)
    }

    fun at(point: GridPoint) {
        trigger = Trigger.AtPosition(point)
    }

    fun at(position: WorldPosition) {
        val point =
            (position as? WorldPosition.Wilderness)?.point
                ?: error("mission ${id.slug} position trigger must be a wilderness position")
        at(point)
    }

    fun at(location: LocationId) {
        trigger = Trigger.AtLocation(location)
    }

    fun onTerrain(terrain: TerrainId) {
        trigger = Trigger.OnTerrain(terrain)
    }

    fun requires(state: FlagState) {
        requires = state.ast
    }

    fun requires(cond: BoolExpr) {
        requires = cond.ast
    }

    fun runs(scene: SceneId) {
        action = scene
    }

    fun build(): MissionModel =
        MissionModel(
            id = id,
            trigger = checkNotNull(trigger) { "mission ${id.slug} requires a trigger" },
            requires = requires,
            action = checkNotNull(action) { "mission ${id.slug} requires an action scene" },
        )
}

@RylNextDsl
class GameProgressBuilder {
    private val milestones = mutableListOf<GameMilestoneModel>()
    private var winBuilder: WinConditionBuilder? = null

    fun milestone(
        id: GameMilestoneId,
        body: GameMilestoneBuilder.() -> Unit,
    ) {
        milestones += GameMilestoneBuilder(id).apply(body).build()
    }

    fun winningConfiguration(body: WinConditionBuilder.() -> Unit) {
        winBuilder = WinConditionBuilder().apply(body)
    }

    fun build(): GameProgressModel = GameProgressModel(milestones.toList(), winBuilder?.build(milestones))
}

@RylNextDsl
class GameMilestoneBuilder(
    private val id: GameMilestoneId,
) {
    private var specialInventoryItem: ItemId? = null
    private var requiredToWin: Boolean = false
    private var sourceSpecial: SourceSpecialMapping? = null

    fun showsAs(item: ItemId) {
        specialInventoryItem = item
    }

    fun requiredToWin() {
        requiredToWin = true
    }

    fun sourcePartyField(
        field: SourceSpecialField,
        achievedValue: Int = 1,
    ) {
        sourceSpecial = SourceSpecialMapping(field, achievedValue)
    }

    fun build(): GameMilestoneModel = GameMilestoneModel(id, specialInventoryItem, requiredToWin, sourceSpecial)
}

@RylNextDsl
class WinConditionBuilder {
    private var includeMarkedRequired = false
    private val explicitRequired = mutableListOf<GameMilestoneId>()
    private var completionMilestone: GameMilestoneId? = null
    private var hallOfHeroesEmptyText = "No party has completed the winning objective yet."
    private var hallOfHeroesEntryText = "{party} completed the winning objective."

    fun requiresAllMilestonesMarkedRequired() {
        includeMarkedRequired = true
    }

    fun requires(vararg milestones: GameMilestoneId) {
        explicitRequired += milestones
    }

    fun completedBy(milestone: GameMilestoneId) {
        completionMilestone = milestone
    }

    fun hallOfHeroes(
        empty: String,
        entry: String,
    ) {
        hallOfHeroesEmptyText = empty
        hallOfHeroesEntryText = entry
    }

    fun build(defined: List<GameMilestoneModel>): WinConditionModel {
        val markedRequired =
            if (includeMarkedRequired) {
                defined.filter { it.requiredToWin }.map { it.id }
            } else {
                emptyList()
            }

        return WinConditionModel(
            required = (explicitRequired + markedRequired).distinct(),
            completedBy = completionMilestone,
            hallOfHeroesEmptyText = hallOfHeroesEmptyText,
            hallOfHeroesEntryText = hallOfHeroesEntryText,
        )
    }
}

@RylNextDsl
class LevelingBuilder {
    var maxLevel: Int = 28
    var hitPointFloor: FormulaId = DslDefaultFormulas.levelUpHitPointFloor
    private var thresholdModel: List<Int> = emptyList()
    private val classGrowthModel = mutableListOf<ClassLevelUpModel>()

    fun xpThresholds(vararg thresholds: Int) {
        thresholdModel = thresholds.toList()
    }

    fun classLevelUp(
        klass: PcClass,
        body: ClassLevelUpBuilder.() -> Unit,
    ) {
        classGrowthModel += ClassLevelUpBuilder(klass).apply(body).build()
    }

    fun build(): LevelingModel =
        LevelingModel(
            maxLevel = maxLevel,
            xpThresholds = thresholdModel,
            hitPointFloor = hitPointFloor,
            classGrowth = classGrowthModel.toList(),
        )
}

@RylNextDsl
class ClassLevelUpBuilder(
    private val klass: PcClass,
) {
    var weaponSkill: Int = 0
    var health: Int = 0
    var wisdom: Int = 0
    var charisma: Int = 0

    fun build(): ClassLevelUpModel = ClassLevelUpModel(klass, weaponSkill, health, wisdom, charisma)
}

@RylNextDsl
class CharacterCreationBuilder {
    private var statRollModel: CharacterCreationStatRollModel? = null
    private var startModel: CharacterCreationStartModel? = null
    private val raceOptions = mutableListOf<CharacterRaceOptionModel>()
    private val classOptions = mutableListOf<CharacterClassOptionModel>()
    private val portraitOptions = mutableListOf<CharacterPortraitModel>()

    fun statRoll(
        min: Int,
        max: Int,
    ) {
        statRollModel = CharacterCreationStatRollModel(min, max)
    }

    fun start(body: CharacterCreationStartBuilder.() -> Unit) {
        startModel = CharacterCreationStartBuilder().apply(body).build()
    }

    fun race(
        race: Race,
        label: String,
    ) {
        raceOptions += CharacterRaceOptionModel(race, label)
    }

    fun playerClass(
        klass: PcClass,
        label: String,
        body: CharacterCreationClassBuilder.() -> Unit = {},
    ) {
        classOptions += CharacterCreationClassBuilder(klass, label).apply(body).build()
    }

    fun portrait(
        image: ImageId,
        gender: Gender? = null,
        race: Race? = null,
        klass: PcClass? = null,
    ) {
        portraitOptions += CharacterPortraitModel(gender, race, klass, image)
    }

    fun build(): CharacterCreationModel =
        CharacterCreationModel(
            statRoll = checkNotNull(statRollModel) { "characterCreation requires statRoll(min, max)" },
            start = checkNotNull(startModel) { "characterCreation requires start { ... }" },
            races = raceOptions.toList(),
            classes = classOptions.toList(),
            portraits = portraitOptions.toList(),
        )
}

@RylNextDsl
class CharacterCreationStartBuilder {
    var map: WorldMapId = DslDefaultMaps.wilderness
    var position: WorldPosition? = null
    var facing: Facing? = null
    var partyMemberMax: Int = 0
    var level: Int = 0
    var dailyTimeLeft: Int = 0
    private var resourcesState: PartyResources? = null

    fun resources(body: PartyResourcesBuilder.() -> Unit) {
        resourcesState = PartyResourcesBuilder().apply(body).build()
    }

    fun build(): CharacterCreationStartModel =
        CharacterCreationStartModel(
            map = map,
            position = checkNotNull(position) { "characterCreation start requires position" },
            facing = facing,
            resources = checkNotNull(resourcesState) { "characterCreation start requires resources { ... }" },
            partyMemberMax = partyMemberMax,
            level = level,
            dailyTimeLeft = dailyTimeLeft,
        )
}

@RylNextDsl
class CharacterCreationClassBuilder(
    private val klass: PcClass,
    private val label: String,
) {
    private var requiredStat: CharacterCreationStat? = null
    private var minimum: Int? = null
    private var failureText: TextBlock? = null

    fun requires(
        stat: CharacterCreationStat,
        minimum: Int,
        failureText: TextBlock,
    ) {
        requiredStat = stat
        this.minimum = minimum
        this.failureText = failureText
    }

    fun build(): CharacterClassOptionModel =
        CharacterClassOptionModel(
            klass = klass,
            label = label,
            requiredStat = requiredStat,
            minimum = minimum,
            failureText = failureText,
        )
}

@RylNextDsl
class FormulaCatalogBuilder {
    private val formulas = mutableListOf<FormulaModel>()

    fun formula(
        id: FormulaId,
        body: FormulaBuilder.() -> Unit,
    ) {
        formulas += FormulaBuilder(id).apply(body).build()
    }

    fun build(): List<FormulaModel> = formulas.toList()
}

@RylNextDsl
@Suppress("unused", "MemberVisibilityCanBePrivate")
class FormulaBuilder(
    private val id: FormulaId,
) {
    var value: IntExpr = lit(0)

    val encounterHitPointMinBase: IntExpr = v("encounter_hit_point_min_base")
    val encounterHitPointMaxBase: IntExpr = v("encounter_hit_point_max_base")
    val partyAverage: IntExpr = v("party_avg")
    val firstLevel: IntExpr = v("first_level")
    val encounterFoeCount: IntExpr = v("encounter_foe_count")
    val encounterHitPointMax: IntExpr = v("encounter_hit_point_max")
    val monsterWeaponSkill: IntExpr = v("monster_weapon_skill")
    val monsterSpellPower: IntExpr = v("monster_spell_power")
    val health: IntExpr = v("health")

    fun whenValue(
        condition: BoolExpr,
        thenValue: IntExpr,
        elseValue: IntExpr = lit(0),
    ): IntExpr = Expr(IntAst.If(condition.ast, thenValue.ast, elseValue.ast))

    fun whenValue(
        condition: BoolExpr,
        thenValue: Int,
        elseValue: Int = 0,
    ): IntExpr = whenValue(condition, lit(thenValue), lit(elseValue))

    fun build(): FormulaModel = FormulaModel(id, value.ast)
}

@RylNextDsl
class ItemUseBuilder(
    private val id: ItemId,
) {
    private val contexts = mutableSetOf(ItemUseContext.Combat)
    var consume: ItemConsumePolicy = ItemConsumePolicy.BeforeUse
    private var ops = emptyList<Op>()

    fun usableIn(vararg contexts: ItemUseContext) {
        this.contexts.clear()
        this.contexts += contexts
    }

    fun combat(): Unit = usableIn(ItemUseContext.Combat)

    fun inventory(): Unit = usableIn(ItemUseContext.Inventory)

    fun effect(body: ScriptScope.() -> Unit) {
        ops = recordScript(body)
    }

    fun build(): ItemUseModel = ItemUseModel(id, contexts.toSet(), consume, ops)
}

@RylNextDsl
class RewardCatalogBuilder {
    private val rewards = mutableListOf<RewardBundleModel>()

    fun reward(
        id: RewardBundleId,
        body: RewardBundleBuilder.() -> Unit,
    ) {
        rewards += RewardBundleBuilder(id).apply(body).build()
    }

    fun build(): List<RewardBundleModel> = rewards.toList()
}

@RylNextDsl
class RewardBundleBuilder(
    private val id: RewardBundleId,
) {
    var experience: FormulaId = DslDefaultFormulas.rewardXpPerSurvivor
    var gold: FormulaId = DslDefaultFormulas.rewardGoldParty
    private var applicationModel = RewardApplicationBuilder().build()
    private var lootModel = LootTableBuilder(DslDefaultLootTables.standardBattle).build()

    fun itemPlacement(body: RewardApplicationBuilder.() -> Unit) {
        applicationModel = RewardApplicationBuilder(applicationModel).apply(body).build()
    }

    fun lootTable(
        id: LootTableId,
        body: LootTableBuilder.() -> Unit,
    ) {
        lootModel = LootTableBuilder(id).apply(body).build()
    }

    fun build(): RewardBundleModel = RewardBundleModel(id, experience, gold, applicationModel, lootModel)
}

@RylNextDsl
class RewardApplicationBuilder(
    initial: RewardApplicationModel? = null,
) {
    var regularItems: RewardItemPlacement =
        initial?.regularItems ?: RewardItemPlacement.FirstFreeInventorySlot
    var overflow: RewardOverflowPolicy =
        initial?.overflow ?: RewardOverflowPolicy.SkipIfInventoryFull
    var specialItems: RewardSpecialItemPolicy =
        initial?.specialItems ?: RewardSpecialItemPolicy.ProjectMilestoneState

    fun build(): RewardApplicationModel = RewardApplicationModel(regularItems, overflow, specialItems)
}

@RylNextDsl
class LootTableBuilder(
    private val id: LootTableId,
) {
    var dropChancePercent: Int = 5
    var treasureChancePercent: Int = 59
    var treasure: ItemPoolId = DslDefaultItemPools.battleSpoils
    var magic: ItemPoolId = DslDefaultItemPools.magicItems

    fun build(): LootTableModel = LootTableModel(id, dropChancePercent, treasureChancePercent, treasure, magic)
}
