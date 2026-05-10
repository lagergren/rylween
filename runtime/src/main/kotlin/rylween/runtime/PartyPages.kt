/**
 * Party and journal views for the runtime.
 *
 * These views render only direct compiled rows: party/member snapshots, item
 * records, journal entries, and the shared string/handle tables. They are kept
 * separate from `RylweenRuntimeMain.kt` because they are reusable
 * wilderness commands and should stay independent from the old terminal
 * adapter/session implementation.
 */

package rylween.runtime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledItemRecord
import rylween.compiled.CompiledJournalEntry
import rylween.compiled.CompiledPartyMember

/** Builds text-window pages for runtime party state. */
internal object PartyPages {
    private const val TEXT_PAGE_LINES = 9

    fun partyPages(
        content: CompiledContentBundle,
        party: PartyState,
    ): List<List<String>> =
        buildList {
            add(partySummaryPage(party))
            party.members.forEachIndexed { index, member -> add(memberPage(content, party, index, member)) }
            addAll(inventoryPages(content, party))
        }

    fun journalLines(
        content: CompiledContentBundle,
        party: PartyState,
    ): List<String> =
        buildList {
            add("Journal")
            add("")
            if (party.journal.isEmpty()) {
                add("No journal entries.")
            } else {
                party.journal.forEach { entry -> add(journalLine(content, entry)) }
            }
        }

    private fun partySummaryPage(party: PartyState): List<String> =
        listOf(
            "Party: ${party.name}",
            "",
            "Gold: ${party.resources.gold}",
            "Food: ${party.resources.food}",
            "Torches: ${party.resources.torches}",
            "Moves: ${party.resources.timeLeft}",
            "Rest: ${party.resources.restedHours}",
            "Kills: ${party.counters.kills}",
            "Members: ${party.members.size}",
        )

    private fun memberPage(
        content: CompiledContentBundle,
        party: PartyState,
        index: Int,
        member: CompiledPartyMember,
    ): List<String> =
        listOf(
            "${index + 1}. ${party.memberName(content, index)}",
            "${member.race.readableName()} ${member.pcClass.readableName()}",
            "Skill ${member.weaponSkill}  HP ${member.hitPoints.current}/${member.hitPoints.max}",
            "Health ${member.health}  Wisdom ${member.wisdom}",
            "Charisma ${member.charisma}  Magic ${member.mana.current}/${member.mana.max}",
            "Level ${member.level}  XP ${member.experience}",
            "Weapon: ${content.itemDisplayName(member.equipment.weapon)}",
            "Armor: ${content.itemDisplayName(member.equipment.armor)}",
            "Status: ${member.statusName()}",
        )

    private fun inventoryPages(
        content: CompiledContentBundle,
        party: PartyState,
    ): List<List<String>> {
        val inventory = inventoryLines(content, party)
        return inventory.chunked(TEXT_PAGE_LINES - 1).map { page -> listOf("Inventory:") + page }
    }

    private fun inventoryLines(
        content: CompiledContentBundle,
        party: PartyState,
    ): List<String> =
        party.inventory
            .mapNotNull { slot ->
                slot.item?.let { item -> "${slot.slot}. ${content.itemDisplayName(item)}" }
            }

    private fun journalLine(
        content: CompiledContentBundle,
        entry: CompiledJournalEntry,
    ): String =
        when (entry) {
            is CompiledJournalEntry.Line -> content.strings.value(entry.textString)
            is CompiledJournalEntry.Text -> content.slug(entry.text).readableSlug()
        }

    private fun CompiledContentBundle.itemDisplayName(item: CompiledHandleRef): String =
        items
            .firstOrNull { record: CompiledItemRecord -> record.id == item }
            ?.let { record -> strings.value(record.displayNameString) }
            ?: slug(item).readableSlug()

    private fun CompiledPartyMember.statusName(): String = if (dead) "Dead" else "Alive"

    private fun Enum<*>.readableName(): String = name.readableSlug()
}
