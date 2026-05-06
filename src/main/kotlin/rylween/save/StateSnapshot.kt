package rylween.save

import rylween.dsl.BuildingId
import rylween.dsl.DslDefaultItems
import rylween.dsl.DslDefaultMaps
import rylween.dsl.DungeonEventId
import rylween.dsl.DungeonId
import rylween.dsl.EquipmentState
import rylween.dsl.Facing
import rylween.dsl.FlagState
import rylween.dsl.GameMilestoneId
import rylween.dsl.Gender
import rylween.dsl.GridPoint
import rylween.dsl.InventorySlotState
import rylween.dsl.ItemId
import rylween.dsl.JournalEntryState
import rylween.dsl.LocationId
import rylween.dsl.LocationPlacement
import rylween.dsl.MissionId
import rylween.dsl.NpcId
import rylween.dsl.NpcPlacement
import rylween.dsl.NpcVisitState
import rylween.dsl.PartyAttackRecord
import rylween.dsl.PartyCounters
import rylween.dsl.PartyId
import rylween.dsl.PartyMemberState
import rylween.dsl.PartyResources
import rylween.dsl.PartyState
import rylween.dsl.PcClass
import rylween.dsl.Race
import rylween.dsl.RealmState
import rylween.dsl.RuntimeMode
import rylween.dsl.SceneId
import rylween.dsl.StateId
import rylween.dsl.StateKind
import rylween.dsl.StateModel
import rylween.dsl.TerrainId
import rylween.dsl.TextId
import rylween.dsl.TownId
import rylween.dsl.Wilderness
import rylween.dsl.WorldMapId
import rylween.dsl.WorldPosition
import rylween.dsl.YdargRoomEvent
import rylween.dsl.building
import rylween.dsl.dungeon
import rylween.dsl.dungeonEvent
import rylween.dsl.item
import rylween.dsl.line
import rylween.dsl.location
import rylween.dsl.milestone
import rylween.dsl.missionId
import rylween.dsl.npc
import rylween.dsl.of
import rylween.dsl.partyId
import rylween.dsl.render
import rylween.dsl.runtimeFlagState
import rylween.dsl.sceneId
import rylween.dsl.stateId
import rylween.dsl.terrain
import rylween.dsl.text
import rylween.dsl.town
import rylween.dsl.worldMap
import rylween.dsl.xy
import rylween.runtime.DungeonSavedStep
import rylween.runtime.DungeonSessionSnapshot
import rylween.runtime.MissionPostDungeonSnapshot
import rylween.runtime.RuntimeModeSnapshot
import rylween.runtime.RuntimeSnapshotMetadata
import rylween.runtime.RuntimeSnapshotModel
import rylween.runtime.ScriptBlockRef
import rylween.runtime.ScriptBranch
import rylween.runtime.ScriptContinuationSnapshot
import rylween.runtime.ScriptFrame
import rylween.runtime.ScriptRootKind
import rylween.runtime.ScriptRootRef
import rylween.runtime.ScriptSegment
import rylween.runtime.YdargSavedStep
import rylween.runtime.YdargSessionSnapshot

fun renderRuntimeStateSnapshot(state: StateModel): String {
    check(state.kind == StateKind.RuntimeSnapshot) {
        "only runtime snapshots can be emitted as exact save DSL"
    }
    return renderRuntimeStateSnapshot(RuntimeSnapshotModel(state))
}

fun renderRuntimeStateSnapshot(snapshot: RuntimeSnapshotModel): String {
    check(snapshot.state.kind == StateKind.RuntimeSnapshot) {
        "only runtime snapshots can be emitted as exact save DSL"
    }
    return RuntimeStateSnapshotWriter().render(snapshot)
}

fun parseRuntimeStateSnapshot(source: String): StateModel = parseRuntimeSnapshot(source).state

fun parseRuntimeSnapshot(source: String): RuntimeSnapshotModel = RuntimeStateSnapshotParser(source).parseSingle()

