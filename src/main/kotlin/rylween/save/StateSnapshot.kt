/**
 * Textual strict runtime snapshot renderer and parser.
 *
 * [RuntimeSave.kt] writes this format for user-visible saves where the active
 * frontend/session permits them. J2 compiled-VM tests and backend handoffs also
 * use the same data-only format for execution-boundary round trips. The ability
 * to represent a blocked battle, reward, or VM continuation here does not by
 * itself make that point a legal player save point.
 *
 * [RuntimeLoad.kt] validates snapshots before resuming play, and
 * [SaveCompatibility.kt] adds metadata used to reject stale files across
 * content or format changes.
 */
package rylween.save

import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledInputBinding
import rylween.compiled.CompiledOfferPauses
import rylween.compiled.CompiledRandomItemOffer
import rylween.compiled.CompiledRewardMessages
import rylween.compiled.CompiledScriptReward
import rylween.compiled.CompiledShopMenuKeys
import rylween.compiled.CompiledShopService
import rylween.compiled.CompiledTextBlock
import rylween.dsl.BuildingId
import rylween.dsl.CastleId
import rylween.dsl.CastleRoomEvent
import rylween.dsl.DslDefaultItems
import rylween.dsl.DslDefaultMaps
import rylween.dsl.DungeonEventId
import rylween.dsl.DungeonId
import rylween.dsl.EncounterTier
import rylween.dsl.EquipmentState
import rylween.dsl.Facing
import rylween.dsl.FlagState
import rylween.dsl.GameMilestoneId
import rylween.dsl.Gender
import rylween.dsl.GridPoint
import rylween.dsl.ImageId
import rylween.dsl.InputBindingModel
import rylween.dsl.InventorySlotChoiceFilter
import rylween.dsl.InventorySlotState
import rylween.dsl.ItemId
import rylween.dsl.ItemPoolId
import rylween.dsl.JournalEntryState
import rylween.dsl.LocationId
import rylween.dsl.LocationPlacement
import rylween.dsl.MemberChoiceFilter
import rylween.dsl.MissionId
import rylween.dsl.MonsterId
import rylween.dsl.NpcId
import rylween.dsl.NpcPlacement
import rylween.dsl.NpcRole
import rylween.dsl.NpcVisitState
import rylween.dsl.PartyAttackRecord
import rylween.dsl.PartyCounters
import rylween.dsl.PartyId
import rylween.dsl.PartyMemberState
import rylween.dsl.PartyResources
import rylween.dsl.PartyState
import rylween.dsl.PcClass
import rylween.dsl.Race
import rylween.dsl.RandomItemOfferModel
import rylween.dsl.RealmState
import rylween.dsl.RewardApplicationModel
import rylween.dsl.RewardItemPlacement
import rylween.dsl.RewardMessageModel
import rylween.dsl.RewardOverflowPolicy
import rylween.dsl.RewardSpecialItemPolicy
import rylween.dsl.RumourTableId
import rylween.dsl.RuntimeMode
import rylween.dsl.SceneId
import rylween.dsl.ScriptRewardModel
import rylween.dsl.StateId
import rylween.dsl.StateKind
import rylween.dsl.StateModel
import rylween.dsl.TerrainId
import rylween.dsl.TextBlock
import rylween.dsl.TextId
import rylween.dsl.TextLine
import rylween.dsl.TextPart
import rylween.dsl.TownId
import rylween.dsl.ViewCommand
import rylween.dsl.Wilderness
import rylween.dsl.WorldMapId
import rylween.dsl.WorldPosition
import rylween.dsl.building
import rylween.dsl.castle
import rylween.dsl.dungeon
import rylween.dsl.dungeonEvent
import rylween.dsl.encounterTier
import rylween.dsl.image
import rylween.dsl.item
import rylween.dsl.itemPool
import rylween.dsl.line
import rylween.dsl.location
import rylween.dsl.milestone
import rylween.dsl.missionId
import rylween.dsl.monster
import rylween.dsl.npc
import rylween.dsl.npcRole
import rylween.dsl.of
import rylween.dsl.partyId
import rylween.dsl.render
import rylween.dsl.rumourTable
import rylween.dsl.runtimeFlagState
import rylween.dsl.sceneId
import rylween.dsl.stateId
import rylween.dsl.terrain
import rylween.dsl.text
import rylween.dsl.town
import rylween.dsl.worldMap
import rylween.dsl.xy
import rylween.runtime.CastleSavedStep
import rylween.runtime.CastleSessionSnapshot
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
import rylween.runtime.battle.BattleRoundState
import rylween.runtime.battle.BattleSavedStep
import rylween.runtime.battle.BattleSessionResultSnapshot
import rylween.runtime.battle.BattleSessionResultStatus
import rylween.runtime.battle.BattleSessionSnapshot
import rylween.runtime.battle.CombatScriptSnapshot
import rylween.runtime.battle.CombatScriptSourceKind
import rylween.runtime.castle.CastleScriptContinuation
import rylween.runtime.reward.RandomItemOfferResultSnapshot
import rylween.runtime.reward.RandomItemOfferSavedStep
import rylween.runtime.reward.RandomItemOfferSessionSnapshot
import rylween.runtime.reward.RandomItemOfferStatus
import rylween.runtime.reward.RewardGrantResultSnapshot
import rylween.runtime.reward.RewardGrantSavedStep
import rylween.runtime.reward.RewardGrantSessionSnapshot
import rylween.runtime.reward.RewardGrantStatus
import rylween.runtime.town.ShopSellQuoteResultSnapshot
import rylween.runtime.town.ShopSellQuoteStatus
import rylween.runtime.town.ShopServiceSavedStep
import rylween.runtime.town.ShopServiceSessionSnapshot
import rylween.runtime.town.ShopTransactionResultSnapshot
import rylween.runtime.town.ShopTransactionStatus
import rylween.runtime.town.SpeakSavedStep
import rylween.runtime.town.SpeakSessionSnapshot
import rylween.runtime.vm.CompiledVmChildRequestSnapshot
import rylween.runtime.vm.CompiledVmContinuationSnapshot
import rylween.runtime.vm.CompiledVmFrameKind
import rylween.runtime.vm.CompiledVmFrameSnapshot
import rylween.runtime.vm.CompiledVmInventorySlotChoiceSnapshot
import rylween.runtime.vm.CompiledVmMenuOptionSnapshot
import rylween.runtime.vm.CompiledVmPendingInputSnapshot
import rylween.runtime.wilderness.WildernessDungeonHandoff

/** Renders one runtime [state] as exact save DSL text. */
fun renderRuntimeStateSnapshot(state: StateModel): String {
    check(state.kind == StateKind.RuntimeSnapshot) {
        "only runtime snapshots can be emitted as exact save DSL"
    }
    return renderRuntimeStateSnapshot(RuntimeSnapshotModel(state))
}

/** Renders a runtime [snapshot], including optional metadata and session snapshots. */
fun renderRuntimeStateSnapshot(snapshot: RuntimeSnapshotModel): String {
    check(snapshot.state.kind == StateKind.RuntimeSnapshot) {
        "only runtime snapshots can be emitted as exact save DSL"
    }
    return RuntimeStateSnapshotWriter().render(snapshot)
}

/** Parses save DSL text and returns only the stored state model. */
fun parseRuntimeStateSnapshot(source: String): StateModel = parseRuntimeSnapshot(source).state

/** Parses save DSL text into the full runtime snapshot, including nested sessions. */
fun parseRuntimeSnapshot(source: String): RuntimeSnapshotModel = RuntimeStateSnapshotParser(source).parseSingle()

