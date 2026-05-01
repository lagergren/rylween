// Pure Kotlin runtime helpers for DSL systems that should work behind any
// frontend or engine bridge. These functions mutate immutable PartyState values
// and keep UI/input decisions explicit in their inputs.

enum class EquipmentKind { Weapon, Armor }
enum class EquipmentCommand { ReadyWeapon, ReadyArmor, RemoveWeapon, RemoveArmor }
enum class ShopMainChoice { Buy, Sell, Leave }

sealed interface EquipmentPromptInput {
    data class InventorySlot(val slot: Int) : EquipmentPromptInput
    data class Member(val memberNumber: Int) : EquipmentPromptInput
}

sealed interface TownLeaveEquipmentInput {
    data class ReadyAnswer(val yes: Boolean) : TownLeaveEquipmentInput
    data class Equipment(val input: EquipmentPromptInput) : TownLeaveEquipmentInput
}

sealed interface ShopServiceInput {
    object ContinueIntro : ShopServiceInput
    data class Main(val choice: ShopMainChoice) : ShopServiceInput
    data class BuyItem(val item: ItemId) : ShopServiceInput
    data class Quantity(val quantity: Int) : ShopServiceInput
    data class ConfirmPurchase(val accept: Boolean) : ShopServiceInput
    data class DropSlot(val slot: Int) : ShopServiceInput
    data class SellSlot(val slot: Int, val bargainRolls: List<Int> = emptyList()) : ShopServiceInput
    data class ConfirmSale(val accept: Boolean) : ShopServiceInput
}

sealed interface TownSessionInput {
    data class MenuKey(val key: Char) : TownSessionInput
    data class Shop(val input: ShopServiceInput) : TownSessionInput
    data class Equipment(val input: TownLeaveEquipmentInput) : TownSessionInput
    data class FinishNativeService(val party: PartyState) : TownSessionInput
}

sealed interface InventoryPlacementResult {
    data class Placed(val party: PartyState, val slot: Int) : InventoryPlacementResult
    data class NeedsDropSlot(val party: PartyState) : InventoryPlacementResult
    data class Full(val party: PartyState) : InventoryPlacementResult
    data class InvalidDropSlot(val party: PartyState, val slot: Int) : InventoryPlacementResult
}

sealed interface EquipmentActionResult {
    data class Equipped(val party: PartyState, val memberNumber: Int, val inventorySlot: Int) : EquipmentActionResult
    data class Unequipped(val party: PartyState, val memberNumber: Int, val inventorySlot: Int) : EquipmentActionResult
    data class InvalidMember(val party: PartyState, val memberNumber: Int) : EquipmentActionResult
    data class DeadMember(val party: PartyState, val memberNumber: Int) : EquipmentActionResult
    data class InvalidInventorySlot(val party: PartyState, val slot: Int) : EquipmentActionResult
    data class WrongItemKind(val party: PartyState, val slot: Int, val item: ItemId?) : EquipmentActionResult
    data class NothingEquipped(val party: PartyState, val memberNumber: Int, val kind: EquipmentKind) : EquipmentActionResult
    data class InventoryFull(val party: PartyState) : EquipmentActionResult
}

sealed interface TorchActionResult {
    data class Consumed(val party: PartyState) : TorchActionResult
    data class NoTorches(val party: PartyState) : TorchActionResult
}

sealed interface DungeonDescentResult {
    data class Descended(val party: PartyState) : DungeonDescentResult
    data class NoTorches(val party: PartyState) : DungeonDescentResult
    data class AtMaxDepth(val party: PartyState) : DungeonDescentResult
}

enum class ShopTransactionStatus {
    Bought,
    Sold,
    NeedsDropSlot,
    InventoryFull,
    InvalidDropSlot,
    ItemNotSoldHere,
    ItemNotAcceptedHere,
    EmptySlot,
    NotEnoughGold,
    InvalidQuantity,
    Declined,
}

enum class ShopSellQuoteStatus {
    Quoted,
    ItemNotAcceptedHere,
    EmptySlot,
    Declined,
}

enum class TownSessionResultStatus {
    EnteredShop,
    EnteredNativeService,
    ServiceNotOffered,
    ServiceClosed,
    MissingService,
    ShopFinished,
    NativeServiceFinished,
}

data class ShopTransactionResult(
    val status: ShopTransactionStatus,
    val party: PartyState,
    val item: ItemId? = null,
    val goldDelta: Int = 0,
    val slot: Int? = null,
    val quantity: Int = 1,
)

data class ShopSellQuoteResult(
    val status: ShopSellQuoteStatus,
    val party: PartyState,
    val item: ItemId? = null,
    val slot: Int? = null,
    val price: Int = 0,
    val bargainRolls: List<Int> = emptyList(),
)

data class TownSessionResult(
    val status: TownSessionResultStatus,
    val party: PartyState,
    val building: BuildingId? = null,
)

data class CampGearEffect(
    val image: ImageId,
    val protectsLowTemperature: Boolean,
    val protectsVeryLowTemperature: Boolean,
)

data class EquipmentPromptModel(
    val command: EquipmentCommand,
    val kind: EquipmentKind,
    val memberFilter: MemberChoiceFilter,
    val inventoryFilter: InventorySlotChoiceFilter?,
)

object EquipmentPromptText {
    val inventory = inlineText("Ready what?")
    val who = inlineText("Who? (1-N)")
    val readyWeapons = inlineText("Do you want to ready", "some weapons before", "leaving?", "")
    val readyArmors = inlineText("Do you want to ready", "some armors before", "leaving?", "")
}

sealed interface EquipmentPromptStep {
    data class ChooseInventorySlot(
        val prompt: TextBlock,
        val filter: InventorySlotChoiceFilter,
    ) : EquipmentPromptStep
    data class ChooseMember(
        val prompt: TextBlock,
        val filter: MemberChoiceFilter,
    ) : EquipmentPromptStep
    data class Finished(val result: EquipmentActionResult) : EquipmentPromptStep
}

