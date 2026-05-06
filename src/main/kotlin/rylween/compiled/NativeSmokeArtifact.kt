/**
 * Narrow binary smoke artifact for native compiled-boundary checks.
 *
 * This is not the product J1 bundle; `ExternalBundleArtifact.kt` owns that JSON
 * format. The smoke artifact gives the small C reader enough typed strings,
 * handles, opcodes, operands, and states to catch boundary/schema drift during
 * `checkNativeSmokeBundle`.
 */
package rylween.compiled

import rylween.dsl.BoolExpr
import rylween.dsl.IntExpr
import rylween.dsl.RewardApplicationModel
import rylween.dsl.ShopQuantityCap
import rylween.dsl.ShopSellBargainModel
import rylween.dsl.TextBlock
import rylween.dsl.ViewCommand
import rylween.dsl.building
import rylween.dsl.image
import rylween.dsl.item
import rylween.dsl.line
import rylween.dsl.lines
import rylween.dsl.location
import rylween.dsl.milestone
import rylween.dsl.monster
import rylween.dsl.text
import rylween.dsl.town
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.enums.enumEntries

private const val NATIVE_SMOKE_MAGIC = "RYLSMOKE"
private const val NATIVE_SMOKE_VERSION = 1
private const val NATIVE_SMOKE_FALSE = 0
private const val NATIVE_SMOKE_TRUE = 1

/** Serializes a compiled bundle into the narrow native smoke-test artifact. */
fun CompiledGameBundle.toNativeSmokeArtifact(): ByteArray = NativeSmokeArtifactWriter().write(this)

/** Writes a compiled bundle as the native smoke-test artifact. */
fun CompiledGameBundle.writeNativeSmokeArtifact(file: File) {
    file.parentFile?.mkdirs()
    file.writeBytes(toNativeSmokeArtifact())
}

/** Reads the summary from native smoke-test artifact bytes. */
fun ByteArray.readNativeSmokeArtifact(): NativeSmokeArtifactSummary = NativeSmokeArtifactReader(this).read()

/** Reads the summary from a native smoke-test artifact file. */
fun File.readNativeSmokeArtifact(): NativeSmokeArtifactSummary = readBytes().readNativeSmokeArtifact()

/** Summary counts extracted by the native smoke-test artifact reader. */
data class NativeSmokeArtifactSummary(
    val version: Int,
    val stringCount: Int,
    val handleCount: Int,
    val scriptCount: Int,
    val stateCount: Int,
    val instructionCount: Long,
    val operandCount: Long,
)

private interface NativeSmokeTag {
    val code: Int
}

private enum class NativeSmokeOperandTag(
    override val code: Int,
) : NativeSmokeTag {
    Integer(0),
    BooleanValue(1),
    Character(2),
    StringId(3),
    StringIdList(4),
    Handle(5),
    HandleList(6),
    TextBlock(7),
    IntExpr(8),
    BoolExpr(9),
    StringExpr(10),
    LValue(11),
    ViewCommand(12),
    Instructions(13),
    SwitchCases(14),
    MenuOptions(15),
    InputBindings(16),
    TextAnswer(17),
    Reward(18),
    RandomItemOffer(19),
    ShopService(20),
    EnumName(21),
}

private enum class NativeSmokeTextBlockTag(
    override val code: Int,
) : NativeSmokeTag {
    Ref(0),
    Inline(1),
    Structured(2),
}

private enum class NativeSmokeTextPartTag(
    override val code: Int,
) : NativeSmokeTag {
    Literal(0),
    IntValue(1),
    StringValue(2),
    RoleName(3),
    RoleLocation(4),
    TownName(5),
    FuzzyTownLocation(6),
    LocationCoordinate(7),
    Runtime(8),
}

private enum class NativeSmokeViewCommandTag(
    override val code: Int,
) : NativeSmokeTag {
    ClearText(0),
    ClearMapWindow(1),
    RenderMap(2),
    StatusLine(3),
    ShowImage(4),
}

private enum class NativeSmokeIntExprTag(
    override val code: Int,
) : NativeSmokeTag {
    Literal(0),
    Local(1),
    Binary(2),
    If(3),
    Random(4),
    MemberField(5),
    MonsterField(6),
    BattleFlag(7),
    BattleQuery(8),
    RealmScalar(9),
    PartyScalar(10),
    InventoryPoolCount(11),
    InventorySlotCost(12),
    NativeInt(13),
}

private enum class NativeSmokeBoolExprTag(
    override val code: Int,
) : NativeSmokeTag {
    Cmp(0),
    Not(1),
    And(2),
    Or(3),
    FlagIs(4),
    MilestoneAchieved(5),
    MemberFlag(6),
    MonsterFlag(7),
    BattleFlag(8),
    RuntimeFlag(9),
}

private enum class NativeSmokeStringExprTag(
    override val code: Int,
) : NativeSmokeTag {
    NativeString(0),
}

private enum class NativeSmokeNativeStringArgTag(
    override val code: Int,
) : NativeSmokeTag {
    Literal(0),
    IntValue(1),
}

private enum class NativeSmokeLValueTag(
    override val code: Int,
) : NativeSmokeTag {
    RealmScalar(0),
    PartyScalar(1),
    MemberField(2),
    MonsterField(3),
    BattleFlag(4),
}

private enum class NativeSmokeShopPriceTag(
    override val code: Int,
) : NativeSmokeTag {
    ItemCost(0),
    HalfItemCost(1),
    Fixed(2),
    LeaderLevelPlus(3),
    ItemCostPlusLeaderLevelTimes(4),
}