private class RuntimeStateSnapshotParser(
    source: String,
) {
    private val lexer = RuntimeSnapshotLexer(source)
    private var token: SnapshotToken = lexer.next()

    fun parseSingle(): RuntimeSnapshotModel {
        val states = parseAll()
        check(states.size == 1) { "expected exactly one runtimeState block, found ${states.size}" }
        return states.single()
    }

    fun parseAll(): List<RuntimeSnapshotModel> =
        buildList {
            while (token !is SnapshotToken.End) {
                add(parseState())
            }
        }

    private fun parseState(): RuntimeSnapshotModel {
        expectIdent("runtimeState")
        expectSymbol('(')
        val id = parseStateId()
        expectSymbol(')')
        expectSymbol('{')
        val metadata = if (matchIdent("metadata")) parseMetadataBody() else null
        val realm = parseRealm()
        val locations = parseLocations()
        val npcs = parseNpcs()
        val parties = mutableListOf<PartyState>()
        var mode: RuntimeModeSnapshot? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "party" -> parties += parsePartyAfterKeyword()
                "sessions" -> mode = parseSessionsBody()
                else -> unexpected("runtimeState field $field")
            }
        }
        return RuntimeSnapshotModel(
            state = StateModel(id, StateKind.RuntimeSnapshot, realm, locations, npcs, parties),
            mode = mode,
            metadata = metadata,
        )
    }

    private fun parseMetadataBody(): RuntimeSnapshotMetadata {
        expectSymbol('{')
        var formatVersion = RUNTIME_SNAPSHOT_FORMAT_VERSION
        var contentHash: String? = null
        var universeId: String? = null
        var universeVersion: String? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "formatVersion" -> formatVersion = parseAssignedInt()
                "universeId" -> {
                    expectSymbol('=')
                    universeId = parseNullable(::parseString)
                }
                "universeVersion" -> {
                    expectSymbol('=')
                    universeVersion = parseNullable(::parseString)
                }
                "contentHash" -> {
                    expectSymbol('=')
                    contentHash = parseNullable(::parseString)
                }
                else -> unexpected("metadata field $field")
            }
        }
        return RuntimeSnapshotMetadata(
            formatVersion = formatVersion,
            contentHash = contentHash,
            universeId = universeId,
            universeVersion = universeVersion,
        )
    }

    private fun parseRealm(): RealmState {
        expectIdent("realm")
        expectSymbol('{')
        var day = 0
        var hour = 0
        var minute = 0
        var rngState: Long? = null
        var activeParty: PartyId? = null
        var currentMap = DslDefaultMaps.wilderness
        var currentMode = RuntimeMode.Wilderness
        var currentTown: TownId? = null
        var banished = false
        var lastPlayedDay = 0
        var partyMemberMax = 4
        var lastTerrain: TerrainId? = null
        while (!matchSymbol('}')) {
            when (val name = expectIdentifier()) {
                "day" -> day = parseAssignedInt()
                "hour" -> hour = parseAssignedInt()
                "minute" -> minute = parseAssignedInt()
                "rngState" -> rngState = parseAssignedLong()
                "activeParty" -> {
                    expectSymbol('=')
                    activeParty = parsePartyId()
                }
                "currentMap" -> {
                    expectSymbol('=')
                    currentMap = parseWorldMapId()
                }
                "currentMode" -> {
                    expectSymbol('=')
                    currentMode = parseEnum("RuntimeMode")
                }
                "currentTown" -> {
                    expectSymbol('=')
                    currentTown = parseNullable(::parseTownId)
                }
                "banished" -> banished = parseAssignedBoolean()
                "lastPlayedDay" -> lastPlayedDay = parseAssignedInt()
                "partyMemberMax" -> partyMemberMax = parseAssignedInt()
                "lastTerrain" -> {
                    expectSymbol('=')
                    lastTerrain = parseTerrainId()
                }
                else -> unexpected("realm field $name")
            }
        }
        return RealmState(
            day = day,
            hour = hour,
            minute = minute,
            rngState = rngState,
            activeParty = checkNotNull(activeParty) { "runtime snapshot realm requires activeParty" },
            currentMap = currentMap,
            currentMode = currentMode,
            currentTown = currentTown,
            banished = banished,
            lastPlayedDay = lastPlayedDay,
            partyMemberMax = partyMemberMax,
            lastTerrain = lastTerrain,
        )
    }

    private fun parseLocations(): List<LocationPlacement> {
        expectIdent("locations")
        expectSymbol('{')
        return buildList {
            while (!matchSymbol('}')) {
                val location = parseLocationId()
                expectIdent("at")
                add(LocationPlacement(location, parsePoint()))
            }
        }
    }

    private fun parseNpcs(): List<NpcPlacement> {
        expectIdent("npcs")
        expectSymbol('{')
        return buildList {
            while (!matchSymbol('}')) {
                val npc = parseNpcId()
                expectIdent("at")
                add(NpcPlacement(npc, parsePosition()))
            }
        }
    }

    private fun parseSessionsBody(): RuntimeModeSnapshot? {
        expectSymbol('{')
        var mode: RuntimeModeSnapshot? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "ydarg" -> {
                    check(mode == null) { "runtime snapshot has multiple active session blocks" }
                    mode = RuntimeModeSnapshot.YdargCastle(parseYdargSessionBody())
                }
                "dungeon" -> {
                    check(mode == null) { "runtime snapshot has multiple active session blocks" }
                    mode = parseDungeonSessionBody()
                }
                else -> unexpected("sessions field $field")
            }
        }
        return mode
    }

    private fun parseYdargSessionBody(): YdargSessionSnapshot {
        expectSymbol('{')
        var floor = 1
        var point = GridPoint(1, 1)
        var step = YdargSavedStep.Navigate
        var treasureItem: ItemId? = null
        var rooms = emptyMap<GridPoint, YdargRoomEvent>()
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "floor" -> floor = parseAssignedInt()
                "point" -> {
                    expectSymbol('=')
                    point = parsePoint()
                }
                "step" -> {
                    expectSymbol('=')
                    step = parseEnum("YdargSavedStep")
                }
                "treasureItem" -> {
                    expectSymbol('=')
                    treasureItem = parseNullable(::parseItemId)
                }
                "rooms" -> rooms = parseYdargRoomsBody()
                else -> unexpected("Ydarg session field $field")
            }
        }
        return YdargSessionSnapshot(
            floor = floor,
            point = point,
            rooms = rooms,
            step = step,
            treasureItem = treasureItem,
        )
    }

    private fun parseYdargRoomsBody(): Map<GridPoint, YdargRoomEvent> {
        expectSymbol('{')
        return buildMap {
            while (!matchSymbol('}')) {
                expectIdent("room")
                expectSymbol('(')
                val point = parsePoint()
                expectSymbol(',')
                val event = parseEnum<YdargRoomEvent>("YdargRoomEvent")
                expectSymbol(')')
                put(point, event)
            }
        }
    }

    private fun parseDungeonSessionBody(): RuntimeModeSnapshot.Dungeon {
        expectSymbol('{')
        var dungeon: DungeonId? = null
        var maxDepth = 1
        var depth = 1
        var step = DungeonSavedStep.Explore
        var levelEvents = emptyMap<Int, DungeonEventId>()
        var postDungeon: MissionPostDungeonSnapshot? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "dungeon" -> {
                    expectSymbol('=')
                    dungeon = parseDungeonId()
                }
                "maxDepth" -> maxDepth = parseAssignedInt()
                "depth" -> depth = parseAssignedInt()
                "step" -> {
                    expectSymbol('=')
                    step = parseEnum("DungeonSavedStep")
                }
                "levelEvents" -> levelEvents = parseDungeonLevelEventsBody()
                "postDungeon" -> postDungeon = parseMissionPostDungeonBody()
                else -> unexpected("Dungeon session field $field")
            }
        }
        return RuntimeModeSnapshot.Dungeon(
            session =
                DungeonSessionSnapshot(
                    dungeon = checkNotNull(dungeon) { "Dungeon session requires dungeon" },
                    maxDepth = maxDepth,
                    depth = depth,
                    levelEvents = levelEvents,
                    step = step,
                ),
            postDungeon = postDungeon,
        )
    }

    private fun parseDungeonLevelEventsBody(): Map<Int, DungeonEventId> {
        expectSymbol('{')
        return buildMap {
            while (!matchSymbol('}')) {
                expectIdent("level")
                expectSymbol('(')
                val level = parseInt()
                expectSymbol(',')
                val event = parseDungeonEventId()
                expectSymbol(')')
                put(level, event)
            }
        }
    }

    private fun parseMissionPostDungeonBody(): MissionPostDungeonSnapshot {
        expectSymbol('{')
        var mission: MissionId? = null
        var scene: SceneId? = null
        var continuation: ScriptContinuationSnapshot? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "mission" -> {
                    expectSymbol('=')
                    mission = parseMissionId()
                }
                "scene" -> {
                    expectSymbol('=')
                    scene = parseSceneId()
                }
                "continuation" -> continuation = parseScriptContinuationBody()
                else -> unexpected("postDungeon field $field")
            }
        }
        return MissionPostDungeonSnapshot(
            mission = checkNotNull(mission) { "postDungeon requires mission" },
            scene = checkNotNull(scene) { "postDungeon requires scene" },
            continuation = checkNotNull(continuation) { "postDungeon requires continuation" },
        )
    }

    private fun parseScriptContinuationBody(): ScriptContinuationSnapshot {
        expectSymbol('{')
        var root = ScriptRootRef.Unspecified
        var locals = emptyMap<String, Int>()
        var realmScalars = emptyMap<String, Int>()
        var stack = emptyList<ScriptFrame>()
        var activeInventorySlot: Int? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "activeInventorySlot" -> {
                    expectSymbol('=')
                    activeInventorySlot = parseNullable(::parseInt)
                }
                "locals" -> locals = parseNamedIntMapBody()
                "realmScalars" -> realmScalars = parseNamedIntMapBody()
                "root" -> root = parseScriptRootBody()
                "stack" -> stack = parseScriptStackBody()
                else -> unexpected("script continuation field $field")
            }
        }
        return ScriptContinuationSnapshot(
            root = root,
            locals = locals,
            realmScalars = realmScalars,
            stack = stack,
            activeInventorySlot = activeInventorySlot,
        )
    }

    private fun parseScriptRootBody(): ScriptRootRef {
        expectSymbol('{')
        var kind = ScriptRootKind.Unspecified
        var denseId: Int? = null
        var slug = ""
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "kind" -> {
                    expectSymbol('=')
                    kind = parseEnum("ScriptRootKind")
                }
                "denseId" -> {
                    expectSymbol('=')
                    denseId = parseNullable(::parseInt)
                }
                "slug" -> slug = parseAssignedString()
                else -> unexpected("script root field $field")
            }
        }
        return ScriptRootRef(kind = kind, denseId = denseId, slug = slug)
    }

    private fun parseNamedIntMapBody(): Map<String, Int> {
        expectSymbol('{')
        return buildMap {
            while (!matchSymbol('}')) {
                expectIdent("value")
                expectSymbol('(')
                val key = parseString()
                expectSymbol(',')
                val value = parseInt()
                expectSymbol(')')
                put(key, value)
            }
        }
    }

    private fun parseScriptStackBody(): List<ScriptFrame> {
        expectSymbol('{')
        return buildList {
            while (!matchSymbol('}')) {
                expectIdent("frame")
                add(parseScriptFrameBody())
            }
        }
    }

    private fun parseScriptFrameBody(): ScriptFrame {
        expectSymbol('{')
        var block = ScriptBlockRef.Root
        var nextIndex = 0
        var loop = false
        var iteration = 0
        var localName: String? = null
        var localValue: Int? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "block" -> block = parseScriptBlockBody()
                "nextIndex" -> nextIndex = parseAssignedInt()
                "loop" -> loop = parseAssignedBoolean()
                "iteration" -> iteration = parseAssignedInt()
                "localName" -> {
                    expectSymbol('=')
                    localName = parseNullable(::parseString)
                }
                "localValue" -> {
                    expectSymbol('=')
                    localValue = parseNullable(::parseInt)
                }
                else -> unexpected("script frame field $field")
            }
        }
        return ScriptFrame(
            block = block,
            nextIndex = nextIndex,
            loop = loop,
            iteration = iteration,
            localName = localName,
            localValue = localValue,
        )
    }

    private fun parseScriptBlockBody(): ScriptBlockRef {
        expectSymbol('{')
        return ScriptBlockRef(
            buildList {
                while (!matchSymbol('}')) {
                    expectIdent("segment")
                    add(parseScriptSegmentBody())
                }
            },
        )
    }

    private fun parseScriptSegmentBody(): ScriptSegment {
        expectSymbol('{')
        var opIndex = 0
        var branch = ScriptBranch.IfThen
        var caseIndex: Int? = null
        var key: Char? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "opIndex" -> opIndex = parseAssignedInt()
                "branch" -> {
                    expectSymbol('=')
                    branch = parseEnum("ScriptBranch")
                }
                "caseIndex" -> {
                    expectSymbol('=')
                    caseIndex = parseNullable(::parseInt)
                }
                "key" -> {
                    expectSymbol('=')
                    key = parseNullable(::parseChar)
                }
                else -> unexpected("script segment field $field")
            }
        }
        return ScriptSegment(opIndex, branch, caseIndex, key)
    }

    private fun parsePartyAfterKeyword(): PartyState {
        expectSymbol('(')
        val id = parsePartyId()
        expectSymbol(')')
        expectSymbol('{')
        var player = ""
        var name = ""
        var position: WorldPosition = Wilderness.xy(0, 0)
        var facing: Facing? = null
        var resources = PartyResources(0, 0, 0, 0, 0)
        var counters =
            PartyCounters(
                memberCount = null,
                kills = 0,
                waitingMail = false,
                attacked = false,
                attackedToday = emptyList(),
            )
        var flags = emptyList<FlagState>()
        var milestones = emptyList<GameMilestoneId>()
        var inventory = emptyList<InventorySlotState>()
        var members = emptyList<PartyMemberState>()
        var visits = emptyList<NpcVisitState>()
        var journal = emptyList<JournalEntryState>()
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "player" -> player = parseAssignedString()
                "name" -> name = parseAssignedString()
                "position" -> {
                    expectSymbol('=')
                    position = parsePosition()
                }
                "facing" -> {
                    expectSymbol('=')
                    facing = parseNullable { parseEnum<Facing>("Facing") }
                }
                "resources" -> resources = parseResourcesBody()
                "counters" -> counters = parseCountersBody()
                "flags" -> flags = parseFlagsBody()
                "milestones" -> milestones = parseMilestonesBody()
                "inventory" -> inventory = parseInventoryBody()
                "members" -> members = parseMembersBody()
                "npcVisits" -> visits = parseNpcVisitsBody()
                "journal" -> journal = parseJournalBody()
                else -> unexpected("party field $field")
            }
        }
        return PartyState(id, player, name, position, facing, resources, counters, flags, milestones, inventory, members, visits, journal)
    }

    private fun parseResourcesBody(): PartyResources {
        expectSymbol('{')
        var gold = 0
        var torches = 0
        var food = 0
        var timeLeft = 0
        var restedHours = 0
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "gold" -> gold = parseAssignedInt()
                "torches" -> torches = parseAssignedInt()
                "food" -> food = parseAssignedInt()
                "timeLeft" -> timeLeft = parseAssignedInt()
                "restedHours" -> restedHours = parseAssignedInt()
                else -> unexpected("resources field $field")
            }
        }
        return PartyResources(gold, torches, food, timeLeft, restedHours)
    }

    private fun parseCountersBody(): PartyCounters {
        expectSymbol('{')
        var memberCount: Int? = null
        var kills = 0
        var waitingMail = false
        var attacked = false
        var attackedToday = emptyList<PartyAttackRecord>()
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "memberCount" -> {
                    expectSymbol('=')
                    memberCount = parseNullable(::parseInt)
                }
                "kills" -> kills = parseAssignedInt()
                "waitingMail" -> waitingMail = parseAssignedBoolean()
                "attacked" -> attacked = parseAssignedBoolean()
                "attackToday" -> attackedToday = parseAttackTodayBody()
                else -> unexpected("counters field $field")
            }
        }
        return PartyCounters(memberCount, kills, waitingMail, attacked, attackedToday)
    }

    private fun parseAttackTodayBody(): List<PartyAttackRecord> {
        expectSymbol('{')
        return buildList {
            while (!matchSymbol('}')) {
                expectIdent("party")
                expectSymbol('(')
                val party = parsePartyId()
                expectSymbol(',')
                expectIdent("attacked")
                expectSymbol('=')
                val attacked = parseBoolean()
                expectSymbol(')')
                add(PartyAttackRecord(party = party, attacked = attacked))
            }
        }
    }

    private fun parseFlagsBody(): List<FlagState> {
        expectSymbol('{')
        return buildList {
            while (!matchSymbol('}')) {
                expectIdent("mark")
                expectSymbol('(')
                expectIdent("runtimeFlagState")
                expectSymbol('(')
                val flag = parseString()
                expectSymbol(',')
                val native = parseString()
                expectSymbol(')')
                expectSymbol(')')
                add(runtimeFlagState(flag, native))
            }
        }
    }

    private fun parseMilestonesBody(): List<GameMilestoneId> {
        expectSymbol('{')
        return buildList {
            while (!matchSymbol('}')) {
                expectIdent("achieved")
                expectSymbol('(')
                add(parseMilestoneId())
                expectSymbol(')')
            }
        }
    }

    private fun parseInventoryBody(): List<InventorySlotState> {
        expectSymbol('{')
        return buildList {
            while (!matchSymbol('}')) {
                expectIdent("slot")
                expectSymbol('(')
                val slot = parseInt()
                expectSymbol(')')
                expectSymbol('.')
                when (val action = expectIdentifier()) {
                    "empty" -> {
                        expectSymbol('(')
                        expectSymbol(')')
                        add(InventorySlotState(slot, null))
                    }
                    "item" -> {
                        expectSymbol('(')
                        add(InventorySlotState(slot, parseItemId()))
                        expectSymbol(')')
                    }
                    else -> unexpected("inventory slot action $action")
                }
            }
        }
    }

    private fun parseMembersBody(): List<PartyMemberState> {
        expectSymbol('{')
        return buildList {
            while (!matchSymbol('}')) {
                expectIdent("member")
                expectSymbol('(')
                val name = parseString()
                expectSymbol(')')
                add(parseMemberBody(name))
            }
        }
    }

    private fun parseMemberBody(name: String): PartyMemberState {
        expectSymbol('{')
        var gender = Gender.Male
        var race = Race.Human
        var klass = PcClass.Thief
        var level = 1
        var health = 1
        var hitPoints = 1 of 1
        var weaponSkill = 0
        var wisdom = 0
        var mana = 0 of 0
        var charisma = 0
        var equipment = EquipmentState(DslDefaultItems.none, DslDefaultItems.none)
        var experience = 0
        var dead = false
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "gender" -> {
                    expectSymbol('=')
                    gender = parseEnum("Gender")
                }
                "race" -> {
                    expectSymbol('=')
                    race = parseEnum("Race")
                }
                "klass" -> {
                    expectSymbol('=')
                    klass = parseEnum("PcClass")
                }
                "level" -> level = parseAssignedInt()
                "health" -> health = parseAssignedInt()
                "hitPoints" -> {
                    expectSymbol('=')
                    val current = parseInt()
                    expectIdent("of")
                    hitPoints = current of parseInt()
                }
                "weaponSkill" -> weaponSkill = parseAssignedInt()
                "wisdom" -> wisdom = parseAssignedInt()
                "mana" -> {
                    expectSymbol('=')
                    val current = parseInt()
                    when (val separator = expectIdentifier()) {
                        "of", "manaOf" -> mana = current of parseInt()
                        else -> unexpected("mana separator '$separator'")
                    }
                }
                "charisma" -> charisma = parseAssignedInt()
                "equipped" -> equipment = parseEquipmentBody()
                "experience" -> experience = parseAssignedInt()
                "dead" -> {
                    expectSymbol('(')
                    expectSymbol(')')
                    dead = true
                }
                "alive" -> {
                    expectSymbol('(')
                    expectSymbol(')')
                    dead = false
                }
                else -> unexpected("member field $field")
            }
        }
        return PartyMemberState(
            name,
            gender,
            race,
            klass,
            level,
            health,
            hitPoints,
            weaponSkill,
            wisdom,
            mana,
            charisma,
            equipment,
            experience,
            dead,
        )
    }

    private fun parseEquipmentBody(): EquipmentState {
        expectSymbol('{')
        var weapon = DslDefaultItems.none
        var armor = DslDefaultItems.none
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "weapon" -> {
                    expectSymbol('=')
                    weapon = parseItemId()
                }
                "armor" -> {
                    expectSymbol('=')
                    armor = parseItemId()
                }
                else -> unexpected("equipment field $field")
            }
        }
        return EquipmentState(weapon, armor)
    }

    private fun parseNpcVisitsBody(): List<NpcVisitState> {
        expectSymbol('{')
        return buildList {
            while (!matchSymbol('}')) {
                when (val action = expectIdentifier()) {
                    "visited" -> {
                        expectSymbol('(')
                        add(NpcVisitState(npc = parseNpcId(), visited = true))
                        expectSymbol(')')
                    }
                    "notVisited" -> {
                        expectSymbol('(')
                        add(NpcVisitState(npc = parseNpcId(), visited = false))
                        expectSymbol(')')
                    }
                    else -> unexpected("NPC visit action $action")
                }
            }
        }
    }

    private fun parseJournalBody(): List<JournalEntryState> {
        expectSymbol('{')
        return buildList {
            while (!matchSymbol('}')) {
                when (val action = expectIdentifier()) {
                    "entry" -> {
                        expectSymbol('(')
                        add(JournalEntryState.Text(parseTextId()))
                        expectSymbol(')')
                    }
                    "line" -> {
                        expectSymbol('(')
                        add(JournalEntryState.Line(parseString()))
                        expectSymbol(')')
                    }
                    else -> unexpected("journal action $action")
                }
            }
        }
    }

    private fun parsePosition(): WorldPosition =
        when (val name = expectIdentifier()) {
            "Wilderness" -> {
                expectSymbol('.')
                expectIdent("xy")
                expectSymbol('(')
                val x = parseInt()
                expectSymbol(',')
                val y = parseInt()
                expectSymbol(')')
                Wilderness.xy(x, y)
            }
            "townPosition" -> {
                expectSymbol('(')
                val town = parseTownId()
                expectSymbol(',')
                val building = parseBuildingId()
                expectSymbol(')')
                WorldPosition.Town(town, building)
            }
            else -> unexpected("position $name")
        }

    private fun parsePoint(): GridPoint {
        expectIdent("xy")
        expectSymbol('(')
        val x = parseInt()
        expectSymbol(',')
        val y = parseInt()
        expectSymbol(')')
        return xy(x, y)
    }

    private fun parseStateId(): StateId = parseStringCall("stateId", ::stateId)

    private fun parsePartyId(): PartyId = parseStringCall("partyId", ::partyId)

    private fun parseWorldMapId(): WorldMapId = parseStringCall("worldMap", ::worldMap)

    private fun parseTownId(): TownId = parseStringCall("town", ::town)

    private fun parseBuildingId(): BuildingId = parseStringCall("building", ::building)

    private fun parseLocationId(): LocationId = parseStringCall("location", ::location)

    private fun parseNpcId(): NpcId = parseStringCall("npc", ::npc)

    private fun parseItemId(): ItemId = parseStringCall("item", ::item)

    private fun parseMilestoneId(): GameMilestoneId = parseStringCall("milestone", ::milestone)

    private fun parseTextId(): TextId = parseStringCall("text", ::text)

    private fun parseSceneId(): SceneId = parseStringCall("scene", ::sceneId)

    private fun parseMissionId(): MissionId = parseStringCall("mission", ::missionId)

    private fun parseDungeonId(): DungeonId = parseStringCall("dungeon", ::dungeon)

    private fun parseDungeonEventId(): DungeonEventId = parseStringCall("dungeonEvent", ::dungeonEvent)

    private fun parseTerrainId(): TerrainId {
        expectIdent("terrain")
        expectSymbol('(')
        val slug = parseString()
        val sourceMapCode = if (matchSymbol(',')) parseChar() else null
        expectSymbol(')')
        return terrain(slug, sourceMapCode)
    }

    private fun <T> parseStringCall(
        name: String,
        build: (String) -> T,
    ): T {
        expectIdent(name)
        expectSymbol('(')
        val value = build(parseString())
        expectSymbol(')')
        return value
    }

    private fun parseAssignedInt(): Int {
        expectSymbol('=')
        return parseInt()
    }

    private fun parseAssignedLong(): Long {
        expectSymbol('=')
        return parseLong()
    }

    private fun parseAssignedBoolean(): Boolean {
        expectSymbol('=')
        return parseBoolean()
    }

    private fun parseAssignedString(): String {
        expectSymbol('=')
        return parseString()
    }

    private fun <T> parseNullable(parseValue: () -> T): T? = if (matchIdent("null")) null else parseValue()

    private inline fun <reified T : Enum<T>> parseEnum(prefix: String): T {
        expectIdent(prefix)
        expectSymbol('.')
        return enumValueOf(expectIdentifier())
    }

    private fun parseBoolean(): Boolean =
        when {
            matchIdent("true") -> true
            matchIdent("false") -> false
            else -> unexpected("boolean")
        }

    private fun parseInt(): Int =
        when (val current = token) {
            is SnapshotToken.Number -> current.value.toInt().also { advance() }
            else -> unexpected("integer")
        }

    private fun parseLong(): Long =
        when (val current = token) {
            is SnapshotToken.Number -> current.value.also { advance() }
            else -> unexpected("long")
        }

    private fun parseString(): String =
        when (val current = token) {
            is SnapshotToken.StringLiteral -> current.value.also { advance() }
            else -> unexpected("string")
        }

    private fun parseChar(): Char =
        when (val current = token) {
            is SnapshotToken.CharLiteral -> current.value.also { advance() }
            else -> unexpected("char")
        }

    private fun expectIdent(value: String) {
        val actual = expectIdentifier()
        check(actual == value) { "expected '$value' at ${lexer.position}, found '$actual'" }
    }

    private fun matchIdent(value: String): Boolean =
        when (val current = token) {
            is SnapshotToken.Identifier ->
                if (current.value == value) {
                    advance()
                    true
                } else {
                    false
                }
            else -> false
        }

    private fun expectIdentifier(): String =
        when (val current = token) {
            is SnapshotToken.Identifier -> current.value.also { advance() }
            else -> unexpected("identifier")
        }

    private fun expectSymbol(value: Char) {
        check(matchSymbol(value)) { "expected '$value' at ${lexer.position}, found $token" }
    }

    private fun matchSymbol(value: Char): Boolean =
        when (val current = token) {
            is SnapshotToken.Symbol ->
                if (current.value == value) {
                    advance()
                    true
                } else {
                    false
                }
            else -> false
        }

    private fun advance() {
        token = lexer.next()
    }

    private fun unexpected(expected: String): Nothing = error("expected $expected at ${lexer.position}, found $token")
}

