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
import rylween.compiled.CompiledInputBinding
import rylween.compiled.CompiledInstruction
import rylween.compiled.CompiledMenuOption
import rylween.compiled.CompiledOpcode
import rylween.compiled.CompiledOperand
import rylween.compiled.CompiledPartyImage
import rylween.compiled.CompiledPartyMember
import rylween.compiled.CompiledStateImage
import rylween.compiled.CompiledTerrain
import rylween.compiled.CompiledTextBlock
import rylween.compiled.CompiledTextPart
import rylween.compiled.CompiledViewCommand
import rylween.compiled.CompiledWorldPosition
import rylween.compiled.ExternalRuntimeSystems
import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeSystems
import rylween.dsl.GridPoint
import rylween.dsl.PartyResources
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
    runtime.runWilderness(stateSlug, scriptedInput)
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
        scriptedInput: String?,
    ) {
        val state = stateBySlug(stateSlug)
        var party = StandaloneParty.from(state.activeParty(), content)
        val world = StandaloneWorldMap.from(content, state.realm.currentMap)
        CompiledAnsiTerminals.open(scripted = scriptedInput != null).use { terminal ->
            val screen = CompiledAnsiScreen(terminal, bundle, systems)
            val input = StandaloneInput(terminal, scriptedInput)
            screen.bootFrame()
            screen.renderWilderness(state, party, world)
            while (true) {
                val command = input.readCommand()
                when (command) {
                    null,
                    'X',
                    -> return
                    '8',
                    'N',
                    -> party = move(party, world, 0, -1, screen)
                    '2',
                    -> party = move(party, world, 0, 1, screen)
                    '4',
                    'W',
                    -> party = move(party, world, -1, 0, screen)
                    '6',
                    -> party = move(party, world, 1, 0, screen)
                    ' ' -> party = party.passTurn()
                    '?' -> printHelp(screen)
                    'T' -> screen.showStaticScreen("title")
                    'E' -> enterTown(party, input, screen)
                    '\n' -> Unit
                    else -> screen.writeText(listOf("Unknown command '$command'.", "Press ? for commands."))
                }
                screen.renderWilderness(state, party, world)
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
                "Space waits in place.",
                "X exits this compiled runtime.",
                "",
                "Dungeon, castle, battle, save/load,",
                "and more host services are next here.",
            ),
        )
    }

    private fun enterTown(
        party: StandaloneParty,
        input: StandaloneInput,
        screen: CompiledAnsiScreen,
    ) {
        val town = content.towns.firstOrNull { it.position == party.position }
        if (town == null) {
            screen.writeText(listOf("There is nothing to enter here."))
            return
        }
        val services =
            town.services.mapNotNull { building ->
                content.buildingServices.firstOrNull { service -> service.building == building }
            }
        if (services.isEmpty()) {
            screen.writeText(listOf("${string(town.nameString)} has no compiled services."))
            return
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
        val choice = input.readCommand() ?: return
        if (choice == 'L') return
        val index = choice.digitToIntOrNull()?.minus(1)
        val service = index?.let(services::getOrNull)
        if (service == null) {
            screen.writeText(listOf("No such service."))
            return
        }
        runService(service, input, screen)
    }

    private fun runService(
        service: CompiledBuildingService,
        input: StandaloneInput,
        screen: CompiledAnsiScreen,
    ) {
        val sceneSlug = slug(service.scene)
        val program =
            content.scripts.roots.firstOrNull { root -> root.root.kind.name == "Scene" && root.root.slug == sceneSlug }
        if (program == null) {
            screen.writeText(listOf("No compiled scene program for $sceneSlug."))
            return
        }
        screen.writeText(listOf("Running ${slug(service.building).readableSlug()}", "Scene: $sceneSlug"))
        StandaloneScriptExecutor(content, input, screen).execute(program.instructions)
    }

    private fun CompiledStateImage.activeParty(): CompiledPartyImage =
        parties.firstOrNull { it.id == realm.activeParty }
            ?: error("state ${slug(state)} has no active party ${realm.activeParty}")

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
            )
    }
}