private enum class NativeSmokeShopPurchaseTag(
    override val code: Int,
) : NativeSmokeTag {
    InventoryItem(0),
    Bulk(1),
}

private enum class NativeSmokeShopQuantityCapTag(
    override val code: Int,
) : NativeSmokeTag {
    FixedAmount(0),
    CarryCapacity(1),
}

private class NativeSmokeArtifactWriter {
    private val out = ByteArrayOutputStream()

    fun write(bundle: CompiledGameBundle): ByteArray {
        val content = bundle.content
        bytes(NATIVE_SMOKE_MAGIC.toByteArray(Charsets.US_ASCII))
        u32(NATIVE_SMOKE_VERSION)
        u32(CompiledHandleKind.entries.size)
        u32(CompiledOpcode.entries.size)
        u32(CompiledRuntimeTextPart.entries.size)
        strings(content.strings.entries)
        handles(content.handles.handles.sortedBy { it.denseId })
        scripts(content.scripts.roots.sortedWith(compareBy({ it.rootHandle.kind.ordinal }, { it.rootHandle.id })))
        u32(bundle.states.size)
        return out.toByteArray()
    }

    private fun strings(entries: List<String>) {
        u32(entries.size)
        entries.forEach { text ->
            val encoded = text.toByteArray(Charsets.UTF_8)
            u32(encoded.size)
            bytes(encoded)
        }
    }

    private fun handles(handles: List<CompiledHandle>) {
        u32(handles.size)
        handles.forEach { handle ->
            handleRef(handle.ref)
            u32(handle.denseId)
            stringId(handle.slugString)
        }
    }

    private fun scripts(scripts: List<CompiledScriptProgram>) {
        u32(scripts.size)
        scripts.forEach { script ->
            handleRef(script.rootHandle)
            instructions(script.instructions)
        }
    }

    private fun instructions(instructions: List<CompiledInstruction>) {
        u32(instructions.size)
        instructions.forEach { instruction ->
            u32(instruction.opcode.ordinal)
            list(instruction.operands, ::operand)
        }
    }

    private fun operand(operand: CompiledOperand) {
        when (operand) {
            is CompiledOperand.Integer -> tag(NativeSmokeOperandTag.Integer) { i32(operand.value) }
            is CompiledOperand.BooleanValue -> tag(NativeSmokeOperandTag.BooleanValue) { bool(operand.value) }
            is CompiledOperand.Character -> tag(NativeSmokeOperandTag.Character) { u32(operand.value.code) }
            is CompiledOperand.StringId -> tag(NativeSmokeOperandTag.StringId) { stringId(operand.id) }
            is CompiledOperand.StringIdList -> tag(NativeSmokeOperandTag.StringIdList) { stringIds(operand.ids) }
            is CompiledOperand.Handle -> tag(NativeSmokeOperandTag.Handle) { handleRef(operand.ref) }
            is CompiledOperand.HandleList -> tag(NativeSmokeOperandTag.HandleList) { handleRefs(operand.refs) }
            is CompiledOperand.TextBlockValue -> tag(NativeSmokeOperandTag.TextBlock) { textBlock(operand.value) }
            is CompiledOperand.IntExprValue -> tag(NativeSmokeOperandTag.IntExpr) { intExpr(operand.value) }
            is CompiledOperand.BoolExprValue -> tag(NativeSmokeOperandTag.BoolExpr) { boolExpr(operand.value) }
            is CompiledOperand.StringExprValue -> tag(NativeSmokeOperandTag.StringExpr) { stringExpr(operand.value) }
            is CompiledOperand.LValueValue -> tag(NativeSmokeOperandTag.LValue) { lValue(operand.value) }
            is CompiledOperand.ViewCommandValue -> tag(NativeSmokeOperandTag.ViewCommand) { viewCommand(operand.value) }
            is CompiledOperand.Instructions ->
                tag(NativeSmokeOperandTag.Instructions) {
                    instructions(operand.instructions)
                }
            is CompiledOperand.SwitchCases ->
                tag(NativeSmokeOperandTag.SwitchCases) {
                    list(operand.cases, ::switchCase)
                }
            is CompiledOperand.MenuOptions ->
                tag(NativeSmokeOperandTag.MenuOptions) {
                    list(operand.options, ::menuOption)
                }
            is CompiledOperand.InputBindings ->
                tag(NativeSmokeOperandTag.InputBindings) {
                    list(operand.bindings, ::inputBinding)
                }
            is CompiledOperand.TextAnswer -> tag(NativeSmokeOperandTag.TextAnswer) { textAnswer(operand.answer) }
            is CompiledOperand.Reward -> tag(NativeSmokeOperandTag.Reward) { scriptReward(operand.reward) }
            is CompiledOperand.RandomItemOffer ->
                tag(NativeSmokeOperandTag.RandomItemOffer) {
                    randomItemOffer(operand.offer)
                }
            is CompiledOperand.ShopService -> tag(NativeSmokeOperandTag.ShopService) { shopService(operand.service) }
            is CompiledOperand.EnumName -> tag(NativeSmokeOperandTag.EnumName) { inlineString(operand.name) }
        }
    }

    private fun switchCase(case: CompiledSwitchCase) {
        nullable(case.match, ::i32)
        instructions(case.body)
    }

    private fun menuOption(option: CompiledMenuOption) {
        inputBinding(option.binding)
        instructions(option.body)
    }

