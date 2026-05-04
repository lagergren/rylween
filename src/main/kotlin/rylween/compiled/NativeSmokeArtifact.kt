package rylween.compiled

import rylween.dsl.*
import java.io.ByteArrayOutputStream
import java.io.File

private const val NativeSmokeMagic = "RYLSMOKE"
private const val NativeSmokeVersion = 1

fun CompiledGameBundle.toNativeSmokeArtifact(): ByteArray =
    NativeSmokeArtifactWriter().write(this)

fun CompiledGameBundle.writeNativeSmokeArtifact(file: File) {
    file.parentFile?.mkdirs()
    file.writeBytes(toNativeSmokeArtifact())
}

private class NativeSmokeArtifactWriter {
    private val out = ByteArrayOutputStream()

    fun write(bundle: CompiledGameBundle): ByteArray {
        val content = bundle.content
        bytes(NativeSmokeMagic.toByteArray(Charsets.US_ASCII))
        u32(NativeSmokeVersion)
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
            is CompiledOperand.Integer -> tag(0) { i32(operand.value) }
            is CompiledOperand.BooleanValue -> tag(1) { bool(operand.value) }
            is CompiledOperand.Character -> tag(2) { u32(operand.value.code) }
            is CompiledOperand.StringId -> tag(3) { stringId(operand.id) }
            is CompiledOperand.StringIdList -> tag(4) { stringIds(operand.ids) }
            is CompiledOperand.Handle -> tag(5) { handleRef(operand.ref) }
            is CompiledOperand.HandleList -> tag(6) { handleRefs(operand.refs) }
            is CompiledOperand.TextBlockValue -> tag(7) { textBlock(operand.value) }
            is CompiledOperand.IntExprValue -> tag(8) { intExpr(operand.value) }
            is CompiledOperand.BoolExprValue -> tag(9) { boolExpr(operand.value) }
            is CompiledOperand.StringExprValue -> tag(10) { stringExpr(operand.value) }
            is CompiledOperand.LValueValue -> tag(11) { lValue(operand.value) }
            is CompiledOperand.ViewCommandValue -> tag(12) { viewCommand(operand.value) }
            is CompiledOperand.Instructions -> tag(13) { instructions(operand.instructions) }
            is CompiledOperand.SwitchCases -> tag(14) { list(operand.cases, ::switchCase) }
            is CompiledOperand.MenuOptions -> tag(15) { list(operand.options, ::menuOption) }
            is CompiledOperand.InputBindings -> tag(16) { list(operand.bindings, ::inputBinding) }
            is CompiledOperand.TextAnswer -> tag(17) { textAnswer(operand.answer) }
            is CompiledOperand.Reward -> tag(18) { scriptReward(operand.reward) }
            is CompiledOperand.RandomItemOffer -> tag(19) { randomItemOffer(operand.offer) }
            is CompiledOperand.ShopService -> tag(20) { shopService(operand.service) }
            is CompiledOperand.EnumName -> tag(21) { inlineString(operand.name) }
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
            is CompiledTextBlock.Ref -> tag(0) { handleRef(block.text) }
            is CompiledTextBlock.Inline -> tag(1) { stringIds(block.lineStrings) }
            is CompiledTextBlock.Structured -> tag(2) { list(block.lines, ::textLine) }
        }
    }

    private fun textLine(line: CompiledTextLine) {
        list(line.parts, ::textPart)
    }

    private fun textPart(part: CompiledTextPart) {
        when (part) {
            is CompiledTextPart.Literal -> tag(0) { stringId(part.string) }
            is CompiledTextPart.IntValue -> tag(1) { intExpr(part.value) }
            is CompiledTextPart.StringValue -> tag(2) { stringExpr(part.value) }
            is CompiledTextPart.RoleName -> tag(3) { handleRef(part.role) }
            is CompiledTextPart.RoleLocation -> tag(4) { handleRef(part.role) }
            is CompiledTextPart.TownName -> tag(5) { handleRef(part.town) }
            is CompiledTextPart.FuzzyTownLocation -> tag(6) {
                handleRef(part.town)
                i32(part.radius)
            }
            is CompiledTextPart.LocationCoordinate -> tag(7) { handleRef(part.location) }
            is CompiledTextPart.Runtime -> tag(8) { u32(part.kind.ordinal) }
        }
    }

    private fun viewCommand(command: CompiledViewCommand) {
        when (command) {
            CompiledViewCommand.ClearText -> tag(0)
            CompiledViewCommand.ClearMapWindow -> tag(1)
            CompiledViewCommand.RenderMap -> tag(2)
            CompiledViewCommand.StatusLine -> tag(3)
            is CompiledViewCommand.ShowImage -> tag(4) { handleRef(command.image) }
        }
    }

    private fun intExpr(expr: CompiledIntExpr) {
        when (expr) {
            is CompiledIntExpr.Literal -> tag(0) { i32(expr.value) }
            is CompiledIntExpr.Local -> tag(1) { stringId(expr.nameString) }
            is CompiledIntExpr.Binary -> tag(2) {
                intExpr(expr.left)
                enumName(expr.op)
                intExpr(expr.right)
            }
            is CompiledIntExpr.If -> tag(3) {
                boolExpr(expr.cond)
                intExpr(expr.thenValue)
                intExpr(expr.elseValue)
            }
            is CompiledIntExpr.Random -> tag(4) {
                intExpr(expr.lo)
                intExpr(expr.hi)
            }
            is CompiledIntExpr.MemberField -> tag(5) {
                intExpr(expr.member)
                stringId(expr.fieldString)
            }
            is CompiledIntExpr.MonsterField -> tag(6) {
                intExpr(expr.monster)
                stringId(expr.fieldString)
            }
            is CompiledIntExpr.BattleFlag -> tag(7) { stringId(expr.nameString) }
            is CompiledIntExpr.BattleQuery -> tag(8) { stringId(expr.nameString) }
            is CompiledIntExpr.RealmScalar -> tag(9) { stringId(expr.nameString) }
            is CompiledIntExpr.PartyScalar -> tag(10) { stringId(expr.nameString) }
            is CompiledIntExpr.InventoryPoolCount -> tag(11) { handleRef(expr.pool) }
            is CompiledIntExpr.InventorySlotCost -> tag(12) { intExpr(expr.slot) }
            is CompiledIntExpr.NativeInt -> tag(13) {
                stringId(expr.pathString)
                stringIds(expr.argStrings)
            }
        }
    }

    private fun boolExpr(expr: CompiledBoolExpr) {
        when (expr) {
            is CompiledBoolExpr.Cmp -> tag(0) {
                intExpr(expr.left)
                enumName(expr.op)
                intExpr(expr.right)
            }
            is CompiledBoolExpr.Not -> tag(1) { boolExpr(expr.expr) }
            is CompiledBoolExpr.And -> tag(2) {
                boolExpr(expr.left)
                boolExpr(expr.right)
            }
            is CompiledBoolExpr.Or -> tag(3) {
                boolExpr(expr.left)
                boolExpr(expr.right)
            }
            is CompiledBoolExpr.FlagIs -> tag(4) {
                handleRef(expr.flag)
                handleRef(expr.state)
            }
            is CompiledBoolExpr.MilestoneAchieved -> tag(5) { handleRef(expr.milestone) }
            is CompiledBoolExpr.MemberFlag -> tag(6) {
                intExpr(expr.member)
                stringId(expr.nameString)
            }
            is CompiledBoolExpr.MonsterFlag -> tag(7) {
                intExpr(expr.monster)
                stringId(expr.nameString)
            }
            is CompiledBoolExpr.BattleFlag -> tag(8) { stringId(expr.nameString) }
            is CompiledBoolExpr.RuntimeFlag -> tag(9) { stringId(expr.nameString) }
        }
    }

    private fun stringExpr(expr: CompiledStringExpr) {
        when (expr) {
            is CompiledStringExpr.NativeString -> tag(0) {
                stringId(expr.pathString)
                list(expr.args, ::nativeStringArg)
            }
        }
    }

    private fun nativeStringArg(arg: CompiledNativeStringArg) {
        when (arg) {
            is CompiledNativeStringArg.Literal -> tag(0) { stringId(arg.string) }
            is CompiledNativeStringArg.IntValue -> tag(1) { intExpr(arg.value) }
        }
    }

    private fun lValue(value: CompiledLValue) {
        when (value) {
            is CompiledLValue.RealmScalar -> tag(0) { stringId(value.nameString) }
            is CompiledLValue.PartyScalar -> tag(1) { stringId(value.nameString) }
            is CompiledLValue.MemberField -> tag(2) {
                intExpr(value.member)
                stringId(value.fieldString)
            }
            is CompiledLValue.MonsterField -> tag(3) {
                intExpr(value.monster)
                stringId(value.fieldString)
            }
            is CompiledLValue.BattleFlag -> tag(4) { stringId(value.nameString) }
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
            CompiledShopPrice.ItemCost -> tag(0)
            CompiledShopPrice.HalfItemCost -> tag(1)
            is CompiledShopPrice.Fixed -> tag(2) { i32(price.amount) }
            is CompiledShopPrice.LeaderLevelPlus -> tag(3) { i32(price.addend) }
            is CompiledShopPrice.ItemCostPlusLeaderLevelTimes -> tag(4) { i32(price.multiplier) }
        }
    }

    private fun shopPurchase(purchase: CompiledShopPurchase) {
        when (purchase) {
            CompiledShopPurchase.InventoryItem -> tag(0)
            is CompiledShopPurchase.Bulk -> tag(1) {
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
            is ShopQuantityCap.FixedAmount -> tag(0) {
                enumName(cap.resource)
                i32(cap.amount)
            }
            is ShopQuantityCap.CarryCapacity -> tag(1) {
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

    private fun tag(value: Int, body: () -> Unit = {}) {
        u32(value)
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

    private fun <T> list(values: Collection<T>, writeValue: (T) -> Unit) {
        u32(values.size)
        values.forEach(writeValue)
    }

    private fun <T> nullable(value: T?, writeValue: (T) -> Unit) {
        bool(value != null)
        value?.let(writeValue)
    }

    private fun bool(value: Boolean) {
        u32(if (value) 1 else 0)
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