internal data class StandaloneParty(
    val name: String,
    val position: GridPoint,
    val resources: PartyResources,
    val members: List<CompiledPartyMember>,
) {
    fun passTurn(): StandaloneParty = copy(resources = resources.copy(timeLeft = max(0, resources.timeLeft - 1)))

    companion object {
        fun from(
            party: CompiledPartyImage,
            content: CompiledContentBundle,
        ): StandaloneParty {
            val position =
                when (val current = party.position) {
                    is CompiledWorldPosition.Wilderness -> current.point
                    is CompiledWorldPosition.Town -> GridPoint(0, 0)
                }
            return StandaloneParty(
                name = content.strings.value(party.nameString),
                position = position,
                resources = party.resources,
                members = party.members,
            )
        }
    }
}

private class StandaloneInput(
    private val terminal: CompiledAnsiTerminal,
    scriptedInput: String?,
) {
    private val scripted = scriptedInput?.iterator()

    val exhausted: Boolean get() = scripted != null && !scripted.hasNext()

    fun readCommand(): Char? {
        scripted?.nextCommand()?.let { command ->
            return command
        }
        return terminal.readKeyToken()?.firstOrNull()?.uppercaseChar()
    }

    private fun CharIterator.nextCommand(): Char? {
        while (hasNext()) {
            val next = nextChar()
            if (!next.isWhitespace()) return next.uppercaseChar()
        }
        return null
    }
}

private class StandaloneScriptExecutor(
    private val content: CompiledContentBundle,
    private val input: StandaloneInput,
    private val screen: CompiledAnsiScreen,
) {
    fun execute(instructions: List<CompiledInstruction>) {
        instructions.forEach { instruction ->
            val stop = execute(instruction)
            if (stop) return
        }
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
            CompiledOpcode.ShopService -> false.also { screen.writeText(listOf("Shop service host is not implemented yet.")) }
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
                    add("${option.binding.primaryLabel()}. ${content.strings.value(option.binding.labelString)}")
                }
            }
        screen.writeText(lines)
        val choice = input.readCommand() ?: return true
        val option = options.firstOrNull { menu -> menu.binding.matches(choice) }
        if (option == null) {
            screen.writeText(listOf("No such choice."))
            execute(instruction.instructionsOperand(2))
            return false
        }
        execute(option.body)
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
            is CompiledTextBlock.Ref -> listOf("[text:${content.slug(text)}]")
            is CompiledTextBlock.Inline -> lineStrings.map(content.strings::value)
            is CompiledTextBlock.Structured ->
                lines.map { line ->
                    line.parts.joinToString(separator = "") { part -> part.render() }
                }
        }

    private fun CompiledTextPart.render(): String =
        when (this) {
            is CompiledTextPart.Literal -> content.strings.value(string)
            is CompiledTextPart.IntValue -> "[int]"
            is CompiledTextPart.StringValue -> "[string]"
            is CompiledTextPart.RoleName -> "[role:${content.slug(role)}]"
            is CompiledTextPart.RoleLocation -> "[role-location:${content.slug(role)}]"
            is CompiledTextPart.TownName -> content.townName(town)
            is CompiledTextPart.FuzzyTownLocation -> "${content.townName(town)} area"
            is CompiledTextPart.LocationCoordinate -> "[location:${content.slug(location)}]"
            is CompiledTextPart.Runtime -> "[${kind.name}]"
        }

    private fun CompiledInputBinding.matches(choice: Char): Boolean {
        val accepted = listOf(primaryString) + aliasStrings
        return accepted
            .map(content.strings::value)
            .any { value -> value.equals(choice.toString(), ignoreCase = true) }
    }

    private fun CompiledInputBinding.primaryLabel(): String = content.strings.value(primaryString)

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

private fun CompiledContentBundle.ref(
    kind: CompiledHandleKind,
    slug: String,
): CompiledHandleRef {
    val slugString = strings.idOf(slug) ?: error("unknown $kind slug $slug")
    return handles.ref(kind, slugString)
}

private fun CompiledContentBundle.townName(ref: CompiledHandleRef): String =
    towns.firstOrNull { town -> town.id == ref }?.let { town -> strings.value(town.nameString) } ?: slug(ref)
