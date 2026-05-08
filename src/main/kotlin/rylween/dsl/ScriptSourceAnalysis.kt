/**
 * Source-level analysis for authored DSL scripts.
 *
 * The compiled VM contract is `rylween.runtime.vm.CompiledVm`, and compiled
 * bundle emission lives in `rylween.compiled.CompiledBundle`. This file is the
 * remaining authoring/compiler boundary that still understands the source
 * [Op] tree before it is lowered into compiled instructions. Keep new
 * validation and source-map probes here, then feed their results to compiler
 * diagnostics instead of teaching runtime hosts how to walk authored ops.
 */

package rylween.dsl

/** Kinds of script roots that can be authored in the Kotlin DSL. */
enum class ScriptSourceRootKind {
    /** A named scene script. */
    Scene,

    /** A dungeon event script. */
    DungeonEvent,

    /** One branch in an NPC dialogue script. */
    DialogueBranch,

    /** A spell effect script. */
    Spell,

    /** An item-use effect script. */
    ItemUse,

    /** A scripted response to a standard conversation question. */
    QuestionAction,
}

/** Stable source id for an authored script root before bundle compilation assigns dense ids. */
data class ScriptSourceRootId(
    val kind: ScriptSourceRootKind,
    val slug: String,
)

/** Authored source root plus its recorded [Op] tree. */
data class ScriptSourceRoot(
    val id: ScriptSourceRootId,
    val ops: List<Op>,
    val label: String,
    val initialConditions: List<BoolAst> = emptyList(),
)

/** Kinds of non-script formulas that can still contain native service probes. */
enum class ScriptSourceFormulaKind {
    /** A mission availability condition. */
    MissionRequirement,
}

/** Stable source id for an authored formula outside a script root. */
data class ScriptSourceFormulaId(
    val kind: ScriptSourceFormulaKind,
    val slug: String,
)

/** Authored formula that participates in script/source validation. */
data class ScriptSourceFormula(
    val id: ScriptSourceFormulaId,
    val condition: BoolAst,
)

/** Aggregate facts collected from authored script and formula sources. */
data class ScriptSourceAnalysis(
    val roots: List<ScriptSourceRoot>,
    val formulas: List<ScriptSourceFormula>,
    val nativePaths: Set<String>,
)

/** Lists every authored script root in deterministic compiler/source-map order. */
fun GameModel.scriptSourceRoots(): List<ScriptSourceRoot> =
    buildList {
        scenes.forEach { scene ->
            add(
                ScriptSourceRoot(
                    id = ScriptSourceRootId(ScriptSourceRootKind.Scene, scene.id.slug),
                    ops = scene.ops,
                    label = "scene ${scene.id.slug}",
                ),
            )
        }
        dungeons.events.forEach { event ->
            add(
                ScriptSourceRoot(
                    id = ScriptSourceRootId(ScriptSourceRootKind.DungeonEvent, event.id.slug),
                    ops = event.ops,
                    label = "dungeon event ${event.id.slug}",
                ),
            )
        }
        dialogue.forEach { dialogue ->
            dialogue.branches.forEach { branch ->
                add(
                    ScriptSourceRoot(
                        id =
                            ScriptSourceRootId(
                                ScriptSourceRootKind.DialogueBranch,
                                dialogueBranchSlug(dialogue, branch),
                            ),
                        ops = branch.ops,
                        label = dialogueBranchLabel(dialogue, branch),
                        initialConditions = branch.whenState?.let { listOf(it.ast) }.orEmpty(),
                    ),
                )
            }
        }
        spells.forEach { spell ->
            add(
                ScriptSourceRoot(
                    id = ScriptSourceRootId(ScriptSourceRootKind.Spell, spell.id.slug),
                    ops = spell.ops,
                    label = "spell ${spell.id.slug}",
                ),
            )
        }
        itemUses.forEach { itemUse ->
            add(
                ScriptSourceRoot(
                    id = ScriptSourceRootId(ScriptSourceRootKind.ItemUse, itemUse.id.slug),
                    ops = itemUse.ops,
                    label = "item use ${itemUse.id.slug}",
                ),
            )
        }
        speak.questionActions.forEach { action ->
            add(
                ScriptSourceRoot(
                    id =
                        ScriptSourceRootId(
                            ScriptSourceRootKind.QuestionAction,
                            questionActionSlug(action),
                        ),
                    ops = action.ops,
                    label = "speak ${action.topic} ${action.job.slug}",
                ),
            )
        }
    }

/** Lists authored formulas that are not script roots but still need source diagnostics. */
fun GameModel.scriptSourceFormulas(): List<ScriptSourceFormula> =
    missions.mapNotNull { mission ->
        mission.requires?.let { requirement ->
            ScriptSourceFormula(
                id =
                    ScriptSourceFormulaId(
                        ScriptSourceFormulaKind.MissionRequirement,
                        mission.id.slug,
                    ),
                condition = requirement,
            )
        }
    }

