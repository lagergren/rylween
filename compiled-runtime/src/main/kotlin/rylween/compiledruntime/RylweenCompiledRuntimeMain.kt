/**
 * Standalone compiled-bundle runtime entry point.
 *
 * This package is the parallel implementation track for running Rylween from
 * the J1/J2 compiled bundle without composing the existing Kotlin
 * `engine`, `runtime`, `wilderness`, `town`, `dungeon`, `castle`, `battle`, or
 * terminal-adapter session classes. It reads direct compiled tables from
 * `compiled/CompiledBundle.kt` plus stable terminal presentation rows from
 * `compiled/ExternalBundleArtifact.kt`, then owns its own small runtime loop.
 *
 * The current slice proves the boundary with title/static-screen rendering and
 * direct wilderness movement. Full town, dungeon, castle, battle, persistence,
 * and compiled-script host services should be added here depth first instead
 * of calling back into the old session layer.
 */
@file:Suppress("ReplacePrintlnWithLogging", "UseOfSystemOutOrSystemErr")

package rylween.compiledruntime

import rylween.compiled.CompiledBuildingService
import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledGameBundle
import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledInstruction
import rylween.compiled.CompiledIntExpr
import rylween.compiled.CompiledInventorySlot
import rylween.compiled.CompiledJournalEntry
import rylween.compiled.CompiledMenuOption
import rylween.compiled.CompiledNpcVisit
import rylween.compiled.CompiledOpcode
import rylween.compiled.CompiledOperand
import rylween.compiled.CompiledPartyCounters
import rylween.compiled.CompiledPartyImage
import rylween.compiled.CompiledPartyMember
import rylween.compiled.CompiledRuntimeTextPart
import rylween.compiled.CompiledShopService
import rylween.compiled.CompiledStateImage
import rylween.compiled.CompiledStringExpr
import rylween.compiled.CompiledTerrain
import rylween.compiled.CompiledTextBlock
import rylween.compiled.CompiledTextPart
import rylween.compiled.CompiledViewCommand
import rylween.compiled.CompiledWorldPosition
import rylween.compiled.ExternalRuntimeRealm
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
        runtime.renderStaticScreen(staticScreen)
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
    fun renderStaticScreen(screenSlug: String) {
        CompiledAnsiTerminals.stdout().use { terminal ->
            CompiledAnsiScreen(terminal, bundle, systems).showStaticScreen(screenSlug)
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
            if (loadFile != null) {
                StandaloneCompiledSave.read(
                    file = loadFile,
                    expectedBundleHash = bundleContentHash,
                    content = content,
                    stateLookup = ::stateBySlug,
                )
            } else {
                StandaloneRuntimeState.from(stateBySlug(stateSlug), content)
            }
        val world = StandaloneWorldMap.from(content, runtime.currentMap)
        CompiledAnsiTerminals.open(scripted = scriptedInput != null).use { terminal ->
            val screen = CompiledAnsiScreen(terminal, bundle, systems)
            val input = StandaloneInput(terminal, scriptedInput)
            val commands = StandaloneWildernessCommands.from(systems)
            screen.bootFrame()
            screen.renderWilderness(runtime, world)
            while (true) {
                val command = input.readCommand()
                val redrawWilderness =
                    when {
                        command == null -> return
                        commands.matches("quit", command) -> return
                        commands.matches("moveNorth", command) -> {
                            runtime = runtime.withParty(move(runtime.party, world, 0, -1, screen))
                            true
                        }
                        commands.matches("moveSouth", command) -> {
                            runtime = runtime.withParty(move(runtime.party, world, 0, 1, screen))
                            true
                        }
                        commands.matches("moveWest", command) -> {
                            runtime = runtime.withParty(move(runtime.party, world, -1, 0, screen))
                            true
                        }
                        commands.matches("moveEast", command) -> {
                            runtime = runtime.withParty(move(runtime.party, world, 1, 0, screen))
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
                            runtime = runtime.withParty(enterTown(runtime.party, input, screen))
                            true
                        }
                        commands.matches("readyWeapon", command) -> {
                            screen.writeText(listOf("Ready weapon is not implemented in standalone runtime yet."))
                            false
                        }
                        commands.matches("readyArmor", command) -> {
                            screen.writeText(listOf("Ready armor is not implemented in standalone runtime yet."))
                            false
                        }
                        commands.matches("removeWeapon", command) -> {
                            screen.writeText(listOf("Remove weapon is not implemented in standalone runtime yet."))
                            false
                        }
                        commands.matches("removeArmor", command) -> {
                            screen.writeText(listOf("Remove armor is not implemented in standalone runtime yet."))
                            false
                        }
                        commands.matches("drop", command) -> {
                            screen.writeText(listOf("Drop item is not implemented in standalone runtime yet."))
                            false
                        }
                        commands.matches("camp", command) -> {
                            screen.writeText(listOf("Camp is not implemented in standalone runtime yet."))
                            false
                        }
                        commands.matches("hunt", command) -> {
                            screen.writeText(listOf("Hunt is not implemented in standalone runtime yet."))
                            false
                        }
                        commands.matches("castSpell", command) -> {
                            screen.writeText(listOf("Cast spell is not implemented in standalone runtime yet."))
                            false
                        }
                        commands.matches("quaff", command) -> {
                            screen.writeText(listOf("Quaff is not implemented in standalone runtime yet."))
                            false
                        }
                        command == '\n' -> false
                        else -> {
                            screen.writeText(listOf("Unknown command '$command'.", "Press ? for commands."))
                            false
                        }
                    }
                if (redrawWilderness) {
                    screen.renderWilderness(runtime, world)
                }
                if (input.exhausted) return
            }
        }
    }

    private fun move(
        party: StandaloneParty,
        world: StandaloneWorldMap,
        dx: Int,
        dy: Int,
        screen: CompiledAnsiScreen,
    ): StandaloneParty {
        val target = GridPoint(party.position.x + dx, party.position.y + dy)
        val terrain = world.terrainAt(target)
        if (terrain == null || !terrain.passable) {
            screen.writeText(listOf("Blocked."))
            return party
        }
        return party.copy(
            position = target,
            resources =
                party.resources.copy(
                    timeLeft = max(0, party.resources.timeLeft - terrain.movementCost),
                ),
        )
    }

    private fun printHelp(screen: CompiledAnsiScreen) {
        screen.writeText(
            listOf(
                "Move with 8/2/4/6 or arrows.",
                "E enters a town or site.",
                "S saves this wilderness state.",
                "! redraws the wilderness view.",
                "Space waits in place.",
                "X exits this compiled runtime.",
                "",
                "Dungeon, castle, battle,",
                "and more host services are next here.",
            ),
        )
    }

    private fun saveRuntime(
        runtime: StandaloneRuntimeState,
        saveFile: File,
        screen: CompiledAnsiScreen,
    ) {
        StandaloneCompiledSave.write(saveFile, runtime, bundleContentHash, content)
        screen.writeText(listOf("Game saved.", saveFile.path))
    }

    private fun enterTown(
        party: StandaloneParty,
        input: StandaloneInput,
        screen: CompiledAnsiScreen,
    ): StandaloneParty {
        val town = content.towns.firstOrNull { it.position == party.position }
        if (town == null) {
            screen.writeText(listOf("There is nothing to enter here."))
            return party
        }
        val services =
            town.services.mapNotNull { building ->
                content.buildingServices.firstOrNull { service -> service.building == building }
            }
        if (services.isEmpty()) {
            screen.writeText(listOf("${string(town.nameString)} has no compiled services."))
            return party
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
        val choice = input.readCommand() ?: return party
        if (choice == 'L') return party
        val index = choice.digitToIntOrNull()?.minus(1)
        val service = index?.let(services::getOrNull)
        if (service == null) {
            screen.writeText(listOf("No such service."))
            return party
        }
        return runService(party, service, input, screen)
    }

    private fun runService(
        party: StandaloneParty,
        service: CompiledBuildingService,
        input: StandaloneInput,
        screen: CompiledAnsiScreen,
    ): StandaloneParty {
        val sceneSlug = slug(service.scene)
        val program =
            content.scripts.roots.firstOrNull { root -> root.root.kind.name == "Scene" && root.root.slug == sceneSlug }
        if (program == null) {
            screen.writeText(listOf("No compiled scene program for $sceneSlug."))
            return party
        }
        screen.writeText(listOf("Running ${slug(service.building).readableSlug()}", "Scene: $sceneSlug"))
        return StandaloneScriptExecutor(content, systems.realm, input, screen, party).execute(program.instructions)
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
}

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

private class StandaloneScriptExecutor(
    private val content: CompiledContentBundle,
    private val realm: ExternalRuntimeRealm,
    private val input: StandaloneInput,
    private val screen: CompiledAnsiScreen,
    initialParty: StandaloneParty,
) {
    private var party: StandaloneParty = initialParty

    fun execute(instructions: List<CompiledInstruction>): StandaloneParty {
        instructions.forEach { instruction ->
            val stop = execute(instruction)
            if (stop) return party
        }
        return party
    }

    private fun execute(instruction: CompiledInstruction): Boolean =
        when (instruction.opcode) {
            CompiledOpcode.Screen -> false.also { screen.writeText(instruction.textBlockOperand(0).renderLines()) }
            CompiledOpcode.View -> false.also { renderView(instruction.viewOperand(0)) }
            CompiledOpcode.WaitInput -> false.also { input.readCommand() }
            CompiledOpcode.Pause -> false
            CompiledOpcode.Menu -> executeMenu(instruction)
            CompiledOpcode.Stop,
            CompiledOpcode.Break,
            -> true
            CompiledOpcode.ShopService ->
                false.also {
                    party = StandaloneCompiledShopService(content, realm, input, screen).run(instruction.shopServiceOperand(0), party)
                }
            CompiledOpcode.RandomItemOffer -> false.also { screen.writeText(listOf("Random item offer host is not implemented yet.")) }
            CompiledOpcode.GrantReward -> false.also { screen.writeText(listOf("Reward host is not implemented yet.")) }
            CompiledOpcode.Speak -> false.also { screen.writeText(listOf("Speak host is not implemented yet.")) }
            CompiledOpcode.TellRumour -> false.also { screen.writeText(listOf("Rumour host is not implemented yet.")) }
            CompiledOpcode.Encounter -> false.also { screen.writeText(listOf("Encounter host is not implemented yet.")) }
            CompiledOpcode.EnterDungeon,
            CompiledOpcode.DiscoverCave,
            -> false.also { screen.writeText(listOf("Dungeon host is not implemented yet.")) }
            CompiledOpcode.NativeCall ->
                false.also { screen.writeText(listOf("Native effect ${instruction.stringOperand(0)} is not implemented yet.")) }
            CompiledOpcode.RunScene -> false.also { screen.writeText(listOf("Nested scene host is not implemented yet.")) }
            else ->
                false.also {
                    screen.writeText(listOf("Compiled opcode ${instruction.opcode.name} is not implemented in standalone runtime yet."))
                }
        }

    private fun executeMenu(instruction: CompiledInstruction): Boolean {
        val options = instruction.menuOptionsOperand(1)
        val lines =
            buildList {
                addAll(instruction.textBlockOperand(0).renderLines())
                options.forEach { option ->
                    add("${option.binding.primaryStandaloneLabel(content)}. ${content.strings.value(option.binding.labelString)}")
                }
            }
        screen.writeText(lines)
        val choice = input.readCommand() ?: return true
        val option = options.firstOrNull { menu -> menu.binding.matchesStandalone(content, choice) }
        if (option == null) {
            screen.writeText(listOf("No such choice."))
            execute(instruction.instructionsOperand(2))
            return false
        }
        party = StandaloneScriptExecutor(content, realm, input, screen, party).execute(option.body)
        return false
    }

    private fun renderView(command: CompiledViewCommand) {
        screen.writeViewCommand(command.toRuntimeViewCommand())
    }

    private fun CompiledViewCommand.toRuntimeViewCommand(): CompiledRuntimeViewCommand =
        when (this) {
            CompiledViewCommand.ClearText -> CompiledRuntimeViewCommand.ClearText
            CompiledViewCommand.ClearMapWindow -> CompiledRuntimeViewCommand.ClearMapWindow
            CompiledViewCommand.RenderMap -> CompiledRuntimeViewCommand.RenderMap
            CompiledViewCommand.StatusLine -> CompiledRuntimeViewCommand.StatusLine
            is CompiledViewCommand.ShowImage -> CompiledRuntimeViewCommand.ShowImage(content.slug(image))
        }

    private fun CompiledTextBlock.renderLines(): List<String> =
        when (this) {
            is CompiledTextBlock.Ref,
            is CompiledTextBlock.Inline,
            is CompiledTextBlock.Structured,
            -> renderStandaloneLines(content)
        }

    private fun CompiledInstruction.textBlockOperand(index: Int): CompiledTextBlock =
        (operands[index] as CompiledOperand.TextBlockValue).value

    private fun CompiledInstruction.viewOperand(index: Int): CompiledViewCommand =
        (operands[index] as CompiledOperand.ViewCommandValue).value

    private fun CompiledInstruction.stringOperand(index: Int): String =
        content.strings.value((operands[index] as CompiledOperand.StringId).id)

    private fun CompiledInstruction.menuOptionsOperand(index: Int): List<CompiledMenuOption> =
        (operands[index] as CompiledOperand.MenuOptions).options

    private fun CompiledInstruction.instructionsOperand(index: Int): List<CompiledInstruction> =
        (operands[index] as CompiledOperand.Instructions).instructions

    private fun CompiledInstruction.shopServiceOperand(index: Int): CompiledShopService =
        (operands[index] as CompiledOperand.ShopService).service
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
