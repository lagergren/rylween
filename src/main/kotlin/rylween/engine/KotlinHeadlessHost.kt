/**
 * Headless Kotlin host facade for exercising runtime sessions without a terminal.
 *
 * This reads the same external J1 bundle as `app/TerminalMain.kt`, constructs
 * semantic sessions from `runtime/`, and reports its surface through
 * `RuntimeHostContract.kt` for host-conformance checks.
 */
package rylween.engine

import rylween.compiled.readExternalRuntimeGame
import rylween.dsl.BuildingId
import rylween.dsl.DungeonId
import rylween.dsl.DungeonSystemModel
import rylween.dsl.EncounterTier
import rylween.dsl.GameModel
import rylween.dsl.ImageAssetModel
import rylween.dsl.ImageId
import rylween.dsl.RuntimeContent
import rylween.dsl.RuntimeMode
import rylween.dsl.RylweenModel
import rylween.dsl.StateId
import rylween.dsl.StateModel
import rylween.dsl.StaticScreenId
import rylween.dsl.StaticScreenModel
import rylween.dsl.TownId
import rylween.dsl.WorldMapId
import rylween.dsl.building
import rylween.dsl.dungeon
import rylween.dsl.image
import rylween.dsl.runtimeContent
import rylween.dsl.stateId
import rylween.dsl.town
import rylween.dsl.worldMap
import rylween.runtime.RuntimeSessionState
import rylween.runtime.battle.BattleSession
import rylween.runtime.dungeon.DungeonSession
import rylween.runtime.realizeNpcPlacements
import rylween.runtime.toRuntimeSessionState
import rylween.runtime.town.ShopServiceSession
import rylween.runtime.town.SpeakSession
import rylween.runtime.town.TownSession
import rylween.runtime.town.shopServicesByBuilding
import rylween.runtime.town.speakRuntimeContent
import rylween.runtime.wilderness.WildernessSession
import rylween.runtime.wilderness.WorldMapGrid
import rylween.runtime.wilderness.worldMapGrid
import rylween.runtime.ydarg.YdargSession

/**
 * Non-ANSI runtime host over the same J1 JSON artifact used by the terminal app.
 *
 * This is intentionally not a frontend. It proves the runtime can be booted
 * from the external compiled bundle and driven through semantic session APIs
 * without terminal rendering, cursor handling, or ANSI resources. A server,
 * native, or bitmap host can use this surface shape as the Kotlin reference
 * while still replacing presentation and persistence.
 */
class KotlinHeadlessHost private constructor(
    /** Runtime game model materialized from the external bundle facade. */
    val game: RylweenModel,
) {
    /** Host capability declaration used by runtime host conformance checks. */
    val surface: RuntimeHostSurface
        get() = RylweenRuntimeHostContract.kotlinHeadless

    /** Authored runtime content carried by [game]. */
    val content: GameModel
        get() = game.content

    private val runtimeContent: RuntimeContent
        get() = content.runtimeContent()

    /** Resolves a named authored or saved state. */
    fun state(id: StateId): StateModel = game.states.single { state -> state.id == id }

    /** Builds the world-map grid for [id]. */
    fun worldMap(id: WorldMapId): WorldMapGrid = checkNotNull(content.worldMapGrid(id)) { "world map ${id.slug} is not declared" }

    /** Resolves an authored static screen by id. */
    fun staticScreen(id: StaticScreenId): StaticScreenModel? = content.visuals.staticScreens.singleOrNull { screen -> screen.id == id }

    /** Resolves an authored image asset by id. */
    fun image(id: ImageId): ImageAssetModel? = content.visuals.images.singleOrNull { image -> image.id == id }

    /** Converts a state id into live runtime state for headless session creation. */
    fun runtime(
        stateId: StateId,
        rngState: Long? = null,
        realizeNpcs: Boolean = false,
    ): RuntimeSessionState {
        val state = state(stateId)
        val runtime = state.toRuntimeSessionState(rngState ?: state.realm.rngState)
        return if (realizeNpcs) {
            runtime.realizeNpcPlacements(content.characters, content.towns)
        } else {
            runtime
        }
    }

    /** Starts a wilderness session from a state and map id. */
    fun wildernessSession(
        stateId: StateId,
        mapId: WorldMapId,
        rngState: Long? = null,
        realizeNpcs: Boolean = false,
    ): WildernessSession =
        wildernessSession(
            runtime = runtime(stateId, rngState, realizeNpcs),
            worldMap = worldMap(mapId),
        )

    private fun wildernessSession(
        runtime: RuntimeSessionState,
        worldMap: WorldMapGrid,
    ): WildernessSession =
        WildernessSession.start(
            content = runtimeContent,
            runtime = runtime,
            worldMap = worldMap.withNpcHuts(runtime.npcs),
        )

    /** Starts a town session from existing live runtime state. */
    fun townSession(
        runtime: RuntimeSessionState,
        town: TownId,
    ): TownSession {
        val townModel = content.towns.single { model -> model.id == town }
        return TownSession.start(
            town = townModel,
            runtime =
                runtime.withRealm(
                    runtime.realm.copy(
                        currentMode = RuntimeMode.Town,
                        currentTown = town,
                    ),
                ),
            content = runtimeContent,
        )
    }

    /** Starts a dungeon session from existing live runtime state. */
    fun dungeonSession(
        runtime: RuntimeSessionState,
        worldMap: WorldMapGrid,
        dungeon: DungeonId,
        levels: Int,
        dungeons: DungeonSystemModel = content.dungeons,
    ): DungeonSession =
        DungeonSession.start(
            runtime = runtime,
            worldMap = worldMap,
            dungeon = dungeon,
            levels = levels,
            content = runtimeContent,
            dungeons = dungeons,
        )

    /** Starts a Ydarg castle session from existing live runtime state. */
    fun castleSession(runtime: RuntimeSessionState): YdargSession = YdargSession.start(runtime, runtimeContent)

    /** Starts a battle session from existing live runtime state. */
    fun battleSession(
        runtime: RuntimeSessionState,
        tier: EncounterTier,
        mandatory: Boolean = false,
    ): BattleSession =
        BattleSession.start(
            runtime = runtime,
            tier = tier,
            content = runtimeContent,
            mandatory = mandatory,
        )

    /** Starts a shop session for the service bound to [building]. */
    fun shopSession(
        runtime: RuntimeSessionState,
        building: BuildingId,
    ): ShopServiceSession {
        val shop =
            checkNotNull(content.scenes.shopServicesByBuilding()[building]) {
                "shop for building ${building.slug} is not declared"
            }
        return ShopServiceSession.start(shop, runtime.activeParty, runtimeContent)
    }

    /** Starts a conversation session for the NPC/service bound to [building]. */
    fun speakSession(
        runtime: RuntimeSessionState,
        building: BuildingId,
    ): SpeakSession = SpeakSession.start(runtimeContent.speakRuntimeContent(), runtime, building)

    companion object {
        private fun fromGame(game: RylweenModel): KotlinHeadlessHost = KotlinHeadlessHost(game)

        /** Creates a headless host by reading an external J1 bundle artifact. */
        fun fromExternalBundle(bytes: ByteArray): KotlinHeadlessHost = fromGame(bytes.readExternalRuntimeGame())
    }
}
