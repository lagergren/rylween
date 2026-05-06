/**
 * Runtime score and hall-of-heroes report generation.
 *
 * `frontend/terminal/ScoreTerminal.kt` renders these reports for static
 * screens, while future persistent scoreboards can reuse the report model
 * without depending on terminal formatting.
 */
package rylween.runtime.score

import rylween.dsl.GameMilestoneId
import rylween.dsl.GameModel
import rylween.dsl.ItemId
import rylween.dsl.ItemRecordModel
import rylween.dsl.PartyMemberState
import rylween.dsl.PartyState
import rylween.dsl.PcClass
import rylween.dsl.SourceSpecialField
import rylween.runtime.RuntimeSessionState
import rylween.runtime.party

/** Special milestone item shown in a party score report. */
data class ScorePowerItem(
    val milestone: GameMilestoneId,
    val name: String,
)

/** One ranked party row in the score report. */
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

/** Ranked score report for every runtime party. */
data class ScoreReport(
    val entries: List<ScoreReportEntry>,
)

/** One party entry eligible for the hall-of-heroes static screen. */
data class HallOfHeroesEntry(
    val partyName: String,
)

/** Hall-of-heroes report derived from parties that completed the win milestone. */
data class HallOfHeroesReport(
    val entries: List<HallOfHeroesEntry>,
)

/** Builds the score report for the current runtime state and authored content. */
fun RuntimeSessionState.scoreReport(content: GameModel): ScoreReport {
    val itemRecords = content.itemCatalog.associateBy(ItemRecordModel::id)
    val powerItems = content.scorePowerItems(itemRecords)
    val sorted =
        parties
            .withIndex()
            .sortedWith(compareByDescending { entry -> entry.value.averageExperience() })
            .map(IndexedValue<PartyState>::value)
    return ScoreReport(
        entries =
            sorted.mapIndexed { index, party ->
                ScoreReportEntry(
                    rank = index + 1,
                    partyName = party.name,
                    leaderName =
                        party.members
                            .firstOrNull()
                            ?.name
                            .orEmpty(),
                    classes = party.members.map(PartyMemberState::pcClass),
                    kills = party.counters.kills,
                    averageExperience = party.averageExperience(),
                    averageLevel = party.averageLevel(),
                    powerItems = powerItems.filter { item -> item.milestone in party.milestones },
                )
            },
    )
}

/** Builds the hall-of-heroes report for parties that satisfy the win condition. */
fun RuntimeSessionState.hallOfHeroesReport(content: GameModel): HallOfHeroesReport {
    val completion = content.progress.win?.completedBy ?: return HallOfHeroesReport(emptyList())
    val entries =
        parties
            .filter { party -> completion in party.milestones }
            .sortedWith(compareBy<PartyState> { party -> party.name.lowercase() }.thenBy { party -> party.id.slug })
            .map { party -> HallOfHeroesEntry(partyName = party.name) }
    return HallOfHeroesReport(entries)
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
                item =
                    ScorePowerItem(
                        milestone = milestone.id,
                        name = record?.displayName ?: item.slug,
                    ),
            )
        }.sortedWith(compareBy<IndexedScorePowerItem> { it.sourceOrder ?: Int.MAX_VALUE }.thenBy { it.index })
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

private fun PartyState.averageExperience(): Int = averageOfMembers(PartyMemberState::experience)

private fun PartyState.averageLevel(): Int = averageOfMembers(PartyMemberState::level)

private fun PartyState.averageOfMembers(value: (PartyMemberState) -> Int): Int {
    val divisor = members.size.coerceAtLeast(1)
    return members.sumOf(value) / divisor
}
