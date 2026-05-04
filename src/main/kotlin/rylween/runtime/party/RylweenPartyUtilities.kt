package rylween.runtime.party

import rylween.runtime.*

import rylween.dsl.*

data class InventorySlotView(
    val slot: Int,
    val key: Char,
    val item: ItemId?,
    val itemName: String,
)

data class SpecialItemView(
    val milestone: GameMilestoneId,
    val item: ItemId,
    val itemName: String,
)

data class PartyMemberView(
    val number: Int,
    val member: PartyMemberState,
    val weaponName: String,
    val armorName: String,
)

data class PartyViewModel(
    val resources: PartyResources,
    val specials: List<SpecialItemView>,
    val inventory: List<InventorySlotView>,
    val members: List<PartyMemberView>,
)

sealed interface PartyViewInput {
    data class Member(val memberNumber: Int) : PartyViewInput
    object All : PartyViewInput
    object Quit : PartyViewInput
    object Continue : PartyViewInput
}

enum class PartyViewStatus {
    Started,
    MemberSelected,
    AllSelected,
    Quit,
    Finished,
}

data class PartyViewResult(
    val status: PartyViewStatus,
    val memberNumbers: List<Int> = emptyList(),
)

sealed interface PartyViewStep {
    object ChooseMember : PartyViewStep
    data class MemberDetails(val memberNumbers: List<Int>) : PartyViewStep
    data class Finished(val result: PartyViewResult) : PartyViewStep
}

data class PartyViewSession(
    val party: PartyState,
    val itemRecords: List<ItemRecordModel> = emptyList(),
    val progress: GameProgressModel = GameProgressModel(emptyList(), null),
    val prompts: PartyPromptsModel = PartyPromptsModel(),
    val step: PartyViewStep = PartyViewStep.ChooseMember,
    val lastResult: PartyViewResult = PartyViewResult(PartyViewStatus.Started),
) {
    fun apply(input: PartyViewInput): PartyViewSession =
        when (val current = step) {
            PartyViewStep.ChooseMember -> when (input) {
                is PartyViewInput.Member -> {
                    val memberNumbers = listOf(input.memberNumber)
                        .filter { it in 1..party.members.size }
                    if (memberNumbers.isEmpty()) {
                        finish(PartyViewResult(PartyViewStatus.Quit))
                    } else {
                        copy(
                            step = PartyViewStep.MemberDetails(memberNumbers),
                            lastResult = PartyViewResult(PartyViewStatus.MemberSelected, memberNumbers),
                        )
                    }
                }
                PartyViewInput.All -> {
                    val memberNumbers = party.members.indices.map { it + 1 }
                    copy(
                        step = PartyViewStep.MemberDetails(memberNumbers),
                        lastResult = PartyViewResult(PartyViewStatus.AllSelected, memberNumbers),
                    )
                }
                PartyViewInput.Quit -> finish(PartyViewResult(PartyViewStatus.Quit))
                PartyViewInput.Continue -> this
            }
            is PartyViewStep.MemberDetails -> {
                require(input is PartyViewInput.Continue) { "party view is showing member details" }
                finish(PartyViewResult(PartyViewStatus.Finished, current.memberNumbers))
            }
            is PartyViewStep.Finished -> error("party view is already finished")
        }

    fun viewModel(): PartyViewModel {
        val byId = itemRecords.associateBy { it.id }
        return PartyViewModel(
            resources = party.resources,
            specials = progress.milestones.mapNotNull { milestone ->
                val item = milestone.specialInventoryItem
                    ?.takeIf { milestone.id in party.milestones }
                    ?: return@mapNotNull null
                SpecialItemView(
                    milestone = milestone.id,
                    item = item,
                    itemName = byId[item]?.displayName ?: item.slug,
                )
            },
            inventory = (1..InventorySaveSlotMax).map { slot ->
                val item = party.inventoryItem(slot)
                InventorySlotView(
                    slot = slot,
                    key = 'A' + slot - 1,
                    item = item,
                    itemName = item?.let { byId[it]?.displayName ?: it.slug }.orEmpty(),
                )
            },
            members = party.members.mapIndexed { index, member ->
                PartyMemberView(
                    number = index + 1,
                    member = member,
                    weaponName = itemName(member.weapon, byId),
                    armorName = itemName(member.armor, byId),
                )
            },
        )
    }

    private fun finish(result: PartyViewResult): PartyViewSession =
        copy(step = PartyViewStep.Finished(result), lastResult = result)
}

enum class PartyJournalStatus {
    Started,
    Empty,
    Shown,
}

data class PartyJournalResult(
    val status: PartyJournalStatus,
)

sealed interface PartyJournalInput {
    object Continue : PartyJournalInput
}

sealed interface PartyJournalStep {
    data class Show(val entries: List<JournalEntryState>) : PartyJournalStep
    data class Finished(val result: PartyJournalResult) : PartyJournalStep
}

data class PartyJournalSession(
    val party: PartyState,
    val prompts: PartyPromptsModel = PartyPromptsModel(),
    val step: PartyJournalStep = party.journal
        .takeIf { it.isNotEmpty() }
        ?.let(PartyJournalStep::Show)
        ?: PartyJournalStep.Finished(PartyJournalResult(PartyJournalStatus.Empty)),
    val lastResult: PartyJournalResult = when (step) {
        is PartyJournalStep.Show -> PartyJournalResult(PartyJournalStatus.Started)
        is PartyJournalStep.Finished -> step.result
    },
) {
    fun apply(input: PartyJournalInput): PartyJournalSession =
        when (val current = step) {
            is PartyJournalStep.Show -> when (input) {
                PartyJournalInput.Continue -> finish(PartyJournalResult(PartyJournalStatus.Shown))
            }
            is PartyJournalStep.Finished -> error("party journal is already finished: $current")
        }

    private fun finish(result: PartyJournalResult): PartyJournalSession =
        copy(step = PartyJournalStep.Finished(result), lastResult = result)
}

