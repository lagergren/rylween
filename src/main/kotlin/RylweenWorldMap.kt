import java.io.File

enum class WildernessDirection { North, South, East, West }

sealed interface WildernessSessionInput {
    data class Move(val direction: WildernessDirection) : WildernessSessionInput
    object Enter : WildernessSessionInput
    object Pass : WildernessSessionInput
    object Continue : WildernessSessionInput
    object Quit : WildernessSessionInput
    data class QuitAnswer(val yes: Boolean) : WildernessSessionInput
    data class OutOfMovesAnswer(val save: Boolean) : WildernessSessionInput
}

enum class WildernessSessionResultStatus {
    Passed,
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
    OutOfMovesSaveAndQuit,
    OutOfMovesQuitNoSave,
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
    object TurnStart : WildernessSessionStep
    object Explore : WildernessSessionStep
    data class Messages(val blocks: List<TextBlock>, val next: WildernessSessionStep) : WildernessSessionStep
    data class OutOfMoves(val prompt: TextBlock) : WildernessSessionStep
    data class Notice(val text: TextBlock, val next: WildernessSessionStep = Explore) : WildernessSessionStep
    data class QuitConfirm(val prompt: TextBlock) : WildernessSessionStep
    data class Town(val session: TownSession) : WildernessSessionStep
    data class Finished(val runtime: RuntimeSessionState) : WildernessSessionStep
}

enum class WildernessFatigueMatch { Equal, LessThan }

data class WildernessFatigueRule(
    val threshold: Int,
    val text: TextBlock,
    val match: WildernessFatigueMatch = WildernessFatigueMatch.Equal,
    val drain: Boolean = false,
)

data class WildernessUpkeepRules(
    val foodCostLeaderDiv: Int = 4,
    val foodTickEvery: Int = 5,
    val starveDamage: Int = 2,
    val manaRegenChance: Int = 50,
    val fatigue: List<WildernessFatigueRule> = StandardRylweenFatigue,
) {
    init {
        require(foodTickEvery >= 0) { "food tick interval must be non-negative" }
        require(foodCostLeaderDiv >= 0) { "food leader divisor must be non-negative" }
        require(starveDamage >= 0) { "starve damage must be non-negative" }
        require(manaRegenChance in 0..100) { "mana regen chance must be 0..100" }
    }
}

