/**
 * Shop service model and builders.
 *
 * This file owns the reusable shop DSL: buy/sell catalog records, pricing
 * formulas, quantity caps, bargain rules, interludes, and the `shop { ... }`
 * service builder used by town scripts. Specific shop inventory, text, prices,
 * and scripted service flow are authored in the game DSL and emitted through
 * these generic records.
 */
package rylween.dsl

enum class ShopZeroQuantityDealPolicy { Always, NonZeroOnly }

data class ShopBargainBonusModel(
    val rollGreaterThan: Int,
    val addCurrentDivisor: Int,
) {
    init {
        require(rollGreaterThan in 0..100) { "shop bargain bonus threshold must be 0..100" }
        require(addCurrentDivisor > 0) { "shop bargain bonus divisor must be positive" }
    }
}

data class ShopSellBargainModel(
    val bonuses: List<ShopBargainBonusModel>,
    val rejectRollBelow: Int?,
) {
    init {
        rejectRollBelow?.let { threshold ->
            require(threshold in 0..101) { "shop bargain rejection threshold must be 0..101" }
        }
    }
}

sealed interface ShopQuantityCap {
    val resource: Resource

    data class FixedAmount(
        override val resource: Resource,
        val amount: Int,
    ) : ShopQuantityCap

    data class CarryCapacity(
        override val resource: Resource,
    ) : ShopQuantityCap
}

object ShopResources {
    val torches: Resource = Resource.Torches
    val food: Resource = Resource.Food
}

object ShopQuantityCaps {
    fun fixed(
        resource: Resource,
        amount: Int,
    ): ShopQuantityCap = ShopQuantityCap.FixedAmount(resource, amount)

    fun carryCapacity(resource: Resource): ShopQuantityCap = ShopQuantityCap.CarryCapacity(resource)
}

object ShopZeroQuantityDeal {
    val always: ShopZeroQuantityDealPolicy = ShopZeroQuantityDealPolicy.Always
    val nonZeroOnly: ShopZeroQuantityDealPolicy = ShopZeroQuantityDealPolicy.NonZeroOnly
}

sealed interface ShopPriceModel {
    object ItemCost : ShopPriceModel

    object HalfItemCost : ShopPriceModel

    data class Fixed(
        val amount: Int,
    ) : ShopPriceModel

    data class LeaderLevelPlus(
        val addend: Int,
    ) : ShopPriceModel

    data class ItemCostPlusLeaderLevelTimes(
        val multiplier: Int,
    ) : ShopPriceModel
}

class ShopPriceRefs {
    val itemCost: ShopPriceModel = ShopPriceModel.ItemCost
    val halfItemCost: ShopPriceModel = ShopPriceModel.HalfItemCost

    fun fixed(amount: Int): ShopPriceModel = ShopPriceModel.Fixed(amount)

    fun leaderLevelPlus(addend: Int): ShopPriceModel = ShopPriceModel.LeaderLevelPlus(addend)

    fun itemCostPlusLeaderLevelTimes(multiplier: Int): ShopPriceModel = ShopPriceModel.ItemCostPlusLeaderLevelTimes(multiplier)
}

val ShopPrices: ShopPriceRefs = ShopPriceRefs()

sealed interface ShopPurchaseModel {
    object InventoryItem : ShopPurchaseModel

    data class Bulk(
        val resource: Resource,
        val quantityPrompt: TextBlock,
        val cap: ShopQuantityCap,
        val tooMany: TextBlock,
        val zeroQuantityDeal: ShopZeroQuantityDealPolicy,
    ) : ShopPurchaseModel
}

data class ShopItemOfferModel(
    val binding: InputBindingModel,
    val item: ItemId,
    val label: String?,
    val description: TextBlock?,
    val price: ShopPriceModel,
    val purchase: ShopPurchaseModel,
) {
    constructor(
        key: Char,
        item: ItemId,
        label: String?,
        description: TextBlock?,
        price: ShopPriceModel,
        purchase: ShopPurchaseModel,
    ) : this(inputKey(key), item, label, description, price, purchase)

    val key: Char get() = binding.primaryChar
}

/** One visible page of a shop catalog; [navigation] owns page-turn controls. */
data class ShopCatalogPageModel(
    val entries: List<ShopItemOfferModel>,
    val footer: TextBlock?,
    val navigation: ShopCatalogPageNavigationModel = ShopCatalogPageNavigationModel(),
)

