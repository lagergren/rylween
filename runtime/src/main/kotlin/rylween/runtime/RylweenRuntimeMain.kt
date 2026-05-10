/**
 * Runtime entry point.
 *
 * This package is the parallel implementation track for running Rylween from
 * the J1/J2 compiled bundle without composing the existing Kotlin
 * `engine`, `runtime`, `wilderness`, `town`, `dungeon`, `castle`, `battle`, or
 * terminal adapter classes. It reads direct compiled tables from
 * `compiled/CompiledBundle.kt` plus stable terminal presentation rows from
 * `compiled/ExternalBundleArtifact.kt`, then owns its own small runtime loop.
 *
 * The current slice proves the boundary with title/static-screen rendering and
 * direct wilderness movement. Full town, dungeon, castle, battle, persistence,
 * and compiled-script host services should be added here depth first instead
 * of calling back into the legacy session layer.
 */
@file:Suppress("ReplacePrintlnWithLogging", "UseOfSystemOutOrSystemErr")

package rylween.runtime

import rylween.compiled.CompiledBuildingService
import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledEquipment
import rylween.compiled.CompiledGameBundle
import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledIntExpr
import rylween.compiled.CompiledInventorySlot
import rylween.compiled.CompiledJournalEntry
import rylween.compiled.CompiledLocationPlacement
import rylween.compiled.CompiledNpcPlacement
import rylween.compiled.CompiledNpcVisit
import rylween.compiled.CompiledPartyCounters
import rylween.compiled.CompiledPartyImage
import rylween.compiled.CompiledPartyMember
import rylween.compiled.CompiledRuntimeTextPart
import rylween.compiled.CompiledStateImage
import rylween.compiled.CompiledStringExpr
import rylween.compiled.CompiledTerrain
import rylween.compiled.CompiledTextBlock
import rylween.compiled.CompiledTextPart
import rylween.compiled.CompiledWorldPosition
import rylween.compiled.ExternalCharacterCreationClassOption
import rylween.compiled.ExternalCharacterCreationPrompts
import rylween.compiled.ExternalCharacterCreationStatRoll
import rylween.compiled.ExternalRuntimeSystems
import rylween.compiled.ExternalRuntimeTextBlock
import rylween.compiled.readExternalCompiledBundleContentHash
import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeSystems
import rylween.dsl.CharacterCreationStat
import rylween.dsl.Facing
import rylween.dsl.Gender
import rylween.dsl.GridPoint
import rylween.dsl.PartyResources
import rylween.dsl.PcClass
import rylween.dsl.Race
import rylween.dsl.Resource
import rylween.dsl.RuntimeMode
import rylween.dsl.StatPool
import rylween.terminal.AnsiTerminal
import rylween.terminal.AnsiTerminals
import java.io.File
import kotlin.math.max
import kotlin.system.exitProcess

private const val TITLE_SCREEN = "title"
private const val SCORE_SCREEN = "score"
private const val HEROES_SCREEN = "heroes"
private const val MANUAL_SCREEN = "manual"
private const val TEXT_WINDOW_PAGE_LINES = 9
private const val CREATION_REJECTION_DELAY_MS = 1_000
private const val TOWN_MAINSTREET_IMAGE = "blitgfx.towne_mainstreet"

/** CLI entry point for the runtime. */
object RylweenRuntimeMain {
    @JvmStatic
    fun main(args: Array<String>) {
        runCatching {
            RuntimeCli.parse(args.toList()).run()
        }.getOrElse { error ->
            System.err.println("runtime failed: ${error.message ?: error::class.simpleName}")
            exitProcess(1)
        }
    }
}

/** Parsed command-line configuration for [GameRuntime]. */
data class RuntimeConfig(
    val bundleFile: File,
    val stateSlug: String = "fresh-replay",
    val stateExplicit: Boolean = false,
    val loadFile: File? = null,
    val saveFile: File = File("build/saves/runtime.rsave"),
    val staticScreen: String? = null,
    val scriptedInput: String? = null,
    val seed: Long? = null,
    val logFile: File = RuntimeLog.defaultFile,
    val debugLogFlash: Boolean = false,
    val debugSecrets: Boolean = false,
    val showTitleMenu: Boolean = false,
    val skipTitleMenu: Boolean = false,
)

private object RuntimeCli {
    fun parse(args: List<String>): RuntimeConfig {
        if (args.any { it == "--help" || it == "-h" }) {
            printHelp()
            exitProcess(0)
        }

        var bundleFile: File? = null
        var stateSlug = "fresh-replay"
        var stateExplicit = false
        var loadFile: File? = null
        var saveFile = File("build/saves/runtime.rsave")
        var staticScreen: String? = null
        var scriptedInput: String? = null
        var seed: Long? = null
        var logFile = RuntimeLog.defaultFile
        var debugLogFlash = false
        var debugSecrets = false
        var showTitleMenu = false
        var skipTitleMenu = false
        var index = 0
        while (index < args.size) {
            when (val arg = args[index]) {
                "--bundle" -> {
                    bundleFile = File(args.valueAfter(index, arg))
                    index += 2
                }

                "--state" -> {
                    stateSlug = args.valueAfter(index, arg)
                    stateExplicit = true
                    index += 2
                }

                "--load" -> {
                    loadFile = File(args.valueAfter(index, arg))
                    index += 2
                }

                "--save" -> {
                    saveFile = File(args.valueAfter(index, arg))
                    index += 2
                }

                "--screen" -> {
                    staticScreen = args.valueAfter(index, arg).screenSlug()
                    index += 2
                }

                "--script" -> {
                    scriptedInput = args.valueAfter(index, arg)
                    index += 2
                }

                "--seed" -> {
                    seed = args.valueAfter(index, arg).toLong()
                    index += 2
                }

                "--log" -> {
                    logFile = File(args.valueAfter(index, arg))
                    index += 2
                }

                "--debug-log", "--debug-log-flash" -> {
                    debugLogFlash = true
                    index += 1
                }

                "--debug-secrets" -> {
                    debugSecrets = true
                    index += 1
                }

                "--title" -> {
                    showTitleMenu = true
                    index += 1
                }

                "--no-title" -> {
                    skipTitleMenu = true
                    index += 1
                }

                else -> error("unknown argument $arg")
            }
        }
        check(listOf(staticScreen != null, scriptedInput != null).count { it } <= 1) {
            "--screen and --script are mutually exclusive"
        }
        check(!(showTitleMenu && skipTitleMenu)) {
            "--title and --no-title are mutually exclusive"
        }
        return RuntimeConfig(
            bundleFile = checkNotNull(bundleFile) { "--bundle is required" },
            stateSlug = stateSlug,
            stateExplicit = stateExplicit,
            loadFile = loadFile,
            saveFile = saveFile,
            staticScreen = staticScreen,
            scriptedInput = scriptedInput,
            seed = seed,
            logFile = logFile,
            debugLogFlash = debugLogFlash,
            debugSecrets = debugSecrets,
            showTitleMenu = showTitleMenu,
            skipTitleMenu = skipTitleMenu,
        )
    }

