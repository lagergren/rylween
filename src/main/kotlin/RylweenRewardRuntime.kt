// Reusable session state machines for granting rewards (mission/quest/script
// rewards) and offering a single random item for sale (e.g. tavern scruffy
// dealer, free dungeon finds via priceOverride).

enum class RandomItemOfferStatus {
    Offered,
    Bought,
    Declined,
    NotEnoughGold,
    NeedsDropSlot,
    InventoryFull,
    InvalidDropSlot,
}

enum class RewardGrantStatus {
    Granted,
    NeedsDropSlot,
    InventoryFull,
    InvalidDropSlot,
}

data class RandomItemOfferResult(
    val status: RandomItemOfferStatus,
    val party: PartyState,
    val item: ItemId,
    val price: Int,
    val slot: Int? = null,
)

data class RewardGrantResult(
    val status: RewardGrantStatus,
    val party: PartyState,
    val item: ItemId? = null,
    val slot: Int? = null,
)

sealed interface RandomItemOfferStep {
    data class Choice(
        val offerText: TextBlock,
        val prompt: TextBlock,
        val item: ItemId,
        val itemName: String,
        val price: Int,
        val buy: InputBindingModel,
        val decline: InputBindingModel,
    ) : RandomItemOfferStep {
        val buyKey: Char get() = buy.primaryChar
        val declineKey: Char get() = decline.primaryChar
    }

    data class DropSlot(
        val prompt: TextBlock,
        val item: ItemId,
        val itemName: String,
        val price: Int,
        val result: RandomItemOfferResult,
    ) : RandomItemOfferStep

    data class Message(
        val text: TextBlock,
        val next: RandomItemOfferStep,
        val result: RandomItemOfferResult,
    ) : RandomItemOfferStep

    data class Finished(val result: RandomItemOfferResult) : RandomItemOfferStep {
        val party: PartyState get() = result.party
    }
}

sealed interface RewardGrantStep {
    data class DropSlot(
        val prompt: TextBlock,
        val item: ItemId,
        val result: RewardGrantResult,
    ) : RewardGrantStep

    data class Message(
        val text: TextBlock,
        val result: RewardGrantResult,
    ) : RewardGrantStep

    data class Finished(val result: RewardGrantResult) : RewardGrantStep {
        val party: PartyState get() = result.party
    }
}

