package rylween.runtime

import rylween.compiled.ExternalCharacterCreationClassOption
import rylween.compiled.ExternalCharacterCreationPortrait
import rylween.compiled.ExternalCharacterCreationRaceOption
import rylween.compiled.ExternalCharacterCreationStart
import rylween.compiled.ExternalCharacterCreationStatRoll
import rylween.compiled.ExternalRuntimeCharacterCreation
import rylween.dsl.impl.Gender
import rylween.dsl.impl.GridPoint
import rylween.dsl.impl.PartyResources
import rylween.dsl.impl.PcClass
import rylween.dsl.impl.Race
import kotlin.test.Test
import kotlin.test.assertEquals

class RuntimeCharacterPortraitsTest {
    @Test
    fun classPortraitBeatsRacePortraitForPartyMemberPreview() {
        val preview =
            characterCreation()
                .creationPortraitPreview(
                    memberNumber = 4,
                    memberName = "Mira",
                    gender = Gender.Female,
                    race = Race.Human,
                    pcClass = PcClass.Priest,
                )

        assertEquals("portrait-female-priest", preview?.imageSlug)
    }

    private fun characterCreation(): ExternalRuntimeCharacterCreation =
        ExternalRuntimeCharacterCreation(
            statRoll = ExternalCharacterCreationStatRoll(min = 3, max = 18),
            start =
                ExternalCharacterCreationStart(
                    map = "wilderness",
                    position = GridPoint(1, 1),
                    facing = null,
                    resources = PartyResources(gold = 0, torches = 0, food = 0, timeLeft = 0, restedHours = 0),
                    partyMemberMax = 4,
                    level = 1,
                    dailyTimeLeft = 0,
                ),
            races = Race.entries.map { race -> ExternalCharacterCreationRaceOption(race, race.name) },
            classes =
                PcClass.entries.map { pcClass ->
                    ExternalCharacterCreationClassOption(
                        pcClass = pcClass,
                        label = pcClass.name,
                        requiredStat = null,
                        minimum = null,
                        failureText = null,
                    )
                },
            portraits =
                listOf(
                    ExternalCharacterCreationPortrait(null, null, null, "portrait-default"),
                    ExternalCharacterCreationPortrait(Gender.Female, null, null, "portrait-female-default"),
                    ExternalCharacterCreationPortrait(Gender.Female, Race.Human, null, "portrait-female-human"),
                    ExternalCharacterCreationPortrait(Gender.Female, null, PcClass.Priest, "portrait-female-priest"),
                ),
        )
}