    private fun printHelp() {
        println(
            """
            |Usage: rylween --bundle build/compiled/rylween-bundle.json [options]
            |
            |Options:
            |  --state <slug>    Boot compiled state image directly, default fresh-replay.
            |  --load <file>     Load a runtime save.
            |  --save <file>     Save target for the S command, default build/saves/runtime.rsave.
            |  --screen <slug>   Render one compiled static screen and exit.
            |  --script <chars>  Feed command characters, useful for smoke tests.
            |  --seed <long>     Override the active party RNG seed before play.
            |  --log <file>      Runtime forensic log, default build/logs/runtime/latest.log.
            |  --debug-log       Flash each forensic log line below the game frame.
            |  --debug-secrets   Enable hidden manual-test wilderness triggers.
            |  --title           Show title flow even when direct boot would skip it.
            |  --no-title        Skip title flow and boot directly.
            """.trimMargin(),
        )
    }
}

private fun List<String>.valueAfter(
    index: Int,
    option: String,
): String = getOrNull(index + 1) ?: error("$option requires a value")

internal fun String.screenSlug(): String =
    when (val normalized = lowercase().replace('-', '_')) {
        "vscore", "verbose", "verbose_score" -> "verbose_score"
        "hall", "hall_of_heroes" -> "heroes"
        else -> normalized
    }

private fun RuntimeState.logPosition(): String = "${party.position.x},${party.position.y}"

private fun RuntimeConfig.run() {
    val runtime = GameRuntime.load(bundleFile)
    RuntimeLog.open(logFile).use { log ->
        log.event(
            "run.start bundle=${bundleFile.path} state=$stateSlug load=${loadFile?.path.orEmpty()} " +
                "save=${saveFile.path} screen=${staticScreen.orEmpty()} script=${scriptedInput != null} " +
                "seed=${seed ?: ""} title=$showTitleMenu noTitle=$skipTitleMenu debugLogFlash=$debugLogFlash " +
                "debugSecrets=$debugSecrets",
        )
        if (staticScreen != null) {
            runtime.renderStaticScreen(staticScreen, stateSlug, loadFile, log)
            return
        }
        runtime.runWilderness(
            stateSlug = stateSlug,
            loadFile = loadFile,
            saveFile = saveFile,
            scriptedInput = scriptedInput,
            seed = seed,
            showTitleMenu =
                loadFile == null &&
                    !skipTitleMenu &&
                    (showTitleMenu || scriptedInput == null && !stateExplicit),
            debugLogFlash = debugLogFlash,
            debugSecrets = debugSecrets,
            log = log,
        )
    }
}

/**
 * Direct runtime facade.
 *
 * This class owns bundle lookup, static resource rendering, and the first
 * independent wilderness loop. It deliberately depends on `rylween.compiled`
 * tables and stable runtime-system data only.
 */