private val EmptyCompiledTextBlock = CompiledTextBlock.Inline(emptyList())
private val EmptyCompiledInputBinding = CompiledInputBinding(primaryString = 0, aliasStrings = emptyList(), labelString = 0)

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
        var compiledBundleHash: String? = null
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
                "compiledBundleHash" -> {
                    expectSymbol('=')
                    compiledBundleHash = parseNullable(::parseString)
                }
                else -> unexpected("metadata field $field")
            }
        }
        return RuntimeSnapshotMetadata(
            formatVersion = formatVersion,
            contentHash = contentHash,
            compiledBundleHash = compiledBundleHash,
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
                "castle" -> {
                    check(mode == null) { "runtime snapshot has multiple active session blocks" }
                    mode = RuntimeModeSnapshot.Castle(parseCastleSessionBody())
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

    private fun parseCastleSessionBody(): CastleSessionSnapshot {
        expectSymbol('{')
        var castle: CastleId? = null
        var floor = 1
        var point = GridPoint(1, 1)
        var step = CastleSavedStep.Navigate
        var treasureItem: ItemId? = null
        var scriptScene: SceneId? = null
        var scriptContinuation: ScriptContinuationSnapshot? = null
        var scriptNext: CastleScriptContinuation? = null
        var rooms = emptyMap<GridPoint, CastleRoomEvent>()
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "castle" -> {
                    expectSymbol('=')
                    castle = parseCastleId()
                }
                "floor" -> floor = parseAssignedInt()
                "point" -> {
                    expectSymbol('=')
                    point = parsePoint()
                }
                "step" -> {
                    expectSymbol('=')
                    step = parseEnum("CastleSavedStep")
                }
                "treasureItem" -> {
                    expectSymbol('=')
                    treasureItem = parseNullable(::parseItemId)
                }
                "scriptScene" -> {
                    expectSymbol('=')
                    scriptScene = parseNullable(::parseSceneId)
                }
                "scriptNext" -> {
                    expectSymbol('=')
                    scriptNext = parseNullable { parseEnum<CastleScriptContinuation>("CastleScriptContinuation") }
                }
                "scriptContinuation" -> scriptContinuation = parseScriptContinuationBody()
                "rooms" -> rooms = parseCastleRoomsBody()
                else -> unexpected("Castle session field $field")
            }
        }
        return CastleSessionSnapshot(
            castle = checkNotNull(castle) { "castle session snapshot is missing castle id" },
            floor = floor,
            point = point,
            rooms = rooms,
            step = step,
            treasureItem = treasureItem,
            scriptScene = scriptScene,
            scriptContinuation = scriptContinuation,
            scriptNext = scriptNext,
        )
    }

    private fun parseCastleRoomsBody(): Map<GridPoint, CastleRoomEvent> {
        expectSymbol('{')
        return buildMap {
            while (!matchSymbol('}')) {
                expectIdent("room")
                expectSymbol('(')
                val point = parsePoint()
                expectSymbol(',')
                val event = parseEnum<CastleRoomEvent>("CastleRoomEvent")
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
        var speakSession: SpeakSessionSnapshot? = null
        var battleSession: BattleSessionSnapshot? = null
        var randomItemOfferSession: RandomItemOfferSessionSnapshot? = null
        var rewardGrantSession: RewardGrantSessionSnapshot? = null
        var shopServiceSession: ShopServiceSessionSnapshot? = null
        var compiledVmContinuation: CompiledVmContinuationSnapshot? = null
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
                "speakSession" -> speakSession = parseSpeakSessionBody()
                "battleSession" -> battleSession = parseBattleSessionBody()
                "randomItemOfferSession" -> randomItemOfferSession = parseRandomItemOfferSessionBody()
                "rewardGrantSession" -> rewardGrantSession = parseRewardGrantSessionBody()
                "shopServiceSession" -> shopServiceSession = parseShopServiceSessionBody()
                "compiledVm" -> compiledVmContinuation = parseCompiledVmContinuationBody()
                else -> unexpected("script continuation field $field")
            }
        }
        return ScriptContinuationSnapshot(
            root = root,
            locals = locals,
            realmScalars = realmScalars,
            stack = stack,
            activeInventorySlot = activeInventorySlot,
            speakSession = speakSession,
            battleSession = battleSession,
            randomItemOfferSession = randomItemOfferSession,
            rewardGrantSession = rewardGrantSession,
            shopServiceSession = shopServiceSession,
            compiledVmContinuation = compiledVmContinuation,
        )
    }

    private fun parseSpeakSessionBody(): SpeakSessionSnapshot {
        expectSymbol('{')
        var building: BuildingId? = null
        var selectedNpc: NpcId? = null
        var rumourTold = false
        var step: SpeakSavedStep? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "building" -> {
                    expectSymbol('=')
                    building = parseBuildingId()
                }
                "selectedNpc" -> {
                    expectSymbol('=')
                    selectedNpc = parseNullable(::parseNpcId)
                }
                "rumourTold" -> rumourTold = parseAssignedBoolean()
                "step" -> step = parseSpeakStepBody()
                else -> unexpected("speak session field $field")
            }
        }
        return SpeakSessionSnapshot(
            building = checkNotNull(building) { "speak session requires building" },
            selectedNpc = selectedNpc,
            rumourTold = rumourTold,
            step = checkNotNull(step) { "speak session requires step" },
        )
    }

    private fun parseSpeakStepBody(): SpeakSavedStep {
        expectSymbol('{')
        val step =
            when (val kind = expectIdentifier()) {
                "chooseNpc" -> parseEmptySpeakStep(SpeakSavedStep.ChooseNpc)
                "scriptBranch" -> parseSpeakScriptBranchStepBody()
                "mainMenu" -> parseSpeakMainMenuStepBody()
                "questionMenu" -> parseSpeakQuestionMenuStepBody()
                "message" -> parseSpeakMessageStepBody()
                "finished" -> parseEmptySpeakStep(SpeakSavedStep.Finished)
                else -> unexpected("speak step kind $kind")
            }
        expectSymbol('}')
        return step
    }

    private fun parseSpeakScriptBranchStepBody(): SpeakSavedStep.ScriptBranch {
        expectSymbol('{')
        var npc: NpcId? = null
        var root: ScriptRootRef? = null
        var next: SpeakSavedStep? = null
        var continuation: ScriptContinuationSnapshot? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "npc" -> {
                    expectSymbol('=')
                    npc = parseNpcId()
                }
                "root" -> root = parseScriptRootBody()
                "next" -> next = parseSpeakStepBody()
                "continuation" -> continuation = parseNullableScriptContinuation()
                else -> unexpected("speak script branch step field $field")
            }
        }
        return SpeakSavedStep.ScriptBranch(
            npc = checkNotNull(npc) { "speak script branch requires npc" },
            root = checkNotNull(root) { "speak script branch requires root" },
            next = checkNotNull(next) { "speak script branch requires next step" },
            continuation = continuation,
        )
    }

    private fun parseSpeakMainMenuStepBody(): SpeakSavedStep.MainMenu {
        expectSymbol('{')
        var npc: NpcId? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "npc" -> {
                    expectSymbol('=')
                    npc = parseNpcId()
                }
                else -> unexpected("speak main menu step field $field")
            }
        }
        return SpeakSavedStep.MainMenu(checkNotNull(npc) { "speak main menu requires npc" })
    }

    private fun parseSpeakQuestionMenuStepBody(): SpeakSavedStep.QuestionMenu {
        expectSymbol('{')
        var npc: NpcId? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "npc" -> {
                    expectSymbol('=')
                    npc = parseNpcId()
                }
                else -> unexpected("speak question menu step field $field")
            }
        }
        return SpeakSavedStep.QuestionMenu(checkNotNull(npc) { "speak question menu requires npc" })
    }

    private fun parseSpeakMessageStepBody(): SpeakSavedStep.Message {
        expectSymbol('{')
        var blocks = emptyList<TextBlock>()
        var next: SpeakSavedStep? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "blocks" -> blocks = parseTextBlocksBody()
                "next" -> next = parseSpeakStepBody()
                else -> unexpected("speak message step field $field")
            }
        }
        return SpeakSavedStep.Message(blocks, checkNotNull(next) { "speak message requires next step" })
    }

    private fun parseEmptySpeakStep(step: SpeakSavedStep): SpeakSavedStep {
        expectSymbol('{')
        expectSymbol('}')
        return step
    }

    private fun parseNullableScriptContinuation(): ScriptContinuationSnapshot? {
        if (matchSymbol('=')) {
            return parseNullable(::parseScriptContinuationExpression)
        }
        return parseScriptContinuationBody()
    }

    private fun parseScriptContinuationExpression(): ScriptContinuationSnapshot {
        expectIdent("continuation")
        return parseScriptContinuationBody()
    }

    private fun parseRandomItemOfferSessionBody(): RandomItemOfferSessionSnapshot {
        expectSymbol('{')
        var offer: RandomItemOfferModel? = null
        var item: ItemId? = null
        var itemName = ""
        var price = 0
        var step: RandomItemOfferSavedStep? = null
        var lastResult: RandomItemOfferResultSnapshot? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "offer" -> offer = parseRandomItemOfferBody()
                "item" -> {
                    expectSymbol('=')
                    item = parseItemId()
                }
                "itemName" -> itemName = parseAssignedString()
                "price" -> price = parseAssignedInt()
                "step" -> step = parseRandomItemOfferStepBody()
                "lastResult" -> lastResult = parseNullableRandomItemOfferResult()
                else -> unexpected("random item offer session field $field")
            }
        }
        return RandomItemOfferSessionSnapshot(
            offer = checkNotNull(offer) { "random item offer session requires offer" },
            item = checkNotNull(item) { "random item offer session requires item" },
            itemName = itemName,
            price = price,
            step = checkNotNull(step) { "random item offer session requires step" },
            lastResult = lastResult,
        )
    }

    private fun parseRandomItemOfferBody(): RandomItemOfferModel {
        expectSymbol('{')
        var pool: ItemPoolId? = null
        var offer: TextBlock? = null
        var choicePrompt: TextBlock? = null
        var broke: TextBlock? = null
        var deal: TextBlock? = null
        var declined: TextBlock? = null
        var application =
            RewardApplicationModel(
                RewardItemPlacement.FirstFreeInventorySlot,
                RewardOverflowPolicy.SkipIfInventoryFull,
                RewardSpecialItemPolicy.ProjectMilestoneState,
            )
        var messages = RewardMessageModel()
        var buy: InputBindingModel? = null
        var decline: InputBindingModel? = null
        var priceOverride: Int? = null
        var brokePauseMillis = 0
        var fullPauseMillis = 0
        var dealPauseMillis = 0
        var declinedPauseMillis = 0
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "pool" -> {
                    expectSymbol('=')
                    pool = parseItemPoolId()
                }
                "offerText" -> offer = parseRequiredTextBlock()
                "choicePrompt" -> choicePrompt = parseRequiredTextBlock()
                "broke" -> broke = parseRequiredTextBlock()
                "deal" -> deal = parseRequiredTextBlock()
                "declined" -> declined = parseNullableTextBlock()
                "application" -> application = parseRewardApplicationBody()
                "messages" -> messages = parseRewardMessagesBody()
                "buy" -> buy = parseInputBindingBody()
                "decline" -> decline = parseInputBindingBody()
                "priceOverride" -> {
                    expectSymbol('=')
                    priceOverride = parseNullable(::parseInt)
                }
                "brokePauseMillis" -> brokePauseMillis = parseAssignedInt()
                "fullPauseMillis" -> fullPauseMillis = parseAssignedInt()
                "dealPauseMillis" -> dealPauseMillis = parseAssignedInt()
                "declinedPauseMillis" -> declinedPauseMillis = parseAssignedInt()
                else -> unexpected("random item offer field $field")
            }
        }
        return RandomItemOfferModel(
            pool = checkNotNull(pool) { "random item offer requires pool" },
            offer = checkNotNull(offer) { "random item offer requires offer text" },
            choicePrompt = checkNotNull(choicePrompt) { "random item offer requires choice prompt" },
            broke = checkNotNull(broke) { "random item offer requires broke text" },
            deal = checkNotNull(deal) { "random item offer requires deal text" },
            declined = declined,
            application = application,
            messages = messages,
            buy = checkNotNull(buy) { "random item offer requires buy binding" },
            decline = checkNotNull(decline) { "random item offer requires decline binding" },
            priceOverride = priceOverride,
            brokePauseMillis = brokePauseMillis,
            fullPauseMillis = fullPauseMillis,
            dealPauseMillis = dealPauseMillis,
            declinedPauseMillis = declinedPauseMillis,
        )
    }

    private fun parseRandomItemOfferStepBody(): RandomItemOfferSavedStep {
        expectSymbol('{')
        val step =
            when (val kind = expectIdentifier()) {
                "choice" -> parseRandomItemOfferChoiceStepBody()
                "dropSlot" -> parseRandomItemOfferDropSlotStepBody()
                "message" -> parseRandomItemOfferMessageStepBody()
                "finished" -> parseRandomItemOfferFinishedStepBody()
                else -> unexpected("random item offer step kind $kind")
            }
        expectSymbol('}')
        return step
    }

    private fun parseRandomItemOfferChoiceStepBody(): RandomItemOfferSavedStep.Choice {
        expectSymbol('{')
        var offerText: TextBlock? = null
        var prompt: TextBlock? = null
        var item: ItemId? = null
        var itemName = ""
        var price = 0
        var buy: InputBindingModel? = null
        var decline: InputBindingModel? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "offerText" -> offerText = parseRequiredTextBlock()
                "prompt" -> prompt = parseRequiredTextBlock()
                "item" -> {
                    expectSymbol('=')
                    item = parseItemId()
                }
                "itemName" -> itemName = parseAssignedString()
                "price" -> price = parseAssignedInt()
                "buy" -> buy = parseInputBindingBody()
                "decline" -> decline = parseInputBindingBody()
                else -> unexpected("random item offer choice step field $field")
            }
        }
        return RandomItemOfferSavedStep.Choice(
            offerText = checkNotNull(offerText) { "random item offer choice requires offerText" },
            prompt = checkNotNull(prompt) { "random item offer choice requires prompt" },
            item = checkNotNull(item) { "random item offer choice requires item" },
            itemName = itemName,
            price = price,
            buy = checkNotNull(buy) { "random item offer choice requires buy binding" },
            decline = checkNotNull(decline) { "random item offer choice requires decline binding" },
        )
    }

    private fun parseRandomItemOfferDropSlotStepBody(): RandomItemOfferSavedStep.DropSlot {
        expectSymbol('{')
        var prompt: TextBlock? = null
        var item: ItemId? = null
        var itemName = ""
        var price = 0
        var result: RandomItemOfferResultSnapshot? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "prompt" -> prompt = parseRequiredTextBlock()
                "item" -> {
                    expectSymbol('=')
                    item = parseItemId()
                }
                "itemName" -> itemName = parseAssignedString()
                "price" -> price = parseAssignedInt()
                "result" -> result = parseRandomItemOfferResultBody()
                else -> unexpected("random item offer drop-slot step field $field")
            }
        }
        return RandomItemOfferSavedStep.DropSlot(
            prompt = checkNotNull(prompt) { "random item offer drop-slot requires prompt" },
            item = checkNotNull(item) { "random item offer drop-slot requires item" },
            itemName = itemName,
            price = price,
            result = checkNotNull(result) { "random item offer drop-slot requires result" },
        )
    }

    private fun parseRandomItemOfferMessageStepBody(): RandomItemOfferSavedStep.Message {
        expectSymbol('{')
        var text: TextBlock? = null
        var next: RandomItemOfferSavedStep? = null
        var result: RandomItemOfferResultSnapshot? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "text" -> text = parseRequiredTextBlock()
                "next" -> next = parseRandomItemOfferStepBody()
                "result" -> result = parseRandomItemOfferResultBody()
                else -> unexpected("random item offer message step field $field")
            }
        }
        return RandomItemOfferSavedStep.Message(
            text = checkNotNull(text) { "random item offer message requires text" },
            next = checkNotNull(next) { "random item offer message requires next step" },
            result = checkNotNull(result) { "random item offer message requires result" },
        )
    }

    private fun parseRandomItemOfferFinishedStepBody(): RandomItemOfferSavedStep.Finished {
        expectSymbol('{')
        var result: RandomItemOfferResultSnapshot? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "result" -> result = parseRandomItemOfferResultBody()
                else -> unexpected("random item offer finished step field $field")
            }
        }
        return RandomItemOfferSavedStep.Finished(checkNotNull(result) { "random item offer finished requires result" })
    }

    private fun parseNullableRandomItemOfferResult(): RandomItemOfferResultSnapshot? {
        if (matchSymbol('=')) {
            return parseNullable(::parseRandomItemOfferResultExpression)
        }
        return parseRandomItemOfferResultBody()
    }

    private fun parseRandomItemOfferResultExpression(): RandomItemOfferResultSnapshot {
        expectIdent("randomItemOfferResult")
        return parseRandomItemOfferResultBody()
    }

    private fun parseRandomItemOfferResultBody(): RandomItemOfferResultSnapshot {
        expectSymbol('{')
        var status = RandomItemOfferStatus.Declined
        var item: ItemId? = null
        var price = 0
        var slot: Int? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "status" -> {
                    expectSymbol('=')
                    status = parseEnum("RandomItemOfferStatus")
                }
                "item" -> {
                    expectSymbol('=')
                    item = parseItemId()
                }
                "price" -> price = parseAssignedInt()
                "slot" -> {
                    expectSymbol('=')
                    slot = parseNullable(::parseInt)
                }
                else -> unexpected("random item offer result field $field")
            }
        }
        return RandomItemOfferResultSnapshot(
            status = status,
            item = checkNotNull(item) { "random item offer result requires item" },
            price = price,
            slot = slot,
        )
    }

    private fun parseShopServiceSessionBody(): ShopServiceSessionSnapshot {
        expectSymbol('{')
        var building: BuildingId? = null
        var step: ShopServiceSavedStep? = null
        var lastTransaction: ShopTransactionResultSnapshot? = null
        var lastSellQuote: ShopSellQuoteResultSnapshot? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "building" -> {
                    expectSymbol('=')
                    building = parseBuildingId()
                }
                "step" -> step = parseShopServiceStepBody()
                "lastTransaction" -> lastTransaction = parseNullableShopTransactionResult()
                "lastSellQuote" -> lastSellQuote = parseNullableShopSellQuoteResult()
                else -> unexpected("shop service session field $field")
            }
        }
        return ShopServiceSessionSnapshot(
            building = checkNotNull(building) { "shop service session requires building" },
            step = checkNotNull(step) { "shop service session requires step" },
            lastTransaction = lastTransaction,
            lastSellQuote = lastSellQuote,
        )
    }

    private fun parseShopServiceStepBody(): ShopServiceSavedStep {
        expectSymbol('{')
        val step =
            when (val kind = expectIdentifier()) {
                "intro" -> parseEmptyShopStep(ShopServiceSavedStep.Intro)
                "mainMenu" -> parseEmptyShopStep(ShopServiceSavedStep.MainMenu)
                "selectCatalog" -> parseEmptyShopStep(ShopServiceSavedStep.SelectCatalog)
                "buyItem" -> parseShopBuyItemStepBody()
                "quantity" -> parseShopQuantityStepBody()
                "confirmPurchase" -> parseShopConfirmPurchaseStepBody()
                "dropSlot" -> parseShopDropSlotStepBody()
                "sellSlot" -> parseEmptyShopStep(ShopServiceSavedStep.SellSlot)
                "confirmSale" -> parseShopConfirmSaleStepBody()
                "finished" -> parseEmptyShopStep(ShopServiceSavedStep.Finished)
                else -> unexpected("shop service step kind $kind")
            }
        expectSymbol('}')
        return step
    }

    private fun parseShopBuyItemStepBody(): ShopServiceSavedStep.BuyItem {
        expectSymbol('{')
        var catalogKeys = emptyList<Char>()
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "catalogKeys" -> catalogKeys = parseCharListBody()
                else -> unexpected("shop buy-item step field $field")
            }
        }
        return ShopServiceSavedStep.BuyItem(catalogKeys)
    }

    private fun parseShopQuantityStepBody(): ShopServiceSavedStep.Quantity {
        expectSymbol('{')
        var item: ItemId? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "item" -> {
                    expectSymbol('=')
                    item = parseItemId()
                }
                else -> unexpected("shop quantity step field $field")
            }
        }
        return ShopServiceSavedStep.Quantity(checkNotNull(item) { "shop quantity step requires item" })
    }

    private fun parseShopConfirmPurchaseStepBody(): ShopServiceSavedStep.ConfirmPurchase {
        expectSymbol('{')
        var item: ItemId? = null
        var quantity = 1
        var unitPrice = 0
        var totalPrice = 0
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "item" -> {
                    expectSymbol('=')
                    item = parseItemId()
                }
                "quantity" -> quantity = parseAssignedInt()
                "unitPrice" -> unitPrice = parseAssignedInt()
                "totalPrice" -> totalPrice = parseAssignedInt()
                else -> unexpected("shop confirm-purchase step field $field")
            }
        }
        return ShopServiceSavedStep.ConfirmPurchase(
            item = checkNotNull(item) { "shop confirm-purchase step requires item" },
            quantity = quantity,
            unitPrice = unitPrice,
            totalPrice = totalPrice,
        )
    }

    private fun parseShopDropSlotStepBody(): ShopServiceSavedStep.DropSlot {
        expectSymbol('{')
        var item: ItemId? = null
        var quantity = 1
        var result: ShopTransactionResultSnapshot? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "item" -> {
                    expectSymbol('=')
                    item = parseItemId()
                }
                "quantity" -> quantity = parseAssignedInt()
                "result" -> result = parseShopTransactionResultBody()
                else -> unexpected("shop drop-slot step field $field")
            }
        }
        return ShopServiceSavedStep.DropSlot(
            item = checkNotNull(item) { "shop drop-slot step requires item" },
            quantity = quantity,
            result = checkNotNull(result) { "shop drop-slot step requires result" },
        )
    }

    private fun parseShopConfirmSaleStepBody(): ShopServiceSavedStep.ConfirmSale {
        expectSymbol('{')
        var quote: ShopSellQuoteResultSnapshot? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "quote" -> quote = parseShopSellQuoteResultBody()
                else -> unexpected("shop confirm-sale step field $field")
            }
        }
        return ShopServiceSavedStep.ConfirmSale(checkNotNull(quote) { "shop confirm-sale step requires quote" })
    }

    private fun parseEmptyShopStep(step: ShopServiceSavedStep): ShopServiceSavedStep {
        expectSymbol('{')
        expectSymbol('}')
        return step
    }

    private fun parseNullableShopTransactionResult(): ShopTransactionResultSnapshot? {
        if (matchSymbol('=')) {
            return parseNullable(::parseShopTransactionResultExpression)
        }
        return parseShopTransactionResultBody()
    }

    private fun parseShopTransactionResultExpression(): ShopTransactionResultSnapshot {
        expectIdent("shopTransactionResult")
        return parseShopTransactionResultBody()
    }

    private fun parseShopTransactionResultBody(): ShopTransactionResultSnapshot {
        expectSymbol('{')
        var status = ShopTransactionStatus.Declined
        var item: ItemId? = null
        var goldDelta = 0
        var slot: Int? = null
        var quantity = 1
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "status" -> {
                    expectSymbol('=')
                    status = parseEnum("ShopTransactionStatus")
                }
                "item" -> {
                    expectSymbol('=')
                    item = parseNullable(::parseItemId)
                }
                "goldDelta" -> goldDelta = parseAssignedInt()
                "slot" -> {
                    expectSymbol('=')
                    slot = parseNullable(::parseInt)
                }
                "quantity" -> quantity = parseAssignedInt()
                else -> unexpected("shop transaction result field $field")
            }
        }
        return ShopTransactionResultSnapshot(status, item, goldDelta, slot, quantity)
    }

    private fun parseNullableShopSellQuoteResult(): ShopSellQuoteResultSnapshot? {
        if (matchSymbol('=')) {
            return parseNullable(::parseShopSellQuoteResultExpression)
        }
        return parseShopSellQuoteResultBody()
    }

    private fun parseShopSellQuoteResultExpression(): ShopSellQuoteResultSnapshot {
        expectIdent("shopSellQuoteResult")
        return parseShopSellQuoteResultBody()
    }

    private fun parseShopSellQuoteResultBody(): ShopSellQuoteResultSnapshot {
        expectSymbol('{')
        var status = ShopSellQuoteStatus.Declined
        var item: ItemId? = null
        var slot: Int? = null
        var price = 0
        var bargainRolls = emptyList<Int>()
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "status" -> {
                    expectSymbol('=')
                    status = parseEnum("ShopSellQuoteStatus")
                }
                "item" -> {
                    expectSymbol('=')
                    item = parseNullable(::parseItemId)
                }
                "slot" -> {
                    expectSymbol('=')
                    slot = parseNullable(::parseInt)
                }
                "price" -> price = parseAssignedInt()
                "bargainRolls" -> bargainRolls = parseIntListBody()
                else -> unexpected("shop sell quote result field $field")
            }
        }
        return ShopSellQuoteResultSnapshot(status, item, slot, price, bargainRolls)
    }

    private fun parseRewardGrantSessionBody(): RewardGrantSessionSnapshot {
        expectSymbol('{')
        var reward: ScriptRewardModel? = null
        var pendingItems = emptyList<ItemId>()
        var step: RewardGrantSavedStep? = null
        var lastResult: RewardGrantResultSnapshot? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "reward" -> reward = parseScriptRewardBody()
                "pendingItems" -> pendingItems = parseItemListBody()
                "step" -> step = parseRewardGrantStepBody()
                "lastResult" -> lastResult = parseNullableRewardGrantResult()
                else -> unexpected("reward grant session field $field")
            }
        }
        return RewardGrantSessionSnapshot(
            reward = checkNotNull(reward) { "reward grant session requires reward" },
            pendingItems = pendingItems,
            step = checkNotNull(step) { "reward grant session requires step" },
            lastResult = lastResult,
        )
    }

    private fun parseScriptRewardBody(): ScriptRewardModel {
        expectSymbol('{')
        var items = emptyList<ItemId>()
        var application =
            RewardApplicationModel(
                RewardItemPlacement.FirstFreeInventorySlot,
                RewardOverflowPolicy.SkipIfInventoryFull,
                RewardSpecialItemPolicy.ProjectMilestoneState,
            )
        var messages = RewardMessageModel()
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "items" -> items = parseItemListBody()
                "application" -> application = parseRewardApplicationBody()
                "messages" -> messages = parseRewardMessagesBody()
                else -> unexpected("script reward field $field")
            }
        }
        return ScriptRewardModel(gold = null, items = items, application = application, messages = messages)
    }

    private fun parseRewardApplicationBody(): RewardApplicationModel {
        expectSymbol('{')
        var regularItems = RewardItemPlacement.FirstFreeInventorySlot
        var overflow = RewardOverflowPolicy.SkipIfInventoryFull
        var specialItems = RewardSpecialItemPolicy.ProjectMilestoneState
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "regularItems" -> {
                    expectSymbol('=')
                    regularItems = parseEnum("RewardItemPlacement")
                }
                "overflow" -> {
                    expectSymbol('=')
                    overflow = parseEnum("RewardOverflowPolicy")
                }
                "specialItems" -> {
                    expectSymbol('=')
                    specialItems = parseEnum("RewardSpecialItemPolicy")
                }
                else -> unexpected("reward application field $field")
            }
        }
        return RewardApplicationModel(regularItems, overflow, specialItems)
    }

    private fun parseRewardMessagesBody(): RewardMessageModel {
        expectSymbol('{')
        var cantCarry: TextBlock? = null
        var pickedUp: TextBlock? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "cantCarry" -> cantCarry = parseNullableTextBlock()
                "pickedUp" -> pickedUp = parseNullableTextBlock()
                else -> unexpected("reward messages field $field")
            }
        }
        return RewardMessageModel(cantCarry, pickedUp)
    }

    private fun parseRewardGrantStepBody(): RewardGrantSavedStep {
        expectSymbol('{')
        val step =
            when (val kind = expectIdentifier()) {
                "dropSlot" -> parseRewardGrantDropSlotStepBody()
                "message" -> parseRewardGrantMessageStepBody()
                "finished" -> parseRewardGrantFinishedStepBody()
                else -> unexpected("reward grant step kind $kind")
            }
        expectSymbol('}')
        return step
    }

    private fun parseRewardGrantDropSlotStepBody(): RewardGrantSavedStep.DropSlot {
        expectSymbol('{')
        var prompt: TextBlock? = null
        var item: ItemId? = null
        var result: RewardGrantResultSnapshot? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "prompt" -> prompt = parseRequiredTextBlock()
                "item" -> {
                    expectSymbol('=')
                    item = parseItemId()
                }
                "result" -> result = parseRewardGrantResultBody()
                else -> unexpected("reward grant drop-slot step field $field")
            }
        }
        return RewardGrantSavedStep.DropSlot(
            prompt = checkNotNull(prompt) { "reward grant drop-slot step requires prompt" },
            item = checkNotNull(item) { "reward grant drop-slot step requires item" },
            result = checkNotNull(result) { "reward grant drop-slot step requires result" },
        )
    }

    private fun parseRewardGrantMessageStepBody(): RewardGrantSavedStep.Message {
        expectSymbol('{')
        var text: TextBlock? = null
        var result: RewardGrantResultSnapshot? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "text" -> text = parseRequiredTextBlock()
                "result" -> result = parseRewardGrantResultBody()
                else -> unexpected("reward grant message step field $field")
            }
        }
        return RewardGrantSavedStep.Message(
            text = checkNotNull(text) { "reward grant message step requires text" },
            result = checkNotNull(result) { "reward grant message step requires result" },
        )
    }

    private fun parseRewardGrantFinishedStepBody(): RewardGrantSavedStep.Finished {
        expectSymbol('{')
        var result: RewardGrantResultSnapshot? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "result" -> result = parseRewardGrantResultBody()
                else -> unexpected("reward grant finished step field $field")
            }
        }
        return RewardGrantSavedStep.Finished(checkNotNull(result) { "reward grant finished step requires result" })
    }

    private fun parseNullableRewardGrantResult(): RewardGrantResultSnapshot? {
        if (matchSymbol('=')) {
            return parseNullable(::parseRewardGrantResultExpression)
        }
        return parseRewardGrantResultBody()
    }

    private fun parseRewardGrantResultExpression(): RewardGrantResultSnapshot {
        expectIdent("rewardGrantResult")
        return parseRewardGrantResultBody()
    }

    private fun parseRewardGrantResultBody(): RewardGrantResultSnapshot {
        expectSymbol('{')
        var status = RewardGrantStatus.Granted
        var item: ItemId? = null
        var slot: Int? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "status" -> {
                    expectSymbol('=')
                    status = parseEnum("RewardGrantStatus")
                }
                "item" -> {
                    expectSymbol('=')
                    item = parseNullable(::parseItemId)
                }
                "slot" -> {
                    expectSymbol('=')
                    slot = parseNullable(::parseInt)
                }
                else -> unexpected("reward grant result field $field")
            }
        }
        return RewardGrantResultSnapshot(status, item, slot)
    }

    private fun parseBattleSessionBody(): BattleSessionSnapshot {
        expectSymbol('{')
        var tier: EncounterTier? = null
        var monster: MonsterId? = null
        var count = 1
        var hitPoints = emptyList<Int>()
        var hitPointMax = 1
        var mandatory = false
        var round: BattleRoundState? = null
        var step: BattleSavedStep? = null
        var lastResult: BattleSessionResultSnapshot? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "tier" -> {
                    expectSymbol('=')
                    tier = parseNullable(::parseEncounterTier)
                }
                "monster" -> {
                    expectSymbol('=')
                    monster = parseMonsterId()
                }
                "count" -> count = parseAssignedInt()
                "hitPoints" -> hitPoints = parseIntListBody()
                "hitPointMax" -> hitPointMax = parseAssignedInt()
                "mandatory" -> mandatory = parseAssignedBoolean()
                "round" -> round = parseBattleRoundBody()
                "step" -> step = parseBattleStepBody()
                "lastResult" -> lastResult = parseBattleResultBody()
                else -> unexpected("battle session field $field")
            }
        }
        return BattleSessionSnapshot(
            tier = tier,
            monster = checkNotNull(monster) { "battle session requires monster" },
            count = count,
            hitPoints = hitPoints,
            hitPointMax = hitPointMax,
            mandatory = mandatory,
            round = checkNotNull(round) { "battle session requires round" },
            step = checkNotNull(step) { "battle session requires step" },
            lastResult = checkNotNull(lastResult) { "battle session requires lastResult" },
        )
    }

    private fun parseBattleRoundBody(): BattleRoundState {
        expectSymbol('{')
        var currentMemberIndex = 0
        var defendCount = 0
        var monsterWeaponSkill = 0
        var monsterConfused = false
        var monstersHeld = false
        var antimagic = false
        var partyHeld = false
        var partyConfused = false
        var actionRetry = false
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "currentMemberIndex" -> currentMemberIndex = parseAssignedInt()
                "defendCount" -> defendCount = parseAssignedInt()
                "monsterWeaponSkill" -> monsterWeaponSkill = parseAssignedInt()
                "monsterConfused" -> monsterConfused = parseAssignedBoolean()
                "monstersHeld" -> monstersHeld = parseAssignedBoolean()
                "antimagic" -> antimagic = parseAssignedBoolean()
                "partyHeld" -> partyHeld = parseAssignedBoolean()
                "partyConfused" -> partyConfused = parseAssignedBoolean()
                "actionRetry" -> actionRetry = parseAssignedBoolean()
                else -> unexpected("battle round field $field")
            }
        }
        return BattleRoundState(
            currentMemberIndex = currentMemberIndex,
            defendCount = defendCount,
            monsterWeaponSkill = monsterWeaponSkill,
            monsterConfused = monsterConfused,
            monstersHeld = monstersHeld,
            antimagic = antimagic,
            partyHeld = partyHeld,
            partyConfused = partyConfused,
            actionRetry = actionRetry,
        )
    }

    private fun parseBattleResultBody(): BattleSessionResultSnapshot {
        expectSymbol('{')
        var status = BattleSessionResultStatus.EncounterStarted
        var monster: MonsterId? = null
        var tier: EncounterTier? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "status" -> {
                    expectSymbol('=')
                    status = parseEnum("BattleSessionResultStatus")
                }
                "monster" -> {
                    expectSymbol('=')
                    monster = parseNullable(::parseMonsterId)
                }
                "tier" -> {
                    expectSymbol('=')
                    tier = parseNullable(::parseEncounterTier)
                }
                else -> unexpected("battle result field $field")
            }
        }
        return BattleSessionResultSnapshot(status, monster, tier)
    }

    private fun parseBattleStepBody(): BattleSavedStep {
        expectSymbol('{')
        val step =
            when (val kind = expectIdentifier()) {
                "opening" -> parseBattleOpeningStepBody()
                "roundOpening" -> parseBattleRoundOpeningStepBody()
                "message" -> parseBattleMessageStepBody()
                "playerAction" -> parseBattlePlayerActionStepBody()
                "monsterTarget" -> parseBattleMonsterTargetStepBody()
                "scriptMonsterTarget" -> parseBattleScriptMonsterTargetStepBody()
                "memberTarget" -> parseBattleMemberTargetStepBody()
                "magicItemSlot" -> parseBattleMagicItemSlotStepBody()
                "spellChoice" -> parseBattleSpellChoiceStepBody()
                "monsterRound" -> parseEmptyBattleStep(BattleSavedStep.MonsterRound)
                "finished" -> parseEmptyBattleStep(BattleSavedStep.Finished)
                else -> unexpected("battle step kind $kind")
            }
        expectSymbol('}')
        return step
    }

    private fun parseBattleOpeningStepBody(): BattleSavedStep.Opening {
        expectSymbol('{')
        var prompt: TextBlock? = null
        var image: ImageId? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "prompt" -> prompt = parseRequiredTextBlock()
                "image" -> {
                    expectSymbol('=')
                    image = parseNullable(::parseImageId)
                }
                else -> unexpected("battle opening step field $field")
            }
        }
        return BattleSavedStep.Opening(checkNotNull(prompt) { "battle opening requires prompt" }, image)
    }

    private fun parseBattleRoundOpeningStepBody(): BattleSavedStep.RoundOpening {
        expectSymbol('{')
        var prompt: TextBlock? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "prompt" -> prompt = parseRequiredTextBlock()
                else -> unexpected("battle round opening step field $field")
            }
        }
        return BattleSavedStep.RoundOpening(checkNotNull(prompt) { "battle round opening requires prompt" })
    }

    private fun parseBattleMessageStepBody(): BattleSavedStep.Message {
        expectSymbol('{')
        var texts = emptyList<TextBlock>()
        var next: BattleSavedStep? = null
        var viewCommands = emptyList<ViewCommand>()
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "texts" -> texts = parseTextBlocksBody()
                "next" -> next = parseBattleStepBody()
                "viewCommands" -> viewCommands = parseViewCommandsBody()
                else -> unexpected("battle message step field $field")
            }
        }
        return BattleSavedStep.Message(texts, checkNotNull(next) { "battle message requires next step" }, viewCommands)
    }

    private fun parseBattlePlayerActionStepBody(): BattleSavedStep.PlayerAction {
        expectSymbol('{')
        var memberIndex = 0
        var prompt: TextBlock? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "memberIndex" -> memberIndex = parseAssignedInt()
                "prompt" -> prompt = parseRequiredTextBlock()
                else -> unexpected("battle player action step field $field")
            }
        }
        return BattleSavedStep.PlayerAction(memberIndex, checkNotNull(prompt) { "battle player action requires prompt" })
    }

    private fun parseBattleMonsterTargetStepBody(): BattleSavedStep.MonsterTarget {
        expectSymbol('{')
        var memberIndex = 0
        var prompt: TextBlock? = null
        var targetKeys = emptyList<Char>()
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "memberIndex" -> memberIndex = parseAssignedInt()
                "prompt" -> prompt = parseRequiredTextBlock()
                "targetKeys" -> targetKeys = parseCharListBody()
                else -> unexpected("battle monster target step field $field")
            }
        }
        return BattleSavedStep.MonsterTarget(
            memberIndex = memberIndex,
            prompt = checkNotNull(prompt) { "battle monster target requires prompt" },
            targetKeys = targetKeys,
        )
    }

    private fun parseBattleScriptMonsterTargetStepBody(): BattleSavedStep.ScriptMonsterTarget {
        expectSymbol('{')
        var prompt: TextBlock? = null
        var targetKeys = emptyList<Char>()
        var script: CombatScriptSnapshot? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "prompt" -> prompt = parseRequiredTextBlock()
                "targetKeys" -> targetKeys = parseCharListBody()
                "script" -> script = parseCombatScriptBody()
                else -> unexpected("battle script monster target step field $field")
            }
        }
        return BattleSavedStep.ScriptMonsterTarget(
            prompt = checkNotNull(prompt) { "battle script monster target requires prompt" },
            targetKeys = targetKeys,
            script = checkNotNull(script) { "battle script monster target requires script" },
        )
    }

    private fun parseBattleMemberTargetStepBody(): BattleSavedStep.MemberTarget {
        expectSymbol('{')
        var prompt: TextBlock? = null
        var targetKeys = emptyList<Char>()
        var script: CombatScriptSnapshot? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "prompt" -> prompt = parseRequiredTextBlock()
                "targetKeys" -> targetKeys = parseCharListBody()
                "script" -> script = parseCombatScriptBody()
                else -> unexpected("battle member target step field $field")
            }
        }
        return BattleSavedStep.MemberTarget(
            prompt = checkNotNull(prompt) { "battle member target requires prompt" },
            targetKeys = targetKeys,
            script = checkNotNull(script) { "battle member target requires script" },
        )
    }

    private fun parseBattleMagicItemSlotStepBody(): BattleSavedStep.MagicItemSlot {
        expectSymbol('{')
        var memberIndex = 0
        var prompt: TextBlock? = null
        var slotKeys = emptyList<Char>()
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "memberIndex" -> memberIndex = parseAssignedInt()
                "prompt" -> prompt = parseRequiredTextBlock()
                "slotKeys" -> slotKeys = parseCharListBody()
                else -> unexpected("battle magic item slot step field $field")
            }
        }
        return BattleSavedStep.MagicItemSlot(
            memberIndex = memberIndex,
            prompt = checkNotNull(prompt) { "battle magic item slot requires prompt" },
            slotKeys = slotKeys,
        )
    }

    private fun parseBattleSpellChoiceStepBody(): BattleSavedStep.SpellChoice {
        expectSymbol('{')
        var memberIndex = 0
        var prompt: TextBlock? = null
        var spellKeys = emptyList<Char>()
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "memberIndex" -> memberIndex = parseAssignedInt()
                "prompt" -> prompt = parseRequiredTextBlock()
                "spellKeys" -> spellKeys = parseCharListBody()
                else -> unexpected("battle spell choice step field $field")
            }
        }
        return BattleSavedStep.SpellChoice(
            memberIndex = memberIndex,
            prompt = checkNotNull(prompt) { "battle spell choice requires prompt" },
            spellKeys = spellKeys,
        )
    }

    private fun parseCombatScriptBody(): CombatScriptSnapshot {
        expectSymbol('{')
        var memberIndex: Int? = null
        var sourceKind = CombatScriptSourceKind.Spell
        var sourceSlug = ""
        var nextIndex = 0
        var pendingVar = ""
        var locals = emptyMap<String, Int>()
        var activeInventorySlot: Int? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "memberIndex" -> {
                    expectSymbol('=')
                    memberIndex = parseNullable(::parseInt)
                }
                "sourceKind" -> {
                    expectSymbol('=')
                    sourceKind = parseEnum("CombatScriptSourceKind")
                }
                "sourceSlug" -> sourceSlug = parseAssignedString()
                "nextIndex" -> nextIndex = parseAssignedInt()
                "pendingVar" -> pendingVar = parseAssignedString()
                "locals" -> locals = parseNamedIntMapBody()
                "activeInventorySlot" -> {
                    expectSymbol('=')
                    activeInventorySlot = parseNullable(::parseInt)
                }
                else -> unexpected("combat script field $field")
            }
        }
        return CombatScriptSnapshot(memberIndex, sourceKind, sourceSlug, nextIndex, pendingVar, locals, activeInventorySlot)
    }

    private fun parseEmptyBattleStep(step: BattleSavedStep): BattleSavedStep {
        expectSymbol('{')
        expectSymbol('}')
        return step
    }

    private fun parseCompiledVmContinuationBody(): CompiledVmContinuationSnapshot {
        expectSymbol('{')
        var root = ScriptRootRef.Unspecified
        var locals = emptyMap<String, Int>()
        var textBindings = emptyMap<String, String>()
        var frames = emptyList<CompiledVmFrameSnapshot>()
        var pending: CompiledVmPendingInputSnapshot? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "root" -> root = parseScriptRootBody()
                "locals" -> locals = parseNamedIntMapBody()
                "textBindings" -> textBindings = parseNamedStringMapBody()
                "frames" -> frames = parseCompiledVmFramesBody()
                "pending" -> pending = parseCompiledVmPendingBody()
                else -> unexpected("compiled VM continuation field $field")
            }
        }
        return CompiledVmContinuationSnapshot(
            root = root,
            locals = locals,
            textBindings = textBindings,
            frames = frames,
            pending = checkNotNull(pending) { "compiled VM continuation requires pending input" },
        )
    }

    private fun parseCompiledVmFramesBody(): List<CompiledVmFrameSnapshot> {
        expectSymbol('{')
        return buildList {
            while (!matchSymbol('}')) {
                expectIdent("frame")
                add(parseCompiledVmFrameBody())
            }
        }
    }

    private fun parseCompiledVmFrameBody(): CompiledVmFrameSnapshot {
        expectSymbol('{')
        var blockPath = emptyList<Int>()
        var nextIndex = 0
        var insideLoop = false
        var kind = CompiledVmFrameKind.Block
        var enclosingInsideLoop = false
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "blockPath" -> blockPath = parseIntListBody()
                "nextIndex" -> nextIndex = parseAssignedInt()
                "insideLoop" -> insideLoop = parseAssignedBoolean()
                "kind" -> {
                    expectSymbol('=')
                    kind = parseEnum("CompiledVmFrameKind")
                }
                "enclosingInsideLoop" -> enclosingInsideLoop = parseAssignedBoolean()
                else -> unexpected("compiled VM frame field $field")
            }
        }
        return CompiledVmFrameSnapshot(
            blockPath = blockPath,
            nextIndex = nextIndex,
            insideLoop = insideLoop,
            kind = kind,
            enclosingInsideLoop = enclosingInsideLoop,
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

    private fun parseNamedStringMapBody(): Map<String, String> {
        expectSymbol('{')
        return buildMap {
            while (!matchSymbol('}')) {
                expectIdent("value")
                expectSymbol('(')
                val key = parseString()
                expectSymbol(',')
                val value = parseString()
                expectSymbol(')')
                put(key, value)
            }
        }
    }

    private fun parseIntListBody(): List<Int> {
        expectSymbol('{')
        return buildList {
            while (!matchSymbol('}')) {
                expectIdent("value")
                expectSymbol('(')
                add(parseInt())
                expectSymbol(')')
            }
        }
    }

    private fun parseCharListBody(): List<Char> {
        expectSymbol('{')
        return buildList {
            while (!matchSymbol('}')) {
                expectIdent("value")
                expectSymbol('(')
                add(parseChar())
                expectSymbol(')')
            }
        }
    }

    private fun parseStringListBody(): List<String> {
        expectSymbol('{')
        return buildList {
            while (!matchSymbol('}')) {
                expectIdent("value")
                expectSymbol('(')
                add(parseString())
                expectSymbol(')')
            }
        }
    }

    private fun parseItemListBody(): List<ItemId> {
        expectSymbol('{')
        return buildList {
            while (!matchSymbol('}')) {
                expectIdent("value")
                expectSymbol('(')
                add(parseItemId())
                expectSymbol(')')
            }
        }
    }

    private fun parseCompiledHandleListBody(): List<CompiledHandleRef> {
        expectSymbol('{')
        return buildList {
            while (!matchSymbol('}')) {
                add(parseCompiledHandleRef())
            }
        }
    }

    private fun parseCompiledHandleRef(): CompiledHandleRef {
        expectIdent("handle")
        expectSymbol('(')
        val kind = parseEnum<CompiledHandleKind>("CompiledHandleKind")
        expectSymbol(',')
        val denseId = parseInt()
        expectSymbol(')')
        return CompiledHandleRef(kind, denseId)
    }

    private fun parseTextBlocksBody(): List<TextBlock> {
        expectSymbol('{')
        return buildList {
            while (!matchSymbol('}')) {
                expectIdent("text")
                add(parseRequiredTextBlock())
            }
        }
    }

    private fun parseViewCommandsBody(): List<ViewCommand> {
        expectSymbol('{')
        return buildList {
            while (!matchSymbol('}')) {
                when (val command = expectIdentifier()) {
                    "clearText" -> add(parseEmptyViewCommand(ViewCommand.ClearText))
                    "clearMapWindow" -> add(parseEmptyViewCommand(ViewCommand.ClearMapWindow))
                    "renderMap" -> add(parseEmptyViewCommand(ViewCommand.RenderMap))
                    "statusLine" -> add(parseEmptyViewCommand(ViewCommand.StatusLine))
                    "showImage" -> {
                        expectSymbol('(')
                        add(ViewCommand.ShowImage(parseImageId()))
                        expectSymbol(')')
                    }
                    else -> unexpected("view command $command")
                }
            }
        }
    }

    private fun parseEmptyViewCommand(command: ViewCommand): ViewCommand {
        expectSymbol('(')
        expectSymbol(')')
        return command
    }

    private fun parseCompiledVmPendingBody(): CompiledVmPendingInputSnapshot {
        expectSymbol('{')
        val pending =
            when (val kind = expectIdentifier()) {
                "waitInput" -> parseCompiledVmWaitInputBody()
                "menu" -> parseCompiledVmMenuBody()
                "askKey" -> parseCompiledVmAskKeyBody()
                "askMember" -> parseCompiledVmAskMemberBody()
                "askMonster" -> parseCompiledVmAskMonsterBody()
                "askInventorySlot" -> parseCompiledVmAskInventorySlotBody()
                "askText" -> parseCompiledVmAskTextBody()
                "childSession" -> parseCompiledVmChildSessionBody()
                else -> unexpected("compiled VM pending kind $kind")
            }
        expectSymbol('}')
        return pending
    }

    private fun parseCompiledVmWaitInputBody(): CompiledVmPendingInputSnapshot.WaitInput {
        expectSymbol('{')
        val header = MutableCompiledVmPendingHeader()
        var reason: String? = null
        var prompt: TextBlock? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "root",
                "instructionIndex",
                "operandPath",
                -> header.parseField(field)
                "reason" -> {
                    expectSymbol('=')
                    reason = parseNullable(::parseString)
                }
                "prompt" -> prompt = parseNullableTextBlock()
                else -> unexpected("compiled VM waitInput field $field")
            }
        }
        return CompiledVmPendingInputSnapshot.WaitInput(header.root, header.instructionIndex, header.operandPath, reason, prompt)
    }

    private fun parseCompiledVmMenuBody(): CompiledVmPendingInputSnapshot.Menu {
        expectSymbol('{')
        val header = MutableCompiledVmPendingHeader()
        var prompt: TextBlock? = null
        var options = emptyList<CompiledVmMenuOptionSnapshot>()
        var hasDefault = false
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "root",
                "instructionIndex",
                "operandPath",
                -> header.parseField(field)
                "prompt" -> prompt = parseRequiredTextBlock()
                "options" -> options = parseCompiledVmMenuOptionsBody()
                "hasDefault" -> hasDefault = parseAssignedBoolean()
                else -> unexpected("compiled VM menu field $field")
            }
        }
        return CompiledVmPendingInputSnapshot.Menu(
            root = header.root,
            instructionIndex = header.instructionIndex,
            operandPath = header.operandPath,
            prompt = checkNotNull(prompt) { "compiled VM menu pending requires prompt" },
            options = options,
            hasDefault = hasDefault,
        )
    }

    private fun parseCompiledVmAskKeyBody(): CompiledVmPendingInputSnapshot.AskKey {
        expectSymbol('{')
        val header = MutableCompiledVmPendingHeader()
        var prompt: TextBlock? = null
        var localName = ""
        var bindings = emptyList<InputBindingModel>()
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "root",
                "instructionIndex",
                "operandPath",
                -> header.parseField(field)
                "prompt" -> prompt = parseRequiredTextBlock()
                "localName" -> localName = parseAssignedString()
                "bindings" -> bindings = parseInputBindingsBody()
                else -> unexpected("compiled VM askKey field $field")
            }
        }
        return CompiledVmPendingInputSnapshot.AskKey(
            header.root,
            header.instructionIndex,
            header.operandPath,
            checkNotNull(prompt) { "compiled VM askKey pending requires prompt" },
            localName,
            bindings,
        )
    }

    private fun parseCompiledVmAskMemberBody(): CompiledVmPendingInputSnapshot.AskMember {
        expectSymbol('{')
        val header = MutableCompiledVmPendingHeader()
        var prompt: TextBlock? = null
        var localName = ""
        var filter = MemberChoiceFilter.Any
        var accepting = MemberChoiceFilter.Any
        var choices = emptyList<Int>()
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "root",
                "instructionIndex",
                "operandPath",
                -> header.parseField(field)
                "prompt" -> prompt = parseRequiredTextBlock()
                "localName" -> localName = parseAssignedString()
                "filter" -> {
                    expectSymbol('=')
                    filter = parseEnum("MemberChoiceFilter")
                }
                "accepting" -> {
                    expectSymbol('=')
                    accepting = parseEnum("MemberChoiceFilter")
                }
                "choices" -> choices = parseIntListBody()
                else -> unexpected("compiled VM askMember field $field")
            }
        }
        return CompiledVmPendingInputSnapshot.AskMember(
            header.root,
            header.instructionIndex,
            header.operandPath,
            checkNotNull(prompt) { "compiled VM askMember pending requires prompt" },
            localName,
            filter,
            accepting,
            choices,
        )
    }

    private fun parseCompiledVmAskMonsterBody(): CompiledVmPendingInputSnapshot.AskMonster {
        expectSymbol('{')
        val header = MutableCompiledVmPendingHeader()
        var prompt: TextBlock? = null
        var localName = ""
        var targetKeys = emptyList<Char>()
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "root",
                "instructionIndex",
                "operandPath",
                -> header.parseField(field)
                "prompt" -> prompt = parseRequiredTextBlock()
                "localName" -> localName = parseAssignedString()
                "targetKeys" -> targetKeys = parseCharListBody()
                else -> unexpected("compiled VM askMonster field $field")
            }
        }
        return CompiledVmPendingInputSnapshot.AskMonster(
            header.root,
            header.instructionIndex,
            header.operandPath,
            checkNotNull(prompt) { "compiled VM askMonster pending requires prompt" },
            localName,
            targetKeys,
        )
    }

    private fun parseCompiledVmAskInventorySlotBody(): CompiledVmPendingInputSnapshot.AskInventorySlot {
        expectSymbol('{')
        val header = MutableCompiledVmPendingHeader()
        var prompt: TextBlock? = null
        var localName = ""
        var firstKey = 'A'
        var firstSlot = 0
        var lastSlot = 0
        var filter = InventorySlotChoiceFilter.Any
        var choices = emptyList<CompiledVmInventorySlotChoiceSnapshot>()
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "root",
                "instructionIndex",
                "operandPath",
                -> header.parseField(field)
                "prompt" -> prompt = parseRequiredTextBlock()
                "localName" -> localName = parseAssignedString()
                "firstKey" -> {
                    expectSymbol('=')
                    firstKey = parseChar()
                }
                "firstSlot" -> firstSlot = parseAssignedInt()
                "lastSlot" -> lastSlot = parseAssignedInt()
                "filter" -> {
                    expectSymbol('=')
                    filter = parseEnum("InventorySlotChoiceFilter")
                }
                "choices" -> choices = parseCompiledVmInventoryChoicesBody()
                else -> unexpected("compiled VM askInventorySlot field $field")
            }
        }
        return CompiledVmPendingInputSnapshot.AskInventorySlot(
            root = header.root,
            instructionIndex = header.instructionIndex,
            operandPath = header.operandPath,
            prompt = checkNotNull(prompt) { "compiled VM askInventorySlot pending requires prompt" },
            localName = localName,
            firstKey = firstKey,
            firstSlot = firstSlot,
            lastSlot = lastSlot,
            filter = filter,
            choices = choices,
        )
    }

    private fun parseCompiledVmAskTextBody(): CompiledVmPendingInputSnapshot.AskText {
        expectSymbol('{')
        val header = MutableCompiledVmPendingHeader()
        var prompt: TextBlock? = null
        var accepted = emptyList<String>()
        var caseSensitive = false
        var trim = true
        var maxLength: Int? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "root",
                "instructionIndex",
                "operandPath",
                -> header.parseField(field)
                "prompt" -> prompt = parseRequiredTextBlock()
                "accepted" -> accepted = parseStringListBody()
                "caseSensitive" -> caseSensitive = parseAssignedBoolean()
                "trim" -> trim = parseAssignedBoolean()
                "maxLength" -> {
                    expectSymbol('=')
                    maxLength = parseNullable(::parseInt)
                }
                else -> unexpected("compiled VM askText field $field")
            }
        }
        return CompiledVmPendingInputSnapshot.AskText(
            header.root,
            header.instructionIndex,
            header.operandPath,
            checkNotNull(prompt) { "compiled VM askText pending requires prompt" },
            accepted,
            caseSensitive,
            trim,
            maxLength,
        )
    }

    private fun parseCompiledVmChildSessionBody(): CompiledVmPendingInputSnapshot.ChildSession {
        expectSymbol('{')
        val header = MutableCompiledVmPendingHeader()
        var request: CompiledVmChildRequestSnapshot? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "root",
                "instructionIndex",
                "operandPath",
                -> header.parseField(field)
                "request" -> request = parseCompiledVmChildRequestBody()
                else -> unexpected("compiled VM childSession field $field")
            }
        }
        return CompiledVmPendingInputSnapshot.ChildSession(
            header.root,
            header.instructionIndex,
            header.operandPath,
            checkNotNull(request) { "compiled VM childSession pending requires request" },
        )
    }

    private inner class MutableCompiledVmPendingHeader {
        var root: ScriptRootRef = ScriptRootRef.Unspecified
        var instructionIndex: Int = 0
        var operandPath: List<Int> = emptyList()

        fun parseField(field: String) {
            when (field) {
                "root" -> root = parseScriptRootBody()
                "instructionIndex" -> instructionIndex = parseAssignedInt()
                "operandPath" -> operandPath = parseIntListBody()
                else -> unexpected("compiled VM pending header field $field")
            }
        }
    }

    private fun parseCompiledVmMenuOptionsBody(): List<CompiledVmMenuOptionSnapshot> {
        expectSymbol('{')
        return buildList {
            while (!matchSymbol('}')) {
                expectIdent("option")
                expectSymbol('(')
                val key = parseChar()
                expectSymbol(',')
                val optionIndex = parseInt()
                expectSymbol(')')
                add(CompiledVmMenuOptionSnapshot(key, optionIndex))
            }
        }
    }

    private fun parseCompiledVmInventoryChoicesBody(): List<CompiledVmInventorySlotChoiceSnapshot> {
        expectSymbol('{')
        return buildList {
            while (!matchSymbol('}')) {
                expectIdent("choice")
                expectSymbol('(')
                val key = parseChar()
                expectSymbol(',')
                val slot = parseInt()
                expectSymbol(')')
                add(CompiledVmInventorySlotChoiceSnapshot(key, slot))
            }
        }
    }

    private fun parseInputBindingsBody(): List<InputBindingModel> {
        expectSymbol('{')
        return buildList {
            while (!matchSymbol('}')) {
                expectIdent("binding")
                add(parseInputBindingFieldsBody())
            }
        }
    }

    private fun parseInputBindingBody(): InputBindingModel {
        expectSymbol('{')
        return parseInputBindingFields()
    }

    private fun parseInputBindingFieldsBody(): InputBindingModel {
        expectSymbol('{')
        return parseInputBindingFields()
    }

    private fun parseInputBindingFields(): InputBindingModel {
        var primary = ""
        var aliases = emptyList<String>()
        var label = ""
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "primary" -> primary = parseAssignedString()
                "aliases" -> aliases = parseStringListBody()
                "label" -> label = parseAssignedString()
                else -> unexpected("input binding field $field")
            }
        }
        return InputBindingModel(primary = primary, aliases = aliases, label = label.ifEmpty { primary })
    }

    private fun parseCompiledVmChildRequestBody(): CompiledVmChildRequestSnapshot {
        expectSymbol('{')
        val request =
            when (val kind = expectIdentifier()) {
                "speak" -> parseCompiledVmSpeakRequestBody()
                "tellRumour" -> parseCompiledVmTellRumourRequestBody()
                "encounter" -> parseCompiledVmEncounterRequestBody()
                "enterDungeon" -> parseCompiledVmEnterDungeonRequestBody()
                "grantReward" -> parseCompiledVmGrantRewardRequestBody()
                "randomItemOffer" -> parseCompiledVmRandomItemOfferRequestBody()
                "shopService" -> parseCompiledVmShopServiceRequestBody()
                else -> unexpected("compiled VM child request kind $kind")
            }
        expectSymbol('}')
        return request
    }

    private fun parseCompiledVmSpeakRequestBody(): CompiledVmChildRequestSnapshot.Speak {
        expectSymbol('{')
        var building: BuildingId? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "building" -> {
                    expectSymbol('=')
                    building = parseBuildingId()
                }
                else -> unexpected("compiled VM speak request field $field")
            }
        }
        return CompiledVmChildRequestSnapshot.Speak(checkNotNull(building) { "compiled VM speak request requires building" })
    }

    private fun parseCompiledVmTellRumourRequestBody(): CompiledVmChildRequestSnapshot.TellRumour {
        expectSymbol('{')
        var table: RumourTableId? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "table" -> {
                    expectSymbol('=')
                    table = parseRumourTableId()
                }
                else -> unexpected("compiled VM tellRumour request field $field")
            }
        }
        return CompiledVmChildRequestSnapshot.TellRumour(checkNotNull(table) { "compiled VM rumour request requires table" })
    }

    private fun parseCompiledVmEncounterRequestBody(): CompiledVmChildRequestSnapshot.Encounter {
        expectSymbol('{')
        var monster: MonsterId? = null
        var mandatory = false
        var tier: String? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "monster" -> {
                    expectSymbol('=')
                    monster = parseMonsterId()
                }
                "mandatory" -> mandatory = parseAssignedBoolean()
                "tier" -> {
                    expectSymbol('=')
                    tier = parseNullable(::parseString)
                }
                else -> unexpected("compiled VM encounter request field $field")
            }
        }
        return CompiledVmChildRequestSnapshot.Encounter(
            monster = checkNotNull(monster) { "compiled VM encounter request requires monster" },
            mandatory = mandatory,
            tier = tier,
        )
    }

    private fun parseCompiledVmEnterDungeonRequestBody(): CompiledVmChildRequestSnapshot.EnterDungeon {
        expectSymbol('{')
        var dungeon: DungeonId? = null
        var levels = 1
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "dungeon" -> {
                    expectSymbol('=')
                    dungeon = parseDungeonId()
                }
                "levels" -> levels = parseAssignedInt()
                else -> unexpected("compiled VM enterDungeon request field $field")
            }
        }
        return CompiledVmChildRequestSnapshot.EnterDungeon(
            WildernessDungeonHandoff(
                dungeon = checkNotNull(dungeon) { "compiled VM dungeon request requires dungeon" },
                levels = levels,
            ),
        )
    }

    private fun parseCompiledVmGrantRewardRequestBody(): CompiledVmChildRequestSnapshot.GrantReward {
        expectSymbol('{')
        var gold = 0
        var items = emptyList<CompiledHandleRef>()
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "gold" -> gold = parseAssignedInt()
                "items" -> items = parseCompiledHandleListBody()
                else -> unexpected("compiled VM grantReward request field $field")
            }
        }
        return CompiledVmChildRequestSnapshot.GrantReward(
            reward =
                CompiledScriptReward(
                    gold = null,
                    items = items,
                    application =
                        RewardApplicationModel(
                            RewardItemPlacement.FirstFreeInventorySlot,
                            RewardOverflowPolicy.SkipIfInventoryFull,
                            RewardSpecialItemPolicy.ProjectMilestoneState,
                        ),
                    messages = CompiledRewardMessages(cantCarry = null, pickedUp = null),
                ),
            gold = gold,
        )
    }

    private fun parseCompiledVmRandomItemOfferRequestBody(): CompiledVmChildRequestSnapshot.RandomItemOffer {
        expectSymbol('{')
        var pool: CompiledHandleRef? = null
        var selectedIndex = 0
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "pool" -> {
                    expectSymbol('=')
                    pool = parseCompiledHandleRef()
                }
                "selectedIndex" -> selectedIndex = parseAssignedInt()
                else -> unexpected("compiled VM randomItemOffer request field $field")
            }
        }
        return CompiledVmChildRequestSnapshot.RandomItemOffer(
            offer = dummyCompiledRandomItemOffer(checkNotNull(pool) { "compiled VM random offer request requires pool" }),
            selectedIndex = selectedIndex,
        )
    }

    private fun parseCompiledVmShopServiceRequestBody(): CompiledVmChildRequestSnapshot.ShopService {
        expectSymbol('{')
        var building: CompiledHandleRef? = null
        while (!matchSymbol('}')) {
            when (val field = expectIdentifier()) {
                "building" -> {
                    expectSymbol('=')
                    building = parseCompiledHandleRef()
                }
                else -> unexpected("compiled VM shopService request field $field")
            }
        }
        return CompiledVmChildRequestSnapshot.ShopService(
            dummyCompiledShopService(checkNotNull(building) { "compiled VM shop request requires building" }),
        )
    }

    private fun dummyCompiledRandomItemOffer(pool: CompiledHandleRef): CompiledRandomItemOffer =
        CompiledRandomItemOffer(
            pool = pool,
            offer = EmptyCompiledTextBlock,
            choicePrompt = EmptyCompiledTextBlock,
            broke = EmptyCompiledTextBlock,
            deal = EmptyCompiledTextBlock,
            declined = null,
            application =
                RewardApplicationModel(
                    RewardItemPlacement.FirstFreeInventorySlot,
                    RewardOverflowPolicy.SkipIfInventoryFull,
                    RewardSpecialItemPolicy.ProjectMilestoneState,
                ),
            messages = CompiledRewardMessages(cantCarry = null, pickedUp = null),
            buy = EmptyCompiledInputBinding,
            decline = EmptyCompiledInputBinding,
            priceOverride = null,
            pauseMillis = CompiledOfferPauses(broke = 0, full = 0, deal = 0, declined = 0),
        )

    private fun dummyCompiledShopService(building: CompiledHandleRef): CompiledShopService =
        CompiledShopService(
            building = building,
            image = CompiledHandleRef(CompiledHandleKind.Image, 0),
            closed = EmptyCompiledTextBlock,
            intro = EmptyCompiledTextBlock,
            menu = EmptyCompiledTextBlock,
            keys =
                CompiledShopMenuKeys(
                    buy = EmptyCompiledInputBinding,
                    sell = EmptyCompiledInputBinding,
                    leave = EmptyCompiledInputBinding,
                ),
            goodbye = EmptyCompiledTextBlock,
            buy = null,
            sell = null,
            interlude = null,
        )

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

    private fun parseNullableTextBlock(): TextBlock? {
        if (matchSymbol('=')) {
            return parseNullable(::parseTextBlockExpression)
        }
        return parseTextBlockBody()
    }

    private fun parseRequiredTextBlock(): TextBlock = parseTextBlockBody()

    private fun parseTextBlockExpression(): TextBlock {
        expectIdent("textBlock")
        return parseTextBlockBody()
    }

    private fun parseTextBlockBody(): TextBlock {
        expectSymbol('{')
        val block =
            when (val kind = expectIdentifier()) {
                "ref" -> parseTextBlockRef()
                "inline" -> parseTextBlockInline()
                "structured" -> parseTextBlockStructured()
                else -> unexpected("text block kind $kind")
            }
        expectSymbol('}')
        return block
    }

    private fun parseTextBlockRef(): TextBlock.Ref {
        expectSymbol('=')
        return TextBlock.Ref(parseTextId())
    }

    private fun parseTextBlockInline(): TextBlock.Inline {
        expectSymbol('{')
        return TextBlock.Inline(
            buildList {
                while (!matchSymbol('}')) {
                    expectIdent("line")
                    expectSymbol('(')
                    add(parseString())
                    expectSymbol(')')
                }
            },
        )
    }

    private fun parseTextBlockStructured(): TextBlock.Structured {
        expectSymbol('{')
        return TextBlock.Structured(
            buildList {
                while (!matchSymbol('}')) {
                    expectIdent("line")
                    add(parseTextLineBody())
                }
            },
        )
    }

    private fun parseTextLineBody(): TextLine {
        expectSymbol('{')
        return TextLine(
            buildList {
                while (!matchSymbol('}')) {
                    add(parseTextPart())
                }
            },
        )
    }

    private fun parseTextPart(): TextPart =
        when (val kind = expectIdentifier()) {
            "literal" -> {
                expectSymbol('(')
                val value = parseString()
                expectSymbol(')')
                TextPart.Literal(value)
            }
            "roleName" -> {
                expectSymbol('(')
                val role = parseNpcRole()
                expectSymbol(')')
                TextPart.RoleName(role)
            }
            "roleLocation" -> {
                expectSymbol('(')
                val role = parseNpcRole()
                expectSymbol(')')
                TextPart.RoleLocation(role)
            }
            "townName" -> {
                expectSymbol('(')
                val town = parseTownId()
                expectSymbol(')')
                TextPart.TownName(town)
            }
            "fuzzyTownLocation" -> {
                expectSymbol('(')
                val town = parseTownId()
                expectSymbol(',')
                val radius = parseInt()
                expectSymbol(')')
                TextPart.FuzzyTownLocation(town, radius)
            }
            "locationCoordinate" -> {
                expectSymbol('(')
                val location = parseLocationId()
                expectSymbol(')')
                TextPart.LocationCoordinate(location)
            }
            "token" -> {
                expectSymbol('(')
                val tokenName = parseString()
                expectSymbol(')')
                parseTextPartToken(tokenName)
            }
            else -> unexpected("text part $kind")
        }

    private fun parseTextPartToken(tokenName: String): TextPart =
        when (tokenName) {
            "OfferedItemName" -> TextPart.OfferedItemName
            "OfferedItemPrice" -> TextPart.OfferedItemPrice
            "SelectedItemName" -> TextPart.SelectedItemName
            "ShopPrice" -> TextPart.ShopPrice
            "ShopCarryCap" -> TextPart.ShopCarryCap
            "HuntMemberName" -> TextPart.HuntMemberName
            "HuntMemberSubjectPronoun" -> TextPart.HuntMemberSubjectPronoun
            "HuntFoodGained" -> TextPart.HuntFoodGained
            "PartyMemberMax" -> TextPart.PartyMemberMax
            "WildernessDungeonName" -> TextPart.WildernessDungeonName
            "WildernessDungeonLevels" -> TextPart.WildernessDungeonLevels
            "DungeonDepth" -> TextPart.DungeonDepth
            "ConversationNpcName" -> TextPart.ConversationNpcName
            "CurrentTownName" -> TextPart.CurrentTownName
            "TownTaxPercent" -> TextPart.TownTaxPercent
            "TownTaxAmount" -> TextPart.TownTaxAmount
            "CreationMemberNumber" -> TextPart.CreationMemberNumber
            "CreationMemberName" -> TextPart.CreationMemberName
            "CreationWeaponSkill" -> TextPart.CreationWeaponSkill
            "CreationHealth" -> TextPart.CreationHealth
            "CreationWisdom" -> TextPart.CreationWisdom
            "CreationCharisma" -> TextPart.CreationCharisma
            else -> unexpected("text part token $tokenName")
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

    private fun parseCastleId(): CastleId = parseStringCall("castle", ::castle)

    private fun parseLocationId(): LocationId = parseStringCall("location", ::location)

    private fun parseNpcId(): NpcId = parseStringCall("npc", ::npc)

    private fun parseNpcRole(): NpcRole = parseStringCall("npcRole", ::npcRole)

    private fun parseItemId(): ItemId = parseStringCall("item", ::item)

    private fun parseItemPoolId(): ItemPoolId = parseStringCall("itemPool", ::itemPool)

    private fun parseMilestoneId(): GameMilestoneId = parseStringCall("milestone", ::milestone)

    private fun parseMonsterId(): MonsterId = parseStringCall("monster", ::monster)

    private fun parseEncounterTier(): EncounterTier = parseStringCall("encounterTier", ::encounterTier)

    private fun parseImageId(): ImageId = parseStringCall("image", ::image)

    private fun parseRumourTableId(): RumourTableId = parseStringCall("rumourTable", ::rumourTable)

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
            line("compiledBundleHash = ${metadata.compiledBundleHash?.let(::quote) ?: "null"}")
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
                is RuntimeModeSnapshot.Castle -> castle(mode.session)
                is RuntimeModeSnapshot.Dungeon -> dungeon(mode)
            }
        }
        line("}")
    }

    private fun castle(session: CastleSessionSnapshot) {
        line("castle {")
        indented {
            line("castle = ${castleId(session.castle)}")
            assignment("floor", session.floor)
            line("point = ${point(session.point)}")
            line("step = CastleSavedStep.${session.step.name}")
            line("treasureItem = ${session.treasureItem?.let(::itemId) ?: "null"}")
            line("scriptScene = ${session.scriptScene?.let(::sceneId) ?: "null"}")
            line("scriptNext = ${session.scriptNext?.let { next -> "CastleScriptContinuation.${next.name}" } ?: "null"}")
            session.scriptContinuation?.let { continuation ->
                line("scriptContinuation {")
                indented {
                    scriptContinuationBody(continuation)
                }
                line("}")
            }
            line("rooms {")
            indented {
                session.rooms.entries
                    .sortedWith(compareBy({ it.key.x }, { it.key.y }))
                    .forEach { (roomPoint, event) ->
                        line("room(${point(roomPoint)}, CastleRoomEvent.${event.name})")
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
            scriptContinuationBody(continuation)
        }
        line("}")
    }

    private fun scriptContinuationBody(continuation: ScriptContinuationSnapshot) {
        scriptRoot(continuation.root)
        line("activeInventorySlot = ${continuation.activeInventorySlot ?: "null"}")
        namedIntMap("locals", continuation.locals)
        namedIntMap("realmScalars", continuation.realmScalars)
        line("stack {")
        indented {
            continuation.stack.forEach(::scriptFrame)
        }
        line("}")
        continuation.speakSession?.let(::speakSession)
        continuation.battleSession?.let(::battleSession)
        continuation.randomItemOfferSession?.let(::randomItemOfferSession)
        continuation.rewardGrantSession?.let(::rewardGrantSession)
        continuation.shopServiceSession?.let(::shopServiceSession)
        continuation.compiledVmContinuation?.let(::compiledVmContinuation)
    }

    private fun speakSession(session: SpeakSessionSnapshot) {
        line("speakSession {")
        indented {
            line("building = ${buildingId(session.building)}")
            line("selectedNpc = ${session.selectedNpc?.let(::npcId) ?: "null"}")
            assignment("rumourTold", session.rumourTold)
            speakStep(session.step)
        }
        line("}")
    }

    private fun speakStep(step: SpeakSavedStep) {
        line("step {")
        indented {
            when (step) {
                SpeakSavedStep.ChooseNpc -> emptySpeakStep("chooseNpc")
                is SpeakSavedStep.ScriptBranch -> speakScriptBranchStep(step)
                is SpeakSavedStep.MainMenu -> speakNpcStep("mainMenu", step.npc)
                is SpeakSavedStep.QuestionMenu -> speakNpcStep("questionMenu", step.npc)
                is SpeakSavedStep.Message -> speakMessageStep(step)
                SpeakSavedStep.Finished -> emptySpeakStep("finished")
            }
        }
        line("}")
    }

    private fun speakScriptBranchStep(step: SpeakSavedStep.ScriptBranch) {
        line("scriptBranch {")
        indented {
            line("npc = ${npcId(step.npc)}")
            scriptRoot(step.root)
            line("next {")
            indented {
                when (val next = step.next) {
                    SpeakSavedStep.ChooseNpc -> emptySpeakStep("chooseNpc")
                    is SpeakSavedStep.ScriptBranch -> speakScriptBranchStep(next)
                    is SpeakSavedStep.MainMenu -> speakNpcStep("mainMenu", next.npc)
                    is SpeakSavedStep.QuestionMenu -> speakNpcStep("questionMenu", next.npc)
                    is SpeakSavedStep.Message -> speakMessageStep(next)
                    SpeakSavedStep.Finished -> emptySpeakStep("finished")
                }
            }
            line("}")
            step.continuation?.let { continuation ->
                line("continuation {")
                indented {
                    scriptContinuationBody(continuation)
                }
                line("}")
            } ?: line("continuation = null")
        }
        line("}")
    }

    private fun speakNpcStep(
        name: String,
        npc: NpcId,
    ) {
        line("$name {")
        indented {
            line("npc = ${npcId(npc)}")
        }
        line("}")
    }

    private fun speakMessageStep(step: SpeakSavedStep.Message) {
        line("message {")
        indented {
            textBlocks("blocks", step.blocks)
            line("next {")
            indented {
                when (val next = step.next) {
                    SpeakSavedStep.ChooseNpc -> emptySpeakStep("chooseNpc")
                    is SpeakSavedStep.ScriptBranch -> speakScriptBranchStep(next)
                    is SpeakSavedStep.MainMenu -> speakNpcStep("mainMenu", next.npc)
                    is SpeakSavedStep.QuestionMenu -> speakNpcStep("questionMenu", next.npc)
                    is SpeakSavedStep.Message -> speakMessageStep(next)
                    SpeakSavedStep.Finished -> emptySpeakStep("finished")
                }
            }
            line("}")
        }
        line("}")
    }

    private fun emptySpeakStep(name: String) {
        line("$name {")
        line("}")
    }

    private fun randomItemOfferSession(session: RandomItemOfferSessionSnapshot) {
        line("randomItemOfferSession {")
        indented {
            randomItemOffer(session.offer)
            line("item = ${itemId(session.item)}")
            assignment("itemName", session.itemName)
            assignment("price", session.price)
            randomItemOfferStep(session.step)
            nullableRandomItemOfferResult("lastResult", session.lastResult)
        }
        line("}")
    }

    private fun randomItemOffer(offer: RandomItemOfferModel) {
        line("offer {")
        indented {
            line("pool = ${itemPoolId(offer.pool)}")
            textBlock("offerText", offer.offer)
            textBlock("choicePrompt", offer.choicePrompt)
            textBlock("broke", offer.broke)
            textBlock("deal", offer.deal)
            nullableTextBlock("declined", offer.declined)
            rewardApplication(offer.application)
            rewardMessages(offer.messages)
            inputBinding("buy", offer.buy)
            inputBinding("decline", offer.decline)
            line("priceOverride = ${offer.priceOverride ?: "null"}")
            assignment("brokePauseMillis", offer.brokePauseMillis)
            assignment("fullPauseMillis", offer.fullPauseMillis)
            assignment("dealPauseMillis", offer.dealPauseMillis)
            assignment("declinedPauseMillis", offer.declinedPauseMillis)
        }
        line("}")
    }

    private fun randomItemOfferStep(step: RandomItemOfferSavedStep) {
        line("step {")
        indented {
            when (step) {
                is RandomItemOfferSavedStep.Choice -> randomItemOfferChoiceStep(step)
                is RandomItemOfferSavedStep.DropSlot -> randomItemOfferDropSlotStep(step)
                is RandomItemOfferSavedStep.Message -> randomItemOfferMessageStep(step)
                is RandomItemOfferSavedStep.Finished -> randomItemOfferFinishedStep(step)
            }
        }
        line("}")
    }

    private fun randomItemOfferChoiceStep(step: RandomItemOfferSavedStep.Choice) {
        line("choice {")
        indented {
            textBlock("offerText", step.offerText)
            textBlock("prompt", step.prompt)
            line("item = ${itemId(step.item)}")
            assignment("itemName", step.itemName)
            assignment("price", step.price)
            inputBinding("buy", step.buy)
            inputBinding("decline", step.decline)
        }
        line("}")
    }

    private fun randomItemOfferDropSlotStep(step: RandomItemOfferSavedStep.DropSlot) {
        line("dropSlot {")
        indented {
            textBlock("prompt", step.prompt)
            line("item = ${itemId(step.item)}")
            assignment("itemName", step.itemName)
            assignment("price", step.price)
            randomItemOfferResult("result", step.result)
        }
        line("}")
    }

    private fun randomItemOfferMessageStep(step: RandomItemOfferSavedStep.Message) {
        line("message {")
        indented {
            textBlock("text", step.text)
            randomItemOfferStep("next", step.next)
            randomItemOfferResult("result", step.result)
        }
        line("}")
    }

    private fun randomItemOfferFinishedStep(step: RandomItemOfferSavedStep.Finished) {
        line("finished {")
        indented {
            randomItemOfferResult("result", step.result)
        }
        line("}")
    }

    private fun randomItemOfferStep(
        name: String,
        step: RandomItemOfferSavedStep,
    ) {
        line("$name {")
        indented {
            when (step) {
                is RandomItemOfferSavedStep.Choice -> randomItemOfferChoiceStep(step)
                is RandomItemOfferSavedStep.DropSlot -> randomItemOfferDropSlotStep(step)
                is RandomItemOfferSavedStep.Message -> randomItemOfferMessageStep(step)
                is RandomItemOfferSavedStep.Finished -> randomItemOfferFinishedStep(step)
            }
        }
        line("}")
    }

    private fun nullableRandomItemOfferResult(
        name: String,
        result: RandomItemOfferResultSnapshot?,
    ) {
        if (result == null) {
            line("$name = null")
            return
        }
        randomItemOfferResult(name, result)
    }

    private fun randomItemOfferResult(
        name: String,
        result: RandomItemOfferResultSnapshot,
    ) {
        line("$name {")
        indented {
            line("status = RandomItemOfferStatus.${result.status.name}")
            line("item = ${itemId(result.item)}")
            assignment("price", result.price)
            line("slot = ${result.slot ?: "null"}")
        }
        line("}")
    }

    private fun shopServiceSession(session: ShopServiceSessionSnapshot) {
        line("shopServiceSession {")
        indented {
            line("building = ${buildingId(session.building)}")
            shopServiceStep(session.step)
            nullableShopTransactionResult("lastTransaction", session.lastTransaction)
            nullableShopSellQuoteResult("lastSellQuote", session.lastSellQuote)
        }
        line("}")
    }

    private fun shopServiceStep(step: ShopServiceSavedStep) {
        line("step {")
        indented {
            when (step) {
                ShopServiceSavedStep.Intro -> emptyShopStep("intro")
                ShopServiceSavedStep.MainMenu -> emptyShopStep("mainMenu")
                ShopServiceSavedStep.SelectCatalog -> emptyShopStep("selectCatalog")
                is ShopServiceSavedStep.BuyItem -> shopBuyItemStep(step)
                is ShopServiceSavedStep.Quantity -> shopQuantityStep(step)
                is ShopServiceSavedStep.ConfirmPurchase -> shopConfirmPurchaseStep(step)
                is ShopServiceSavedStep.DropSlot -> shopDropSlotStep(step)
                ShopServiceSavedStep.SellSlot -> emptyShopStep("sellSlot")
                is ShopServiceSavedStep.ConfirmSale -> shopConfirmSaleStep(step)
                ShopServiceSavedStep.Finished -> emptyShopStep("finished")
            }
        }
        line("}")
    }

    private fun shopBuyItemStep(step: ShopServiceSavedStep.BuyItem) {
        line("buyItem {")
        indented {
            charList("catalogKeys", step.catalogKeys)
        }
        line("}")
    }

    private fun shopQuantityStep(step: ShopServiceSavedStep.Quantity) {
        line("quantity {")
        indented {
            line("item = ${itemId(step.item)}")
        }
        line("}")
    }

    private fun shopConfirmPurchaseStep(step: ShopServiceSavedStep.ConfirmPurchase) {
        line("confirmPurchase {")
        indented {
            line("item = ${itemId(step.item)}")
            assignment("quantity", step.quantity)
            assignment("unitPrice", step.unitPrice)
            assignment("totalPrice", step.totalPrice)
        }
        line("}")
    }

    private fun shopDropSlotStep(step: ShopServiceSavedStep.DropSlot) {
        line("dropSlot {")
        indented {
            line("item = ${itemId(step.item)}")
            assignment("quantity", step.quantity)
            shopTransactionResult("result", step.result)
        }
        line("}")
    }

    private fun shopConfirmSaleStep(step: ShopServiceSavedStep.ConfirmSale) {
        line("confirmSale {")
        indented {
            shopSellQuoteResult("quote", step.quote)
        }
        line("}")
    }

    private fun emptyShopStep(name: String) {
        line("$name {")
        line("}")
    }

    private fun nullableShopTransactionResult(
        name: String,
        result: ShopTransactionResultSnapshot?,
    ) {
        if (result == null) {
            line("$name = null")
            return
        }
        shopTransactionResult(name, result)
    }

    private fun shopTransactionResult(
        name: String,
        result: ShopTransactionResultSnapshot,
    ) {
        line("$name {")
        indented {
            line("status = ShopTransactionStatus.${result.status.name}")
            line("item = ${result.item?.let(::itemId) ?: "null"}")
            assignment("goldDelta", result.goldDelta)
            line("slot = ${result.slot ?: "null"}")
            assignment("quantity", result.quantity)
        }
        line("}")
    }

    private fun nullableShopSellQuoteResult(
        name: String,
        result: ShopSellQuoteResultSnapshot?,
    ) {
        if (result == null) {
            line("$name = null")
            return
        }
        shopSellQuoteResult(name, result)
    }

    private fun shopSellQuoteResult(
        name: String,
        result: ShopSellQuoteResultSnapshot,
    ) {
        line("$name {")
        indented {
            line("status = ShopSellQuoteStatus.${result.status.name}")
            line("item = ${result.item?.let(::itemId) ?: "null"}")
            line("slot = ${result.slot ?: "null"}")
            assignment("price", result.price)
            intList("bargainRolls", result.bargainRolls)
        }
        line("}")
    }

    private fun rewardGrantSession(session: RewardGrantSessionSnapshot) {
        line("rewardGrantSession {")
        indented {
            scriptReward(session.reward)
            itemList("pendingItems", session.pendingItems)
            rewardGrantStep(session.step)
            nullableRewardGrantResult("lastResult", session.lastResult)
        }
        line("}")
    }

    private fun scriptReward(reward: ScriptRewardModel) {
        line("reward {")
        indented {
            itemList("items", reward.items)
            rewardApplication(reward.application)
            rewardMessages(reward.messages)
        }
        line("}")
    }

    private fun rewardApplication(application: RewardApplicationModel) {
        line("application {")
        indented {
            line("regularItems = RewardItemPlacement.${application.regularItems.name}")
            line("overflow = RewardOverflowPolicy.${application.overflow.name}")
            line("specialItems = RewardSpecialItemPolicy.${application.specialItems.name}")
        }
        line("}")
    }

    private fun rewardMessages(messages: RewardMessageModel) {
        line("messages {")
        indented {
            nullableTextBlock("cantCarry", messages.cantCarry)
            nullableTextBlock("pickedUp", messages.pickedUp)
        }
        line("}")
    }

    private fun rewardGrantStep(step: RewardGrantSavedStep) {
        line("step {")
        indented {
            when (step) {
                is RewardGrantSavedStep.DropSlot -> rewardGrantDropSlotStep(step)
                is RewardGrantSavedStep.Message -> rewardGrantMessageStep(step)
                is RewardGrantSavedStep.Finished -> rewardGrantFinishedStep(step)
            }
        }
        line("}")
    }

    private fun rewardGrantDropSlotStep(step: RewardGrantSavedStep.DropSlot) {
        line("dropSlot {")
        indented {
            textBlock("prompt", step.prompt)
            line("item = ${itemId(step.item)}")
            rewardGrantResult("result", step.result)
        }
        line("}")
    }

    private fun rewardGrantMessageStep(step: RewardGrantSavedStep.Message) {
        line("message {")
        indented {
            textBlock("text", step.text)
            rewardGrantResult("result", step.result)
        }
        line("}")
    }

    private fun rewardGrantFinishedStep(step: RewardGrantSavedStep.Finished) {
        line("finished {")
        indented {
            rewardGrantResult("result", step.result)
        }
        line("}")
    }

    private fun nullableRewardGrantResult(
        name: String,
        result: RewardGrantResultSnapshot?,
    ) {
        if (result == null) {
            line("$name = null")
            return
        }
        rewardGrantResult(name, result)
    }

    private fun rewardGrantResult(
        name: String,
        result: RewardGrantResultSnapshot,
    ) {
        line("$name {")
        indented {
            line("status = RewardGrantStatus.${result.status.name}")
            line("item = ${result.item?.let(::itemId) ?: "null"}")
            line("slot = ${result.slot ?: "null"}")
        }
        line("}")
    }

    private fun battleSession(session: BattleSessionSnapshot) {
        line("battleSession {")
        indented {
            line("tier = ${session.tier?.let(::encounterTierId) ?: "null"}")
            line("monster = ${monsterId(session.monster)}")
            assignment("count", session.count)
            intList("hitPoints", session.hitPoints)
            assignment("hitPointMax", session.hitPointMax)
            assignment("mandatory", session.mandatory)
            battleRound(session.round)
            battleSavedStep(session.step)
            battleResult(session.lastResult)
        }
        line("}")
    }

    private fun battleRound(round: BattleRoundState) {
        line("round {")
        indented {
            assignment("currentMemberIndex", round.currentMemberIndex)
            assignment("defendCount", round.defendCount)
            assignment("monsterWeaponSkill", round.monsterWeaponSkill)
            assignment("monsterConfused", round.monsterConfused)
            assignment("monstersHeld", round.monstersHeld)
            assignment("antimagic", round.antimagic)
            assignment("partyHeld", round.partyHeld)
            assignment("partyConfused", round.partyConfused)
            assignment("actionRetry", round.actionRetry)
        }
        line("}")
    }

    private fun battleResult(result: BattleSessionResultSnapshot) {
        line("lastResult {")
        indented {
            line("status = BattleSessionResultStatus.${result.status.name}")
            line("monster = ${result.monster?.let(::monsterId) ?: "null"}")
            line("tier = ${result.tier?.let(::encounterTierId) ?: "null"}")
        }
        line("}")
    }

    private fun battleSavedStep(step: BattleSavedStep) = battleSavedStep("step", step)

    private fun battleSavedStep(
        name: String,
        step: BattleSavedStep,
    ) {
        line("$name {")
        indented {
            when (step) {
                is BattleSavedStep.Opening -> battleOpeningStep(step)
                is BattleSavedStep.RoundOpening -> battleRoundOpeningStep(step)
                is BattleSavedStep.Message -> battleMessageStep(step)
                is BattleSavedStep.PlayerAction -> battlePlayerActionStep(step)
                is BattleSavedStep.MonsterTarget -> battleMonsterTargetStep(step)
                is BattleSavedStep.ScriptMonsterTarget -> battleScriptMonsterTargetStep(step)
                is BattleSavedStep.MemberTarget -> battleMemberTargetStep(step)
                is BattleSavedStep.MagicItemSlot -> battleMagicItemSlotStep(step)
                is BattleSavedStep.SpellChoice -> battleSpellChoiceStep(step)
                BattleSavedStep.MonsterRound -> emptyBattleStep("monsterRound")
                BattleSavedStep.Finished -> emptyBattleStep("finished")
            }
        }
        line("}")
    }

    private fun battleOpeningStep(step: BattleSavedStep.Opening) {
        line("opening {")
        indented {
            textBlock("prompt", step.prompt)
            line("image = ${step.image?.let(::imageId) ?: "null"}")
        }
        line("}")
    }

    private fun battleRoundOpeningStep(step: BattleSavedStep.RoundOpening) {
        line("roundOpening {")
        indented {
            textBlock("prompt", step.prompt)
        }
        line("}")
    }

    private fun battleMessageStep(step: BattleSavedStep.Message) {
        line("message {")
        indented {
            textBlocks("texts", step.texts)
            battleSavedStep("next", step.next)
            viewCommands(step.viewCommands)
        }
        line("}")
    }

    private fun battlePlayerActionStep(step: BattleSavedStep.PlayerAction) {
        line("playerAction {")
        indented {
            assignment("memberIndex", step.memberIndex)
            textBlock("prompt", step.prompt)
        }
        line("}")
    }

    private fun battleMonsterTargetStep(step: BattleSavedStep.MonsterTarget) {
        line("monsterTarget {")
        indented {
            assignment("memberIndex", step.memberIndex)
            textBlock("prompt", step.prompt)
            charList("targetKeys", step.targetKeys)
        }
        line("}")
    }

    private fun battleScriptMonsterTargetStep(step: BattleSavedStep.ScriptMonsterTarget) {
        line("scriptMonsterTarget {")
        indented {
            textBlock("prompt", step.prompt)
            charList("targetKeys", step.targetKeys)
            combatScript(step.script)
        }
        line("}")
    }

    private fun battleMemberTargetStep(step: BattleSavedStep.MemberTarget) {
        line("memberTarget {")
        indented {
            textBlock("prompt", step.prompt)
            charList("targetKeys", step.targetKeys)
            combatScript(step.script)
        }
        line("}")
    }

    private fun battleMagicItemSlotStep(step: BattleSavedStep.MagicItemSlot) {
        line("magicItemSlot {")
        indented {
            assignment("memberIndex", step.memberIndex)
            textBlock("prompt", step.prompt)
            charList("slotKeys", step.slotKeys)
        }
        line("}")
    }

    private fun battleSpellChoiceStep(step: BattleSavedStep.SpellChoice) {
        line("spellChoice {")
        indented {
            assignment("memberIndex", step.memberIndex)
            textBlock("prompt", step.prompt)
            charList("spellKeys", step.spellKeys)
        }
        line("}")
    }

    private fun combatScript(script: CombatScriptSnapshot) {
        line("script {")
        indented {
            line("memberIndex = ${script.memberIndex ?: "null"}")
            line("sourceKind = CombatScriptSourceKind.${script.sourceKind.name}")
            assignment("sourceSlug", script.sourceSlug)
            assignment("nextIndex", script.nextIndex)
            assignment("pendingVar", script.pendingVar)
            namedIntMap("locals", script.locals)
            line("activeInventorySlot = ${script.activeInventorySlot ?: "null"}")
        }
        line("}")
    }

    private fun emptyBattleStep(name: String) {
        line("$name {")
        line("}")
    }

    private fun compiledVmContinuation(continuation: CompiledVmContinuationSnapshot) {
        line("compiledVm {")
        indented {
            scriptRoot(continuation.root)
            namedIntMap("locals", continuation.locals)
            namedStringMap("textBindings", continuation.textBindings)
            line("frames {")
            indented {
                continuation.frames.forEach(::compiledVmFrame)
            }
            line("}")
            compiledVmPending(continuation.pending)
        }
        line("}")
    }

    private fun compiledVmFrame(frame: CompiledVmFrameSnapshot) {
        line("frame {")
        indented {
            intList("blockPath", frame.blockPath)
            assignment("nextIndex", frame.nextIndex)
            assignment("insideLoop", frame.insideLoop)
            line("kind = CompiledVmFrameKind.${frame.kind.name}")
            assignment("enclosingInsideLoop", frame.enclosingInsideLoop)
        }
        line("}")
    }

    private fun compiledVmPending(pending: CompiledVmPendingInputSnapshot) {
        line("pending {")
        indented {
            when (pending) {
                is CompiledVmPendingInputSnapshot.WaitInput -> compiledVmWaitInput(pending)
                is CompiledVmPendingInputSnapshot.Menu -> compiledVmMenu(pending)
                is CompiledVmPendingInputSnapshot.AskKey -> compiledVmAskKey(pending)
                is CompiledVmPendingInputSnapshot.AskMember -> compiledVmAskMember(pending)
                is CompiledVmPendingInputSnapshot.AskMonster -> compiledVmAskMonster(pending)
                is CompiledVmPendingInputSnapshot.AskInventorySlot -> compiledVmAskInventorySlot(pending)
                is CompiledVmPendingInputSnapshot.AskText -> compiledVmAskText(pending)
                is CompiledVmPendingInputSnapshot.ChildSession -> compiledVmChildSession(pending)
            }
        }
        line("}")
    }

    private fun compiledVmWaitInput(pending: CompiledVmPendingInputSnapshot.WaitInput) {
        line("waitInput {")
        indented {
            compiledVmPendingHeader(pending)
            line("reason = ${pending.reason?.let(::quote) ?: "null"}")
            nullableTextBlock("prompt", pending.prompt)
        }
        line("}")
    }

    private fun compiledVmMenu(pending: CompiledVmPendingInputSnapshot.Menu) {
        line("menu {")
        indented {
            compiledVmPendingHeader(pending)
            textBlock("prompt", pending.prompt)
            line("options {")
            indented {
                pending.options.forEach { option -> line("option(${charLiteral(option.key)}, ${option.optionIndex})") }
            }
            line("}")
            assignment("hasDefault", pending.hasDefault)
        }
        line("}")
    }

    private fun compiledVmAskKey(pending: CompiledVmPendingInputSnapshot.AskKey) {
        line("askKey {")
        indented {
            compiledVmPendingHeader(pending)
            textBlock("prompt", pending.prompt)
            assignment("localName", pending.localName)
            inputBindings(pending.bindings)
        }
        line("}")
    }

    private fun compiledVmAskMember(pending: CompiledVmPendingInputSnapshot.AskMember) {
        line("askMember {")
        indented {
            compiledVmPendingHeader(pending)
            textBlock("prompt", pending.prompt)
            assignment("localName", pending.localName)
            line("filter = MemberChoiceFilter.${pending.filter.name}")
            line("accepting = MemberChoiceFilter.${pending.accepting.name}")
            intList("choices", pending.choices)
        }
        line("}")
    }

    private fun compiledVmAskMonster(pending: CompiledVmPendingInputSnapshot.AskMonster) {
        line("askMonster {")
        indented {
            compiledVmPendingHeader(pending)
            textBlock("prompt", pending.prompt)
            assignment("localName", pending.localName)
            charList("targetKeys", pending.targetKeys)
        }
        line("}")
    }

    private fun compiledVmAskInventorySlot(pending: CompiledVmPendingInputSnapshot.AskInventorySlot) {
        line("askInventorySlot {")
        indented {
            compiledVmPendingHeader(pending)
            textBlock("prompt", pending.prompt)
            assignment("localName", pending.localName)
            line("firstKey = ${charLiteral(pending.firstKey)}")
            assignment("firstSlot", pending.firstSlot)
            assignment("lastSlot", pending.lastSlot)
            line("filter = InventorySlotChoiceFilter.${pending.filter.name}")
            line("choices {")
            indented {
                pending.choices.forEach { choice -> line("choice(${charLiteral(choice.key)}, ${choice.slot})") }
            }
            line("}")
        }
        line("}")
    }

    private fun compiledVmAskText(pending: CompiledVmPendingInputSnapshot.AskText) {
        line("askText {")
        indented {
            compiledVmPendingHeader(pending)
            textBlock("prompt", pending.prompt)
            stringList("accepted", pending.accepted)
            assignment("caseSensitive", pending.caseSensitive)
            assignment("trim", pending.trim)
            line("maxLength = ${pending.maxLength ?: "null"}")
        }
        line("}")
    }

    private fun compiledVmChildSession(pending: CompiledVmPendingInputSnapshot.ChildSession) {
        line("childSession {")
        indented {
            compiledVmPendingHeader(pending)
            compiledVmChildRequest(pending.request)
        }
        line("}")
    }

    private fun compiledVmPendingHeader(pending: CompiledVmPendingInputSnapshot) {
        scriptRoot(pending.root)
        assignment("instructionIndex", pending.instructionIndex)
        intList("operandPath", pending.operandPath)
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

    private fun namedStringMap(
        name: String,
        values: Map<String, String>,
    ) {
        line("$name {")
        indented {
            values.entries.sortedBy { it.key }.forEach { (key, value) ->
                line("value(${quote(key)}, ${quote(value)})")
            }
        }
        line("}")
    }

    private fun intList(
        name: String,
        values: List<Int>,
    ) {
        line("$name {")
        indented {
            values.forEach { value -> line("value($value)") }
        }
        line("}")
    }

    private fun stringList(
        name: String,
        values: List<String>,
    ) {
        line("$name {")
        indented {
            values.forEach { value -> line("value(${quote(value)})") }
        }
        line("}")
    }

    private fun charList(
        name: String,
        values: List<Char>,
    ) {
        line("$name {")
        indented {
            values.forEach { value -> line("value(${charLiteral(value)})") }
        }
        line("}")
    }

    private fun itemList(
        name: String,
        values: List<ItemId>,
    ) {
        line("$name {")
        indented {
            values.forEach { value -> line("value(${itemId(value)})") }
        }
        line("}")
    }

    private fun compiledHandleList(
        name: String,
        values: List<CompiledHandleRef>,
    ) {
        line("$name {")
        indented {
            values.forEach { value -> line(compiledHandleRef(value)) }
        }
        line("}")
    }

    private fun compiledHandleRef(value: CompiledHandleRef): String = "handle(CompiledHandleKind.${value.kind.name}, ${value.id})"

    private fun textBlocks(
        name: String,
        blocks: List<TextBlock>,
    ) {
        line("$name {")
        indented {
            blocks.forEach { block -> textBlock("text", block) }
        }
        line("}")
    }

    private fun viewCommands(commands: List<ViewCommand>) {
        line("viewCommands {")
        indented {
            commands.forEach { command ->
                when (command) {
                    ViewCommand.ClearText -> line("clearText()")
                    ViewCommand.ClearMapWindow -> line("clearMapWindow()")
                    ViewCommand.RenderMap -> line("renderMap()")
                    ViewCommand.StatusLine -> line("statusLine()")
                    is ViewCommand.ShowImage -> line("showImage(${imageId(command.image)})")
                }
            }
        }
        line("}")
    }

    private fun inputBindings(bindings: List<InputBindingModel>) {
        line("bindings {")
        indented {
            bindings.forEach { binding -> inputBinding("binding", binding) }
        }
        line("}")
    }

    private fun inputBinding(
        name: String,
        binding: InputBindingModel,
    ) {
        line("$name {")
        indented {
            assignment("primary", binding.primary)
            stringList("aliases", binding.aliases)
            assignment("label", binding.label)
        }
        line("}")
    }

    private fun nullableTextBlock(
        name: String,
        block: TextBlock?,
    ) {
        if (block == null) {
            line("$name = null")
            return
        }
        textBlock(name, block)
    }

    private fun textBlock(
        name: String,
        block: TextBlock,
    ) {
        line("$name {")
        indented {
            when (block) {
                is TextBlock.Ref -> line("ref = ${textId(block.id)}")
                is TextBlock.Inline -> {
                    line("inline {")
                    indented {
                        block.lines.forEach { line -> line("line(${quote(line)})") }
                    }
                    line("}")
                }
                is TextBlock.Structured -> {
                    line("structured {")
                    indented {
                        block.lines.forEach(::textLine)
                    }
                    line("}")
                }
            }
        }
        line("}")
    }

    private fun textLine(textLine: TextLine) {
        line("line {")
        indented {
            textLine.parts.forEach(::textPart)
        }
        line("}")
    }

    private fun textPart(part: TextPart) {
        when (part) {
            is TextPart.Literal -> line("literal(${quote(part.text)})")
            is TextPart.RoleName -> line("roleName(${npcRoleId(part.role)})")
            is TextPart.RoleLocation -> line("roleLocation(${npcRoleId(part.role)})")
            is TextPart.TownName -> line("townName(${townId(part.town)})")
            is TextPart.FuzzyTownLocation -> line("fuzzyTownLocation(${townId(part.town)}, ${part.radius})")
            is TextPart.LocationCoordinate -> line("locationCoordinate(${locationId(part.location)})")
            is TextPart.IntValue,
            is TextPart.StringValue,
            -> error("runtime snapshot text blocks cannot persist expression text parts yet")
            TextPart.OfferedItemName -> textPartToken("OfferedItemName")
            TextPart.OfferedItemPrice -> textPartToken("OfferedItemPrice")
            TextPart.SelectedItemName -> textPartToken("SelectedItemName")
            TextPart.ShopPrice -> textPartToken("ShopPrice")
            TextPart.ShopCarryCap -> textPartToken("ShopCarryCap")
            TextPart.HuntMemberName -> textPartToken("HuntMemberName")
            TextPart.HuntMemberSubjectPronoun -> textPartToken("HuntMemberSubjectPronoun")
            TextPart.HuntFoodGained -> textPartToken("HuntFoodGained")
            TextPart.PartyMemberMax -> textPartToken("PartyMemberMax")
            TextPart.WildernessDungeonName -> textPartToken("WildernessDungeonName")
            TextPart.WildernessDungeonLevels -> textPartToken("WildernessDungeonLevels")
            TextPart.DungeonDepth -> textPartToken("DungeonDepth")
            TextPart.ConversationNpcName -> textPartToken("ConversationNpcName")
            TextPart.CurrentTownName -> textPartToken("CurrentTownName")
            TextPart.TownTaxPercent -> textPartToken("TownTaxPercent")
            TextPart.TownTaxAmount -> textPartToken("TownTaxAmount")
            TextPart.CreationMemberNumber -> textPartToken("CreationMemberNumber")
            TextPart.CreationMemberName -> textPartToken("CreationMemberName")
            TextPart.CreationWeaponSkill -> textPartToken("CreationWeaponSkill")
            TextPart.CreationHealth -> textPartToken("CreationHealth")
            TextPart.CreationWisdom -> textPartToken("CreationWisdom")
            TextPart.CreationCharisma -> textPartToken("CreationCharisma")
        }
    }

    private fun textPartToken(name: String) {
        line("token(${quote(name)})")
    }

    private fun compiledVmChildRequest(request: CompiledVmChildRequestSnapshot) {
        line("request {")
        indented {
            when (request) {
                is CompiledVmChildRequestSnapshot.Speak -> {
                    line("speak {")
                    indented {
                        line("building = ${buildingId(request.building)}")
                    }
                    line("}")
                }
                is CompiledVmChildRequestSnapshot.TellRumour -> {
                    line("tellRumour {")
                    indented {
                        line("table = ${rumourTableId(request.table)}")
                    }
                    line("}")
                }
                is CompiledVmChildRequestSnapshot.Encounter -> {
                    line("encounter {")
                    indented {
                        line("monster = ${monsterId(request.monster)}")
                        assignment("mandatory", request.mandatory)
                        line("tier = ${request.tier?.let(::quote) ?: "null"}")
                    }
                    line("}")
                }
                is CompiledVmChildRequestSnapshot.EnterDungeon -> {
                    line("enterDungeon {")
                    indented {
                        line("dungeon = ${dungeonId(request.handoff.dungeon)}")
                        assignment("levels", request.handoff.levels)
                    }
                    line("}")
                }
                is CompiledVmChildRequestSnapshot.GrantReward -> {
                    line("grantReward {")
                    indented {
                        assignment("gold", request.gold)
                        compiledHandleList("items", request.reward.items)
                    }
                    line("}")
                }
                is CompiledVmChildRequestSnapshot.RandomItemOffer -> {
                    line("randomItemOffer {")
                    indented {
                        line("pool = ${compiledHandleRef(request.offer.pool)}")
                        assignment("selectedIndex", request.selectedIndex)
                    }
                    line("}")
                }
                is CompiledVmChildRequestSnapshot.ShopService -> {
                    line("shopService {")
                    indented {
                        line("building = ${compiledHandleRef(request.shop.building)}")
                    }
                    line("}")
                }
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

    private fun castleId(id: CastleId): String = "castle(${quote(id.slug)})"

    private fun locationId(id: LocationId): String = "location(${quote(id.slug)})"

    private fun npcId(id: NpcId): String = "npc(${quote(id.slug)})"

    private fun npcRoleId(id: NpcRole): String = "npcRole(${quote(id.slug)})"

    private fun itemId(id: ItemId): String = "item(${quote(id.slug)})"

    private fun itemPoolId(id: ItemPoolId): String = "itemPool(${quote(id.slug)})"

    private fun milestoneId(id: GameMilestoneId): String = "milestone(${quote(id.slug)})"

    private fun monsterId(id: MonsterId): String = "monster(${quote(id.slug)})"

    private fun encounterTierId(id: EncounterTier): String = "encounterTier(${quote(id.slug)})"

    private fun imageId(id: ImageId): String = "image(${quote(id.slug)})"

    private fun rumourTableId(id: RumourTableId): String = "rumourTable(${quote(id.slug)})"

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
