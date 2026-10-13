/**
 * Direct compiled-table shop service for the runtime.
 *
 * This file is the first town host service that does real gameplay work
 * without delegating to the old Kotlin `ShopServiceSession`. It consumes the
 * compiled `ShopService` operand, item table, input bindings, party image, and
 * runtime-system realm constants directly.
 *
 * Shop buy/sell prompts are runtime service loops, not authored script-control
 * loops. The service owns live input, catalog paging, inventory mutation, and
 * price RNG while labels, text, bindings, catalog rows, policies, and formulas
 * remain authored data in the compiled bundle.
 */

package rylween.runtime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledInputBinding
import rylween.compiled.CompiledInventorySlot
import rylween.compiled.CompiledItemRecord
import rylween.compiled.CompiledRuntimeTextPart
import rylween.compiled.CompiledShopBuy
import rylween.compiled.CompiledShopCatalog
import rylween.compiled.CompiledShopCatalogPage
import rylween.compiled.CompiledShopItemOffer
import rylween.compiled.CompiledShopPrice
import rylween.compiled.CompiledShopPurchase
import rylween.compiled.CompiledShopSell
import rylween.compiled.CompiledShopService
import rylween.compiled.CompiledTextBlock
import rylween.compiled.ExternalRuntimeSystems
import rylween.compiled.ExternalRuntimeTextBlock
import rylween.dsl.AudioCue
import rylween.dsl.impl.Resource
import rylween.dsl.impl.RewardOverflowPolicy
import rylween.dsl.impl.ShopQuantityCap
import rylween.dsl.impl.ShopZeroQuantityDealPolicy
import kotlin.math.max

