/**
 * Headless Kotlin host facade for exercising runtime sessions without a terminal.
 *
 * This reads the same external J1 bundle as `app/TerminalMain.kt`, constructs
 * semantic sessions from `runtime/`, and reports its surface through
 * `RuntimeHostContract.kt` for host-conformance checks. In J2.5 compiled-VM
 * mode it also carries `compiled/CompiledLoader.kt` output and routes named
 * script roots through `runtime/vm/CompiledVmScriptRunner.kt`.
 */
package rylween.engine

import rylween.compiled.LoadedCompiledGame
import rylween.compiled.loadCompiledGame
import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeGame
import rylween.dsl.BuildingId
import rylween.dsl.CastleId
import rylween.dsl.DungeonEventId
import rylween.dsl.DungeonId
import rylween.dsl.DungeonSystemModel
import rylween.dsl.EncounterTier
import rylween.dsl.GameModel
import rylween.dsl.ImageAssetModel
import rylween.dsl.ImageId
import rylween.dsl.ItemId
import rylween.dsl.RuntimeContent
import rylween.dsl.RuntimeMode
import rylween.dsl.RylweenModel
import rylween.dsl.SceneId
import rylween.dsl.SpellId
import rylween.dsl.StateId
import rylween.dsl.StateModel
import rylween.dsl.StaticScreenId
import rylween.dsl.StaticScreenModel
import rylween.dsl.TownId
import rylween.dsl.WorldMapId
import rylween.dsl.runtimeContent
import rylween.runtime.RuntimeSessionState
import rylween.runtime.ScriptRootRef
import rylween.runtime.battle.BattleSession
import rylween.runtime.castle.CastleSession
import rylween.runtime.dungeon.DungeonSession
import rylween.runtime.realizeNpcPlacements
import rylween.runtime.scriptRootForDungeonEvent
import rylween.runtime.scriptRootForItemUse
import rylween.runtime.scriptRootForScene
import rylween.runtime.scriptRootForSpell
import rylween.runtime.toRuntimeSessionState
import rylween.runtime.town.ShopServiceSession
import rylween.runtime.town.SpeakSession
import rylween.runtime.town.TownSession
import rylween.runtime.town.shopServicesByBuilding
import rylween.runtime.town.speakRuntimeContent
import rylween.runtime.vm.CompiledVmHost
import rylween.runtime.vm.CompiledVmResult
import rylween.runtime.vm.CompiledVmScriptRunner
import rylween.runtime.vm.NativeServiceCatalog
import rylween.runtime.vm.RecordingCompiledVmHost
import rylween.runtime.wilderness.WildernessSession
import rylween.runtime.wilderness.WorldMapGrid
import rylween.runtime.wilderness.worldMapGrid

/**
 * Non-ANSI runtime host over the same J1 JSON artifact used by the terminal app.
 *
 * This is intentionally not a frontend. It proves the runtime can be booted
 * from the external compiled bundle and driven through semantic session APIs
 * without terminal rendering, cursor handling, or ANSI resources. In authored
 * mode, script bodies still use the existing Kotlin `Op` bridge. In compiled
 * mode, [runCompiledScript] and its root-specific helpers execute validated
 * script programs through [CompiledVmScriptRunner] while larger child systems
 * such as town, battle, dungeon, shops, and rewards stay host-owned.
 *
 * A server, native, or bitmap host can use this surface shape as the Kotlin
 * reference while replacing presentation, persistence, and child-session
 * driving with implementation-specific code.
 */
