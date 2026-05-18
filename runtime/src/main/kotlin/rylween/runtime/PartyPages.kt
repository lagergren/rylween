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
import rylween.compiled.ExternalRuntimeTextBlock

/** Builds text-window pages for runtime party state. */
internal object PartyPages {
    private const val TEXT_PAGE_LINES = 9
    private const val PARTY_PAGE_NAVIGATION_HINT = "Left/Right browse pages, RETURN/ESC back"

    private fun partyPageViews(
        content: CompiledContentBundle,
        systems: ExternalRuntimeSystems,
        party: PartyState,
    ): List<RuntimePartyPageView> =
        buildList {
            add(summaryPageView(party))
            party.members.forEachIndexed { index, member ->
                add(memberPageView(content, party, index, member))
            }
            addAll(inventoryPageViews(content, systems, party))
        }

    fun journalLines(
        systems: ExternalRuntimeSystems,
        party: PartyState,
        wrapWidth: Int?,
    ): List<String> =
        buildList {
            addAll(
                systems.prompts.party.journalHeader
                    .renderPartyText(wrapWidth),
            )
            if (party.journal.isEmpty()) {
                addAll(
                    systems.prompts.party.journalEmpty
                        .renderPartyText(wrapWidth),
                )
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
        val pages = partyPageViews(content, systems, runtime.party)
        if (pages.isEmpty()) return true
        val creation = systems.characterCreation
        var pageIndex = 0
        var renderedPageIndex: Int? = null
        try {
            while (true) {
                if (renderedPageIndex != pageIndex) {
                    val page = pages[pageIndex]
                    val preview = page.memberIndex?.let { index -> creation?.partyPortraitPreview(content, runtime.party, index) }
                    screen.showCharacterPortrait(preview)
                    screen.showPartyPage(page, PARTY_PAGE_NAVIGATION_HINT)
                    renderedPageIndex = pageIndex
                }
                when (input.readCommand()) {
                    null -> return false
                    '\n',
                    RuntimeInputTokens.ESCAPE_COMMAND,
                    -> return true
                    '!' -> renderedPageIndex = null
                    '4',
                    '8',
                    -> pageIndex = previousPageIndex(pageIndex, pages.size)
                    '6',
                    '2',
                    -> pageIndex = nextPageIndex(pageIndex, pages.size)
                }
            }
        } finally {
            screen.showCharacterPortrait(null)
        }
        return true
    }

    private fun previousPageIndex(
        index: Int,
        count: Int,
    ): Int = (index + count - 1) % count

    private fun nextPageIndex(
        index: Int,
        count: Int,
    ): Int = (index + 1) % count

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
        systems: ExternalRuntimeSystems,
        party: PartyState,
    ): List<List<String>> {
        val inventory = inventoryLines(content, party)
        val header =
            systems.prompts.party.inventoryHeader
                .renderPartyText(wrapWidth = null)
        val bodyLines = (TEXT_PAGE_LINES - header.size).coerceAtLeast(1)
        return inventory.chunked(bodyLines).map { page -> header + page }
    }

    private fun inventoryPageViews(
        content: CompiledContentBundle,
        systems: ExternalRuntimeSystems,
        party: PartyState,
    ): List<RuntimePartyPageView> {
        val headerLineCount =
            systems.prompts.party.inventoryHeader
                .renderPartyText(wrapWidth = null)
                .size
        return inventoryPages(content, systems, party).map { lines ->
            RuntimePartyPageView(
                kind = RuntimePartyPageKind.Inventory,
                lines = lines,
                inventory = RuntimePartyInventoryView(lines.drop(headerLineCount)),
            )
        }
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

    private fun ExternalRuntimeTextBlock.renderPartyText(wrapWidth: Int?): List<String> = render({ null }, wrapWidth)
}
