/**
 * Bootstraps runtime sessions from loaded game content and snapshots.
 *
 * App launchers call this after `engine/Startup.kt` has selected or parsed a
 * state. It connects `RylweenKotlinEngine` to terminal adapters without moving
 * frontend policy into the backend-neutral runtime packages.
 */
package rylween.app

import rylween.engine.RylweenKotlinEngine
import rylween.frontend.terminal.CharacterCreationTerminalAdapter
import rylween.frontend.terminal.TerminalPort
import rylween.frontend.terminal.TerminalRunStatus
import rylween.frontend.terminal.TerminalTextRenderer
import rylween.runtime.RuntimeSnapshotModel
import rylween.runtime.toRuntimeSessionState
import rylween.runtime.town.CharacterCreationStep
import rylween.runtime.wilderness.WildernessSession
import rylween.runtime.wilderness.WorldMapGrid

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
        val creationTerminal =
            checkNotNull(terminal) {
                "character creation requires a terminal frontend"
            }
        val creation =
            engine.characterCreationSession(
                state = snapshot.state,
                playerName = playerName,
                rngState = rngState ?: snapshot.state.realm.rngState,
            )
        val result = CharacterCreationTerminalAdapter(creationTerminal, renderer).runUntilBlocked(creation)
        when (val status = result.status) {
            TerminalRunStatus.Finished -> Unit
            TerminalRunStatus.StepLimitReached -> error("character creation step limit reached")
            is TerminalRunStatus.Waiting ->
                error(
                    status.rejection?.let { rejection ->
                        "character creation input '${rejection.input}' did not match " +
                            rejection.expected.joinToString("/")
                    } ?: "character creation input unavailable",
                )
        }
        val created =
            when (val step = result.session.step) {
                is CharacterCreationStep.Finished -> step.runtime
                CharacterCreationStep.Aborted -> error("character creation aborted")
                else -> error("character creation stopped before a wilderness runtime was created")
            }
        val baseRuntime = snapshot.state.toRuntimeSessionState(rngState ?: snapshot.state.realm.rngState)
        engine.wildernessSession(
            runtime =
                created.copy(
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
