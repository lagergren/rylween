/**
 * Party and journal views for the standalone compiled-bundle runtime.
 *
 * These views render only direct compiled rows: party/member snapshots, item
 * records, journal entries, and the shared string/handle tables. They are kept
 * separate from `RylweenCompiledRuntimeMain.kt` because they are reusable
 * wilderness commands and should stay independent from the old terminal
 * adapter/session implementation.
 */

package rylween.compiledruntime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledItemRecord
import rylween.compiled.CompiledJournalEntry
import rylween.compiled.CompiledPartyMember

/** Builds text-window pages for standalone compiled-runtime party state. */
internal object StandalonePartyPages {
    fun partyLines(
        content: CompiledContentBundle,
        party: StandaloneParty,
    ): List<String> =
        buildList {
            add("Party: ${party.name}")
            add("Gold ${party.resources.gold}  Food ${party.resources.food}")
            add("Torches ${party.resources.torches}  Moves ${party.resources.timeLeft}")
            add("Rest ${party.resources.restedHours}  Kills ${party.counters.kills}")
            add("")
            party.members.forEachIndexed { index, member ->
                addAll(memberSummary(content, party, index, member))
            }
            val inventory = inventoryLines(content, party)
            if (inventory.isNotEmpty()) {
                add("")
                add("Inventory:")
                addAll(inventory)
            }
        }

    fun journalLines(
        content: CompiledContentBundle,
        party: StandaloneParty,
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

    private fun memberSummary(
        content: CompiledContentBundle,
        party: StandaloneParty,
        index: Int,
        member: CompiledPartyMember,
    ): List<String> {
        val status = if (member.dead) "dead" else "alive"
        return listOf(
            "${index + 1}. ${party.memberName(content, index)} L${member.level} ${member.race.shortName()} ${member.pcClass.shortName()}",
            "   HP ${member.hitPoints.current}/${member.hitPoints.max} MP ${member.mana.current}/${member.mana.max} $status",
        )
    }

    private fun inventoryLines(
        content: CompiledContentBundle,
        party: StandaloneParty,
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

    private fun rylween.dsl.Race.shortName(): String =
        when (this) {
            rylween.dsl.Race.Human -> "Hum"
            rylween.dsl.Race.Dwarf -> "Dwf"
            rylween.dsl.Race.Elf -> "Elf"
            rylween.dsl.Race.Halfling -> "Hlf"
        }

    private fun rylween.dsl.PcClass.shortName(): String =
        when (this) {
            rylween.dsl.PcClass.Fighter -> "Fgt"
            rylween.dsl.PcClass.Priest -> "Pri"
            rylween.dsl.PcClass.Mage -> "Mag"
            rylween.dsl.PcClass.Thief -> "Thf"
            rylween.dsl.PcClass.Bard -> "Brd"
            rylween.dsl.PcClass.Ranger -> "Rgr"
        }
}
