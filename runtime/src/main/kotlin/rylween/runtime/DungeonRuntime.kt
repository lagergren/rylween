/**
 * Direct dungeon loop for the runtime.
 *
 * This is the runtime counterpart to the reusable dungeon host: it
 * consumes compiled dungeon scenarios/events, authored dungeon prompt rows from
 * `runtimeSystems.dungeon`, and direct compiled script/battle services. It owns
 * the dungeon flow instead of delegating to the legacy runtime.
 */

package rylween.runtime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledDungeonEvent
import rylween.compiled.CompiledDungeonRandomEvent
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledScriptProgram
import rylween.compiled.ExternalRuntimeCommand
import rylween.compiled.ExternalRuntimeTextBlock
import rylween.compiled.RuntimeCommandActionId
import rylween.compiled.RuntimeCommandGroupId
import rylween.dsl.DungeonEventDisposition
import rylween.dsl.impl.Resource
import rylween.dsl.impl.RuntimeMode

/** Savable dungeon state. */
data class DungeonState(
    val dungeon: CompiledHandleRef,
    val maxDepth: Int,
    val depth: Int,
    val levelEvents: Map<Int, CompiledHandleRef>,
    val pendingMission: CompiledHandleRef? = null,
    val postDungeon: ScriptContinuationState? = null,
)

