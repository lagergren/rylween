import java.io.File

enum class WildernessDirection { North, South, East, West }

sealed interface WildernessSessionInput {
    data class Move(val direction: WildernessDirection) : WildernessSessionInput
    object Enter : WildernessSessionInput
    object Continue : WildernessSessionInput
    object Quit : WildernessSessionInput
    data class QuitAnswer(val yes: Boolean) : WildernessSessionInput
}

enum class WildernessSessionResultStatus {
    Moved,
    Blocked,
    NoMoves,
    EnteredTown,
    EnteredHut,
    EnteredCastle,
    NothingToEnter,
    UnknownTown,
    LeftTown,
    NotInWilderness,
    QuitRequested,
    QuitCancelled,
    Finished,
}

data class WildernessSessionResult(
    val status: WildernessSessionResultStatus,
    val from: GridPoint? = null,
    val to: GridPoint? = null,
    val terrain: Char? = null,
    val town: TownId? = null,
)

sealed interface WildernessSessionStep {
    object Explore : WildernessSessionStep
    data class Notice(val text: TextBlock, val next: WildernessSessionStep = Explore) : WildernessSessionStep
    data class QuitConfirm(val prompt: TextBlock) : WildernessSessionStep
    data class Town(val session: TownSession) : WildernessSessionStep
    data class Finished(val runtime: RuntimeSessionState) : WildernessSessionStep
}

data class WorldMapGrid(
    val id: WorldMapId,
    val rows: List<String>,
) {
    init {
        require(rows.isNotEmpty()) { "world map requires at least one row" }
    }

    val height: Int get() = rows.size
    val width: Int get() = rows.maxOfOrNull { it.length } ?: 0

    fun tileAt(point: GridPoint): Char? =
        rows.getOrNull(point.y)?.getOrNull(point.x)

    fun isPassable(point: GridPoint): Boolean =
        tileAt(point)?.isImpassableTerrain() == false

    fun isTown(point: GridPoint): Boolean =
        tileAt(point) == Terrain.town.legacyMapCode

    fun isHut(point: GridPoint): Boolean =
        tileAt(point) == Terrain.hut.legacyMapCode

    fun isCastle(point: GridPoint): Boolean =
        tileAt(point) == Terrain.castle.legacyMapCode

    fun isSettlement(point: GridPoint): Boolean =
        isTown(point) || isHut(point) || isCastle(point)

    fun isNpcPlacementTerrain(point: GridPoint): Boolean =
        isPassable(point) && !isSettlement(point)

    fun passableNpcPoints(): Set<GridPoint> =
        rows.flatMapIndexed { y, row ->
            row.indices.mapNotNull { x ->
                GridPoint(x, y).takeIf(::isNpcPlacementTerrain)
            }
        }.toSet()

    fun withNpcHuts(npcs: List<NpcPlacement>): WorldMapGrid =
        npcs.fold(this) { map, placement ->
            val point = (placement.position as? WorldPosition.Wilderness)?.point
            if (point != null && map.isNpcPlacementTerrain(point)) {
                map.withTileAt(point, requireNotNull(Terrain.hut.legacyMapCode))
            } else {
                map
            }
        }

    fun viewport(center: GridPoint, radius: Int = 2): List<String> {
        require(radius >= 0) { "map viewport radius must be non-negative" }
        return (-radius..radius).map { dy ->
            buildString {
                (-radius..radius).forEach { dx ->
                    val point = GridPoint(center.x + dx, center.y + dy)
                    append(if (point == center) '@' else tileAt(point) ?: ' ')
                }
            }
        }
    }

    private fun withTileAt(point: GridPoint, tile: Char): WorldMapGrid {
        val row = rows.getOrNull(point.y) ?: return this
        if (point.x !in row.indices) return this
        val updatedRow = StringBuilder(row).apply { setCharAt(point.x, tile) }.toString()
        return copy(rows = rows.mapIndexed { y, existing -> if (y == point.y) updatedRow else existing })
    }

    companion object {
        fun legacyWilderness(rows: List<String>): WorldMapGrid =
            WorldMapGrid(
                id = Maps.wilderness,
                rows = listOf("") + rows.map { it.trimEnd('\r', '\n') },
            )

        fun loadLegacyMapDat(file: File): WorldMapGrid =
            legacyWilderness(file.readLines())
    }
}