    private fun inputBinding(binding: CompiledInputBinding) {
        stringId(binding.primaryString)
        stringIds(binding.aliasStrings)
        stringId(binding.labelString)
    }

    private fun textAnswer(answer: CompiledTextAnswer) {
        stringIds(answer.acceptedStrings)
        instructions(answer.matchOps)
        instructions(answer.missOps)
        bool(answer.caseSensitive)
        bool(answer.trim)
        nullable(answer.maxLength, ::i32)
    }

    private fun textBlock(block: CompiledTextBlock) {
        when (block) {
            is CompiledTextBlock.Ref -> tag(NativeSmokeTextBlockTag.Ref) { handleRef(block.text) }
            is CompiledTextBlock.Inline -> tag(NativeSmokeTextBlockTag.Inline) { stringIds(block.lineStrings) }
            is CompiledTextBlock.Structured -> tag(NativeSmokeTextBlockTag.Structured) { list(block.lines, ::textLine) }
        }
    }

    private fun textLine(line: CompiledTextLine) {
        list(line.parts, ::textPart)
    }

    private fun textPart(part: CompiledTextPart) {
        when (part) {
            is CompiledTextPart.Literal -> tag(NativeSmokeTextPartTag.Literal) { stringId(part.string) }
            is CompiledTextPart.IntValue -> tag(NativeSmokeTextPartTag.IntValue) { intExpr(part.value) }
            is CompiledTextPart.StringValue -> tag(NativeSmokeTextPartTag.StringValue) { stringExpr(part.value) }
            is CompiledTextPart.RoleName -> tag(NativeSmokeTextPartTag.RoleName) { handleRef(part.role) }
            is CompiledTextPart.RoleLocation -> tag(NativeSmokeTextPartTag.RoleLocation) { handleRef(part.role) }
            is CompiledTextPart.TownName -> tag(NativeSmokeTextPartTag.TownName) { handleRef(part.town) }
            is CompiledTextPart.FuzzyTownLocation ->
                tag(NativeSmokeTextPartTag.FuzzyTownLocation) {
                    handleRef(part.town)
                    i32(part.radius)
                }
            is CompiledTextPart.LocationCoordinate ->
                tag(NativeSmokeTextPartTag.LocationCoordinate) {
                    handleRef(part.location)
                }
            is CompiledTextPart.Runtime -> tag(NativeSmokeTextPartTag.Runtime) { u32(part.kind.ordinal) }
        }
    }

    private fun viewCommand(command: CompiledViewCommand) {
        when (command) {
            CompiledViewCommand.ClearText -> tag(NativeSmokeViewCommandTag.ClearText)
            CompiledViewCommand.ClearMapWindow -> tag(NativeSmokeViewCommandTag.ClearMapWindow)
            CompiledViewCommand.RenderMap -> tag(NativeSmokeViewCommandTag.RenderMap)
            CompiledViewCommand.StatusLine -> tag(NativeSmokeViewCommandTag.StatusLine)
            is CompiledViewCommand.ShowImage -> tag(NativeSmokeViewCommandTag.ShowImage) { handleRef(command.image) }
        }
    }

    private fun intExpr(expr: CompiledIntExpr) {
        when (expr) {
            is CompiledIntExpr.Literal -> tag(NativeSmokeIntExprTag.Literal) { i32(expr.value) }
            is CompiledIntExpr.Local -> tag(NativeSmokeIntExprTag.Local) { stringId(expr.nameString) }
            is CompiledIntExpr.Binary ->
                tag(NativeSmokeIntExprTag.Binary) {
                    intExpr(expr.left)
                    enumName(expr.op)
                    intExpr(expr.right)
                }
            is CompiledIntExpr.If ->
                tag(NativeSmokeIntExprTag.If) {
                    boolExpr(expr.cond)
                    intExpr(expr.thenValue)
                    intExpr(expr.elseValue)
                }
            is CompiledIntExpr.Random ->
                tag(NativeSmokeIntExprTag.Random) {
                    intExpr(expr.lo)
                    intExpr(expr.hi)
                }
            is CompiledIntExpr.MemberField ->
                tag(NativeSmokeIntExprTag.MemberField) {
                    intExpr(expr.member)
                    stringId(expr.fieldString)
                }
            is CompiledIntExpr.MonsterField ->
                tag(NativeSmokeIntExprTag.MonsterField) {
                    intExpr(expr.monster)
                    stringId(expr.fieldString)
                }
            is CompiledIntExpr.BattleFlag -> tag(NativeSmokeIntExprTag.BattleFlag) { stringId(expr.nameString) }
            is CompiledIntExpr.BattleQuery -> tag(NativeSmokeIntExprTag.BattleQuery) { stringId(expr.nameString) }
            is CompiledIntExpr.RealmScalar -> tag(NativeSmokeIntExprTag.RealmScalar) { stringId(expr.nameString) }
            is CompiledIntExpr.PartyScalar -> tag(NativeSmokeIntExprTag.PartyScalar) { stringId(expr.nameString) }
            is CompiledIntExpr.InventoryPoolCount ->
                tag(NativeSmokeIntExprTag.InventoryPoolCount) {
                    handleRef(expr.pool)
                }
            is CompiledIntExpr.InventorySlotCost ->
                tag(NativeSmokeIntExprTag.InventorySlotCost) {
                    intExpr(expr.slot)
                }
            is CompiledIntExpr.NativeInt ->
                tag(NativeSmokeIntExprTag.NativeInt) {
                    stringId(expr.pathString)
                    stringIds(expr.argStrings)
                }
        }
    }