private sealed interface SnapshotToken {
    data class Identifier(
        val value: String,
    ) : SnapshotToken

    data class StringLiteral(
        val value: String,
    ) : SnapshotToken

    data class CharLiteral(
        val value: Char,
    ) : SnapshotToken

    data class Number(
        val value: Long,
    ) : SnapshotToken

    data class Symbol(
        val value: Char,
    ) : SnapshotToken

    object End : SnapshotToken
}

private class RuntimeSnapshotLexer(
    private val source: String,
) {
    private var index = 0
    val position: Int get() = index

    fun next(): SnapshotToken {
        skipWhitespace()
        if (index >= source.length) return SnapshotToken.End
        val char = source[index]
        return when {
            char.isLetter() || char == '_' -> identifier()
            char == '"' -> stringLiteral()
            char == '\'' -> charLiteral()
            char == '-' || char.isDigit() -> number()
            char in "{}().,=" -> SnapshotToken.Symbol(char).also { index += 1 }
            else -> error("unexpected character '$char' at $index")
        }
    }

    private fun skipWhitespace() {
        while (index < source.length && source[index].isWhitespace()) {
            index += 1
        }
    }

    private fun identifier(): SnapshotToken.Identifier {
        val start = index
        while (index < source.length && (source[index].isLetterOrDigit() || source[index] == '_')) {
            index += 1
        }
        return SnapshotToken.Identifier(source.substring(start, index))
    }

    private fun number(): SnapshotToken.Number {
        val start = index
        if (source[index] == '-') index += 1
        while (index < source.length && source[index].isDigit()) {
            index += 1
        }
        val value = source.substring(start, index).toLong()
        if (index < source.length && source[index] == 'L') {
            index += 1
        }
        return SnapshotToken.Number(value)
    }

    private fun stringLiteral(): SnapshotToken.StringLiteral {
        index += 1
        val value =
            buildString {
                while (index < source.length) {
                    when (val char = source[index++]) {
                        '"' -> return SnapshotToken.StringLiteral(toString())
                        '\\' -> append(escapedChar())
                        else -> append(char)
                    }
                }
            }
        error("unterminated string literal at $index: $value")
    }

    private fun charLiteral(): SnapshotToken.CharLiteral {
        index += 1
        val value =
            when (val char = source.getOrNull(index++) ?: error("unterminated char literal")) {
                '\\' -> escapedChar()
                else -> char
            }
        check(source.getOrNull(index) == '\'') { "unterminated char literal at $index" }
        index += 1
        return SnapshotToken.CharLiteral(value)
    }

    private fun escapedChar(): Char {
        val escaped = source.getOrNull(index++) ?: error("unterminated escape at $index")
        return when (escaped) {
            '\\' -> '\\'
            '"' -> '"'
            '\'' -> '\''
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            else -> error("unknown escape \\$escaped at $index")
        }
    }
}