data class EquipmentPromptSession(
    val command: EquipmentCommand,
    val party: PartyState,
    val step: EquipmentPromptStep,
    val inventorySlot: Int? = null,
) {
    fun apply(input: EquipmentPromptInput): EquipmentPromptSession =
        when (val current = step) {
            is EquipmentPromptStep.ChooseInventorySlot -> {
                val slot = (input as? EquipmentPromptInput.InventorySlot)
                    ?: error("equipment session is choosing an inventory slot")
                copy(
                    step = EquipmentPromptStep.ChooseMember(
                        EquipmentPromptText.who,
                        command.prompt().memberFilter,
                    ),
                    inventorySlot = slot.slot,
                )
            }
            is EquipmentPromptStep.ChooseMember -> {
                val member = (input as? EquipmentPromptInput.Member)
                    ?: error("equipment session is choosing a member")
                val result = party.applyEquipmentCommand(command, member.memberNumber, inventorySlot)
                copy(party = result.party, step = EquipmentPromptStep.Finished(result))
            }
            is EquipmentPromptStep.Finished -> error("equipment session is already finished: $current")
        }

    companion object {
        fun start(command: EquipmentCommand, party: PartyState): EquipmentPromptSession {
            val prompt = command.prompt()
            val step = prompt.inventoryFilter?.let {
                EquipmentPromptStep.ChooseInventorySlot(EquipmentPromptText.inventory, it)
            } ?: EquipmentPromptStep.ChooseMember(EquipmentPromptText.who, prompt.memberFilter)
            return EquipmentPromptSession(command, party, step)
        }
    }
}

sealed interface TownLeaveEquipmentStep {
    data class ReadyQuestion(val command: EquipmentCommand, val prompt: TextBlock) : TownLeaveEquipmentStep
    data class Equipment(val session: EquipmentPromptSession) : TownLeaveEquipmentStep
    data class Finished(val party: PartyState) : TownLeaveEquipmentStep
}

data class TownLeaveEquipmentSession(
    val party: PartyState,
    val step: TownLeaveEquipmentStep,
    val itemRecords: List<ItemRecordModel> = ItemCatalog.all,
    val phase: Int = 0,
    val lastResult: EquipmentActionResult? = null,
) {
    fun apply(input: TownLeaveEquipmentInput): TownLeaveEquipmentSession =
        when (val current = step) {
            is TownLeaveEquipmentStep.ReadyQuestion -> {
                val answer = (input as? TownLeaveEquipmentInput.ReadyAnswer)
                    ?: error("town-leave equipment session is asking a ready question")
                if (answer.yes) {
                    copy(step = TownLeaveEquipmentStep.Equipment(EquipmentPromptSession.start(current.command, party)))
                } else {
                    advance(party, lastResult = null)
                }
            }
            is TownLeaveEquipmentStep.Equipment -> {
                val equipment = (input as? TownLeaveEquipmentInput.Equipment)
                    ?: error("town-leave equipment session is running equipment prompts")
                val updated = current.session.apply(equipment.input)
                when (val finished = updated.step) {
                    is EquipmentPromptStep.Finished ->
                        advance(updated.party, finished.result)
                    else -> copy(step = TownLeaveEquipmentStep.Equipment(updated), party = updated.party)
                }
            }
            is TownLeaveEquipmentStep.Finished ->
                error("town-leave equipment session is already finished: $current")
        }

    private fun advance(nextParty: PartyState, lastResult: EquipmentActionResult?): TownLeaveEquipmentSession {
        val next = nextStep(nextParty, phase + 1)
        return copy(
            party = nextParty,
            phase = next.phase,
            lastResult = lastResult,
            step = next.step,
        )
    }

    private fun nextStep(nextParty: PartyState, nextPhase: Int): NextTownLeaveStep =
        readyCommands
            .withIndex()
            .drop(nextPhase)
            .firstOrNull { (_, command) -> nextParty.needsTownLeaveReadyPrompt(command, itemRecords) }
            ?.let { (index, command) ->
                NextTownLeaveStep(index, TownLeaveEquipmentStep.ReadyQuestion(command, command.townLeavePrompt()))
            }
            ?: NextTownLeaveStep(readyCommands.size, TownLeaveEquipmentStep.Finished(nextParty))

    private data class NextTownLeaveStep(val phase: Int, val step: TownLeaveEquipmentStep)

    companion object {
        private val readyCommands = listOf(EquipmentCommand.ReadyWeapon, EquipmentCommand.ReadyArmor)

        fun start(
            party: PartyState,
            itemRecords: List<ItemRecordModel> = ItemCatalog.all,
        ): TownLeaveEquipmentSession {
            val first = readyCommands
                .withIndex()
                .firstOrNull { (_, command) -> party.needsTownLeaveReadyPrompt(command, itemRecords) }
            return TownLeaveEquipmentSession(
                party = party,
                phase = first?.index ?: readyCommands.size,
                itemRecords = itemRecords,
                step = first?.value?.let { TownLeaveEquipmentStep.ReadyQuestion(it, it.townLeavePrompt()) }
                    ?: TownLeaveEquipmentStep.Finished(party),
            )
        }
    }
}

object TownPromptText {
    fun menu(town: TownModel): TextBlock =
        inlineText(town.name, "")

    fun unavailable(building: BuildingId): TextBlock =
        inlineText("${building.slug} is not available here.")

    fun missingService(building: BuildingId): TextBlock =
        inlineText("${building.slug} service is not implemented yet.")

    fun closed(building: BuildingId): TextBlock =
        inlineText("${building.slug} is closed.")
}