    private fun boolExpr(expr: CompiledBoolExpr) {
        when (expr) {
            is CompiledBoolExpr.Cmp ->
                tag(NativeSmokeBoolExprTag.Cmp) {
                    intExpr(expr.left)
                    enumName(expr.op)
                    intExpr(expr.right)
                }
            is CompiledBoolExpr.Not -> tag(NativeSmokeBoolExprTag.Not) { boolExpr(expr.expr) }
            is CompiledBoolExpr.And ->
                tag(NativeSmokeBoolExprTag.And) {
                    boolExpr(expr.left)
                    boolExpr(expr.right)
                }
            is CompiledBoolExpr.Or ->
                tag(NativeSmokeBoolExprTag.Or) {
                    boolExpr(expr.left)
                    boolExpr(expr.right)
                }
            is CompiledBoolExpr.FlagIs ->
                tag(NativeSmokeBoolExprTag.FlagIs) {
                    handleRef(expr.flag)
                    handleRef(expr.state)
                }
            is CompiledBoolExpr.MilestoneAchieved ->
                tag(NativeSmokeBoolExprTag.MilestoneAchieved) {
                    handleRef(expr.milestone)
                }
            is CompiledBoolExpr.MemberFlag ->
                tag(NativeSmokeBoolExprTag.MemberFlag) {
                    intExpr(expr.member)
                    stringId(expr.nameString)
                }
            is CompiledBoolExpr.MonsterFlag ->
                tag(NativeSmokeBoolExprTag.MonsterFlag) {
                    intExpr(expr.monster)
                    stringId(expr.nameString)
                }
            is CompiledBoolExpr.BattleFlag -> tag(NativeSmokeBoolExprTag.BattleFlag) { stringId(expr.nameString) }
            is CompiledBoolExpr.RuntimeFlag -> tag(NativeSmokeBoolExprTag.RuntimeFlag) { stringId(expr.nameString) }
        }
    }

    private fun stringExpr(expr: CompiledStringExpr) {
        when (expr) {
            is CompiledStringExpr.NativeString ->
                tag(NativeSmokeStringExprTag.NativeString) {
                    stringId(expr.pathString)
                    list(expr.args, ::nativeStringArg)
                }
        }
    }

    private fun nativeStringArg(arg: CompiledNativeStringArg) {
        when (arg) {
            is CompiledNativeStringArg.Literal ->
                tag(NativeSmokeNativeStringArgTag.Literal) {
                    stringId(arg.string)
                }
            is CompiledNativeStringArg.IntValue ->
                tag(NativeSmokeNativeStringArgTag.IntValue) {
                    intExpr(arg.value)
                }
        }
    }

    private fun lValue(value: CompiledLValue) {
        when (value) {
            is CompiledLValue.RealmScalar -> tag(NativeSmokeLValueTag.RealmScalar) { stringId(value.nameString) }
            is CompiledLValue.PartyScalar -> tag(NativeSmokeLValueTag.PartyScalar) { stringId(value.nameString) }
            is CompiledLValue.MemberField ->
                tag(NativeSmokeLValueTag.MemberField) {
                    intExpr(value.member)
                    stringId(value.fieldString)
                }
            is CompiledLValue.MonsterField ->
                tag(NativeSmokeLValueTag.MonsterField) {
                    intExpr(value.monster)
                    stringId(value.fieldString)
                }
            is CompiledLValue.BattleFlag -> tag(NativeSmokeLValueTag.BattleFlag) { stringId(value.nameString) }
        }
    }

    private fun scriptReward(reward: CompiledScriptReward) {
        nullable(reward.gold, ::intExpr)
        handleRefs(reward.items)
        rewardApplication(reward.application)
        rewardMessages(reward.messages)
    }

    private fun randomItemOffer(offer: CompiledRandomItemOffer) {
        handleRef(offer.pool)
        textBlock(offer.offer)
        textBlock(offer.choicePrompt)
        textBlock(offer.broke)
        textBlock(offer.deal)
        nullable(offer.declined, ::textBlock)
        rewardApplication(offer.application)
        rewardMessages(offer.messages)
        inputBinding(offer.buy)
        inputBinding(offer.decline)
        nullable(offer.priceOverride, ::i32)
        i32(offer.pauseMillis.broke)
        i32(offer.pauseMillis.full)
        i32(offer.pauseMillis.deal)
        i32(offer.pauseMillis.declined)
    }

    private fun rewardApplication(application: RewardApplicationModel) {
        enumName(application.regularItems)
        enumName(application.overflow)
        enumName(application.specialItems)
    }

    private fun rewardMessages(messages: CompiledRewardMessages) {
        nullable(messages.cantCarry, ::textBlock)
        nullable(messages.pickedUp, ::textBlock)
    }

    private fun shopService(service: CompiledShopService) {
        handleRef(service.building)
        handleRef(service.image)
        textBlock(service.closed)
        textBlock(service.intro)
        textBlock(service.menu)
        inputBinding(service.keys.buy)
        inputBinding(service.keys.sell)
        inputBinding(service.keys.leave)
        textBlock(service.goodbye)
        nullable(service.buy, ::shopBuy)
        nullable(service.sell, ::shopSell)
        nullable(service.interlude, ::shopInterlude)
    }