private class RuntimeStateSnapshotWriter {
    private val out = StringBuilder()
    private var indent = 0

    fun render(snapshot: RuntimeSnapshotModel): String {
        val state = snapshot.state
        line("runtimeState(${stateId(state.id)}) {")
        indented {
            snapshot.metadata?.let(::metadata)
            realm(state.realm)
            locations(state.locations)
            npcs(state.npcs)
            state.parties.forEach(::party)
            snapshot.mode?.let(::sessions)
        }
        line("}")
        return out.toString()
    }

    private fun metadata(metadata: RuntimeSnapshotMetadata) {
        line("metadata {")
        indented {
            assignment("formatVersion", metadata.formatVersion)
            line("universeId = ${metadata.universeId?.let(::quote) ?: "null"}")
            line("universeVersion = ${metadata.universeVersion?.let(::quote) ?: "null"}")
            line("contentHash = ${metadata.contentHash?.let(::quote) ?: "null"}")
        }
        line("}")
    }

    private fun realm(realm: RealmState) {
        val rngState =
            checkNotNull(realm.rngState) {
                "runtime snapshot ${realm.activeParty.slug} requires rngState before emission"
            }
        line("realm {")
        indented {
            assignment("day", realm.day)
            assignment("hour", realm.hour)
            assignment("minute", realm.minute)
            line("rngState = ${rngState}L")
            line("activeParty = ${partyId(realm.activeParty)}")
            line("currentMap = ${worldMapId(realm.currentMap)}")
            line("currentMode = RuntimeMode.${realm.currentMode.name}")
            line("currentTown = ${realm.currentTown?.let(::townId) ?: "null"}")
            assignment("banished", realm.banished)
            assignment("lastPlayedDay", realm.lastPlayedDay)
            assignment("partyMemberMax", realm.partyMemberMax)
            realm.lastTerrain?.let { line("lastTerrain = ${terrainId(it)}") }
        }
        line("}")
    }

