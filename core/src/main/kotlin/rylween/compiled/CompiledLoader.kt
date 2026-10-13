/**
 * Loader and validation boundary for compiled J1 game bundles.
 *
 * [CompiledBundleLoader] indexes `CompiledBundle.kt` data, enforces
 * [CompiledOpcodeContract], and checks native expression paths against
 * `runtime/vm/NativeServiceCatalog.kt` before runtime hosts see a bundle.
 */
package rylween.compiled

import rylween.runtime.ScriptRootRef
import rylween.runtime.vm.NativeServiceArgSlot
import rylween.runtime.vm.NativeServiceCatalog
import rylween.runtime.vm.VmDiagnostic

/**
 * Runtime-facing view of a validated [CompiledGameBundle].
 *
 * `CompiledLoader.kt` is the J1/J2 boundary between compiled data and runtime
 * consumers. It validates cross-table references, script opcode shapes from
 * [CompiledOpcodeContract], and native expression paths from [NativeServiceCatalog],
 * then exposes lookup maps used by Kotlin hosts and future compiled VM code in
 * `runtime/vm`.
 */
data class LoadedCompiledGame(
    val content: LoadedCompiledContent,
    val states: Map<CompiledHandleRef, CompiledStateImage>,
)

/**
 * Materialized compiled content with lookup helpers.
 *
 * This keeps the raw tables from `CompiledBundle.kt` but adds hash buckets and
 * direct script lookup maps so callers do not need to rebuild indexes after
 * loading a J1 artifact.
 */
data class LoadedCompiledContent(
    val raw: CompiledContentBundle,
    val strings: CompiledStringTable,
    val handles: CompiledHandleTable,
    val stringHashBuckets: Map<Int, List<Int>>,
    val handleHashBuckets: Map<Int, List<CompiledHandleRef>>,
    val scripts: Map<CompiledHandleRef, CompiledScriptProgram>,
) {
    /** Resolves a compiled string id through the loaded string table. */
    fun string(id: Int): String = strings.value(id)

    /** Resolves a compiled handle ref through the loaded handle table. */
    fun handle(ref: CompiledHandleRef): CompiledHandle = handles.handle(ref)

    /** Returns a compiled script program by root handle, or `null` if absent. */
    fun script(root: CompiledHandleRef): CompiledScriptProgram? = scripts[root]
}

/**
 * Validates and materializes this compiled bundle with the default VM native catalog.
 *
 * Pass a custom [nativeServices] catalog when testing host-specific service
 * binding. Production callers should normally use [NativeServiceCatalog.default]
 * so native-expression loader checks match the DSL [rylween.dsl.NativeAllowlist].
 */
fun CompiledGameBundle.loadCompiledGame(nativeServices: NativeServiceCatalog = NativeServiceCatalog.default): LoadedCompiledGame =
    CompiledBundleLoader.load(this, nativeServices)

/** Loader entry point for validating and indexing compiled game bundles. */
object CompiledBundleLoader {
    /**
     * Validates [bundle] and returns indexed runtime-facing compiled content.
     *
     * Validation rejects malformed handle/string references, opcode operand
     * shapes, and unallowlisted native-expression paths before the existing authored
     * interpreter or future compiled VM can execute the script stream.
     */
    fun load(
        bundle: CompiledGameBundle,
        nativeServices: NativeServiceCatalog = NativeServiceCatalog.default,
    ): LoadedCompiledGame {
        CompiledBundleValidator(bundle, nativeServices).validate()
        val content = bundle.content
        return LoadedCompiledGame(
            content =
                LoadedCompiledContent(
                    raw = content,
                    strings = content.strings,
                    handles = content.handles,
                    stringHashBuckets = content.strings.hashEntries.groupBy({ it.hash }, { it.stringId }),
                    handleHashBuckets = content.handles.hashEntries.groupBy({ it.hash }, { it.ref }),
                    scripts = content.scripts.roots.associateBy { it.rootHandle },
                ),
            states = bundle.states.associateBy { it.state },
        )
    }
}