class GameRuntime private constructor(
    private val bundle: CompiledGameBundle,
    private val systems: ExternalRuntimeSystems,
    private val bundleContentHash: String,
) {
    private val content: CompiledContentBundle = bundle.content

    /** Renders a compiled static screen route from the bundle and returns. */
    fun renderStaticScreen(
        screenSlug: String,
        stateSlug: String,
        loadFile: File?,
        log: RuntimeLog,
    ) {
        log.event("screen.static slug=$screenSlug state=$stateSlug load=${loadFile?.path.orEmpty()}")
        val runtime =
            loadFile?.let { file ->
                SaveFile.read(
                    file = file,
                    expectedBundleHash = bundleContentHash,
                    content = content,
                    stateLookup = ::stateBySlug,
                )
            } ?: RuntimeState.from(stateBySlug(stateSlug), content)
        AnsiTerminals.stdout().use { terminal ->
            RuntimeScreen(terminal, bundle, systems, log).showStaticScreen(screenSlug, runtime)
        }
    }

    /** Runs the runtime wilderness loop from one compiled state image. */
    fun runWilderness(
        stateSlug: String,
        loadFile: File?,
        saveFile: File,
        scriptedInput: String?,
        seed: Long? = null,
        showTitleMenu: Boolean = false,
        debugLogFlash: Boolean = false,
        debugSecrets: Boolean = false,
        log: RuntimeLog,
    ) {
        var runtime =
            loadFile?.let { file ->
                SaveFile.read(
                    file = file,
                    expectedBundleHash = bundleContentHash,
                    content = content,
                    stateLookup = ::stateBySlug,
                )
            } ?: RuntimeState.from(stateBySlug(stateSlug), content)
        runtime = seed?.let(runtime::withSeed) ?: runtime
        val world = WorldMap.from(content, runtime.currentMap)
        AnsiTerminals.open(scripted = scriptedInput != null).use { terminal ->
            val screen = RuntimeScreen(terminal, bundle, systems, log)
            if (debugLogFlash) log.mirrorTo(screen::flashLogLine)
            try {
                log.event("wilderness.boot state=$stateSlug position=${runtime.logPosition()} mode=${runtime.currentMode}")
                val input = RuntimeInput(terminal, scriptedInput, log)
                if (showTitleMenu) {
                    runtime = runTitleMenu(runtime, stateSlug, seed, saveFile, input, screen, log) ?: return
                    log.event("wilderness.title_selected position=${runtime.logPosition()} mode=${runtime.currentMode}")
                }
                val commands = WildernessCommands.from(systems)
                val debugCommands = WildernessCommands.from(systems, "wilderness.debug")
                val actions =
                    WildernessActions(
                        content = content,
                        systems = systems,
                        realm = systems.realm,
                        combat = systems.combat,
                        wilderness = systems.wilderness,
                        commands = systems.commands,
                        input = input,
                        screen = screen,
                        log = log,
                    )
                val utility = UtilityActions(content, systems, input, screen)
                screen.bootFrame()
                screen.renderWilderness(runtime, world)
                while (true) {
                    when (runtime.currentMode) {
                        RuntimeMode.Dungeon -> {
                            runtime =
                                DungeonRuntime(content, systems, input, screen, saveFile, bundleContentHash, log)
                                    .run(runtime)
                            if (runtime.currentMode == RuntimeMode.GameOver || input.exhausted) return
                            screen.renderWilderness(runtime, world)
                            continue
                        }

                        RuntimeMode.Castle -> {
                            runtime =
                                CastleRuntime(content, systems, input, screen, saveFile, bundleContentHash)
                                    .run(runtime)
                            if (runtime.currentMode == RuntimeMode.GameOver || input.exhausted) return
                            screen.renderWilderness(runtime, world)
                            continue
                        }

                        RuntimeMode.GameOver -> return
                        else -> Unit
                    }
                    val command = input.readCommand()
                    log.event("wilderness.command key=${command ?: "null"} position=${runtime.logPosition()}")
                    var moveEvent: MoveEvent? = null
                    val advanceTurn =
                        when {
                            command == null -> return
                            commands.matches("quit", command) -> return
                            commands.matches("moveNorth", command) -> {
                                val move = move(runtime.party, world, 0, -1, screen)
                                runtime =
                                    runtime.copy(lastTerrain = move.event?.fromTerrain?.id ?: runtime.lastTerrain).withParty(move.party)
                                moveEvent = move.event
                                true
                            }

                            commands.matches("moveSouth", command) -> {
                                val move = move(runtime.party, world, 0, 1, screen)
                                runtime =
                                    runtime.copy(lastTerrain = move.event?.fromTerrain?.id ?: runtime.lastTerrain).withParty(move.party)
                                moveEvent = move.event
                                true
                            }

                            commands.matches("moveWest", command) -> {
                                val move = move(runtime.party, world, -1, 0, screen)
                                runtime =
                                    runtime.copy(lastTerrain = move.event?.fromTerrain?.id ?: runtime.lastTerrain).withParty(move.party)
                                moveEvent = move.event
                                true
                            }

                            commands.matches("moveEast", command) -> {
                                val move = move(runtime.party, world, 1, 0, screen)
                                runtime =
                                    runtime.copy(lastTerrain = move.event?.fromTerrain?.id ?: runtime.lastTerrain).withParty(move.party)
                                moveEvent = move.event
                                true
                            }

                            commands.matches("pass", command) -> {
                                runtime = runtime.withParty(runtime.party.passTurn())
                                true
                            }

                            commands.matches("redraw", command) -> {
                                screen.renderWilderness(runtime, world)
                                false
                            }

                            commands.matches("help", command) -> {
                                printHelp(screen, input, runtime, world)
                                false
                            }

                            commands.matches("save", command) -> {
                                saveRuntime(runtime, saveFile, screen)
                                false
                            }

                            commands.matches("viewParty", command) -> {
                                showWildernessPages(screen, input, runtime, world, PartyPages.partyPages(content, runtime.party))
                                false
                            }

                            commands.matches("journal", command) -> {
                                showWildernessPage(screen, input, runtime, world, PartyPages.journalLines(content, runtime.party))
                                false
                            }

                            commands.matches("enter", command) -> {
                                val entered = enterLocation(runtime, input, screen, saveFile, log)
                                runtime = entered
                                entered.currentMode == RuntimeMode.Wilderness
                            }

                            commands.matches("readyWeapon", command) -> {
                                runtime = utility.readyWeapon(runtime)
                                false
                            }

                            commands.matches("readyArmor", command) -> {
                                runtime = utility.readyArmor(runtime)
                                false
                            }

                            commands.matches("removeWeapon", command) -> {
                                runtime = utility.removeWeapon(runtime)
                                redrawWildernessIfCurrent(screen, runtime, world)
                                false
                            }

                            commands.matches("removeArmor", command) -> {
                                runtime = utility.removeArmor(runtime)
                                redrawWildernessIfCurrent(screen, runtime, world)
                                false
                            }

                            commands.matches("drop", command) -> {
                                runtime = utility.drop(runtime)
                                redrawWildernessIfCurrent(screen, runtime, world)
                                false
                            }

                            commands.matches("camp", command) -> {
                                runtime = applyAction(actions.camp(runtime), screen, world)
                                false
                            }

                            commands.matches("hunt", command) -> {
                                runtime = applyAction(actions.hunt(runtime), screen, world)
                                false
                            }

                            commands.matches("castSpell", command) -> {
                                runtime = utility.castPriestSpell(runtime)
                                redrawWildernessIfCurrent(screen, runtime, world)
                                false
                            }

                            commands.matches("quaff", command) -> {
                                runtime = utility.quaff(runtime)
                                redrawWildernessIfCurrent(screen, runtime, world)
                                false
                            }

                            debugSecrets && debugCommands.matches("forceCave", command) -> {
                                runtime = applyAction(actions.forceCaveDiscovery(runtime), screen, world)
                                false
                            }

                            debugSecrets && debugCommands.matches("forceEncounter", command) -> {
                                runtime = applyAction(actions.forceEncounter(runtime), screen, world)
                                false
                            }

                            command == '\n' -> false
                            else -> {
                                screen.writeText(listOf("Unknown command '$command'.", "Press ? for commands."))
                                false
                            }
                        }
                    if (advanceTurn) {
                        runtime = applyAction(actions.applyTurnUpkeep(runtime), screen, world)
                        if (runtime.currentMode == RuntimeMode.GameOver) return
                        moveEvent?.let { event ->
                            runtime = applyAction(actions.applyMoveEvents(runtime, event), screen, world)
                            if (runtime.currentMode == RuntimeMode.GameOver) return
                        }
                    }
                    if (input.exhausted) return
                }
            } finally {
                if (debugLogFlash) log.mirrorTo(null)
            }
        }
    }

    private fun runTitleMenu(
        runtime: RuntimeState,
        stateSlug: String,
        seed: Long?,
        saveFile: File,
        input: RuntimeInput,
        screen: RuntimeScreen,
        log: RuntimeLog,
    ): RuntimeState? {
        screen.showStaticScreen(TITLE_SCREEN, runtime)
        log.event("title.splash")
        if (input.readCommand() == null) return null
        while (true) {
            val savedRuntime = loadSavedRuntimeOrNull(saveFile, log)
            screen.bootFrame()
            screen.writeText(titleMenuLines(hasSavedGame = savedRuntime != null))
            when (val command = input.readCommand()) {
                null -> return null
                'N' if savedRuntime == null -> {
                    log.event("title.new state=$stateSlug")
                    val freshRuntime = RuntimeState.from(stateBySlug(stateSlug), content)
                    return runCharacterCreation(seed?.let(freshRuntime::withSeed) ?: freshRuntime, input, screen, log)
                }

                'C' if savedRuntime != null -> {
                    log.event("title.continue save=${saveFile.path}")
                    return seed?.let(savedRuntime::withSeed) ?: savedRuntime
                }

                'T' if savedRuntime != null -> {
                    screen.writeText(listOf("Terminate saved party?", "", "(Y)es", "(N)o"))
                    if (input.readCommand() == 'Y') {
                        val deleted = runCatching { !saveFile.exists() || saveFile.delete() }.getOrDefault(false)
                        log.event("title.terminate save=${saveFile.path} deleted=$deleted")
                        screen.writeText(listOf(if (deleted) "Saved party terminated." else "Could not delete saved party.", saveFile.path))
                        input.readCommand()
                    }
                }

                'S' -> {
                    log.event("title.score")
                    screen.showPagedStaticScreen(SCORE_SCREEN, savedRuntime ?: runtime, input::readCommand)
                }

                'H' -> {
                    log.event("title.heroes")
                    screen.showPagedStaticScreen(HEROES_SCREEN, savedRuntime ?: runtime, input::readCommand)
                }

                'D' -> {
                    log.event("title.manual")
                    screen.showPagedStaticScreen(MANUAL_SCREEN, savedRuntime ?: runtime, input::readCommand)
                }

                'X' -> {
                    log.event("title.exit")
                    return null
                }

                else -> {
                    log.event("title.invalid key=$command")
                    screen.writeText(listOf("Invalid input.", "Key: $command", ""))
                    input.readCommand()
                }
            }
        }
    }

    private fun loadSavedRuntimeOrNull(
        saveFile: File,
        log: RuntimeLog,
    ): RuntimeState? {
        if (!saveFile.exists()) return null
        return runCatching {
            SaveFile.read(
                file = saveFile,
                expectedBundleHash = bundleContentHash,
                content = content,
                stateLookup = ::stateBySlug,
            )
        }.onFailure { error ->
            log.event("title.save_load_failed save=${saveFile.path} error=${error.message.orEmpty()}")
        }.getOrNull()
    }

    private fun titleMenuLines(hasSavedGame: Boolean): List<String> =
        buildList {
            add("Rylween")
            add("")
            if (hasSavedGame) {
                add("C. Continue Saved Game")
                add("T. Terminate Party")
            } else {
                add("N. Create New Party")
            }
            add("S. View Current Scores")
            add("H. View Hall of Fame")
            add("D. View Documentation")
            add("X. Exit")
        }

    private fun runCharacterCreation(
        baseRuntime: RuntimeState,
        input: RuntimeInput,
        screen: RuntimeScreen,
        log: RuntimeLog,
    ): RuntimeState? {
        val rules = systems.characterCreation ?: error("compiled bundle has no character-creation rules")
        val prompts = systems.prompts.characterCreation
        screen.bootFrame()
        screen.writeText(prompts.header.renderCreation() + prompts.partyName.renderCreation())
        val partyName = input.readText(24)?.normalizedCreationName()?.takeIf(String::isNotBlank) ?: return null
        log.event("creation.party_name name=$partyName")
        var runtime = baseRuntime
        val members = mutableListOf<CompiledPartyMember>()
        val memberNames = mutableListOf<String>()
        while (members.size < rules.start.partyMemberMax) {
            val memberNumber = members.size + 1
            val statsResult = readAcceptedCreationStats(runtime, memberNumber, input, screen, log) ?: return null
            runtime = statsResult.runtime
            screen.writeText(prompts.memberName.renderCreation(memberNumber = memberNumber))
            val memberName = input.readText(24)?.normalizedCreationName()?.takeIf(String::isNotBlank) ?: return null
            log.event("creation.member_name member=$memberNumber name=$memberName")
            val gender =
                readCreationChoice(
                    screen,
                    input,
                    log,
                    prompts.gender.renderCreation(memberName = memberName),
                    creationGenderChoices(prompts),
                    "gender",
                )
                    ?: return null
            val race =
                readCreationChoice(
                    screen,
                    input,
                    log,
                    prompts.race.renderCreation(memberName = memberName),
                    rules.races.mapIndexed { index, option -> CreationChoice(index.digitChoiceKey(), option.label, option.race) },
                    "race",
                )
                    ?: return null
            val pcClass =
                readCreationChoice(
                    screen,
                    input,
                    log,
                    prompts.classMenu.renderCreation(),
                    rules.classes.mapIndexed { index, option -> CreationChoice(index.digitChoiceKey(), option.label, option) },
                    "class",
                )
                    ?: return null
            val failure = pcClass.failureFor(statsResult.stats)
            if (failure != null) {
                log.event(
                    "creation.class_rejected member=$memberNumber class=${pcClass.pcClass} required=${pcClass.requiredStat} minimum=${pcClass.minimum}",
                )
                screen.writeText(failure.renderCreation())
                input.pause(CREATION_REJECTION_DELAY_MS)
            } else {
                log.event("creation.member_accepted member=$memberNumber gender=$gender race=$race class=${pcClass.pcClass}")
                members += runtime.creationMember(memberNumber, gender, race, pcClass.pcClass, statsResult.stats)
                memberNames += memberName
            }
        }
        runtime = runtime.applyCreatedParty(partyName, members, memberNames)
        screen.writeText(prompts.saving.renderCreation() + PausePrompts(systems.prompts).line())
        input.readCommand()
        return runtime
    }

    private fun readAcceptedCreationStats(
        runtime: RuntimeState,
        memberNumber: Int,
        input: RuntimeInput,
        screen: RuntimeScreen,
        log: RuntimeLog,
    ): CreationStatsResult? {
        var current = runtime
        val prompts = systems.prompts.characterCreation
        val roll = checkNotNull(systems.characterCreation).statRoll
        while (true) {
            val draw = current.drawCreationStats(roll, memberNumber, log)
            current = draw.runtime
            screen.writeText(
                prompts.statsPage.renderCreation(memberNumber = memberNumber, stats = draw.stats) +
                    prompts.reroll.renderCreation(),
            )
            val command = input.readCommand() ?: return null
            val reroll = command == 'Y'
            log.event("creation.reroll member=$memberNumber key=$command reroll=$reroll")
            if (!reroll) return CreationStatsResult(draw.stats, current)
        }
    }

    private fun RuntimeState.drawCreationStats(
        roll: ExternalCharacterCreationStatRoll,
        memberNumber: Int,
        log: RuntimeLog,
    ): CreationStatsResult {
        var nextParty = party

        fun draw(stat: String): Int {
            val result = nextParty.drawRandom(roll.min, roll.max)
            nextParty = result.party
            log.event("rng.creation_stat member=$memberNumber stat=$stat range=${roll.min}..${roll.max} value=${result.value}")
            return result.value
        }

        val stats =
            CreationStats(
                weaponSkill = draw("weaponSkill"),
                wisdom = draw("wisdom"),
                health = draw("health"),
                charisma = draw("charisma"),
            )
        return CreationStatsResult(stats, withParty(nextParty))
    }

    private fun <T> readCreationChoice(
        screen: RuntimeScreen,
        input: RuntimeInput,
        log: RuntimeLog,
        prompt: List<String>,
        choices: List<CreationChoice<T>>,
        label: String,
    ): T? {
        while (true) {
            screen.writeText(prompt + choices.menuLines())
            val command = input.readCommand() ?: return null
            val choice = choices.firstOrNull { choice -> choice.key == command }
            if (choice != null) {
                log.event("creation.choice kind=$label key=$command label=${choice.label}")
                return choice.value
            }
            screen.writeText(listOf("Invalid input.", "Key: $command"))
        }
    }

    private fun creationGenderChoices(prompts: ExternalCharacterCreationPrompts): List<CreationChoice<Gender>> =
        listOf(
            CreationChoice('1', prompts.genderMaleLabel, Gender.Male),
            CreationChoice('2', prompts.genderFemaleLabel, Gender.Female),
        )

    private fun Int.digitChoiceKey(): Char = (this + 1).digitToChar()

    private fun <T> List<CreationChoice<T>>.menuLines(): List<String> =
        chunked(2).map { row -> row.joinToString("  ") { choice -> "${choice.key}. ${choice.label}".padEnd(12) }.trimEnd() }

    private fun ExternalCharacterCreationClassOption.failureFor(stats: CreationStats): ExternalRuntimeTextBlock? =
        requiredStat?.let { stat -> minimum?.let { min -> failureText.takeIf { stats.value(stat) < min } } }

    private fun RuntimeState.creationMember(
        memberNumber: Int,
        gender: Gender,
        race: Race,
        pcClass: PcClass,
        stats: CreationStats,
    ): CompiledPartyMember {
        val blank = content.ref(CompiledHandleKind.Item, "blank")
        return CompiledPartyMember(
            nameString = party.members.getOrNull(memberNumber - 1)?.nameString ?: party.members.first().nameString,
            gender = gender,
            race = race,
            pcClass = pcClass,
            level = checkNotNull(systems.characterCreation).start.level,
            health = stats.health,
            hitPoints = StatPool(stats.health, stats.health),
            weaponSkill = stats.weaponSkill,
            wisdom = stats.wisdom,
            mana = initialCreationMana(pcClass, stats.wisdom),
            charisma = stats.charisma,
            equipment = CompiledEquipment(blank, blank),
            experience = 0,
            dead = false,
        )
    }

    private fun initialCreationMana(
        pcClass: PcClass,
        wisdom: Int,
    ): StatPool =
        when (pcClass) {
            PcClass.Mage,
            PcClass.Priest,
            -> StatPool(wisdom, wisdom)

            PcClass.Fighter,
            PcClass.Thief,
            PcClass.Bard,
            PcClass.Ranger,
            -> StatPool(0, wisdom)
        }

    private fun RuntimeState.applyCreatedParty(
        partyName: String,
        members: List<CompiledPartyMember>,
        memberNames: List<String>,
    ): RuntimeState {
        val rules = checkNotNull(systems.characterCreation).start
        val memberMax = rules.partyMemberMax
        val nextParty =
            party.copy(
                name = partyName,
                position = rules.position,
                facing = rules.facing?.let { facing -> enumValueOf<Facing>(facing) },
                resources = rules.resources,
                counters =
                    CompiledPartyCounters(
                        memberCount = members.size,
                        kills = 0,
                        waitingMail = false,
                        attacked = false,
                        attackedToday = emptyList(),
                    ),
                flags = emptyList(),
                milestones = emptyList(),
                inventory = party.inventory.map { slot -> slot.copy(item = null) },
                members = members,
                memberNames = memberNames,
                visitedNpcs = party.visitedNpcs.map { visit -> visit.copy(visited = false) },
                journal = emptyList(),
            )
        return copy(
            hour = 9,
            minute = 0,
            currentMap = content.ref(CompiledHandleKind.WorldMap, rules.map),
            currentMode = RuntimeMode.Wilderness,
            currentTown = null,
            partyMemberMax = memberMax,
            completedMissions = emptyList(),
            dungeon = null,
            castle = null,
            party = nextParty,
        )
    }

    private fun applyAction(
        result: WildernessActionResult,
        screen: RuntimeScreen,
        world: WorldMap,
    ): RuntimeState {
        screen.renderWilderness(result.runtime, world)
        if (result.messages.isNotEmpty()) {
            screen.writeText(result.messages)
        }
        return result.runtime
    }

    private fun move(
        party: PartyState,
        world: WorldMap,
        dx: Int,
        dy: Int,
        screen: RuntimeScreen,
    ): MoveResult {
        val source = party.position
        val target = GridPoint(source.x + dx, source.y + dy)
        val sourceTerrain = world.terrainAt(source)
        val targetTerrain = world.terrainAt(target)
        if (targetTerrain == null || !targetTerrain.passable) {
            screen.writeText(listOf("Blocked."))
            return MoveResult(party)
        }
        return MoveResult(
            party =
                party.copy(
                    position = target,
                    resources =
                        party.resources.copy(
                            timeLeft = max(0, party.resources.timeLeft - targetTerrain.movementCost),
                        ),
                ),
            event = MoveEvent(source, target, sourceTerrain, targetTerrain),
        )
    }

    private fun printHelp(
        screen: RuntimeScreen,
        input: RuntimeInput,
        runtime: RuntimeState,
        world: WorldMap,
    ) {
        val pages =
            systems.wilderness.helpPages
                .map { page -> page.render { null } }
                .ifEmpty { generatedWildernessHelpPages() }
        showWildernessPages(screen, input, runtime, world, pages)
    }

    private fun showWildernessPage(
        screen: RuntimeScreen,
        input: RuntimeInput,
        runtime: RuntimeState,
        world: WorldMap,
        lines: List<String>,
    ) {
        showWildernessPages(screen, input, runtime, world, lines.chunked(TEXT_WINDOW_PAGE_LINES))
    }

    private fun showWildernessPages(
        screen: RuntimeScreen,
        input: RuntimeInput,
        runtime: RuntimeState,
        world: WorldMap,
        pages: List<List<String>>,
    ) {
        val pausePrompts = PausePrompts(systems.prompts)
        for (page in pages) {
            screen.writeText(page.take(TEXT_WINDOW_PAGE_LINES) + "" + pausePrompts.line())
            if (input.readCommand() == null) return
        }
        redrawWildernessIfCurrent(screen, runtime, world)
    }

    private fun redrawWildernessIfCurrent(
        screen: RuntimeScreen,
        runtime: RuntimeState,
        world: WorldMap,
    ) {
        if (runtime.currentMode == RuntimeMode.Wilderness) screen.renderWilderness(runtime, world)
    }

    private fun generatedWildernessHelpPages(): List<List<String>> =
        systems.commands["wilderness"]
            .orEmpty()
            .map { command -> "${command.binding.label} = ${command.action.wildernessHelpText()}" }
            .chunked(TEXT_WINDOW_PAGE_LINES)
            .ifEmpty { listOf(listOf("Press ? for commands.")) }

    private fun String.wildernessHelpText(): String =
        when (this) {
            "moveNorth" -> "Move North"
            "moveSouth" -> "Move South"
            "moveWest" -> "Move West"
            "moveEast" -> "Move East"
            "pass" -> "Pass"
            "save" -> "Save Game"
            "readyWeapon" -> "Wield Weapon"
            "readyArmor" -> "Ready Armor"
            "redraw" -> "Redraw Screen"
            "journal" -> "View Journal"
            "help" -> "View this help"
            "hunt" -> "Hunt for Food"
            "viewParty" -> "View Party"
            "removeArmor" -> "Take off armor"
            "removeWeapon" -> "Put down weapon"
            "enter" -> "Enter"
            "drop" -> "Leave (Discard) Item"
            "camp" -> "Camp"
            "castSpell" -> "Cast Spell"
            "quaff" -> "Quaff Potion"
            "quit" -> "Quit Game (No Save)"
            else -> readableSlug()
        }

    private fun saveRuntime(
        runtime: RuntimeState,
        saveFile: File,
        screen: RuntimeScreen,
    ) {
        SaveFile.write(saveFile, runtime, bundleContentHash, content)
        screen.writeText(listOf("Game saved.", saveFile.path))
    }

    private fun enterLocation(
        runtime: RuntimeState,
        input: RuntimeInput,
        screen: RuntimeScreen,
        saveFile: File,
        log: RuntimeLog,
    ): RuntimeState {
        log.event("wilderness.enter position=${runtime.logPosition()}")
        val town = content.towns.firstOrNull { it.position == runtime.party.position }
        if (town != null) return enterTown(runtime, input, screen, town, log)
        val castle = systems.castleRows.firstOrNull { castle -> castle.entry == runtime.party.position }
        if (castle != null) {
            log.event("wilderness.enter_castle castle=${castle.id}")
            return CastleRuntime(content, systems, input, screen, saveFile, bundleContentHash)
                .startCastle(runtime, castle)
        }
        log.event("wilderness.enter_none")
        screen.writeText(listOf("There is nothing to enter here."))
        return runtime
    }

    private fun enterTown(
        runtime: RuntimeState,
        input: RuntimeInput,
        screen: RuntimeScreen,
        town: rylween.compiled.CompiledTown,
        log: RuntimeLog,
    ): RuntimeState {
        log.event("town.enter town=${slug(town.id)}")
        val services =
            town.services.mapNotNull { building ->
                content.buildingServices.firstOrNull { service -> service.building == building }
            }
        if (services.isEmpty()) {
            screen.writeText(listOf("${string(town.nameString)} has no compiled services."))
            return runtime
        }

        fun menuLines(): List<String> =
            buildList {
                add("Entering ${string(town.nameString)}")
                add("")
                services.forEachIndexed { index, service -> add("${index + 1}. ${slug(service.building).readableSlug()}") }
                add("L. Leave")
            }

        var current = runtime.copy(currentMode = RuntimeMode.Town, currentTown = town.id)
        while (true) {
            val menu = menuLines()
            screen.showImage(TOWN_MAINSTREET_IMAGE)
            screen.writeText(menu)
            var choice = input.readCommand() ?: return current
            while (choice == '!') {
                screen.redrawCurrentView(menu)
                choice = input.readCommand() ?: return current
            }
            if (choice == 'L') return current.copy(currentMode = RuntimeMode.Wilderness, currentTown = null)
            val index = choice.digitToIntOrNull()?.minus(1)
            val service = index?.let(services::getOrNull)
            if (service == null) {
                log.event("town.invalid_choice key=$choice")
                screen.writeText(listOf("No such service.", "", "Press any key."))
                input.readCommand() ?: return current
                continue
            }
            log.event("town.service town=${slug(town.id)} building=${slug(service.building)}")
            current = runService(current.copy(currentMode = RuntimeMode.Town, currentTown = town.id), service, input, screen, log)
            if (current.currentMode != RuntimeMode.Town || input.exhausted) return current
        }
    }

    private fun runService(
        runtime: RuntimeState,
        service: CompiledBuildingService,
        input: RuntimeInput,
        screen: RuntimeScreen,
        log: RuntimeLog,
    ): RuntimeState {
        val sceneSlug = slug(service.scene)
        val program =
            content.scripts.roots.firstOrNull { root -> root.root.kind.name == "Scene" && root.root.slug == sceneSlug }
        if (program == null) {
            screen.writeText(listOf("No compiled scene program for $sceneSlug."))
            return runtime
        }
        return ScriptExecutor(content, systems, input, screen, runtime, log = log).execute(program)
    }

    private fun stateBySlug(slug: String): CompiledStateImage {
        val stateRef = content.ref(CompiledHandleKind.State, slug)
        return bundle.states.firstOrNull { it.state == stateRef } ?: error("unknown compiled state $slug")
    }

    private fun string(id: Int): String = content.strings.value(id)

    private fun slug(ref: CompiledHandleRef): String = content.slug(ref)

    companion object {
        /** Loads and validates the direct compiled bundle/runtime-system sections. */
        fun load(bundleFile: File): GameRuntime =
            GameRuntime(
                bundle = bundleFile.readExternalCompiledGameBundle(),
                systems = bundleFile.readExternalRuntimeSystems(),
                bundleContentHash = bundleFile.readExternalCompiledBundleContentHash(),
            )
    }
}

