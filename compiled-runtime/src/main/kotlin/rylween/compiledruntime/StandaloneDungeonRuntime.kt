/**
 * Direct dungeon loop for the standalone compiled-bundle runtime.
 *
 * This is the compiled-runtime counterpart to the reusable dungeon host: it
 * consumes compiled dungeon scenarios/events, authored dungeon prompt rows from
 * `runtimeSystems.dungeon`, and direct compiled script/battle services. It owns
 * the dungeon flow instead of delegating to the legacy runtime.
 */

package rylween.compiledruntime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledDungeonEvent
import rylween.compiled.CompiledDungeonRandomEvent
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledScriptProgram
import rylween.compiled.ExternalRuntimeSystems
import rylween.compiled.ExternalRuntimeTextBlock
import rylween.dsl.DungeonEventDisposition
import rylween.dsl.Resource
import rylween.dsl.RuntimeMode
import java.io.File

/** Saveable standalone dungeon state. */
internal data class StandaloneDungeonState(
    val dungeon: CompiledHandleRef,
    val maxDepth: Int,
    val depth: Int,
    val levelEvents: Map<Int, CompiledHandleRef>,
    val postDungeon: StandaloneScriptContinuation? = null,
)

/** Runs dungeon exploration directly from compiled content and runtime-system rows. */
internal class StandaloneDungeonRuntime(
    private val content: CompiledContentBundle,
    private val systems: ExternalRuntimeSystems,
    private val input: StandaloneInput,
    private val screen: CompiledAnsiScreen,
    private val saveFile: File,
    private val bundleContentHash: String,
) {
    private val utility = StandaloneUtilityActions(content, systems, input, screen)
    private val commands = systems.commands.getValue(DUNGEON_COMMAND_GROUP)

    fun run(initialRuntime: StandaloneRuntimeState): StandaloneRuntimeState {
        var runtime = initialRuntime.withDungeonMode()
        screen.renderDungeon(runtime)
        runtime = runDepthEvent(runtime)
        while (runtime.currentMode == RuntimeMode.Dungeon) {
            screen.renderDungeon(runtime)
            val command = input.readCommand() ?: return runtime
            runtime =
                when {
                    commands.matches("descend", command) -> descend(runtime)
                    commands.matches("ascend", command) -> ascend(runtime)
                    commands.matches("help", command) -> runtime.also { showHelp(it) }
                    commands.matches(
                        "viewParty",
                        command,
                    ) -> runtime.also { screen.writeText(StandalonePartyPages.partyLines(content, it.party)) }
                    commands.matches("save", command) -> runtime.also { saveRuntime(it) }
                    commands.matches("drop", command) -> utility.drop(runtime)
                    commands.matches("removeArmor", command) -> utility.removeArmor(runtime)
                    commands.matches("removeWeapon", command) -> utility.removeWeapon(runtime)
                    commands.matches("readyWeapon", command) -> utility.readyWeapon(runtime)
                    commands.matches("readyArmor", command) -> utility.readyArmor(runtime)
                    commands.matches("quaff", command) -> utility.quaff(runtime)
                    commands.matches("castSpell", command) -> utility.castPriestSpell(runtime)
                    else -> runtime.also { screen.writeText(systems.dungeon.commandHint.renderDungeon(it.dungeon?.depth ?: 1)) }
                }
            if (input.exhausted) return runtime
        }
        return runtime
    }

    private fun descend(runtime: StandaloneRuntimeState): StandaloneRuntimeState {
        val dungeon = runtime.dungeon ?: return runtime
        return when {
            dungeon.depth >= dungeon.maxDepth ->
                runtime.also {
                    screen.writeText(
                        systems.dungeon.prompts.atMaxDepth
                            .renderDungeon(dungeon.depth),
                    )
                }
            runtime.party.resources.torches <= 0 ->
                runtime.also {
                    screen.writeText(
                        systems.dungeon.prompts.noTorches
                            .renderDungeon(dungeon.depth),
                    )
                }
            else -> {
                val nextDepth = dungeon.depth + 1
                val next =
                    runtime
                        .copy(dungeon = dungeon.copy(depth = nextDepth))
                        .withParty(runtime.party.withResource(Resource.Torches, runtime.party.resources.torches - 1))
                screen.writeText(
                    systems.dungeon.prompts.enterLevel
                        .renderDungeon(nextDepth),
                )
                runDepthEvent(next)
            }
        }
    }

    private fun ascend(runtime: StandaloneRuntimeState): StandaloneRuntimeState {
        val dungeon = runtime.dungeon ?: return runtime
        if (dungeon.depth <= 1) {
            screen.writeText(
                systems.dungeon.prompts.leaveCavern
                    .renderDungeon(dungeon.depth),
            )
            val exited = runtime.copy(currentMode = RuntimeMode.Wilderness, currentTown = null, dungeon = null)
            return dungeon.postDungeon?.let { continuation -> resumePostDungeon(exited, continuation) } ?: exited
        }
        val nextDepth = dungeon.depth - 1
        val next = runtime.copy(dungeon = dungeon.copy(depth = nextDepth))
        screen.writeText(
            systems.dungeon.prompts.enterLevel
                .renderDungeon(nextDepth),
        )
        return runDepthEvent(next)
    }

    private fun runDepthEvent(runtime: StandaloneRuntimeState): StandaloneRuntimeState {
        val dungeon = runtime.dungeon ?: return runtime
        val event = dungeon.levelEvents[dungeon.depth]?.let(::eventByRef) ?: return runtime
        var next = runtime
        val program = programByRef(event.scriptRoot)
        val randomEncounterTier = event.randomEncounterTier
        next =
            if (randomEncounterTier != null && program.instructions.isEmpty()) {
                StandaloneCompiledBattleRunner(content, systems.combat, input, screen)
                    .runTier(next, content.slug(randomEncounterTier))
                    .let { battle ->
                        if (battle.messages.isNotEmpty()) screen.writeText(battle.messages)
                        battle.runtime
                    }
            } else {
                StandaloneScriptExecutor(
                    content = content,
                    systems = systems,
                    input = input,
                    screen = screen,
                    initialRuntime = next,
                    initialLocals = mapOf("dungeon_level" to dungeon.depth),
                ).execute(program)
            }
        return next.copy(dungeon = next.dungeon?.clearEventIfNeeded(event, next))
    }

    private fun resumePostDungeon(
        runtime: StandaloneRuntimeState,
        continuation: StandaloneScriptContinuation,
    ): StandaloneRuntimeState =
        StandaloneScriptExecutor(content, systems, input, screen, runtime)
            .execute(continuation)

    private fun StandaloneDungeonState.clearEventIfNeeded(
        event: CompiledDungeonEvent,
        runtime: StandaloneRuntimeState,
    ): StandaloneDungeonState {
        val shouldClear =
            event.disposition == DungeonEventDisposition.ClearAfterRun ||
                event.clearWhenFlag?.let { flag -> flag in runtime.party.flags } == true
        return if (shouldClear) copy(levelEvents = levelEvents - depth) else this
    }

    private fun showHelp(runtime: StandaloneRuntimeState) {
        val depth = runtime.dungeon?.depth ?: 1
        val help =
            systems.dungeon.helpPages
                .firstOrNull()
                ?.renderDungeon(depth)
                ?: commands.map { command -> "${command.binding.primary}. ${command.label ?: command.binding.label}" }
        screen.writeText(help)
    }

    private fun saveRuntime(runtime: StandaloneRuntimeState) {
        StandaloneCompiledSave.write(saveFile, runtime, bundleContentHash, content)
        screen.writeText(listOf(saveFile.path))
    }

    private fun eventByRef(ref: CompiledHandleRef): CompiledDungeonEvent =
        content.dungeonEvents.firstOrNull { event -> event.id == ref }
            ?: error("missing dungeon event ${content.slug(ref)}")

    private fun programByRef(root: CompiledHandleRef): CompiledScriptProgram =
        content.scripts.roots.firstOrNull { program -> program.rootHandle == root }
            ?: error("missing compiled script root ${content.slug(root)}")
}