class KotlinHeadlessHost private constructor(
    /** Runtime game model materialized from the external bundle facade. */
    val game: RylweenModel,
    private val compiledGame: LoadedCompiledGame?,
    private val declaredSurface: RuntimeHostSurface,
    private val compiledNativeServices: NativeServiceCatalog,
) {
    /** Host capability declaration used by runtime host conformance checks. */
    val surface: RuntimeHostSurface
        get() = declaredSurface

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

    /**
     * Builds a root-oriented compiled VM runner for this headless host.
     *
     * This is available only on hosts created with
     * [fromExternalBundleCompiledVm]. The authored-op host surface deliberately
     * does not keep a compiled runner so tests cannot accidentally fall back to
     * VM execution while using the oracle surface.
     */
    fun compiledScriptRunner(
        vmHost: CompiledVmHost = RecordingCompiledVmHost(),
        nativeServices: NativeServiceCatalog = compiledNativeServices,
    ): CompiledVmScriptRunner =
        CompiledVmScriptRunner(
            content = requireCompiledGame().content,
            host = vmHost,
            nativeServices = nativeServices,
        )

    /**
     * Runs an arbitrary compiled script [root] from [runtime].
     *
     * Scene, dungeon-event, spell, and item-use convenience methods below all
     * delegate here. Callers that need trace parity should pass a
     * [RecordingCompiledVmHost] and inspect its recorded events.
     */
    fun runCompiledScript(
        root: ScriptRootRef,
        runtime: RuntimeSessionState,
        vmHost: CompiledVmHost = RecordingCompiledVmHost(),
        nativeServices: NativeServiceCatalog = compiledNativeServices,
        locals: Map<String, Int> = emptyMap(),
        textBindings: Map<String, String> = emptyMap(),
    ): CompiledVmResult =
        compiledScriptRunner(vmHost, nativeServices)
            .run(root, runtime, locals, textBindings)

    /** Runs the compiled script root for [scene] from [runtime]. */
    fun runCompiledScene(
        scene: SceneId,
        runtime: RuntimeSessionState,
        vmHost: CompiledVmHost = RecordingCompiledVmHost(),
    ): CompiledVmResult =
        runCompiledScript(
            root = runtimeContent.scriptRootForScene(scene),
            runtime = runtime,
            vmHost = vmHost,
        )

    /** Runs the compiled dungeon-event script root for [event] from [runtime]. */
    fun runCompiledDungeonEvent(
        event: DungeonEventId,
        runtime: RuntimeSessionState,
        vmHost: CompiledVmHost = RecordingCompiledVmHost(),
    ): CompiledVmResult =
        runCompiledScript(
            root = runtimeContent.scriptRootForDungeonEvent(event),
            runtime = runtime,
            vmHost = vmHost,
        )

    /** Runs the compiled spell script root for [spell] from [runtime]. */
    fun runCompiledSpell(
        spell: SpellId,
        runtime: RuntimeSessionState,
        vmHost: CompiledVmHost = RecordingCompiledVmHost(),
    ): CompiledVmResult =
        runCompiledScript(
            root = runtimeContent.scriptRootForSpell(spell),
            runtime = runtime,
            vmHost = vmHost,
        )

    /** Runs the compiled item-use script root for [item] from [runtime]. */
    fun runCompiledItemUse(
        item: ItemId,
        runtime: RuntimeSessionState,
        vmHost: CompiledVmHost = RecordingCompiledVmHost(),
    ): CompiledVmResult =
        runCompiledScript(
            root = runtimeContent.scriptRootForItemUse(item),
            runtime = runtime,
            vmHost = vmHost,
        )

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

    /** Starts a grid-castle session from existing live runtime state. */
    fun castleSession(
        runtime: RuntimeSessionState,
        castle: CastleId? = null,
    ): CastleSession {
        val content = runtimeContent
        val castleContent =
            if (castle == null) {
                content.castles.singleOrNull()
            } else {
                content.castles.singleOrNull { model -> model.id == castle }
            }
        if (castleContent == null) {
            error("Castle content is not configured")
        }
        return CastleSession.start(runtime, content, castleContent)
    }

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
        private fun fromGame(
            game: RylweenModel,
            compiledGame: LoadedCompiledGame? = null,
            surface: RuntimeHostSurface = RylweenRuntimeHostContract.kotlinHeadless,
            nativeServices: NativeServiceCatalog = NativeServiceCatalog.default,
        ): KotlinHeadlessHost =
            KotlinHeadlessHost(
                game = game,
                compiledGame = compiledGame,
                declaredSurface = surface,
                compiledNativeServices = nativeServices,
            )

        /** Creates a headless host by reading an external J1 bundle artifact. */
        fun fromExternalBundle(bytes: ByteArray): KotlinHeadlessHost = fromGame(bytes.readExternalRuntimeGame())

        /**
         * Creates a headless host that runs script roots through [CompiledVmScriptRunner].
         *
         * The authored-op [fromExternalBundle] host remains the comparison
         * oracle. This factory validates and stores the compiled tables from the
         * same J1 JSON artifact, then advertises
         * [RuntimeScriptExecutionStrategy.CompiledVm] through [surface].
         */
        fun fromExternalBundleCompiledVm(
            bytes: ByteArray,
            nativeServices: NativeServiceCatalog = NativeServiceCatalog.default,
        ): KotlinHeadlessHost =
            fromGame(
                game = bytes.readExternalRuntimeGame(),
                compiledGame = bytes.readExternalCompiledGameBundle().loadCompiledGame(nativeServices),
                surface = RylweenRuntimeHostContract.kotlinHeadlessCompiledVm,
                nativeServices = nativeServices,
            )
    }

    private fun requireCompiledGame(): LoadedCompiledGame =
        checkNotNull(compiledGame) {
            "host ${surface.id} does not carry compiled VM content; use fromExternalBundleCompiledVm"
        }
}