internal data class RuntimeState(
    val stateSlug: String,
    val state: CompiledHandleRef,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val currentMap: CompiledHandleRef,
    val currentMode: RuntimeMode,
    val currentTown: CompiledHandleRef?,
    val lastTerrain: CompiledHandleRef?,
    val partyMemberMax: Int,
    val locations: List<CompiledLocationPlacement>,
    val npcs: List<CompiledNpcPlacement>,
    val completedMissions: List<CompiledHandleRef>,
    val dungeon: DungeonState?,
    val castle: CastleState?,
    val party: PartyState,
) {
    fun withParty(party: PartyState): RuntimeState = copy(party = party)

    fun withCompletedMission(mission: CompiledHandleRef): RuntimeState = copy(completedMissions = (completedMissions + mission).distinct())

    fun withSeed(seed: Long): RuntimeState = withParty(party.copy(rngState = seed))

    companion object {
        fun from(
            state: CompiledStateImage,
            content: CompiledContentBundle,
        ): RuntimeState =
            RuntimeState(
                stateSlug = content.slug(state.state),
                state = state.state,
                day = state.realm.day,
                hour = state.realm.hour,
                minute = state.realm.minute,
                currentMap = state.realm.currentMap,
                currentMode = state.realm.currentMode,
                currentTown = state.realm.currentTown,
                lastTerrain = state.realm.lastTerrain,
                partyMemberMax = state.realm.partyMemberMax,
                locations = state.locations,
                npcs = state.npcs,
                completedMissions = emptyList(),
                dungeon =
                    (state.mode as? rylween.compiled.CompiledRuntimeModeImage.Dungeon)
                        ?.session
                        ?.let { session ->
                            DungeonState(
                                dungeon = session.dungeon,
                                maxDepth = session.maxDepth,
                                depth = session.depth,
                                levelEvents = session.levelEvents,
                            )
                        },
                castle = null,
                party = PartyState.from(content, state.activeParty(), state.realm.rngState),
            )
    }
}