data class RandomItemOfferSession(
    val offer: RandomItemOfferModel,
    val party: PartyState,
    val item: ItemId,
    val itemName: String,
    val price: Int,
    val step: RandomItemOfferStep,
    val lastResult: RandomItemOfferResult? = null,
    val itemRecords: List<ItemRecordModel> = emptyList(),
    val realmContent: RealmContentModel = RealmContentModel(),
) {
    fun apply(input: RandomItemOfferInput): RandomItemOfferSession =
        when (val current = step) {
            is RandomItemOfferStep.Choice -> {
                val buy = (input as? RandomItemOfferInput.Buy)
                    ?: error("random item offer is waiting for buy/decline input")
                if (buy.accept) buy() else decline()
            }
            is RandomItemOfferStep.DropSlot -> {
                val drop = (input as? RandomItemOfferInput.DropSlot)
                    ?: error("random item offer is waiting for a drop slot")
                place(drop.slot)
            }
            is RandomItemOfferStep.Message -> {
                require(input is RandomItemOfferInput.Continue) { "random item offer is showing message text" }
                copy(step = current.next, lastResult = current.result)
            }
            is RandomItemOfferStep.Finished -> error("random item offer is already finished: $current")
        }

    private fun buy(): RandomItemOfferSession =
        when {
            party.resources.gold < price -> finish(RandomItemOfferStatus.NotEnoughGold, party, offer.broke)
            else -> place(dropSlot = null)
        }

    private fun decline(): RandomItemOfferSession =
        finish(RandomItemOfferStatus.Declined, party, offer.declined)

    private fun place(dropSlot: Int?): RandomItemOfferSession =
        when (val placed = party.placeInventoryItem(
            item,
            offer.application.overflow,
            dropSlot,
            itemRecords = itemRecords,
            realmContent = realmContent,
        )) {
            is InventoryPlacementResult.Placed -> {
                val updated = placed.party.copy(resources = placed.party.resources.copy(gold = placed.party.resources.gold - price))
                finish(RandomItemOfferStatus.Bought, updated, offer.deal, placed.slot)
            }
            is InventoryPlacementResult.NeedsDropSlot -> {
                val result = result(RandomItemOfferStatus.NeedsDropSlot, placed.party)
                copy(
                    step = RandomItemOfferStep.DropSlot(
                        prompt = checkNotNull(offer.messages.cantCarry) {
                            "drop-prompt random item offer requires cant-carry text"
                        },
                        item = item,
                        itemName = itemName,
                        price = price,
                        result = result,
                    ),
                    lastResult = result,
                )
            }
            is InventoryPlacementResult.Full ->
                finish(RandomItemOfferStatus.InventoryFull, placed.party, checkNotNull(offer.messages.cantCarry))
            is InventoryPlacementResult.InvalidDropSlot -> {
                val result = result(RandomItemOfferStatus.InvalidDropSlot, placed.party, placed.slot)
                copy(
                    step = RandomItemOfferStep.DropSlot(
                        prompt = checkNotNull(offer.messages.cantCarry),
                        item = item,
                        itemName = itemName,
                        price = price,
                        result = result,
                    ),
                    lastResult = result,
                )
            }
        }

    private fun finish(
        status: RandomItemOfferStatus,
        nextParty: PartyState,
        message: TextBlock,
        slot: Int? = null,
    ): RandomItemOfferSession {
        val result = result(status, nextParty, slot)
        return copy(
            party = nextParty,
            step = RandomItemOfferStep.Message(message, RandomItemOfferStep.Finished(result), result),
            lastResult = result,
        )
    }

    private fun result(
        status: RandomItemOfferStatus,
        nextParty: PartyState,
        slot: Int? = null,
    ): RandomItemOfferResult =
        RandomItemOfferResult(status, nextParty, item, price, slot)

    companion object {
        fun start(
            offer: RandomItemOfferModel,
            party: PartyState,
            itemRecords: List<ItemRecordModel> = emptyList(),
            selectedIndex: Int = 0,
            realmContent: RealmContentModel = RealmContentModel(),
        ): RandomItemOfferSession {
            require(offer.pool.items.isNotEmpty()) { "random item offer ${offer.pool.slug} has no items" }
            val item = offer.pool.items[selectedIndex.floorMod(offer.pool.items.size)]
            val record = checkNotNull(itemRecords.associateBy { it.id }[item]) { "unknown item ${item.slug}" }
            val price = offer.priceOverride ?: record.cost
            return RandomItemOfferSession(
                offer = offer,
                party = party,
                item = item,
                itemName = record.displayName,
                price = price,
                step = RandomItemOfferStep.Choice(
                    offerText = offer.offer.withRandomItemOffer(record.displayName, price),
                    prompt = offer.choicePrompt,
                    item = item,
                    itemName = record.displayName,
                    price = price,
                    buy = offer.buy,
                    decline = offer.decline,
                ),
                itemRecords = itemRecords,
                realmContent = realmContent,
            )
        }
    }
}

