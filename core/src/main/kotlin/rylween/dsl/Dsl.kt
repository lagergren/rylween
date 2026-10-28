@file:Suppress("ktlint:standard:no-wildcard-imports")

/**
 * Kotlin implementation for the Rylween authoring DSL.
 *
 * This file is implementation/compiler scaffolding: typed handles, immutable
 * models, builders, and recorded source scripts. Current-game handles are
 * generated from `RylweenGame.kt` by the Gradle content-generation task.
 * Game-specific content belongs in `game/src/main/kotlin/rylween/game`,
 * validation helpers live in `DslValidation.kt`, and compiled J1 emission lives
 * in `compiled/CompiledBundle.kt`.
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

/**
 * Mutable recording context shared while one authored script block is captured.
 *
 * Designers normally never use this directly; builder receivers use it to
 * allocate stable temporary names and to route nested DSL bodies into the
 * correct source-statement block.
 */
class ScriptContext {
    private var nextVar = 0
    private val statementTargets = mutableListOf<ScriptBlockBuilder>()

    fun fresh(prefix: String): String = "${prefix}_${nextVar++}"

    fun record(
        fallback: ScriptBlockBuilder,
        statement: ScriptStatement,
    ) {
        (statementTargets.lastOrNull() ?: fallback).record(statement)
    }

    fun <T> recording(
        target: ScriptBlockBuilder,
        body: () -> T,
    ): T {
        statementTargets += target
        return try {
            body()
        } finally {
            statementTargets.removeAt(statementTargets.lastIndex)
        }
    }
}

@DslMarker
annotation class RylNextDsl

fun itemPools(vararg pools: ItemPoolId): List<ItemPoolId> = pools.toList()

fun acceptedText(vararg values: String): List<String> = values.toList()

/**
 * Receiver for authored script bodies such as scenes, dialogue branches,
 * dungeon events, item-use effects, rewards, and service hooks.
 *
 * Calls on this receiver record source script operations. They do not run
 * gameplay at authoring time; validation and the compiler later lower the
 * recorded operations into the compiled VM bundle consumed by runtimes.
 */