internal data class PartyState(
    val id: CompiledHandleRef,
    val playerString: Int,
    val name: String,
    val position: GridPoint,
    val facing: Facing?,
    val resources: PartyResources,
    val counters: CompiledPartyCounters,
    val flags: List<CompiledHandleRef>,
    val milestones: List<CompiledHandleRef>,
    val inventory: List<CompiledInventorySlot>,
    val members: List<CompiledPartyMember>,
    val memberNames: List<String>,
    val visitedNpcs: List<CompiledNpcVisit>,
    val journal: List<CompiledJournalEntry>,
    val rngState: Long?,
) {
    fun passTurn(): PartyState = copy(resources = resources.copy(timeLeft = max(0, resources.timeLeft - 1)))

    fun withGold(value: Int): PartyState = copy(resources = resources.copy(gold = value.coerceAtLeast(0)))

    fun withResource(
        resource: Resource,
        amount: Int,
    ): PartyState =
        when (resource) {
            Resource.Food -> copy(resources = resources.copy(food = amount.coerceAtLeast(0)))
            Resource.Torches -> copy(resources = resources.copy(torches = amount.coerceAtLeast(0)))
        }

    fun inventoryItem(slot: Int): CompiledHandleRef? = inventory.firstOrNull { current -> current.slot == slot }?.item

    fun withInventoryItem(
        slot: Int,
        item: CompiledHandleRef?,
    ): PartyState =
        copy(
            inventory =
                inventory.map { current ->
                    if (current.slot == slot) current.copy(item = item) else current
                },
        )

    fun drawRandom(
        lo: Int,
        hi: Int,
    ): RandomDraw {
        require(lo <= hi) { "random range must be ascending: $lo..$hi" }
        val seed = rngState ?: return RandomDraw(lo, this)
        var state = seed.takeUnless { it == 0L } ?: 1L
        state = state xor (state ushr 12)
        state = state xor (state shl 25)
        state = state xor (state ushr 27)
        val mixed = state * REFERENCE_RANDOM_MULTIPLIER
        val span = hi.toLong() - lo.toLong() + 1L
        val offset = java.lang.Long.remainderUnsigned(mixed, span)
        return RandomDraw(
            value = (lo.toLong() + offset).toInt(),
            party = copy(rngState = state),
        )
    }

    companion object {
        fun from(
            content: CompiledContentBundle,
            party: CompiledPartyImage,
            rngState: Long?,
        ): PartyState {
            val position =
                when (val current = party.position) {
                    is CompiledWorldPosition.Wilderness -> current.point
                    is CompiledWorldPosition.Town -> GridPoint(0, 0)
                }
            return PartyState(
                id = party.id,
                playerString = party.playerString,
                name = content.strings.value(party.nameString),
                position = position,
                facing = party.facing,
                resources = party.resources,
                counters = party.counters,
                flags = party.flags,
                milestones = party.milestones,
                inventory = party.inventory.normalizedInventorySlots(),
                members = party.members,
                memberNames = party.members.map { member -> content.strings.value(member.nameString) },
                visitedNpcs = party.visitedNpcs,
                journal = party.journal,
                rngState = rngState,
            )
        }
    }
}

