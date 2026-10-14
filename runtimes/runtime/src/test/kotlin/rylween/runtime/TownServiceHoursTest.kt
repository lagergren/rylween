package rylween.runtime

import org.junit.jupiter.api.io.TempDir
import rylween.compiled.CompiledBuildingService
import rylween.compiled.readExternalCompiledGameBundle
import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TownServiceHoursTest {
    private val bundleFile = File(requiredRuntimeTestProperty("rylween.runtime.bundle"))
    @TempDir
    private lateinit var testTmp: File
    private val content = bundleFile.readExternalCompiledGameBundle().content

    @Test
    fun hoursAreInclusiveAndWrapPastMidnight() {
        assertEquals((6..18).toList(), openHours(service("smith")))
        assertEquals((0..1) + (6..23), openHours(service("tavern")))
        assertEquals((0..23).toList(), openHours(service("church")))
    }

    @Test
    fun aClosedShopShowsItsClosedTextAndReturnsToTheTownMenu() {
        val screen = visitSmith("town-hours-closed", hour = 19)

        val closedAt = screen.screens.indexOfFirst { lines -> lines == listOf("Shop Closed.") }
        assertTrue(closedAt >= 0, "the smith shows its closed text, saw ${screen.screens}")
        assertFalse(screen.screens.any { lines -> lines.any { line -> "smith welcomes you" in line } })
        assertEquals(screen.screens.first(), screen.screens.getOrNull(closedAt + 1), "the town menu comes back")
    }

    @Test
    fun aShopOpensDuringItsHours() {
        val screen = visitSmith("town-hours-open", hour = 18)

        assertFalse(screen.screens.any { lines -> lines == listOf("Shop Closed.") })
        assertTrue(screen.screens.any { lines -> lines.any { line -> "smith welcomes you" in line } })
    }

    private fun service(building: String): CompiledBuildingService =
        content.buildingServices.single { service -> content.slug(service.building) == building }

    private fun openHours(service: CompiledBuildingService): List<Int> = (0..23).filter(service::isOpenAt)

    /** Starts a new party, moves it onto a town with a smith at [hour], enters the town and the smith, then redraws what follows. */
    private fun visitSmith(
        name: String,
        hour: Int,
    ): TextScreen {
        val town = content.towns.first { town -> town.services.any { building -> content.slug(building) == "smith" } }
        val saveFile = testTmp.resolve("$name.rsave").also(File::delete)
        runSession(name, ScriptedInput(""), NoOpRuntimeScreen(), saveFile, loadFile = null)

        val values = SaveCodec.decodeValues(saveFile.readLines())
        val prefix = values.localUserPrefix()
        saveFile.writeText(
            SaveCodec.encodeValues(
                values +
                    mapOf(
                        "realm.hour" to hour.toString(),
                        "${prefix}party.x" to town.position.x.toString(),
                        "${prefix}party.y" to town.position.y.toString(),
                    ),
            ),
        )

        val screen = TextScreen()
        runSession("$name-visit", ScriptedInput("ES!"), screen, saveFile, loadFile = saveFile)
        return screen
    }

    private fun runSession(
        name: String,
        input: RuntimeInputPort,
        screen: RuntimeScreenPort,
        saveFile: File,
        loadFile: File?,
    ) {
        RuntimeLog.open(testTmp.resolve("$name.log")).use { log ->
            RuntimeSessionFactory.load(bundleFile).start(
                input = input,
                screen = screen,
                config =
                    RuntimeSessionConfig(
                        userId = "local",
                        stateSlug = "fresh-replay",
                        loadFile = loadFile,
                        saveFile = saveFile,
                        sessionDate = LocalDate.of(2026, 5, 11),
                    ),
                log = log,
            )
        }
    }

    private fun Map<String, String>.localUserPrefix(): String =
        (1..getValue("users.count").toInt())
            .map { index -> "user.$index." }
            .first { prefix -> get("${prefix}id") == "local" }

    /** Records each text screen the runtime writes. */
    private class TextScreen : NoOpRuntimeScreen() {
        val screens = mutableListOf<List<String>>()

        override fun writeText(lines: List<String>) {
            screens += lines
        }
    }
}