@RylNextDsl
@Suppress("unused", "MemberVisibilityCanBePrivate")
class ScriptScope(
    private val ctx: ScriptContext,
    private val block: ScriptBlockBuilder,
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

    fun record(statement: ScriptStatement) {
        ctx.record(block, statement)
    }

    fun fresh(prefix: String): String = ctx.fresh(prefix)

    /**
     * Writes text to the active text window, on a fresh page: whatever the
     * window showed before is gone.
     *
     * This is the preferred script display verb. It intentionally names the
     * runtime side effect, while [flowText] and [literalText] name reusable text
     * data. Text the player has not had time to read must be held first, by
     * [waitInput], a pause, or a prompt; use [appendText] to keep it on screen
     * and add more below it. A plain string is [flowText] prose.
     *
     * `{pause}` markers in the text, such as "nothing but... {pause 2s} dust...",
     * show the page up to each marker, pause, and then show it again with more of
     * the text, so a scene's beats need no separate [pause] calls.
     */
    fun writeText(text: TextBlock) {
        text.beats().forEach { beat ->
            record(ScriptStatementNode.Screen(beat.text))
            beat.pauseMillis?.let(::pause)
        }
    }

    fun writeText(text: String): Unit = writeText(flowText(text))

    fun writeText(body: StructuredTextBlockBuilder.() -> Unit): Unit = writeText(literalText(body))

    /**
     * Adds text below what the text window already shows, so a beat such as
     * "...you reveal nothing but..." can be followed, after a pause, by "dust..."
     * on the same page. Flow text is a new paragraph and gets a blank line
     * above it; literal lines continue directly below, exactly as written. A
     * `{pause}` marker at the end holds the page that long.
     */
    fun appendText(text: TextBlock) {
        val beat = text.beats().singleOrNull()
        requireNotNull(beat) { "appendText takes a {pause} marker only at the end of its text; put earlier beats in writeText" }
        record(ScriptStatementNode.Screen(beat.text, append = true))
        beat.pauseMillis?.let(::pause)
    }

    fun appendText(text: String): Unit = appendText(flowText(text))

    fun appendText(body: StructuredTextBlockBuilder.() -> Unit): Unit = appendText(literalText(body))

    /**
     * Writes text, waits for the player to press Return/Enter, and clears the
     * text window. Use this for read-and-dismiss pages rather than spelling out
     * the three operations at each script site.
     */
    fun showTextAndWait(text: String) {
        showTextAndWait(flowText(text))
    }

    fun showTextAndWait(text: TextBlock) {
        writeText(text)
        waitInput()
        clearText()
    }

    fun showTextAndWait(body: StructuredTextBlockBuilder.() -> Unit) {
        showTextAndWait(literalText(body))
    }

    fun journal(text: TextBlock): Unit = record(ScriptStatementNode.AppendJournal(text))

    fun journal(vararg lines: String): Unit = journal(literalText(*lines))

    fun journal(body: StructuredTextBlockBuilder.() -> Unit): Unit = journal(literalText(body))

    fun waitInput(reason: String? = null): Unit = record(ScriptStatementNode.WaitInput(reason))

    /**
     * Waits [ms] milliseconds. A pause that holds text belongs in the text as a
     * `{pause}` marker; this is for a beat between other things, such as a
     * sound and the line that follows it.
     */
    fun pause(ms: Int): Unit = record(ScriptStatementNode.Pause(ms))

    fun clearText(): Unit = view.clearText()

    fun clearMapWindow(): Unit = view.clearMapWindow()

    fun renderMap(): Unit = view.renderMap()

    fun showBackdrop(image: ImageId): Unit = view.showBackdrop(image)

    fun playSound(sound: SoundId): Unit = view.playSound(sound)

    fun playMusic(music: MusicId): Unit = view.playMusic(music)

    fun statusLine(): Unit = view.statusLine()

    fun let(
        name: String,
        value: IntExpr,
    ): IntExpr {
        record(ScriptStatementNode.LetInt(name, value.ast))
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
        record(ScriptStatementNode.LetInt(ref.name, value.ast))
        return ref
    }

    fun localInt(
        value: Int,
        hint: String = "local",
    ): LocalIntRef = localInt(lit(value), hint)

    /**
     * Records a script local and exposes it only inside [body].
     *
     * Prefer this block form in game-authored scripts when the value is only used
     * by the following scripted statements. It keeps temporary storage explicit in
     * the source IR without requiring authors to write Kotlin `val` declarations.
     */
    fun localInt(
        value: IntExpr,
        hint: String = "local",
        body: ScriptScope.(LocalIntRef) -> Unit,
    ) {
        body(localInt(value, hint))
    }

    fun localInt(
        value: Int,
        hint: String = "local",
        body: ScriptScope.(LocalIntRef) -> Unit,
    ) {
        body(localInt(value, hint))
    }

    /**
     * Exposes the active battle/item caster to [body] without a Kotlin `val`.
     *
     * The runtime still supplies the `caster_idx` binding; this helper only makes
     * the authoring surface read like a DSL block instead of imperative Kotlin.
     */
    fun withCurrentCaster(body: ScriptScope.(MemberRef) -> Unit) {
        body(currentCaster)
    }

    /**
     * Exposes the active caster plus what the spell costs them, as the game's
     * `realm { spellCost }` formula works it out. The runtime supplies both,
     * the way battle charges a mage spell.
     */
    fun withCurrentCasterCost(body: ScriptScope.(MemberRef, IntExpr) -> Unit) {
        withCurrentCaster { caster ->
            body(caster, v("spell_cost"))
        }
    }

    infix fun LocalIntRef.set(value: IntExpr) {
        record(ScriptStatementNode.LetInt(name, value.ast))
    }

    infix fun LocalIntRef.set(value: Int): Unit = set(lit(value))

    operator fun LocalIntRef.plusAssign(value: IntExpr) {
        record(ScriptStatementNode.LetInt(name, (this + value).ast))
    }

    operator fun LocalIntRef.plusAssign(value: Int): Unit = plusAssign(lit(value))

    operator fun LocalIntRef.minusAssign(value: IntExpr) {
        record(ScriptStatementNode.LetInt(name, (this - value).ast))
    }

    operator fun LocalIntRef.minusAssign(value: Int): Unit = minusAssign(lit(value))

    fun mark(state: FlagState) {
        record(ScriptStatementNode.SetFlag(state.flag.slug, state.native))
    }

    fun whenever(
        cond: BoolExpr,
        then: ScriptScope.() -> Unit,
    ): ElseBranch {
        val thenStatements = capture(then)
        val index = block.size
        block.record(ScriptStatementNode.If(cond.ast, thenStatements, emptyList()))
        return ElseBranch(ctx) { elseStatements -> block[index] = ScriptStatementNode.If(cond.ast, thenStatements, elseStatements) }
    }

    fun stop(reason: String? = null): Unit = record(ScriptStatementNode.Stop(reason))

    /**
     * Leaves the nearest authored script-control loop.
     *
     * This is source-script flow that lowers to the compiled VM. Runtime
     * mode/service loops such as wilderness, dungeon, castle, shop, or battle
     * loops stay native because they own live input, rendering, persistence, RNG,
     * and subsystem state.
     */
    fun breakLoop(reason: String? = null): Unit = record(ScriptStatementNode.Break(reason))

    /**
     * Repeats an authored script block until that block emits [breakLoop] or
     * [stop].
     *
     * Use this for local scripted menus or retry prompts. Do not use it to model
     * engine cadence loops; those belong in runtime hosts and should consume
     * authored text, formulas, choices, rewards, and policies from compiled
     * content/runtime-system rows.
     */
    fun loop(body: ScriptScope.() -> Unit) {
        record(ScriptStatementNode.Loop(capture(body)))
    }

    fun choose(
        expr: IntExpr,
        body: SwitchBuilder.() -> Unit,
    ) {
        val builder = SwitchBuilder(ctx)
        builder.body()
        record(ScriptStatementNode.Switch(expr.ast, builder.cases.toList()))
    }

    fun menu(
        vararg promptLines: String,
        body: MenuBuilder.() -> Unit,
    ) {
        val builder = MenuBuilder(ctx)
        builder.body()
        record(builder.build(literalText(*promptLines)))
    }

    fun menu(
        prompt: TextBlock,
        body: MenuBuilder.() -> Unit,
    ) {
        val builder = MenuBuilder(ctx)
        builder.body()
        record(builder.build(prompt))
    }

    /**
     * Iterates over monsters inside an authored combat script.
     *
     * This is compiled script-control flow for local scripted effects; it does
     * not own the standard battle round loop or target-selection UI.
     */
    fun forEachMonster(
        includeDead: Boolean = false,
        body: ScriptScope.(MonsterRef) -> Unit,
    ) {
        val name = ctx.fresh("monster")
        val childStatements =
            capture {
                body(MonsterRef(this, v(name)))
            }
        record(ScriptStatementNode.ForEachMonster(name, includeDead, childStatements))
    }

    /**
     * Iterates over party members inside an authored script.
     *
     * This is compiled script-control flow for local scripted effects; it is not
     * the owner of broad party, battle, or screen rendering loops.
     */
    fun forEachMember(
        includeDead: Boolean = false,
        body: ScriptScope.(MemberRef) -> Unit,
    ) {
        val name = ctx.fresh("member")
        val childStatements =
            capture {
                body(MemberRef(this, v(name)))
            }
        record(ScriptStatementNode.ForEachMember(name, includeDead, childStatements))
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
        record(ScriptStatementNode.RunScene(scene, bindings.associate { it.first to render(it.second) }))
    }

    fun speak(building: BuildingId) {
        record(ScriptStatementNode.Speak(building))
    }

    fun tellRumor(table: RumorTableId = DslDefaultRumorTables.standardTown) {
        record(ScriptStatementNode.TellRumor(table))
    }

    fun encounter(
        monster: MonsterId,
        mandatory: Boolean = false,
        tier: EncounterTier? = null,
    ) {
        record(ScriptStatementNode.Encounter(foe = monster, mandatory = mandatory, tier = tier))
    }

    fun enterDungeon(
        dungeon: DungeonId,
        levels: Int,
    ) {
        record(ScriptStatementNode.EnterDungeon(dungeon, levels))
    }

    fun dungeonEntrance(
        dungeon: DungeonId,
        levels: Int,
        leaveClearsText: Boolean = true,
        prompt: StructuredTextBlockBuilder.() -> Unit,
    ) {
        clearText()
        choice.enterOrLeave(
            literalText(prompt),
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
                line("You find a ", itemName, ".")
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

    fun teleportParty(
        directionKey: IntExpr,
        distance: IntExpr,
    ) {
        record(ScriptStatementNode.TeleportParty(directionKey.ast, distance.ast))
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
        record(ScriptStatementNode.DiscoverCave(dungeon, levels.ast, moveCost.ast))
    }

    fun discoverCave(
        levels: Int,
        dungeon: DungeonId = DslDefaultDungeons.randomCave,
        moveCost: Int = 10,
    ) {
        discoverCave(lit(levels), dungeon, lit(moveCost))
    }

    fun consumeActiveItem(): Unit = record(ScriptStatementNode.ConsumeActiveItem)

    /**
     * Grants player-facing gold or inventory rewards.
     *
     * Use this for mission payoffs, found treasure, and gifts where the player
     * is receiving something as content. Mechanical costs, damage, timers, and
     * temporary counters should keep using the direct state slots that own those
     * mechanics. Debug/sysop grants belong under `secrets { ... }`, not here.
     */
    fun reward(body: ScriptRewardBuilder.() -> Unit) {
        record(ScriptStatementNode.GrantReward(ScriptRewardBuilder().apply(body).build()))
    }

    fun gainGold(amount: IntExpr) {
        reward { gold(amount) }
    }

    fun gainGold(amount: Int): Unit = gainGold(lit(amount))

    fun gainTorches(amount: IntExpr) {
        party.torches += amount
    }

    fun gainTorches(amount: Int): Unit = gainTorches(lit(amount))

    fun gainFood(amount: IntExpr) {
        party.food += amount
    }

    fun gainFood(amount: Int): Unit = gainFood(lit(amount))

    fun shopService(
        building: BuildingId,
        body: ShopServiceBuilder.() -> Unit,
    ) {
        record(ScriptStatementNode.ShopService(ShopServiceBuilder(building).apply(body).build()))
    }

    fun achieve(milestone: GameMilestoneId) {
        record(ScriptStatementNode.AchieveMilestone(milestone))
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

    internal fun capture(body: ScriptScope.() -> Unit): List<ScriptStatementNode> = recordStatementNodes(ctx, body)
}

/**
 * Builds a reward embedded inside a script statement.
 *
 * Designers use this from `reward { ... }` style blocks to describe gold,
 * items, inventory-overflow policy, and the player-facing text shown when an
 * item must be dropped before the reward can be placed.
 */
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

    // Public authoring overload: callers may pass structured reward/drop text,
    // while the String overload below keeps common one-line cases simple.
    @Suppress("MemberVisibilityCanBePrivate")
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
            literalText(cantCarry),
            pickedUp?.let { literalText(it) },
        )
    }

    fun build(): ScriptRewardModel = ScriptRewardModel(goldAst, itemIds.toList(), applicationModel, messagesModel)
}