internal fun PartyState.memberName(
    content: CompiledContentBundle,
    index: Int,
): String =
    memberNames
        .getOrNull(index)
        ?.takeIf(String::isNotBlank)
        ?: members.getOrNull(index)?.let { member -> content.strings.value(member.nameString) }.orEmpty()

internal data class RandomDraw(
    val value: Int,
    val party: PartyState,
)

private data class CreationStats(
    val weaponSkill: Int,
    val health: Int,
    val wisdom: Int,
    val charisma: Int,
) {
    fun value(stat: CharacterCreationStat): Int =
        when (stat) {
            CharacterCreationStat.WeaponSkill -> weaponSkill
            CharacterCreationStat.Health -> health
            CharacterCreationStat.Wisdom -> wisdom
            CharacterCreationStat.Charisma -> charisma
        }
}

private data class CreationStatsResult(
    val stats: CreationStats,
    val runtime: RuntimeState,
)

private data class CreationChoice<T>(
    val key: Char,
    val label: String,
    val value: T,
)

internal data class MoveResult(
    val party: PartyState,
    val event: MoveEvent? = null,
)

private const val REFERENCE_RANDOM_MULTIPLIER: Long = 0x2545F4914F6CDD1DL

internal class RuntimeInput(
    private val terminal: AnsiTerminal,
    scriptedInput: String?,
    private val log: RuntimeLog? = null,
) {
    private val scripted = scriptedInput?.toList()?.let(::ArrayDeque)

    val exhausted: Boolean get() = scripted != null && scripted.isEmpty()

    fun pause(ms: Int) {
        if (scripted == null) terminal.pause(ms)
    }

    fun readCommand(): Char? {
        val scriptedCommand = readScriptedCommand()
        if (scriptedCommand != null) {
            log?.event("input.command key=$scriptedCommand scripted=true")
            return scriptedCommand
        }
        if (scripted != null) {
            log?.event("input.command eof scripted=true")
            return null
        }
        val token = terminal.readKeyToken() ?: return null
        return (token.firstOrNull()?.uppercaseChar() ?: '\n').also { command ->
            log?.event("input.command key=$command scripted=false")
        }
    }

    fun readInt(maxDigits: Int = 6): Int? {
        val scriptedValue = readScriptedUnsignedInt()
        if (scriptedValue != null) return scriptedValue
        if (scripted != null) return null
        return terminal.readTextLine(maxDigits)?.trim()?.toIntOrNull()
    }

    fun readText(maxLength: Int? = null): String? {
        val scriptedValue = readScriptedText()
        if (scriptedValue != null) return scriptedValue
        if (scripted != null) return null
        return terminal.readTextLine(maxLength ?: DEFAULT_TEXT_INPUT_LIMIT)
    }

    private fun readScriptedCommand(): Char? {
        val chars = scripted ?: return null
        while (chars.isNotEmpty()) {
            val next = chars.removeFirst()
            if (!next.isWhitespace()) return next.uppercaseChar()
        }
        return null
    }

    private fun readScriptedUnsignedInt(): Int? {
        val chars = scripted ?: return null
        while (chars.firstOrNull()?.isWhitespace() == true) {
            chars.removeFirst()
        }
        val digits =
            buildString {
                while (chars.firstOrNull()?.isDigit() == true) {
                    append(chars.removeFirst())
                }
            }
        return digits.takeIf(String::isNotEmpty)?.toIntOrNull()
    }

    private fun readScriptedText(): String? {
        val chars = scripted ?: return null
        while (chars.firstOrNull()?.isWhitespace() == true) {
            chars.removeFirst()
        }
        if (chars.firstOrNull() == '"') {
            chars.removeFirst()
            val quoted =
                buildString {
                    while (chars.isNotEmpty() && chars.first() != '"') {
                        append(chars.removeFirst())
                    }
                }
            if (chars.firstOrNull() == '"') chars.removeFirst()
            return quoted
        }
        val value =
            buildString {
                while (chars.isNotEmpty() && !chars.first().isWhitespace()) {
                    append(chars.removeFirst())
                }
            }
        return value.takeIf(String::isNotEmpty)
    }
}