/** Builds source-analysis facts used by validation, native catalogs, and compiled source maps. */
fun analyzeScriptSources(model: GameModel): ScriptSourceAnalysis {
    val roots = model.scriptSourceRoots()
    val formulas = model.scriptSourceFormulas()
    val nativePaths = NativePathCollector()

    roots.forEach { root -> nativePaths.collect(root.ops) }
    formulas.forEach { formula -> nativePaths.collect(formula.condition) }

    return ScriptSourceAnalysis(
        roots = roots,
        formulas = formulas,
        nativePaths = nativePaths.paths(),
    )
}

/**
 * Returns native-service paths referenced by authored script sources.
 *
 * Prefer [analyzeScriptSources] for new code so diagnostics can retain the
 * source root/formula that produced a path. This compatibility helper exists
 * while older tests and validation code are folded into the source-analysis
 * boundary.
 */
fun nativePathsIn(model: GameModel): Set<String> = analyzeScriptSources(model).nativePaths

/**
 * One authored operation with source-map style context produced by [visitScriptOps].
 *
 * [path] is intentionally numeric so it can line up with VM diagnostics and
 * future compiled source maps. Branch slots use deterministic indexes inside
 * their parent operation before the nested operation index is appended.
 */
data class ScriptSourceOp(
    val root: ScriptSourceRootId?,
    val op: Op,
    val label: String,
    val path: List<Int>,
    val loopDepth: Int,
    val branchConditions: List<BoolAst>,
)

/** Walks every operation in this source root with branch labels and condition context. */
fun ScriptSourceRoot.visitScriptOps(visitor: (ScriptSourceOp) -> Unit) {
    visitScriptOps(
        ops = ops,
        label = label,
        initialConditions = initialConditions,
        root = id,
        visitor = visitor,
    )
}

/** Returns true when any operation in this source root matches [predicate]. */
fun ScriptSourceRoot.anyScriptOp(predicate: (Op) -> Boolean): Boolean {
    var found = false
    visitScriptOps { sourceOp ->
        if (!found && predicate(sourceOp.op)) {
            found = true
        }
    }
    return found
}

/** Walks an authored op tree using the shared traversal that feeds validation and source maps. */
fun visitScriptOps(
    ops: List<Op>,
    label: String,
    initialConditions: List<BoolAst> = emptyList(),
    root: ScriptSourceRootId? = null,
    visitor: (ScriptSourceOp) -> Unit,
) {
    visitScriptOps(
        ops = ops,
        label = label,
        root = root,
        path = emptyList(),
        loopDepth = 0,
        branchConditions = initialConditions,
        visitor = visitor,
    )
}

private fun dialogueBranchSlug(
    dialogue: DialogueModel,
    branch: DialogueBranchModel,
): String =
    listOf(
        "dialogue",
        dialogue.npc.slug,
        branch.whenState?.let { state -> "${state.flag.slug}.${state.native}" } ?: "default",
    ).joinToString(".")

private fun dialogueBranchLabel(
    dialogue: DialogueModel,
    branch: DialogueBranchModel,
): String =
    branch.whenState
        ?.let { "dialogue ${dialogue.npc.slug} when ${it.flag.slug}.${it.native}" }
        ?: "dialogue ${dialogue.npc.slug} default"

private fun questionActionSlug(action: QuestionActionScriptModel): String = "question.${action.topic.name.lowercase()}.${action.job.slug}"

private fun visitScriptOps(
    ops: List<Op>,
    label: String,
    root: ScriptSourceRootId?,
    path: List<Int>,
    loopDepth: Int,
    branchConditions: List<BoolAst>,
    visitor: (ScriptSourceOp) -> Unit,
) {
    ops.forEachIndexed { index, op ->
        val opPath = path + index
        visitor(
            ScriptSourceOp(
                root = root,
                op = op,
                label = label,
                path = opPath,
                loopDepth = loopDepth,
                branchConditions = branchConditions,
            ),
        )
        visitNestedScriptOps(
            op = op,
            label = label,
            root = root,
            path = opPath,
            loopDepth = loopDepth,
            branchConditions = branchConditions,
            visitor = visitor,
        )
    }
}