sealed interface TownSessionStep {
    data class Menu(
        val town: TownModel,
        val actions: List<TownMenuActionModel>,
    ) : TownSessionStep
    data class Notice(
        val text: TextBlock,
        val next: Menu,
    ) : TownSessionStep
    data class Shop(
        val service: BuildingServiceModel,
        val session: ShopServiceSession,
    ) : TownSessionStep
    data class NativeService(
        val service: BuildingServiceModel,
        val scene: SceneModel,
    ) : TownSessionStep
    data class LeaveEquipment(
        val session: TownLeaveEquipmentSession,
    ) : TownSessionStep
    data class Finished(
        val party: PartyState,
    ) : TownSessionStep
}

data class TownSession(
    val town: TownModel,
    val townSystem: TownSystemModel,
    val party: PartyState,
    val currentHour: Int,
    val shopServices: Map<BuildingId, ShopServiceModel>,
    val serviceScenes: Map<SceneId, SceneModel>,
    val itemRecords: List<ItemRecordModel> = ItemCatalog.all,
    val step: TownSessionStep = TownSessionStep.Menu(town, availableTownActions(town, townSystem)),
    val lastResult: TownSessionResult? = null,
) {
    fun apply(input: TownSessionInput): TownSession =
        when (input) {
            is TownSessionInput.MenuKey -> chooseMenu(input.key)
            is TownSessionInput.Shop -> applyShop(input.input)
            is TownSessionInput.Equipment -> applyEquipment(input.input)
            is TownSessionInput.FinishNativeService -> finishNative(input.party)
        }

    fun continueNotice(): TownSession {
        val current = step as? TownSessionStep.Notice
            ?: error("town session is not showing a notice")
        return copy(step = current.next)
    }

    fun replaceShopSession(session: ShopServiceSession): TownSession {
        val current = step as? TownSessionStep.Shop
            ?: error("town session is not running a shop")
        return if (session.step is ShopServiceStep.Finished) {
            finishShop(session, current.service.building)
        } else {
            copy(party = session.party, step = current.copy(session = session))
        }
    }

    fun replaceLeaveEquipmentSession(session: TownLeaveEquipmentSession): TownSession {
        require(step is TownSessionStep.LeaveEquipment) { "town session is not running leave equipment prompts" }
        return if (session.step is TownLeaveEquipmentStep.Finished) {
            copy(party = session.party, step = TownSessionStep.Finished(session.party))
        } else {
            copy(party = session.party, step = TownSessionStep.LeaveEquipment(session))
        }
    }

    private fun chooseMenu(key: Char): TownSession {
        val action = townSystem.menu.firstOrNull { it.key == key.uppercaseChar() }
            ?: return copy(lastResult = null)
        if (action.endsMenu) {
            val equipment = TownLeaveEquipmentSession.start(party, itemRecords)
            return if (equipment.step is TownLeaveEquipmentStep.Finished) {
                copy(step = TownSessionStep.Finished(party))
            } else {
                copy(step = TownSessionStep.LeaveEquipment(equipment), lastResult = null)
            }
        }

        val building = checkNotNull(action.building) { "town service action ${action.action.slug} has no building" }
        if (!town.offers(building)) {
            return notice(
                TownPromptText.unavailable(building),
                TownSessionResult(TownSessionResultStatus.ServiceNotOffered, party, building),
            )
        }

        val service = townSystem.buildingServices.firstOrNull { it.building == building }
            ?: return notice(
                TownPromptText.missingService(building),
                TownSessionResult(TownSessionResultStatus.MissingService, party, building),
            )
        val scene = serviceScenes[service.scene]
            ?: return notice(
                TownPromptText.missingService(building),
                TownSessionResult(TownSessionResultStatus.MissingService, party, building),
            )
        val shop = shopServices[building]
        if (!service.isOpenAt(currentHour)) {
            return notice(
                shop?.closed ?: TownPromptText.closed(building),
                TownSessionResult(TownSessionResultStatus.ServiceClosed, party, building),
            )
        }

        return if (shop != null) {
            copy(
                step = TownSessionStep.Shop(service, ShopServiceSession.start(shop, party, itemRecords)),
                lastResult = TownSessionResult(TownSessionResultStatus.EnteredShop, party, building),
            )
        } else {
            copy(
                step = TownSessionStep.NativeService(service, scene),
                lastResult = TownSessionResult(TownSessionResultStatus.EnteredNativeService, party, building),
            )
        }
    }

    private fun applyShop(input: ShopServiceInput): TownSession {
        val current = step as? TownSessionStep.Shop
            ?: error("town session is not running a shop")
        return replaceShopSession(applyShopInput(current.session, input))
    }

    private fun applyEquipment(input: TownLeaveEquipmentInput): TownSession {
        val current = step as? TownSessionStep.LeaveEquipment
            ?: error("town session is not running leave equipment prompts")
        return replaceLeaveEquipmentSession(current.session.apply(input))
    }

    private fun finishNative(nextParty: PartyState): TownSession {
        val current = step as? TownSessionStep.NativeService
            ?: error("town session is not running a native service")
        return copy(
            party = nextParty,
            step = menuStep(),
            lastResult = TownSessionResult(TownSessionResultStatus.NativeServiceFinished, nextParty, current.service.building),
        )
    }

    private fun finishShop(session: ShopServiceSession, building: BuildingId): TownSession =
        copy(
            party = session.party,
            step = menuStep(),
            lastResult = TownSessionResult(TownSessionResultStatus.ShopFinished, session.party, building),
        )

    private fun notice(text: TextBlock, result: TownSessionResult): TownSession =
        copy(step = TownSessionStep.Notice(text, menuStep()), lastResult = result)

    private fun menuStep(): TownSessionStep.Menu =
        TownSessionStep.Menu(town, availableTownActions(town, townSystem))

    companion object {
        fun start(
            town: TownModel,
            townSystem: TownSystemModel,
            scenes: List<SceneModel>,
            party: PartyState,
            currentHour: Int,
            itemRecords: List<ItemRecordModel> = ItemCatalog.all,
        ): TownSession =
            TownSession(
                town = town,
                townSystem = townSystem,
                party = party,
                currentHour = currentHour,
                shopServices = scenes.shopServicesByBuilding(),
                serviceScenes = scenes.associateBy { scene -> scene.id },
                itemRecords = itemRecords,
            )
    }
}

