package rylween.compiledruntime

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class StandaloneCompiledSaveTest {
    @Test
    fun saveMemberRecordsMustMeetDslMinimum() {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                StandaloneCompiledSave.validateSavedPartyMemberCount(
                    mapOf(
                        "party.members" to "2",
                        "member.1.name" to "Aragorn",
                        "member.2.name" to "Legolas",
                    ),
                    requiredPartyMemberCount = 4,
                )
            }

        assertContains(failure.message.orEmpty(), "below DSL minimum 4")
    }

    @Test
    fun deadMembersStillCountAsSavedMemberRecords() {
        StandaloneCompiledSave.validateSavedPartyMemberCount(
            mapOf(
                "party.members" to "4",
                "party.memberCount" to "3",
                "member.1.name" to "Aragorn",
                "member.2.name" to "Legolas",
                "member.3.name" to "Gandalf",
                "member.4.name" to "Mira",
                "member.4.dead" to "true",
            ),
            requiredPartyMemberCount = 4,
        )
    }

    @Test
    fun explicitSaveMemberCountMustMatchMemberRecords() {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                StandaloneCompiledSave.validateSavedPartyMemberCount(
                    mapOf(
                        "party.members" to "4",
                        "member.1.name" to "Aragorn",
                        "member.2.name" to "Legolas",
                    ),
                    requiredPartyMemberCount = 4,
                )
            }

        assertContains(failure.message.orEmpty(), "party member count mismatch")
    }
}