/** Runs dungeon exploration directly from compiled content and runtime-system rows. */
internal class DungeonRuntime(
    context: RuntimeLoopContext,
) {
    private val content = context.content
    private val systems = context.systems
    private val input = context.input
    private val screen = context.screen
    private val save = context.save
    private val log = context.log
    private val utility = UtilityActions(content, systems, input, screen, log)
    private val commands = systems.commands.getValue(RuntimeCommandGroupId.Dungeon.id)

    fun run(initialRuntime: RuntimeState): RuntimeState {
        var runtime = initialRuntime.withDungeonMode()
        screen.renderDungeon(runtime)
        runtime = runDepthEvent(runtime)
        while (runtime.currentMode == RuntimeMode.Dungeon) {
            screen.renderDungeon(runtime)
            val command = input.readCommand() ?: return runtime
            runtime =
                when {
                    commands.matches(RuntimeCommandActionId.Descend, command) -> descend(runtime)
                    commands.matches(RuntimeCommandActionId.Ascend, command) -> ascend(runtime)
                    commands.matches(RuntimeCommandActionId.Help, command) -> runtime.also { showHelp(it) }
                    commands.matches(
                        RuntimeCommandActionId.ViewParty,
                        command,
                    ) -> runtime.also(::showDungeonPageViews)
                    commands.matches(RuntimeCommandActionId.Save, command) -> runtime.also { saveRuntime(it) }
                    commands.matches(RuntimeCommandActionId.Drop, command) -> utility.drop(runtime).also(::redrawDungeonIfCurrent)
                    commands.matches(
                        RuntimeCommandActionId.RemoveArmor,
                        command,
                    ) -> utility.removeArmor(runtime).also(::redrawDungeonIfCurrent)
                    commands.matches(
                        RuntimeCommandActionId.RemoveWeapon,
                        command,
                    ) -> utility.removeWeapon(runtime).also(::redrawDungeonIfCurrent)
                    commands.matches(
                        RuntimeCommandActionId.ReadyWeapon,
                        command,
                    ) -> utility.readyWeapon(runtime).also(::redrawDungeonIfCurrent)
                    commands.matches(
                        RuntimeCommandActionId.ReadyArmor,
                        command,
                    ) -> utility.readyArmor(runtime).also(::redrawDungeonIfCurrent)
                    commands.matches(RuntimeCommandActionId.Quaff, command) -> utility.quaff(runtime).also(::redrawDungeonIfCurrent)
                    commands.matches(
                        RuntimeCommandActionId.CastSpell,
                        command,
                    ) -> utility.castPriestSpell(runtime).also(::redrawDungeonIfCurrent)
                    else ->
                        runtime.also {
                            screen.writeText(
                                systems.dungeon.commandHint.renderDungeon(it.dungeon?.depth ?: 1, screen.textColumns),
                            )
                        }
                }
            if (input.exhausted) return runtime
        }
        return runtime
    }

    private fun descend(runtime: RuntimeState): RuntimeState {
        val dungeon = runtime.dungeon ?: return runtime
        return when {
            dungeon.depth >= dungeon.maxDepth ->
                runtime.also {
                    screen.showMessage(
                        systems.dungeon.prompts.atMaxDepth
                            .renderDungeon(dungeon.depth, screen.textColumns),
                    )
                }
            runtime.party.resources.torches <= 0 ->
                runtime.also {
                    screen.showMessage(
                        systems.dungeon.prompts.noTorches
                            .renderDungeon(dungeon.depth, screen.textColumns),
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
                        .renderDungeon(nextDepth, screen.textColumns),
                )
                runDepthEvent(next)
            }
        }
    }

    private fun ascend(runtime: RuntimeState): RuntimeState {
        val dungeon = runtime.dungeon ?: return runtime
        if (dungeon.depth <= 1) {
            screen.showMessage(
                systems.dungeon.prompts.leaveCavern
                    .renderDungeon(dungeon.depth, screen.textColumns),
            )
            val exited = runtime.copy(currentMode = RuntimeMode.Wilderness, currentTown = null, dungeon = null)
            val resumed = dungeon.postDungeon?.let { continuation -> resumePostDungeon(exited, continuation) } ?: exited
            return dungeon.pendingMission?.let(resumed::withCompletedMission) ?: resumed
        }
        val nextDepth = dungeon.depth - 1
        val next = runtime.copy(dungeon = dungeon.copy(depth = nextDepth))
        screen.writeText(
            systems.dungeon.prompts.enterLevel
                .renderDungeon(nextDepth, screen.textColumns),
        )
        return runDepthEvent(next)
    }

    private fun runDepthEvent(runtime: RuntimeState): RuntimeState {
        val dungeon = runtime.dungeon ?: return runtime
        val event = dungeon.levelEvents[dungeon.depth]?.let(::eventByRef) ?: return runtime
        var next = runtime
        val program = programByRef(event.scriptRoot)
        val randomEncounterTier = event.randomEncounterTier
        next =
            if (randomEncounterTier != null && program.instructions.isEmpty()) {
                BattleRunner(content, systems.combat, systems.pacing.battle, input, screen, log, PausePrompts(systems.prompts))
                    .runTier(next, content.slug(randomEncounterTier))
                    .let { battle ->
                        if (battle.messages.isNotEmpty()) screen.writeText(battle.messages)
                        battle.runtime
                    }
            } else {
                ScriptExecutor(
                    content = content,
                    systems = systems,
                    input = input,
                    screen = screen,
                    initialRuntime = next,
                    initialLocals = mapOf("dungeon_level" to dungeon.depth),
                    log = log,
                ).execute(program)
            }
        return next.copy(dungeon = next.dungeon?.clearEventIfNeeded(event, next))
    }

    private fun resumePostDungeon(
        runtime: RuntimeState,
        continuation: ScriptContinuationState,
    ): RuntimeState =
        ScriptExecutor(content, systems, input, screen, runtime, log = log)
            .execute(continuation)

    private fun DungeonState.clearEventIfNeeded(
        event: CompiledDungeonEvent,
        runtime: RuntimeState,
    ): DungeonState {
        val shouldClear =
            event.disposition == DungeonEventDisposition.ClearAfterRun ||
                event.clearWhenFlag?.let { flag -> flag in runtime.party.flags } == true
        return if (shouldClear) copy(levelEvents = levelEvents - depth) else this
    }

    private fun showHelp(runtime: RuntimeState) {
        val depth = runtime.dungeon?.depth ?: 1
        val pages =
            systems.dungeon.helpPages
                .map { page -> page.renderDungeon(depth, screen.textColumns) }
                .ifEmpty { listOf(commands.map(ExternalRuntimeCommand::runtimeMnemonicLine)) }
        val pausePrompts = PausePrompts(systems.prompts)
        pages.forEach { page ->
            screen.writeText(page.withPausePrompt(pausePrompts))
            if (input.readCommand() == null) return
        }
        redrawDungeonIfCurrent(runtime)
    }

    private fun showDungeonPageViews(runtime: RuntimeState) {
        if (
            PartyPages.showPartyPageViews(
                content = content,
                systems = systems,
                input = input,
                screen = screen,
                runtime = runtime,
            )
        ) {
            redrawDungeonIfCurrent(runtime)
        }
    }

    private fun redrawDungeonIfCurrent(runtime: RuntimeState) {
        if (runtime.currentMode == RuntimeMode.Dungeon) screen.renderDungeon(runtime)
    }

    private fun saveRuntime(runtime: RuntimeState) {
        save.write(runtime, content)
        log.event(
            "runtime.save user=${save.userId} file=${save.file.path} " +
                "position=${runtime.party.position.x},${runtime.party.position.y} mode=${runtime.currentMode}",
        )
        screen.writeText(listOf(save.file.path))
    }

    private fun eventByRef(ref: CompiledHandleRef): CompiledDungeonEvent =
        content.dungeonEvents.firstOrNull { event -> event.id == ref }
            ?: error("missing dungeon event ${content.slug(ref)}")

    private fun programByRef(root: CompiledHandleRef): CompiledScriptProgram =
        content.scripts.roots.firstOrNull { program -> program.rootHandle == root }
            ?: error("missing compiled script root ${content.slug(root)}")
}

