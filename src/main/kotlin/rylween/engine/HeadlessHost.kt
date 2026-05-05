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
    val game: RylweenModel,
) {
    val surface: RuntimeHostSurface
        get() = RylweenRuntimeHostContract.kotlinHeadless

    val content: GameModel
        get() = game.content

    private val runtimeContent: RuntimeContent
        get() = content.runtimeContent()

    fun state(id: StateId): StateModel = game.states.single { state -> state.id == id }

    fun worldMap(id: WorldMapId): WorldMapGrid = checkNotNull(content.worldMapGrid(id)) { "world map ${id.slug} is not declared" }

    fun staticScreen(id: StaticScreenId): StaticScreenModel? = content.visuals.staticScreens.singleOrNull { screen -> screen.id == id }

    fun image(id: ImageId): ImageAssetModel? = content.visuals.images.singleOrNull { image -> image.id == id }

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

    fun castleSession(runtime: RuntimeSessionState): YdargSession = YdargSession.start(runtime, runtimeContent)

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

    fun speakSession(
        runtime: RuntimeSessionState,
        building: BuildingId,
    ): SpeakSession = SpeakSession.start(runtimeContent.speakRuntimeContent(), runtime, building)

    companion object {
        private fun fromGame(game: RylweenModel): KotlinHeadlessHost = KotlinHeadlessHost(game)

        fun fromExternalBundle(bytes: ByteArray): KotlinHeadlessHost = fromGame(bytes.readExternalRuntimeGame())
    }
}
