/**
 * Standalone compiled-bundle runtime entry point.
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

package rylween.compiledruntime

import rylween.compiled.CompiledBuildingService
import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledGameBundle
import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledIntExpr
import rylween.compiled.CompiledInventorySlot
import rylween.compiled.CompiledJournalEntry
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
import rylween.compiled.ExternalRuntimeSystems
import rylween.compiled.readExternalCompiledBundleContentHash
import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeSystems
import rylween.dsl.Facing
import rylween.dsl.GridPoint
import rylween.dsl.PartyResources
import rylween.dsl.Resource
import rylween.dsl.RuntimeMode
import java.io.File
import kotlin.math.max
import kotlin.system.exitProcess

/** CLI entry point for the standalone compiled-bundle runtime. */
object RylweenCompiledRuntimeMain {
    @JvmStatic
    fun main(args: Array<String>) {
        runCatching {
            CompiledRuntimeCli.parse(args.toList()).run()
        }.getOrElse { error ->
            System.err.println("compiled runtime failed: ${error.message ?: error::class.simpleName}")
            exitProcess(1)
        }
    }
}

/** Parsed command-line configuration for [StandaloneCompiledRuntime]. */
data class CompiledRuntimeConfig(
    val bundleFile: File,
    val stateSlug: String = "fresh-replay",
    val loadFile: File? = null,
    val saveFile: File = File("build/saves/compiled-runtime.rsave"),
    val staticScreen: String? = null,
    val scriptedInput: String? = null,
)