sealed interface ShopServiceStep {
    data class Intro(val text: TextBlock) : ShopServiceStep
    data class MainMenu(val text: TextBlock, val canBuy: Boolean, val canSell: Boolean) : ShopServiceStep
    data class BuyItem(val prompt: TextBlock, val catalogs: List<ShopCatalogModel>) : ShopServiceStep
    data class Quantity(val offer: ShopItemOfferModel, val prompt: TextBlock) : ShopServiceStep
    data class ConfirmPurchase(
        val offer: ShopItemOfferModel,
        val quantity: Int,
        val unitPrice: Int,
        val totalPrice: Int,
        val prompt: TextBlock,
    ) : ShopServiceStep
    data class DropSlot(
        val offer: ShopItemOfferModel,
        val quantity: Int,
        val prompt: TextBlock,
        val result: ShopTransactionResult,
    ) : ShopServiceStep
    data class SellSlot(val prompt: TextBlock) : ShopServiceStep
    data class ConfirmSale(val quote: ShopSellQuoteResult, val prompt: TextBlock) : ShopServiceStep
    data class Finished(val text: TextBlock) : ShopServiceStep
}

data class ShopServiceSession(
    val shop: ShopServiceModel,
    val party: PartyState,
    val step: ShopServiceStep,
    val lastTransaction: ShopTransactionResult? = null,
    val lastSellQuote: ShopSellQuoteResult? = null,
    val itemRecords: List<ItemRecordModel> = ItemCatalog.all,
) {
    private fun interpreter(): ShopServiceInterpreter = ShopServiceInterpreter(itemRecords)
    private fun mainMenu(): ShopServiceStep =
        ShopServiceStep.MainMenu(shop.menu, canBuy = shop.buy != null, canSell = shop.sell != null)

    fun continueIntro(): ShopServiceSession {
        require(step is ShopServiceStep.Intro) { "shop session is not showing intro" }
        return copy(step = mainMenu(), lastTransaction = null, lastSellQuote = null)
    }

    fun chooseMain(choice: ShopMainChoice): ShopServiceSession {
        require(step is ShopServiceStep.MainMenu) { "shop session is not at main menu" }
        return when (choice) {
            ShopMainChoice.Buy -> {
                val buy = checkNotNull(shop.buy) { "shop ${shop.building.slug} has no buy section" }
                copy(step = ShopServiceStep.BuyItem(buy.prompt, buy.catalogs), lastTransaction = null, lastSellQuote = null)
            }
            ShopMainChoice.Sell -> {
                val sell = checkNotNull(shop.sell) { "shop ${shop.building.slug} has no sell section" }
                copy(step = ShopServiceStep.SellSlot(sell.prompt), lastTransaction = null, lastSellQuote = null)
            }
            ShopMainChoice.Leave -> copy(step = ShopServiceStep.Finished(shop.goodbye))
        }
    }

    fun chooseBuyItem(item: ItemId): ShopServiceSession {
        require(step is ShopServiceStep.BuyItem) { "shop session is not choosing a buy item" }
        val buy = checkNotNull(shop.buy) { "shop ${shop.building.slug} has no buy section" }
        val offer = buy.offerFor(item)
            ?: return copy(
                step = mainMenu(),
                lastTransaction = ShopTransactionResult(ShopTransactionStatus.ItemNotSoldHere, party, item),
            )
        val purchase = offer.purchase
        return if (purchase is ShopPurchaseModel.Resource) {
            copy(step = ShopServiceStep.Quantity(offer, purchase.quantityPrompt), lastTransaction = null, lastSellQuote = null)
        } else {
            purchaseStep(offer, quantity = 1)
        }
    }

    fun enterQuantity(quantity: Int): ShopServiceSession {
        val current = step as? ShopServiceStep.Quantity
            ?: error("shop session is not asking for a quantity")
        return purchaseStep(current.offer, quantity)
    }

    fun confirmPurchase(accept: Boolean): ShopServiceSession {
        val current = step as? ShopServiceStep.ConfirmPurchase
            ?: error("shop session is not confirming a purchase")
        val buy = checkNotNull(shop.buy) { "shop ${shop.building.slug} has no buy section" }
        if (!accept) {
            return copy(
                step = mainMenu(),
                lastTransaction = ShopTransactionResult(
                    status = ShopTransactionStatus.Declined,
                    party = party,
                    item = current.offer.item,
                    quantity = current.quantity,
                ),
            )
        }

        val result = interpreter().buy(shop, party, current.offer.item, current.quantity)
        return if (result.status == ShopTransactionStatus.NeedsDropSlot) {
            copy(step = ShopServiceStep.DropSlot(current.offer, current.quantity, buy.cantCarry, result))
        } else {
            copy(step = mainMenu(), party = result.party, lastTransaction = result)
        }
    }

    fun chooseDropSlot(slot: Int): ShopServiceSession {
        val current = step as? ShopServiceStep.DropSlot
            ?: error("shop session is not asking for a drop slot")
        val result = interpreter().buy(shop, party, current.offer.item, current.quantity, dropSlot = slot)
        return if (result.status == ShopTransactionStatus.Bought) {
            copy(step = mainMenu(), party = result.party, lastTransaction = result)
        } else {
            copy(step = current.copy(result = result), lastTransaction = result)
        }
    }

    fun chooseSellSlot(slot: Int, bargainRolls: List<Int> = emptyList()): ShopServiceSession {
        require(step is ShopServiceStep.SellSlot) { "shop session is not choosing a sell slot" }
        val sell = checkNotNull(shop.sell) { "shop ${shop.building.slug} has no sell section" }
        val quote = interpreter().sellQuote(shop, party, slot, bargainRolls)
        return if (quote.status == ShopSellQuoteStatus.Quoted) {
            copy(step = ShopServiceStep.ConfirmSale(quote, sell.offer), lastSellQuote = quote)
        } else {
            copy(step = mainMenu(), lastSellQuote = quote)
        }
    }

    fun confirmSale(accept: Boolean): ShopServiceSession {
        val current = step as? ShopServiceStep.ConfirmSale
            ?: error("shop session is not confirming a sale")
        val result = if (accept) {
            interpreter().sell(shop, party, checkNotNull(current.quote.slot), bargainRolls = current.quote.bargainRolls)
        } else {
            ShopTransactionResult(
                status = ShopTransactionStatus.Declined,
                party = party,
                item = current.quote.item,
                slot = current.quote.slot,
            )
        }
        return copy(step = mainMenu(), party = result.party, lastTransaction = result)
    }

    private fun purchaseStep(offer: ShopItemOfferModel, quantity: Int): ShopServiceSession {
        val buy = checkNotNull(shop.buy) { "shop ${shop.building.slug} has no buy section" }
        val unitPrice = interpreter().unitPrice(offer, party)
        return copy(
            step = ShopServiceStep.ConfirmPurchase(
                offer = offer,
                quantity = quantity,
                unitPrice = unitPrice,
                totalPrice = unitPrice * quantity,
                prompt = buy.payPrompt,
            ),
            lastTransaction = null,
            lastSellQuote = null,
        )
    }

    companion object {
        fun start(
            shop: ShopServiceModel,
            party: PartyState,
            itemRecords: List<ItemRecordModel> = ItemCatalog.all,
        ): ShopServiceSession =
            ShopServiceSession(
                shop = shop,
                party = party,
                step = ShopServiceStep.Intro(shop.intro),
                itemRecords = itemRecords,
            )
    }
}