private const val DEFAULT_TEXT_INPUT_LIMIT = 80

private fun ExternalRuntimeTextBlock.renderCreation(
    memberNumber: Int? = null,
    memberName: String? = null,
    stats: CreationStats? = null,
): List<String> =
    render { kind ->
        when (kind) {
            "creationMemberNumber" -> memberNumber?.toString()
            "creationMemberName" -> memberName
            "creationWeaponSkill" -> stats?.weaponSkill?.toString()
            "creationHealth" -> stats?.health?.toString()
            "creationWisdom" -> stats?.wisdom?.toString()
            "creationCharisma" -> stats?.charisma?.toString()
            else -> null
        }
    }

private fun String.normalizedCreationName(): String = trim().replaceFirstChar(Char::uppercaseChar)

internal fun CompiledTextBlock.renderTextLines(
    content: CompiledContentBundle,
    intValue: (CompiledIntExpr) -> String = { "[int]" },
    stringValue: (CompiledStringExpr) -> String = { "[string]" },
    locationValue: (CompiledHandleRef) -> String = { location -> "[location:${content.slug(location)}]" },
    runtimeValue: (CompiledRuntimeTextPart) -> String = { part -> "[${part.name}]" },
): List<String> =
    when (this) {
        is CompiledTextBlock.Ref -> listOf("[text:${content.slug(text)}]")
        is CompiledTextBlock.Inline -> lineStrings.map(content.strings::value)
        is CompiledTextBlock.Structured ->
            lines.map { line ->
                line.parts.joinToString(separator = "") { part ->
                    part.renderText(content, intValue, stringValue, locationValue, runtimeValue)
                }
            }
    }