    private fun shopBuy(buy: CompiledShopBuy) {
        textBlock(buy.prompt)
        list(buy.catalogs, ::shopCatalog)
        textBlock(buy.tooExpensive)
        textBlock(buy.cantCarry)
        textBlock(buy.deal)
        nullable(buy.unitPrice, ::textBlock)
        nullable(buy.singlePrice, ::textBlock)
        textBlock(buy.payPrompt)
        inputBinding(buy.paymentKeys.pay)
        inputBinding(buy.paymentKeys.decline)
        rewardApplication(buy.inventoryApplication)
    }

    private fun shopCatalog(catalog: CompiledShopCatalog) {
        inputBinding(catalog.binding)
        stringId(catalog.titleString)
        nullable(catalog.intro, ::textBlock)
        list(catalog.pages, ::shopCatalogPage)
    }

    private fun shopCatalogPage(page: CompiledShopCatalogPage) {
        list(page.entries, ::shopItemOffer)
        nullable(page.footer, ::textBlock)
    }

    private fun shopItemOffer(offer: CompiledShopItemOffer) {
        inputBinding(offer.binding)
        handleRef(offer.item)
        nullable(offer.labelString, ::stringId)
        nullable(offer.description, ::textBlock)
        shopPrice(offer.price)
        shopPurchase(offer.purchase)
    }

    private fun shopPrice(price: CompiledShopPrice) {
        when (price) {
            CompiledShopPrice.ItemCost -> tag(NativeSmokeShopPriceTag.ItemCost)
            CompiledShopPrice.HalfItemCost -> tag(NativeSmokeShopPriceTag.HalfItemCost)
            is CompiledShopPrice.Fixed -> tag(NativeSmokeShopPriceTag.Fixed) { i32(price.amount) }
            is CompiledShopPrice.LeaderLevelPlus ->
                tag(NativeSmokeShopPriceTag.LeaderLevelPlus) {
                    i32(price.addend)
                }
            is CompiledShopPrice.ItemCostPlusLeaderLevelTimes -> {
                tag(NativeSmokeShopPriceTag.ItemCostPlusLeaderLevelTimes) { i32(price.multiplier) }
            }
        }
    }

    private fun shopPurchase(purchase: CompiledShopPurchase) {
        when (purchase) {
            CompiledShopPurchase.InventoryItem -> tag(NativeSmokeShopPurchaseTag.InventoryItem)
            is CompiledShopPurchase.Bulk ->
                tag(NativeSmokeShopPurchaseTag.Bulk) {
                    enumName(purchase.resource)
                    textBlock(purchase.quantityPrompt)
                    shopQuantityCap(purchase.cap)
                    textBlock(purchase.tooMany)
                    enumName(purchase.zeroQuantityDeal)
                }
        }
    }

    private fun shopQuantityCap(cap: ShopQuantityCap) {
        when (cap) {
            is ShopQuantityCap.FixedAmount ->
                tag(NativeSmokeShopQuantityCapTag.FixedAmount) {
                    enumName(cap.resource)
                    i32(cap.amount)
                }
            is ShopQuantityCap.CarryCapacity ->
                tag(NativeSmokeShopQuantityCapTag.CarryCapacity) {
                    enumName(cap.resource)
                }
        }
    }

    private fun shopSell(sell: CompiledShopSell) {
        textBlock(sell.prompt)
        enumNames(sell.acceptedKinds)
        handleRefs(sell.acceptedItems)
        handleRefs(sell.rejectedItems)
        textBlock(sell.wrongKind)
        textBlock(sell.offer)
        nullable(sell.sold, ::textBlock)
        shopPrice(sell.price)
        nullable(sell.bargain, ::shopBargain)
        inputBinding(sell.saleKeys.accept)
        inputBinding(sell.saleKeys.decline)
    }

    private fun shopBargain(bargain: ShopSellBargainModel) {
        list(bargain.bonuses) { bonus ->
            i32(bonus.rollGreaterThan)
            i32(bonus.addCurrentDivisor)
        }
        nullable(bargain.rejectRollBelow, ::i32)
    }

    private fun shopInterlude(interlude: CompiledShopInterlude) {
        i32(interlude.chancePercent)
        textBlock(interlude.offer)
        inputBinding(interlude.accept)
        inputBinding(interlude.decline)
        list(interlude.accepted, ::textBlock)
        textBlock(interlude.declined)
        bool(interlude.runsRumourAfterAccept)
    }

    private fun tag(
        value: NativeSmokeTag,
        body: () -> Unit = {},
    ) {
        u32(value.code)
        body()
    }

    private fun handleRef(ref: CompiledHandleRef) {
        u32(ref.kind.ordinal)
        u32(ref.id)
    }

    private fun handleRefs(refs: List<CompiledHandleRef>) {
        list(refs, ::handleRef)
    }

    private fun stringId(id: Int) {
        u32(id)
    }

    private fun stringIds(ids: List<Int>) {
        list(ids, ::stringId)
    }

    private fun enumName(value: Enum<*>) {
        inlineString(value.name)
    }

    private fun enumNames(values: Collection<Enum<*>>) {
        list(values.sortedBy { it.name }, ::enumName)
    }

    private fun inlineString(value: String) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        u32(encoded.size)
        bytes(encoded)
    }

    private fun <T> list(
        values: Collection<T>,
        writeValue: (T) -> Unit,
    ) {
        u32(values.size)
        values.forEach(writeValue)
    }

    private fun <T> nullable(
        value: T?,
        writeValue: (T) -> Unit,
    ) {
        bool(value != null)
        value?.let(writeValue)
    }

    private fun bool(value: Boolean) {
        u32(if (value) NATIVE_SMOKE_TRUE else NATIVE_SMOKE_FALSE)
    }

    private fun i32(value: Int) {
        u32(value)
    }

    private fun u32(value: Int) {
        out.write(value and 0xff)
        out.write((value ushr 8) and 0xff)
        out.write((value ushr 16) and 0xff)
        out.write((value ushr 24) and 0xff)
    }

    private fun bytes(value: ByteArray) {
        out.write(value)
    }
}