/** Runs one compiled shop service against the runtime party model. */
internal class ShopService(
    private val content: CompiledContentBundle,
    private val systems: ExternalRuntimeSystems,
    private val input: RuntimeInputPort,
    private val screen: RuntimeScreenPort,
    private val log: RuntimeLog,
) {
    private val realm = systems.realm
    private val itemsById: Map<CompiledHandleRef, CompiledItemRecord> = content.items.associateBy { it.id }
    private val pausePrompts = PausePrompts(systems.prompts)
    private val worldText = RuntimeWorldText(content)
    private val audio = RuntimeAudio(systems.audio, screen)

    fun run(
        shop: CompiledShopService,
        runtime: RuntimeState,
    ): RuntimeState {
        var current = runtime
        screen.showImage(content.slug(shop.image))
        val homeLines = shopHomeLines(shop)
        current = runInterludeGate(shop, current).runtime

        while (true) {
            screen.updateStatus(current)
            screen.writeText(homeLines)
            val choice = input.readCommand() ?: return current
            val buy = shop.buy
            val sell = shop.sell
            when {
                shop.keys.leave.matchesInput(content, choice) -> {
                    return current
                }

                choice == '!' -> screen.redrawCurrentView(homeLines)

                buy != null && shop.keys.buy.matchesInput(content, choice) -> {
                    current = runBuy(buy, current)
                }

                sell != null && shop.keys.sell.matchesInput(content, choice) -> {
                    current = runSell(sell, current)
                }

                else -> {
                    log.event("shop.invalid_choice building=${content.slug(shop.building)} key=$choice")
                }
            }
        }
    }

    private fun shopHomeLines(shop: CompiledShopService): List<String> =
        screen.choiceMenuLines(shop.intro.renderScreenTextLines(), shop.menu.renderScreenTextLines())

    private fun CompiledTextBlock.renderScreenTextLines(): List<String> =
        renderTextLines(
            CompiledTextRenderContext(
                content = content,
                flowColumns = screen.textColumns,
            ),
        )

    private fun runInterludeGate(
        shop: CompiledShopService,
        runtime: RuntimeState,
    ): InterludeResult {
        val interlude = shop.interlude ?: return InterludeResult(runtime, ran = false)
        val forceState = interlude.forceFirstUntil
        val forced = forceState != null && forceState !in runtime.party.flags
        var current = runtime
        val shouldRun =
            if (forced) {
                log.event("shop.interlude.forced building=${content.slug(shop.building)}")
                true
            } else {
                val draw = runtime.party.drawRandom(0, 99)
                current = runtime.withParty(draw.party)
                val hit = draw.value < interlude.chancePercent
                log.rngDraw(
                    context = "shop.interlude",
                    lo = 0,
                    hi = 99,
                    value = draw.value,
                    hit = hit,
                    details =
                        mapOf(
                            "building" to content.slug(shop.building),
                            "threshold_lt" to interlude.chancePercent,
                        ),
                )
                hit
            }
        if (!shouldRun) return InterludeResult(current, ran = false)
        if (forceState != null) {
            current = current.withParty(current.party.copy(flags = (current.party.flags + forceState).distinct()))
        }
        return InterludeResult(runInterlude(shop, current), ran = true)
    }

    private fun runInterlude(
        shop: CompiledShopService,
        runtime: RuntimeState,
    ): RuntimeState {
        val interlude = shop.interlude ?: return runtime
        screen.updateStatus(runtime)
        val lines =
            (shop.intro.renderScreenTextLines() + interlude.offer.renderScreenTextLines()).withChoiceGap() +
                listOf(
                    "(${interlude.accept.primaryInputLabel(content)})ccept",
                    "(${interlude.decline.primaryInputLabel(content)})ecline",
                )
        screen.writeText(lines)
        val choice =
            input.readValidChoice(screen, lines, audio) { candidate ->
                interlude.accept.matchesInput(content, candidate) || interlude.decline.matchesInput(content, candidate)
            } ?: return runtime
        if (!interlude.accept.matchesInput(content, choice)) {
            writeTextAndWait(interlude.declined.renderScreenTextLines())
            return runtime
        }

        interlude
            .accepted
            .runningFold(emptyList<String>()) { lines, text ->
                lines.withInterludePage(text.renderScreenTextLines())
            }.drop(1)
            .forEach { lines ->
                screen.writeText(lines)
                input.pause(systems.pacing.shop.interludePagePauseMs)
            }
        if (!interlude.runsRumorAfterAccept) return runtime
        val rumor = rumorLines(runtime)
        writeTextAndWait(rumor.lines)
        return rumor.runtime
    }

    private fun runBuy(
        buy: CompiledShopBuy,
        runtime: RuntimeState,
    ): RuntimeState {
        val party = runtime.party
        val catalog = chooseCatalog(buy) ?: return runtime
        val offer = chooseOffer(buy, catalog, party) ?: return runtime
        return runtime.withParty(confirmPurchase(buy, offer, party, runtime))
    }

    private fun chooseCatalog(buy: CompiledShopBuy): CompiledShopCatalog? {
        if (buy.catalogs.size == 1) return buy.catalogs.single()
        val promptLines = buy.prompt.renderScreenTextLines()
        val generatedLines =
            buildList {
                buy.catalogs.forEach { catalog ->
                    add(catalog.binding.mnemonicLine(content, content.strings.value(catalog.titleString)))
                }
                add(buy.paymentKeys.decline.mnemonicLine(content, "None"))
            }
        val generatedKeys =
            (buy.catalogs.map { catalog -> catalog.binding } + buy.paymentKeys.decline)
                .map { binding -> binding.primaryInputLabel(content) }
        while (true) {
            screen.writeText(promptLines.withGeneratedChoices(generatedKeys, generatedLines))
            val choice = input.readCommand() ?: return null
            if (buy.paymentKeys.decline.matchesInput(content, choice)) return null
            buy.catalogs.firstOrNull { catalog -> catalog.binding.matchesInput(content, choice) }?.let { return it }
        }
    }

    private fun chooseOffer(
        buy: CompiledShopBuy,
        catalog: CompiledShopCatalog,
        party: PartyState,
    ): CompiledShopItemOffer? {
        var pageIndex = 0
        while (true) {
            val page = catalog.pages[pageIndex]
            showCatalogPage(buy, catalog, page, party)
            val choice = input.readCommand() ?: return null
            val targetPage = catalog.navigationTarget(pageIndex, choice)
            if (targetPage != null) {
                pageIndex = targetPage
                continue
            }
            if (buy.paymentKeys.decline.matchesInput(content, choice)) return null
            page.entries
                .firstOrNull { item -> item.binding.matchesInput(content, choice) }
                ?.let { return it }
        }
    }

    private fun showCatalogPage(
        buy: CompiledShopBuy,
        catalog: CompiledShopCatalog,
        page: CompiledShopCatalogPage,
        party: PartyState,
    ) {
        val lines =
            buildList {
                catalog.intro?.let { addAll(it.renderScreenTextLines().withChoiceGap()) }
                page.entries.forEach { offer ->
                    val label = offer.labelString?.let(content.strings::value) ?: itemName(offer.item)
                    add(formatOfferLine(offer.binding, label, unitPrice(offer, party)))
                }
                page.navigation.previous?.let { add(it.optionLine(content)) }
                page.navigation.next?.let { add(it.optionLine(content)) }
                add(buy.paymentKeys.decline.mnemonicLine(content, "None"))
                page.footer?.let { addAll(it.renderScreenTextLines()) }
            }
        screen.writeText(lines)
    }

    private fun formatOfferLine(
        binding: CompiledInputBinding,
        label: String,
        unitPrice: Int,
    ): String {
        // The price is its own column, so every renderer starts the prices of
        // a catalog page on one column.
        return "(${binding.primaryInputLabel(content).uppercase()}) $label$TEXT_COLUMN_SEPARATOR$unitPrice au"
    }

    private fun CompiledShopCatalog.navigationTarget(
        pageIndex: Int,
        choice: Char,
    ): Int? {
        val page = pages[pageIndex]
        return when {
            page.navigation.previous.matchesChoice(choice) -> (pageIndex - 1).coerceAtLeast(0)
            page.navigation.next.matchesChoice(choice) -> (pageIndex + 1).coerceAtMost(pages.lastIndex)
            else -> null
        }
    }

    private fun CompiledInputBinding?.matchesChoice(choice: Char): Boolean = this?.matchesInput(content, choice) == true

    private fun confirmPurchase(
        buy: CompiledShopBuy,
        offer: CompiledShopItemOffer,
        party: PartyState,
        runtime: RuntimeState,
    ): PartyState {
        val quantity = purchaseQuantity(offer, party) ?: return party
        if (quantity == 0 && offer.purchase.zeroQuantityRequiresNonZero()) {
            val purchase = offer.purchase as CompiledShopPurchase.Bulk
            writeTextAndWait(purchase.tooMany.renderShopLines(carryCap = capacityLimit(party, purchase.cap)))
            return party
        }

        val unitPrice = unitPrice(offer, party)
        val totalPrice = unitPrice * quantity
        val promptLines =
            buildList {
                offer.description?.let { addAll(it.renderShopLines(item = offer.item, price = totalPrice)) }
                addAll(purchasePriceLines(buy, offer, unitPrice, totalPrice).withChoiceGap())
                addAll(buy.payPrompt.renderScreenTextLines())
            }
        var choice: Char
        do {
            screen.writeText(promptLines)
            choice = input.readCommand() ?: return party
            if (buy.paymentKeys.decline.matchesInput(content, choice)) return party
        } while (!buy.paymentKeys.pay.matchesInput(content, choice))
        if (party.resources.gold < totalPrice) {
            audio.cue(AudioCue.NotEnoughGold)
            writeTextAndWait(buy.tooExpensive.renderShopLines(item = offer.item, price = totalPrice))
            return party
        }

        val result = applyPurchase(buy, offer, party, quantity, totalPrice)
        if (result.party.resources.gold < party.resources.gold) audio.cue(AudioCue.GoldSpent)
        screen.updateStatus(runtime.withParty(result.party))
        writeTextAndWait(result.lines)
        return result.party
    }

    private fun purchaseQuantity(
        offer: CompiledShopItemOffer,
        party: PartyState,
    ): Int? {
        val purchase = offer.purchase
        if (purchase !is CompiledShopPurchase.Bulk) return 1
        screen.writeText(purchase.quantityPrompt.renderScreenTextLines())
        val requested = input.readInt() ?: return null
        val remaining = remainingCapacity(party, purchase.cap)
        if (requested > remaining) {
            writeTextAndWait(purchase.tooMany.renderShopLines(carryCap = capacityLimit(party, purchase.cap)))
            return null
        }
        return requested
    }

    private fun applyPurchase(
        buy: CompiledShopBuy,
        offer: CompiledShopItemOffer,
        party: PartyState,
        quantity: Int,
        totalPrice: Int,
    ): ShopResult =
        when (val purchase = offer.purchase) {
            CompiledShopPurchase.InventoryItem -> buyInventoryItem(buy, offer, party, totalPrice)
            is CompiledShopPurchase.Bulk -> buyResource(buy, purchase, party, quantity, totalPrice)
        }

    private fun buyInventoryItem(
        buy: CompiledShopBuy,
        offer: CompiledShopItemOffer,
        party: PartyState,
        totalPrice: Int,
    ): ShopResult {
        val freeSlot = party.inventory.firstOrNull { slot -> slot.item == null }?.slot
        val targetSlot = freeSlot ?: chooseDropSlot(buy, party)
        if (targetSlot == null) {
            return ShopResult(party, buy.cantCarry.renderScreenTextLines())
        }
        val updated =
            party
                .withInventoryItem(targetSlot, offer.item)
                .withGold(party.resources.gold - totalPrice)
        return ShopResult(
            updated,
            buy.deal.renderScreenTextLines() +
                requireNotNull(buy.bought) { "shop inventory purchases require bought text" }
                    .renderShopLines(item = offer.item),
        )
    }

    private fun chooseDropSlot(
        buy: CompiledShopBuy,
        party: PartyState,
    ): Int? {
        if (buy.inventoryApplication.overflow != RewardOverflowPolicy.PromptDropThenPlace) return null
        return chooseDropSlotForInventoryOverflow(
            systems = systems,
            input = input,
            screen = screen,
            party = party,
            leadLines = buy.cantCarry.renderScreenTextLines(),
            itemName = ::itemName,
        )
    }

    private fun buyResource(
        buy: CompiledShopBuy,
        purchase: CompiledShopPurchase.Bulk,
        party: PartyState,
        quantity: Int,
        totalPrice: Int,
    ): ShopResult {
        if (quantity == 0 && purchase.zeroQuantityDeal == ShopZeroQuantityDealPolicy.NonZeroOnly) {
            return ShopResult(party, purchase.tooMany.renderShopLines(carryCap = capacityLimit(party, purchase.cap)))
        }
        val updated =
            party
                .withResource(purchase.resource, party.resourceAmount(purchase.resource) + quantity)
                .withGold(party.resources.gold - totalPrice)
        return ShopResult(updated, buy.deal.renderScreenTextLines())
    }

    private fun runSell(
        sell: CompiledShopSell,
        runtime: RuntimeState,
    ): RuntimeState {
        val party = runtime.party
        // RETURN leaves, as in every inventory picker: a letter key would shadow the inventory slot it names.
        val promptLines =
            screen.inventoryPickerLines(
                sell.prompt.renderScreenTextLines(),
                party.inventory.inventoryChoiceLines(::itemName),
                listOf(systems.prompts.returnLeavesLine()),
            )
        screen.writeText(promptLines)
        val slot =
            input.readValidInventorySlotChoice(
                screen = screen,
                lines = promptLines,
                validSlots = party.inventory.filter { slot -> slot.item != null }.mapTo(mutableSetOf(), CompiledInventorySlot::slot),
                cancelKeys = setOf('\n'),
            ) ?: return runtime
        val item = party.inventoryItem(slot)
        check(item != null) { "shop sell selected slot $slot was empty after input validation" }
        if (!sell.accepts(item)) {
            writeTextAndWait(sell.wrongKind.renderScreenTextLines())
            return runtime
        }

        return runSellOfferLoop(sell, runtime, slot, item, price(sell.price, item, party))
    }

    private fun runSellOfferLoop(
        sell: CompiledShopSell,
        runtime: RuntimeState,
        slot: Int,
        item: CompiledHandleRef,
        startingPrice: Int,
    ): RuntimeState {
        var current = runtime
        var price = startingPrice
        while (true) {
            val offerLines = sell.offer.renderShopLines(item = item, price = price)
            screen.writeText(offerLines.withChoiceGap() + sellChoiceLines(sell))
            val confirm = input.readCommand() ?: return current
            if (sell.saleKeys.decline.matchesInput(content, confirm)) {
                writeTextAndWait(requireNotNull(sell.declined).renderScreenTextLines())
                return current
            }
            if (sell.haggle != null && sell.saleKeys.haggle.matchesInput(content, confirm)) {
                val quote = haggleQuote(sell, item, price, current.party)
                price =
                    quote.price
                        ?: return current.withParty(quote.party).also {
                            writeTextAndWait(requireNotNull(sell.declined).renderScreenTextLines())
                        }
                current = current.withParty(quote.party)
                continue
            }
            if (!sell.saleKeys.accept.matchesInput(content, confirm)) continue
            val updated =
                current.party
                    .withInventoryItem(slot, null)
                    .withGold(current.party.resources.gold + price)
            val soldLines = requireNotNull(sell.sold).renderScreenTextLines()
            audio.cue(AudioCue.GoldGained)
            screen.updateStatus(current.withParty(updated))
            writeTextAndWait(soldLines)
            return current.withParty(updated)
        }
    }

    private fun sellChoiceLines(sell: CompiledShopSell): List<String> =
        buildList {
            add(listOf(sell.saleKeys.accept, sell.saleKeys.decline).joinToString(" / ") { key -> key.optionLine(content) })
            if (sell.haggle != null) add(sell.saleKeys.haggle.optionLine(content))
        }

    private fun writeTextAndWait(lines: List<String>) {
        screen.writeText(lines.withPausePrompt(pausePrompts))
        input.readCommand()
    }

    private fun CompiledShopSell.accepts(item: CompiledHandleRef): Boolean {
        if (item in rejectedItems) return false
        if (item in acceptedItems) return true
        return itemRecord(item).kind in acceptedKinds
    }

    private fun List<String>.withInterludePage(page: List<String>): List<String> {
        if (isEmpty()) return page
        return this + "" + page
    }

    private fun List<String>.withGeneratedChoices(
        keys: List<String>,
        generatedLines: List<String>,
    ): List<String> = takeIf { lines -> lines.alreadyListChoices(keys) } ?: screen.choiceMenuLines(this, generatedLines)

    private fun List<String>.alreadyListChoices(keys: List<String>): Boolean = keys.all { key -> any { line -> line.offersChoiceKey(key) } }

    private fun purchasePriceLines(
        buy: CompiledShopBuy,
        offer: CompiledShopItemOffer,
        unitPrice: Int,
        totalPrice: Int,
    ): List<String> {
        val priceText =
            when (offer.purchase) {
                is CompiledShopPurchase.Bulk -> buy.unitPrice
                CompiledShopPurchase.InventoryItem -> buy.singlePrice
            }
        val renderedPrice =
            when (offer.purchase) {
                is CompiledShopPurchase.Bulk -> unitPrice
                CompiledShopPurchase.InventoryItem -> totalPrice
            }
        return requireNotNull(priceText).renderShopLines(item = offer.item, price = renderedPrice)
    }

    private fun unitPrice(
        offer: CompiledShopItemOffer,
        party: PartyState,
    ): Int = price(offer.price, offer.item, party)

    private fun haggleQuote(
        sell: CompiledShopSell,
        item: CompiledHandleRef,
        price: Int,
        party: PartyState,
    ): SellQuote {
        var current = price
        val haggle = sell.haggle ?: return SellQuote(current, party)
        val draw = party.drawRandom(0, 99)
        haggle.rejectRollBelow?.let { threshold ->
            if (draw.value < threshold) {
                log.rngDraw(
                    context = "shop.sell.haggle",
                    lo = 0,
                    hi = 99,
                    value = draw.value,
                    hit = false,
                    details = mapOf("threshold" to threshold, "item" to content.slug(item), "price" to current),
                )
                return SellQuote(price = null, party = draw.party)
            }
        }
        haggle.bonuses.forEach { bonus ->
            if (draw.value > bonus.rollGreaterThan) {
                current += current / max(1, bonus.addCurrentDivisor)
            }
        }
        log.rngDraw(
            context = "shop.sell.haggle",
            lo = 0,
            hi = 99,
            value = draw.value,
            hit = true,
            details = mapOf("item" to content.slug(item), "price" to current),
        )
        return SellQuote(current, draw.party)
    }

    private fun rumorLines(runtime: RuntimeState): RumorResult {
        val speak = checkNotNull(systems.speak) { "shop interlude rumor requires authored speak runtime systems" }
        val entries =
            speak.textTables.rumorTables
                .firstOrNull { table -> table.id == STANDARD_TOWN_RUMOR_TABLE }
                ?.entries
                ?.filter { entry ->
                    entry.excludedBy.none { state ->
                        flagStateRef("${state.flag}.${state.native}")?.let { ref -> ref in runtime.party.flags } == true
                    }
                }.orEmpty()
        if (entries.isEmpty()) {
            return RumorResult(
                runtime,
                checkNotNull(
                    speak.textTables.screens
                        .firstOrNull { screen -> screen.id == SPEAK_NO_RUMOR_SCREEN }
                        ?.text,
                ) {
                    "shop interlude rumor requires authored no-rumor screen"
                }.renderExternal(runtime),
            )
        }
        val draw = runtime.party.drawRandom(0, entries.lastIndex)
        log.rngDraw("speak.rumor.entry", 0, entries.lastIndex, draw.value)
        val current = runtime.withParty(draw.party)
        return RumorResult(current, entries[draw.value].text.renderExternal(current))
    }

    private fun ExternalRuntimeTextBlock.renderExternal(runtime: RuntimeState): List<String> =
        renderParts(
            values = { part -> runtime.runtimeTextValue(content, part.kind) ?: worldText.value(part, runtime, journal = false) },
            wrapWidth = screen.textColumns,
        )

    private fun flagStateRef(slug: String): CompiledHandleRef? = content.refOrNull(CompiledHandleKind.FlagState, slug)

    private fun price(
        price: CompiledShopPrice,
        item: CompiledHandleRef,
        party: PartyState?,
    ): Int =
        when (price) {
            CompiledShopPrice.ItemCost -> itemRecord(item).cost
            CompiledShopPrice.HalfItemCost -> itemRecord(item).cost / 2
            is CompiledShopPrice.Fixed -> price.amount
            is CompiledShopPrice.LeaderLevelPlus -> (party?.leaderLevel ?: 0) + price.addend
            is CompiledShopPrice.ItemCostPlusLeaderLevelTimes -> itemRecord(item).cost + (party?.leaderLevel ?: 0) * price.multiplier
        }

    private fun remainingCapacity(
        party: PartyState,
        cap: ShopQuantityCap,
    ): Int = (capacityLimit(party, cap) - party.resourceAmount(cap.resource)).coerceAtLeast(0)

    private fun capacityLimit(
        party: PartyState,
        cap: ShopQuantityCap,
    ): Int =
        when (cap) {
            is ShopQuantityCap.FixedAmount -> cap.amount
            is ShopQuantityCap.CarryCapacity -> party.resourceCapacity(cap.resource)
        }

    private fun PartyState.resourceCapacity(resource: Resource): Int {
        val base = realm.baseResourceCapacity[resource] ?: resourceAmount(resource)
        val itemBonus =
            inventory
                .mapNotNull { slot -> slot.item?.let(::itemRecord) }
                .sumOf { item -> item.resourceCapacityBonus[resource] ?: 0 }
        return base + itemBonus
    }

    private fun PartyState.resourceAmount(resource: Resource): Int =
        when (resource) {
            Resource.Food -> resources.food
            Resource.Torches -> resources.torches
        }

    private val PartyState.leaderLevel: Int
        get() = members.firstOrNull()?.level ?: 0

    private fun itemName(item: CompiledHandleRef): String = content.strings.value(itemRecord(item).displayNameString)

    private fun itemRecord(item: CompiledHandleRef): CompiledItemRecord =
        checkNotNull(itemsById[item]) { "unknown compiled item ${content.slug(item)}" }

    private fun CompiledShopPurchase.zeroQuantityRequiresNonZero(): Boolean =
        this is CompiledShopPurchase.Bulk && zeroQuantityDeal == ShopZeroQuantityDealPolicy.NonZeroOnly

    private fun CompiledTextBlock.renderShopLines(
        item: CompiledHandleRef? = null,
        price: Int? = null,
        carryCap: Int? = null,
    ): List<String> =
        renderTextLines(
            CompiledTextRenderContext(
                content = content,
                flowColumns = screen.textColumns,
                runtimeValue = { part ->
                    when (part) {
                        CompiledRuntimeTextPart.OfferedItemName,
                        CompiledRuntimeTextPart.SelectedItemName,
                        -> item?.let(::itemName) ?: "[${part.name}]"

                        CompiledRuntimeTextPart.OfferedItemPrice,
                        CompiledRuntimeTextPart.ShopPrice,
                        -> price?.toString() ?: "[${part.name}]"

                        CompiledRuntimeTextPart.ShopCarryCap -> carryCap?.toString() ?: "[${part.name}]"
                        else -> "[${part.name}]"
                    }
                },
            ),
        )

    private fun ExternalRuntimeTextBlock.renderShopPrompt(): List<String> = render({ null }, screen.textColumns)
}

private const val STANDARD_TOWN_RUMOR_TABLE = "standard_town"
private const val SPEAK_NO_RUMOR_SCREEN = "speak.no_rumor"

private data class InterludeResult(
    val runtime: RuntimeState,
    val ran: Boolean,
)

private data class RumorResult(
    val runtime: RuntimeState,
    val lines: List<String>,
)

private data class ShopResult(
    val party: PartyState,
    val lines: List<String>,
)

private data class SellQuote(
    val price: Int?,
    val party: PartyState,
)

private fun CompiledInputBinding.optionLine(content: CompiledContentBundle): String =
    mnemonicLine(content, content.strings.value(labelString))