private fun CompiledTextPart.renderText(
    content: CompiledContentBundle,
    intValue: (CompiledIntExpr) -> String,
    stringValue: (CompiledStringExpr) -> String,
    locationValue: (CompiledHandleRef) -> String,
    runtimeValue: (CompiledRuntimeTextPart) -> String,
): String =
    when (this) {
        is CompiledTextPart.Literal -> content.strings.value(string)
        is CompiledTextPart.IntValue -> intValue(value)
        is CompiledTextPart.StringValue -> stringValue(value)
        is CompiledTextPart.RoleName -> "[role:${content.slug(role)}]"
        is CompiledTextPart.RoleLocation -> "[role-location:${content.slug(role)}]"
        is CompiledTextPart.TownName -> content.townName(town)
        is CompiledTextPart.FuzzyTownLocation -> "${content.townName(town)} area"
        is CompiledTextPart.LocationCoordinate -> locationValue(location)
        is CompiledTextPart.Runtime -> runtimeValue(kind)
    }

internal class WorldMap(
    private val rows: List<String>,
    private val terrainByCode: Map<Char, CompiledTerrain>,
) {
    val width: Int = rows.maxOfOrNull(String::length) ?: 0
    val height: Int = rows.size

    fun terrainAt(point: GridPoint): CompiledTerrain? = tileAt(point)?.let(terrainByCode::get)

    private fun tileAt(point: GridPoint): Char? = rows.getOrNull(point.y - 1)?.getOrNull(point.x)

    companion object {
        fun from(
            content: CompiledContentBundle,
            mapRef: CompiledHandleRef,
        ): WorldMap {
            val map =
                content.worldMaps.firstOrNull { it.id == mapRef }
                    ?: error("missing compiled world map ${content.slug(mapRef)}")
            val layer = map.layers.firstOrNull() ?: error("world map ${content.slug(mapRef)} has no layers")
            return WorldMap(
                rows = layer.rowStrings.map(content.strings::value),
                terrainByCode = content.terrain.associateBy { it.mapCode },
            )
        }
    }
}

internal fun CompiledContentBundle.ref(
    kind: CompiledHandleKind,
    slug: String,
): CompiledHandleRef {
    val slugString = strings.idOf(slug) ?: error("unknown $kind slug $slug")
    return handles.ref(kind, slugString)
}

private fun CompiledContentBundle.townName(ref: CompiledHandleRef): String =
    towns.firstOrNull { town -> town.id == ref }?.let { town -> strings.value(town.nameString) } ?: slug(ref)

private fun CompiledStateImage.activeParty(): CompiledPartyImage =
    parties.firstOrNull { it.id == realm.activeParty }
        ?: error("state ${state.kind} has no active party ${realm.activeParty}")