fun applyShopInput(session: ShopServiceSession, input: ShopServiceInput): ShopServiceSession =
    when (input) {
        ShopServiceInput.ContinueIntro -> session.continueIntro()
        is ShopServiceInput.Main -> session.chooseMain(input.choice)
        is ShopServiceInput.BuyItem -> session.chooseBuyItem(input.item)
        is ShopServiceInput.Quantity -> session.enterQuantity(input.quantity)
        is ShopServiceInput.ConfirmPurchase -> session.confirmPurchase(input.accept)
        is ShopServiceInput.DropSlot -> session.chooseDropSlot(input.slot)
        is ShopServiceInput.SellSlot -> session.chooseSellSlot(input.slot, input.bargainRolls)
        is ShopServiceInput.ConfirmSale -> session.confirmSale(input.accept)
    }

fun List<SceneModel>.shopServicesByBuilding(): Map<BuildingId, ShopServiceModel> =
    flatMap { scene -> scene.ops.filterIsInstance<Op.ShopService>().map { it.shop } }
        .associateBy { shop -> shop.building }

fun BuildingServiceModel.isOpenAt(hour: Int): Boolean {
    val opensAt = opensAt ?: return true
    val closesAt = closesAt ?: return true
    val current = hour.floorMod(24)
    return if (opensAt <= closesAt) {
        current in opensAt..closesAt
    } else {
        current >= opensAt || current <= closesAt
    }
}

private fun availableTownActions(
    town: TownModel,
    townSystem: TownSystemModel,
): List<TownMenuActionModel> =
    townSystem.menu.filter { action -> action.endsMenu || action.building?.let(town::offers) == true }

private fun TownModel.offers(building: BuildingId): Boolean =
    services.any { it.building == building }

private fun Int.floorMod(divisor: Int): Int =
    ((this % divisor) + divisor) % divisor

