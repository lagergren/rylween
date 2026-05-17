package rylween.runtime

import rylween.compiled.CompiledPartyMember
import rylween.dsl.MemberChoiceFilter
import rylween.dsl.PartyMemberSelection
import rylween.dsl.impl.PcClass

internal fun PartyState.memberChoices(filter: MemberChoiceFilter): List<Int> =
    members.mapIndexedNotNull { index, member ->
        (index + 1).takeIf { member.matches(filter) }
    }

internal fun List<Int>.selectPartyMember(
    selection: PartyMemberSelection,
    randomIndex: (IntRange) -> Int,
): Int =
    when {
        isEmpty() -> 0
        selection == PartyMemberSelection.First -> first()
        else -> this[randomIndex(indices)]
    }

private fun CompiledPartyMember.matches(filter: MemberChoiceFilter): Boolean =
    when (filter) {
        MemberChoiceFilter.Any -> true
        MemberChoiceFilter.AliveOnly -> !dead
        MemberChoiceFilter.DeadOnly -> dead
        MemberChoiceFilter.SpellcasterOnly -> !dead && pcClass in setOf(PcClass.Mage, PcClass.Priest)
    }