/**
 * Structured-text receiver for random-item offer prompts.
 *
 * It exposes the offered item name and price as typed text parts so game prose
 * can be authored once and rendered with the runtime-selected item.
 */
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

private fun randomItemOfferText(body: RandomItemOfferTextBuilder.() -> Unit): TextBlock = RandomItemOfferTextBuilder().apply(body).build()

/**
 * Builds a random shop/treasure offer inside a script choice.
 *
 * Designers use it to author the offer prose, buy/decline bindings, price
 * override, inventory-full policy, and the short pauses/messages around a
 * generated item purchase.
 */
@RylNextDsl
@Suppress("unused")
class RandomItemOfferBuilder(
    private val pool: ItemPoolId,
) {
    private var offerText: TextBlock? = null
    private var choicePromptText: TextBlock = literalText("(B)uy it", "(N)o thanks")
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
        offerText = literalText(*lines)
    }

    fun offer(text: TextBlock) {
        offerText = text
    }

    fun offer(body: RandomItemOfferTextBuilder.() -> Unit) {
        offerText = randomItemOfferText(body)
    }

    fun choicePrompt(vararg lines: String) {
        choicePromptText = literalText(*lines)
    }

    fun choicePrompt(text: TextBlock) {
        choicePromptText = text
    }

    fun choicePrompt(body: RandomItemOfferTextBuilder.() -> Unit) {
        choicePromptText = randomItemOfferText(body)
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
        brokeText = literalText(*lines)
    }

    fun broke(text: TextBlock) {
        brokeText = text
    }

    fun broke(body: RandomItemOfferTextBuilder.() -> Unit) {
        brokeText = randomItemOfferText(body)
    }

    fun full(vararg lines: String) {
        messagesModel = messagesModel.copy(cantCarry = literalText(*lines))
    }

    fun full(text: TextBlock) {
        messagesModel = messagesModel.copy(cantCarry = text)
    }

    fun full(body: RandomItemOfferTextBuilder.() -> Unit) {
        messagesModel = messagesModel.copy(cantCarry = randomItemOfferText(body))
    }

    // Public authoring overload: random offers can customize the picked-up
    // message with a prebuilt text row when one-line strings are not enough.
    @Suppress("MemberVisibilityCanBePrivate")
    fun promptDrop(pickedUp: TextBlock? = null) {
        applicationModel = applicationModel.copy(overflow = RewardOverflowPolicy.PromptDropThenPlace)
        messagesModel = messagesModel.copy(pickedUp = pickedUp)
    }

    fun exactFullInventoryReject() {
        applicationModel = applicationModel.copy(overflow = RewardOverflowPolicy.SkipIfInventoryFull)
    }

    fun promptDrop(pickedUp: String) {
        promptDrop(literalText(pickedUp))
    }

    fun deal(vararg lines: String) {
        dealText = literalText(*lines)
    }

    fun deal(text: TextBlock) {
        dealText = text
    }

    fun deal(body: RandomItemOfferTextBuilder.() -> Unit) {
        dealText = randomItemOfferText(body)
    }

    fun declined(vararg lines: String) {
        declinedText = literalText(*lines)
    }

    fun declined(text: TextBlock) {
        declinedText = text
    }

    fun declined(body: RandomItemOfferTextBuilder.() -> Unit) {
        declinedText = randomItemOfferText(body)
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

/**
 * Continuation returned by [ScriptScope.whenever] so a designer can attach the
 * `otherwise { ... }` branch immediately after an authored condition.
 */
class ElseBranch(
    private val ctx: ScriptContext,
    private val finish: (List<ScriptStatementNode>) -> Unit,
) {
    infix fun otherwise(body: ScriptScope.() -> Unit) {
        finish(recordStatementNodes(ctx, body))
    }
}

/**
 * Receiver for `choose(expr) { ... }` script blocks.
 *
 * Each case records a branch of script operations keyed by an integer value;
 * `default` records the fallback branch when no explicit case matches.
 */
@RylNextDsl
class SwitchBuilder(
    private val ctx: ScriptContext,
) {
    private val switchCases = mutableListOf<SwitchCase>()
    val cases: List<SwitchCase> get() = switchCases

    fun case(
        value: Int,
        body: ScriptScope.() -> Unit,
    ) {
        switchCases += SwitchCase(value, recordStatementNodes(ctx, body))
    }

    fun default(body: ScriptScope.() -> Unit) {
        switchCases += SwitchCase(null, recordStatementNodes(ctx, body))
    }
}

/**
 * Receiver for script menus that wait for one of several input bindings.
 *
 * Designers use `option` for explicit choices and `default`/`defaultOption`
 * for the branch taken when input does not match a declared option.
 */
@RylNextDsl
@Suppress("unused", "MemberVisibilityCanBePrivate")
class MenuBuilder(
    private val ctx: ScriptContext,
) {
    private val options = mutableListOf<MenuOption>()
    private var defaultStatements = emptyList<ScriptStatementNode>()

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
        options += MenuOption(binding, recordStatementNodes(ctx, body))
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
        val bodyStatements = recordStatementNodes(ctx, body)
        options += MenuOption(binding, bodyStatements)
        defaultStatements = bodyStatements
    }

    fun default(body: ScriptScope.() -> Unit) {
        defaultStatements = recordStatementNodes(ctx, body)
    }

    fun build(prompt: TextBlock): ScriptStatementNode.Menu = ScriptStatementNode.Menu(prompt, options.toList(), defaultStatements)
}

/**
 * Party member and inventory slot prompts offered by both `ask` and `choice` inside [ScriptScope].
 *
 * [AskScope] records the prompts; [ChoiceScope] delegates to its script's [AskScope].
 */
interface PartyPrompts {
    fun member(
        prompt: TextBlock,
        filter: MemberChoiceFilter = MemberChoiceFilter.Any,
        accepting: MemberChoiceFilter = filter,
    ): MemberRef

