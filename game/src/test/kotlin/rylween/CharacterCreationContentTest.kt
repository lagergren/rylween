package rylween

import rylween.compiled.ExternalPrerolledMember
import rylween.compiled.readExternalRuntimeSystems
import rylween.compiled.toExternalBundleArtifact
import rylween.dsl.collectAuthoredContentDiagnostics
import rylween.dsl.impl.CharacterCreationStatRollModel
import rylween.dsl.impl.Gender
import rylween.dsl.impl.PcClass
import rylween.dsl.impl.Race
import rylween.game.RylweenCompiledGame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CharacterCreationContentTest {
    @Test
    fun prerolledRosterAndChoicePromptsSurviveBundleExport() {
        val systems = RylweenCompiledGame.nextRylween.toExternalBundleArtifact().readExternalRuntimeSystems()
        assertEquals(
            listOf(
                ExternalPrerolledMember("Ronan", Gender.Male, Race.Human, PcClass.Fighter),
                ExternalPrerolledMember("Brynja", Gender.Female, Race.Dwarf, PcClass.Ranger),
                ExternalPrerolledMember("Elowen", Gender.Female, Race.Elf, PcClass.Mage),
                ExternalPrerolledMember("Walter", Gender.Male, Race.Human, PcClass.Priest),
            ),
            checkNotNull(systems.characterCreation).prerolledMembers,
        )
        val prompts = systems.prompts.characterCreation
        assertEquals("Roll your own", prompts.manualLabel)
        assertEquals("Pre-rolled", prompts.prerolledLabel)
        assertTrue(prompts.method.render({ null }).any { "Choose your party:" in it })
    }

    @Test
    fun invalidPrerolledRostersFailAuthoringValidation() {
        val game = RylweenCompiledGame.nextDsl
        val rules = checkNotNull(game.characterCreation)
        val invalidRules =
            listOf(
                rules.copy(prerolledMembers = rules.prerolledMembers.dropLast(1)),
                rules.copy(statRoll = CharacterCreationStatRollModel(15, 49)),
                rules.copy(races = rules.races.filter { it.race != Race.Dwarf }),
                rules.copy(classes = rules.classes.filter { it.klass != PcClass.Priest }),
                rules.copy(prerolledMembers = rules.prerolledMembers.map { it.copy(name = " ") }),
                rules.copy(prerolledMembers = rules.prerolledMembers.map { it.copy(name = "x".repeat(25)) }),
            )
        invalidRules.forEach { invalid ->
            val diagnostics = collectAuthoredContentDiagnostics(game.copy(characterCreation = invalid))
            assertTrue(diagnostics.any { it.path == "characterCreation" }, "invalid roster was accepted: $invalid")
        }
        val withoutPreset = game.copy(characterCreation = rules.copy(prerolledMembers = emptyList()))
        assertTrue(collectAuthoredContentDiagnostics(withoutPreset).none { it.path == "characterCreation" })
    }
}
