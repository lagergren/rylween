package rylween.runtime

import org.junit.jupiter.api.io.TempDir
import rylween.compiled.readExternalCompiledGameBundle
import rylween.terminal.AnsiTerminal
import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeSessionFactoryTest {
    private val bundleFile = File(requiredRuntimeProperty("rylween.runtime.bundle"))

    @TempDir
    private lateinit var testTmp: File

    @Test
    fun staticScreensRenderThroughInjectedTerminal() {
        val terminal = RecordingAnsiTerminal()
        val logFile = testTmp.resolve("session-api-static.log")

        RuntimeLog.open(logFile).use { log ->
            val result =
                RuntimeSessionFactory
                    .load(bundleFile)
                    .renderStaticScreen(
                        terminal,
                        RuntimeStaticScreenConfig(
                            screenSlug = "title",
                            stateSlug = "fresh-replay",
                        ),
                        log,
                    )

            assertEquals(RuntimeSessionExitReason.StaticScreenRendered, result.reason)
        }

        assertContains(terminal.output, "ADVENTURERS OF")
        assertFalse(terminal.closed, "programmatic callers own terminal lifetime")
    }

    @Test
    fun wildernessSessionStartsThroughInjectedTerminal() {
        val terminal = RecordingAnsiTerminal()
        val saveFile = testTmp.resolve("session-api.rsave").freshSaveFile()
        val logFile = testTmp.resolve("session-api-wilderness.log")

        RuntimeLog.open(logFile).use { log ->
            val result =
                RuntimeSessionFactory
                    .load(bundleFile)
                    .start(
                        terminal,
                        RuntimeSessionConfig(
                            userId = "test-user",
                            stateSlug = "fresh-replay",
                            saveFile = saveFile,
                            scriptedInput = "",
                        ),
                        log,
                    )

            assertEquals(RuntimeSessionExitReason.ScriptExhausted, result.reason)
        }

        assertContains(terminal.output, "Wilderness")
        assertFalse(terminal.closed, "programmatic callers own terminal lifetime")
    }

    @Test
    fun sessionStartPersistsGeneratedWorldState() {
        val saveFile = testTmp.resolve("session-api-world.rsave").freshSaveFile()
        val firstLog = testTmp.resolve("session-api-world-first.log")
        val secondLog = testTmp.resolve("session-api-world-second.log")

        runRuntimeSession(saveFile, firstLog, sessionDate = LocalDate.of(2026, 5, 11))

        val firstValues = saveFile.readSaveValues()
        val localPrefix = firstValues.userPrefix("local")
        assertEquals("2026-05-11", firstValues["realm.lastPlayedDate"])
        assertEquals("shared-world", firstValues["saveModel"])
        assertEquals("1", firstValues["users.count"])
        assertEquals("Replay", firstValues["${localPrefix}party.name"])
        assertEquals(null, firstValues["party.name"], "active parties are stored below user records")
        assertTrue(firstValues.containsKey("${localPrefix}member.1.health"), "member stats should be fully persisted")
        assertTrue(firstValues.containsKey("${localPrefix}member.1.weapon"), "member equipment should be persisted")
        assertTrue(firstValues.containsKey("${localPrefix}member.1.charisma"), "member charisma should be fully persisted")
        assertEquals(null, firstValues["$localPrefix.charisma"], "member charisma must not be written as a stray user field")
        val npcCount = firstValues.getValue("npcs.count").toInt()
        val npcPositions = (1..npcCount).mapNotNull { index -> firstValues["npc.$index.position"] }.toSet()
        assertTrue(npcCount > 3, "generated NPC placements should be persisted into the world save")
        assertTrue("town" in npcPositions, "town NPC placements should be persisted")
        assertTrue("wilderness" in npcPositions, "wilderness NPC placements should be persisted")
        assertContains(firstLog.readText(), "runtime.save.line")

        val beforeReload = saveFile.readText()
        runRuntimeSession(saveFile, secondLog, loadFile = saveFile, sessionDate = LocalDate.of(2026, 5, 11))

        assertEquals(beforeReload, saveFile.readText(), "loading the same date should not rewrite unchanged world state")
        assertContains(secondLog.readText(), "runtime.restore.line")
    }

    @Test
    fun generatedNpcPlacementUsesAuthoredTownServicePolicy() {
        val saveFile = testTmp.resolve("session-api-npc-placement.rsave").freshSaveFile()
        val logFile = testTmp.resolve("session-api-npc-placement.log")

        runRuntimeSession(saveFile, logFile, sessionDate = LocalDate.of(2026, 5, 11))

        val values = saveFile.readSaveValues()
        val placements = values.npcPlacements()
        val townServices = bundleFile.readExternalCompiledGameBundle().content.townServiceSlugs()
        val townPlacements = placements.filter { placement -> placement.position == "town" }
        val wildernessPlacements = placements.filter { placement -> placement.position == "wilderness" }

        assertTrue(placements.size > 20, "fresh shared world should persist generated NPC placements")
        assertTrue(townPlacements.size > wildernessPlacements.size, "Rylween random NPC policy should favor town service buckets")
        assertTrue(
            wildernessPlacements.none { placement -> placement.x == 0 && placement.y == 0 },
            "inactive imported NPC slots must not become wilderness 0,0 placements",
        )
        assertTrue(
            placements.none { placement -> placement.id in setOf("alex", "galdon", "kosch", "ufarsin") },
            "inactive imported merchant slots should not be materialized",
        )
        townPlacements.forEach { placement ->
            val services = townServices.getValue(checkNotNull(placement.town))
            assertContains(services, checkNotNull(placement.building), "${placement.id} should use an authored town service")
        }
        assertTrue(
            townPlacements.all { placement -> placement.building in setOf("inn", "tavern") },
            "random NPC town placements should use only the authored inn/tavern buckets",
        )
    }

    @Test
    fun sessionDateResetsMovesAcrossSameDayOfMonth() {
        val saveFile = testTmp.resolve("session-api-date-reset.rsave").freshSaveFile()
        val firstLog = testTmp.resolve("session-api-date-first.log")
        val secondLog = testTmp.resolve("session-api-date-second.log")

        runRuntimeSession(saveFile, firstLog, sessionDate = LocalDate.of(2026, 4, 11))
        val localPrefix = saveFile.readSaveValues().userPrefix("local")
        saveFile.writeText(
            SaveCodec.encodeValues(
                saveFile.readSaveValues() + ("${localPrefix}party.timeLeft" to "12"),
            ),
        )

        runRuntimeSession(saveFile, secondLog, loadFile = saveFile, sessionDate = LocalDate.of(2026, 5, 11))

        val reloadedValues = saveFile.readSaveValues()
        val reloadedPrefix = reloadedValues.userPrefix("local")
        assertEquals("2026-05-11", reloadedValues["realm.lastPlayedDate"])
        assertEquals("2026-05-11", reloadedValues["${reloadedPrefix}realm.lastPlayedDate"])
        assertEquals("175", reloadedValues["${reloadedPrefix}party.timeLeft"])
    }

    @Test
    fun starvationDeathTextUsesAuthoredUpkeepPrompt() {
        val saveFile = testTmp.resolve("session-api-starvation.rsave").freshSaveFile()
        val setupLog = testTmp.resolve("session-api-starvation-setup.log")
        val starveLog = testTmp.resolve("session-api-starvation-run.log")

        runRuntimeSession(saveFile, setupLog, sessionDate = LocalDate.of(2026, 5, 11))

        val content = bundleFile.readExternalCompiledGameBundle().content
        val values = saveFile.readSaveValues()
        val localPrefix = values.userPrefix("local")
        val memberName =
            values["${localPrefix}member.1.name"]
                ?.takeIf(String::isNotBlank)
                ?: content.strings.value(values.getValue("${localPrefix}member.1.nameString").toInt())
        saveFile.writeText(
            SaveCodec.encodeValues(
                values +
                    mapOf(
                        "${localPrefix}party.food" to "0",
                        "${localPrefix}party.timeLeft" to "6",
                        "${localPrefix}member.1.hitPoints" to "1",
                        "${localPrefix}member.1.dead" to "false",
                    ),
            ),
        )

        val terminal = RecordingAnsiTerminal()
        RuntimeLog.open(starveLog).use { log ->
            RuntimeSessionFactory
                .load(bundleFile)
                .start(
                    terminal,
                    RuntimeSessionConfig(
                        userId = "local",
                        stateSlug = "fresh-replay",
                        loadFile = saveFile,
                        saveFile = saveFile,
                        // Scripted commands treat whitespace as separators, so use the numeric north key to advance one turn.
                        scriptedInput = "8",
                        sessionDate = LocalDate.of(2026, 5, 11),
                    ),
                    log,
                )
        }

        // The classic 26-column window may wrap the line anywhere after the name.
        assertContains(terminal.output, "$memberName starved")
        assertContains(terminal.output, "death!!!")
    }

    @Test
    fun outOfMovesEndsTheDayWithASave() {
        val (result, terminal) = sessionWithMovesLeft("session-api-out-of-moves-save", moves = 0, commands = "8S")

        assertEquals(RuntimeSessionExitReason.Quit, result.reason)
        assertContains(terminal.output, "You are out of moves for")
        assertContains(terminal.output, "Game saved.")
    }

    @Test
    fun savingInTheWildernessShowsProgressButNotTheSaveFile() {
        val (_, terminal) = sessionWithMovesLeft("session-api-wilderness-save", moves = 100, commands = "S")

        assertContains(terminal.output, "Saving position...")
        assertContains(terminal.output, "Game saved.")
        assertFalse(".rsave" in terminal.output)
    }

    @Test
    fun aFailedSaveAtSessionStartIsShownAndPlayGoesOn() {
        val today = LocalDate.of(2026, 5, 11)
        val loadFile = testTmp.resolve("session-api-start-save-failed.rsave").freshSaveFile()
        runRuntimeSession(loadFile, testTmp.resolve("session-api-start-save-failed-setup.log"), sessionDate = today)
        val notADirectory = testTmp.resolve("session-api-start-save-failed-blocker").apply { writeText("") }
        val logFile = testTmp.resolve("session-api-start-save-failed.log")
        val terminal = RecordingAnsiTerminal()

        val result =
            RuntimeLog.open(logFile).use { log ->
                RuntimeSessionFactory
                    .load(bundleFile)
                    .start(
                        terminal,
                        RuntimeSessionConfig(
                            userId = "local",
                            stateSlug = "fresh-replay",
                            loadFile = loadFile,
                            saveFile = notADirectory.resolve("world.rsave"),
                            // Dismiss the failure, then ask to quit and play on.
                            scriptedInput = "AXN",
                            sessionDate = today,
                        ),
                        log,
                    )
            }

        assertContains(terminal.output, "Save failed.")
        assertContains(terminal.output, "Do you really want")
        assertEquals(RuntimeSessionExitReason.ScriptExhausted, result.reason)
        assertContains(logFile.readText(), "runtime.save.failed reason=session_start")
    }

    @Test
    fun deletingAPartyFromTheTitleMenuDoesNotShowTheSaveFile() {
        val today = LocalDate.of(2026, 5, 11)
        val saveFile = testTmp.resolve("session-api-title-delete.rsave").freshSaveFile()
        runRuntimeSession(saveFile, testTmp.resolve("session-api-title-delete-setup.log"), sessionDate = today)
        val terminal = RecordingAnsiTerminal()

        RuntimeLog.open(testTmp.resolve("session-api-title-delete.log")).use { log ->
            RuntimeSessionFactory
                .load(bundleFile)
                .start(
                    terminal,
                    RuntimeSessionConfig(
                        userId = "local",
                        stateSlug = "fresh-replay",
                        loadFile = saveFile,
                        saveFile = saveFile,
                        scriptedInput = "DY",
                        showTitleMenu = true,
                        sessionDate = today,
                    ),
                    log,
                )
        }

        assertContains(terminal.output, "Party terminated.")
        assertFalse(".rsave" in terminal.output)
    }

    @Test
    fun outOfMovesCanEndTheDayWithoutSaving() {
        val (result, terminal) = sessionWithMovesLeft("session-api-out-of-moves-quit", moves = 0, commands = "Q")

        assertEquals(RuntimeSessionExitReason.Quit, result.reason)
        assertFalse("Game saved." in terminal.output)
    }

    @Test
    fun quitAsksFirstAndAnyAnswerButYesPlaysOn() {
        val (declined, declinedTerminal) = sessionWithMovesLeft("session-api-quit-declined", moves = 100, commands = "XN")
        val (confirmed, _) = sessionWithMovesLeft("session-api-quit-confirmed", moves = 100, commands = "XY")

        assertContains(declinedTerminal.output, "Do you really want")
        assertEquals(RuntimeSessionExitReason.ScriptExhausted, declined.reason)
        assertEquals(RuntimeSessionExitReason.Quit, confirmed.reason)
    }

    @Test
    fun savingOneUserPreservesOtherUserParties() {
        val saveFile = testTmp.resolve("session-api-users.rsave").freshSaveFile()
        val firstLog = testTmp.resolve("session-api-users-first.log")
        val secondLog = testTmp.resolve("session-api-users-second.log")

        runRuntimeSession(saveFile, firstLog, sessionDate = LocalDate.of(2026, 5, 11))
        val withBeta =
            saveFile
                .readSaveValues()
                .withDuplicatedUser("local", "beta") + ("user.2.party.name" to "Beta Party")
        saveFile.writeText(SaveCodec.encodeValues(withBeta))

        runRuntimeSession(saveFile, secondLog, loadFile = saveFile, sessionDate = LocalDate.of(2026, 5, 11))

        val reloadedValues = saveFile.readSaveValues()
        val betaPrefix = reloadedValues.userPrefix("beta")
        val localPrefix = reloadedValues.userPrefix("local")
        assertEquals("2", reloadedValues["users.count"])
        assertEquals("Beta Party", reloadedValues["${betaPrefix}party.name"])
        assertEquals("Replay", reloadedValues["${localPrefix}party.name"])
    }

    @Test
    fun scoreAndHeroesAreSharedAcrossSavedUsers() {
        val saveFile = testTmp.resolve("session-api-score-heroes.rsave").freshSaveFile()
        val firstLog = testTmp.resolve("session-api-score-heroes-first.log")
        val secondLog = testTmp.resolve("session-api-score-heroes-second.log")

        runRuntimeSession(saveFile, firstLog, sessionDate = LocalDate.of(2026, 5, 11))
        val withBeta =
            saveFile
                .readSaveValues()
                .withDuplicatedUser("local", "beta") +
                mapOf(
                    "user.1.party.name" to "Alpha Party",
                    "user.2.party.name" to "Beta Party",
                    "user.2.party.milestones" to "ydarg_victory",
                )
        saveFile.writeText(SaveCodec.encodeValues(withBeta))

        runRuntimeSession(saveFile, secondLog, loadFile = saveFile, sessionDate = LocalDate.of(2026, 5, 11))

        val scoreTerminal = RecordingAnsiTerminal()
        val heroesTerminal = RecordingAnsiTerminal()
        RuntimeLog.open(testTmp.resolve("session-api-score-screen.log")).use { log ->
            RuntimeSessionFactory
                .load(bundleFile)
                .renderStaticScreen(
                    scoreTerminal,
                    RuntimeStaticScreenConfig(
                        screenSlug = "score",
                        stateSlug = "fresh-replay",
                        loadFile = saveFile,
                        userId = "local",
                    ),
                    log,
                )
        }
        RuntimeLog.open(testTmp.resolve("session-api-heroes-screen.log")).use { log ->
            RuntimeSessionFactory
                .load(bundleFile)
                .renderStaticScreen(
                    heroesTerminal,
                    RuntimeStaticScreenConfig(
                        screenSlug = "heroes",
                        stateSlug = "fresh-replay",
                        loadFile = saveFile,
                        userId = "local",
                    ),
                    log,
                )
        }

        assertContains(scoreTerminal.output, "Alpha Party")
        assertContains(scoreTerminal.output, "Beta Party")
        assertContains(heroesTerminal.output, "Beta Party defeated Baron Ydarg")
    }

    private fun runRuntimeSession(
        saveFile: File,
        logFile: File,
        loadFile: File? = null,
        sessionDate: LocalDate,
    ) {
        RuntimeLog.open(logFile).use { log ->
            RuntimeSessionFactory
                .load(bundleFile)
                .start(
                    RecordingAnsiTerminal(),
                    RuntimeSessionConfig(
                        userId = "local",
                        stateSlug = "fresh-replay",
                        loadFile = loadFile,
                        saveFile = saveFile,
                        scriptedInput = "",
                        sessionDate = sessionDate,
                    ),
                    log,
                )
        }
    }

    /** Runs a session for a new party with [moves] moves left today, typing [commands]. */
    private fun sessionWithMovesLeft(
        name: String,
        moves: Int,
        commands: String,
    ): Pair<RuntimeSessionResult, RecordingAnsiTerminal> {
        val saveFile = testTmp.resolve("$name.rsave").freshSaveFile()
        val today = LocalDate.of(2026, 5, 11)
        runRuntimeSession(saveFile, testTmp.resolve("$name-setup.log"), sessionDate = today)
        val values = saveFile.readSaveValues()
        saveFile.writeText(SaveCodec.encodeValues(values + ("${values.userPrefix("local")}party.timeLeft" to moves.toString())))
        val terminal = RecordingAnsiTerminal()
        val result =
            RuntimeLog.open(testTmp.resolve("$name.log")).use { log ->
                RuntimeSessionFactory
                    .load(bundleFile)
                    .start(
                        terminal,
                        RuntimeSessionConfig(
                            userId = "local",
                            stateSlug = "fresh-replay",
                            loadFile = saveFile,
                            saveFile = saveFile,
                            scriptedInput = commands,
                            sessionDate = today,
                        ),
                        log,
                    )
            }
        return result to terminal
    }

    private fun File.freshSaveFile(): File {
        delete()
        return this
    }

    private fun File.readSaveValues(): Map<String, String> = SaveCodec.decodeValues(readLines())

    private fun Map<String, String>.userPrefix(userId: String): String {
        val count = getValue("users.count").toInt()
        return (1..count)
            .firstNotNullOf { index ->
                val prefix = "user.$index."
                prefix.takeIf { get("${prefix}id") == userId }
            }
    }

    private fun Map<String, String>.withDuplicatedUser(
        sourceUserId: String,
        targetUserId: String,
    ): Map<String, String> {
        val sourcePrefix = userPrefix(sourceUserId)
        val targetPrefix = "user.2."
        val duplicated =
            entries
                .asSequence()
                .filter { (key, _) -> key.startsWith(sourcePrefix) }
                .associate { (key, value) -> targetPrefix + key.removePrefix(sourcePrefix) to value }
        return this + duplicated + ("users.count" to "2") + ("${targetPrefix}id" to targetUserId)
    }

    private fun Map<String, String>.npcPlacements(): List<SaveNpcPlacement> {
        val count = getValue("npcs.count").toInt()
        return (1..count).map { index ->
            val prefix = "npc.$index."
            SaveNpcPlacement(
                id = getValue("${prefix}id"),
                position = getValue("${prefix}position"),
                town = get("${prefix}town"),
                building = get("${prefix}building"),
                x = get("${prefix}x")?.toInt(),
                y = get("${prefix}y")?.toInt(),
            )
        }
    }
}

private data class SaveNpcPlacement(
    val id: String,
    val position: String,
    val town: String?,
    val building: String?,
    val x: Int?,
    val y: Int?,
)

private fun rylween.compiled.CompiledContentBundle.townServiceSlugs(): Map<String, Set<String>> =
    towns.associate { town ->
        slug(town.id) to town.services.map(::slug).toSet()
    }

private class RecordingAnsiTerminal : AnsiTerminal {
    private val buffer = StringBuilder()
    var closed: Boolean = false
        private set

    val output: String
        get() = buffer.toString()

    override fun writeRaw(text: String) {
        buffer.append(text)
    }

    override fun flush() = Unit

    override fun readKeyToken(): String? = null

    override fun readTextLine(maxLength: Int?): String? = null

    override fun pause(ms: Int) = Unit

    override fun close() {
        closed = true
    }
}

private fun requiredRuntimeProperty(name: String): String =
    System.getProperty(name)
        ?: error("missing required test system property $name")
