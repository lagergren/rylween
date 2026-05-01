// Pure Kotlin runtime helpers for DSL systems that should work behind any
// frontend or engine bridge. These functions mutate immutable PartyState values
// and keep UI/input decisions explicit in their inputs.

enum class EquipmentKind { Weapon, Armor }
enum class EquipmentCommand { ReadyWeapon, ReadyArmor, RemoveWeapon, RemoveArmor }
enum class ShopMainChoice { Buy, Sell, Leave }

enum class SpeakMainChoice { Chitchat, Question, Rumour, Bye }

sealed interface RandomItemOfferInput {
    data class Buy(val accept: Boolean) : RandomItemOfferInput
    data class DropSlot(val slot: Int) : RandomItemOfferInput
    object Continue : RandomItemOfferInput
}

sealed interface RewardGrantInput {
    data class DropSlot(val slot: Int) : RewardGrantInput
    object Continue : RewardGrantInput
}

sealed interface EquipmentPromptInput {
    data class InventorySlot(val slot: Int) : EquipmentPromptInput
    data class Member(val memberNumber: Int) : EquipmentPromptInput
}

sealed interface SpeakInput {
    data class Npc(val npc: NpcId) : SpeakInput
    data class Main(val choice: SpeakMainChoice) : SpeakInput
    data class Question(val topic: QuestionTopic) : SpeakInput
    object Continue : SpeakInput
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
    data class FinishNativeService(val runtime: RuntimeSessionState) : TownSessionInput
}

data class RuntimeSessionState(
    val realm: RealmState,
    val parties: List<PartyState>,
) {
    val activeParty: PartyState
        get() = parties.firstOrNull { party -> party.id == realm.activeParty }
            ?: error("active party ${realm.activeParty.slug} is missing from runtime state")

    fun withRealm(realm: RealmState): RuntimeSessionState =
        copy(realm = realm)

    fun withActiveParty(party: PartyState): RuntimeSessionState {
        val nextParties = if (parties.any { it.id == party.id }) {
            parties.map { existing -> if (existing.id == party.id) party else existing }
        } else {
            parties + party
        }
        return copy(
            realm = realm.copy(activeParty = party.id),
            parties = nextParties,
        )
    }

    companion object {
        fun singleParty(
            party: PartyState,
            hour: Int,
            currentTown: TownId?,
            rngSeed: Long? = null,
        ): RuntimeSessionState =
            RuntimeSessionState(
                realm = RealmState(
                    day = 0,
                    hour = hour,
                    minute = 0,
                    rngSeed = rngSeed,
                    activeParty = party.id,
                    currentMap = Maps.wilderness,
                    currentMode = RuntimeMode.Town,
                    currentTown = currentTown,
                    banished = false,
                    lastPlayedDay = 0,
                    partyMemberMax = 4,
                ),
                parties = listOf(party),
            )
    }
}

data class RylweenRandomDraw(
    val value: Int,
    val nextSeed: Long,
)

data class RuntimeRandomDraw(
    val value: Int,
    val runtime: RuntimeSessionState,
)

interface RylweenRandomPort {
    fun draw(seed: Long, lo: Int, hi: Int): RylweenRandomDraw
}

object LegacyRylweenRandom : RylweenRandomPort {
    private const val Multiplier: Long = 0x2545F4914F6CDD1DL

    override fun draw(seed: Long, lo: Int, hi: Int): RylweenRandomDraw {
        require(lo <= hi) { "random range must be ascending: $lo..$hi" }
        var state = seed.takeUnless { it == 0L } ?: 1L
        state = state xor (state ushr 12)
        state = state xor (state shl 25)
        state = state xor (state ushr 27)
        val mixed = state * Multiplier
        val span = hi.toLong() - lo.toLong() + 1L
        val offset = java.lang.Long.remainderUnsigned(mixed, span)
        return RylweenRandomDraw((lo.toLong() + offset).toInt(), state)
    }
}

fun RuntimeSessionState.drawRandom(
    lo: Int,
    hi: Int,
    random: RylweenRandomPort = LegacyRylweenRandom,
): RuntimeRandomDraw {
    require(lo <= hi) { "random range must be ascending: $lo..$hi" }
    val seed = realm.rngSeed ?: return RuntimeRandomDraw(lo, this)
    val draw = random.draw(seed, lo, hi)
    return RuntimeRandomDraw(draw.value, withRealm(realm.copy(rngSeed = draw.nextSeed)))
}

