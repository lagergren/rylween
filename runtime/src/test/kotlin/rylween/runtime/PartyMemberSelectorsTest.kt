package rylween.runtime

import rylween.compiled.CompiledEquipment
import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledPartyCounters
import rylween.compiled.CompiledPartyMember
import rylween.dsl.MemberChoiceFilter
import rylween.dsl.PartyMemberSelection
import rylween.dsl.impl.Gender
import rylween.dsl.impl.GridPoint
import rylween.dsl.impl.PartyResources
import rylween.dsl.impl.PcClass
import rylween.dsl.impl.Race
import rylween.dsl.impl.of
import kotlin.test.Test
import kotlin.test.assertEquals

class PartyMemberSelectorsTest {
    @Test
    fun memberChoicesUseOneBasedPartyNumbersAndFiltersDeadMembers() {
        val party =
            party(
                member(pcClass = PcClass.Fighter),
                member(dead = true),
                member(pcClass = PcClass.Mage),
                member(pcClass = PcClass.Priest, dead = true),
            )

        assertEquals(listOf(1, 3), party.memberChoices(MemberChoiceFilter.AliveOnly))
        assertEquals(listOf(2, 4), party.memberChoices(MemberChoiceFilter.DeadOnly))
        assertEquals(listOf(3), party.memberChoices(MemberChoiceFilter.SpellcasterOnly))
    }

    @Test
    fun partyMemberSelectionReturnsZeroWhenNoMemberMatches() {
        assertEquals(2, listOf(2, 4).selectPartyMember(PartyMemberSelection.First) { error("not random") })
        assertEquals(
            4,
            listOf(2, 4).selectPartyMember(PartyMemberSelection.Random) { range ->
                assertEquals(0..1, range)
                1
            },
        )
        assertEquals(0, emptyList<Int>().selectPartyMember(PartyMemberSelection.Random) { error("no choices") })
    }

    private fun party(vararg members: CompiledPartyMember): PartyState =
        PartyState(
            id = CompiledHandleRef(CompiledHandleKind.Party, 1),
            playerString = 1,
            name = "Test",
            position = GridPoint(0, 0),
            facing = null,
            resources = PartyResources(gold = 0, torches = 0, food = 0, timeLeft = 0, restedHours = 0),
            counters =
                CompiledPartyCounters(
                    memberCount = null,
                    kills = 0,
                    waitingMail = false,
                    attacked = false,
                    attackedToday = emptyList(),
                ),
            flags = emptyList(),
            milestones = emptyList(),
            inventory = emptyList(),
            members = members.toList(),
            memberNames = members.indices.map { index -> "Member ${index + 1}" },
            visitedNpcs = emptyList(),
            journal = emptyList(),
            rngState = null,
        )

    private fun member(
        pcClass: PcClass = PcClass.Fighter,
        dead: Boolean = false,
    ): CompiledPartyMember =
        CompiledPartyMember(
            nameString = 1,
            gender = Gender.Male,
            race = Race.Human,
            pcClass = pcClass,
            level = 1,
            health = 10,
            hitPoints = 10 of 10,
            weaponSkill = 10,
            wisdom = 10,
            mana = 0 of 0,
            charisma = 10,
            equipment =
                CompiledEquipment(
                    weapon = CompiledHandleRef(CompiledHandleKind.Item, 1),
                    armor = CompiledHandleRef(CompiledHandleKind.Item, 2),
                ),
            experience = 0,
            dead = dead,
        )
}
