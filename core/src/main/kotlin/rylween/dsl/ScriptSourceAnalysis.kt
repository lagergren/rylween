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
fun ScriptSourceRoot.anyScriptStatement(predicate: (ScriptStatement) -> Boolean): Boolean =
    buildList { visitScriptStatements { sourceStatement -> add(sourceStatement.statement) } }.any(predicate)

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
                is TextBlock.Inline -> emptyList()
            }
        lines.forEach { line ->
            line.parts.forEach(::collect)
        }
    }

    private fun collect(part: TextPart) {
        when (part) {
            is TextPart.IntValue -> collect(part.value)
            is TextPart.StringValue -> collect(part.value)
            is TextPart.Pause -> Unit
            is TextPart.Literal,
            is TextPart.LineColor,
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

/**
 * Text a script shows that the player never gets to read: before anything holds
 * it on screen (a key press, a pause, or a menu that keeps it above its choices),
 * the script writes a fresh page over it, clears it, or hands the screen to
 * something that draws its own, such as a battle or the runtime's view once the
 * script ends.
 */
data class UnreadScriptText(
    val root: String,
    val text: String,
    val replacedBy: String,
) {
    override fun toString(): String = "$root: \"$text\" is replaced by $replacedBy before it can be read"
}

/** Every [UnreadScriptText] in the game's script roots. */
fun GameModel.unreadScriptTexts(): List<UnreadScriptText> = scriptSourceRoots().flatMap(ScriptSourceRoot::unreadTexts)

/** Every [UnreadScriptText] in this script root. */
fun ScriptSourceRoot.unreadTexts(): List<UnreadScriptText> = UnreadTextScan(label).scan(script.statementNodes)

/**
 * Walks one script tracking the texts on screen that nothing has held yet. Where
 * control can take several paths, the texts left unread by each are merged.
 */
private class UnreadTextScan(
    private val root: String,
) {
    private val findings = linkedSetOf<UnreadScriptText>()
    private val loopExits = ArrayDeque<LoopExit>()
    private val scriptEnd = mutableSetOf<String>()

    private class LoopExit {
        /** What the breaks out of the loop left unread, or null while no break has reached it. */
        var unread: Set<String>? = null
    }

    fun scan(statements: List<ScriptStatementNode>): List<UnreadScriptText> {
        scan(statements, emptySet())?.let(scriptEnd::addAll)
        replace(scriptEnd, "the end of the script")
        return findings.toList()
    }

    /** The texts still unread after [statements], or null when no path falls through them. */
    private fun scan(
        statements: List<ScriptStatementNode>,
        unread: Set<String>,
    ): Set<String>? =
        statements.fold<ScriptStatementNode, Set<String>?>(unread) { current, statement -> current?.let { step(statement, it) } }

    private fun step(
        statement: ScriptStatementNode,
        unread: Set<String>,
    ): Set<String>? =
        when (statement) {
            is ScriptStatementNode.Screen -> {
                val shown = setOfNotNull(statement.text.preview().takeIf(String::isNotBlank))
                when {
                    statement.append -> unread + shown
                    shown.isEmpty() -> replace(unread, "a blank page")
                    else -> replace(unread, "\"${shown.single()}\"") + shown
                }
            }
            is ScriptStatementNode.View ->
                if (statement.command == ViewCommand.ClearText) replace(unread, "clearText()") else unread
            is ScriptStatementNode.WaitInput,
            is ScriptStatementNode.Pause,
            -> emptySet()
            is ScriptStatementNode.Menu -> {
                if (!statement.prompt.listsOnly(statement.options)) replace(unread, "the menu \"${statement.prompt.preview()}\"")
                val defaultPath = if (statement.defaultStatements.isEmpty()) emptySet() else scan(statement.defaultStatements, emptySet())
                merge(statement.options.map { option -> scan(option.body, emptySet()) } + listOf(defaultPath))
            }
            is ScriptStatementNode.AskKey -> replace(unread, "the prompt \"${statement.prompt.preview()}\"")
            is ScriptStatementNode.AskMember -> replace(unread, "the prompt \"${statement.prompt.preview()}\"")
            is ScriptStatementNode.AskInventorySlot -> replace(unread, "the prompt \"${statement.prompt.preview()}\"")
            is ScriptStatementNode.AskMonster -> replace(unread, "the prompt \"${statement.prompt.preview()}\"")
            is ScriptStatementNode.AskText -> {
                replace(unread, "the prompt \"${statement.prompt.preview()}\"")
                merge(listOf(scan(statement.matchStatements, emptySet()), scan(statement.missStatements, emptySet())))
            }
            is ScriptStatementNode.If -> merge(listOf(scan(statement.thenStatements, unread), scan(statement.elseStatements, unread)))
            is ScriptStatementNode.Switch ->
                merge(
                    statement.cases.map { case -> scan(case.body, unread) } +
                        listOf(unread.takeIf { statement.cases.none { case -> case.match == null } }),
                )
            is ScriptStatementNode.Loop -> loop(statement.body, unread)
            is ScriptStatementNode.ForEachMember -> repeated(statement.body, unread)
            is ScriptStatementNode.ForEachMonster -> repeated(statement.body, unread)
            is ScriptStatementNode.Break -> exit(statement.signal, unread)
            is ScriptStatementNode.Stop -> exit(statement.signal, unread)
            is ScriptStatementNode.TellRumor -> unread + "a rumor"
            is ScriptStatementNode.GrantReward ->
                statement.reward.messages.pickedUp
                    ?.takeIf { statement.reward.items.isNotEmpty() }
                    ?.let { message -> replace(unread, "\"${message.preview()}\"") + message.preview() }
                    ?: unread
            is ScriptStatementNode.Speak -> replace(unread, "the conversation menu")
            is ScriptStatementNode.RunScene -> replace(unread, "scene ${statement.scene.slug}")
            is ScriptStatementNode.Encounter -> replace(unread, "the battle")
            is ScriptStatementNode.EnterDungeon,
            is ScriptStatementNode.DiscoverCave,
            -> replace(unread, "the dungeon entrance")
            is ScriptStatementNode.ShopService -> replace(unread, "the shop")
            is ScriptStatementNode.RandomItemOffer -> replace(unread, "the item offer")
            is ScriptStatementNode.LetInt,
            is ScriptStatementNode.BindText,
            is ScriptStatementNode.Set,
            is ScriptStatementNode.Add,
            is ScriptStatementNode.Sub,
            is ScriptStatementNode.AddClamped,
            is ScriptStatementNode.SetFlag,
            is ScriptStatementNode.AppendJournal,
            is ScriptStatementNode.TeleportParty,
            ScriptStatementNode.ConsumeActiveItem,
            is ScriptStatementNode.ClearInventorySlot,
            is ScriptStatementNode.ConsumeInventoryPool,
            is ScriptStatementNode.AchieveMilestone,
            -> unread
        }

    /** Records every text in [unread] as lost to [replacedBy]; nothing is unread afterwards. */
    private fun replace(
        unread: Set<String>,
        replacedBy: String,
    ): Set<String> {
        unread.forEach { text -> findings += UnreadScriptText(root, text, replacedBy) }
        return emptySet()
    }

    /** A script loop leaves only through a break, so what is unread afterwards is what each break left. */
    private fun loop(
        body: List<ScriptStatementNode>,
        unread: Set<String>,
    ): Set<String>? {
        val exit = LoopExit()
        loopExits.addLast(exit)
        // A second pass carries the end of one iteration into the start of the next.
        scan(body, unread)?.let { end -> scan(body, unread + end) }
        loopExits.removeLast()
        return exit.unread
    }

    /** A member or monster iteration runs its body any number of times, including none. */
    private fun repeated(
        body: List<ScriptStatementNode>,
        unread: Set<String>,
    ): Set<String> {
        val once = scan(body, unread)
        val twice = once?.let { end -> scan(body, unread + end) }
        return unread + once.orEmpty() + twice.orEmpty()
    }

    private fun exit(
        signal: ScriptControlSignal,
        unread: Set<String>,
    ): Set<String>? =
        when (signal) {
            ScriptControlSignal.Continue -> unread
            ScriptControlSignal.BreakNearestLoop -> {
                val exit = loopExits.lastOrNull()
                if (exit == null) {
                    scriptEnd += unread
                } else {
                    exit.unread = exit.unread.orEmpty() + unread
                }
                null
            }
            ScriptControlSignal.StopScript -> {
                scriptEnd += unread
                null
            }
        }

    private fun merge(paths: List<Set<String>?>): Set<String>? =
        paths
            .filterNotNull()
            .takeIf(List<Set<String>>::isNotEmpty)
            ?.flatten()
            ?.toSet()
}

/** True when every non-blank line of this menu prompt offers one of [options], so the menu keeps the text above it. */
private fun TextBlock.listsOnly(options: List<MenuOption>): Boolean {
    val lines = plainLines().filter(String::isNotBlank)
    return lines.isNotEmpty() &&
        lines.all { line ->
            options.any { option ->
                val key = option.binding.primary
                "($key)" in line || line.trimStart().startsWith("$key.")
            }
        }
}

private fun TextBlock.plainLines(): List<String> =
    when (this) {
        is TextBlock.Inline -> lines
        is TextBlock.Structured -> lines.map(TextLine::plainText)
        is TextBlock.Flow -> paragraphs.map(TextLine::plainText)
    }

private fun TextLine.plainText(): String =
    parts.joinToString(separator = "") { part ->
        (part as? TextPart.Literal)?.text
            ?: part.fallbackText()
    }

private const val PREVIEW_LENGTH = 48

private fun TextBlock.preview(): String {
    val text = plainLines().joinToString(separator = " ").replace(Regex("\\s+"), " ").trim()
    return if (text.length <= PREVIEW_LENGTH) text else text.take(PREVIEW_LENGTH - 3) + "..."
}