    private fun locations(locations: List<LocationPlacement>) {
        line("locations {")
        indented {
            locations.sortedWith(compareBy({ it.location.slug }, { it.point.x }, { it.point.y })).forEach {
                line("${locationId(it.location)} at ${point(it.point)}")
            }
        }
        line("}")
    }

    private fun npcs(npcs: List<NpcPlacement>) {
        line("npcs {")
        indented {
            npcs.sortedBy { it.npc.slug }.forEach {
                line("${npcId(it.npc)} at ${position(it.position)}")
            }
        }
        line("}")
    }

    private fun sessions(mode: RuntimeModeSnapshot) {
        line("sessions {")
        indented {
            when (mode) {
                is RuntimeModeSnapshot.YdargCastle -> ydarg(mode.session)
                is RuntimeModeSnapshot.Dungeon -> dungeon(mode)
            }
        }
        line("}")
    }

    private fun ydarg(session: YdargSessionSnapshot) {
        line("ydarg {")
        indented {
            assignment("floor", session.floor)
            line("point = ${point(session.point)}")
            line("step = YdargSavedStep.${session.step.name}")
            line("treasureItem = ${session.treasureItem?.let(::itemId) ?: "null"}")
            line("rooms {")
            indented {
                session.rooms.entries
                    .sortedWith(compareBy({ it.key.x }, { it.key.y }))
                    .forEach { (roomPoint, event) ->
                        line("room(${point(roomPoint)}, YdargRoomEvent.${event.name})")
                    }
            }
            line("}")
        }
        line("}")
    }