class ShopServiceInterpreter(
    itemRecords: List<ItemRecordModel> = ItemCatalog.all,
) {
    private val itemRecordById = itemRecords.associateBy { it.id }

    fun buy(
        shop: ShopServiceModel,
        party: PartyState,
        item: ItemId,
        quantity: Int = 1,
        dropSlot: Int? = null,
    ): ShopTransactionResult {
        val buy = shop.buy ?: return ShopTransactionResult(ShopTransactionStatus.ItemNotSoldHere, party, item)
        val offer = buy.offerFor(item)
            ?: return ShopTransactionResult(ShopTransactionStatus.ItemNotSoldHere, party, item)
        val unitPrice = unitPrice(offer, party)

        return when (val purchase = offer.purchase) {
            ShopPurchaseModel.InventoryItem -> buyInventoryItem(buy, party, offer.item, unitPrice, dropSlot)
            is ShopPurchaseModel.Resource -> buyResource(party, offer.item, purchase, unitPrice, quantity)
        }
    }

    fun sell(
        shop: ShopServiceModel,
        party: PartyState,
        slot: Int,
        accept: Boolean = true,
        bargainRolls: List<Int> = emptyList(),
    ): ShopTransactionResult {
        val quote = sellQuote(shop, party, slot, bargainRolls)
        val item = quote.item
        val offerPrice = quote.price
        when (quote.status) {
            ShopSellQuoteStatus.EmptySlot ->
                return ShopTransactionResult(ShopTransactionStatus.EmptySlot, party, slot = slot)
            ShopSellQuoteStatus.ItemNotAcceptedHere ->
                return ShopTransactionResult(ShopTransactionStatus.ItemNotAcceptedHere, party, item, slot = slot)
            ShopSellQuoteStatus.Declined ->
                return ShopTransactionResult(ShopTransactionStatus.Declined, party, item, slot = slot)
            ShopSellQuoteStatus.Quoted -> Unit
        }
        if (!accept) {
            return ShopTransactionResult(ShopTransactionStatus.Declined, party, item, slot = slot)
        }

        val updated = party
            .withInventoryItem(slot, null)
            .withGold(party.resources.gold + offerPrice)
        return ShopTransactionResult(
            status = ShopTransactionStatus.Sold,
            party = updated,
            item = item,
            goldDelta = offerPrice,
            slot = slot,
        )
    }

    fun sellQuote(
        shop: ShopServiceModel,
        party: PartyState,
        slot: Int,
        bargainRolls: List<Int> = emptyList(),
    ): ShopSellQuoteResult {
        val sell = shop.sell ?: return ShopSellQuoteResult(ShopSellQuoteStatus.ItemNotAcceptedHere, party, slot = slot)
        val item = party.inventoryItem(slot)
            ?: return ShopSellQuoteResult(ShopSellQuoteStatus.EmptySlot, party, slot = slot)
        if (!sell.accepts(item)) {
            return ShopSellQuoteResult(ShopSellQuoteStatus.ItemNotAcceptedHere, party, item, slot)
        }

        val offerPrice = bargainPrice(sell, item, party, bargainRolls)
            ?: return ShopSellQuoteResult(ShopSellQuoteStatus.Declined, party, item, slot, bargainRolls = bargainRolls)
        return ShopSellQuoteResult(
            status = ShopSellQuoteStatus.Quoted,
            party = party,
            item = item,
            slot = slot,
            price = offerPrice,
            bargainRolls = bargainRolls,
        )
    }

    fun unitPrice(offer: ShopItemOfferModel, party: PartyState): Int =
        price(offer.price, offer.item, party)

    private fun buyInventoryItem(
        buy: ShopBuyModel,
        party: PartyState,
        item: ItemId,
        unitPrice: Int,
        dropSlot: Int?,
    ): ShopTransactionResult {
        if (party.resources.gold < unitPrice) {
            return ShopTransactionResult(ShopTransactionStatus.NotEnoughGold, party, item, goldDelta = -unitPrice)
        }
        return when (val placed = party.placeInventoryItem(item, buy.inventoryApplication.overflow, dropSlot)) {
            is InventoryPlacementResult.Placed -> ShopTransactionResult(
                status = ShopTransactionStatus.Bought,
                party = placed.party.withGold(placed.party.resources.gold - unitPrice),
                item = item,
                goldDelta = -unitPrice,
                slot = placed.slot,
            )
            is InventoryPlacementResult.NeedsDropSlot -> ShopTransactionResult(
                status = ShopTransactionStatus.NeedsDropSlot,
                party = placed.party,
                item = item,
            )
            is InventoryPlacementResult.Full -> ShopTransactionResult(
                status = ShopTransactionStatus.InventoryFull,
                party = placed.party,
                item = item,
            )
            is InventoryPlacementResult.InvalidDropSlot -> ShopTransactionResult(
                status = ShopTransactionStatus.InvalidDropSlot,
                party = placed.party,
                item = item,
                slot = placed.slot,
            )
        }
    }

    private fun buyResource(
        party: PartyState,
        item: ItemId,
        purchase: ShopPurchaseModel.Resource,
        unitPrice: Int,
        requestedQuantity: Int,
    ): ShopTransactionResult {
        if (requestedQuantity < 0) {
            return ShopTransactionResult(ShopTransactionStatus.InvalidQuantity, party, item, quantity = requestedQuantity)
        }

        val quantity = minOf(requestedQuantity, party.remainingCapacity(purchase.cap))
        if (quantity == 0 && purchase.zeroQuantityDeal == ShopZeroQuantityDealPolicy.NonZeroOnly) {
            return ShopTransactionResult(ShopTransactionStatus.InvalidQuantity, party, item, quantity = 0)
        }

        val total = unitPrice * quantity
        if (party.resources.gold < total) {
            return ShopTransactionResult(ShopTransactionStatus.NotEnoughGold, party, item, goldDelta = -total)
        }

        val updated = party
            .withGold(party.resources.gold - total)
            .withResource(purchase.resource, quantity)
        return ShopTransactionResult(
            status = ShopTransactionStatus.Bought,
            party = updated,
            item = item,
            goldDelta = -total,
            quantity = quantity,
        )
    }

    private fun bargainPrice(
        sell: ShopSellModel,
        item: ItemId,
        party: PartyState,
        rolls: List<Int>,
    ): Int? {
        var current = price(sell.price, item, party)
        if (sell.bargain != ShopSellBargainPolicy.SmithHaggle) return current

        rolls.forEach { roll ->
            if (roll > 90) current += current / 4
            if (roll > 75) current += current / 6
            if (roll < 76) return null
        }
        return current
    }

    private fun ShopSellModel.accepts(item: ItemId): Boolean {
        if (item in rejectedItems) return false
        if (item in acceptedItems) return true
        return itemRecordById[item]?.kind in acceptedKinds
    }

    private fun price(model: ShopPriceModel, item: ItemId, party: PartyState): Int =
        when (model) {
            ShopPriceModel.ItemCost -> itemRecord(item).cost
            ShopPriceModel.HalfItemCost -> itemRecord(item).cost / 2
            is ShopPriceModel.Fixed -> model.amount
            is ShopPriceModel.LeaderLevelPlus -> party.leaderLevel + model.addend
            is ShopPriceModel.ItemCostPlusLeaderLevelTimes -> itemRecord(item).cost + party.leaderLevel * model.multiplier
        }

    private fun itemRecord(item: ItemId): ItemRecordModel =
        checkNotNull(itemRecordById[item]) { "unknown item ${item.slug}" }
}

fun ShopBuyModel.offers(): List<ShopItemOfferModel> =
    catalogs.flatMap { catalog -> catalog.pages.flatMap { page -> page.entries } }

fun ShopBuyModel.offerFor(item: ItemId): ShopItemOfferModel? =
    offers().firstOrNull { it.item == item }