data class RewardGrantSession(
    val reward: ScriptRewardModel,
    val party: PartyState,
    val pendingItems: List<ItemId>,
    val step: RewardGrantStep,
    val lastResult: RewardGrantResult? = null,
    val itemRecords: List<ItemRecordModel> = emptyList(),
    val realmContent: RealmContentModel = RealmContentModel(),
) {
    fun apply(input: RewardGrantInput): RewardGrantSession =
        when (val current = step) {
            is RewardGrantStep.DropSlot -> {
                val drop = (input as? RewardGrantInput.DropSlot)
                    ?: error("reward grant is waiting for a drop slot")
                place(party, current.item, drop.slot, pendingItems)
            }
            is RewardGrantStep.Message -> {
                require(input is RewardGrantInput.Continue) { "reward grant is showing message text" }
                continueWith(party, pendingItems)
            }
            is RewardGrantStep.Finished -> error("reward grant is already finished: $current")
        }

    private fun continueWith(nextParty: PartyState, items: List<ItemId>): RewardGrantSession {
        val item = items.firstOrNull() ?: return finish(RewardGrantStatus.Granted, nextParty)
        return place(nextParty, item, dropSlot = null, pendingItems = items)
    }

    private fun place(
        nextParty: PartyState,
        item: ItemId,
        dropSlot: Int?,
        pendingItems: List<ItemId>,
    ): RewardGrantSession {
        val remainingItems = pendingItems.drop(1)
        return when (val placed = nextParty.placeInventoryItem(
            item,
            reward.application.overflow,
            dropSlot,
            itemRecords = itemRecords,
            realmContent = realmContent,
        )) {
            is InventoryPlacementResult.Placed -> {
                val result = result(RewardGrantStatus.Granted, placed.party, item, placed.slot)
                val pickedUp = reward.messages.pickedUp
                if (dropSlot != null && pickedUp != null) {
                    copy(
                        party = placed.party,
                        pendingItems = remainingItems,
                        step = RewardGrantStep.Message(pickedUp, result),
                        lastResult = result,
                    )
                } else {
                    continueWith(placed.party, remainingItems)
                }
            }
            is InventoryPlacementResult.NeedsDropSlot -> {
                val result = result(RewardGrantStatus.NeedsDropSlot, placed.party, item)
                copy(
                    party = placed.party,
                    pendingItems = pendingItems,
                    step = RewardGrantStep.DropSlot(
                        prompt = checkNotNull(reward.messages.cantCarry) {
                            "drop-prompt reward requires cant-carry text"
                        },
                        item = item,
                        result = result,
                    ),
                    lastResult = result,
                )
            }
            is InventoryPlacementResult.Full -> {
                val result = result(RewardGrantStatus.InventoryFull, placed.party, item)
                continueWith(placed.party, remainingItems).copy(lastResult = result)
            }
            is InventoryPlacementResult.InvalidDropSlot -> {
                val result = result(RewardGrantStatus.InvalidDropSlot, placed.party, item, placed.slot)
                copy(
                    party = placed.party,
                    pendingItems = pendingItems,
                    step = RewardGrantStep.DropSlot(
                        prompt = checkNotNull(reward.messages.cantCarry),
                        item = item,
                        result = result,
                    ),
                    lastResult = result,
                )
            }
        }
    }

    private fun finish(status: RewardGrantStatus, nextParty: PartyState): RewardGrantSession {
        val result = result(status, nextParty)
        return copy(
            party = nextParty,
            pendingItems = emptyList(),
            step = RewardGrantStep.Finished(result),
            lastResult = result,
        )
    }

    private fun result(
        status: RewardGrantStatus,
        nextParty: PartyState,
        item: ItemId? = null,
        slot: Int? = null,
    ): RewardGrantResult =
        RewardGrantResult(status, nextParty, item, slot)

    companion object {
        fun start(
            reward: ScriptRewardModel,
            party: PartyState,
            gold: Int = 0,
            itemRecords: List<ItemRecordModel> = emptyList(),
            realmContent: RealmContentModel = RealmContentModel(),
        ): RewardGrantSession {
            val itemRecordById = itemRecords.associateBy { it.id }
            reward.items.forEach { item ->
                val record = checkNotNull(itemRecordById[item]) { "unknown reward item ${item.slug}" }
                check(record.storage == ItemStorageKind.SlottedInventory) {
                    "script reward item ${item.slug} is ${record.storage}; use achieve(...) for milestone-special items"
                }
            }
            val session = RewardGrantSession(
                reward = reward,
                party = party.withGold(party.resources.gold + gold),
                pendingItems = reward.items,
                step = RewardGrantStep.Finished(RewardGrantResult(RewardGrantStatus.Granted, party)),
                itemRecords = itemRecords,
                realmContent = realmContent,
            )
            return session.continueWith(session.party, session.pendingItems)
        }
    }
}

internal fun TextBlock.withRandomItemOffer(itemName: String, itemPrice: Int): TextBlock =
    when (this) {
        is TextBlock.Inline -> this
        is TextBlock.Ref -> this
        is TextBlock.Structured -> TextBlock.Structured(
            lines.map { line ->
                TextLine(
                    line.parts.map { part ->
                        when (part) {
                            TextPart.OfferedItemName -> TextPart.Literal(itemName)
                            TextPart.OfferedItemPrice -> TextPart.Literal(itemPrice.toString())
                            else -> part
                        }
                    },
                )
            },
        )
    }