data class WildernessSession(
    val runtime: RuntimeSessionState,
    val worldMap: WorldMapGrid,
    val towns: List<TownModel>,
    val townSystem: TownSystemModel,
    val scenes: List<SceneModel>,
    val itemRecords: List<ItemRecordModel> = ItemCatalog.all,
    val step: WildernessSessionStep = WildernessSessionStep.Explore,
    val lastResult: WildernessSessionResult? = null,
) {
    val party: PartyState get() = runtime.activeParty

    fun apply(input: WildernessSessionInput): WildernessSession =
        when (input) {
            is WildernessSessionInput.Move -> move(input.direction)
            WildernessSessionInput.Enter -> enter()
            WildernessSessionInput.Continue -> continueNotice()
            WildernessSessionInput.Quit -> requestQuit()
            is WildernessSessionInput.QuitAnswer -> answerQuit(input.yes)
        }

    fun continueNotice(): WildernessSession {
        val current = step as? WildernessSessionStep.Notice
            ?: error("wilderness session is not showing a notice")
        return copy(step = current.next)
    }

    fun replaceTownSession(session: TownSession): WildernessSession {
        require(step is WildernessSessionStep.Town) { "wilderness session is not running a town session" }
        return if (session.step is TownSessionStep.Finished) {
            val nextRuntime = session.runtime.withRealm(
                session.runtime.realm.copy(
                    currentMode = RuntimeMode.Wilderness,
                    currentTown = null,
                    currentMap = worldMap.id,
                ),
            )
            copy(
                runtime = nextRuntime,
                step = WildernessSessionStep.Explore,
                lastResult = WildernessSessionResult(WildernessSessionResultStatus.LeftTown),
            )
        } else {
            copy(runtime = session.runtime, step = WildernessSessionStep.Town(session))
        }
    }

    private fun move(direction: WildernessDirection): WildernessSession {
        val from = party.wildernessPoint()
            ?: return notice(
                WildernessPromptText.notInWilderness(),
                WildernessSessionResult(WildernessSessionResultStatus.NotInWilderness),
            )
        if (party.resources.timeLeft <= 0) {
            return notice(
                WildernessPromptText.noMoves(),
                WildernessSessionResult(WildernessSessionResultStatus.NoMoves, from = from),
            )
        }

        val to = from + direction.delta()
        if (!worldMap.isPassable(to)) {
            return copy(
                lastResult = WildernessSessionResult(
                    WildernessSessionResultStatus.Blocked,
                    from = from,
                    to = to,
                    terrain = worldMap.tileAt(to),
                ),
            )
        }

        val nextParty = party.copy(
            position = WorldPosition.Wilderness(to),
            facing = direction.facing(),
            resources = party.resources.copy(timeLeft = (party.resources.timeLeft - 1).coerceAtLeast(0)),
        )
        val nextRuntime = runtime
            .withActiveParty(nextParty)
            .withRealm(
                runtime.realm.copy(
                    currentMode = RuntimeMode.Wilderness,
                    currentTown = null,
                    currentMap = worldMap.id,
                ),
            )
        return copy(
            runtime = nextRuntime,
            step = WildernessSessionStep.Explore,
            lastResult = WildernessSessionResult(
                WildernessSessionResultStatus.Moved,
                from = from,
                to = to,
                terrain = worldMap.tileAt(to),
            ),
        )
    }

    private fun enter(): WildernessSession {
        val point = party.wildernessPoint()
            ?: return notice(
                WildernessPromptText.notInWilderness(),
                WildernessSessionResult(WildernessSessionResultStatus.NotInWilderness),
            )
        return when {
            worldMap.isTown(point) -> enterTown(point)
            worldMap.isHut(point) -> notice(
                WildernessPromptText.hutPending(),
                WildernessSessionResult(WildernessSessionResultStatus.EnteredHut, from = point),
            )
            worldMap.isCastle(point) -> notice(
                WildernessPromptText.castlePending(),
                WildernessSessionResult(WildernessSessionResultStatus.EnteredCastle, from = point),
            )
            else -> notice(
                WildernessPromptText.nothingToEnter(),
                WildernessSessionResult(WildernessSessionResultStatus.NothingToEnter, from = point),
            )
        }
    }

    private fun enterTown(point: GridPoint): WildernessSession {
        val town = towns.firstOrNull { it.position == point }
            ?: return notice(
                WildernessPromptText.unknownTown(),
                WildernessSessionResult(WildernessSessionResultStatus.UnknownTown, from = point),
            )
        val townRuntime = runtime.withRealm(
            runtime.realm.copy(
                currentMode = RuntimeMode.Town,
                currentTown = town.id,
                currentMap = worldMap.id,
            ),
        )
        return copy(
            runtime = townRuntime,
            step = WildernessSessionStep.Town(
                TownSession.start(
                    town = town,
                    townSystem = townSystem,
                    scenes = scenes,
                    runtime = townRuntime,
                    itemRecords = itemRecords,
                ),
            ),
            lastResult = WildernessSessionResult(
                WildernessSessionResultStatus.EnteredTown,
                from = point,
                town = town.id,
            ),
        )
    }

    private fun requestQuit(): WildernessSession =
        copy(
            step = WildernessSessionStep.QuitConfirm(WildernessPromptText.quitConfirm()),
            lastResult = WildernessSessionResult(WildernessSessionResultStatus.QuitRequested),
        )

    private fun answerQuit(yes: Boolean): WildernessSession {
        require(step is WildernessSessionStep.QuitConfirm) { "wilderness session is not confirming quit" }
        return if (yes) {
            finish()
        } else {
            copy(
                step = WildernessSessionStep.Explore,
                lastResult = WildernessSessionResult(WildernessSessionResultStatus.QuitCancelled),
            )
        }
    }

    private fun finish(): WildernessSession {
        val nextRuntime = runtime.withRealm(
            runtime.realm.copy(
                currentMode = RuntimeMode.Wilderness,
                currentTown = null,
                currentMap = worldMap.id,
            ),
        )
        return copy(
            runtime = nextRuntime,
            step = WildernessSessionStep.Finished(nextRuntime),
            lastResult = WildernessSessionResult(WildernessSessionResultStatus.Finished),
        )
    }

    private fun notice(text: TextBlock, result: WildernessSessionResult): WildernessSession =
        copy(step = WildernessSessionStep.Notice(text), lastResult = result)

    companion object {
        fun start(
            content: GameModel,
            runtime: RuntimeSessionState,
            worldMap: WorldMapGrid,
            itemRecords: List<ItemRecordModel> = ItemCatalog.all,
        ): WildernessSession =
            WildernessSession(
                runtime = runtime.withRealm(
                    runtime.realm.copy(
                        currentMap = worldMap.id,
                        currentMode = RuntimeMode.Wilderness,
                        currentTown = null,
                    ),
                ),
                worldMap = worldMap,
                towns = content.towns,
                townSystem = requireNotNull(content.townSystem) { "town system is not configured" },
                scenes = content.scenes,
                itemRecords = itemRecords,
            )
    }
}