    fun member(
        prompt: TextBlock,
        filter: MemberChoiceFilter = MemberChoiceFilter.Any,
        accepting: MemberChoiceFilter = filter,
        body: ScriptScope.(MemberRef) -> Unit,
    )

    fun inventorySlot(
        prompt: TextBlock,
        firstKey: Char = 'A',
        slots: IntRange = 1..INVENTORY_SAVE_SLOT_MAX,
        filter: InventorySlotChoiceFilter = InventorySlotChoiceFilter.Any,
        body: ScriptScope.(InventorySlotRef) -> Unit,
    )
}

/**
 * Helper exposed as `ask` inside [ScriptScope].
 *
 * It records typed input prompts and returns script references to selected
 * values, such as keys, party members, inventory slots, or monsters, for later
 * operations in the same authored script block.
 */
@Suppress("unused")
class AskScope(
    private val script: ScriptScope,
) : PartyPrompts {
    fun key(
        prompt: TextBlock,
        vararg bindings: InputBindingModel,
    ): IntExpr {
        val uniqueBindings = bindings.toList()
        require(uniqueBindings.isNotEmpty()) { "key prompt must accept at least one key" }
        val name = script.fresh("key")
        script.record(ScriptStatementNode.AskKey(prompt, name, uniqueBindings))
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

    fun digit(
        prompt: TextBlock,
        range: IntRange,
        body: ScriptScope.(IntExpr) -> Unit,
    ) {
        script.body(digit(prompt, range))
    }

    fun wildernessDirection(prompt: TextBlock): IntExpr {
        val keys = WildernessKeysModel()
        return key(prompt, keys.moveSouth, keys.moveWest, keys.moveEast, keys.moveNorth)
    }

    fun wildernessDirection(
        prompt: TextBlock,
        body: ScriptScope.(IntExpr) -> Unit,
    ) {
        script.body(wildernessDirection(prompt))
    }

    override fun member(
        prompt: TextBlock,
        filter: MemberChoiceFilter,
        accepting: MemberChoiceFilter,
    ): MemberRef {
        val name = script.fresh("member")
        script.record(ScriptStatementNode.AskMember(prompt, name, filter, accepting))
        return script.party.member(v(name))
    }

    override fun member(
        prompt: TextBlock,
        filter: MemberChoiceFilter,
        accepting: MemberChoiceFilter,
        body: ScriptScope.(MemberRef) -> Unit,
    ) {
        script.body(member(prompt, filter, accepting))
    }

    fun inventorySlot(
        prompt: TextBlock,
        firstKey: Char = 'A',
        slots: IntRange = 1..INVENTORY_SAVE_SLOT_MAX,
        filter: InventorySlotChoiceFilter = InventorySlotChoiceFilter.Any,
    ): InventorySlotRef {
        val name = script.fresh("inventory_slot")
        script.record(ScriptStatementNode.AskInventorySlot(prompt, name, firstKey.uppercaseChar(), slots, filter))
        return InventorySlotRef(v(name))
    }

    override fun inventorySlot(
        prompt: TextBlock,
        firstKey: Char,
        slots: IntRange,
        filter: InventorySlotChoiceFilter,
        body: ScriptScope.(InventorySlotRef) -> Unit,
    ) {
        script.body(inventorySlot(prompt, firstKey, slots, filter))
    }

    fun monsterAlive(prompt: TextBlock): MonsterRef {
        val name = script.fresh("monster")
        script.record(ScriptStatementNode.AskMonster(prompt, name))
        return MonsterRef(script, v(name))
    }

    fun monsterAlive(
        prompt: TextBlock,
        body: ScriptScope.(MonsterRef) -> Unit,
    ) {
        script.body(monsterAlive(prompt))
    }
}

/**
 * Helper exposed as `choice` inside [ScriptScope].
 *
 * It packages input-driven branches, such as yes/no menus, member choices, and
 * keyboard text entry, while recording ordinary script input operations so
 * runtime input handling remains unified.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
class ChoiceScope(
    private val script: ScriptScope,
) : PartyPrompts by script.ask {
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
        yesNo(literalText(*promptLines), yes, no)
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
        script.record(ScriptStatementNode.RandomItemOffer(RandomItemOfferBuilder(pool).apply(body).build()))
    }

    fun textInput(
        prompt: TextBlock,
        acceptedInputs: List<String>,
        caseSensitive: Boolean = false,
        trim: Boolean = true,
        maxLength: Int? = null,
        onAccepted: ScriptScope.() -> Unit,
        onRejected: ScriptScope.() -> Unit,
    ) {
        require(acceptedInputs.isNotEmpty()) { "text input prompt must accept at least one value" }
        script.record(
            ScriptStatementNode.AskText(
                prompt = prompt,
                accepted = acceptedInputs,
                matchStatements = script.capture(onAccepted),
                missStatements = script.capture(onRejected),
                caseSensitive = caseSensitive,
                trim = trim,
                maxLength = maxLength,
            ),
        )
    }

    fun textInput(
        prompt: String,
        acceptedInputs: List<String>,
        caseSensitive: Boolean = false,
        trim: Boolean = true,
        maxLength: Int? = null,
        onAccepted: ScriptScope.() -> Unit,
        onRejected: ScriptScope.() -> Unit,
    ) {
        textInput(literalText(prompt), acceptedInputs, caseSensitive, trim, maxLength, onAccepted, onRejected)
    }
}

/**
 * Script reference to an inventory slot selected by an authored prompt.
 *
 * The index is an expression because the selected slot is only known at
 * runtime when the compiled script resumes after input.
 */
data class InventorySlotRef(
    val index: IntExpr,
)

/**
 * Helper exposed as `view` inside [ScriptScope] for runtime display commands.
 *
 * These calls record semantic view operations, such as clearing the text
 * window or showing an image, instead of embedding frontend-specific rendering
 * code in authored scripts.
 */
class ViewScope(
    private val script: ScriptScope,
) {
    fun clearText(): Unit = script.record(ScriptStatementNode.View(ViewCommand.ClearText))

    fun clearMapWindow(): Unit = script.record(ScriptStatementNode.View(ViewCommand.ClearMapWindow))

    fun renderMap(): Unit = script.record(ScriptStatementNode.View(ViewCommand.RenderMap))

    fun showBackdrop(image: ImageId): Unit = showImage(image)

    private fun showImage(image: ImageId): Unit = script.record(ScriptStatementNode.View(ViewCommand.ShowImage(image)))

    fun statusLine(): Unit = script.record(ScriptStatementNode.View(ViewCommand.StatusLine))

    /** Plays a one-shot sound effect; renderers without audio ignore it. */
    fun playSound(sound: SoundId): Unit = script.record(ScriptStatementNode.View(ViewCommand.PlaySound(sound)))

    /** Switches the background music until the player moves to another place. */
    fun playMusic(music: MusicId): Unit = script.record(ScriptStatementNode.View(ViewCommand.PlayMusic(music)))
}

internal fun recordStatementNodes(body: ScriptScope.() -> Unit): List<ScriptStatementNode> = recordStatementNodes(ScriptContext(), body)

internal fun recordStatementNodes(
    ctx: ScriptContext,
    body: ScriptScope.() -> Unit,
): List<ScriptStatementNode> = recordAuthoredScript(ctx, body).statementNodes

internal fun recordAuthoredScript(body: ScriptScope.() -> Unit): AuthoredScript = recordAuthoredScript(ScriptContext(), body)

internal fun recordAuthoredScript(
    ctx: ScriptContext,
    body: ScriptScope.() -> Unit,
): AuthoredScript = AuthoredScript(recordScriptBlock(ctx, body))

internal fun recordScriptBlock(
    ctx: ScriptContext,
    body: ScriptScope.() -> Unit,
): ScriptBlock {
    val builder = ScriptBlockBuilder()
    ctx.recording(builder) {
        ScriptScope(ctx, builder).body()
    }
    return builder.toBlock()
}

/**
 * A slice of game content declared in its own source file, such as all audio,
 * and added to the game with [GameBuilder.include].
 */
class GameSection internal constructor(
    internal val body: GameBuilder.() -> Unit,
)

/** Declares a [GameSection]; its body uses the same blocks as `content { ... }`. */
fun gameSection(body: GameBuilder.() -> Unit): GameSection = GameSection(body)

/**
 * Root receiver for a complete authored game.
 *
 * The game designer enters through `game { ... }` and fills this receiver with
 * catalogs, runtime systems, maps, scripts, prompts, and progression rules.
 * `build()` assembles the source graph later validated and emitted to bundles.
 */
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
    private var audioModel = AudioModel()
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

    fun audio(body: AudioBuilder.() -> Unit) {
        audioModel = AudioBuilder(audioModel).apply(body).build()
    }

    /**
     * Adds content declared in other source files, exactly as if it were
     * written here. Sections apply in order, so a later section can extend
     * blocks such as `visuals` or `audio` that an earlier one started.
     */
    fun include(vararg sections: GameSection) {
        sections.forEach { section -> section.body(this) }
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
        scenes += SceneModel(id, recordAuthoredScript(body))
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
        spells += SpellModel(id, slot, baseCost, name, recordAuthoredScript(body))
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
            audio = audioModel,
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

/**
 * Receiver for optional debug and secret systems.
 *
 * Designers declare what the sysop and a sysop-marked victim can do; each
 * server decides which accounts play those roles, so the game never names a
 * player and nobody gets the powers by picking a name.
 */
@RylNextDsl
class SecretSystemBuilder(
    initial: SecretSystemModel = SecretSystemModel(),
) {
    private val players = initial.players.toMutableList()

    /** Hooks for the sysop account, `admin` unless the server sets `RYLWEEN_ADMIN_ACCOUNT`. */
    fun admin(body: SecretPlayerBuilder.() -> Unit) {
        players += SecretPlayerBuilder(SecretRole.Admin).apply(body).build()
    }

    /** Hooks for the account the server names in `RYLWEEN_VICTIM_ACCOUNT`; nobody when it names none. */
    fun victim(body: SecretPlayerBuilder.() -> Unit) {
        players += SecretPlayerBuilder(SecretRole.Victim).apply(body).build()
    }

    fun build(): SecretSystemModel = SecretSystemModel(players = players.toList())
}

/**
 * Receiver for the secret hooks of one [SecretRole].
 *
 * It captures the hidden wilderness keys and the step trigger unlocked for the
 * account that plays the role.
 */
@RylNextDsl
class SecretPlayerBuilder(
    private val role: SecretRole,
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

    /**
     * Debug/sysop-only gold grant bound to a secret key action.
     *
     * Normal authored rewards should use `reward { gold(...) }` or
     * `gainGold(...)` so they compile through the same reward vocabulary used by
     * mission and dungeon content.
     */
    fun addGold(
        key: Char,
        amount: Int,
        refreshStatus: Boolean = false,
    ) {
        onKey(key, SecretActionModel.AddGold(amount, refreshStatus))
    }

    /**
     * Offers every item of [pool] in one go. Items go to free slots; for each
     * one that does not fit, the player is asked to drop something for it or
     * to pass it up.
     */
    fun grantItems(
        key: Char,
        pool: ItemPoolId,
    ) {
        onKey(key, SecretActionModel.GrantItems(pool))
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
            role = role,
            keyActions = keyActions.toList(),
            stepTrigger = stepTrigger,
            alwaysRunFromNonMandatoryEncounters = alwaysRunFromNonMandatoryEncounters,
        )
}

/**
 * Receiver for an authored world map.
 *
 * Designers add named map layers here; validation derives dimensions and checks
 * terrain/map consistency from the authored row data.
 */
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

/**
 * Receiver for one layer of a world map.
 *
 * Each `rows(...)` call supplies the exact source-map strings used to build the
 * traversable grid for wilderness movement and placement checks.
 */
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

/**
 * Receiver for all reusable runtime prompts.
 *
 * Designers use this to replace player-facing text that would otherwise be
 * hardcoded in runtime services: title menus, inventory use, equipment, party
 * pages, dungeon/wilderness prompts, saves, and character creation.
 */
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

/**
 * Receiver for authored pacing defaults.
 *
 * Designers use this to group gameplay delays that should be content-owned
 * rather than frontend implementation constants.
 */
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

/**
 * Receiver for battle pacing values.
 *
 * These delays are consumed by the battle runner around opening, message,
 * battle-cry, and victory output.
 */
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

/**
 * Receiver for character-creation pacing.
 *
 * Designers tune rejection and save-confirmation pauses for the reusable party
 * creation flow here.
 */
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

/**
 * Receiver for shop pacing.
 *
 * [interludePagePauseMs] controls the interlude pause reused by shop sessions;
 * [noticePauseMs] is how long a shop's closed or goodbye text stays up before
 * the town menu returns.
 */
@RylNextDsl
class ShopPacingBuilder(
    initial: ShopPacingModel = ShopPacingModel(),
) {
    var interludePagePauseMs: Int = initial.interludePagePauseMs
    var noticePauseMs: Int = initial.noticePauseMs

    fun build(): ShopPacingModel = ShopPacingModel(interludePagePauseMs = interludePagePauseMs, noticePauseMs = noticePauseMs)
}

/**
 * Receiver for speak/dialogue pacing.
 *
 * Designers tune delays around prompt echoes and response text without putting
 * timing literals inside individual dialogue branches.
 */
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

/**
 * Receiver for wilderness pacing.
 *
 * Designers tune wilderness-action delays, currently the camp message pause,
 * while the runtime applies the delay consistently.
 */
@RylNextDsl
class WildernessPacingBuilder(
    initial: WildernessPacingModel = WildernessPacingModel(),
) {
    var campMessagePauseMs: Int = initial.campMessagePauseMs

    fun build(): WildernessPacingModel = WildernessPacingModel(campMessagePauseMs = campMessagePauseMs)
}

/**
 * Receiver for title-screen menu prompts and command bindings.
 *
 * Designers author the visible menu header, menu choices, destructive-action
 * confirmations, unavailable-save text, and invalid-input text here.
 */
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
    var continueUnavailableReasonLabel: String = initial.continueUnavailableReasonLabel
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
            continueUnavailableReasonLabel = continueUnavailableReasonLabel,
            continueUnavailable = continueUnavailable,
            overwriteConfirm = overwriteConfirm,
            terminateConfirm = terminateConfirm,
            terminated = terminated,
            terminateFailed = terminateFailed,
            invalidInput = invalidInput,
        )
}