    private fun dungeon(mode: RuntimeModeSnapshot.Dungeon) {
        val session = mode.session
        line("dungeon {")
        indented {
            line("dungeon = ${dungeonId(session.dungeon)}")
            assignment("maxDepth", session.maxDepth)
            assignment("depth", session.depth)
            line("step = DungeonSavedStep.${session.step.name}")
            line("levelEvents {")
            indented {
                session.levelEvents.entries
                    .sortedBy { it.key }
                    .forEach { (level, event) ->
                        line("level($level, ${dungeonEventId(event)})")
                    }
            }
            line("}")
            mode.postDungeon?.let(::postDungeon)
        }
        line("}")
    }

    private fun postDungeon(postDungeon: MissionPostDungeonSnapshot) {
        line("postDungeon {")
        indented {
            line("mission = ${missionId(postDungeon.mission)}")
            line("scene = ${sceneId(postDungeon.scene)}")
            scriptContinuation(postDungeon.continuation)
        }
        line("}")
    }

    private fun scriptContinuation(continuation: ScriptContinuationSnapshot) {
        line("continuation {")
        indented {
            scriptRoot(continuation.root)
            line("activeInventorySlot = ${continuation.activeInventorySlot ?: "null"}")
            namedIntMap("locals", continuation.locals)
            namedIntMap("realmScalars", continuation.realmScalars)
            line("stack {")
            indented {
                continuation.stack.forEach(::scriptFrame)
            }
            line("}")
        }
        line("}")
    }

