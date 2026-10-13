/**
 * Party facts the autopilot plans with: who is alive, who can cast or hunt,
 * how hurt the party is, and how far its quests have come.
 */

package rylween.runtime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledItemRecord
import rylween.compiled.CompiledPartyMember
import rylween.dsl.impl.PcClass

/** A party member together with its one-based number, as the runtime's member prompts count them. */
internal data class AutopilotMember(
    val number: Int,
    val member: CompiledPartyMember,
) {
    val alive: Boolean get() = !member.dead
    val hurt: Int get() = member.hitPoints.max - member.hitPoints.current
    val hitPointShare: Double get() = member.hitPoints.current.toDouble() / member.hitPoints.max.coerceAtLeast(1)
    val digit: Char get() = number.digitToChar()
}

internal class AutopilotParty(
    private val content: CompiledContentBundle,
) {
    fun members(party: PartyState): List<AutopilotMember> = party.members.mapIndexed { index, member -> AutopilotMember(index + 1, member) }

    fun living(party: PartyState): List<AutopilotMember> = members(party).filter(AutopilotMember::alive)

    fun dead(party: PartyState): List<AutopilotMember> = members(party).filterNot(AutopilotMember::alive)

    fun firstLiving(
        party: PartyState,
        pcClass: PcClass,
    ): AutopilotMember? = living(party).firstOrNull { member -> member.member.pcClass == pcClass }

    /** The member whose name the runtime printed, such as the one a battle asks "What now?". */
    fun named(
        party: PartyState,
        name: String,
    ): AutopilotMember? = members(party).firstOrNull { member -> content.strings.value(member.member.nameString) == name }

    fun name(member: AutopilotMember): String = content.strings.value(member.member.nameString)

    /** Whether [party] holds the story flag state with this slug, such as `garth.bandits.accepted`. */
    fun hasFlag(
        party: PartyState,
        slug: String,
    ): Boolean = content.refOrNull(CompiledHandleKind.FlagState, slug)?.let { flag -> flag in party.flags } ?: false

    /** The current state of story flag [flag], such as `accepted` for `garth.bandits`, or `none`. */
    fun flagState(
        party: PartyState,
        flag: String,
    ): String =
        party.flags
            .map(content::slug)
            .firstOrNull { slug -> slug.startsWith("$flag.") && slug.removePrefix("$flag.").none { char -> char == '.' } }
            ?.removePrefix("$flag.")
            ?: "none"

    fun hasMilestone(
        party: PartyState,
        slug: String,
    ): Boolean = content.refOrNull(CompiledHandleKind.Milestone, slug)?.let { milestone -> milestone in party.milestones } ?: false

    fun item(ref: CompiledHandleRef): CompiledItemRecord? = content.items.firstOrNull { item -> item.id == ref }

    fun itemName(ref: CompiledHandleRef): String? = item(ref)?.let { item -> content.strings.value(item.displayNameString) }

    /** The inventory items, in slot order. */
    fun inventory(party: PartyState): List<CompiledItemRecord> = party.inventory.mapNotNull { slot -> slot.item?.let(::item) }

    fun freeSlots(party: PartyState): Int = party.inventory.count { slot -> slot.item == null }

    fun inventoryCount(
        party: PartyState,
        itemSlug: String,
    ): Int = party.inventory.count { slot -> slot.item?.let(content::slug) == itemSlug }

    fun weaponDamage(member: CompiledPartyMember): Int = item(member.equipment.weapon)?.weaponDamage ?: 0

    fun armorClass(member: CompiledPartyMember): Int = item(member.equipment.armor)?.armorClass ?: 0
}