/**
 * Receiver for reusable spell-selection prompts.
 *
 * It owns the priest/mage selection wording, spell key range, and failure
 * messages used by the shared spell prompt runner. RETURN leaves its menus.
 */
@RylNextDsl
class PriestSpellPromptsBuilder(
    initial: PriestSpellPromptsModel = PriestSpellPromptsModel(),
) {
    var who: TextBlock = initial.who
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
            firstSpellKey = firstSpellKey,
            mageCombatOnly = mageCombatOnly,
            wrongClass = wrongClass,
            dead = dead,
            invalidCaster = invalidCaster,
            invalidSpell = invalidSpell,
            noSpells = noSpells,
        )
}

/**
 * Receiver for inventory item-use prompts.
 *
 * Designers author quaff/use/no-item text plus structured invalid-slot and
 * not-usable rows that runtime code renders with the selected slot or item.
 */
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
        invalidSlot = literalText(body)
    }

    fun notUsable(body: StructuredTextBlockBuilder.() -> Unit) {
        notUsable = literalText(body)
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

/**
 * Receiver for equipment prompts.
 *
 * It supplies inventory/equipping text and typed selected-item/kind prompt rows
 * used by ready/remove equipment flows in wilderness, dungeon, and castle.
 */
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

/**
 * Receiver for save prompts.
 *
 * Designers author the visible saving/done/failed text while the runtime owns
 * the save path and persistence diagnostics.
 */
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

/**
 * Receiver for character-creation prompt text.
 *
 * It owns the visible party/member/race/class prompts, labels, invalid-input
 * text, and save-confirmation row for the reusable character-creation session.
 */
@RylNextDsl
class CharacterCreationPromptsBuilder(
    initial: CharacterCreationPromptsModel = CharacterCreationPromptsModel(),
) {
    var header: TextBlock = initial.header
    var method: TextBlock = initial.method
    var manualLabel: String = initial.manualLabel
    var prerolledLabel: String = initial.prerolledLabel
    var partyName: TextBlock = initial.partyName
    private var statsPageText: TextBlock = initial.statsPage
    var reroll: TextBlock = initial.reroll
    private var memberNameText: TextBlock = initial.memberName
    private var genderText: TextBlock = initial.gender
    var genderMaleLabel: String = initial.genderMaleLabel
    var genderFemaleLabel: String = initial.genderFemaleLabel
    private var raceText: TextBlock = initial.race
    var classMenu: TextBlock = initial.classMenu
    var invalidInput: TextBlock = initial.invalidInput
    var saving: TextBlock = initial.saving
    var saveConfirmation: TextBlock = initial.saveConfirmation

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
            method = method,
            manualLabel = manualLabel,
            prerolledLabel = prerolledLabel,
            partyName = partyName,
            statsPage = statsPageText,
            reroll = reroll,
            memberName = memberNameText,
            gender = genderText,
            genderMaleLabel = genderMaleLabel,
            genderFemaleLabel = genderFemaleLabel,
            race = raceText,
            classMenu = classMenu,
            invalidInput = invalidInput,
            saving = saving,
            saveConfirmation = saveConfirmation,
        )
}

