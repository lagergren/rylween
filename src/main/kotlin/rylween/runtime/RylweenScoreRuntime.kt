package rylween.runtime

import rylween.dsl.GameMilestoneId
import rylween.dsl.GameModel
import rylween.dsl.ItemId
import rylween.dsl.ItemRecordModel
import rylween.dsl.PartyState
import rylween.dsl.PartyMemberState
import rylween.dsl.PcClass
import rylween.dsl.SourceSpecialField
import rylween.dsl.StateModel

data class ScorePowerItem(
    val milestone: GameMilestoneId,
    val name: String,
)

data class ScoreReportEntry(
    val rank: Int,
    val partyName: String,
    val leaderName: String,
    val classes: List<PcClass>,
    val kills: Int,
    val averageExperience: Int,
    val averageLevel: Int,
    val powerItems: List<ScorePowerItem>,
)

data class ScoreReport(
    val entries: List<ScoreReportEntry>,
)

fun StateModel.toRuntimeSessionState(rngState: Long? = realm.rngState): RuntimeSessionState =
    RuntimeSessionState(
        realm = realm.copy(rngState = rngState),
        parties = parties,
        npcs = npcs,
        locations = locations,
    )

fun RuntimeSessionState.scoreReport(content: GameModel): ScoreReport {
    val itemRecords = content.itemCatalog.associateBy(ItemRecordModel::id)
    val powerItems = content.scorePowerItems(itemRecords)
    val sorted = parties
        .withIndex()
        .sortedWith(compareByDescending<IndexedValue<PartyState>> { entry -> entry.value.averageExperience() })
        .map(IndexedValue<PartyState>::value)
    return ScoreReport(
        entries = sorted.mapIndexed { index, party ->
            ScoreReportEntry(
                rank = index + 1,
                partyName = party.name,
                leaderName = party.members.firstOrNull()?.name.orEmpty(),
                classes = party.members.map(PartyMemberState::pcClass),
                kills = party.counters.kills,
                averageExperience = party.averageExperience(),
                averageLevel = party.averageLevel(),
                powerItems = powerItems.filter { item -> item.milestone in party.milestones },
            )
        },
    )
}

private fun GameModel.scorePowerItems(records: Map<ItemId, ItemRecordModel>): List<ScorePowerItem> =
    progress.milestones
        .withIndex()
        .mapNotNull { (index, milestone) ->
            val item = milestone.specialInventoryItem ?: return@mapNotNull null
            val record = records[item]
            IndexedScorePowerItem(
                index = index,
                sourceOrder = milestone.sourceSpecial?.field?.scoreOrder(),
                item = ScorePowerItem(
                    milestone = milestone.id,
                    name = record?.displayName ?: item.slug,
                ),
            )
        }
        .sortedWith(compareBy<IndexedScorePowerItem> { it.sourceOrder ?: Int.MAX_VALUE }.thenBy { it.index })
        .map(IndexedScorePowerItem::item)

private data class IndexedScorePowerItem(
    val index: Int,
    val sourceOrder: Int?,
    val item: ScorePowerItem,
)

private fun SourceSpecialField.scoreOrder(): Int =
    when (this) {
        SourceSpecialField.Fireshield -> 1
        SourceSpecialField.Bracelets -> 2
        SourceSpecialField.Mastercrown -> 3
        SourceSpecialField.Silverkey -> 4
        SourceSpecialField.Jazzyfoot -> 5
    }

private fun PartyState.averageExperience(): Int =
    averageOfMembers(PartyMemberState::experience)

private fun PartyState.averageLevel(): Int =
    averageOfMembers(PartyMemberState::level)

private fun PartyState.averageOfMembers(value: (PartyMemberState) -> Int): Int {
    val divisor = members.size.coerceAtLeast(1)
    return members.sumOf(value) / divisor
}