/** Optional typed controls for moving between shop catalog pages. */
data class ShopCatalogPageNavigationModel(
    val previous: InputBindingModel? = null,
    val next: InputBindingModel? = null,
)

data class ShopCatalogModel(
    val binding: InputBindingModel,
    val title: String,
    val intro: TextBlock?,
    val pages: List<ShopCatalogPageModel>,
) {
    constructor(
        key: Char,
        title: String,
        intro: TextBlock?,
        pages: List<ShopCatalogPageModel>,
    ) : this(inputKey(key), title, intro, pages)

    val key: Char get() = binding.primaryChar
}

data class ShopBuyModel(
    val prompt: TextBlock,
    val catalogs: List<ShopCatalogModel>,
    val tooExpensive: TextBlock,
    val cantCarry: TextBlock,
    val deal: TextBlock,
    val unitPrice: TextBlock?,
    val singlePrice: TextBlock?,
    val payPrompt: TextBlock,
    val paymentKeys: ShopPurchaseKeysModel,
    val inventoryApplication: RewardApplicationModel,
)

data class ShopSellModel(
    val prompt: TextBlock,
    val acceptedKinds: Set<ItemKind>,
    val acceptedItems: Set<ItemId>,
    val rejectedItems: Set<ItemId>,
    val wrongKind: TextBlock,
    val offer: TextBlock,
    val declined: TextBlock?,
    val sold: TextBlock?,
    val price: ShopPriceModel,
    val bargain: ShopSellBargainModel?,
    val saleKeys: ShopSaleKeysModel,
)

data class ShopInterludeModel(
    val chancePercent: Int,
    val offer: TextBlock,
    val accept: InputBindingModel,
    val decline: InputBindingModel,
    val accepted: List<TextBlock>,
    val declined: TextBlock,
    val runsRumorAfterAccept: Boolean,
    val forceFirstUntil: FlagState?,
) {
    val acceptKey: Char get() = accept.primaryChar
    val declineKey: Char get() = decline.primaryChar
}

data class ShopMenuKeysModel(
    val buy: InputBindingModel = inputKey('B'),
    val sell: InputBindingModel = inputKey('S'),
    val leave: InputBindingModel = inputKey('L'),
)

data class ShopPurchaseKeysModel(
    val pay: InputBindingModel = inputKey('P'),
    val decline: InputBindingModel = inputKey('N'),
)

data class ShopSaleKeysModel(
    val accept: InputBindingModel = inputKey('Y'),
    val decline: InputBindingModel = inputKey('N'),
    val bargain: InputBindingModel = inputKey('B', label = "Bargain"),
)

data class ShopServiceModel(
    val building: BuildingId,
    val image: ImageId,
    val closed: TextBlock,
    val intro: TextBlock,
    val menu: TextBlock,
    val keys: ShopMenuKeysModel,
    val goodbye: TextBlock,
    val buy: ShopBuyModel?,
    val sell: ShopSellModel?,
    val interlude: ShopInterludeModel?,
)

@RylNextDsl
class ShopTextBuilder : TextLineSink {
    private val text = StructuredTextBlockBuilder()

    val price: TextPart = TextPart.ShopPrice
    val cap: TextPart = TextPart.ShopCarryCap

    override fun addTextLine(line: TextLine) {
        text.addTextLine(line)
    }

    fun build(): TextBlock = text.build()
}

fun shopText(body: ShopTextBuilder.() -> Unit): TextBlock = ShopTextBuilder().apply(body).build()