sealed interface InventoryDropInput {
    data class Slot(val slot: Int) : InventoryDropInput
    object Abort : InventoryDropInput
    object Continue : InventoryDropInput
}

enum class InventoryDropStatus {
    Started,
    Dropped,
    EmptySlot,
    Aborted,
}

fun <T> InventoryDropStatus.toParentStatus(
    dropped: T,
    unavailable: T,
): T =
    when (this) {
        InventoryDropStatus.Dropped -> dropped
        InventoryDropStatus.EmptySlot,
        InventoryDropStatus.Aborted,
        InventoryDropStatus.Started -> unavailable
    }

data class InventoryDropResult(
    val status: InventoryDropStatus,
    val party: PartyState,
    val slot: Int? = null,
    val item: ItemId? = null,
    val itemName: String? = null,
)

sealed interface InventoryDropStep {
    object ChooseSlot : InventoryDropStep
    data class Messages(val blocks: List<TextBlock>) : InventoryDropStep
    data class Finished(val result: InventoryDropResult) : InventoryDropStep
}

data class InventoryDropSession(
    val party: PartyState,
    val itemRecords: List<ItemRecordModel> = emptyList(),
    val realm: RealmContentModel = RealmContentModel(),
    val prompts: PartyPromptsModel = PartyPromptsModel(),
    val step: InventoryDropStep = InventoryDropStep.ChooseSlot,
    val lastResult: InventoryDropResult = InventoryDropResult(InventoryDropStatus.Started, party),
) {
    fun apply(input: InventoryDropInput): InventoryDropSession =
        when (step) {
            InventoryDropStep.ChooseSlot -> when (input) {
                is InventoryDropInput.Slot -> drop(input.slot)
                InventoryDropInput.Abort -> message(
                    InventoryDropResult(InventoryDropStatus.Aborted, party),
                    listOf(prompts.dropAborted),
                )
                InventoryDropInput.Continue -> this
            }
            is InventoryDropStep.Messages -> {
                require(input is InventoryDropInput.Continue) { "inventory drop is showing messages" }
                finish(lastResult)
            }
            is InventoryDropStep.Finished -> error("inventory drop is already finished")
        }

    fun viewModel(): PartyViewModel =
        PartyViewSession(
            party = party,
            itemRecords = itemRecords,
            prompts = prompts,
        ).viewModel()

    private fun drop(slot: Int): InventoryDropSession {
        return when (val outcome = party.dropInventorySlot(slot, itemRecords, realm)) {
            is InventoryDropOutcome.Dropped -> {
                val result = InventoryDropResult(
                    status = InventoryDropStatus.Dropped,
                    party = outcome.party,
                    slot = outcome.slot,
                    item = outcome.item,
                    itemName = outcome.itemName,
                )
                val messages = buildList {
                    add(prompts.renderDropped(outcome.itemName))
                    if (outcome.foodReduced) {
                        add(prompts.dropCapacityReduced)
                    }
                }
                copy(party = outcome.party).message(result, messages)
            }
            is InventoryDropOutcome.EmptySlot -> message(
                InventoryDropResult(InventoryDropStatus.EmptySlot, party, slot = outcome.slot),
                listOf(prompts.dropEmptySlot),
            )
            InventoryDropOutcome.Aborted -> message(
                InventoryDropResult(InventoryDropStatus.Aborted, party),
                listOf(prompts.dropAborted),
            )
        }
    }

    private fun message(result: InventoryDropResult, blocks: List<TextBlock>): InventoryDropSession =
        copy(step = InventoryDropStep.Messages(blocks), lastResult = result)

    private fun finish(result: InventoryDropResult): InventoryDropSession =
        copy(step = InventoryDropStep.Finished(result), lastResult = result)
}

private sealed interface InventoryDropOutcome {
    data class Dropped(
        val party: PartyState,
        val slot: Int,
        val item: ItemId,
        val itemName: String,
        val foodReduced: Boolean,
    ) : InventoryDropOutcome

    data class EmptySlot(val slot: Int) : InventoryDropOutcome
    object Aborted : InventoryDropOutcome
}

private fun PartyState.dropInventorySlot(
    slot: Int,
    itemRecords: List<ItemRecordModel>,
    realm: RealmContentModel,
): InventoryDropOutcome {
    if (slot !in 1..InventorySaveSlotMax) return InventoryDropOutcome.Aborted
    val item = inventoryItem(slot) ?: return InventoryDropOutcome.EmptySlot(slot)
    val name = itemName(item, itemRecords.associateBy { it.id })
    val removed = withInventoryItem(slot, null)
    val clamped = removed.clampResourcesToCapacity(itemRecords, realm)
    return InventoryDropOutcome.Dropped(
        party = clamped,
        slot = slot,
        item = item,
        itemName = name,
        foodReduced = clamped.resources.food < removed.resources.food,
    )
}

private fun itemName(item: ItemId, records: Map<ItemId, ItemRecordModel>): String =
    records[item]?.displayName ?: item.slug