enum class TownServiceScriptBranch {
    IfThen,
    IfElse,
    LoopBody,
    ForEachMemberBody,
    ForEachMonsterBody,
    SwitchCase,
    MenuOption,
    MenuDefault,
}

data class TownServiceScriptSegment(
    val opIndex: Int,
    val branch: TownServiceScriptBranch,
    val caseIndex: Int? = null,
    val key: Char? = null,
)

data class TownServiceScriptBlockRef(
    val segments: List<TownServiceScriptSegment> = emptyList(),
) {
    fun child(
        opIndex: Int,
        branch: TownServiceScriptBranch,
        caseIndex: Int? = null,
        key: Char? = null,
    ): TownServiceScriptBlockRef =
        copy(segments = segments + TownServiceScriptSegment(opIndex, branch, caseIndex, key))

    companion object {
        val Root = TownServiceScriptBlockRef()
    }
}

data class TownServiceScriptFrame(
    val block: TownServiceScriptBlockRef,
    val nextIndex: Int,
    val loop: Boolean = false,
    val iteration: Int = 0,
    val localName: String? = null,
    val localValue: Int? = null,
)

data class TownServiceContinuation(
    val runtime: RuntimeSessionState,
    val locals: Map<String, Int> = emptyMap(),
    val realmScalars: Map<String, Int> = emptyMap(),
    val stack: List<TownServiceScriptFrame>,
    val speakSession: SpeakSession? = null,
    val randomItemOfferSession: RandomItemOfferSession? = null,
    val rewardGrantSession: RewardGrantSession? = null,
) {
    val party: PartyState get() = runtime.activeParty
    val realm: RealmState get() = runtime.realm

    companion object {
        fun start(runtime: RuntimeSessionState): TownServiceContinuation =
            TownServiceContinuation(
                runtime = runtime,
                stack = listOf(TownServiceScriptFrame(TownServiceScriptBlockRef.Root, nextIndex = 0)),
            )

        fun start(party: PartyState): TownServiceContinuation =
            start(RuntimeSessionState.singleParty(party, hour = 0, currentTown = null))
    }
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
        val buyKey: Char,
        val declineKey: Char,
    ) : RandomItemOfferStep

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
        when (val placed = party.placeInventoryItem(item, offer.application.overflow, dropSlot)) {
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
            itemRecords: List<ItemRecordModel> = ItemCatalog.all,
            selectedIndex: Int = 0,
        ): RandomItemOfferSession {
            require(offer.pool.items.isNotEmpty()) { "random item offer ${offer.pool.slug} has no items" }
            val item = offer.pool.items[selectedIndex.floorMod(offer.pool.items.size)]
            val record = checkNotNull(itemRecords.associateBy { it.id }[item]) { "unknown item ${item.slug}" }
            return RandomItemOfferSession(
                offer = offer,
                party = party,
                item = item,
                itemName = record.displayName,
                price = record.cost,
                step = RandomItemOfferStep.Choice(
                    offerText = offer.offer.withRandomItemOffer(record.displayName, record.cost),
                    prompt = offer.choicePrompt,
                    item = item,
                    itemName = record.displayName,
                    price = record.cost,
                    buyKey = offer.buyKey,
                    declineKey = offer.declineKey,
                ),
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
        return when (val placed = nextParty.placeInventoryItem(item, reward.application.overflow, dropSlot)) {
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
            itemRecords: List<ItemRecordModel> = ItemCatalog.all,
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
            )
            return session.continueWith(session.party, session.pendingItems)
        }
    }
}

data class SpeakRuntimeContent(
    val speak: SpeakSystemModel = SpeakSystemModel(null),
    val characters: List<CharacterModel> = emptyList(),
    val dialogue: List<DialogueModel> = emptyList(),
)

sealed interface SpeakStep {
    data class ChooseNpc(val npcs: List<CharacterModel>) : SpeakStep
    data class DialogueBranch(
        val npc: CharacterModel,
        val branch: DialogueBranchModel,
        val next: SpeakStep,
        val continuation: TownServiceContinuation? = null,
    ) : SpeakStep
    data class MainMenu(val npc: CharacterModel, val actions: List<ConversationMenuActionModel>) : SpeakStep
    data class QuestionMenu(val npc: CharacterModel, val actions: List<QuestionMenuActionModel>) : SpeakStep
    data class Message(val blocks: List<TextBlock>, val next: SpeakStep) : SpeakStep
    data class Finished(val runtime: RuntimeSessionState) : SpeakStep {
        val party: PartyState get() = runtime.activeParty
    }
}