@RylNextDsl
class ShopServiceBuilder(
    private val building: BuildingId,
) {
    var image: ImageId? = null
    private var closedText: TextBlock = inlineText("Shop Closed.")
    private var introText: TextBlock? = null
    private var menuText: TextBlock? = null
    private var keysModel: ShopMenuKeysModel = ShopMenuKeysModel()
    private var goodbyeText: TextBlock = inlineText("Well, bye then...")
    private var buyModel: ShopBuyModel? = null
    private var sellModel: ShopSellModel? = null
    private var interludeModel: ShopInterludeModel? = null

    fun closed(vararg lines: String) {
        closedText = inlineText(*lines)
    }

    fun closed(body: StructuredTextBlockBuilder.() -> Unit) {
        closedText = structuredText(body)
    }

    fun intro(vararg lines: String) {
        introText = inlineText(*lines)
    }

    fun intro(body: StructuredTextBlockBuilder.() -> Unit) {
        introText = structuredText(body)
    }

    fun menu(vararg lines: String) {
        menuText = inlineText(*lines)
    }

    fun menu(body: StructuredTextBlockBuilder.() -> Unit) {
        menuText = structuredText(body)
    }

    fun keys(body: ShopMenuKeysBuilder.() -> Unit) {
        keysModel = ShopMenuKeysBuilder(keysModel).apply(body).build()
    }

    fun goodbye(vararg lines: String) {
        goodbyeText = inlineText(*lines)
    }

    fun goodbye(body: StructuredTextBlockBuilder.() -> Unit) {
        goodbyeText = structuredText(body)
    }

    fun interlude(
        chancePercent: Int,
        body: ShopInterludeBuilder.() -> Unit,
    ) {
        interludeModel = ShopInterludeBuilder(chancePercent).apply(body).build()
    }

    fun buy(body: ShopBuyBuilder.() -> Unit) {
        buyModel = ShopBuyBuilder().apply(body).build()
    }

    fun sell(body: ShopSellBuilder.() -> Unit) {
        sellModel = ShopSellBuilder().apply(body).build()
    }

    fun build(): ShopServiceModel =
        ShopServiceModel(
            building = building,
            image = requireNotNull(image) { "shop ${building.slug} requires image" },
            closed = closedText,
            intro = requireNotNull(introText) { "shop ${building.slug} requires intro text" },
            menu = requireNotNull(menuText) { "shop ${building.slug} requires menu text" },
            keys = keysModel,
            goodbye = goodbyeText,
            buy = buyModel,
            sell = sellModel,
            interlude = interludeModel,
        )
}

@RylNextDsl
class ShopMenuKeysBuilder(
    initial: ShopMenuKeysModel = ShopMenuKeysModel(),
) {
    var buy: InputBindingModel = initial.buy
    var sell: InputBindingModel = initial.sell
    var leave: InputBindingModel = initial.leave

    fun build(): ShopMenuKeysModel = ShopMenuKeysModel(buy, sell, leave)
}

@RylNextDsl
class ShopInterludeBuilder(
    private val chancePercent: Int,
) {
    private var offerText: TextBlock? = null
    private val acceptedTexts = mutableListOf<TextBlock>()
    private var declinedText: TextBlock? = null
    private var acceptBinding: InputBindingModel = inputKey('A')
    private var declineBinding: InputBindingModel = inputKey('D')
    var runsRumorAfterAccept: Boolean = false
    private var forceFirstUntilState: FlagState? = null

    var acceptKey: Char
        get() = acceptBinding.primaryChar
        set(value) {
            acceptBinding = inputKey(value)
        }
    var declineKey: Char
        get() = declineBinding.primaryChar
        set(value) {
            declineBinding = inputKey(value)
        }

    fun choiceKeys(
        accept: InputBindingModel = acceptBinding,
        decline: InputBindingModel = declineBinding,
    ) {
        acceptBinding = accept
        declineBinding = decline
    }

    fun forceFirstUntil(state: FlagState) {
        forceFirstUntilState = state
    }

    fun offer(vararg lines: String) {
        offerText = inlineText(*lines)
    }

    fun offer(body: StructuredTextBlockBuilder.() -> Unit) {
        offerText = structuredText(body)
    }

    fun accepted(vararg lines: String) {
        acceptedTexts += inlineText(*lines)
    }

    fun accepted(body: StructuredTextBlockBuilder.() -> Unit) {
        acceptedTexts += structuredText(body)
    }

    fun declined(vararg lines: String) {
        declinedText = inlineText(*lines)
    }

    fun declined(body: StructuredTextBlockBuilder.() -> Unit) {
        declinedText = structuredText(body)
    }

    fun build(): ShopInterludeModel =
        ShopInterludeModel(
            chancePercent = chancePercent,
            offer = requireNotNull(offerText) { "shop interlude requires offer text" },
            accept = acceptBinding,
            decline = declineBinding,
            accepted = acceptedTexts.toList(),
            declined = requireNotNull(declinedText) { "shop interlude requires declined text" },
            runsRumorAfterAccept = runsRumorAfterAccept,
            forceFirstUntil = forceFirstUntilState,
        )
}

