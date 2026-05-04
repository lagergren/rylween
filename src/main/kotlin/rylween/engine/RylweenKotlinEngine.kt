// Boot facade for compiled game content into runtime sessions.

package rylween.engine

import rylween.dsl.*
import rylween.runtime.*
import rylween.runtime.ydarg.toYdargSession

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
        val runtime = RuntimeSessionState.singleParty(
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

    fun wildernessSession(
        runtime: RuntimeSessionState,
        worldMap: WorldMapGrid,
        realizeNpcs: Boolean = true,
    ): WildernessSession {
        val realized = if (realizeNpcs) {
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

    fun wildernessSession(
        snapshot: RuntimeSnapshotModel,
        worldMap: WorldMapGrid,
        rngState: Long? = snapshot.state.realm.rngState,
        realizeNpcs: Boolean = true,
    ): WildernessSession {
        val runtime = initialRuntime(snapshot.state, rngState, realizeNpcs)
        val map = worldMap.withNpcHuts(runtime.npcs)
        val base = WildernessSession.start(
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
                    step = WildernessSessionStep.Dungeon(
                        session = dungeon,
                        postDungeon = mode.postDungeon?.toMissionPostDungeonContinuation(
                            runtime = dungeon.runtime,
                            content = runtimeContent,
                        ),
                    ),
                )
            }
        }
    }

    fun speakContent(): SpeakRuntimeContent =
        SpeakRuntimeContent(
            speak = content.speak,
            characters = content.characters,
            dialogue = content.dialogue,
        )
}