private fun visitNestedScriptOps(
    op: Op,
    label: String,
    root: ScriptSourceRootId?,
    path: List<Int>,
    loopDepth: Int,
    branchConditions: List<BoolAst>,
    visitor: (ScriptSourceOp) -> Unit,
) {
    fun visitBranch(
        branchOps: List<Op>,
        branchIndex: Int,
        branchLabel: String,
        nestedLoopDepth: Int = loopDepth,
        nestedConditions: List<BoolAst> = branchConditions,
    ) {
        visitScriptOps(
            ops = branchOps,
            label = branchLabel,
            root = root,
            path = path + branchIndex,
            loopDepth = nestedLoopDepth,
            branchConditions = nestedConditions,
            visitor = visitor,
        )
    }

    when (op) {
        is Op.If -> {
            visitBranch(op.thenOps, 0, "$label if-then", nestedConditions = branchConditions + op.cond)
            visitBranch(op.elseOps, 1, "$label if-else")
        }
        is Op.Loop -> visitBranch(op.body, 0, "$label loop", nestedLoopDepth = loopDepth + 1)
        is Op.Switch ->
            op.cases.forEachIndexed { index, case ->
                visitBranch(case.body, index, "$label switch case ${index + 1}")
            }
        is Op.Menu -> {
            op.options.forEachIndexed { index, option ->
                visitBranch(option.body, index, "$label menu ${option.key}")
            }
            visitBranch(op.defaultOps, op.options.size, "$label menu default")
        }
        is Op.AskText -> {
            visitBranch(op.matchOps, 0, "$label text-answer match")
            visitBranch(op.missOps, 1, "$label text-answer miss")
        }
        is Op.ForEachMember -> visitBranch(op.body, 0, "$label member loop")
        is Op.ForEachMonster -> visitBranch(op.body, 0, "$label monster loop")
        else -> Unit
    }
}

private class NativePathCollector {
    private val paths = linkedSetOf<String>()

    fun paths(): Set<String> = paths.toSet()

    fun collect(items: List<Op>) {
        items.forEach(::collect)
    }

    fun collect(ast: BoolAst) {
        when (ast) {
            is BoolAst.Cmp -> {
                collect(ast.left)
                collect(ast.right)
            }
            is BoolAst.Not -> collect(ast.expr)
            is BoolAst.And -> {
                collect(ast.left)
                collect(ast.right)
            }
            is BoolAst.Or -> {
                collect(ast.left)
                collect(ast.right)
            }
            is BoolAst.FlagIs -> Unit
            is BoolAst.MilestoneAchieved -> Unit
            is BoolAst.MemberFlag -> collect(ast.member)
            is BoolAst.MonsterFlag -> collect(ast.monster)
            is BoolAst.BattleFlag -> Unit
            is BoolAst.RuntimeFlag -> Unit
        }
    }

    private fun collect(ast: IntAst) {
        when (ast) {
            is IntAst.Lit -> Unit
            is IntAst.Var -> Unit
            is IntAst.Binary -> {
                collect(ast.left)
                collect(ast.right)
            }
            is IntAst.If -> {
                collect(ast.cond)
                collect(ast.thenValue)
                collect(ast.elseValue)
            }
            is IntAst.Random -> {
                collect(ast.lo)
                collect(ast.hi)
            }
            is IntAst.MemberField -> collect(ast.member)
            is IntAst.MonsterField -> collect(ast.monster)
            is IntAst.BattleFlag -> Unit
            is IntAst.BattleQuery -> Unit
            is IntAst.RealmScalar -> Unit
            is IntAst.PartyScalar -> Unit
            is IntAst.InventoryPoolCount -> Unit
            is IntAst.InventorySlotCost -> collect(ast.slot)
            is IntAst.NativeInt -> paths += ast.path
        }
    }

    private fun collect(ast: StrAst) {
        when (ast) {
            is StrAst.NativeString -> {
                paths += ast.path
                ast.args.forEach { arg ->
                    when (arg) {
                        is NativeStringArg.IntValue -> collect(arg.value)
                        is NativeStringArg.Literal -> Unit
                    }
                }
            }
        }
    }

    private fun collect(block: TextBlock) {
        if (block is TextBlock.Structured) {
            block.lines.forEach { line ->
                line.parts.forEach(::collect)
            }
        }
    }

    private fun collect(part: TextPart) {
        when (part) {
            is TextPart.IntValue -> collect(part.value)
            is TextPart.StringValue -> collect(part.value)
            is TextPart.Literal,
            is TextPart.RoleName,
            is TextPart.RoleLocation,
            is TextPart.TownName,
            is TextPart.FuzzyTownLocation,
            is TextPart.LocationCoordinate,
            TextPart.OfferedItemName,
            TextPart.OfferedItemPrice,
            TextPart.SelectedItemName,
            TextPart.ShopPrice,
            TextPart.ShopCarryCap,
            TextPart.HuntMemberName,
            TextPart.HuntMemberSubjectPronoun,
            TextPart.HuntFoodGained,
            TextPart.PartyMemberMax,
            TextPart.WildernessDungeonName,
            TextPart.WildernessDungeonLevels,
            TextPart.DungeonDepth,
            TextPart.ConversationNpcName,
            TextPart.CurrentTownName,
            TextPart.TownTaxPercent,
            TextPart.TownTaxAmount,
            TextPart.CreationMemberNumber,
            TextPart.CreationMemberName,
            TextPart.CreationWeaponSkill,
            TextPart.CreationHealth,
            TextPart.CreationWisdom,
            TextPart.CreationCharisma,
            TextPart.BattleFoeDisplayName,
            TextPart.BattleAttackerName,
            TextPart.BattleTargetName,
            TextPart.BattleDamage,
            TextPart.BattleExperience,
            TextPart.BattleGold,
            TextPart.BattleLootName,
            TextPart.BattleLevelMemberName,
            TextPart.BattleLevelsGained,
            -> Unit
        }
    }