object WildernessPromptText {
    fun noMoves(): TextBlock =
        inlineText("No moves left.")

    fun notInWilderness(): TextBlock =
        inlineText("The active party is not in the wilderness.")

    fun nothingToEnter(): TextBlock =
        inlineText("There is nothing to enter here.")

    fun unknownTown(): TextBlock =
        inlineText("This town has no town declaration yet.")

    fun hutPending(): TextBlock =
        inlineText("Hut entry is not ported yet.")

    fun castlePending(): TextBlock =
        inlineText("Castle entry is not ported yet.")

    fun quitConfirm(): TextBlock =
        inlineText("Do you really want", "to quit?", "")
}

fun wildernessDirectionForKey(key: Char): WildernessDirection? =
    when (key.uppercaseChar()) {
        '8' -> WildernessDirection.North
        '2' -> WildernessDirection.South
        '6' -> WildernessDirection.East
        '4' -> WildernessDirection.West
        else -> null
    }

private fun PartyState.wildernessPoint(): GridPoint? =
    (position as? WorldPosition.Wilderness)?.point

private fun WildernessDirection.delta(): GridPoint =
    when (this) {
        WildernessDirection.North -> GridPoint(0, -1)
        WildernessDirection.South -> GridPoint(0, 1)
        WildernessDirection.East -> GridPoint(1, 0)
        WildernessDirection.West -> GridPoint(-1, 0)
    }

private fun WildernessDirection.facing(): Facing =
    when (this) {
        WildernessDirection.North -> Facing.North
        WildernessDirection.South -> Facing.South
        WildernessDirection.East -> Facing.East
        WildernessDirection.West -> Facing.West
    }

private operator fun GridPoint.plus(other: GridPoint): GridPoint =
    GridPoint(x + other.x, y + other.y)

private fun Char.isImpassableTerrain(): Boolean =
    this in ImpassableTerrainTiles

private val ImpassableTerrainTiles: Set<Char> =
    listOfNotNull(
        Terrain.mountain.legacyMapCode,
        Terrain.water.legacyMapCode,
    ).toSet()