@RylNextDsl
class ShopBuyBuilder {
    private var promptText: TextBlock? = null
    private val catalogModels = mutableListOf<ShopCatalogModel>()
    private var tooExpensiveText: TextBlock = inlineText("Too expensive!")
    private var cantCarryText: TextBlock = inlineText("You cannot carry more.")
    private var dealText: TextBlock = inlineText("Deal!")
    private var unitPriceText: TextBlock? = null
    private var singlePriceText: TextBlock? = null
    private var payPromptText: TextBlock = inlineText("(P)ay", "(N)one")
    private var paymentKeysModel: ShopPurchaseKeysModel = ShopPurchaseKeysModel()
    private var applicationModel =
        RewardApplicationBuilder()
            .apply {
                overflow = RewardOverflowPolicy.PromptDropThenPlace
            }.build()

    fun prompt(vararg lines: String) {
        promptText = inlineText(*lines)
    }

    fun prompt(body: StructuredTextBlockBuilder.() -> Unit) {
        promptText = structuredText(body)
    }

    fun tooExpensive(vararg lines: String) {
        tooExpensiveText = inlineText(*lines)
    }

    fun cantCarry(vararg lines: String) {
        cantCarryText = inlineText(*lines)
    }

    fun deal(vararg lines: String) {
        dealText = inlineText(*lines)
    }

    fun unitPrice(body: ShopTextBuilder.() -> Unit) {
        unitPriceText = ShopTextBuilder().apply(body).build()
    }

    fun singlePrice(body: ShopTextBuilder.() -> Unit) {
        singlePriceText = ShopTextBuilder().apply(body).build()
    }

    fun payPrompt(vararg lines: String) {
        payPromptText = inlineText(*lines)
    }

    fun paymentKeys(body: ShopPurchaseKeysBuilder.() -> Unit) {
        paymentKeysModel = ShopPurchaseKeysBuilder(paymentKeysModel).apply(body).build()
    }

    fun exactFullInventoryReject() {
        applicationModel = applicationModel.copy(overflow = RewardOverflowPolicy.SkipIfInventoryFull)
    }

    fun catalog(
        key: Char,
        title: String,
        body: ShopCatalogBuilder.() -> Unit,
    ) {
        catalog(inputKey(key), title, body)
    }

    fun catalog(
        binding: InputBindingModel,
        title: String,
        body: ShopCatalogBuilder.() -> Unit,
    ) {
        catalogModels += ShopCatalogBuilder(binding, title).apply(body).build()
    }

    fun build(): ShopBuyModel =
        ShopBuyModel(
            prompt = requireNotNull(promptText) { "shop buy section requires prompt text" },
            catalogs = catalogModels.toList(),
            tooExpensive = tooExpensiveText,
            cantCarry = cantCarryText,
            deal = dealText,
            unitPrice = unitPriceText,
            singlePrice = singlePriceText,
            payPrompt = payPromptText,
            paymentKeys = paymentKeysModel,
            inventoryApplication = applicationModel,
        )
}

@RylNextDsl
class ShopPurchaseKeysBuilder(
    initial: ShopPurchaseKeysModel = ShopPurchaseKeysModel(),
) {
    var pay: InputBindingModel = initial.pay
    var decline: InputBindingModel = initial.decline

    fun build(): ShopPurchaseKeysModel = ShopPurchaseKeysModel(pay, decline)
}