internal fun StandaloneRuntimeState.enterDungeon(
    content: CompiledContentBundle,
    dungeon: CompiledHandleRef,
    levels: Int,
    postDungeon: StandaloneScriptContinuation? = null,
): StandaloneRuntimeState {
    var plannedParty = party
    val maxDepth = levels.coerceAtLeast(1)
    val scenario = content.dungeonScenarios.firstOrNull { scenario -> scenario.dungeon == dungeon }
    val randomEvents =
        (1..maxDepth)
            .mapNotNull { level ->
                val draw = scenario?.randomEvents.orEmpty().drawDungeonEvent(plannedParty) ?: return@mapNotNull null
                plannedParty = draw.party
                level to draw.event
            }.toMap()
    val fixedEvents =
        scenario
            ?.fixedLevelEvents
            .orEmpty()
            .filterKeys { level -> level in 1..maxDepth }
    return copy(
        currentMode = RuntimeMode.Dungeon,
        currentTown = null,
        party = plannedParty,
        dungeon =
            StandaloneDungeonState(
                dungeon = dungeon,
                maxDepth = maxDepth,
                depth = 1,
                levelEvents = randomEvents + fixedEvents,
                postDungeon = postDungeon,
            ),
        castle = null,
    )
}

internal fun StandaloneRuntimeState.withDungeonMode(): StandaloneRuntimeState = copy(currentMode = RuntimeMode.Dungeon, currentTown = null)

internal fun ExternalRuntimeTextBlock.renderDungeon(depth: Int): List<String> =
    render { kind ->
        when (kind) {
            "dungeonDepth" -> depth.toString()
            else -> null
        }
    }

private data class DungeonEventDraw(
    val party: StandaloneParty,
    val event: CompiledHandleRef,
)

private fun List<CompiledDungeonRandomEvent>.drawDungeonEvent(party: StandaloneParty): DungeonEventDraw? {
    val totalWeight = sumOf(CompiledDungeonRandomEvent::weight)
    if (totalWeight <= 0) return null
    val draw = party.drawRandom(1, totalWeight)
    var running = 0
    val event =
        first { random ->
            running += random.weight
            draw.value <= running
        }.event
    return DungeonEventDraw(draw.party, event)
}

private fun List<rylween.compiled.ExternalRuntimeCommand>.matches(
    action: String,
    command: Char,
): Boolean =
    firstOrNull { candidate -> candidate.action == action }
        ?.binding
        ?.matches(command)
        ?: false

private const val DUNGEON_COMMAND_GROUP = "dungeon"