private data class WildernessUpkeepResult(
    val runtime: RuntimeSessionState,
    val messages: List<TextBlock>,
    val allDead: Boolean,
)

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
    val upkeepRules: WildernessUpkeepRules = WildernessUpkeepRules(),
    val step: WildernessSessionStep = WildernessSessionStep.TurnStart,
    val lastResult: WildernessSessionResult? = null,
) {
    val party: PartyState get() = runtime.activeParty

    fun apply(input: WildernessSessionInput): WildernessSession =
        when (input) {
            is WildernessSessionInput.Move -> move(input.direction)
            WildernessSessionInput.Enter -> enter()
            WildernessSessionInput.Pass -> pass()
            WildernessSessionInput.Continue -> continueNotice()
            WildernessSessionInput.Quit -> requestQuit()
            is WildernessSessionInput.QuitAnswer -> answerQuit(input.yes)
            is WildernessSessionInput.OutOfMovesAnswer -> answerOutOfMoves(input.save)
        }

    fun beginTurn(): WildernessSession {
        require(step is WildernessSessionStep.TurnStart) { "wilderness session is not starting a turn" }
        val upkeep = runtime.applyWildernessUpkeep(upkeepRules)
        val next = when {
            upkeep.allDead -> WildernessSessionStep.Finished(
                upkeep.runtime.withRealm(upkeep.runtime.realm.copy(currentMode = RuntimeMode.GameOver)),
            )
            upkeep.runtime.activeParty.resources.timeLeft <= 0 ->
                WildernessSessionStep.OutOfMoves(WildernessPromptText.outOfMoves())
            else -> WildernessSessionStep.Explore
        }
        return copy(
            runtime = upkeep.runtime,
            step = if (upkeep.messages.isEmpty()) next else WildernessSessionStep.Messages(upkeep.messages, next),
        )
    }

    fun continueNotice(): WildernessSession {
        val current = step as? WildernessSessionStep.Notice
            ?: error("wilderness session is not showing a notice")
        return copy(step = current.next)
    }

    fun continueMessages(): WildernessSession {
        val current = step as? WildernessSessionStep.Messages
            ?: error("wilderness session is not showing messages")
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
                step = WildernessSessionStep.TurnStart,
                lastResult = WildernessSessionResult(WildernessSessionResultStatus.LeftTown),
            )
        } else {
            copy(runtime = session.runtime, step = WildernessSessionStep.Town(session))
        }
    }

    private fun pass(): WildernessSession =
        notice(
            WildernessPromptText.pass(),
            WildernessSessionResult(WildernessSessionResultStatus.Passed),
        )

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
            step = WildernessSessionStep.TurnStart,
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
                step = WildernessSessionStep.TurnStart,
                lastResult = WildernessSessionResult(WildernessSessionResultStatus.QuitCancelled),
            )
        }
    }

    private fun answerOutOfMoves(save: Boolean): WildernessSession {
        require(step is WildernessSessionStep.OutOfMoves) { "wilderness session is not waiting for out-of-moves input" }
        return finish(
            if (save) {
                WildernessSessionResultStatus.OutOfMovesSaveAndQuit
            } else {
                WildernessSessionResultStatus.OutOfMovesQuitNoSave
            },
        )
    }

    private fun finish(status: WildernessSessionResultStatus = WildernessSessionResultStatus.Finished): WildernessSession {
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
            lastResult = WildernessSessionResult(status),
        )
    }

    private fun notice(text: TextBlock, result: WildernessSessionResult): WildernessSession =
        copy(step = WildernessSessionStep.Notice(text, WildernessSessionStep.TurnStart), lastResult = result)

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
    fun pass(): TextBlock =
        inlineText("Time passes...")

    fun noMoves(): TextBlock =
        inlineText("No moves left.")

    fun outOfMoves(): TextBlock =
        inlineText("You are out of moves", "for today.", "", "(S)ave and Quit", "(Q)uit (no save)")

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

private fun RuntimeSessionState.applyWildernessUpkeep(rules: WildernessUpkeepRules): WildernessUpkeepResult {
    val advancedHour = withRealm(realm.copy(hour = (realm.hour + 1).positiveMod(24)))
    val manaResult = advancedHour.regenerateMana(rules)
    val foodResult = manaResult.runtime.applyFoodAndFatigue(rules)
    return foodResult.copy(messages = manaResult.messages + foodResult.messages)
}

private fun RuntimeSessionState.regenerateMana(rules: WildernessUpkeepRules): WildernessUpkeepResult {
    var runtime = this
    val members = activeParty.members.map { member ->
        if (member.pcClass !in setOf(PcClass.Mage, PcClass.Priest) || member.mana.current >= member.wisdom) {
            member
        } else {
            val draw = runtime.drawRandom(0, 99)
            runtime = draw.runtime
            if (draw.value < rules.manaRegenChance) {
                member.copy(mana = member.mana.copy(current = (member.mana.current + 1).coerceAtMost(member.wisdom)))
            } else {
                member
            }
        }
    }
    return WildernessUpkeepResult(
        runtime = runtime.withActiveParty(runtime.activeParty.copy(members = members)),
        messages = emptyList(),
        allDead = false,
    )
}