fun PartyState.placeInventoryItem(
    item: ItemId,
    overflow: RewardOverflowPolicy = RewardOverflowPolicy.PromptDropThenPlace,
    dropSlot: Int? = null,
): InventoryPlacementResult {
    firstFreeInventorySlot()?.let { slot ->
        return InventoryPlacementResult.Placed(withInventoryItem(slot, item), slot)
    }

    if (overflow == RewardOverflowPolicy.SkipIfInventoryFull) {
        return InventoryPlacementResult.Full(this)
    }

    val slot = dropSlot ?: return InventoryPlacementResult.NeedsDropSlot(this)
    if (slot !in 1..InventorySaveSlotMax || inventoryItem(slot) == null) {
        return InventoryPlacementResult.InvalidDropSlot(this, slot)
    }
    return InventoryPlacementResult.Placed(withInventoryItem(slot, item), slot)
}

fun PartyState.equipFromInventory(
    memberNumber: Int,
    slot: Int,
    kind: EquipmentKind,
    itemRecords: List<ItemRecordModel> = ItemCatalog.all,
): EquipmentActionResult {
    val memberIndex = memberNumber - 1
    val member = members.getOrNull(memberIndex)
        ?: return EquipmentActionResult.InvalidMember(this, memberNumber)
    if (member.dead) return EquipmentActionResult.DeadMember(this, memberNumber)
    if (slot !in 1..InventorySaveSlotMax) return EquipmentActionResult.InvalidInventorySlot(this, slot)

    val item = inventoryItem(slot)
        ?: return EquipmentActionResult.WrongItemKind(this, slot, null)
    val record = itemRecords.associateBy { it.id }[item]
        ?: return EquipmentActionResult.WrongItemKind(this, slot, item)
    if (!record.matches(kind)) {
        return EquipmentActionResult.WrongItemKind(this, slot, item)
    }

    val oldEquipped = member.equipment.item(kind)
    val updatedMember = member.withEquipmentItem(kind, item)
    val updatedInventoryItem = oldEquipped.takeUnless { it == Items.none }
    return EquipmentActionResult.Equipped(
        party = withMember(memberIndex, updatedMember).withInventoryItem(slot, updatedInventoryItem),
        memberNumber = memberNumber,
        inventorySlot = slot,
    )
}

fun PartyState.unequipToInventory(
    memberNumber: Int,
    kind: EquipmentKind,
): EquipmentActionResult {
    val memberIndex = memberNumber - 1
    val member = members.getOrNull(memberIndex)
        ?: return EquipmentActionResult.InvalidMember(this, memberNumber)
    val equipped = member.equipment.item(kind)
    if (equipped == Items.none) {
        return EquipmentActionResult.NothingEquipped(this, memberNumber, kind)
    }

    val slot = firstFreeInventorySlot()
        ?: return EquipmentActionResult.InventoryFull(this)
    val updated = withMember(memberIndex, member.withEquipmentItem(kind, Items.none))
        .withInventoryItem(slot, equipped)
    return EquipmentActionResult.Unequipped(updated, memberNumber, slot)
}

fun EquipmentCommand.prompt(): EquipmentPromptModel =
    when (this) {
        EquipmentCommand.ReadyWeapon -> EquipmentPromptModel(
            command = this,
            kind = EquipmentKind.Weapon,
            memberFilter = MemberChoiceFilter.AliveOnly,
            inventoryFilter = InventorySlotChoiceFilter.NonEmpty,
        )
        EquipmentCommand.ReadyArmor -> EquipmentPromptModel(
            command = this,
            kind = EquipmentKind.Armor,
            memberFilter = MemberChoiceFilter.AliveOnly,
            inventoryFilter = InventorySlotChoiceFilter.NonEmpty,
        )
        EquipmentCommand.RemoveWeapon -> EquipmentPromptModel(
            command = this,
            kind = EquipmentKind.Weapon,
            memberFilter = MemberChoiceFilter.Any,
            inventoryFilter = null,
        )
        EquipmentCommand.RemoveArmor -> EquipmentPromptModel(
            command = this,
            kind = EquipmentKind.Armor,
            memberFilter = MemberChoiceFilter.Any,
            inventoryFilter = null,
        )
    }

fun EquipmentCommand.townLeavePrompt(): TextBlock =
    when (this) {
        EquipmentCommand.ReadyWeapon -> EquipmentPromptText.readyWeapons
        EquipmentCommand.ReadyArmor -> EquipmentPromptText.readyArmors
        EquipmentCommand.RemoveWeapon,
        EquipmentCommand.RemoveArmor -> error("$this is not a town-leave ready command")
    }

fun PartyState.applyEquipmentCommand(
    command: EquipmentCommand,
    memberNumber: Int,
    inventorySlot: Int? = null,
): EquipmentActionResult =
    when (command) {
        EquipmentCommand.ReadyWeapon -> equipFromInventory(memberNumber, inventorySlot ?: 0, EquipmentKind.Weapon)
        EquipmentCommand.ReadyArmor -> equipFromInventory(memberNumber, inventorySlot ?: 0, EquipmentKind.Armor)
        EquipmentCommand.RemoveWeapon -> unequipToInventory(memberNumber, EquipmentKind.Weapon)
        EquipmentCommand.RemoveArmor -> unequipToInventory(memberNumber, EquipmentKind.Armor)
    }

val EquipmentActionResult.party: PartyState
    get() = when (this) {
        is EquipmentActionResult.Equipped -> party
        is EquipmentActionResult.Unequipped -> party
        is EquipmentActionResult.InvalidMember -> party
        is EquipmentActionResult.DeadMember -> party
        is EquipmentActionResult.InvalidInventorySlot -> party
        is EquipmentActionResult.WrongItemKind -> party
        is EquipmentActionResult.NothingEquipped -> party
        is EquipmentActionResult.InventoryFull -> party
    }

