/**
 * Source-level analysis for authored DSL scripts.
 *
 * The compiled VM contract is `rylween.runtime.vm.CompiledVm`, and compiled
 * bundle emission lives in `rylween.compiled.CompiledBundle`. This file is the
 * remaining authoring/compiler boundary that still understands the source
 * [AuthoredScript] tree before it is lowered into compiled instructions. Keep new
 * validation and source-map probes here, then feed their results to compiler
 * diagnostics instead of teaching runtime hosts how to walk authored source.
 */

package rylween.dsl

import rylween.dsl.impl.QuestionActionScriptModel
import rylween.dsl.impl.RandomItemOfferModel
import rylween.dsl.impl.ShopBuyModel
import rylween.dsl.impl.ShopPurchaseModel
import rylween.dsl.impl.ShopSellModel
import rylween.dsl.impl.ShopServiceModel

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

/** Authored source root plus its recorded source-script tree. */
data class ScriptSourceRoot(
    val id: ScriptSourceRootId,
    val script: AuthoredScript,
    val label: String,
    val initialConditions: List<BoolAst> = emptyList(),
) {
    constructor(
        id: ScriptSourceRootId,
        statementNodes: List<ScriptStatementNode>,
        label: String,
        initialConditions: List<BoolAst> = emptyList(),
    ) : this(
        id = id,
        script = AuthoredScript(statementNodes),
        label = label,
        initialConditions = initialConditions,
    )

    val statementNodes: List<ScriptStatementNode> get() = script.statementNodes
}

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
                    script = scene.script,
                    label = "scene ${scene.id.slug}",
                ),
            )
        }
        dungeons.events.forEach { event ->
            add(
                ScriptSourceRoot(
                    id = ScriptSourceRootId(ScriptSourceRootKind.DungeonEvent, event.id.slug),
                    script = event.script,
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
                        script = branch.script,
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
                    script = spell.script,
                    label = "spell ${spell.id.slug}",
                ),
            )
        }
        itemUses.forEach { itemUse ->
            add(
                ScriptSourceRoot(
                    id = ScriptSourceRootId(ScriptSourceRootKind.ItemUse, itemUse.id.slug),
                    script = itemUse.script,
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
                    script = action.script,
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

    roots.forEach { root -> nativePaths.collect(root.script) }
    formulas.forEach { formula -> nativePaths.collect(formula.condition) }

    return ScriptSourceAnalysis(
        roots = roots,
        formulas = formulas,
        nativePaths = nativePaths.paths(),
    )
}

/**
 * One authored source statement with source-map style context produced by
 * [visitScriptStatements].
 *
 * [path] is intentionally numeric so it can line up with VM diagnostics and
 * future compiled source maps. Branch slots use deterministic indexes inside
 * their parent statement before the nested statement index is appended.
 */
data class ScriptSourceStatement(
    val root: ScriptSourceRootId?,
    val statement: ScriptStatement,
    val label: String,
    val path: List<Int>,
    val loopDepth: Int,
    val branchConditions: List<BoolAst>,
)

/** Walks every source statement in this source root with branch labels and condition context. */
fun ScriptSourceRoot.visitScriptStatements(visitor: (ScriptSourceStatement) -> Unit) {
    visitScriptStatements(
        script = script,
        label = label,
        initialConditions = initialConditions,
        root = id,
        visitor = visitor,
    )
}

/** Returns true when any source statement in this source root matches [predicate]. */
fun ScriptSourceRoot.anyScriptStatement(predicate: (ScriptStatement) -> Boolean): Boolean {
    var found = false
    visitScriptStatements { sourceStatement ->
        if (!found && predicate(sourceStatement.statement)) {
            found = true
        }
    }
    return found
}

/** Walks an authored script using the shared traversal that feeds validation and source maps. */
fun visitScriptStatements(
    script: AuthoredScript,
    label: String,
    initialConditions: List<BoolAst> = emptyList(),
    root: ScriptSourceRootId? = null,
    visitor: (ScriptSourceStatement) -> Unit,
) {
    visitScriptStatements(
        statements = script.block.statements,
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

private fun visitScriptStatements(
    statements: List<ScriptStatement>,
    label: String,
    root: ScriptSourceRootId?,
    path: List<Int>,
    loopDepth: Int,
    branchConditions: List<BoolAst>,
    visitor: (ScriptSourceStatement) -> Unit,
) {
    statements.forEachIndexed { index, statement ->
        val statementPath = path + index
        visitor(
            ScriptSourceStatement(
                root = root,
                statement = statement,
                label = label,
                path = statementPath,
                loopDepth = loopDepth,
                branchConditions = branchConditions,
            ),
        )
        visitNestedScriptStatements(
            statement = statement,
            label = label,
            root = root,
            path = statementPath,
            loopDepth = loopDepth,
            branchConditions = branchConditions,
            visitor = visitor,
        )
    }
}

private fun visitNestedScriptStatements(
    statement: ScriptStatement,
    label: String,
    root: ScriptSourceRootId?,
    path: List<Int>,
    loopDepth: Int,
    branchConditions: List<BoolAst>,
    visitor: (ScriptSourceStatement) -> Unit,
) {
    fun visitBranch(
        branchStatements: List<ScriptStatementNode>,
        branchIndex: Int,
        branchLabel: String,
        nestedLoopDepth: Int = loopDepth,
        nestedConditions: List<BoolAst> = branchConditions,
    ) {
        visitScriptStatements(
            statements = branchStatements,
            label = branchLabel,
            root = root,
            path = path + branchIndex,
            loopDepth = nestedLoopDepth,
            branchConditions = nestedConditions,
            visitor = visitor,
        )
    }

    when (val op = statement) {
        is ScriptStatementNode.If -> {
            visitBranch(op.thenStatements, 0, "$label if-then", nestedConditions = branchConditions + op.cond)
            visitBranch(op.elseStatements, 1, "$label if-else")
        }
        is ScriptStatementNode.Loop -> visitBranch(op.body, 0, "$label loop", nestedLoopDepth = loopDepth + 1)
        is ScriptStatementNode.Switch ->
            op.cases.forEachIndexed { index, case ->
                visitBranch(case.body, index, "$label switch case ${index + 1}")
            }
        is ScriptStatementNode.Menu -> {
            op.options.forEachIndexed { index, option ->
                visitBranch(option.body, index, "$label menu ${option.key}")
            }
            visitBranch(op.defaultStatements, op.options.size, "$label menu default")
        }
        is ScriptStatementNode.AskText -> {
            visitBranch(op.matchStatements, 0, "$label text-input accepted")
            visitBranch(op.missStatements, 1, "$label text-input rejected")
        }
        is ScriptStatementNode.ForEachMember -> visitBranch(op.body, 0, "$label member loop")
        is ScriptStatementNode.ForEachMonster -> visitBranch(op.body, 0, "$label monster loop")
        else -> Unit
    }
}

private class NativePathCollector {
    private val paths = linkedSetOf<String>()

    fun paths(): Set<String> = paths.toSet()

    fun collect(script: AuthoredScript) {
        collect(script.block.statements)
    }

    private fun collect(statements: List<ScriptStatement>) {
        statements.forEach(::collect)
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
            is IntAst.PartyMemberQuery -> Unit
            is IntAst.RealmScalar -> Unit
            is IntAst.PartyScalar -> Unit
            is IntAst.InventoryPoolCount -> Unit
            is IntAst.InventorySlotCost -> collect(ast.slot)
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
        val lines =
            when (block) {
                is TextBlock.Structured -> block.lines
                is TextBlock.Flow -> block.paragraphs
                is TextBlock.Ref,
                is TextBlock.Inline,
                -> emptyList()
            }
        lines.forEach { line ->
            line.parts.forEach(::collect)
        }
    }

    private fun collect(part: TextPart) {
        when (part) {
            is TextPart.IntValue -> collect(part.value)
            is TextPart.StringValue -> collect(part.value)
            is TextPart.Literal,
            is TextPart.RoleName,
            is TextPart.RoleJob,
            is TextPart.RoleLocation,
            is TextPart.NpcName,
            is TextPart.NpcJob,
            is TextPart.NpcLocation,
            is TextPart.TownName,
            is TextPart.FuzzyTownLocation,
            is TextPart.LocationCoordinate,
            TextPart.OfferedItemName,
            TextPart.OfferedItemPrice,
            TextPart.SelectedItemName,
            TextPart.InventorySlotName,
            TextPart.EquipmentKindName,
            TextPart.ShopPrice,
            TextPart.ShopCarryCap,
            TextPart.HuntMemberName,
            TextPart.HuntMemberSubjectPronoun,
            TextPart.HuntFoodGained,
            TextPart.UpkeepMemberName,
            TextPart.CampGuardName,
            TextPart.PartyMemberCount,
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
            TextPart.CreationDexterity,
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

    private fun collect(statement: ScriptStatement) {
        when (val op = statement) {
            is ScriptStatementNode.Screen -> collect(op.text)
            is ScriptStatementNode.Emit -> Unit
            is ScriptStatementNode.WaitInput -> Unit
            is ScriptStatementNode.Pause -> Unit
            is ScriptStatementNode.View -> Unit
            is ScriptStatementNode.LetInt -> collect(op.value)
            is ScriptStatementNode.BindText -> collect(op.value)
            is ScriptStatementNode.Set -> collect(op.value)
            is ScriptStatementNode.Add -> collect(op.value)
            is ScriptStatementNode.Sub -> collect(op.value)
            is ScriptStatementNode.AddClamped -> {
                collect(op.value)
                collect(op.max)
            }
            is ScriptStatementNode.SetFlag -> Unit
            is ScriptStatementNode.AppendJournal -> collect(op.text)
            is ScriptStatementNode.If -> {
                collect(op.cond)
                collect(op.thenStatements)
                collect(op.elseStatements)
            }
            is ScriptStatementNode.Loop -> collect(op.body)
            is ScriptStatementNode.Break -> Unit
            is ScriptStatementNode.Stop -> Unit
            is ScriptStatementNode.Switch -> {
                collect(op.expr)
                op.cases.forEach { collect(it.body) }
            }
            is ScriptStatementNode.Menu -> {
                collect(op.prompt)
                op.options.forEach { collect(it.body) }
                collect(op.defaultStatements)
            }
            is ScriptStatementNode.AskKey -> collect(op.prompt)
            is ScriptStatementNode.AskMember -> collect(op.prompt)
            is ScriptStatementNode.AskInventorySlot -> collect(op.prompt)
            is ScriptStatementNode.AskText -> {
                collect(op.prompt)
                collect(op.matchStatements)
                collect(op.missStatements)
            }
            is ScriptStatementNode.AskMonster -> collect(op.prompt)
            is ScriptStatementNode.ForEachMember -> collect(op.body)
            is ScriptStatementNode.ForEachMonster -> collect(op.body)
            is ScriptStatementNode.Speak -> Unit
            is ScriptStatementNode.TellRumor -> Unit
            is ScriptStatementNode.RunScene -> Unit
            is ScriptStatementNode.Encounter -> Unit
            is ScriptStatementNode.EnterDungeon -> Unit
            ScriptStatementNode.ConsumeActiveItem -> Unit
            is ScriptStatementNode.ClearInventorySlot -> collect(op.slot)
            is ScriptStatementNode.ConsumeInventoryPool -> collect(op.quantity)
            is ScriptStatementNode.TeleportParty -> {
                collect(op.directionKey)
                collect(op.distance)
            }
            is ScriptStatementNode.DiscoverCave -> {
                collect(op.levels)
                collect(op.moveCost)
            }
            is ScriptStatementNode.GrantReward -> op.reward.gold?.let(::collect)
            is ScriptStatementNode.RandomItemOffer -> collect(op.offer)
            is ScriptStatementNode.ShopService -> collect(op.shop)
            is ScriptStatementNode.AchieveMilestone -> Unit
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
        sell.declined?.let(::collect)
        sell.sold?.let(::collect)
    }
}