/**
 * Receiver for town-entry and town-menu prompt text.
 *
 * Designers author the reusable town menu title and entry/tax messages shared
 * by the town session.
 */
@RylNextDsl
class TownPromptsBuilder(
    initial: TownPromptsModel = TownPromptsModel(),
) {
    private var menuTitleText: TextBlock = initial.menuTitle
    var entryWelcome: TextBlock = initial.entryWelcome
    var entryTax: TextBlock = initial.entryTax
    var entryTaxThanks: TextBlock = initial.entryTaxThanks

    fun menuTitle(body: StructuredTextBlockBuilder.() -> Unit) {
        menuTitleText = literalText(body)
    }

    fun build(): TownPromptsModel =
        TownPromptsModel(
            menuTitle = menuTitleText,
            entryWelcome = entryWelcome,
            entryTax = entryTax,
            entryTaxThanks = entryTaxThanks,
        )
}

/**
 * Receiver for shared party-page and inventory-drop prompts.
 *
 * Party view, journal, inventory, and drop flows consume these rows from
 * `runtimeSystems.prompts.party` instead of hardcoding prose in the runtime.
 */
@RylNextDsl
class PartyPromptsBuilder(
    initial: PartyPromptsModel = PartyPromptsModel(),
) {
    var journalHeader: TextBlock = initial.journalHeader

    // Public prompt overrides consumed from runtimeSystems by PartyPages.
    @Suppress("MemberVisibilityCanBePrivate")
    var journalEmpty: TextBlock = initial.journalEmpty

    // Public prompt overrides consumed from runtimeSystems by PartyPages.
    @Suppress("MemberVisibilityCanBePrivate")
    var inventoryHeader: TextBlock = initial.inventoryHeader

    // Heading and empty-ledger text of the monsters-slain pages that end the party view.
    var killsHeader: TextBlock = initial.killsHeader
    var killsEmpty: TextBlock = initial.killsEmpty
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

    fun dropped(text: TextBlock) {
        dropDroppedText = text
    }

    fun build(): PartyPromptsModel =
        PartyPromptsModel(
            journalHeader = journalHeader,
            journalEmpty = journalEmpty,
            inventoryHeader = inventoryHeader,
            killsHeader = killsHeader,
            killsEmpty = killsEmpty,
            dropPrompt = dropPrompt,
            dropEmptySlot = dropEmptySlot,
            dropAborted = dropAborted,
            dropDropped = dropDroppedText,
            dropCapacityReduced = dropCapacityReduced,
            dropOverflowConfirm = dropOverflowConfirm,
            dropOverflowPrompt = dropOverflowPrompt,
        )
}

/**
 * Receiver for wilderness action prompts.
 *
 * Designers author pass/no-moves/quit/entry failure text and the structured
 * dungeon-handoff prompt used when a cave or mission transfers to a dungeon.
 */
@RylNextDsl
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

/**
 * Receiver for reusable dungeon prompts.
 *
 * Designers author level-selection rows, max-depth/no-torch failures, and
 * cavern-leave text used by the dungeon session.
 */
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

/**
 * Structured prompt receiver for wilderness-to-dungeon handoff text.
 *
 * It exposes dungeon name and level count as typed text parts so the runtime
 * supplies the discovered dungeon facts while the prose remains authored.
 */
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

/**
 * Structured prompt receiver for dungeon depth text.
 *
 * Designers use the `level` text part in level menus and enter-level prompts.
 */
@RylNextDsl
class DungeonLevelPromptBuilder : TextLineSink {
    private val text = StructuredTextBlockBuilder()

    val level: TextPart = TextPart.DungeonDepth

    override fun addTextLine(line: TextLine) {
        text.addTextLine(line)
    }

    fun build(): TextBlock = text.build()
}