private data class NativeSmokeHandleRef(
    val kind: Int,
    val id: Int,
)

private class NativeSmokeArtifactReader(
    private val data: ByteArray,
) {
    private var pos = 0
    private var handleKindCount = 0
    private var opcodeCount = 0
    private var runtimeTextPartCount = 0
    private var stringCount = 0
    private var declaredHandles = emptySet<NativeSmokeHandleRef>()
    private var instructionCount = 0L
    private var operandCount = 0L

    fun read(): NativeSmokeArtifactSummary {
        magic()
        val version = u32("artifact version")
        if (version != NATIVE_SMOKE_VERSION) fail("unsupported artifact version")

        handleKindCount = u32("handle kind count")
        opcodeCount = u32("opcode count")
        runtimeTextPartCount = u32("runtime text part count")
        if (handleKindCount != CompiledHandleKind.entries.size) fail("handle kind count does not match runtime")
        if (opcodeCount != CompiledOpcode.entries.size) fail("opcode count does not match runtime")
        if (runtimeTextPartCount != CompiledRuntimeTextPart.entries.size) {
            fail("runtime text part count does not match runtime")
        }

        stringCount = u32("string count")
        repeatCount(stringCount) { skipBytes(u32("string length")) }

        val handles =
            List(u32("handle count")) {
                val ref = rawHandleRef()
                u32("dense handle id")
                stringRef()
                ref
            }
        if (handles.toSet().size != handles.size) fail("declared handle refs must be unique")
        declaredHandles = handles.toSet()

        val scriptCount = u32("script count")
        repeatCount(scriptCount) {
            handleRef()
            instructions()
        }

        val stateCount = u32("state count")
        if (pos != data.size) fail("trailing bytes")

        return NativeSmokeArtifactSummary(
            version = version,
            stringCount = stringCount,
            handleCount = declaredHandles.size,
            scriptCount = scriptCount,
            stateCount = stateCount,
            instructionCount = instructionCount,
            operandCount = operandCount,
        )
    }

    private fun magic() {
        val expected = NATIVE_SMOKE_MAGIC.toByteArray(Charsets.US_ASCII)
        requireAvailable(expected.size)
        if (!data.copyOfRange(pos, pos + expected.size).contentEquals(expected)) fail("bad magic")
        pos += expected.size
    }

    private fun instructions() {
        repeatCount(u32("instruction count")) {
            val opcode = u32("opcode")
            if (opcode !in 0 until opcodeCount) fail("opcode is out of range")
            ++instructionCount
            repeatCount(u32("operand count")) { operand() }
        }
    }

    private fun operand() {
        ++operandCount
        when (tag<NativeSmokeOperandTag>("operand")) {
            NativeSmokeOperandTag.Integer -> u32("integer operand")
            NativeSmokeOperandTag.BooleanValue -> bool()
            NativeSmokeOperandTag.Character -> u32("character operand")
            NativeSmokeOperandTag.StringId -> stringRef()
            NativeSmokeOperandTag.StringIdList -> stringRefList()
            NativeSmokeOperandTag.Handle -> handleRef()
            NativeSmokeOperandTag.HandleList -> handleRefList()
            NativeSmokeOperandTag.TextBlock -> textBlock()
            NativeSmokeOperandTag.IntExpr -> intExpr()
            NativeSmokeOperandTag.BoolExpr -> boolExpr()
            NativeSmokeOperandTag.StringExpr -> stringExpr()
            NativeSmokeOperandTag.LValue -> lValue()
            NativeSmokeOperandTag.ViewCommand -> viewCommand()
            NativeSmokeOperandTag.Instructions -> instructions()
            NativeSmokeOperandTag.SwitchCases -> switchCases()
            NativeSmokeOperandTag.MenuOptions -> menuOptions()
            NativeSmokeOperandTag.InputBindings -> inputBindings()
            NativeSmokeOperandTag.TextAnswer -> textAnswer()
            NativeSmokeOperandTag.Reward -> scriptReward()
            NativeSmokeOperandTag.RandomItemOffer -> randomItemOffer()
            NativeSmokeOperandTag.ShopService -> shopService()
            NativeSmokeOperandTag.EnumName -> inlineString()
        }
    }

    private fun textBlock() {
        when (tag<NativeSmokeTextBlockTag>("text block")) {
            NativeSmokeTextBlockTag.Ref -> handleRef()
            NativeSmokeTextBlockTag.Inline -> stringRefList()
            NativeSmokeTextBlockTag.Structured -> repeatCount(u32("text line count")) { textLine() }
        }
    }

    private fun textLine() {
        repeatCount(u32("text part count")) { textPart() }
    }

    private fun textPart() {
        when (tag<NativeSmokeTextPartTag>("text part")) {
            NativeSmokeTextPartTag.Literal -> stringRef()
            NativeSmokeTextPartTag.IntValue -> intExpr()
            NativeSmokeTextPartTag.StringValue -> stringExpr()
            NativeSmokeTextPartTag.RoleName,
            NativeSmokeTextPartTag.RoleLocation,
            NativeSmokeTextPartTag.TownName,
            NativeSmokeTextPartTag.LocationCoordinate,
            -> handleRef()
            NativeSmokeTextPartTag.FuzzyTownLocation -> {
                handleRef()
                u32("fuzzy town radius")
            }
            NativeSmokeTextPartTag.Runtime -> {
                val runtimePart = u32("runtime text part")
                if (runtimePart !in 0 until runtimeTextPartCount) fail("runtime text part is out of range")
            }
        }
    }

    private fun viewCommand() {
        when (tag<NativeSmokeViewCommandTag>("view command")) {
            NativeSmokeViewCommandTag.ClearText,
            NativeSmokeViewCommandTag.ClearMapWindow,
            NativeSmokeViewCommandTag.RenderMap,
            NativeSmokeViewCommandTag.StatusLine,
            -> Unit
            NativeSmokeViewCommandTag.ShowImage -> handleRef()
        }
    }

    private fun intExpr() {
        when (tag<NativeSmokeIntExprTag>("int expr")) {
            NativeSmokeIntExprTag.Literal -> u32("integer literal")
            NativeSmokeIntExprTag.Local,
            NativeSmokeIntExprTag.BattleFlag,
            NativeSmokeIntExprTag.BattleQuery,
            NativeSmokeIntExprTag.RealmScalar,
            NativeSmokeIntExprTag.PartyScalar,
            -> stringRef()
            NativeSmokeIntExprTag.Binary -> {
                intExpr()
                enumName()
                intExpr()
            }
            NativeSmokeIntExprTag.If -> {
                boolExpr()
                intExpr()
                intExpr()
            }
            NativeSmokeIntExprTag.Random -> {
                intExpr()
                intExpr()
            }
            NativeSmokeIntExprTag.MemberField,
            NativeSmokeIntExprTag.MonsterField,
            -> {
                intExpr()
                stringRef()
            }
            NativeSmokeIntExprTag.InventoryPoolCount -> handleRef()
            NativeSmokeIntExprTag.InventorySlotCost -> intExpr()
            NativeSmokeIntExprTag.NativeInt -> {
                stringRef()
                stringRefList()
            }
        }
    }

    private fun boolExpr() {
        when (tag<NativeSmokeBoolExprTag>("bool expr")) {
            NativeSmokeBoolExprTag.Cmp -> {
                intExpr()
                enumName()
                intExpr()
            }
            NativeSmokeBoolExprTag.Not -> boolExpr()
            NativeSmokeBoolExprTag.And,
            NativeSmokeBoolExprTag.Or,
            -> {
                boolExpr()
                boolExpr()
            }
            NativeSmokeBoolExprTag.FlagIs -> {
                handleRef()
                handleRef()
            }
            NativeSmokeBoolExprTag.MilestoneAchieved -> handleRef()
            NativeSmokeBoolExprTag.MemberFlag,
            NativeSmokeBoolExprTag.MonsterFlag,
            -> {
                intExpr()
                stringRef()
            }
            NativeSmokeBoolExprTag.BattleFlag,
            NativeSmokeBoolExprTag.RuntimeFlag,
            -> stringRef()
        }
    }

    private fun stringExpr() {
        when (tag<NativeSmokeStringExprTag>("string expr")) {
            NativeSmokeStringExprTag.NativeString -> {
                stringRef()
                repeatCount(u32("native string arg count")) { nativeStringArg() }
            }
        }
    }

    private fun nativeStringArg() {
        when (tag<NativeSmokeNativeStringArgTag>("native string arg")) {
            NativeSmokeNativeStringArgTag.Literal -> stringRef()
            NativeSmokeNativeStringArgTag.IntValue -> intExpr()
        }
    }

    private fun lValue() {
        when (tag<NativeSmokeLValueTag>("lvalue")) {
            NativeSmokeLValueTag.RealmScalar,
            NativeSmokeLValueTag.PartyScalar,
            NativeSmokeLValueTag.BattleFlag,
            -> stringRef()
            NativeSmokeLValueTag.MemberField,
            NativeSmokeLValueTag.MonsterField,
            -> {
                intExpr()
                stringRef()
            }
        }
    }

    private fun switchCases() {
        repeatCount(u32("switch case count")) {
            optional { u32("switch match") }
            instructions()
        }
    }

    private fun menuOptions() {
        repeatCount(u32("menu option count")) {
            inputBinding()
            instructions()
        }
    }

    private fun inputBindings() {
        repeatCount(u32("input binding count")) { inputBinding() }
    }

    private fun inputBinding() {
        stringRef()
        stringRefList()
        stringRef()
    }

    private fun textAnswer() {
        stringRefList()
        instructions()
        instructions()
        bool()
        bool()
        optional { u32("text answer max length") }
    }

    private fun scriptReward() {
        optional { intExpr() }
        handleRefList()
        rewardApplication()
        rewardMessages()
    }

    private fun randomItemOffer() {
        handleRef()
        textBlock()
        textBlock()
        textBlock()
        textBlock()
        optional { textBlock() }
        rewardApplication()
        rewardMessages()
        inputBinding()
        inputBinding()
        optional { u32("price override") }
        u32("broke pause")
        u32("full pause")
        u32("deal pause")
        u32("declined pause")
    }

    private fun rewardApplication() {
        enumName()
        enumName()
        enumName()
    }

    private fun rewardMessages() {
        optional { textBlock() }
        optional { textBlock() }
    }

    private fun shopService() {
        handleRef()
        handleRef()
        textBlock()
        textBlock()
        textBlock()
        inputBinding()
        inputBinding()
        inputBinding()
        textBlock()
        optional { shopBuy() }
        optional { shopSell() }
        optional { shopInterlude() }
    }

    private fun shopBuy() {
        textBlock()
        repeatCount(u32("shop catalog count")) { shopCatalog() }
        textBlock()
        textBlock()
        textBlock()
        optional { textBlock() }
        optional { textBlock() }
        textBlock()
        inputBinding()
        inputBinding()
        rewardApplication()
    }

    private fun shopCatalog() {
        inputBinding()
        stringRef()
        optional { textBlock() }
        repeatCount(u32("shop catalog page count")) { shopCatalogPage() }
    }

    private fun shopCatalogPage() {
        repeatCount(u32("shop catalog entry count")) { shopItemOffer() }
        optional { textBlock() }
    }

    private fun shopItemOffer() {
        inputBinding()
        handleRef()
        optional { stringRef() }
        optional { textBlock() }
        shopPrice()
        shopPurchase()
    }

    private fun shopPrice() {
        when (tag<NativeSmokeShopPriceTag>("shop price")) {
            NativeSmokeShopPriceTag.ItemCost,
            NativeSmokeShopPriceTag.HalfItemCost,
            -> Unit
            NativeSmokeShopPriceTag.Fixed,
            NativeSmokeShopPriceTag.LeaderLevelPlus,
            NativeSmokeShopPriceTag.ItemCostPlusLeaderLevelTimes,
            -> u32("shop price value")
        }
    }

    private fun shopPurchase() {
        when (tag<NativeSmokeShopPurchaseTag>("shop purchase")) {
            NativeSmokeShopPurchaseTag.InventoryItem -> Unit
            NativeSmokeShopPurchaseTag.Bulk -> {
                enumName()
                textBlock()
                shopQuantityCap()
                textBlock()
                enumName()
            }
        }
    }

    private fun shopQuantityCap() {
        when (tag<NativeSmokeShopQuantityCapTag>("shop quantity cap")) {
            NativeSmokeShopQuantityCapTag.FixedAmount -> {
                enumName()
                u32("shop quantity cap amount")
            }
            NativeSmokeShopQuantityCapTag.CarryCapacity -> enumName()
        }
    }

    private fun shopSell() {
        textBlock()
        repeatCount(u32("accepted item kind count")) { enumName() }
        handleRefList()
        handleRefList()
        textBlock()
        textBlock()
        optional { textBlock() }
        shopPrice()
        optional { shopBargain() }
        inputBinding()
        inputBinding()
    }

    private fun shopBargain() {
        repeatCount(u32("shop bargain bonus count")) {
            u32("shop bargain roll floor")
            u32("shop bargain divisor")
        }
        optional { u32("shop bargain reject floor") }
    }

    private fun shopInterlude() {
        u32("shop interlude chance")
        textBlock()
        inputBinding()
        inputBinding()
        repeatCount(u32("shop accepted interlude count")) { textBlock() }
        textBlock()
        bool()
    }

    private fun rawHandleRef(): NativeSmokeHandleRef {
        val ref =
            NativeSmokeHandleRef(
                kind = u32("handle kind"),
                id = u32("handle id"),
            )
        if (ref.kind !in 0 until handleKindCount) fail("handle kind is out of range")
        return ref
    }

    private fun handleRef() {
        val ref = rawHandleRef()
        if (ref !in declaredHandles) fail("handle ref is not declared")
    }

    private fun handleRefList() {
        repeatCount(u32("handle ref count")) { handleRef() }
    }

    private fun stringRef() {
        val id = u32("string id")
        if (id !in 1..stringCount) fail("string id is out of range")
    }

    private fun stringRefList() {
        repeatCount(u32("string id count")) { stringRef() }
    }

    private fun enumName() {
        inlineString()
    }

    private fun inlineString() {
        skipBytes(u32("inline string length"))
    }

    private fun optional(body: () -> Unit) {
        when (u32("optional marker")) {
            NATIVE_SMOKE_FALSE -> Unit
            NATIVE_SMOKE_TRUE -> body()
            else -> fail("optional marker is not 0/1")
        }
    }

    private fun bool() {
        when (u32("boolean")) {
            NATIVE_SMOKE_FALSE,
            NATIVE_SMOKE_TRUE,
            -> Unit
            else -> fail("boolean value is not 0/1")
        }
    }

    private inline fun <reified T> tag(label: String): T where T : Enum<T>, T : NativeSmokeTag {
        val code = u32("$label tag")
        return enumEntries<T>().firstOrNull { it.code == code }
            ?: fail("unknown $label tag $code")
    }

    private fun repeatCount(
        count: Int,
        body: () -> Unit,
    ) {
        if (count < 0) fail("count is out of range")
        repeat(count) { body() }
    }

    private fun u32(label: String): Int {
        requireAvailable(4)
        val value =
            (data[pos].toInt() and 0xff) or
                ((data[pos + 1].toInt() and 0xff) shl 8) or
                ((data[pos + 2].toInt() and 0xff) shl 16) or
                ((data[pos + 3].toInt() and 0xff) shl 24)
        pos += 4
        if (value < 0 && label.contains("count")) fail("$label is out of range")
        return value
    }

    private fun skipBytes(count: Int) {
        if (count < 0) fail("byte count is out of range")
        requireAvailable(count)
        pos += count
    }

    private fun requireAvailable(count: Int) {
        if (count < 0 || pos > data.size || count > data.size - pos) fail("unexpected end of file")
    }

    private fun fail(message: String): Nothing = error("native smoke loader failed at byte $pos: $message")
}
