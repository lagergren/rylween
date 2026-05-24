package rylween.runtime

import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeSystems
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeReplayPlanTest {
    private val executable = File(requiredProperty("rylween.runtime.executable"))
    private val bundleFile = File(requiredProperty("rylween.runtime.bundle"))
    private val javaHome = File(requiredProperty("rylween.runtime.javaHome"))
    private val testTmp = File(requiredProperty("rylween.runtime.testTmp")).also(File::mkdirs)
    private val bundle = bundleFile.readExternalCompiledGameBundle()
    private val systems = bundleFile.readExternalRuntimeSystems()

    @Test
    fun townServiceReplayPlansRunThroughInstalledRuntime() {
        runReplay(
            "town-social-smoke",
            expected =
                listOf(
                    "Entering Thord",
                    "(M)erchant",
                    "(I)nn",
                    "(T)avern",
                    "(S)mith",
                    "(C)hurch",
                    "(G)uild",
                    "(L)eave",
                    "The smith welcomes",
                    "The smith offers you some",
                    "Do you want supper,",
                    "The innkeeper leaves.",
                    "Welcome! Have a seat",
                ),
            forbidden = listOf("No such service.", "1. Merchant", "2. Inn", "3. Tavern", "4. Smith"),
        )
        runReplay(
            "town-garth-reward-smoke",
            seed = 2,
            expected =
                listOf(
                    "The merchant is GRATEFUL!",
                    "You pick up the gem.",
                ),
        )
        runReplay(
            "town-mission-intro-route",
            expected =
                listOf(
                    "In fact, I have a mission",
                    "You persuade him to",
                    "You take the sword.",
                    "- OH! A message from my",
                    "precious map",
                    "Ah, a seeker",
                    "Erethor's Lute",
                ),
        )
        runReplay(
            "town-silver-key-chain-route",
            expected =
                listOf(
                    "Tokawa, the keeper",
                    "Luqas, Innkeeper",
                    "sold the Key",
                    "Nah, sorry",
                    "Find them and",
                ),
        )
    }

    @Test
    fun missionDiscoveryReplayPlansRunThroughInstalledRuntime() {
        runReplay(
            "silver-key-discovery-route",
            expected =
                listOf(
                    "precious map",
                    "This room shines like",
                    "Master Thief",
                    "sold the Key",
                    "Find them and",
                ),
        )
        runReplay(
            "hobgoblin-caravan-route",
            seed = 21,
            expected =
                listOf(
                    "caravan of hobgoblins",
                    "Boss has it",
                    "carotid artery",
                    "Thou hast bested",
                    "Each survivor gets",
                    "experience pts",
                ),
        )
        runReplay(
            "master-crown-discovery-route",
            expected =
                listOf(
                    "better suited for",
                    "this than I am",
                    "ugly pendant",
                    "grant you the second",
                    "skullweed plant",
                    "DWARF",
                    "STONE",
                    "Nolrim's crypt",
                    "dagger you seek",
                    "purple dwarf stone",
                    "Master Crown is hidden",
                ),
        )
    }

    @Test
    fun townCommerceChurchAndSocialReplayPlansRunThroughInstalledRuntime() {
        runReplay(
            "town-commerce-paths",
            seed = 3,
            debugLog = true,
            expected =
                listOf(
                    "Merchant",
                    "You can carry no",
                    "(Y)es / (N)o",
                    "No thanks.",
                    "Sold!",
                    "- I don't deal in that",
                    "The smith welcomes",
                    "The smith offers you some",
                    "The smith is disappointed.",
                    "Armor or Weapon?",
                    "(N)one",
                    "2h-Sword",
                    "Not weapon or armor.",
                    "Ok! Give ya 2900!",
                    "Ok! Give ya 450!",
                    "rng.draw context=shop.sell.haggle",
                ),
            forbidden = listOf("No such shop choice."),
        )
        runReplay(
            "town-church-paths",
            expected =
                listOf(
                    "You enter the church",
                    "Who? (1-4)",
                    "1. Aragorn",
                    "Mira needs no healing.",
                    "The priestess raises",
                    "The priestess",
                    "Not a spellcaster!",
                    "seems very alive",
                ),
        )
        runReplay(
            "town-social-paths",
            seed = 20,
            debugLog = true,
            expected =
                listOf(
                    "Welcome! Have a seat",
                    "AAH!",
                    "laughing",
                    "After listening",
                    "(R)umors",
                    "Chatting with",
                    "Niklas",
                    "(C)hitchat",
                    "(Q)uestion",
                    "(B)ye",
                    "Hassan",
                    "(L)odging",
                    "(M)agic",
                    "(P)eople",
                    "(N)othing",
                    "Well. My inn is the",
                    "TRAVELER'S STORY",
                    "Anything to drink?",
                    "(Y)es",
                    "(N)o",
                    "Deanna",
                    "The other guests giggle.",
                    "exhausting hour",
                    "Are you in need",
                    "of lodging?",
                    "You are served a well",
                    "You go to bed",
                    "You wake up",
                    "rng.draw context=speak.chitchat.subject",
                ),
            forbidden = listOf("Y. Y", "N. N", "L. Lodging", "M. Magic", "P. People", "N. Nothing"),
        )
        runReplay(
            "town-random-offer-path",
            seed = 21,
            debugLog = true,
            expected =
                listOf(
                    "scruffy",
                    "I will sell you this",
                    "Deal!!!",
                    "rng.draw context=script.chance",
                    "rng.draw context=script.random_item_offer.item",
                    "script.random_item_offer.bought",
                ),
        )
    }

    @Test
    fun acceptancePowerRouteRunsThroughInstalledRuntimeAndSavedHeroesScreen() {
        val saveFile = testTmp.resolve("acceptance-power-route.rsave").also(File::delete)
        runReplay(
            "acceptance-power-route",
            saveFile = saveFile,
            expected =
                listOf(
                    "FIRE",
                    "SHIELD",
                    "Bracelets of Thun",
                    "Master Crown",
                    "Today, you have killed",
                    "precious Silver Key",
                    "CONGRATULATIONS!",
                    "You have banished Ydarg",
                    "Game saved.",
                ),
        )

        val heroes =
            runLauncher(
                listOf(
                    "--screen",
                    "heroes",
                    "--load",
                    saveFile.path,
                ),
                name = "acceptance-heroes",
            )
        assertTrue("HALL OF NATIONAL HEROES" in heroes)
        assertTrue("Playtest defeated Baron Ydarg" in heroes)
    }

    private fun runReplay(
        name: String,
        seed: Long? = null,
        debugLog: Boolean = false,
        saveFile: File? = null,
        expected: List<String>,
        forbidden: List<String> = emptyList(),
    ) {
        val plan = ReplayPlans.build(name, bundle, systems, stateOverride = null)
        assertTrue(plan.summaryLines().any { line -> "runtime replay component" in line })

        val args =
            buildList {
                add("--state")
                add(plan.stateSlug)
                add("--script")
                add(plan.script)
                seed?.let { value ->
                    add("--seed")
                    add(value.toString())
                }
                if (debugLog) add("--debug-log")
                saveFile?.let { file ->
                    add("--save")
                    add(file.path)
                }
            }
        val output = runLauncher(args, name)
        expected.forEach { token ->
            assertTrue(token in output, "launcher output for replay $name missed expected text: $token")
        }
        forbidden.forEach { token ->
            assertFalse(token in output, "launcher output for replay $name contained forbidden text: $token")
        }
    }

    private fun runLauncher(
        args: List<String>,
        name: String,
    ): String {
        val outputFile = testTmp.resolve("$name.out").also(File::delete)
        val command =
            listOf(
                executable.path,
                "--bundle",
                bundleFile.path,
                "--user",
                "replay-test",
            ) + args + listOf("--log", testTmp.resolve("$name.log").path)
        val process =
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(outputFile)
                .apply {
                    environment()["JAVA_HOME"] = javaHome.path
                    environment()["PATH"] = javaHome.resolve("bin").path + File.pathSeparator + environment()["PATH"].orEmpty()
                }.start()
        val finished = process.waitFor(120, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            error("launcher timed out for $name")
        }
        val output = outputFile.readText()
        assertEquals(process.exitValue(), 0, "launcher failed for $name\n${output.takeLast(4000)}")
        return output
    }
}

private fun requiredProperty(name: String): String =
    System.getProperty(name)
        ?: error("missing required test system property $name")