private fun RuntimeSessionState.applyFoodAndFatigue(rules: WildernessUpkeepRules): WildernessUpkeepResult {
    val party = activeParty
    val foodAfterTick = if (rules.foodTickEvery > 0 && party.resources.timeLeft % rules.foodTickEvery == 0) {
        (party.resources.food - party.foodTickCost(rules)).coerceAtLeast(0)
    } else {
        party.resources.food
    }
    val starvation = party.applyStarvation(foodAfterTick, rules.starveDamage)
    val restedHours = party.resources.restedHours - 1
    val fatigue = starvation.party.applyFatigue(restedHours, rules.fatigue)
    val nextParty = fatigue.party.copy(
        resources = fatigue.party.resources.copy(
            food = foodAfterTick,
            restedHours = restedHours,
        ),
    )
    return WildernessUpkeepResult(
        runtime = withActiveParty(nextParty),
        messages = starvation.messages + fatigue.messages,
        allDead = nextParty.members.isNotEmpty() && nextParty.members.all { it.dead },
    )
}

private data class PartyUpkeepMutation(
    val party: PartyState,
    val messages: List<TextBlock>,
)

private fun PartyState.foodTickCost(rules: WildernessUpkeepRules): Int {
    val leaderLevelCost = if (rules.foodCostLeaderDiv > 0) {
        (members.firstOrNull()?.level ?: 0) / rules.foodCostLeaderDiv
    } else {
        0
    }
    return partySizeForFood() + leaderLevelCost
}

private fun PartyState.partySizeForFood(): Int =
    counters.memberCount ?: members.size

private fun PartyState.applyStarvation(foodAfterTick: Int, damage: Int): PartyUpkeepMutation {
    if (foodAfterTick > 0 || damage == 0) return PartyUpkeepMutation(this, emptyList())
    val messages = mutableListOf<TextBlock>()
    val nextMembers = members.map { member ->
        if (member.dead) {
            member
        } else {
            val nextHp = member.hitPoints.current - damage
            if (nextHp < 1) {
                messages += inlineText("${member.name} starved", "to death!!!")
                member.copy(hitPoints = member.hitPoints.copy(current = 0), dead = true)
            } else {
                member.copy(hitPoints = member.hitPoints.copy(current = nextHp))
            }
        }
    }
    return PartyUpkeepMutation(copy(members = nextMembers), messages)
}

private fun PartyState.applyFatigue(
    restedHours: Int,
    rules: List<WildernessFatigueRule>,
): PartyUpkeepMutation {
    val rule = rules.firstOrNull { it.matches(restedHours) }
        ?: return PartyUpkeepMutation(this, emptyList())
    val nextParty = if (rule.drain) {
        copy(
            members = members.map { member ->
                if (member.hitPoints.current > 1) {
                    member.copy(hitPoints = member.hitPoints.copy(current = member.hitPoints.current - 1))
                } else {
                    member
                }
            },
        )
    } else {
        this
    }
    return PartyUpkeepMutation(nextParty, listOf(rule.text))
}

private fun WildernessFatigueRule.matches(restedHours: Int): Boolean =
    when (match) {
        WildernessFatigueMatch.Equal -> restedHours == threshold
        WildernessFatigueMatch.LessThan -> restedHours < threshold
    }

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

private fun Int.positiveMod(divisor: Int): Int =
    ((this % divisor) + divisor) % divisor

private fun Char.isImpassableTerrain(): Boolean =
    this in ImpassableTerrainTiles

private val ImpassableTerrainTiles: Set<Char> =
    listOfNotNull(
        Terrain.mountain.legacyMapCode,
        Terrain.water.legacyMapCode,
    ).toSet()

private val StandardRylweenFatigue: List<WildernessFatigueRule> =
    listOf(
        WildernessFatigueRule(
            threshold = 0,
            text = inlineText("You begin to feel tired"),
        ),
        WildernessFatigueRule(
            threshold = -4,
            text = inlineText("You are exhausted!!!"),
            drain = true,
        ),
        WildernessFatigueRule(
            threshold = -8,
            text = inlineText("You begin to feel weak", "from lack of sleep.", ""),
            drain = true,
        ),
        WildernessFatigueRule(
            threshold = -8,
            match = WildernessFatigueMatch.LessThan,
            text = inlineText("You faint from lack", "of well needed sleep."),
            drain = true,
        ),
    )