    private fun scriptRoot(root: ScriptRootRef) {
        line("root {")
        indented {
            line("kind = ScriptRootKind.${root.kind.name}")
            line("denseId = ${root.denseId ?: "null"}")
            line("slug = ${quote(root.slug)}")
        }
        line("}")
    }

    private fun namedIntMap(
        name: String,
        values: Map<String, Int>,
    ) {
        line("$name {")
        indented {
            values.entries.sortedBy { it.key }.forEach { (key, value) ->
                line("value(${quote(key)}, $value)")
            }
        }
        line("}")
    }

    private fun scriptFrame(frame: ScriptFrame) {
        line("frame {")
        indented {
            scriptBlock(frame.block)
            assignment("nextIndex", frame.nextIndex)
            assignment("loop", frame.loop)
            assignment("iteration", frame.iteration)
            line("localName = ${frame.localName?.let(::quote) ?: "null"}")
            line("localValue = ${frame.localValue ?: "null"}")
        }
        line("}")
    }

    private fun scriptBlock(block: ScriptBlockRef) {
        line("block {")
        indented {
            block.segments.forEach(::scriptSegment)
        }
        line("}")
    }

    private fun scriptSegment(segment: ScriptSegment) {
        line("segment {")
        indented {
            assignment("opIndex", segment.opIndex)
            line("branch = ScriptBranch.${segment.branch.name}")
            line("caseIndex = ${segment.caseIndex ?: "null"}")
            line("key = ${segment.key?.let(::charLiteral) ?: "null"}")
        }
        line("}")
    }

    private fun party(party: PartyState) {
        line("party(${partyId(party.id)}) {")
        indented {
            assignment("player", party.player)
            assignment("name", party.name)
            line("position = ${position(party.position)}")
            line("facing = ${party.facing?.let { "Facing.${it.name}" } ?: "null"}")
            resources(party.resources)
            counters(party.counters)
            flags(party.flags)
            milestones(party.milestones)
            inventory(party.inventory)
            members(party.members)
            visits(party.visitedNpcs)
            journal(party.journal)
        }
        line("}")
    }

    private fun resources(resources: PartyResources) {
        line("resources {")
        indented {
            assignment("gold", resources.gold)
            assignment("torches", resources.torches)
            assignment("food", resources.food)
            assignment("timeLeft", resources.timeLeft)
            assignment("restedHours", resources.restedHours)
        }
        line("}")
    }

