/**
 * Direct compiled-table shop service for the runtime.
 *
 * This file is the first town host service that does real gameplay work
 * without delegating to the old Kotlin `ShopServiceSession`. It consumes the
 * compiled `ShopService` operand, item table, input bindings, party image, and
 * runtime-system realm constants directly.
 */

package rylween.runtime

import rylween.compiled.CompiledCharacter
import rylween.compiled.CompiledCharacterPlacement
import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledInputBinding
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
import rylween.compiled.CompiledWorldPosition
import rylween.compiled.ExternalRuntimeSystems
import rylween.compiled.ExternalRuntimeTextBlock
import rylween.dsl.GridPoint
import rylween.dsl.Resource
import rylween.dsl.RewardOverflowPolicy
import rylween.dsl.ShopQuantityCap
import rylween.dsl.ShopZeroQuantityDealPolicy
import kotlin.math.max

/** Runs one compiled shop service against the runtime party model. */
internal class ShopService(
    private val content: CompiledContentBundle,
    private val systems: ExternalRuntimeSystems,
    private val input: RuntimeInput,
    private val screen: RuntimeScreen,
    private val log: RuntimeLog,
) {
    private val realm = systems.realm
    private val itemsById: Map<CompiledHandleRef, CompiledItemRecord> = content.items.associateBy { it.id }
    private val pausePrompts = PausePrompts(systems.prompts)

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
        shop.intro.renderTextLines(content) + shop.menu.renderTextLines(content)

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
        screen.writeText(
            (shop.intro.renderTextLines(content) + interlude.offer.renderTextLines(content)).withChoiceGap() +
                listOf(
                    "(${interlude.accept.primaryInputLabel(content)})ccept",
                    "(${interlude.decline.primaryInputLabel(content)})ecline",
                ),
        )
        val choice = input.readCommand() ?: return runtime
        if (!interlude.accept.matchesInput(content, choice)) {
            writeTextAndWait(interlude.declined.renderTextLines(content))
            return runtime
        }

        var acceptedLines = emptyList<String>()
        interlude.accepted.forEach { text ->
            if (acceptedLines.isNotEmpty()) acceptedLines += ""
            acceptedLines += text.renderTextLines(content)
            screen.writeText(acceptedLines)
            input.pause(SHOP_INTERLUDE_DELAY_MS)
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
        val promptLines = buy.prompt.renderTextLines(content)
        val generatedLines =
            buildList {
                buy.catalogs.forEach { catalog ->
                    add(catalog.binding.mnemonicLine(content, content.strings.value(catalog.titleString)))
                }
                add(buy.paymentKeys.decline.mnemonicLine(content, "Nothing"))
            }
        while (true) {
            screen.writeText(promptLines.withGeneratedChoices(generatedLines))
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
            showCatalogPage(catalog, page, party)
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
        catalog: CompiledShopCatalog,
        page: CompiledShopCatalogPage,
        party: PartyState,
    ) {
        val lines =
            buildList {
                catalog.intro?.let { addAll(it.renderTextLines(content)) }
                page.entries.forEach { offer ->
                    val label = offer.labelString?.let(content.strings::value) ?: itemName(offer.item)
                    add(offer.binding.mnemonicLine(content, "$label  ${unitPrice(offer, party)} au"))
                }
                page.navigation.previous?.let { add(it.optionLine(content)) }
                page.navigation.next?.let { add(it.optionLine(content)) }
                page.footer?.let { addAll(it.renderTextLines(content)) }
            }
        screen.writeText(lines)
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
                addAll(buy.payPrompt.renderTextLines(content))
            }
        var choice: Char
        do {
            screen.writeText(promptLines)
            choice = input.readCommand() ?: return party
            if (buy.paymentKeys.decline.matchesInput(content, choice)) return party
        } while (!buy.paymentKeys.pay.matchesInput(content, choice))
        if (party.resources.gold < totalPrice) {
            writeTextAndWait(buy.tooExpensive.renderShopLines(item = offer.item, price = totalPrice))
            return party
        }

        val result = applyPurchase(buy, offer, party, quantity, totalPrice)
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
        screen.writeText(purchase.quantityPrompt.renderTextLines(content))
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
            return ShopResult(party, buy.cantCarry.renderTextLines(content))
        }
        val updated =
            party
                .withInventoryItem(targetSlot, offer.item)
                .withGold(party.resources.gold - totalPrice)
        return ShopResult(updated, buy.deal.renderTextLines(content) + "Bought ${itemName(offer.item)}.")
    }

    private fun chooseDropSlot(
        buy: CompiledShopBuy,
        party: PartyState,
    ): Int? {
        if (buy.inventoryApplication.overflow != RewardOverflowPolicy.PromptDropThenPlace) return null
        screen.writeText(buy.cantCarry.renderTextLines(content) + "" + "Drop an item? (Y/N)")
        val confirm = input.readCommand()?.uppercaseChar() ?: return null
        if (confirm != 'Y') return null
        val lines =
            buildList {
                add("Drop which item?")
                add("Any other key leaves.")
                addAll(inventoryLines(party))
            }
        screen.writeText(lines)
        val choice = input.readCommand() ?: return null
        val slot = choice.inventorySlotFromKey() ?: return null
        return slot.takeIf { party.inventoryItem(it) != null }
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
        return ShopResult(updated, buy.deal.renderTextLines(content))
    }

    private fun runSell(
        sell: CompiledShopSell,
        runtime: RuntimeState,
    ): RuntimeState {
        val party = runtime.party
        screen.writeText(sellPromptLines(sell, party))
        val choice = input.readCommand() ?: return runtime
        val slot = choice.inventorySlotFromKey() ?: return runtime
        val item = party.inventoryItem(slot)
        if (item == null) {
            writeTextAndWait(listOf("That slot is empty."))
            return runtime
        }
        if (!sell.accepts(item)) {
            writeTextAndWait(sell.wrongKind.renderTextLines(content))
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
                writeTextAndWait(sell.declined?.renderTextLines(content) ?: listOf("No thanks."))
                return current
            }
            if (sell.bargain != null && sell.saleKeys.bargain.matchesInput(content, confirm)) {
                val quote = haggleQuote(sell, item, price, current.party)
                price =
                    quote.price
                        ?: return current.withParty(quote.party).also {
                            writeTextAndWait(sell.declined?.renderTextLines(content) ?: listOf("No thanks."))
                        }
                current = current.withParty(quote.party)
                continue
            }
            if (!sell.saleKeys.accept.matchesInput(content, confirm)) continue
            val updated =
                current.party
                    .withInventoryItem(slot, null)
                    .withGold(current.party.resources.gold + price)
            val soldLines = sell.sold?.renderTextLines(content) ?: listOf("Sold!")
            screen.updateStatus(current.withParty(updated))
            writeTextAndWait(soldLines)
            return current.withParty(updated)
        }
    }

    private fun sellChoiceLines(sell: CompiledShopSell): List<String> =
        buildList {
            add(
                "Accept? (${sell.saleKeys.accept.primaryInputLabel(content)})es / " +
                    "(${sell.saleKeys.decline.primaryInputLabel(content)})o",
            )
            if (sell.bargain != null) add("(${sell.saleKeys.bargain.primaryInputLabel(content)})argain")
        }

    private fun writeTextAndWait(lines: List<String>) {
        screen.writeText(lines.withPausePrompt(pausePrompts))
        input.readCommand()
    }

    private fun sellPromptLines(
        sell: CompiledShopSell,
        party: PartyState,
    ): List<String> =
        buildList {
            addAll(sell.prompt.renderTextLines(content))
            addAll(inventoryLines(party))
        }

    private fun inventoryLines(party: PartyState): List<String> =
        inventoryMenuLines(
            party.inventory.map { slot ->
                inventorySlotKey(slot.slot) to (slot.item?.let(::itemName) ?: "")
            },
        )

    private fun CompiledShopSell.accepts(item: CompiledHandleRef): Boolean {
        if (item in rejectedItems) return false
        if (item in acceptedItems) return true
        return itemRecord(item).kind in acceptedKinds
    }

    private fun List<String>.withChoiceGap(): List<String> = if (lastOrNull().isNullOrBlank()) this else this + ""

    private fun List<String>.withGeneratedChoices(generatedLines: List<String>): List<String> =
        takeIf { lines -> lines.alreadyListChoices(generatedLines.map { line -> line.substringBefore('.') }) } ?: (this + generatedLines)

    private fun List<String>.alreadyListChoices(keys: List<String>): Boolean =
        keys.all { key -> any { line -> "($key)" in line || line.trimStart().startsWith("$key.") } }

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
        return priceText?.renderShopLines(item = offer.item, price = renderedPrice) ?: listOf("Price: $totalPrice")
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
        val bargain = sell.bargain ?: return SellQuote(current, party)
        val draw = party.drawRandom(0, 99)
        bargain.rejectRollBelow?.let { threshold ->
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
        bargain.bonuses.forEach { bonus ->
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
        val speak = systems.speak ?: return RumorResult(runtime, listOf("No rumor."))
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
                speak.textTables.screens
                    .firstOrNull { screen -> screen.id == SPEAK_NO_RUMOR_SCREEN }
                    ?.text
                    ?.renderExternal(runtime)
                    .orEmpty(),
            )
        }
        val draw = runtime.party.drawRandom(0, entries.lastIndex)
        log.rngDraw("speak.rumor.entry", 0, entries.lastIndex, draw.value)
        val current = runtime.withParty(draw.party)
        return RumorResult(current, entries[draw.value].text.renderExternal(current))
    }

    private fun ExternalRuntimeTextBlock.renderExternal(runtime: RuntimeState): List<String> =
        renderParts { part ->
            when (part.kind) {
                "partyMemberMax" -> runtime.partyMemberMax.toString()
                "currentTownName" -> runtime.currentTown?.let(::townName)
                "townName" -> part.args["town"]?.let(::townNameBySlug)
                "fuzzyTownLocation" -> part.args["town"]?.let { town -> townLocation(town, part.args["radius"]?.toIntOrNull() ?: 0) }
                "roleName" -> part.args["role"]?.let(::roleName)
                "roleJob" -> part.args["role"]?.let(::roleJob)
                "roleLocation" -> part.args["role"]?.let { role -> roleLocation(role, runtime) }
                "npcName" -> part.args["npc"]?.let(::npcName)
                "npcJob" -> part.args["npc"]?.let(::npcJob)
                "npcLocation" -> part.args["npc"]?.let { npc -> npcLocation(npc, runtime) }
                "locationCoordinate" -> null
                else -> null
            }
        }

    private fun roleName(roleSlug: String): String? =
        roleCharacter(roleSlug)?.let { character -> content.strings.value(character.nameString) }

    private fun roleJob(roleSlug: String): String? =
        roleCharacter(roleSlug)?.let(::characterJob)

    private fun roleLocation(
        roleSlug: String,
        runtime: RuntimeState,
    ): String? =
        roleCharacter(roleSlug)
            ?.position(runtime)
            ?.let(::positionText)

    private fun npcName(npcSlug: String): String? =
        npcCharacter(npcSlug)?.let { character -> content.strings.value(character.nameString) }

    private fun npcJob(npcSlug: String): String? =
        npcCharacter(npcSlug)?.let(::characterJob)

    private fun npcLocation(
        npcSlug: String,
        runtime: RuntimeState,
    ): String? =
        npcCharacter(npcSlug)
            ?.position(runtime)
            ?.let(::positionText)

    private fun roleCharacter(roleSlug: String): CompiledCharacter? {
        val role = refOrNull(CompiledHandleKind.Role, roleSlug) ?: return null
        return content.characters.firstOrNull { character -> role in character.roles }
    }

    private fun npcCharacter(npcSlug: String): CompiledCharacter? {
        val npc = refOrNull(CompiledHandleKind.Npc, npcSlug) ?: return null
        return content.characters.firstOrNull { character -> character.id == npc }
    }

    private fun characterJob(character: CompiledCharacter): String = content.slug(character.job).readableJob()

    private fun CompiledCharacter.position(runtime: RuntimeState): CompiledWorldPosition? =
        runtime.npcs.firstOrNull { placement -> placement.npc == id }?.position
            ?: (placement as? CompiledCharacterPlacement.Fixed)?.position

    private fun townNameBySlug(townSlug: String): String = refOrNull(CompiledHandleKind.Town, townSlug)?.let(::townName) ?: townSlug

    private fun townLocation(
        townSlug: String,
        radius: Int,
    ): String? {
        val town = refOrNull(CompiledHandleKind.Town, townSlug) ?: return null
        val position = content.towns.firstOrNull { current -> current.id == town }?.position ?: return null
        return pointText(fuzzyPoint(position, townSlug, radius))
    }

    private fun positionText(position: CompiledWorldPosition): String =
        when (position) {
            is CompiledWorldPosition.Town -> "in ${townName(position.town)}"
            is CompiledWorldPosition.Wilderness -> "at ${pointText(position.point)}"
        }

    private fun townName(town: CompiledHandleRef): String =
        content.towns.firstOrNull { current -> current.id == town }?.let { current -> content.strings.value(current.nameString) }
            ?: content.slug(town)

    private fun pointText(point: GridPoint): String = "${point.x}x${point.y}"

    private fun fuzzyPoint(
        point: GridPoint,
        slug: String,
        radius: Int,
    ): GridPoint =
        when {
            radius <= 0 -> point
            else ->
                GridPoint(
                    x = point.x + stableOffset("$slug:x", radius),
                    y = point.y + stableOffset("$slug:y", radius),
                )
        }

    private fun stableOffset(
        key: String,
        radius: Int,
    ): Int {
        val span = radius * 2 + 1
        val hash = key.fold(0) { acc, char -> acc * 31 + char.code }
        return Math.floorMod(hash, span) - radius
    }

    private fun flagStateRef(slug: String): CompiledHandleRef? = refOrNull(CompiledHandleKind.FlagState, slug)

    private fun refOrNull(
        kind: CompiledHandleKind,
        slug: String,
    ): CompiledHandleRef? = content.strings.idOf(slug)?.let { slugString -> content.handles.refOrNull(kind, slugString) }

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
                content,
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
}

private const val SHOP_INTERLUDE_DELAY_MS = 2_000
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

internal fun CompiledInputBinding.matchesInput(
    content: CompiledContentBundle,
    choice: Char,
): Boolean {
    val accepted = listOf(primaryString) + aliasStrings
    return accepted
        .map(content.strings::value)
        .any { value -> value.equals(choice.toString(), ignoreCase = true) }
}

internal fun CompiledInputBinding.primaryInputLabel(content: CompiledContentBundle): String = content.strings.value(primaryString)

private fun CompiledInputBinding.optionLine(content: CompiledContentBundle): String =
    mnemonicLine(content, content.strings.value(labelString))

internal fun CompiledInputBinding.mnemonicLine(
    content: CompiledContentBundle,
    text: String,
): String = "(${primaryInputLabel(content).uppercase()})${text.afterMnemonic(primaryInputLabel(content))}"

private fun String.afterMnemonic(key: String): String =
    removePrefix(key)
        .removePrefix(key.uppercase())
        .removePrefix(key.lowercase())