/**
 * Structured prompt receiver for selected-item text.
 *
 * Equipment, drop, and item-use prompts use this when the runtime must inject
 * the selected item name into authored prose.
 */
@RylNextDsl
class SelectedItemPromptBuilder : TextLineSink {
    private val text = StructuredTextBlockBuilder()

    val itemName: TextPart = TextPart.SelectedItemName

    override fun addTextLine(line: TextLine) {
        text.addTextLine(line)
    }

    fun build(): TextBlock = text.build()
}

/**
 * Structured prompt receiver for equipment-kind text.
 *
 * Designers use `equipmentKind` in generic weapon/armor prompts shared by
 * ready/remove equipment flows.
 */
@RylNextDsl
class EquipmentKindPromptBuilder : TextLineSink {
    private val text = StructuredTextBlockBuilder()

    val equipmentKind: TextPart = TextPart.EquipmentKindName

    override fun addTextLine(line: TextLine) {
        text.addTextLine(line)
    }

    fun build(): TextBlock = text.build()
}

/**
 * Structured prompt receiver for character stat pages.
 *
 * It exposes rolled stat fields and member number as text parts for the
 * character-creation screen.
 */
@RylNextDsl
class CharacterCreationStatsPromptBuilder : TextLineSink {
    private val text = StructuredTextBlockBuilder()

    val memberNumber: TextPart = TextPart.CreationMemberNumber
    val weaponSkill: TextPart = TextPart.CreationWeaponSkill
    val health: TextPart = TextPart.CreationHealth
    val wisdom: TextPart = TextPart.CreationWisdom
    val charisma: TextPart = TextPart.CreationCharisma
    val dexterity: TextPart = TextPart.CreationDexterity

    override fun addTextLine(line: TextLine) {
        text.addTextLine(line)
    }

    fun build(): TextBlock = text.build()
}

/**
 * Structured prompt receiver for character-creation member-number prompts.
 *
 * Designers use this when a prompt needs the current member slot number.
 */
@RylNextDsl
class CharacterCreationMemberNumberPromptBuilder : TextLineSink {
    private val text = StructuredTextBlockBuilder()

    val memberNumber: TextPart = TextPart.CreationMemberNumber

    override fun addTextLine(line: TextLine) {
        text.addTextLine(line)
    }

    fun build(): TextBlock = text.build()
}

/**
 * Structured prompt receiver for character-creation member-name prompts.
 *
 * Designers use this when a prompt needs the current member's authored name.
 */
@RylNextDsl
class CharacterCreationMemberNamePromptBuilder : TextLineSink {
    private val text = StructuredTextBlockBuilder()

    val memberName: TextPart = TextPart.CreationMemberName

    override fun addTextLine(line: TextLine) {
        text.addTextLine(line)
    }

    fun build(): TextBlock = text.build()
}

/**
 * Receiver for wilderness runtime system configuration.
 *
 * Designers use it to author wilderness key bindings, random events, and
 * terrain-transition narration consumed by the wilderness session.
 */
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

