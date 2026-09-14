package rylween.runtime

import rylween.compiled.readExternalCompiledBundleContentHash
import rylween.compiled.readExternalCompiledGameBundle
import rylween.dsl.impl.Gender
import rylween.dsl.impl.GridPoint
import rylween.dsl.impl.PcClass
import rylween.dsl.impl.Race
import rylween.terminal.AnsiTerminal
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrerolledPartyTest {
    private val bundleFile = File(checkNotNull(System.getProperty("rylween.runtime.bundle")))
    private val bundle = bundleFile.readExternalCompiledGameBundle()
    private val bundleHash = bundleFile.readExternalCompiledBundleContentHash()
    private val factory = RuntimeSessionFactory.load(bundleFile)

    @Test
    fun generatedPartiesMeetClassRequirementsAndKeepTheirRollsOnReload() =
        withSaveFile { file ->
            val rolls = mutableSetOf<List<Int>>()
            repeat(16) { seed ->
                val user = "player-$seed"
                val output = runSession(file, user, "x N 2 \"Dawnward $seed\"", seed.toLong())
                assertContains(output, "1. Roll your own")
                assertContains(output, "2. Pre-rolled")
                assertFalse("Reroll (y/N)" in output)

                val runtime = read(file, user)
                val party = runtime.party
                assertEquals("Dawnward $seed", party.name)
                assertEquals(listOf("Ronan", "Brynja", "Elowen", "Walter"), party.memberNames)
                assertEquals(listOf(Gender.Male, Gender.Female, Gender.Female, Gender.Male), party.members.map { it.gender })
                assertEquals(listOf(Race.Human, Race.Dwarf, Race.Elf, Race.Human), party.members.map { it.race })
                assertEquals(listOf(PcClass.Fighter, PcClass.Ranger, PcClass.Mage, PcClass.Priest), party.members.map { it.pcClass })
                assertTrue(party.members[0].weaponSkill >= 50)
                assertTrue(party.members[2].wisdom >= 50)
                assertTrue(party.members[3].wisdom >= 50)
                party.members.forEach { member ->
                    val stats = listOf(member.weaponSkill, member.wisdom, member.health, member.charisma, member.dexterity)
                    assertTrue(stats.all { it in 15..70 }, "all generated stats must use the normal starting range")
                    rolls += stats
                    assertEquals(1, member.level)
                    assertEquals(0, member.experience)
                    assertFalse(member.dead)
                    assertEquals(member.health, member.hitPoints.current)
                    assertEquals(member.health, member.hitPoints.max)
                    val expectedMana = if (member.pcClass in setOf(PcClass.Mage, PcClass.Priest)) member.wisdom else 0
                    assertEquals(expectedMana, member.mana.current)
                    assertEquals(member.wisdom, member.mana.max)
                    assertEquals("blank", bundle.content.slug(member.equipment.weapon))
                    assertEquals("blank", bundle.content.slug(member.equipment.armor))
                }
                assertEquals(GridPoint(12, 9), party.position)
                assertEquals(1_000, party.resources.gold)
                assertEquals(5, party.resources.torches)
                assertEquals(100, party.resources.food)
                assertTrue(party.inventory.all { it.item == null })
                assertTrue(party.journal.isEmpty())
                assertTrue(party.milestones.isEmpty())
                assertEquals(0, party.counters.kills)

                val resumed = runSession(file, user, "x L")
                assertContains(resumed, "Wilderness")
                assertEquals(party, read(file, user).party, "continuing must keep the saved names, stats, and resources")
            }
            assertTrue(rolls.size > 16, "the generator should draw fresh stats rather than reuse a fixed test party")
            assertEquals("16", SaveCodec.decodeValues(file.readLines())["users.count"])
            assertEquals("Dawnward 0", read(file, "player-0").party.name)
        }

    @Test
    fun invalidChoiceRetriesAndAbandoningCreationDoesNotOverwriteSaves() =
        withSaveFile { file ->
            val output = runSession(file, "owner", "x N Z 2")
            assertContains(output, "Invalid input.")
            assertContains(output, "What is the name of your")
            assertFalse(file.exists(), "closing before naming the party must not create a save")

            runSession(file, "owner", "x N 2 \"Original\"")
            val original = file.readText()
            runSession(file, "owner", "x N N")
            assertEquals(original, file.readText(), "declining replacement must keep the existing save")
            runSession(file, "owner", "x N Y")
            assertEquals(original, file.readText(), "closing at the method menu must keep the existing save")
            runSession(file, "owner", "x N Y 2")
            assertEquals(original, file.readText(), "closing at the party-name prompt must keep the existing save")
        }

    @Test
    fun replacingAPartyPreservesOtherUsers() =
        withSaveFile { file ->
            runSession(file, "owner", "x N 2 \"Original\"")
            runSession(file, "neighbor", "x N 2 \"Neighbors\"")
            val neighbor = read(file, "neighbor").party
            runSession(file, "owner", "x N Y 2 \"New Dawn\"", seed = 91)
            assertEquals("New Dawn", read(file, "owner").party.name)
            assertEquals(neighbor, read(file, "neighbor").party)
        }

    private fun runSession(
        file: File,
        user: String,
        script: String,
        seed: Long = 42,
    ): String {
        val terminal = CreationTestTerminal()
        RuntimeLog.open(file.resolveSibling("session.log")).use { log ->
            factory.start(
                terminal,
                RuntimeSessionConfig(
                    userId = user,
                    stateSlug = "fresh-replay",
                    saveFile = file,
                    scriptedInput = script,
                    seed = seed,
                    showTitleMenu = true,
                    sessionDate = LocalDate.of(2026, 9, 14),
                ),
                log,
            )
        }
        return terminal.output.toString()
    }

    private fun read(
        file: File,
        user: String,
    ): RuntimeState =
        SaveFile.read(
            file,
            bundleHash,
            bundle.content,
            { slug -> bundle.states.single { bundle.content.slug(it.state) == slug } },
            user,
        )

    private fun withSaveFile(action: (File) -> Unit) {
        val directory = Files.createTempDirectory("rylween-prerolled-").toFile()
        try {
            action(directory.resolve("world.rsave"))
        } finally {
            directory.deleteRecursively()
        }
    }
}

private class CreationTestTerminal : AnsiTerminal {
    val output = StringBuilder()

    override fun writeRaw(text: String) {
        output.append(text)
    }

    override fun flush() = Unit

    override fun readKeyToken(): String? = null

    override fun readTextLine(maxLength: Int?): String? = null

    override fun pause(ms: Int) = Unit

    override fun close() = Unit
}