    private fun counters(counters: PartyCounters) {
        line("counters {")
        indented {
            line("memberCount = ${counters.memberCount ?: "null"}")
            assignment("kills", counters.kills)
            assignment("waitingMail", counters.waitingMail)
            assignment("attacked", counters.attacked)
            line("attackToday {")
            indented {
                counters.attackedToday.sortedBy { it.party.slug }.forEach {
                    line("party(${partyId(it.party)}, attacked = ${it.attacked})")
                }
            }
            line("}")
        }
        line("}")
    }

    private fun flags(flags: List<FlagState>) {
        line("flags {")
        indented {
            flags.sortedWith(compareBy({ it.flag.slug }, { it.native })).forEach {
                line("mark(runtimeFlagState(${quote(it.flag.slug)}, ${quote(it.native)}))")
            }
        }
        line("}")
    }

    private fun milestones(milestones: List<GameMilestoneId>) {
        line("milestones {")
        indented {
            milestones.sortedBy { it.slug }.forEach {
                line("achieved(${milestoneId(it)})")
            }
        }
        line("}")
    }

    private fun inventory(inventory: List<InventorySlotState>) {
        line("inventory {")
        indented {
            inventory.sortedBy { it.slot }.forEach { slot ->
                val slotExpr = "slot(${slot.slot})"
                line(slot.item?.let { "$slotExpr.item(${itemId(it)})" } ?: "$slotExpr.empty()")
            }
        }
        line("}")
    }

    private fun members(members: List<PartyMemberState>) {
        line("members {")
        indented {
            members.forEach(::member)
        }
        line("}")
    }

    private fun member(member: PartyMemberState) {
        line("member(${quote(member.name)}) {")
        indented {
            line("gender = Gender.${member.gender.name}")
            line("race = Race.${member.race.name}")
            line("klass = PcClass.${member.pcClass.name}")
            assignment("level", member.level)
            assignment("health", member.health)
            line("hitPoints = ${member.hitPoints.current} of ${member.hitPoints.max}")
            assignment("weaponSkill", member.weaponSkill)
            assignment("wisdom", member.wisdom)
            line("mana = ${member.mana.current} of ${member.mana.max}")
            assignment("charisma", member.charisma)
            equipment(member.equipment)
            assignment("experience", member.experience)
            line(if (member.dead) "dead()" else "alive()")
        }
        line("}")
    }

    private fun equipment(equipment: EquipmentState) {
        line("equipped {")
        indented {
            line("weapon = ${itemId(equipment.weapon)}")
            line("armor = ${itemId(equipment.armor)}")
        }
        line("}")
    }

    private fun visits(visits: List<NpcVisitState>) {
        line("npcVisits {")
        indented {
            visits.sortedBy { it.npc.slug }.forEach {
                line(if (it.visited) "visited(${npcId(it.npc)})" else "notVisited(${npcId(it.npc)})")
            }
        }
        line("}")
    }

    private fun journal(entries: List<JournalEntryState>) {
        line("journal {")
        indented {
            entries.forEach { entry ->
                when (entry) {
                    is JournalEntryState.Text -> line("entry(${textId(entry.id)})")
                    is JournalEntryState.Line -> line("line(${quote(entry.text)})")
                }
            }
        }
        line("}")
    }

    private fun position(position: WorldPosition): String =
        when (position) {
            is WorldPosition.Wilderness -> "Wilderness.xy(${position.point.x}, ${position.point.y})"
            is WorldPosition.Town -> "townPosition(${townId(position.town)}, ${buildingId(position.building)})"
        }

    private fun point(point: GridPoint): String = "xy(${point.x}, ${point.y})"

    private fun stateId(id: StateId): String = "stateId(${quote(id.slug)})"

    private fun partyId(id: PartyId): String = "partyId(${quote(id.slug)})"

    private fun worldMapId(id: WorldMapId): String = "worldMap(${quote(id.slug)})"

    private fun terrainId(id: TerrainId): String =
        id.sourceMapCode?.let { "terrain(${quote(id.slug)}, ${charLiteral(it)})" }
            ?: "terrain(${quote(id.slug)})"

    private fun townId(id: TownId): String = "town(${quote(id.slug)})"

    private fun buildingId(id: BuildingId): String = "building(${quote(id.slug)})"

    private fun locationId(id: LocationId): String = "location(${quote(id.slug)})"

    private fun npcId(id: NpcId): String = "npc(${quote(id.slug)})"

    private fun itemId(id: ItemId): String = "item(${quote(id.slug)})"

    private fun milestoneId(id: GameMilestoneId): String = "milestone(${quote(id.slug)})"

    private fun textId(id: TextId): String = "text(${quote(id.id)})"

    private fun sceneId(id: SceneId): String = "scene(${quote(id.slug)})"

    private fun missionId(id: MissionId): String = "mission(${quote(id.slug)})"

    private fun dungeonId(id: DungeonId): String = "dungeon(${quote(id.slug)})"

    private fun dungeonEventId(id: DungeonEventId): String = "dungeonEvent(${quote(id.slug)})"

    private fun assignment(
        name: String,
        value: Int,
    ) = line("$name = $value")

    private fun assignment(
        name: String,
        value: Boolean,
    ) = line("$name = $value")

    private fun assignment(
        name: String,
        value: String,
    ) = line("$name = ${quote(value)}")

    private fun indented(body: () -> Unit) {
        indent += 1
        body()
        indent -= 1
    }

    private fun line(text: String) {
        out.append("    ".repeat(indent)).appendLine(text)
    }

    private fun quote(value: String): String = dslQuotedString(value)

    private fun charLiteral(value: Char): String =
        when (value) {
            '\\' -> "'\\\\'"
            '\'' -> "'\\''"
            '\n' -> "'\\n'"
            '\r' -> "'\\r'"
            '\t' -> "'\\t'"
            else -> "'$value'"
        }
}
