/**
 * Boot facade from authored game content into backend-neutral runtime sessions.
 *
 * App launchers and tests use [RylweenKotlinEngine] to create wilderness, town,
 * dungeon, Ydarg, battle, and character-creation sessions from validated
 * [rylween.dsl.GameModel] content.
 */

package rylween.engine

import rylween.dsl.GameModel
import rylween.dsl.PartyId
import rylween.dsl.PartyState
import rylween.dsl.RuntimeMode
import rylween.dsl.StateModel
import rylween.dsl.TownId
import rylween.dsl.Wilderness
import rylween.dsl.dungeon
import rylween.dsl.partyId
import rylween.dsl.random
import rylween.dsl.runtimeContent
import rylween.dsl.town
import rylween.dsl.worldMap
import rylween.runtime.NpcPlacementRealizerConfig
import rylween.runtime.ReferenceRylweenRandom
import rylween.runtime.RuntimeModeSnapshot
import rylween.runtime.RuntimeSessionState
import rylween.runtime.RuntimeSnapshotModel
import rylween.runtime.RylweenRandomPort
import rylween.runtime.dungeon.toDungeonSession
import rylween.runtime.party
import rylween.runtime.realizeNpcPlacements
import rylween.runtime.toRuntimeSessionState
import rylween.runtime.town.CharacterCreationSession
import rylween.runtime.town.SpeakRuntimeContent
import rylween.runtime.town.TownSession
import rylween.runtime.wilderness.WildernessSession
import rylween.runtime.wilderness.WildernessSessionStep
import rylween.runtime.wilderness.WorldMapGrid
import rylween.runtime.wilderness.toMissionPostDungeonContinuation
import rylween.runtime.ydarg.toYdargSession

/** Factory and resume helper for the current pure-Kotlin runtime session graph. */
class RylweenKotlinEngine(
    private val content: GameModel,
    private val npcPlacementConfig: NpcPlacementRealizerConfig = NpcPlacementRealizerConfig(),
) {
    private fun initialRuntime(
        state: StateModel,
        rngState: Long? = state.realm.rngState,
        realizeNpcs: Boolean = true,
    ): RuntimeSessionState {
        val runtime = state.toRuntimeSessionState(rngState)
        return if (realizeNpcs) {
            runtime.realizeNpcPlacements(content.characters, content.towns, npcPlacementConfig)
        } else {
            runtime
        }
    }

    private fun initialRuntime(
        party: PartyState,
        hour: Int,
        currentTown: TownId?,
        rngState: Long? = null,
        realizeNpcs: Boolean = true,
    ): RuntimeSessionState {
        val runtime =
            RuntimeSessionState.singleParty(
                party = party,
                hour = hour,
                currentTown = currentTown,
                rngState = rngState,
            )
        return if (realizeNpcs) {
            runtime.realizeNpcPlacements(content.characters, content.towns, npcPlacementConfig)
        } else {
            runtime
        }
    }

    /** Starts a town session for a party at a given town and hour. */
    fun townSession(
        party: PartyState,
        town: TownId,
        hour: Int,
        rngState: Long? = null,
        realizeNpcs: Boolean = true,
    ): TownSession =
        TownSession.start(
            town = content.towns.single { it.id == town },
            runtime = initialRuntime(party, hour, town, rngState, realizeNpcs),
            content = content.runtimeContent(),
        )

    /** Starts character creation from a pending runtime/start state. */
    fun characterCreationSession(
        state: StateModel,
        partyId: PartyId = state.realm.activeParty,
        playerName: String = "Player",
        rngState: Long? = state.realm.rngState,
        random: RylweenRandomPort = ReferenceRylweenRandom,
    ): CharacterCreationSession =
        CharacterCreationSession.start(
            partyId = partyId,
            playerName = playerName,
            baseRealm = state.realm.copy(rngState = rngState),
            content = content.runtimeContent(),
            random = random,
        )

    /** Starts wilderness from existing live runtime state and a world-map grid. */
    fun wildernessSession(
        runtime: RuntimeSessionState,
        worldMap: WorldMapGrid,
        realizeNpcs: Boolean = true,
    ): WildernessSession {
        val realized =
            if (realizeNpcs) {
                runtime.realizeNpcPlacements(content.characters, content.towns, npcPlacementConfig)
            } else {
                runtime
            }
        return WildernessSession.start(
            content = content.runtimeContent(),
            runtime = realized,
            worldMap = worldMap.withNpcHuts(realized.npcs),
        )
    }

    /** Starts wilderness from an authored or loaded state and a world-map grid. */
    fun wildernessSession(
        state: StateModel,
        worldMap: WorldMapGrid,
        rngState: Long? = state.realm.rngState,
        realizeNpcs: Boolean = true,
    ): WildernessSession {
        val runtime = initialRuntime(state, rngState, realizeNpcs)
        return WildernessSession.start(
            content = content,
            runtime = runtime,
            worldMap = worldMap.withNpcHuts(runtime.npcs),
        )
    }

    /** Resumes wilderness, dungeon, or Ydarg mode from a full runtime snapshot. */
    fun wildernessSession(
        snapshot: RuntimeSnapshotModel,
        worldMap: WorldMapGrid,
        rngState: Long? = snapshot.state.realm.rngState,
        realizeNpcs: Boolean = true,
    ): WildernessSession {
        val runtime = initialRuntime(snapshot.state, rngState, realizeNpcs)
        val map = worldMap.withNpcHuts(runtime.npcs)
        val base =
            WildernessSession.start(
                content = content.runtimeContent(),
                runtime = runtime,
                worldMap = map,
            )
        return when (val mode = snapshot.mode) {
            null -> {
                require(snapshot.state.realm.currentMode == RuntimeMode.Wilderness) {
                    "wilderness session startup for ${snapshot.state.realm.currentMode.name} requires mode snapshot"
                }
                base
            }
            is RuntimeModeSnapshot.YdargCastle -> {
                val ydarg = mode.toYdargSession(runtime, content.runtimeContent())
                base.copy(runtime = ydarg.runtime, step = WildernessSessionStep.Ydarg(ydarg))
            }
            is RuntimeModeSnapshot.Dungeon -> {
                val runtimeContent = content.runtimeContent()
                val dungeon = mode.toDungeonSession(runtime, runtimeContent, map)
                base.copy(
                    runtime = dungeon.runtime,
                    step =
                        WildernessSessionStep.Dungeon(
                            session = dungeon,
                            postDungeon =
                                mode.postDungeon?.toMissionPostDungeonContinuation(
                                    runtime = dungeon.runtime,
                                    content = runtimeContent,
                                ),
                        ),
                )
            }
        }
    }

    /** Builds the subset of authored content required by conversation sessions. */
    fun speakContent(): SpeakRuntimeContent =
        SpeakRuntimeContent(
            speak = content.speak,
            characters = content.characters,
            dialogue = content.dialogue,
        )
}