private object CompiledRuntimeCli {
    fun parse(args: List<String>): CompiledRuntimeConfig {
        if (args.any { it == "--help" || it == "-h" }) {
            printHelp()
            exitProcess(0)
        }

        var bundleFile: File? = null
        var stateSlug = "fresh-replay"
        var loadFile: File? = null
        var saveFile = File("build/saves/compiled-runtime.rsave")
        var staticScreen: String? = null
        var scriptedInput: String? = null
        var index = 0
        while (index < args.size) {
            when (val arg = args[index]) {
                "--bundle" -> {
                    bundleFile = File(args.valueAfter(index, arg))
                    index += 2
                }
                "--state" -> {
                    stateSlug = args.valueAfter(index, arg)
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
                else -> error("unknown argument $arg")
            }
        }
        return CompiledRuntimeConfig(
            bundleFile = checkNotNull(bundleFile) { "--bundle is required" },
            stateSlug = stateSlug,
            loadFile = loadFile,
            saveFile = saveFile,
            staticScreen = staticScreen,
            scriptedInput = scriptedInput,
        )
    }

    private fun printHelp() {
        println(
            """
            |Usage: rylween-compiled --bundle build/compiled/rylween-bundle.json [options]
            |
            |Options:
            |  --state <slug>    Boot compiled state image, default fresh-replay.
            |  --load <file>     Load a standalone compiled-runtime save.
            |  --save <file>     Save target for the S command, default build/saves/compiled-runtime.rsave.
            |  --screen <slug>   Render one compiled static screen and exit.
            |  --script <chars>  Feed command characters, useful for smoke tests.
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

private fun CompiledRuntimeConfig.run() {
    val runtime = StandaloneCompiledRuntime.load(bundleFile)
    if (staticScreen != null) {
        runtime.renderStaticScreen(staticScreen, stateSlug, loadFile)
        return
    }
    runtime.runWilderness(stateSlug, loadFile, saveFile, scriptedInput)
}

/**
 * Direct compiled-bundle runtime facade.
 *
 * This class owns bundle lookup, static resource rendering, and the first
 * independent wilderness loop. It deliberately depends on `rylween.compiled`
 * tables and stable runtime-system data only.
 */
class StandaloneCompiledRuntime private constructor(
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
    ) {
        val runtime =
            loadFile?.let { file ->
                StandaloneCompiledSave.read(
                    file = file,
                    expectedBundleHash = bundleContentHash,
                    content = content,
                    stateLookup = ::stateBySlug,
                )
            } ?: StandaloneRuntimeState.from(stateBySlug(stateSlug), content)
        CompiledAnsiTerminals.stdout().use { terminal ->
            CompiledAnsiScreen(terminal, bundle, systems).showStaticScreen(screenSlug, runtime)
        }
    }

    /** Runs the standalone wilderness loop from one compiled state image. */
    fun runWilderness(
        stateSlug: String,
        loadFile: File?,
        saveFile: File,
        scriptedInput: String?,
    ) {
        var runtime =
            loadFile?.let { file ->
                StandaloneCompiledSave.read(
                    file = file,
                    expectedBundleHash = bundleContentHash,
                    content = content,
                    stateLookup = ::stateBySlug,
                )
            } ?: StandaloneRuntimeState.from(stateBySlug(stateSlug), content)
        val world = StandaloneWorldMap.from(content, runtime.currentMap)
        CompiledAnsiTerminals.open(scripted = scriptedInput != null).use { terminal ->
            val screen = CompiledAnsiScreen(terminal, bundle, systems)
            val input = StandaloneInput(terminal, scriptedInput)
            val commands = StandaloneWildernessCommands.from(systems)
            val actions =
                StandaloneWildernessActions(
                    content = content,
                    realm = systems.realm,
                    combat = systems.combat,
                    wilderness = systems.wilderness,
                    commands = systems.commands,
                    input = input,
                    screen = screen,
                )
            val utility = StandaloneUtilityActions(content, systems, input, screen)
            screen.bootFrame()
            screen.renderWilderness(runtime, world)
            while (true) {
                when (runtime.currentMode) {
                    RuntimeMode.Dungeon -> {
                        runtime =
                            StandaloneDungeonRuntime(content, systems, input, screen, saveFile, bundleContentHash)
                                .run(runtime)
                        if (runtime.currentMode == RuntimeMode.GameOver || input.exhausted) return
                        screen.renderWilderness(runtime, world)
                        continue
                    }
                    RuntimeMode.Castle -> {
                        runtime =
                            StandaloneCastleRuntime(content, systems, input, screen, saveFile, bundleContentHash)
                                .run(runtime)
                        if (runtime.currentMode == RuntimeMode.GameOver || input.exhausted) return
                        screen.renderWilderness(runtime, world)
                        continue
                    }
                    RuntimeMode.GameOver -> return
                    else -> Unit
                }
                val command = input.readCommand()
                var moveEvent: StandaloneMoveEvent? = null
                val advanceTurn =
                    when {
                        command == null -> return
                        commands.matches("quit", command) -> return
                        commands.matches("moveNorth", command) -> {
                            val move = move(runtime.party, world, 0, -1, screen)
                            runtime = runtime.copy(lastTerrain = move.event?.fromTerrain?.id ?: runtime.lastTerrain).withParty(move.party)
                            moveEvent = move.event
                            true
                        }
                        commands.matches("moveSouth", command) -> {
                            val move = move(runtime.party, world, 0, 1, screen)
                            runtime = runtime.copy(lastTerrain = move.event?.fromTerrain?.id ?: runtime.lastTerrain).withParty(move.party)
                            moveEvent = move.event
                            true
                        }
                        commands.matches("moveWest", command) -> {
                            val move = move(runtime.party, world, -1, 0, screen)
                            runtime = runtime.copy(lastTerrain = move.event?.fromTerrain?.id ?: runtime.lastTerrain).withParty(move.party)
                            moveEvent = move.event
                            true
                        }
                        commands.matches("moveEast", command) -> {
                            val move = move(runtime.party, world, 1, 0, screen)
                            runtime = runtime.copy(lastTerrain = move.event?.fromTerrain?.id ?: runtime.lastTerrain).withParty(move.party)
                            moveEvent = move.event
                            true
                        }
                        commands.matches("pass", command) -> {
                            runtime = runtime.withParty(runtime.party.passTurn())
                            true
                        }
                        commands.matches("redraw", command) -> true
                        commands.matches("help", command) -> {
                            printHelp(screen)
                            false
                        }
                        commands.matches("save", command) -> {
                            saveRuntime(runtime, saveFile, screen)
                            false
                        }
                        commands.matches("viewParty", command) -> {
                            screen.writeText(StandalonePartyPages.partyLines(content, runtime.party))
                            false
                        }
                        commands.matches("journal", command) -> {
                            screen.writeText(StandalonePartyPages.journalLines(content, runtime.party))
                            false
                        }
                        commands.matches("enter", command) -> {
                            runtime = enterLocation(runtime, input, screen, saveFile)
                            true
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
                            false
                        }
                        commands.matches("removeArmor", command) -> {
                            runtime = utility.removeArmor(runtime)
                            false
                        }
                        commands.matches("drop", command) -> {
                            runtime = utility.drop(runtime)
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
                            false
                        }
                        commands.matches("quaff", command) -> {
                            runtime = utility.quaff(runtime)
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
        }
    }

    private fun applyAction(
        result: StandaloneWildernessActionResult,
        screen: CompiledAnsiScreen,
        world: StandaloneWorldMap,
    ): StandaloneRuntimeState {
        screen.renderWilderness(result.runtime, world)
        if (result.messages.isNotEmpty()) {
            screen.writeText(result.messages)
        }
        return result.runtime
    }

    private fun move(
        party: StandaloneParty,
        world: StandaloneWorldMap,
        dx: Int,
        dy: Int,
        screen: CompiledAnsiScreen,
    ): StandaloneMoveResult {
        val source = party.position
        val target = GridPoint(source.x + dx, source.y + dy)
        val sourceTerrain = world.terrainAt(source)
        val targetTerrain = world.terrainAt(target)
        if (targetTerrain == null || !targetTerrain.passable) {
            screen.writeText(listOf("Blocked."))
            return StandaloneMoveResult(party)
        }
        return StandaloneMoveResult(
            party =
                party.copy(
                    position = target,
                    resources =
                        party.resources.copy(
                            timeLeft = max(0, party.resources.timeLeft - targetTerrain.movementCost),
                        ),
                ),
            event = StandaloneMoveEvent(source, target, sourceTerrain, targetTerrain),
        )
    }

    private fun printHelp(screen: CompiledAnsiScreen) {
        val lines =
            systems.wilderness.helpPages
                .firstOrNull()
                ?.render { null }
                ?: generatedWildernessHelpLines()
        screen.writeText(lines)
    }

    private fun generatedWildernessHelpLines(): List<String> =
        systems.commands["wilderness"]
            .orEmpty()
            .map { command -> "${command.binding.label}: ${command.action.readableSlug()}" }
            .take(7)
            .ifEmpty { listOf("Press ? for commands.") }

    private fun saveRuntime(
        runtime: StandaloneRuntimeState,
        saveFile: File,
        screen: CompiledAnsiScreen,
    ) {
        StandaloneCompiledSave.write(saveFile, runtime, bundleContentHash, content)
        screen.writeText(listOf("Game saved.", saveFile.path))
    }

    private fun enterLocation(
        runtime: StandaloneRuntimeState,
        input: StandaloneInput,
        screen: CompiledAnsiScreen,
        saveFile: File,
    ): StandaloneRuntimeState {
        val town = content.towns.firstOrNull { it.position == runtime.party.position }
        if (town != null) return enterTown(runtime, input, screen, town)
        val castle = systems.castleRows.firstOrNull { castle -> castle.entry == runtime.party.position }
        if (castle != null) {
            return StandaloneCastleRuntime(content, systems, input, screen, saveFile, bundleContentHash)
                .startCastle(runtime, castle)
        }
        screen.writeText(listOf("There is nothing to enter here."))
        return runtime
    }

    private fun enterTown(
        runtime: StandaloneRuntimeState,
        input: StandaloneInput,
        screen: CompiledAnsiScreen,
        town: rylween.compiled.CompiledTown,
    ): StandaloneRuntimeState {
        val services =
            town.services.mapNotNull { building ->
                content.buildingServices.firstOrNull { service -> service.building == building }
            }
        if (services.isEmpty()) {
            screen.writeText(listOf("${string(town.nameString)} has no compiled services."))
            return runtime
        }
        val menu =
            buildList {
                add("Entering ${string(town.nameString)}")
                add("")
                services.forEachIndexed { index, service ->
                    add("${index + 1}. ${slug(service.building).readableSlug()}")
                }
                add("L. Leave")
            }
        screen.writeText(menu)
        val choice = input.readCommand() ?: return runtime
        if (choice == 'L') return runtime
        val index = choice.digitToIntOrNull()?.minus(1)
        val service = index?.let(services::getOrNull)
        if (service == null) {
            screen.writeText(listOf("No such service."))
            return runtime
        }
        val serviceRuntime = runtime.copy(currentMode = RuntimeMode.Town, currentTown = town.id)
        return runService(serviceRuntime, service, input, screen).copy(currentMode = RuntimeMode.Wilderness, currentTown = null)
    }

    private fun runService(
        runtime: StandaloneRuntimeState,
        service: CompiledBuildingService,
        input: StandaloneInput,
        screen: CompiledAnsiScreen,
    ): StandaloneRuntimeState {
        val sceneSlug = slug(service.scene)
        val program =
            content.scripts.roots.firstOrNull { root -> root.root.kind.name == "Scene" && root.root.slug == sceneSlug }
        if (program == null) {
            screen.writeText(listOf("No compiled scene program for $sceneSlug."))
            return runtime
        }
        return StandaloneScriptExecutor(content, systems, input, screen, runtime).execute(program)
    }

    private fun stateBySlug(slug: String): CompiledStateImage {
        val stateRef = content.ref(CompiledHandleKind.State, slug)
        return bundle.states.firstOrNull { it.state == stateRef } ?: error("unknown compiled state $slug")
    }

    private fun string(id: Int): String = content.strings.value(id)

    private fun slug(ref: CompiledHandleRef): String = content.slug(ref)

    companion object {
        /** Loads and validates the direct compiled bundle/runtime-system sections. */
        fun load(bundleFile: File): StandaloneCompiledRuntime =
            StandaloneCompiledRuntime(
                bundle = bundleFile.readExternalCompiledGameBundle(),
                systems = bundleFile.readExternalRuntimeSystems(),
                bundleContentHash = bundleFile.readExternalCompiledBundleContentHash(),
            )
    }
}

internal data class StandaloneRuntimeState(
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
    val npcs: List<CompiledNpcPlacement>,
    val dungeon: StandaloneDungeonState?,
    val castle: StandaloneCastleState?,
    val party: StandaloneParty,
) {
    fun withParty(party: StandaloneParty): StandaloneRuntimeState = copy(party = party)

    companion object {
        fun from(
            state: CompiledStateImage,
            content: CompiledContentBundle,
        ): StandaloneRuntimeState =
            StandaloneRuntimeState(
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
                npcs = state.npcs,
                dungeon =
                    (state.mode as? rylween.compiled.CompiledRuntimeModeImage.Dungeon)
                        ?.session
                        ?.let { session ->
                            StandaloneDungeonState(
                                dungeon = session.dungeon,
                                maxDepth = session.maxDepth,
                                depth = session.depth,
                                levelEvents = session.levelEvents,
                            )
                        },
                castle = null,
                party = StandaloneParty.from(content, state.activeParty(), state.realm.rngState),
            )
    }
}

internal data class StandaloneParty(
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
    val visitedNpcs: List<CompiledNpcVisit>,
    val journal: List<CompiledJournalEntry>,
    val rngState: Long?,
) {
    fun passTurn(): StandaloneParty = copy(resources = resources.copy(timeLeft = max(0, resources.timeLeft - 1)))

    fun withGold(value: Int): StandaloneParty = copy(resources = resources.copy(gold = value.coerceAtLeast(0)))

    fun withResource(
        resource: Resource,
        amount: Int,
    ): StandaloneParty =
        when (resource) {
            Resource.Food -> copy(resources = resources.copy(food = amount.coerceAtLeast(0)))
            Resource.Torches -> copy(resources = resources.copy(torches = amount.coerceAtLeast(0)))
        }

    fun inventoryItem(slot: Int): CompiledHandleRef? = inventory.firstOrNull { current -> current.slot == slot }?.item

    fun withInventoryItem(
        slot: Int,
        item: CompiledHandleRef?,
    ): StandaloneParty =
        copy(
            inventory =
                inventory.map { current ->
                    if (current.slot == slot) current.copy(item = item) else current
                },
        )

    fun drawRandom(
        lo: Int,
        hi: Int,
    ): StandaloneRandomDraw {
        require(lo <= hi) { "random range must be ascending: $lo..$hi" }
        val seed = rngState ?: return StandaloneRandomDraw(lo, this)
        var state = seed.takeUnless { it == 0L } ?: 1L
        state = state xor (state ushr 12)
        state = state xor (state shl 25)
        state = state xor (state ushr 27)
        val mixed = state * REFERENCE_RANDOM_MULTIPLIER
        val span = hi.toLong() - lo.toLong() + 1L
        val offset = java.lang.Long.remainderUnsigned(mixed, span)
        return StandaloneRandomDraw(
            value = (lo.toLong() + offset).toInt(),
            party = copy(rngState = state),
        )
    }

    companion object {
        fun from(
            content: CompiledContentBundle,
            party: CompiledPartyImage,
            rngState: Long?,
        ): StandaloneParty {
            val position =
                when (val current = party.position) {
                    is CompiledWorldPosition.Wilderness -> current.point
                    is CompiledWorldPosition.Town -> GridPoint(0, 0)
                }
            return StandaloneParty(
                id = party.id,
                playerString = party.playerString,
                name = content.strings.value(party.nameString),
                position = position,
                facing = party.facing,
                resources = party.resources,
                counters = party.counters,
                flags = party.flags,
                milestones = party.milestones,
                inventory = party.inventory,
                members = party.members,
                visitedNpcs = party.visitedNpcs,
                journal = party.journal,
                rngState = rngState,
            )
        }
    }
}

internal data class StandaloneRandomDraw(
    val value: Int,
    val party: StandaloneParty,
)

internal data class StandaloneMoveResult(
    val party: StandaloneParty,
    val event: StandaloneMoveEvent? = null,
)

private const val REFERENCE_RANDOM_MULTIPLIER: Long = 0x2545F4914F6CDD1DL

internal class StandaloneInput(
    private val terminal: CompiledAnsiTerminal,
    scriptedInput: String?,
) {
    private val scripted = scriptedInput?.toList()?.let(::ArrayDeque)

    val exhausted: Boolean get() = scripted != null && scripted.isEmpty()

    fun readCommand(): Char? {
        val scriptedCommand = readScriptedCommand()
        if (scriptedCommand != null) return scriptedCommand
        if (scripted != null) return null
        val token = terminal.readKeyToken() ?: return null
        return token.firstOrNull()?.uppercaseChar() ?: '\n'
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

internal fun CompiledTextBlock.renderStandaloneLines(
    content: CompiledContentBundle,
    intValue: (CompiledIntExpr) -> String = { "[int]" },
    stringValue: (CompiledStringExpr) -> String = { "[string]" },
    runtimeValue: (CompiledRuntimeTextPart) -> String = { part -> "[${part.name}]" },
): List<String> =
    when (this) {
        is CompiledTextBlock.Ref -> listOf("[text:${content.slug(text)}]")
        is CompiledTextBlock.Inline -> lineStrings.map(content.strings::value)
        is CompiledTextBlock.Structured ->
            lines.map { line ->
                line.parts.joinToString(separator = "") { part ->
                    part.renderStandalone(content, intValue, stringValue, runtimeValue)
                }
            }
    }

private fun CompiledTextPart.renderStandalone(
    content: CompiledContentBundle,
    intValue: (CompiledIntExpr) -> String,
    stringValue: (CompiledStringExpr) -> String,
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
        is CompiledTextPart.LocationCoordinate -> "[location:${content.slug(location)}]"
        is CompiledTextPart.Runtime -> runtimeValue(kind)
    }

internal class StandaloneWorldMap(
    private val content: CompiledContentBundle,
    private val rows: List<String>,
    private val terrainByCode: Map<Char, CompiledTerrain>,
) {
    val width: Int = rows.maxOfOrNull(String::length) ?: 0
    val height: Int = rows.size

    fun terrainAt(point: GridPoint): CompiledTerrain? = tileAt(point)?.let(terrainByCode::get)

    private fun tileAt(point: GridPoint): Char? = rows.getOrNull(point.y)?.getOrNull(point.x)

    companion object {
        fun from(
            content: CompiledContentBundle,
            mapRef: CompiledHandleRef,
        ): StandaloneWorldMap {
            val map =
                content.worldMaps.firstOrNull { it.id == mapRef }
                    ?: error("missing compiled world map ${content.slug(mapRef)}")
            val layer = map.layers.firstOrNull() ?: error("world map ${content.slug(mapRef)} has no layers")
            return StandaloneWorldMap(
                content = content,
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