    private fun collect(op: Op) {
        when (op) {
            is Op.Screen -> collect(op.text)
            is Op.Emit -> Unit
            is Op.EmitIndexed -> collect(op.value)
            is Op.WaitInput -> Unit
            is Op.Pause -> Unit
            is Op.View -> Unit
            is Op.LetInt -> collect(op.value)
            is Op.BindText -> collect(op.value)
            is Op.Set -> collect(op.value)
            is Op.Add -> collect(op.value)
            is Op.Sub -> collect(op.value)
            is Op.AddClamped -> {
                collect(op.value)
                collect(op.max)
            }
            is Op.SetFlag -> Unit
            is Op.AppendJournal -> collect(op.text)
            is Op.If -> {
                collect(op.cond)
                collect(op.thenOps)
                collect(op.elseOps)
            }
            is Op.Loop -> collect(op.body)
            is Op.Break -> Unit
            is Op.Stop -> Unit
            is Op.Switch -> {
                collect(op.expr)
                op.cases.forEach { collect(it.body) }
            }
            is Op.Menu -> {
                collect(op.prompt)
                op.options.forEach { collect(it.body) }
                collect(op.defaultOps)
            }
            is Op.AskKey -> collect(op.prompt)
            is Op.AskMember -> collect(op.prompt)
            is Op.AskInventorySlot -> collect(op.prompt)
            is Op.AskText -> {
                collect(op.prompt)
                collect(op.matchOps)
                collect(op.missOps)
            }
            is Op.AskMonster -> collect(op.prompt)
            is Op.ForEachMember -> collect(op.body)
            is Op.ForEachMonster -> collect(op.body)
            is Op.Speak -> Unit
            is Op.TellRumour -> Unit
            is Op.RunScene -> Unit
            is Op.Encounter -> Unit
            is Op.EnterDungeon -> Unit
            Op.ConsumeActiveItem -> Unit
            is Op.ClearInventorySlot -> collect(op.slot)
            is Op.ConsumeInventoryPool -> collect(op.quantity)
            Op.ConsumeTorch -> Unit
            is Op.TeleportParty -> {
                collect(op.directionKey)
                collect(op.distance)
            }
            is Op.DiscoverCave -> {
                collect(op.levels)
                collect(op.moveCost)
            }
            is Op.GrantReward -> op.reward.gold?.let(::collect)
            is Op.RandomItemOffer -> collect(op.offer)
            is Op.ShopService -> collect(op.shop)
            is Op.AchieveMilestone -> Unit
            is Op.NativeCall -> paths += op.path
        }
    }

    private fun collect(offer: RandomItemOfferModel) {
        collect(offer.offer)
        collect(offer.choicePrompt)
        collect(offer.broke)
        offer.messages.cantCarry?.let(::collect)
        offer.messages.pickedUp?.let(::collect)
        collect(offer.deal)
        offer.declined?.let(::collect)
    }

    private fun collect(shop: ShopServiceModel) {
        collect(shop.closed)
        collect(shop.intro)
        collect(shop.menu)
        collect(shop.goodbye)
        shop.interlude?.let { interlude ->
            collect(interlude.offer)
            interlude.accepted.forEach(::collect)
            collect(interlude.declined)
        }
        shop.buy?.let(::collect)
        shop.sell?.let(::collect)
    }

    private fun collect(buy: ShopBuyModel) {
        collect(buy.prompt)
        collect(buy.tooExpensive)
        collect(buy.cantCarry)
        collect(buy.deal)
        buy.unitPrice?.let(::collect)
        buy.singlePrice?.let(::collect)
        collect(buy.payPrompt)
        buy.catalogs.forEach { catalog ->
            catalog.intro?.let(::collect)
            catalog.pages.forEach { page ->
                page.footer?.let(::collect)
                page.entries.forEach { entry ->
                    entry.description?.let(::collect)
                    (entry.purchase as? ShopPurchaseModel.Bulk)?.let { resource ->
                        collect(resource.quantityPrompt)
                        collect(resource.tooMany)
                    }
                }
            }
        }
    }

    private fun collect(sell: ShopSellModel) {
        collect(sell.prompt)
        collect(sell.wrongKind)
        collect(sell.offer)
        sell.sold?.let(::collect)
    }
}