data class SpeakSession(
    val content: SpeakRuntimeContent,
    val runtime: RuntimeSessionState,
    val building: BuildingId,
    val step: SpeakStep,
    val selectedNpc: CharacterModel? = null,
    val rumourTold: Boolean = false,
) {
    private val conversation: StandardConversationModel
        get() = requireNotNull(content.speak.standardConversation) {
            "standard conversation system is not configured"
        }

    fun apply(input: SpeakInput): SpeakSession =
        when (val current = step) {
            is SpeakStep.ChooseNpc -> {
                val npc = (input as? SpeakInput.Npc)
                    ?: error("speak session is choosing an NPC")
                val character = current.npcs.firstOrNull { it.id == npc.npc }
                    ?: error("NPC ${npc.npc.slug} is not available for ${building.slug}")
                val nextRuntime = runtime.withActiveParty(runtime.activeParty.withNpcVisit(character.id, visited = true))
                val branch = content.firstMatchingDialogueBranch(character.id, nextRuntime.activeParty)
                val menu = mainMenu(character)
                copy(
                    runtime = nextRuntime,
                    selectedNpc = character,
                    step = branch?.let { SpeakStep.DialogueBranch(character, it, next = menu) } ?: menu,
                )
            }
            is SpeakStep.DialogueBranch -> error("speak session is running a dialogue branch")
            is SpeakStep.MainMenu -> {
                val choice = (input as? SpeakInput.Main)
                    ?: error("speak session is at the main conversation menu")
                when (choice.choice) {
                    SpeakMainChoice.Chitchat -> chitchat(current.npc)
                    SpeakMainChoice.Question -> copy(step = questionMenu(current.npc))
                    SpeakMainChoice.Rumour -> rumour(current.npc)
                    SpeakMainChoice.Bye -> copy(step = SpeakStep.Finished(runtime))
                }
            }
            is SpeakStep.QuestionMenu -> {
                val question = (input as? SpeakInput.Question)
                    ?: error("speak session is at the question menu")
                if (question.topic == QuestionTopic.Nothing) {
                    copy(step = mainMenu(current.npc))
                } else {
                    copy(step = message(questionReply(question.topic, current.npc), mainMenu(current.npc)))
                }
            }
            is SpeakStep.Message -> {
                require(input is SpeakInput.Continue) { "speak session is showing message text" }
                copy(step = current.next)
            }
            is SpeakStep.Finished -> error("speak session is already finished: $current")
        }

    private fun chitchat(npc: CharacterModel): SpeakSession {
        val subjects = conversation.chitchat.subjects.ifEmpty { listOf(ChitchatSubject.Well) }
        val draw = runtime.drawRandom(0, subjects.lastIndex)
        val subject = subjects[draw.value]
        return copy(
            runtime = draw.runtime,
            step = message(
                listOf(chitchatPrompt(subject), chitchatReply(subject, npc)),
                mainMenu(npc),
            ),
        )
    }

    private fun rumour(npc: CharacterModel): SpeakSession {
        val selection = if (rumourTold) null else content.selectStandardRumour(runtime)
        val text = selection?.rumour?.text ?: noRumourText()
        return copy(
            runtime = selection?.runtime ?: runtime,
            rumourTold = true,
            step = message(text, mainMenu(npc)),
        )
    }

    private fun mainMenu(npc: CharacterModel): SpeakStep.MainMenu =
        SpeakStep.MainMenu(npc, conversation.menu.actions)

    private fun questionMenu(npc: CharacterModel): SpeakStep.QuestionMenu =
        SpeakStep.QuestionMenu(npc, conversation.menu.questions)

    private fun message(text: TextBlock, next: SpeakStep): SpeakStep.Message =
        message(listOf(text), next)

    private fun message(blocks: List<TextBlock>, next: SpeakStep): SpeakStep.Message =
        SpeakStep.Message(blocks, next)

    private fun chitchatPrompt(subject: ChitchatSubject): TextBlock {
        val leaderGender = runtime.activeParty.members.firstOrNull()?.gender
        val variants = content.speak.textTables.chitchatPrompts.firstOrNull { it.subject == subject }?.variants.orEmpty()
        return variants.firstOrNull { it.audience.matches(leaderGender) }?.text
            ?: variants.firstOrNull()?.text
            ?: inlineText("- Hello.")
    }

    private fun chitchatReply(subject: ChitchatSubject, npc: CharacterModel): TextBlock =
        content.speak.textTables.chitchatReplies
            .firstOrNull { it.subject == subject }
            ?.replies
            ?.firstOrNull { it.job == npc.job }
            ?.text
            ?: inlineText("- I have nothing to say.")

    private fun questionReply(topic: QuestionTopic, npc: CharacterModel): TextBlock =
        content.speak.textTables.questionReplies
            .firstOrNull { it.topic == topic }
            ?.replies
            ?.firstOrNull { it.job == npc.job }
            ?.text
            ?: noRumourText()

    private fun noRumourText(): TextBlock =
        content.speak.textTables.screens.firstOrNull { it.id == T.Speak.noRumour }?.text
            ?: inlineText("- Sorry. There's", "nothing new I can", "tell you.", "")

    companion object {
        fun start(
            content: SpeakRuntimeContent,
            runtime: RuntimeSessionState,
            building: BuildingId,
        ): SpeakSession {
            val npcs = content.charactersFor(runtime, building)
            val step = if (npcs.isEmpty()) {
                SpeakStep.Message(
                    blocks = listOf(
                        content.speak.textTables.screens.firstOrNull { it.id == T.Speak.noNpc }?.text
                            ?: inlineText("Noone is here!"),
                    ),
                    next = SpeakStep.Finished(runtime),
                )
            } else {
                SpeakStep.ChooseNpc(npcs)
            }
            return SpeakSession(content, runtime, building, step)
        }
    }
}

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
        val continuation: TownServiceContinuation? = null,
    ) : TownSessionStep
    data class LeaveEquipment(
        val session: TownLeaveEquipmentSession,
    ) : TownSessionStep
    data class Finished(
        val runtime: RuntimeSessionState,
    ) : TownSessionStep {
        val party: PartyState get() = runtime.activeParty
    }
}

