package rylween.app

import rylween.engine.RylweenKotlinEngine
import rylween.frontend.terminal.CharacterCreationTerminalAdapter
import rylween.frontend.terminal.TerminalPort
import rylween.frontend.terminal.TerminalRunStatus
import rylween.frontend.terminal.TerminalTextRenderer
import rylween.runtime.CharacterCreationStep
import rylween.runtime.RuntimeSnapshotModel
import rylween.runtime.WildernessSession
import rylween.runtime.WorldMapGrid
import rylween.runtime.toRuntimeSessionState

internal fun bootWildernessSession(
    engine: RylweenKotlinEngine,
    terminal: TerminalPort? = null,
    renderer: TerminalTextRenderer,
    snapshot: RuntimeSnapshotModel,
    worldMap: WorldMapGrid,
    rngState: Long?,
    newGame: Boolean,
    playerName: String = "Player",
): WildernessSession =
    if (newGame) {
        val creationTerminal = checkNotNull(terminal) {
            "character creation requires a terminal frontend"
        }
        val creation = engine.characterCreationSession(
            state = snapshot.state,
            playerName = playerName,
            rngState = rngState ?: snapshot.state.realm.rngState,
        )
        val result = CharacterCreationTerminalAdapter(creationTerminal, renderer).runUntilBlocked(creation)
        when (val status = result.status) {
            TerminalRunStatus.Finished -> Unit
            TerminalRunStatus.StepLimitReached -> error("character creation step limit reached")
            is TerminalRunStatus.Waiting -> error(
                status.rejection?.let { rejection ->
                    "character creation input '${rejection.input}' did not match " +
                        rejection.expected.joinToString("/")
                } ?: "character creation input unavailable",
            )
        }
        val created = when (val step = result.session.step) {
            is CharacterCreationStep.Finished -> step.runtime
            CharacterCreationStep.Aborted -> error("character creation aborted")
            else -> error("character creation stopped before a wilderness runtime was created")
        }
        val baseRuntime = snapshot.state.toRuntimeSessionState(rngState ?: snapshot.state.realm.rngState)
        engine.wildernessSession(
            runtime = created.copy(
                locations = baseRuntime.locations,
                npcs = baseRuntime.npcs,
            ),
            worldMap = worldMap,
            realizeNpcs = true,
        )
    } else {
        engine.wildernessSession(
            snapshot = snapshot,
            worldMap = worldMap,
            rngState = rngState ?: snapshot.state.realm.rngState,
        )
    }
