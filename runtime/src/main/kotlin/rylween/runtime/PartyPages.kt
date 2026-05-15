/**
 * Party and journal views for the runtime.
 *
 * These views render only direct compiled rows: party/member snapshots, item
 * records, journal entries, and the shared string/handle tables. They are kept
 * separate from `RylweenRuntimeMain.kt` because they are reusable
 * wilderness commands and should stay independent of the old terminal
 * adapter/session implementation.
 */

package rylween.runtime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledItemRecord
import rylween.compiled.CompiledPartyMember
import rylween.compiled.ExternalRuntimeSystems

/** Builds text-window pages for runtime party state. */
internal object PartyPages {
    private const val TEXT_PAGE_LINES = 9

    private fun partyPageViews(
        content: CompiledContentBundle,
        party: PartyState,
    ): List<RuntimePartyPageView> =
        buildList {
            add(summaryPageView(party))
            party.members.forEachIndexed { index, member ->
                add(memberPageView(content, party, index, member))
            }
            addAll(inventoryPageViews(content, party))
        }

    fun journalLines(party: PartyState): List<String> =
        buildList {
            add("Journal")
            add("")
            if (party.journal.isEmpty()) {
                add("No journal entries.")
            } else {
                addAll(party.journal)
            }
        }

    fun showPartyPageViews(
        content: CompiledContentBundle,
        systems: ExternalRuntimeSystems,
        input: RuntimeInputPort,
        screen: RuntimeScreenPort,
        runtime: RuntimeState,
    ): Boolean {
        val pauseLine = PausePrompts(systems.prompts).line()
        val creation = systems.characterCreation
        return try {
            partyPageViews(content, runtime.party).forEach { page ->
                val preview = page.memberIndex?.let { index -> creation?.partyPortraitPreview(content, runtime.party, index) }
                screen.showCharacterPortrait(preview)
                screen.showPartyPage(page, pauseLine)
                if (input.readCommand() == null) return false
            }
            true
        } finally {
            screen.showCharacterPortrait(null)
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

    private fun summaryPageView(party: PartyState): RuntimePartyPageView =
        RuntimePartyPageView(
            kind = RuntimePartyPageKind.Summary,
            lines = partySummaryPage(party),
            summary =
                RuntimePartySummaryView(
                    partyName = party.name,
                    gold = party.resources.gold,
                    food = party.resources.food,
                    torches = party.resources.torches,
                    moves = party.resources.timeLeft,
                    rest = party.resources.restedHours,
                    kills = party.counters.kills,
                    memberCount = party.members.size,
                ),
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

    private fun memberPageView(
        content: CompiledContentBundle,
        party: PartyState,
        index: Int,
        member: CompiledPartyMember,
    ): RuntimePartyPageView =
        RuntimePartyPageView(
            kind = RuntimePartyPageKind.Member,
            lines = memberPage(content, party, index, member),
            member =
                RuntimePartyMemberView(
                    index = index,
                    name = party.memberName(content, index),
                    gender = member.gender.readableName(),
                    race = member.race.readableName(),
                    pcClass = member.pcClass.readableName(),
                    weaponSkill = member.weaponSkill,
                    health = member.health,
                    wisdom = member.wisdom,
                    charisma = member.charisma,
                    hitPoints = member.hitPoints.current,
                    hitPointMax = member.hitPoints.max,
                    mana = member.mana.current,
                    manaMax = member.mana.max,
                    level = member.level,
                    experience = member.experience,
                    weapon = content.itemDisplayName(member.equipment.weapon),
                    armor = content.itemDisplayName(member.equipment.armor),
                    status = member.statusName(),
                ),
        )

    private fun inventoryPages(
        content: CompiledContentBundle,
        party: PartyState,
    ): List<List<String>> {
        val inventory = inventoryLines(content, party)
        return inventory.chunked(TEXT_PAGE_LINES - 1).map { page -> listOf("Inventory:") + page }
    }

    private fun inventoryPageViews(
        content: CompiledContentBundle,
        party: PartyState,
    ): List<RuntimePartyPageView> =
        inventoryPages(content, party).map { lines ->
            RuntimePartyPageView(
                kind = RuntimePartyPageKind.Inventory,
                lines = lines,
                inventory = RuntimePartyInventoryView(lines.drop(1)),
            )
        }

    private fun inventoryLines(
        content: CompiledContentBundle,
        party: PartyState,
    ): List<String> =
        party.inventory
            .mapNotNull { slot ->
                slot.item?.let { item -> "${slot.slot}. ${content.itemDisplayName(item)}" }
            }

    private fun CompiledContentBundle.itemDisplayName(item: CompiledHandleRef): String =
        items
            .firstOrNull { record: CompiledItemRecord -> record.id == item }
            ?.let { record -> strings.value(record.displayNameString) }
            ?: slug(item).readableSlug()

    private fun CompiledPartyMember.statusName(): String = if (dead) "Dead" else "Alive"

    private fun Enum<*>.readableName(): String = name.readableSlug()
}