internal fun RuntimeState.enterDungeon(
    content: CompiledContentBundle,
    dungeon: CompiledHandleRef,
    levels: Int,
    postDungeon: ScriptContinuationState? = null,
    pendingMission: CompiledHandleRef? = null,
    log: RuntimeLog,
): RuntimeState {
    var plannedParty = party
    val maxDepth = levels.coerceAtLeast(1)
    val scenario = content.dungeonScenarios.firstOrNull { scenario -> scenario.dungeon == dungeon }
    val fixedEvents =
        scenario
            ?.fixedLevelEvents
            .orEmpty()
            .filterKeys { level -> level in 1..maxDepth }
    val randomEvents =
        (1..maxDepth)
            .filterNot { level -> level in fixedEvents }
            .mapNotNull { level ->
                val draw =
                    scenario
                        ?.randomEvents
                        .orEmpty()
                        .drawDungeonEvent(plannedParty, content, dungeon, level, log) ?: return@mapNotNull null
                plannedParty = draw.party
                level to draw.event
            }.toMap()
    return copy(
        currentMode = RuntimeMode.Dungeon,
        currentTown = null,
        party = plannedParty,
        dungeon =
            DungeonState(
                dungeon = dungeon,
                maxDepth = maxDepth,
                depth = 1,
                levelEvents = randomEvents + fixedEvents,
                pendingMission = pendingMission,
                postDungeon = postDungeon,
            ),
        castle = null,
    )
}

internal fun RuntimeState.withDungeonMode(): RuntimeState = copy(currentMode = RuntimeMode.Dungeon, currentTown = null)

internal fun ExternalRuntimeTextBlock.renderDungeon(
    depth: Int,
    wrapWidth: Int? = null,
): List<String> =
    render(
        values = { kind ->
            when (kind) {
                "dungeonDepth" -> depth.toString()
                else -> null
            }
        },
        wrapWidth = wrapWidth,
    )

private data class DungeonEventDraw(
    val party: PartyState,
    val event: CompiledHandleRef,
)

private fun List<CompiledDungeonRandomEvent>.drawDungeonEvent(
    party: PartyState,
    content: CompiledContentBundle,
    dungeon: CompiledHandleRef,
    level: Int,
    log: RuntimeLog,
): DungeonEventDraw? {
    if (party.rngState == null) return null
    val totalWeight = sumOf(CompiledDungeonRandomEvent::weight)
    if (totalWeight <= 0) return null
    val draw = party.drawRandom(1, totalWeight)
    var running = 0
    val event =
        first { random ->
            running += random.weight
            draw.value <= running
        }.event
    log.rngDraw(
        context = "dungeon.random_event",
        lo = 1,
        hi = totalWeight,
        value = draw.value,
        details =
            mapOf(
                "dungeon" to content.slug(dungeon),
                "level" to level,
                "event" to content.slug(event),
            ),
    )
    return DungeonEventDraw(draw.party, event)
}