data class TownSession(
    val town: TownModel,
    val townSystem: TownSystemModel,
    val runtime: RuntimeSessionState,
    val shopServices: Map<BuildingId, ShopServiceModel>,
    val serviceScenes: Map<SceneId, SceneModel>,
    val itemRecords: List<ItemRecordModel> = ItemCatalog.all,
    val step: TownSessionStep = TownSessionStep.Menu(town, availableTownActions(town, townSystem)),
    val lastResult: TownSessionResult? = null,
) {
    val realm: RealmState get() = runtime.realm
    val party: PartyState get() = runtime.activeParty
    val currentHour: Int get() = runtime.realm.hour

    fun apply(input: TownSessionInput): TownSession =
        when (input) {
            is TownSessionInput.MenuKey -> chooseMenu(input.key)
            is TownSessionInput.Shop -> applyShop(input.input)
            is TownSessionInput.Equipment -> applyEquipment(input.input)
            is TownSessionInput.FinishNativeService -> finishNative(input.runtime)
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
            copy(runtime = runtime.withActiveParty(session.party), step = current.copy(session = session))
        }
    }

    fun replaceLeaveEquipmentSession(session: TownLeaveEquipmentSession): TownSession {
        require(step is TownSessionStep.LeaveEquipment) { "town session is not running leave equipment prompts" }
        return if (session.step is TownLeaveEquipmentStep.Finished) {
            val nextRuntime = runtime.withActiveParty(session.party)
            copy(runtime = nextRuntime, step = TownSessionStep.Finished(nextRuntime))
        } else {
            copy(runtime = runtime.withActiveParty(session.party), step = TownSessionStep.LeaveEquipment(session))
        }
    }

    fun replaceNativeServiceContinuation(continuation: TownServiceContinuation): TownSession {
        val current = step as? TownSessionStep.NativeService
            ?: error("town session is not running a native service")
        return copy(
            runtime = continuation.runtime,
            step = current.copy(continuation = continuation),
        )
    }

    private fun chooseMenu(key: Char): TownSession {
        val action = townSystem.menu.firstOrNull { it.key == key.uppercaseChar() }
            ?: return copy(lastResult = null)
        if (action.endsMenu) {
            val equipment = TownLeaveEquipmentSession.start(party, itemRecords)
            return if (equipment.step is TownLeaveEquipmentStep.Finished) {
                copy(step = TownSessionStep.Finished(runtime))
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

    private fun finishNative(nextRuntime: RuntimeSessionState): TownSession {
        val current = step as? TownSessionStep.NativeService
            ?: error("town session is not running a native service")
        val nextParty = nextRuntime.activeParty
        return copy(
            runtime = nextRuntime,
            step = menuStep(),
            lastResult = TownSessionResult(TownSessionResultStatus.NativeServiceFinished, nextParty, current.service.building),
        )
    }

    private fun finishShop(session: ShopServiceSession, building: BuildingId): TownSession =
        runtime.withActiveParty(session.party).let { nextRuntime ->
            copy(
                runtime = nextRuntime,
                step = menuStep(),
                lastResult = TownSessionResult(TownSessionResultStatus.ShopFinished, session.party, building),
            )
        }

    private fun notice(text: TextBlock, result: TownSessionResult): TownSession =
        copy(step = TownSessionStep.Notice(text, menuStep()), lastResult = result)

    private fun menuStep(): TownSessionStep.Menu =
        TownSessionStep.Menu(town, availableTownActions(town, townSystem))

    companion object {
        fun start(
            town: TownModel,
            townSystem: TownSystemModel,
            scenes: List<SceneModel>,
            runtime: RuntimeSessionState,
            itemRecords: List<ItemRecordModel> = ItemCatalog.all,
        ): TownSession =
            TownSession(
                town = town,
                townSystem = townSystem,
                runtime = runtime,
                shopServices = scenes.shopServicesByBuilding(),
                serviceScenes = scenes.associateBy { scene -> scene.id },
                itemRecords = itemRecords,
            )

        fun start(
            town: TownModel,
            townSystem: TownSystemModel,
            scenes: List<SceneModel>,
            party: PartyState,
            currentHour: Int,
            rngSeed: Long? = null,
            itemRecords: List<ItemRecordModel> = ItemCatalog.all,
        ): TownSession =
            start(
                town = town,
                townSystem = townSystem,
                scenes = scenes,
                runtime = RuntimeSessionState.singleParty(
                    party = party,
                    hour = currentHour,
                    currentTown = town.id,
                    rngSeed = rngSeed,
                ),
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

fun SpeakRuntimeContent.charactersFor(runtime: RuntimeSessionState, building: BuildingId): List<CharacterModel> {
    val town = runtime.realm.currentTown ?: return emptyList()
    return characters
        .filter { character ->
            val placement = character.placement
            placement is CharacterPlacementModel.Fixed && placement.position == WorldPosition.Town(town, building)
        }
        .sortedBy { it.name }
}

fun SpeakRuntimeContent.firstMatchingDialogueBranch(npc: NpcId, party: PartyState): DialogueBranchModel? {
    val branches = dialogue.firstOrNull { it.npc == npc }?.branches.orEmpty()
    return branches.firstOrNull { branch ->
        branch.whenState?.let(party::hasFlagState) == true
    } ?: branches.firstOrNull { it.whenState == null }
}

data class RumourSelection(
    val rumour: RumourEntryModel,
    val runtime: RuntimeSessionState,
)

fun SpeakRuntimeContent.standardRumours(party: PartyState): List<RumourEntryModel> =
    speak.textTables.rumourTables
        .firstOrNull { it.id == RumourTables.standardTown }
        ?.entries
        ?.filter { entry -> entry.excludedBy.none(party::hasFlagState) }
        .orEmpty()

fun SpeakRuntimeContent.firstStandardRumour(party: PartyState): TextBlock? =
    standardRumours(party).firstOrNull()?.text

fun SpeakRuntimeContent.selectStandardRumour(runtime: RuntimeSessionState): RumourSelection? {
    val entries = standardRumours(runtime.activeParty)
    if (entries.isEmpty()) return null
    val draw = runtime.drawRandom(0, entries.lastIndex)
    return RumourSelection(entries[draw.value], draw.runtime)
}

fun PartyState.withNpcVisit(npc: NpcId, visited: Boolean): PartyState {
    val visits = visitedNpcs.associateBy { it.npc } + (npc to NpcVisitState(npc, visited))
    return copy(visitedNpcs = visits.values.sortedBy { it.npc.slug })
}

fun PartyState.withFlagState(flag: String, native: String): PartyState =
    copy(
        flags = (flags.filterNot { it.flag.slug == flag } + runtimeFlagState(flag, native))
            .sortedWith(compareBy({ it.flag.slug }, { it.native })),
    )

fun PartyState.hasFlagState(state: FlagState): Boolean =
    flags.any { it.flag == state.flag && it.native == state.native }

fun PartyState.withMilestone(milestone: GameMilestoneId): PartyState =
    if (milestone in milestones) this else copy(milestones = (milestones + milestone).sortedBy { it.slug })

fun TextBlock.withRandomItemOffer(itemName: String, itemPrice: Int): TextBlock =
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

private fun ChitchatPromptAudience.matches(gender: Gender?): Boolean =
    when (this) {
        ChitchatPromptAudience.Any -> true
        ChitchatPromptAudience.Female -> gender == Gender.Female
        ChitchatPromptAudience.Male -> gender == Gender.Male
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