@RylNextDsl
class ShopCatalogBuilder(
    private val binding: InputBindingModel,
    private val title: String,
) : ShopItemSink {
    private var introText: TextBlock? = null
    private val pages = mutableListOf<ShopCatalogPageModel>()
    private val looseEntries = mutableListOf<ShopItemOfferModel>()

    override fun addShopItem(model: ShopItemOfferModel) {
        looseEntries += model
    }

    fun intro(vararg lines: String) {
        introText = inlineText(*lines)
    }

    fun resource(
        key: Char,
        item: ItemId,
        resource: Resource,
        cap: ShopQuantityCap,
        tooMany: TextBlock,
        label: String? = null,
        price: ShopPriceModel = ShopPriceModel.ItemCost,
        zeroQuantityDeal: ShopZeroQuantityDealPolicy = ShopZeroQuantityDealPolicy.NonZeroOnly,
        quantityPrompt: TextBlock = inlineText("How many? "),
        description: TextBlock? = null,
    ) {
        resource(inputKey(key), item, resource, cap, tooMany, label, price, zeroQuantityDeal, quantityPrompt, description)
    }

    fun resource(
        binding: InputBindingModel,
        item: ItemId,
        resource: Resource,
        cap: ShopQuantityCap,
        tooMany: TextBlock,
        label: String? = null,
        price: ShopPriceModel = ShopPriceModel.ItemCost,
        zeroQuantityDeal: ShopZeroQuantityDealPolicy = ShopZeroQuantityDealPolicy.NonZeroOnly,
        quantityPrompt: TextBlock = inlineText("How many? "),
        description: TextBlock? = null,
    ) {
        looseEntries +=
            ShopItemOfferModel(
                binding = binding,
                item = item,
                label = label,
                description = description,
                price = price,
                purchase = ShopPurchaseModel.Bulk(resource, quantityPrompt, cap, tooMany, zeroQuantityDeal),
            )
    }

    fun resource(
        key: Char,
        item: ItemId,
        resource: Resource,
        cap: ShopQuantityCap,
        tooMany: TextBlock,
        label: String? = null,
        price: ShopPriceModel = ShopPriceModel.ItemCost,
        zeroQuantityDeal: ShopZeroQuantityDealPolicy = ShopZeroQuantityDealPolicy.NonZeroOnly,
        quantityPrompt: TextBlock = inlineText("How many? "),
        description: StructuredTextBlockBuilder.() -> Unit,
    ) {
        resource(
            inputKey(key),
            item,
            resource,
            cap,
            tooMany,
            label,
            price,
            zeroQuantityDeal,
            quantityPrompt,
            structuredText(description),
        )
    }

    fun resource(
        binding: InputBindingModel,
        item: ItemId,
        resource: Resource,
        cap: ShopQuantityCap,
        tooMany: TextBlock,
        label: String? = null,
        price: ShopPriceModel = ShopPriceModel.ItemCost,
        zeroQuantityDeal: ShopZeroQuantityDealPolicy = ShopZeroQuantityDealPolicy.NonZeroOnly,
        quantityPrompt: TextBlock = inlineText("How many? "),
        description: StructuredTextBlockBuilder.() -> Unit,
    ) {
        resource(
            binding,
            item,
            resource,
            cap,
            tooMany,
            label,
            price,
            zeroQuantityDeal,
            quantityPrompt,
            structuredText(description),
        )
    }

    fun page(
        footer: TextBlock? = null,
        body: ShopCatalogPageBuilder.() -> Unit,
    ) {
        pages += ShopCatalogPageBuilder().apply(body).build(footer)
    }

    fun page(
        vararg footerLines: String,
        body: ShopCatalogPageBuilder.() -> Unit,
    ) {
        page(inlineText(*footerLines), body)
    }

    fun build(): ShopCatalogModel =
        ShopCatalogModel(
            binding = binding,
            title = title,
            intro = introText,
            pages =
                pages.toList() +
                    looseEntries
                        .takeIf { entries -> entries.isNotEmpty() }
                        ?.let { entries ->
                            listOf(ShopCatalogPageModel(entries.toList(), footer = null))
                        }.orEmpty(),
        )
}

@RylNextDsl
class ShopCatalogPageBuilder : ShopItemSink {
    private val entries = mutableListOf<ShopItemOfferModel>()
    private var navigation = ShopCatalogPageNavigationModel()

    override fun addShopItem(model: ShopItemOfferModel) {
        entries += model
    }

    /** Marks this page as accepting [binding] to move to the previous catalog page. */
    fun previousPage(binding: InputBindingModel = inputKey('P', label = "Previous Page")) {
        navigation = navigation.copy(previous = binding)
    }

    fun previousPage(
        key: Char,
        label: String = "Previous Page",
    ) {
        previousPage(inputKey(key, label = label))
    }

    /** Marks this page as accepting [binding] to move to the next catalog page. */
    fun nextPage(binding: InputBindingModel = inputKey('N', label = "Next Page")) {
        navigation = navigation.copy(next = binding)
    }

    fun nextPage(
        key: Char,
        label: String = "Next Page",
    ) {
        nextPage(inputKey(key, label = label))
    }

    fun build(footer: TextBlock?): ShopCatalogPageModel = ShopCatalogPageModel(entries.toList(), footer, navigation)
}

interface ShopItemSink {
    fun addShopItem(model: ShopItemOfferModel)
}

fun ShopItemSink.item(
    binding: InputBindingModel,
    item: ItemId,
    label: String? = null,
    price: ShopPriceModel = ShopPriceModel.ItemCost,
    description: TextBlock? = null,
) {
    addShopItem(
        ShopItemOfferModel(
            binding = binding,
            item = item,
            label = label,
            description = description,
            price = price,
            purchase = ShopPurchaseModel.InventoryItem,
        ),
    )
}

fun ShopItemSink.item(
    key: Char,
    item: ItemId,
    label: String? = null,
    price: ShopPriceModel = ShopPriceModel.ItemCost,
    description: TextBlock? = null,
) {
    item(inputKey(key), item, label, price, description)
}

