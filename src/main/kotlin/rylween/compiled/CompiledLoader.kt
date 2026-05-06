package rylween.compiled

import rylween.runtime.ScriptRootRef

data class LoadedCompiledGame(
    val content: LoadedCompiledContent,
    val states: Map<CompiledHandleRef, CompiledStateImage>,
)

data class LoadedCompiledContent(
    val strings: CompiledStringTable,
    val handles: CompiledHandleTable,
    val stringHashBuckets: Map<Int, List<Int>>,
    val handleHashBuckets: Map<Int, List<CompiledHandleRef>>,
    val scripts: Map<CompiledHandleRef, CompiledScriptProgram>,
) {
    fun string(id: Int): String = strings.value(id)

    fun handle(ref: CompiledHandleRef): CompiledHandle = handles.handle(ref)

    fun resolveHandle(
        kind: CompiledHandleKind,
        slug: String,
    ): CompiledHandleRef? = strings.idOf(slug)?.let { slugString -> handles.refOrNull(kind, slugString) }

    fun script(root: CompiledHandleRef): CompiledScriptProgram? = scripts[root]
}

fun CompiledGameBundle.loadCompiledGame(): LoadedCompiledGame = CompiledBundleLoader.load(this)

object CompiledBundleLoader {
    fun load(bundle: CompiledGameBundle): LoadedCompiledGame {
        CompiledBundleValidator(bundle).validate()
        val content = bundle.content
        return LoadedCompiledGame(
            content =
                LoadedCompiledContent(
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
        content.flags.forEach(::flag)
        content.items.forEach(::item)
        content.itemPools.forEach(::itemPool)
        content.formulas.forEach(::formula)
        content.rewards.forEach(::rewardBundle)
        content.terrain.forEach(::terrain)
        content.worldMaps.forEach(::worldMap)
        content.images.forEach(::image)
        content.staticScreens.forEach(::staticScreen)
        content.screenRegions.forEach { handle(it.id, "screen region") }
        content.towns.forEach(::town)
        content.buildingServices.forEach(::buildingService)
        content.characters.forEach(::character)
        content.monsters.forEach(::monster)
        content.missions.forEach(::mission)
        content.spells.forEach(::spell)
        content.itemUses.forEach(::itemUse)
        content.dungeonEvents.forEach(::dungeonEvent)
        content.dungeonScenarios.forEach(::dungeonScenario)
        content.scripts.roots.forEach(::script)
        bundle.states.forEach(::state)
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
            CompiledCharacterPlacement.Randomized,
            CompiledCharacterPlacement.RandomizedWilderness,
            -> Unit
        }
    }

    private fun monster(monster: CompiledMonster) {
        handle(monster.id, "monster")
        string(monster.nameString, "monster name")
        monster.tier?.let { handle(it, "monster tier") }
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
                operand(root, operandPath + listOf(instructionIndex, operandIndex), operand)
            }
        }
    }

    private fun validateInstruction(
        root: ScriptRootRef,
        instructionIndex: Int,
        operandPath: List<Int>,
        instruction: CompiledInstruction,
    ) {
        val diagnostic =
            when (val result = CompiledOpcodeContract.validateInstruction(instruction)) {
                CompiledOpcodeContract.Validation.Ok -> return
                is CompiledOpcodeContract.Validation.UnknownOpcode ->
                    CompiledInstructionContractDiagnostic.UnknownOpcode(
                        root = root,
                        instructionIndex = instructionIndex,
                        operandPath = operandPath,
                        opcode = result.opcode,
                    )
                is CompiledOpcodeContract.Validation.WrongShape ->
                    CompiledInstructionContractDiagnostic.WrongShape(
                        root = root,
                        instructionIndex = instructionIndex,
                        operandPath = operandPath,
                        opcode = result.opcode,
                        expected = result.expected,
                        actual = result.actual,
                    )
            }
        throw IllegalArgumentException(diagnostic.message())
    }

    private fun operand(
        root: ScriptRootRef,
        operandPath: List<Int>,
        operand: CompiledOperand,
    ) {
        when (operand) {
            is CompiledOperand.StringId -> string(operand.id, "script string")
            is CompiledOperand.StringIdList -> operand.ids.forEach { string(it, "script string list") }
            is CompiledOperand.Handle -> handle(operand.ref, "script handle")
            is CompiledOperand.HandleList -> operand.refs.forEach { handle(it, "script handle list") }
            is CompiledOperand.TextBlockValue -> text(operand.value)
            is CompiledOperand.IntExprValue -> intExpr(operand.value)
            is CompiledOperand.BoolExprValue -> boolExpr(operand.value)
            is CompiledOperand.StringExprValue -> stringExpr(operand.value)
            is CompiledOperand.LValueValue -> lvalue(operand.value)
            is CompiledOperand.ViewCommandValue -> view(operand.value)
            is CompiledOperand.Instructions -> instructions(root, operand.instructions, operandPath)
            is CompiledOperand.SwitchCases ->
                operand.cases.forEachIndexed { caseIndex, case ->
                    instructions(root, case.body, operandPath + caseIndex)
                }
            is CompiledOperand.MenuOptions ->
                operand.options.forEachIndexed { optionIndex, option ->
                    binding(option.binding)
                    instructions(root, option.body, operandPath + optionIndex)
                }
            is CompiledOperand.InputBindings -> operand.bindings.forEach(::binding)
            is CompiledOperand.TextAnswer -> textAnswer(root, operandPath, operand.answer)
            is CompiledOperand.Reward -> scriptReward(operand.reward)
            is CompiledOperand.RandomItemOffer -> randomItemOffer(operand.offer)
            is CompiledOperand.ShopService -> shopService(operand.service)
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

    private fun text(block: CompiledTextBlock) {
        when (block) {
            is CompiledTextBlock.Ref -> handle(block.text, "text ref")
            is CompiledTextBlock.Inline -> block.lineStrings.forEach { string(it, "inline text") }
            is CompiledTextBlock.Structured ->
                block.lines.forEach { line ->
                    line.parts.forEach(::textPart)
                }
        }
    }

    private fun textPart(part: CompiledTextPart) {
        when (part) {
            is CompiledTextPart.Literal -> string(part.string, "text literal")
            is CompiledTextPart.IntValue -> intExpr(part.value)
            is CompiledTextPart.StringValue -> stringExpr(part.value)
            is CompiledTextPart.RoleName -> handle(part.role, "role name")
            is CompiledTextPart.RoleLocation -> handle(part.role, "role location")
            is CompiledTextPart.TownName -> handle(part.town, "town name")
            is CompiledTextPart.FuzzyTownLocation -> handle(part.town, "fuzzy town location")
            is CompiledTextPart.LocationCoordinate -> handle(part.location, "location coordinate")
            is CompiledTextPart.Runtime -> Unit
        }
    }

    private fun intExpr(expr: CompiledIntExpr) {
        when (expr) {
            is CompiledIntExpr.Local -> string(expr.nameString, "local int name")
            is CompiledIntExpr.Binary -> {
                intExpr(expr.left)
                intExpr(expr.right)
            }
            is CompiledIntExpr.If -> {
                boolExpr(expr.cond)
                intExpr(expr.thenValue)
                intExpr(expr.elseValue)
            }
            is CompiledIntExpr.Random -> {
                intExpr(expr.lo)
                intExpr(expr.hi)
            }
            is CompiledIntExpr.MemberField -> {
                intExpr(expr.member)
                string(expr.fieldString, "member field")
            }
            is CompiledIntExpr.MonsterField -> {
                intExpr(expr.monster)
                string(expr.fieldString, "monster field")
            }
            is CompiledIntExpr.BattleFlag -> string(expr.nameString, "battle flag")
            is CompiledIntExpr.BattleQuery -> string(expr.nameString, "battle query")
            is CompiledIntExpr.RealmScalar -> string(expr.nameString, "realm scalar")
            is CompiledIntExpr.PartyScalar -> string(expr.nameString, "party scalar")
            is CompiledIntExpr.InventoryPoolCount -> handle(expr.pool, "inventory pool count")
            is CompiledIntExpr.InventorySlotCost -> intExpr(expr.slot)
            is CompiledIntExpr.NativeInt -> {
                string(expr.pathString, "native int path")
                expr.argStrings.forEach { string(it, "native int arg") }
            }
            is CompiledIntExpr.Literal -> Unit
        }
    }

    private fun boolExpr(expr: CompiledBoolExpr) {
        when (expr) {
            is CompiledBoolExpr.Cmp -> {
                intExpr(expr.left)
                intExpr(expr.right)
            }
            is CompiledBoolExpr.Not -> boolExpr(expr.expr)
            is CompiledBoolExpr.And -> {
                boolExpr(expr.left)
                boolExpr(expr.right)
            }
            is CompiledBoolExpr.Or -> {
                boolExpr(expr.left)
                boolExpr(expr.right)
            }
            is CompiledBoolExpr.FlagIs -> {
                handle(expr.flag, "flag expr")
                handle(expr.state, "flag state expr")
            }
            is CompiledBoolExpr.MilestoneAchieved -> handle(expr.milestone, "milestone expr")
            is CompiledBoolExpr.MemberFlag -> {
                intExpr(expr.member)
                string(expr.nameString, "member flag")
            }
            is CompiledBoolExpr.MonsterFlag -> {
                intExpr(expr.monster)
                string(expr.nameString, "monster flag")
            }
            is CompiledBoolExpr.BattleFlag -> string(expr.nameString, "battle bool flag")
            is CompiledBoolExpr.RuntimeFlag -> string(expr.nameString, "runtime flag")
        }
    }

    private fun stringExpr(expr: CompiledStringExpr) {
        when (expr) {
            is CompiledStringExpr.NativeString -> {
                string(expr.pathString, "native string path")
                expr.args.forEach { arg ->
                    when (arg) {
                        is CompiledNativeStringArg.Literal -> string(arg.string, "native string literal arg")
                        is CompiledNativeStringArg.IntValue -> intExpr(arg.value)
                    }
                }
            }
        }
    }

    private fun lvalue(value: CompiledLValue) {
        when (value) {
            is CompiledLValue.RealmScalar -> string(value.nameString, "realm lvalue")
            is CompiledLValue.PartyScalar -> string(value.nameString, "party lvalue")
            is CompiledLValue.MemberField -> {
                intExpr(value.member)
                string(value.fieldString, "member lvalue")
            }
            is CompiledLValue.MonsterField -> {
                intExpr(value.monster)
                string(value.fieldString, "monster lvalue")
            }
            is CompiledLValue.BattleFlag -> string(value.nameString, "battle flag lvalue")
        }
    }

    private fun view(command: CompiledViewCommand) {
        if (command is CompiledViewCommand.ShowImage) handle(command.image, "view image")
    }

    private fun scriptReward(reward: CompiledScriptReward) {
        reward.gold?.let(::intExpr)
        reward.items.forEach { handle(it, "script reward item") }
        reward.messages.cantCarry?.let(::text)
        reward.messages.pickedUp?.let(::text)
    }

    private fun randomItemOffer(offer: CompiledRandomItemOffer) {
        handle(offer.pool, "random item offer pool")
        text(offer.offer)
        text(offer.choicePrompt)
        text(offer.broke)
        text(offer.deal)
        offer.declined?.let(::text)
        offer.messages.cantCarry?.let(::text)
        offer.messages.pickedUp?.let(::text)
        binding(offer.buy)
        binding(offer.decline)
    }

    private fun shopService(service: CompiledShopService) {
        handle(service.building, "shop building")
        handle(service.image, "shop image")
        text(service.closed)
        text(service.intro)
        text(service.menu)
        binding(service.keys.buy)
        binding(service.keys.sell)
        binding(service.keys.leave)
        text(service.goodbye)
        service.buy?.let(::shopBuy)
        service.sell?.let(::shopSell)
        service.interlude?.let(::shopInterlude)
    }

    private fun shopBuy(buy: CompiledShopBuy) {
        text(buy.prompt)
        buy.catalogs.forEach(::shopCatalog)
        text(buy.tooExpensive)
        text(buy.cantCarry)
        text(buy.deal)
        buy.unitPrice?.let(::text)
        buy.singlePrice?.let(::text)
        text(buy.payPrompt)
        binding(buy.paymentKeys.pay)
        binding(buy.paymentKeys.decline)
    }

    private fun shopCatalog(catalog: CompiledShopCatalog) {
        binding(catalog.binding)
        string(catalog.titleString, "shop catalog title")
        catalog.intro?.let(::text)
        catalog.pages.forEach { page ->
            page.entries.forEach { offer ->
                binding(offer.binding)
                handle(offer.item, "shop catalog item")
                offer.labelString?.let { string(it, "shop item label") }
                offer.description?.let(::text)
                shopPurchase(offer.purchase)
            }
            page.footer?.let(::text)
        }
    }

    private fun shopPurchase(purchase: CompiledShopPurchase) {
        if (purchase is CompiledShopPurchase.Bulk) {
            text(purchase.quantityPrompt)
            text(purchase.tooMany)
        }
    }

    private fun shopSell(sell: CompiledShopSell) {
        text(sell.prompt)
        sell.acceptedItems.forEach { handle(it, "shop sell accepted item") }
        sell.rejectedItems.forEach { handle(it, "shop sell rejected item") }
        text(sell.wrongKind)
        text(sell.offer)
        sell.sold?.let(::text)
        binding(sell.saleKeys.accept)
        binding(sell.saleKeys.decline)
    }

    private fun shopInterlude(interlude: CompiledShopInterlude) {
        text(interlude.offer)
        binding(interlude.accept)
        binding(interlude.decline)
        interlude.accepted.forEach(::text)
        text(interlude.declined)
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
            is CompiledRuntimeModeImage.YdargCastle,
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

private sealed interface CompiledInstructionContractDiagnostic {
    val root: ScriptRootRef
    val instructionIndex: Int
    val operandPath: List<Int>
    val opcode: CompiledOpcode

    fun message(): String

    fun location(): String =
        "root=${root.diagnosticName()} " +
            "instructionIndex=$instructionIndex " +
            "operandPath=${operandPath.diagnosticPath()} " +
            "opcode=$opcode"

    data class UnknownOpcode(
        override val root: ScriptRootRef,
        override val instructionIndex: Int,
        override val operandPath: List<Int>,
        override val opcode: CompiledOpcode,
    ) : CompiledInstructionContractDiagnostic {
        override fun message(): String = "compiled script contract violation: UnknownOpcode ${location()}"
    }

    data class WrongShape(
        override val root: ScriptRootRef,
        override val instructionIndex: Int,
        override val operandPath: List<Int>,
        override val opcode: CompiledOpcode,
        val expected: List<List<CompiledOpcodeContract.Slot>>,
        val actual: List<CompiledOpcodeContract.Slot>,
    ) : CompiledInstructionContractDiagnostic {
        override fun message(): String =
            "compiled script contract violation: WrongShape " +
                "${location()} expected=$expected actual=$actual"
    }
}

private fun ScriptRootRef.diagnosticName(): String =
    if (denseId == null) {
        "${kind.name}:$slug"
    } else {
        "${kind.name}#$denseId:$slug"
    }

private fun List<Int>.diagnosticPath(): String = joinToString(prefix = "[", postfix = "]")