/**
 * Receiver for wilderness command bindings and help text.
 *
 * The wilderness runner exports these bindings through `runtimeSystems.commands`
 * and uses the same model for movement, cave prompts, out-of-moves prompts,
 * quit confirmation, and debug-only commands.
 */
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
        commandHintText = literalText(*lines)
    }

    fun commandHint(body: StructuredTextBlockBuilder.() -> Unit) {
        commandHintText = literalText(body)
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

/**
 * Receiver for reusable command-help pages.
 *
 * Wilderness and dungeon key builders use these pages for the runtime command
 * help static screen.
 */
@RylNextDsl
class WildernessHelpPagesBuilder {
    private val pages = mutableListOf<TextBlock>()

    fun page(vararg lines: String) {
        pages += literalText(*lines)
    }

    fun page(body: StructuredTextBlockBuilder.() -> Unit) {
        pages += literalText(body)
    }

    fun build(): List<TextBlock> = pages.toList()
}

/**
 * Receiver for dungeon runtime system configuration.
 *
 * Designers declare reusable dungeon events, named dungeon scenarios, backdrop,
 * and dungeon command bindings here; runtime sessions consume the built system.
 */
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

/**
 * Receiver for dungeon command bindings and help text.
 *
 * The dungeon runner exports these bindings through `runtimeSystems.commands`
 * and uses them for descent/ascent, equipment, item, spell, party, save, and
 * help commands.
 */
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
    private var commandLabels: DungeonCommandLabelsModel = initial.commandLabels
    private var commandHintText = initial.commandHint
    private var helpPageText = initial.helpPages

    fun commandHint(vararg lines: String) {
        commandHintText = literalText(*lines)
    }

    fun commandHint(body: StructuredTextBlockBuilder.() -> Unit) {
        commandHintText = literalText(body)
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

/**
 * Receiver for one reusable dungeon event.
 *
 * Designers author event disposition, optional random encounter, optional
 * clear-when flag, and the script that runs when the event is selected.
 */
@RylNextDsl
class DungeonEventBuilder(
    private val id: DungeonEventId,
) {
    private var disposition = DungeonEventDisposition.ClearAfterRun
    private var randomEncounterTier: EncounterTier? = null
    private var clearWhenFlag: FlagState? = null
    private val statements = mutableListOf<ScriptStatement>()

    fun recurring() {
        disposition = DungeonEventDisposition.Recurring
        clearWhenFlag = null
    }

    fun recurringUntil(flag: FlagState) {
        disposition = DungeonEventDisposition.Recurring
        clearWhenFlag = flag
    }

    /** The event runs on every visit to its level until the party wins its battle; fleeing leaves it in place. */
    fun recurringUntilWon() {
        disposition = DungeonEventDisposition.RecurringUntilWon
        clearWhenFlag = null
    }

    fun randomEncounter(tier: EncounterTier) {
        randomEncounterTier = tier
    }

    fun script(body: ScriptScope.() -> Unit) {
        statements += recordScriptBlock(ScriptContext(), body).statements
    }

    fun build(): DungeonEventModel =
        DungeonEventModel(
            id = id,
            disposition = disposition,
            script = AuthoredScript(ScriptBlock(statements.toList())),
            randomEncounterTier = randomEncounterTier,
            clearWhenFlag = clearWhenFlag,
        )
}

/**
 * Receiver for one named dungeon scenario.
 *
 * It maps dungeon depth to fixed events and weighted random event pools for a
 * specific dungeon id.
 */
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

/**
 * Receiver for a weighted pool of dungeon random events.
 *
 * Dungeon scenarios use this to choose non-fixed level events.
 */
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

/**
 * Receiver for wilderness random-event tuning.
 *
 * Designers set cave discovery, quiet-move, encounter split, cave dungeon/depth,
 * move cost, imagery, and cave discovery/failure text here.
 */
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
        caveFoundPromptText = literalText(*lines)
    }

    fun caveFoundPrompt(text: TextBlock) {
        caveFoundPromptText = text
    }

    fun caveFoundPrompt(body: StructuredTextBlockBuilder.() -> Unit) {
        caveFoundPromptText = literalText(body)
    }

    fun caveNoMoves(vararg lines: String) {
        caveNoMovesText = literalText(*lines)
    }

    fun caveNoMoves(text: TextBlock) {
        caveNoMovesText = text
    }

    fun caveNoMoves(body: StructuredTextBlockBuilder.() -> Unit) {
        caveNoMovesText = literalText(body)
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

/**
 * Receiver for authored terrain-transition narration.
 *
 * The wilderness runner may show one of these entries on quiet movement when
 * entering matching terrain.
 */
@RylNextDsl
class TerrainTransitionBuilder {
    var chancePercent: Int = 9
    private val entries = mutableListOf<TerrainTransitionTextModel>()

    fun terrain(
        id: TerrainId,
        vararg lines: String,
    ) {
        entries += TerrainTransitionTextModel(id, literalText(*lines))
    }

    fun terrain(
        id: TerrainId,
        text: TextBlock,
    ) {
        entries += TerrainTransitionTextModel(id, text)
    }

    fun terrain(
        id: TerrainId,
        body: StructuredTextBlockBuilder.() -> Unit,
    ) {
        entries += TerrainTransitionTextModel(id, literalText(body))
    }

    fun build(): TerrainTransitionModel = TerrainTransitionModel(chancePercent = chancePercent, entries = entries.toList())
}

/**
 * Receiver for NPC dialogue catalog entries.
 *
 * Designers group all dialogue branches for NPCs here; each branch is still an
 * authored script recorded through [ScriptScope].
 */
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

/**
 * Receiver for one NPC's dialogue branches.
 *
 * Branches can be gated by a flag state or declared as the default branch for
 * the reusable speak session.
 */
@RylNextDsl
class NpcDialogueBuilder(
    private val id: NpcId,
) {
    private val branches = mutableListOf<DialogueBranchModel>()

    fun whenState(
        state: FlagState,
        body: ScriptScope.() -> Unit,
    ) {
        branches += DialogueBranchModel(state, recordAuthoredScript(body))
    }

    fun default(body: ScriptScope.() -> Unit) {
        branches += DialogueBranchModel(null, recordAuthoredScript(body))
    }

    fun build(): DialogueModel = DialogueModel(id, branches.toList())
}

/**
 * Receiver for authored town catalog entries.
 *
 * Designers declare each town's id, display name, world-map placement, kind,
 * and optional services through nested town builders.
 */
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

/**
 * Receiver for one town declaration.
 *
 * Designers set display name, map position, city/village kind, tax rate, and
 * the set of building services available in the town.
 */
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

/**
 * Receiver for the services available in one town.
 *
 * It records which standard or custom buildings appear in that town; detailed
 * building behavior is declared in the shared town system.
 */
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

/**
 * Receiver for shared town runtime behavior.
 *
 * Designers author the town menu, entry-tax keys, leave-equipment prompts, and
 * service catalog consumed by all town sessions.
 */
@RylNextDsl
class TownSystemBuilder {
    private var menuModel = TownMenuBuilder().build()
    private var buildingServiceModels = BuildingServiceCatalogBuilder().build()
    private var leavePromptCommands: List<EquipmentCommand> = emptyList()
    private var entryTaxModel: TownEntryTaxModel = TownEntryTaxModel()

    /** The street view shown with the town menu and the entry tax. */
    var image: ImageId? = null

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
            image = image,
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

/**
 * Receiver for the town-entry tax interaction.
 *
 * It declares the tax formula and the pay/leave bindings used when a town
 * charges entry tax.
 */
@RylNextDsl
class TownEntryTaxBuilder(
    initial: TownEntryTaxModel = TownEntryTaxModel(),
) {
    /** The gold a party pays to enter a town, as a formula; a town with nothing to charge lets it in. */
    var amount: FormulaId? = initial.amount
    var pay: InputBindingModel = initial.pay
    var leave: InputBindingModel = initial.leave

    fun build(): TownEntryTaxModel =
        TownEntryTaxModel(
            amount = amount,
            pay = pay,
            leave = leave,
        )
}

/**
 * Receiver for town menu command rows.
 *
 * Designers map visible keys to building services or the leave action. The
 * resulting commands are exported through `runtimeSystems.commands.town`.
 */
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

/**
 * Receiver for reusable building-service definitions.
 *
 * Designers define the shared service scene, action id, and opening hours for
 * church, smith, inn, merchant, tavern, guild, or custom services.
 */
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

/**
 * Receiver for one reusable building service.
 *
 * Designers can override the command action, entry scene, and opening hours
 * for the service selected from the town menu.
 */
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

/**
 * Receiver for authored flag metadata.
 *
 * Designers declare which generated flag handles are story or encounter flags
 * and can narrow the explicit state set used by validation and diagnostics.
 */
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

/**
 * Receiver for one authored flag metadata entry.
 *
 * It optionally overrides the flag's declared state list for validation and
 * content reporting.
 */
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

/**
 * Receiver for the monster catalog.
 *
 * Designers declare combat stats, encounter tier, counts, image, and immunity
 * for each monster used by battle and encounter systems.
 */
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

/**
 * Receiver for one monster definition.
 *
 * It owns the authored combat-facing numbers and optional encounter image for a
 * stable monster id.
 */
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

    // Member stats, bound by the system that evaluates the formula: the
    // hunting ranger, the thief disarming a castle trap, the caster of a spell,
    // or, for a haggle roll bonus, the party's highest living charisma.
    val charisma: IntExpr = v("charisma")
    val dexterity: IntExpr = v("dexterity")
    val level: IntExpr = v("level")

    // The spell being cast, for the spell cost formula; the caster's stats are bound above.
    val spellBaseCost: IntExpr = v("spell_base_cost")

    // Upkeep and town entry: the party's living members and the gold it
    // carries, and the tax rate of the town it enters.
    val livingMembers: IntExpr = v("living_members")
    val gold: IntExpr = v("gold")
    val taxPercent: IntExpr = v("tax_percent")

    // Battle arithmetic, bound by the battle runner: the member who attacks or
    // defends, or the one a monster attacks; the attacking monster; and the
    // monster an encounter is made of.
    val weaponSkill: IntExpr = v("weapon_skill")
    val weaponDamage: IntExpr = v("weapon_damage")
    val hitPoints: IntExpr = v("hit_points")
    val armorClass: IntExpr = v("armor_class")
    val partyConfused: BoolExpr = Bool(BoolAst.Cmp(IntAst.Var("party_confused"), "ne", IntAst.Lit(0)))
    val monsterHitPoints: IntExpr = v("monster_hit_points")
    val monsterCountMax: IntExpr = v("monster_count_max")
    val countMaxLevelDivisor: IntExpr = v("count_max_level_divisor")

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
    private var script = AuthoredScript(emptyList())

    fun usableIn(vararg contexts: ItemUseContext) {
        this.contexts.clear()
        this.contexts += contexts
    }

    fun combat(): Unit = usableIn(ItemUseContext.Combat)

    fun inventory(): Unit = usableIn(ItemUseContext.Inventory)

    fun effect(body: ScriptScope.() -> Unit) {
        script = recordAuthoredScript(body)
    }

    fun build(): ItemUseModel = ItemUseModel(id, contexts.toSet(), consume, script)
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