private class CompiledBundleValidator(
    private val bundle: CompiledGameBundle,
    private val nativeServices: NativeServiceCatalog,
) {
    private val content = bundle.content
    private val handleRefs =
        content.handles.handles
            .map { it.ref }
            .toSet()

    fun validate() {
        require(
            content.handles.handles
                .map { it.denseId }
                .toSet()
                .size == content.handles.handles.size,
        ) {
            "compiled handle dense ids must be unique"
        }
        require(
            content.handles.handles
                .map { it.ref }
                .toSet()
                .size == content.handles.handles.size,
        ) {
            "compiled handle refs must be unique"
        }
        content.handles.handles.forEach { handle ->
            string(handle.slugString, "handle slug ${handle.ref}")
        }
        string(content.universe.nameString, "universe name")
        string(content.universe.versionString, "universe version")
        string(content.universe.compatibilityIdString, "universe compatibility id")
        validateCatalogTables()
        validatePresentationTables()
        validateTownAndCharacterTables()
        validateScriptedGameplayTables()
        validateEach(bundle.states, ::state)
    }

    private fun <T> validateEach(
        values: Iterable<T>,
        validate: (T) -> Unit,
    ) {
        values.forEach(validate)
    }

    private fun validateCatalogTables() {
        validateEach(content.flags, ::flag)
        validateEach(content.items, ::item)
        validateEach(content.itemPools, ::itemPool)
        validateEach(content.formulas, ::formula)
        validateEach(content.rewards, ::rewardBundle)
    }

    private fun validatePresentationTables() {
        validateEach(content.terrain, ::terrain)
        validateEach(content.worldMaps, ::worldMap)
        validateEach(content.images, ::image)
        validateEach(content.staticScreens, ::staticScreen)
        validateEach(content.screenRegions) { handle(it.id, "screen region") }
    }

    private fun validateTownAndCharacterTables() {
        validateEach(content.towns, ::town)
        validateEach(content.buildingServices, ::buildingService)
        validateEach(content.characters, ::character)
        validateEach(content.monsters, ::monster)
        validateEach(content.missions, ::mission)
    }

    private fun validateScriptedGameplayTables() {
        validateEach(content.spells, ::spell)
        validateEach(content.itemUses, ::itemUse)
        validateEach(content.dungeonEvents, ::dungeonEvent)
        validateEach(content.dungeonScenarios, ::dungeonScenario)
        validateEach(content.scripts.roots, ::script)
    }

    private fun flag(flag: CompiledFlag) {
        handle(flag.id, "flag")
        flag.states.forEach { state ->
            handle(state.id, "flag state")
            string(state.nativeString, "flag native")
        }
    }

    private fun item(item: CompiledItemRecord) {
        handle(item.id, "item")
        string(item.displayNameString, "item display name")
        item.campImage?.let { handle(it, "item camp image") }
    }

    private fun itemPool(pool: CompiledItemPool) {
        handle(pool.id, "item pool")
        pool.items.forEach { handle(it, "item pool item") }
    }

    private fun formula(formula: CompiledFormula) {
        handle(formula.id, "formula")
        intExpr(formula.value)
    }

    private fun rewardBundle(reward: CompiledRewardBundle) {
        handle(reward.id, "reward bundle")
        handle(reward.experience, "reward experience formula")
        handle(reward.gold, "reward gold formula")
        loot(reward.loot)
    }

    private fun loot(loot: CompiledLootTable) {
        handle(loot.id, "loot table")
        handle(loot.treasure, "loot treasure pool")
        handle(loot.magic, "loot magic pool")
    }

    private fun terrain(terrain: CompiledTerrain) {
        handle(terrain.id, "terrain")
        terrain.tags.forEach { handle(it, "terrain tag") }
    }

    private fun worldMap(map: CompiledWorldMap) {
        handle(map.id, "world map")
        map.layers.forEach { layer ->
            handle(layer.id, "world map layer")
            layer.rowStrings.forEach { string(it, "world map row") }
        }
    }

    private fun image(image: CompiledImageAsset) {
        handle(image.id, "image")
        string(image.resourceString, "image resource")
    }

    private fun staticScreen(screen: CompiledStaticScreen) {
        handle(screen.id, "static screen")
        screen.image?.let { handle(it, "static screen image") }
        screen.resourceString?.let { string(it, "static screen resource") }
    }

    private fun town(town: CompiledTown) {
        handle(town.id, "town")
        string(town.nameString, "town name")
        town.services.forEach { handle(it, "town service") }
    }

    private fun buildingService(service: CompiledBuildingService) {
        handle(service.building, "building service building")
        handle(service.action, "building service action")
        handle(service.scene, "building service scene")
    }

    private fun character(character: CompiledCharacter) {
        handle(character.id, "character")
        string(character.nameString, "character name")
        handle(character.job, "character job")
        character.roles.forEach { handle(it, "character role") }
        when (val placement = character.placement) {
            is CompiledCharacterPlacement.Fixed -> position(placement.position)
            is CompiledCharacterPlacement.Randomized -> randomNpcPlacementPolicy(placement.policy)
            CompiledCharacterPlacement.Unplaced -> Unit
        }
    }

    private fun randomNpcPlacementPolicy(policy: CompiledRandomNpcPlacementPolicy) {
        require(policy.options.isNotEmpty()) { "random NPC placement policy has no options" }
        policy.options.forEach { option ->
            require(option.chanceShares > 0) {
                "random NPC placement option has non-positive chanceShares ${option.chanceShares}"
            }
            when (option) {
                is CompiledRandomNpcPlacementOption.TownServices -> {
                    handle(option.town, "random NPC placement town")
                    option.services.forEach { service -> handle(service, "random NPC placement town service") }
                }
                is CompiledRandomNpcPlacementOption.WildernessRect -> Unit
            }
        }
    }

    private fun monster(monster: CompiledMonster) {
        handle(monster.id, "monster")
        string(monster.nameString, "monster name")
        monster.tier?.let { handle(it, "monster tier") }
        monster.encounterImage?.let { handle(it, "monster encounter image") }
    }

    private fun mission(mission: CompiledMission) {
        handle(mission.id, "mission")
        trigger(mission.trigger)
        mission.requires?.let(::boolExpr)
        handle(mission.action, "mission action")
    }

    private fun trigger(trigger: CompiledTrigger) {
        when (trigger) {
            is CompiledTrigger.AtLocation -> handle(trigger.location, "location trigger")
            is CompiledTrigger.OnTerrain -> handle(trigger.terrain, "terrain trigger")
            is CompiledTrigger.AtPosition,
            is CompiledTrigger.RandomStep,
            -> Unit
        }
    }

    private fun spell(spell: CompiledSpell) {
        handle(spell.id, "spell")
        string(spell.nameString, "spell name")
        handle(spell.scriptRoot, "spell script root")
    }

    private fun itemUse(use: CompiledItemUse) {
        handle(use.item, "item use item")
        handle(use.scriptRoot, "item use script root")
    }

    private fun dungeonEvent(event: CompiledDungeonEvent) {
        handle(event.id, "dungeon event")
        handle(event.scriptRoot, "dungeon event script root")
        event.randomEncounterTier?.let { handle(it, "dungeon event encounter tier") }
        event.clearWhenFlag?.let { handle(it, "dungeon event clear flag") }
    }

    private fun dungeonScenario(scenario: CompiledDungeonScenario) {
        handle(scenario.dungeon, "dungeon scenario")
        scenario.nameString?.let { string(it, "dungeon scenario name") }
        scenario.fixedLevelEvents.values.forEach { handle(it, "fixed dungeon event") }
        scenario.randomEvents.forEach { event ->
            handle(event.event, "random dungeon event")
        }
    }

    private fun script(script: CompiledScriptProgram) {
        handle(script.rootHandle, "script root")
        require(script.rootHandle.kind == CompiledHandleKind.ScriptRoot) {
            "script program root handle must be ScriptRoot"
        }
        instructions(script.root, script.instructions, emptyList())
    }

    private fun instructions(
        root: ScriptRootRef,
        instructions: List<CompiledInstruction>,
        operandPath: List<Int>,
    ) {
        instructions.forEachIndexed { instructionIndex, instruction ->
            validateInstruction(root, instructionIndex, operandPath, instruction)
            instruction.operands.forEachIndexed { operandIndex, operand ->
                operand(
                    OperandValidationContext(
                        root = root,
                        instructionIndex = instructionIndex,
                        operandPath = operandPath + listOf(instructionIndex, operandIndex),
                        opcode = instruction.opcode,
                    ),
                    operand,
                )
            }
        }
    }

    private fun validateInstruction(
        root: ScriptRootRef,
        instructionIndex: Int,
        operandPath: List<Int>,
        instruction: CompiledInstruction,
    ) {
        when (val result = CompiledOpcodeContract.validateInstruction(instruction)) {
            CompiledOpcodeContract.Validation.Ok -> Unit
            is CompiledOpcodeContract.Validation.UnknownOpcode ->
                fail(
                    VmDiagnostic.BadOpcode(
                        root = root,
                        instructionIndex = instructionIndex,
                        operandPath = operandPath,
                        opcode = result.opcode,
                        reason = "UnknownOpcode",
                    ),
                )

            is CompiledOpcodeContract.Validation.WrongShape ->
                fail(
                    VmDiagnostic.BadOperand(
                        root = root,
                        instructionIndex = instructionIndex,
                        operandPath = operandPath,
                        opcode = result.opcode,
                        reason = "WrongShape",
                        expected = result.expected.toString(),
                        actual = result.actual.toString(),
                    ),
                )
        }
    }

    private fun fail(diagnostic: VmDiagnostic): Nothing = throw IllegalArgumentException(diagnostic.message())

    private data class OperandValidationContext(
        val root: ScriptRootRef,
        val instructionIndex: Int,
        val operandPath: List<Int>,
        val opcode: CompiledOpcode,
    )

    private fun operand(
        context: OperandValidationContext,
        operand: CompiledOperand,
    ) {
        when (operand) {
            is CompiledOperand.StringId -> string(operand.id, "script string")
            is CompiledOperand.StringIdList -> operand.ids.forEach { string(it, "script string list") }
            is CompiledOperand.Handle -> handle(operand.ref, "script handle")
            is CompiledOperand.HandleList -> operand.refs.forEach { handle(it, "script handle list") }
            is CompiledOperand.TextBlockValue -> text(operand.value, context)
            is CompiledOperand.IntExprValue -> intExpr(operand.value, context)
            is CompiledOperand.BoolExprValue -> boolExpr(operand.value, context)
            is CompiledOperand.StringExprValue -> stringExpr(operand.value, context)
            is CompiledOperand.LValueValue -> lvalue(operand.value, context)
            is CompiledOperand.ViewCommandValue -> view(operand.value)
            is CompiledOperand.Instructions -> instructions(context.root, operand.instructions, context.operandPath)
            is CompiledOperand.SwitchCases ->
                operand.cases.forEachIndexed { caseIndex, case ->
                    instructions(context.root, case.body, context.operandPath + caseIndex)
                }

            is CompiledOperand.MenuOptions ->
                operand.options.forEachIndexed { optionIndex, option ->
                    binding(option.binding)
                    instructions(context.root, option.body, context.operandPath + optionIndex)
                }

            is CompiledOperand.InputBindings -> operand.bindings.forEach(::binding)
            is CompiledOperand.TextAnswer -> textAnswer(context.root, context.operandPath, operand.answer)
            is CompiledOperand.Reward -> scriptReward(operand.reward, context)
            is CompiledOperand.RandomItemOffer -> randomItemOffer(operand.offer, context)
            is CompiledOperand.ShopService -> shopService(operand.service, context)
            is CompiledOperand.Integer,
            is CompiledOperand.BooleanValue,
            is CompiledOperand.Character,
            is CompiledOperand.EnumName,
            -> Unit
        }
    }

    private fun textAnswer(
        root: ScriptRootRef,
        operandPath: List<Int>,
        answer: CompiledTextAnswer,
    ) {
        answer.acceptedStrings.forEach { string(it, "text answer") }
        instructions(root, answer.matchOps, operandPath + 0)
        instructions(root, answer.missOps, operandPath + 1)
    }

    private fun binding(binding: CompiledInputBinding) {
        string(binding.primaryString, "input primary")
        binding.aliasStrings.forEach { string(it, "input alias") }
        string(binding.labelString, "input label")
    }

    private fun text(
        block: CompiledTextBlock,
        context: OperandValidationContext? = null,
    ) {
        when (block) {
            is CompiledTextBlock.Ref -> handle(block.text, "text ref")
            is CompiledTextBlock.Inline -> block.lineStrings.forEach { string(it, "inline text") }
            is CompiledTextBlock.Structured ->
                block.lines.forEach { line ->
                    line.parts.forEach { part -> textPart(part, context) }
                }
            is CompiledTextBlock.Flow ->
                block.paragraphs.forEach { line ->
                    line.parts.forEach { part -> textPart(part, context) }
                }
        }
    }

    private fun textPart(
        part: CompiledTextPart,
        context: OperandValidationContext?,
    ) {
        when (part) {
            is CompiledTextPart.Literal -> string(part.string, "text literal")
            is CompiledTextPart.IntValue -> intExpr(part.value, context)
            is CompiledTextPart.StringValue -> stringExpr(part.value, context)
            is CompiledTextPart.RoleName -> handle(part.role, "role name")
            is CompiledTextPart.RoleJob -> handle(part.role, "role job")
            is CompiledTextPart.RoleLocation -> handle(part.role, "role location")
            is CompiledTextPart.NpcName -> handle(part.npc, "NPC name")
            is CompiledTextPart.NpcJob -> handle(part.npc, "NPC job")
            is CompiledTextPart.NpcLocation -> handle(part.npc, "NPC location")
            is CompiledTextPart.TownName -> handle(part.town, "town name")
            is CompiledTextPart.FuzzyTownLocation -> handle(part.town, "fuzzy town location")
            is CompiledTextPart.LocationCoordinate -> handle(part.location, "location coordinate")
            is CompiledTextPart.Runtime -> Unit
        }
    }

    private fun intExpr(
        expr: CompiledIntExpr,
        context: OperandValidationContext? = null,
    ) {
        when (expr) {
            is CompiledIntExpr.Local -> string(expr.nameString, "local int name")
            is CompiledIntExpr.Binary -> {
                intExpr(expr.left, context)
                intExpr(expr.right, context)
            }

            is CompiledIntExpr.If -> {
                boolExpr(expr.cond, context)
                intExpr(expr.thenValue, context)
                intExpr(expr.elseValue, context)
            }

            is CompiledIntExpr.Random -> {
                intExpr(expr.lo, context)
                intExpr(expr.hi, context)
            }

            is CompiledIntExpr.MemberField -> {
                intExpr(expr.member, context)
                string(expr.fieldString, "member field")
            }

            is CompiledIntExpr.MonsterField -> {
                intExpr(expr.monster, context)
                string(expr.fieldString, "monster field")
            }

            is CompiledIntExpr.BattleFlag -> string(expr.nameString, "battle flag")
            is CompiledIntExpr.BattleQuery -> string(expr.nameString, "battle query")
            is CompiledIntExpr.PartyMemberQuery -> Unit
            is CompiledIntExpr.RealmScalar -> string(expr.nameString, "realm scalar")
            is CompiledIntExpr.PartyScalar -> string(expr.nameString, "party scalar")
            is CompiledIntExpr.InventoryPoolCount -> handle(expr.pool, "inventory pool count")
            is CompiledIntExpr.InventorySlotCost -> intExpr(expr.slot, context)

            is CompiledIntExpr.Literal -> Unit
        }
    }

    private fun boolExpr(
        expr: CompiledBoolExpr,
        context: OperandValidationContext? = null,
    ) {
        when (expr) {
            is CompiledBoolExpr.Cmp -> {
                intExpr(expr.left, context)
                intExpr(expr.right, context)
            }

            is CompiledBoolExpr.Not -> boolExpr(expr.expr, context)
            is CompiledBoolExpr.And -> {
                boolExpr(expr.left, context)
                boolExpr(expr.right, context)
            }

            is CompiledBoolExpr.Or -> {
                boolExpr(expr.left, context)
                boolExpr(expr.right, context)
            }

            is CompiledBoolExpr.FlagIs -> {
                handle(expr.flag, "flag expr")
                handle(expr.state, "flag state expr")
            }

            is CompiledBoolExpr.MilestoneAchieved -> handle(expr.milestone, "milestone expr")
            is CompiledBoolExpr.MemberFlag -> indexedName(expr.member, expr.nameString, "member flag")
            is CompiledBoolExpr.MonsterFlag -> indexedName(expr.monster, expr.nameString, "monster flag")
            is CompiledBoolExpr.BattleFlag -> string(expr.nameString, "battle bool flag")
            is CompiledBoolExpr.RuntimeFlag -> string(expr.nameString, "runtime flag")
        }
    }

    private fun stringExpr(
        expr: CompiledStringExpr,
        context: OperandValidationContext? = null,
    ) {
        when (expr) {
            is CompiledStringExpr.NativeString -> {
                validateNativeString(expr, context)
                string(expr.pathString, "native string path")
                expr.args.forEach { arg ->
                    when (arg) {
                        is CompiledNativeStringArg.Literal -> string(arg.string, "native string literal arg")
                        is CompiledNativeStringArg.IntValue -> intExpr(arg.value, context)
                    }
                }
            }
        }
    }

    private fun validateNativeString(
        expr: CompiledStringExpr.NativeString,
        context: OperandValidationContext?,
    ) {
        string(expr.pathString, "native string path")
        val path = content.strings.value(expr.pathString)
        val descriptor = nativeServices.descriptor(path)
        if (descriptor == null) {
            if (context == null) {
                require(false) { "native string path $path is not allowlisted" }
            } else {
                fail(
                    VmDiagnostic.MissingNativeOp(
                        root = context.root,
                        instructionIndex = context.instructionIndex,
                        operandPath = context.operandPath,
                        path = path,
                        reason = "UnknownNativeService",
                    ),
                )
            }
            return
        }
        val actualSlots =
            expr.args.map { arg ->
                when (arg) {
                    is CompiledNativeStringArg.Literal -> NativeServiceArgSlot.StringValue
                    is CompiledNativeStringArg.IntValue -> NativeServiceArgSlot.IntExprValue
                }
            }
        if (actualSlots != descriptor.argSlots) {
            if (context == null) {
                require(false) {
                    "native string path $path expects ${descriptor.argSlots}, got $actualSlots"
                }
            } else {
                fail(
                    VmDiagnostic.BadOperand(
                        root = context.root,
                        instructionIndex = context.instructionIndex,
                        operandPath = context.operandPath,
                        opcode = context.opcode,
                        reason = "NativeServiceWrongShape",
                        expected = descriptor.argSlots.toString(),
                        actual = actualSlots.toString(),
                    ),
                )
            }
        }
    }

    private fun lvalue(
        value: CompiledLValue,
        context: OperandValidationContext? = null,
    ) {
        when (value) {
            is CompiledLValue.RealmScalar -> string(value.nameString, "realm lvalue")
            is CompiledLValue.PartyScalar -> string(value.nameString, "party lvalue")
            is CompiledLValue.MemberField -> indexedName(value.member, value.fieldString, "member lvalue", context)
            is CompiledLValue.MonsterField -> indexedName(value.monster, value.fieldString, "monster lvalue", context)
            is CompiledLValue.BattleFlag -> string(value.nameString, "battle flag lvalue")
        }
    }

    private fun view(command: CompiledViewCommand) {
        when (command) {
            is CompiledViewCommand.ShowImage -> handle(command.image, "view image")
            is CompiledViewCommand.PlaySound -> handle(command.sound, "view sound")
            is CompiledViewCommand.PlayMusic -> handle(command.music, "view music")
            else -> Unit
        }
    }

    private fun indexedName(
        index: CompiledIntExpr,
        nameString: Int,
        owner: String,
        context: OperandValidationContext? = null,
    ) {
        intExpr(index, context)
        string(nameString, owner)
    }

    private fun scriptReward(
        reward: CompiledScriptReward,
        context: OperandValidationContext? = null,
    ) {
        reward.gold?.let { intExpr(it, context) }
        reward.items.forEach { handle(it, "script reward item") }
        reward.messages.cantCarry?.let { text(it, context) }
        reward.messages.pickedUp?.let { text(it, context) }
    }

    private fun randomItemOffer(
        offer: CompiledRandomItemOffer,
        context: OperandValidationContext? = null,
    ) {
        handle(offer.pool, "random item offer pool")
        text(offer.offer, context)
        text(offer.choicePrompt, context)
        text(offer.broke, context)
        text(offer.deal, context)
        offer.declined?.let { text(it, context) }
        offer.messages.cantCarry?.let { text(it, context) }
        offer.messages.pickedUp?.let { text(it, context) }
        binding(offer.buy)
        binding(offer.decline)
    }

    private fun shopService(
        service: CompiledShopService,
        context: OperandValidationContext? = null,
    ) {
        handle(service.building, "shop building")
        handle(service.image, "shop image")
        text(service.closed, context)
        text(service.intro, context)
        text(service.menu, context)
        binding(service.keys.buy)
        binding(service.keys.sell)
        binding(service.keys.leave)
        text(service.goodbye, context)
        service.buy?.let { shopBuy(it, context) }
        service.sell?.let { shopSell(it, context) }
        service.interlude?.let { shopInterlude(it, context) }
    }

    private fun shopBuy(
        buy: CompiledShopBuy,
        context: OperandValidationContext?,
    ) {
        text(buy.prompt, context)
        buy.catalogs.forEach { shopCatalog(it, context) }
        text(buy.tooExpensive, context)
        text(buy.cantCarry, context)
        text(buy.deal, context)
        buy.bought?.let { text(it, context) }
        buy.unitPrice?.let { text(it, context) }
        buy.singlePrice?.let { text(it, context) }
        text(buy.payPrompt, context)
        binding(buy.paymentKeys.pay)
        binding(buy.paymentKeys.decline)
    }

    private fun shopCatalog(
        catalog: CompiledShopCatalog,
        context: OperandValidationContext?,
    ) {
        binding(catalog.binding)
        string(catalog.titleString, "shop catalog title")
        catalog.intro?.let { text(it, context) }
        catalog.pages.forEach { page ->
            page.entries.forEach { offer ->
                binding(offer.binding)
                handle(offer.item, "shop catalog item")
                offer.labelString?.let { string(it, "shop item label") }
                offer.description?.let { text(it, context) }
                shopPurchase(offer.purchase, context)
            }
            page.footer?.let { text(it, context) }
            page.navigation.previous?.let(::binding)
            page.navigation.next?.let(::binding)
        }
    }

    private fun shopPurchase(
        purchase: CompiledShopPurchase,
        context: OperandValidationContext?,
    ) {
        if (purchase is CompiledShopPurchase.Bulk) {
            text(purchase.quantityPrompt, context)
            text(purchase.tooMany, context)
        }
    }

    private fun shopSell(
        sell: CompiledShopSell,
        context: OperandValidationContext?,
    ) {
        text(sell.prompt, context)
        sell.acceptedItems.forEach { handle(it, "shop sell accepted item") }
        sell.rejectedItems.forEach { handle(it, "shop sell rejected item") }
        text(sell.wrongKind, context)
        text(sell.offer, context)
        sell.declined?.let { text(it, context) }
        sell.sold?.let { text(it, context) }
        binding(sell.saleKeys.accept)
        binding(sell.saleKeys.decline)
        binding(sell.saleKeys.haggle)
    }

    private fun shopInterlude(
        interlude: CompiledShopInterlude,
        context: OperandValidationContext?,
    ) {
        text(interlude.offer, context)
        binding(interlude.accept)
        binding(interlude.decline)
        interlude.accepted.forEach { text(it, context) }
        text(interlude.declined, context)
        interlude.forceFirstUntil?.let { handle(it, "shop interlude first-offer flag") }
    }

    private fun state(state: CompiledStateImage) {
        handle(state.state, "state")
        realm(state.realm)
        state.locations.forEach { placement -> handle(placement.location, "state location") }
        state.npcs.forEach { placement ->
            handle(placement.npc, "state npc")
            position(placement.position)
        }
        state.parties.forEach(::party)
        when (val mode = state.mode) {
            is CompiledRuntimeModeImage.Dungeon -> {
                handle(mode.session.dungeon, "dungeon mode dungeon")
                mode.session.levelEvents.values
                    .forEach { handle(it, "dungeon mode event") }
                mode.postDungeon?.let { post ->
                    handle(post.mission, "post dungeon mission")
                    handle(post.scene, "post dungeon scene")
                }
            }

            is CompiledRuntimeModeImage.Castle,
            null,
            -> Unit
        }
    }

    private fun realm(realm: CompiledRealmImage) {
        handle(realm.activeParty, "realm active party")
        handle(realm.currentMap, "realm current map")
        realm.currentTown?.let { handle(it, "realm current town") }
        realm.lastTerrain?.let { handle(it, "realm last terrain") }
    }

    private fun party(party: CompiledPartyImage) {
        handle(party.id, "party")
        string(party.playerString, "party player")
        string(party.nameString, "party name")
        position(party.position)
        party.flags.forEach { handle(it, "party flag") }
        party.milestones.forEach { handle(it, "party milestone") }
        party.inventory.forEach { slot -> slot.item?.let { handle(it, "inventory item") } }
        party.members.forEach { member ->
            string(member.nameString, "party member name")
            handle(member.equipment.weapon, "member weapon")
            handle(member.equipment.armor, "member armor")
        }
        party.visitedNpcs.forEach { handle(it.npc, "visited npc") }
        party.journal.forEach { entry ->
            when (entry) {
                is CompiledJournalEntry.Text -> handle(entry.text, "journal text")
                is CompiledJournalEntry.Line -> string(entry.textString, "journal line")
            }
        }
        party.counters.attackedToday.forEach { handle(it.party, "party attack record") }
    }

    private fun position(position: CompiledWorldPosition) {
        if (position is CompiledWorldPosition.Town) {
            handle(position.town, "town position town")
            handle(position.building, "town position building")
        }
    }

    private fun string(
        id: Int,
        owner: String,
    ) {
        require(id in 1..content.strings.entries.size) {
            "$owner references unknown string id $id"
        }
    }

    private fun handle(
        ref: CompiledHandleRef,
        owner: String,
    ) {
        require(ref in handleRefs) {
            "$owner references unknown handle ${ref.kind}#${ref.id}"
        }
    }
}