fun PartyState.needsTownLeaveReadyPrompt(
    command: EquipmentCommand,
    itemRecords: List<ItemRecordModel> = ItemCatalog.all,
): Boolean =
    when (command) {
        EquipmentCommand.ReadyWeapon ->
            hasInventoryEquipment(EquipmentKind.Weapon, itemRecords) && hasLivingMemberWithout(EquipmentKind.Weapon)
        EquipmentCommand.ReadyArmor ->
            hasInventoryEquipment(EquipmentKind.Armor, itemRecords) && hasLivingMemberWithout(EquipmentKind.Armor)
        EquipmentCommand.RemoveWeapon,
        EquipmentCommand.RemoveArmor -> false
    }

fun PartyState.consumeTorch(): TorchActionResult =
    if (resources.torches <= 0) {
        TorchActionResult.NoTorches(this)
    } else {
        TorchActionResult.Consumed(copy(resources = resources.copy(torches = resources.torches - 1)))
    }

fun PartyState.tryDungeonDescent(currentDepth: Int, maxDepth: Int): DungeonDescentResult {
    require(currentDepth >= 0) { "current dungeon depth must be non-negative" }
    require(maxDepth >= 0) { "max dungeon depth must be non-negative" }
    if (resources.torches <= 0) return DungeonDescentResult.NoTorches(this)
    if (currentDepth >= maxDepth) return DungeonDescentResult.AtMaxDepth(this)
    return when (val result = consumeTorch()) {
        is TorchActionResult.Consumed -> DungeonDescentResult.Descended(result.party)
        is TorchActionResult.NoTorches -> DungeonDescentResult.NoTorches(result.party)
    }
}

fun PartyState.inventoryItem(slot: Int): ItemId? =
    inventory.firstOrNull { it.slot == slot }?.item

fun PartyState.firstFreeInventorySlot(): Int? =
    (1..InventorySaveSlotMax).firstOrNull { inventoryItem(it) == null }

fun PartyState.withInventoryItem(slot: Int, item: ItemId?): PartyState {
    require(slot in 0..InventorySaveSlotMax) { "inventory slot $slot outside 0..$InventorySaveSlotMax" }
    val slots = inventory.associateBy { it.slot }.toMutableMap()
    val previous = slots[slot]?.item
    slots[slot] = InventorySlotState(slot, item)
    val updated = copy(inventory = slots.values.sortedBy { it.slot })
    return if (previous == Items.donkey && item != Items.donkey) {
        updated.withFoodClampedToDonkeyCapacity()
    } else {
        updated
    }
}

fun PartyState.foodCapacityByDonkeys(): Int =
    150 * (countInventoryItem(Items.donkey) + 1)

fun PartyState.hasInventoryItem(item: ItemId): Boolean =
    inventory.any { it.slot in 1..InventorySaveSlotMax && it.item == item }

fun PartyState.hasInventoryEquipment(
    kind: EquipmentKind,
    itemRecords: List<ItemRecordModel> = ItemCatalog.all,
): Boolean {
    val records = itemRecords.associateBy { it.id }
    return inventory.any { slot ->
        slot.slot in 1..InventorySaveSlotMax && slot.item?.let { records[it]?.matches(kind) } == true
    }
}

fun PartyState.hasLivingMemberWithout(kind: EquipmentKind): Boolean =
    members.any { member -> !member.dead && member.equipment.item(kind) == Items.none }

fun PartyState.campGearEffect(): CampGearEffect {
    val hasTent = hasInventoryItem(Items.tent)
    val hasBlanket = hasInventoryItem(Items.blanket)
    return CampGearEffect(
        image = if (hasTent) Images.Scene.tent else Images.Scene.noTent,
        protectsLowTemperature = hasBlanket || hasTent,
        protectsVeryLowTemperature = hasTent,
    )
}

private fun PartyState.withGold(gold: Int): PartyState =
    copy(resources = resources.copy(gold = gold.coerceAtLeast(0)))

private fun PartyState.withResource(resource: ShopResourceKind, quantity: Int): PartyState =
    when (resource) {
        ShopResourceKind.Torches -> copy(resources = resources.copy(torches = resources.torches + quantity))
        ShopResourceKind.Food -> copy(resources = resources.copy(food = resources.food + quantity))
    }

private fun PartyState.withMember(index: Int, member: PartyMemberState): PartyState =
    copy(members = members.mapIndexed { i, existing -> if (i == index) member else existing })

private val PartyState.leaderLevel: Int
    get() = members.firstOrNull()?.level ?: 0

private fun PartyState.remainingCapacity(cap: ShopQuantityCap): Int =
    when (cap) {
        ShopQuantityCap.TorchLimit80 -> 80 - resources.torches
        ShopQuantityCap.FoodByDonkeys -> foodCapacityByDonkeys() - resources.food
    }.coerceAtLeast(0)

fun PartyState.countInventoryItem(item: ItemId): Int =
    inventory.count { it.slot in 1..InventorySaveSlotMax && it.item == item }

private fun PartyState.withFoodClampedToDonkeyCapacity(): PartyState {
    val cap = foodCapacityByDonkeys()
    return if (resources.food <= cap) this else copy(resources = resources.copy(food = cap))
}

private fun ItemRecordModel.matches(kind: EquipmentKind): Boolean =
    when (kind) {
        EquipmentKind.Weapon -> this.kind == ItemKind.Weapon
        EquipmentKind.Armor -> this.kind == ItemKind.Armor
    }

private fun EquipmentState.item(kind: EquipmentKind): ItemId =
    when (kind) {
        EquipmentKind.Weapon -> weapon
        EquipmentKind.Armor -> armor
    }

private fun PartyMemberState.withEquipmentItem(kind: EquipmentKind, item: ItemId): PartyMemberState =
    copy(
        equipment = when (kind) {
            EquipmentKind.Weapon -> equipment.copy(weapon = item)
            EquipmentKind.Armor -> equipment.copy(armor = item)
        },
    )