fun ShopItemSink.item(
    binding: InputBindingModel,
    item: ItemId,
    label: String? = null,
    price: ShopPriceModel = ShopPriceModel.ItemCost,
    description: StructuredTextBlockBuilder.() -> Unit,
) {
    item(binding, item, label, price, structuredText(description))
}

fun ShopItemSink.item(
    key: Char,
    item: ItemId,
    label: String? = null,
    price: ShopPriceModel = ShopPriceModel.ItemCost,
    description: StructuredTextBlockBuilder.() -> Unit,
) {
    item(inputKey(key), item, label, price, structuredText(description))
}

@RylNextDsl
class ShopSellBuilder {
    private var promptText: TextBlock? = null
    private val acceptedKindModels = mutableSetOf<ItemKind>()
    private val acceptedItemModels = mutableSetOf<ItemId>()
    private val rejectedItemModels = mutableSetOf<ItemId>()
    private var wrongKindText: TextBlock = inlineText("Wrong kind.")
    private var offerText: TextBlock? = null
    private var declinedText: TextBlock? = null
    private var soldText: TextBlock? = null
    var price: ShopPriceModel = ShopPriceModel.HalfItemCost
    private var bargainModel: ShopSellBargainModel? = null
    private var saleKeysModel: ShopSaleKeysModel = ShopSaleKeysModel()

    fun prompt(vararg lines: String) {
        promptText = inlineText(*lines)
    }

    fun acceptsKinds(vararg kinds: ItemKind) {
        acceptedKindModels += kinds
    }

    fun accepts(vararg items: ItemId) {
        acceptedItemModels += items
    }

    fun rejects(vararg items: ItemId) {
        rejectedItemModels += items
    }

    fun wrongKind(vararg lines: String) {
        wrongKindText = inlineText(*lines)
    }

    fun offer(body: ShopTextBuilder.() -> Unit) {
        offerText = ShopTextBuilder().apply(body).build()
    }

    fun declined(vararg lines: String) {
        declinedText = inlineText(*lines)
    }

    fun sold(vararg lines: String) {
        soldText = inlineText(*lines)
    }

    fun saleKeys(body: ShopSaleKeysBuilder.() -> Unit) {
        saleKeysModel = ShopSaleKeysBuilder(saleKeysModel).apply(body).build()
    }

    fun bargain(body: ShopSellBargainBuilder.() -> Unit) {
        bargainModel = ShopSellBargainBuilder().apply(body).build()
    }

    fun smithHaggle() {
        bargain {
            bonus(rollGreaterThan = 90, addCurrentDivisor = 4)
            bonus(rollGreaterThan = 75, addCurrentDivisor = 6)
            rejectWhenRollBelow(76)
        }
    }

    fun build(): ShopSellModel =
        ShopSellModel(
            prompt = requireNotNull(promptText) { "shop sell section requires prompt text" },
            acceptedKinds = acceptedKindModels.toSet(),
            acceptedItems = acceptedItemModels.toSet(),
            rejectedItems = rejectedItemModels.toSet(),
            wrongKind = wrongKindText,
            offer = requireNotNull(offerText) { "shop sell section requires offer text" },
            declined = declinedText,
            sold = soldText,
            price = price,
            bargain = bargainModel,
            saleKeys = saleKeysModel,
        )
}

@RylNextDsl
class ShopSellBargainBuilder {
    private val bonuses = mutableListOf<ShopBargainBonusModel>()
    private var rejectRollBelow: Int? = null

    fun bonus(
        rollGreaterThan: Int,
        addCurrentDivisor: Int,
    ) {
        bonuses += ShopBargainBonusModel(rollGreaterThan, addCurrentDivisor)
    }

    fun rejectWhenRollBelow(threshold: Int) {
        rejectRollBelow = threshold
    }

    internal fun build(): ShopSellBargainModel =
        ShopSellBargainModel(
            bonuses = bonuses.toList(),
            rejectRollBelow = rejectRollBelow,
        )
}

@RylNextDsl
class ShopSaleKeysBuilder(
    initial: ShopSaleKeysModel = ShopSaleKeysModel(),
) {
    var accept: InputBindingModel = initial.accept
    var decline: InputBindingModel = initial.decline
    var bargain: InputBindingModel = initial.bargain

    fun build(): ShopSaleKeysModel = ShopSaleKeysModel(accept, decline, bargain)
}
