// Boot facade for compiled game content into runtime sessions.

package rylween.engine

import rylween.dsl.*
import rylween.runtime.*

class RylweenKotlinEngine(
    private val content: GameModel,
    private val npcPlacementConfig: NpcPlacementRealizerConfig = NpcPlacementRealizerConfig(),
) {
    private fun initialRuntime(
        state: StateModel,
        rngState: Long? = state.realm.rngState,
        realizeNpcs: Boolean = true,
    ): RuntimeSessionState {
        val runtime = RuntimeSessionState(
            realm = state.realm.copy(rngState = rngState),
            parties = state.parties,
            npcs = state.npcs,
            locations = state.locations,
        )
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

    fun speakContent(): SpeakRuntimeContent =
        SpeakRuntimeContent(
            speak = content.speak,
            characters = content.characters,
            dialogue = content.dialogue,
        )
}
