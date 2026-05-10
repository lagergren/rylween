/**
 * Save/load support for the standalone compiled-bundle runtime.
 *
 * This file intentionally writes a small runtime-owned save format from direct
 * compiled handles and scalar rows. It does not use the old Kotlin
 * `RuntimeSessionState` snapshot path, because the standalone runtime must be
 * able to persist state without depending on the legacy session graph.
 */

package rylween.compiledruntime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledInventorySlot
import rylween.compiled.CompiledNpcVisit
import rylween.compiled.CompiledPartyCounters
import rylween.compiled.CompiledPartyMember
import rylween.compiled.CompiledStateImage
import rylween.dsl.GridPoint
import rylween.dsl.PartyResources
import rylween.dsl.RuntimeMode
import java.io.File

private const val SAVE_FORMAT = "rylween-compiled-runtime-save"
private const val SAVE_VERSION = "1"
private const val PARTY_MEMBER_RECORD_COUNT = "party.members"
private val MEMBER_FIELD_KEY = Regex("""^member\.(\d+)\.""")

/** Reads and writes standalone compiled-runtime wilderness saves. */
internal object StandaloneCompiledSave {
    fun write(
        file: File,
        runtime: StandaloneRuntimeState,
        bundleHash: String,
        content: CompiledContentBundle,
    ) {
        val values =
            buildMap {
                put("format", SAVE_FORMAT)
                put("version", SAVE_VERSION)
                put("bundleHash", bundleHash)
                put("state", runtime.stateSlug)
                put("realm.day", runtime.day.toString())
                put("realm.hour", runtime.hour.toString())
                put("realm.minute", runtime.minute.toString())
                put("realm.currentMap", content.slug(runtime.currentMap))
                put("realm.currentMode", runtime.currentMode.name)
                put("realm.currentTown", runtime.currentTown?.let(content::slug).orEmpty())
                put("realm.lastTerrain", runtime.lastTerrain?.let(content::slug).orEmpty())
                put("realm.partyMemberMax", runtime.partyMemberMax.toString())
                put("missions.completed", runtime.completedMissions.joinToString(",") { content.slug(it) })
                runtime.dungeon?.let { dungeon ->
                    put("dungeon.id", content.slug(dungeon.dungeon))
                    put("dungeon.maxDepth", dungeon.maxDepth.toString())
                    put("dungeon.depth", dungeon.depth.toString())
                    put("dungeon.pendingMission", dungeon.pendingMission?.let(content::slug).orEmpty())
                    put(
                        "dungeon.levelEvents",
                        dungeon.levelEvents.entries
                            .sortedBy(Map.Entry<Int, CompiledHandleRef>::key)
                            .joinToString(",") { (level, event) -> "$level:${content.slug(event)}" },
                    )
                    dungeon.postDungeon?.let { continuation -> putPostDungeon(continuation, content) }
                }
                runtime.castle?.let { castle ->
                    put("castle.id", castle.castleId)
                    put("castle.floor", castle.floor.toString())
                    put("castle.x", castle.point.x.toString())
                    put("castle.y", castle.point.y.toString())
                    put("castle.roomEntryPending", castle.roomEntryPending.toString())
                    put(
                        "castle.rooms",
                        castle.rooms.entries
                            .sortedWith(compareBy<Map.Entry<GridPoint, String>> { it.key.y }.thenBy { it.key.x })
                            .joinToString(",") { (point, event) -> "${point.x}:${point.y}:$event" },
                    )
                }
                putParty(runtime.party, content)
            }
        file.parentFile?.mkdirs()
        file.writeText(StandaloneSaveCodec.encodeValues(values))
    }

    fun read(
        file: File,
        expectedBundleHash: String,
        content: CompiledContentBundle,
        stateLookup: (String) -> CompiledStateImage,
    ): StandaloneRuntimeState {
        val values = file.readSaveValues()
        values.requireValue("format", SAVE_FORMAT)
        values.requireValue("version", SAVE_VERSION)
        values.requireValue("bundleHash", expectedBundleHash)

        val baseState = StandaloneRuntimeState.from(stateLookup(values.required("state")), content)
        validateSavedPartyMemberCount(values, baseState.partyMemberMax)
        val mode = values["realm.currentMode"]?.let(RuntimeMode::valueOf) ?: baseState.currentMode
        return baseState.copy(
            day = values.intOrDefault("realm.day", baseState.day),
            hour = values.intOrDefault("realm.hour", baseState.hour),
            minute = values.intOrDefault("realm.minute", baseState.minute),
            currentMap = values.handleOrDefault("realm.currentMap", CompiledHandleKind.WorldMap, content, baseState.currentMap),
            currentMode = mode,
            currentTown = values.optionalHandle("realm.currentTown", CompiledHandleKind.Town, content) ?: baseState.currentTown,
            lastTerrain = values.optionalHandle("realm.lastTerrain", CompiledHandleKind.Terrain, content) ?: baseState.lastTerrain,
            partyMemberMax = values.intOrDefault("realm.partyMemberMax", baseState.partyMemberMax),
            completedMissions =
                values.handleListOrDefault("missions.completed", CompiledHandleKind.Mission, content, baseState.completedMissions),
            dungeon = values.readDungeon(content) ?: baseState.dungeon,
            castle = values.readCastle() ?: baseState.castle,
            party = values.readParty(baseState.party, content),
        )
    }

    private fun MutableMap<String, String>.putParty(
        party: StandaloneParty,
        content: CompiledContentBundle,
    ) {
        put("party.id", content.slug(party.id))
        put("party.name", party.name)
        put("party.x", party.position.x.toString())
        put("party.y", party.position.y.toString())
        put("party.gold", party.resources.gold.toString())
        put("party.torches", party.resources.torches.toString())
        put("party.food", party.resources.food.toString())
        put("party.timeLeft", party.resources.timeLeft.toString())
        put("party.restedHours", party.resources.restedHours.toString())
        put(
            "party.memberCount",
            party.counters.memberCount
                ?.toString()
                .orEmpty(),
        )
        put(PARTY_MEMBER_RECORD_COUNT, party.members.size.toString())
        put("party.kills", party.counters.kills.toString())
        put("party.waitingMail", party.counters.waitingMail.toString())
        put("party.attacked", party.counters.attacked.toString())
        put("party.rngState", party.rngState?.toString().orEmpty())
        put("party.flags", party.flags.joinToString(",") { content.slug(it) })
        put("party.milestones", party.milestones.joinToString(",") { content.slug(it) })
        put(
            "party.visitedNpcs",
            party.visitedNpcs
                .filter(CompiledNpcVisit::visited)
                .joinToString(",") { visit -> content.slug(visit.npc) },
        )
        party.inventory.forEach { slot ->
            put("inventory.${slot.slot}", slot.item?.let(content::slug).orEmpty())
        }
        party.members.forEachIndexed { index, member ->
            val number = index + 1
            put("member.$number.name", party.memberNames.getOrNull(index).orEmpty())
            put("member.$number.hitPoints", member.hitPoints.current.toString())
            put("member.$number.magic", member.mana.current.toString())
            put("member.$number.dead", member.dead.toString())
        }
    }

    internal fun validateSavedPartyMemberCount(
        values: Map<String, String>,
        requiredPartyMemberCount: Int,
    ) {
        require(requiredPartyMemberCount > 0) { "DSL party member minimum must be positive" }

        val explicitCount = values.optionalInt(PARTY_MEMBER_RECORD_COUNT)
        val memberRecordCount = values.savedMemberSlots().size.takeIf { count -> count > 0 }
        if (explicitCount != null && memberRecordCount != null) {
            require(explicitCount == memberRecordCount) {
                "compiled save party member count mismatch: $PARTY_MEMBER_RECORD_COUNT=$explicitCount, " +
                    "member records=$memberRecordCount"
            }
        }

        val savedCount =
            explicitCount
                ?: memberRecordCount
                ?: values.optionalInt("party.memberCount")
                ?: requiredPartyMemberCount
        require(savedCount >= requiredPartyMemberCount) {
            "compiled save party member count $savedCount is below DSL minimum $requiredPartyMemberCount"
        }
    }

    private fun MutableMap<String, String>.putPostDungeon(
        continuation: StandaloneScriptContinuation,
        content: CompiledContentBundle,
    ) {
        put("dungeon.post.root", content.slug(continuation.root))
        put("dungeon.post.frames", StandaloneSaveCodec.encodeFrames(continuation.frames))
        put("dungeon.post.locals", StandaloneSaveCodec.encodeIntMap(continuation.locals))
        put("dungeon.post.textBindings", StandaloneSaveCodec.encodeStringMap(continuation.textBindings))
        put("dungeon.post.activeInventorySlot", continuation.activeInventorySlot?.toString().orEmpty())
    }

    private fun Map<String, String>.readParty(
        base: StandaloneParty,
        content: CompiledContentBundle,
    ): StandaloneParty =
        base.copy(
            name = get("party.name")?.takeIf(String::isNotEmpty) ?: base.name,
            position = GridPoint(intOrDefault("party.x", base.position.x), intOrDefault("party.y", base.position.y)),
            resources =
                PartyResources(
                    gold = intOrDefault("party.gold", base.resources.gold),
                    torches = intOrDefault("party.torches", base.resources.torches),
                    food = intOrDefault("party.food", base.resources.food),
                    timeLeft = intOrDefault("party.timeLeft", base.resources.timeLeft),
                    restedHours = intOrDefault("party.restedHours", base.resources.restedHours),
                ),
            counters =
                CompiledPartyCounters(
                    memberCount = optionalInt("party.memberCount") ?: base.counters.memberCount,
                    kills = intOrDefault("party.kills", base.counters.kills),
                    waitingMail = booleanOrDefault("party.waitingMail", base.counters.waitingMail),
                    attacked = booleanOrDefault("party.attacked", base.counters.attacked),
                    attackedToday = base.counters.attackedToday,
                ),
            flags = handleListOrDefault("party.flags", CompiledHandleKind.FlagState, content, base.flags),
            milestones = handleListOrDefault("party.milestones", CompiledHandleKind.Milestone, content, base.milestones),
            visitedNpcs = readVisitedNpcs(base.visitedNpcs, content),
            inventory = readInventory(base.inventory, content),
            members = readMembers(base.members),
            memberNames = readMemberNames(base.memberNames),
            rngState = optionalLong("party.rngState") ?: base.rngState,
        )

    private fun Map<String, String>.readDungeon(content: CompiledContentBundle): StandaloneDungeonState? {
        val dungeon = optionalHandle("dungeon.id", CompiledHandleKind.Dungeon, content) ?: return null
        return StandaloneDungeonState(
            dungeon = dungeon,
            maxDepth = intOrDefault("dungeon.maxDepth", 1),
            depth = intOrDefault("dungeon.depth", 1),
            levelEvents =
                get("dungeon.levelEvents")
                    ?.takeIf(String::isNotEmpty)
                    ?.split(',')
                    ?.associate { token ->
                        val parts = token.split(':')
                        parts[0].toInt() to content.ref(CompiledHandleKind.DungeonEvent, parts[1])
                    }.orEmpty(),
            pendingMission = optionalHandle("dungeon.pendingMission", CompiledHandleKind.Mission, content),
            postDungeon = readPostDungeon(content),
        )
    }

    private fun Map<String, String>.readPostDungeon(content: CompiledContentBundle): StandaloneScriptContinuation? {
        val root = optionalHandle("dungeon.post.root", CompiledHandleKind.ScriptRoot, content) ?: return null
        val frames = get("dungeon.post.frames")?.takeIf(String::isNotEmpty)?.let(StandaloneSaveCodec::decodeFrames).orEmpty()
        if (frames.isEmpty()) return null
        return StandaloneScriptContinuation(
            root = root,
            frames = frames,
            locals = StandaloneSaveCodec.decodeIntMap(get("dungeon.post.locals").orEmpty()),
            textBindings = StandaloneSaveCodec.decodeStringMap(get("dungeon.post.textBindings").orEmpty()),
            activeInventorySlot = optionalInt("dungeon.post.activeInventorySlot"),
        )
    }

    private fun Map<String, String>.readCastle(): StandaloneCastleState? {
        val castleId = get("castle.id")?.takeIf(String::isNotEmpty) ?: return null
        return StandaloneCastleState(
            castleId = castleId,
            floor = intOrDefault("castle.floor", 1),
            point = GridPoint(intOrDefault("castle.x", 1), intOrDefault("castle.y", 1)),
            roomEntryPending = booleanOrDefault("castle.roomEntryPending", false),
            rooms =
                get("castle.rooms")
                    ?.takeIf(String::isNotEmpty)
                    ?.split(',')
                    ?.associate { token ->
                        val parts = token.split(':')
                        GridPoint(parts[0].toInt(), parts[1].toInt()) to parts[2]
                    }.orEmpty(),
        )
    }

    private fun Map<String, String>.readVisitedNpcs(
        base: List<CompiledNpcVisit>,
        content: CompiledContentBundle,
    ): List<CompiledNpcVisit> {
        if ("party.visitedNpcs" !in this) return base
        val visited =
            handleListOrDefault("party.visitedNpcs", CompiledHandleKind.Npc, content, emptyList())
                .toSet()
        val baseNpcs = base.map(CompiledNpcVisit::npc).toSet()
        return (baseNpcs + visited)
            .sortedBy(content::slug)
            .map { npc -> CompiledNpcVisit(npc, npc in visited) }
    }

    private fun Map<String, String>.readInventory(
        base: List<CompiledInventorySlot>,
        content: CompiledContentBundle,
    ): List<CompiledInventorySlot> =
        base.map { slot ->
            val item = optionalHandle("inventory.${slot.slot}", CompiledHandleKind.Item, content)
            if (containsKey("inventory.${slot.slot}")) slot.copy(item = item) else slot
        }

    private fun Map<String, String>.readMembers(base: List<CompiledPartyMember>): List<CompiledPartyMember> =
        base.mapIndexed { index, member ->
            val number = index + 1
            member.copy(
                hitPoints = member.hitPoints.copy(current = intOrDefault("member.$number.hitPoints", member.hitPoints.current)),
                mana = member.mana.copy(current = intOrDefault("member.$number.magic", member.mana.current)),
                dead = booleanOrDefault("member.$number.dead", member.dead),
            )
        }

    private fun Map<String, String>.readMemberNames(base: List<String>): List<String> =
        base.mapIndexed { index, name ->
            get("member.${index + 1}.name")?.takeIf(String::isNotEmpty) ?: name
        }

    private fun File.readSaveValues(): Map<String, String> = StandaloneSaveCodec.decodeValues(readLines())

    private fun Map<String, String>.savedMemberSlots(): Set<Int> =
        keys.mapNotNull { key -> MEMBER_FIELD_KEY.find(key)?.groupValues?.get(1)?.toIntOrNull() }.toSet()

    private fun Map<String, String>.required(key: String): String = get(key) ?: error("compiled save missing $key")

    private fun Map<String, String>.requireValue(
        key: String,
        expected: String,
    ) {
        val actual = required(key)
        require(actual == expected) { "compiled save $key mismatch: expected $expected, found $actual" }
    }

    private fun Map<String, String>.intOrDefault(
        key: String,
        default: Int,
    ): Int = get(key)?.takeIf(String::isNotEmpty)?.toInt() ?: default

    private fun Map<String, String>.optionalInt(key: String): Int? = get(key)?.takeIf(String::isNotEmpty)?.toInt()

    private fun Map<String, String>.optionalLong(key: String): Long? = get(key)?.takeIf(String::isNotEmpty)?.toLong()

    private fun Map<String, String>.booleanOrDefault(
        key: String,
        default: Boolean,
    ): Boolean = get(key)?.takeIf(String::isNotEmpty)?.toBooleanStrict() ?: default

    private fun Map<String, String>.handleOrDefault(
        key: String,
        kind: CompiledHandleKind,
        content: CompiledContentBundle,
        default: CompiledHandleRef,
    ): CompiledHandleRef = optionalHandle(key, kind, content) ?: default

    private fun Map<String, String>.optionalHandle(
        key: String,
        kind: CompiledHandleKind,
        content: CompiledContentBundle,
    ): CompiledHandleRef? = get(key)?.takeIf(String::isNotEmpty)?.let { slug -> content.ref(kind, slug) }

    private fun Map<String, String>.handleListOrDefault(
        key: String,
        kind: CompiledHandleKind,
        content: CompiledContentBundle,
        default: List<CompiledHandleRef>,
    ): List<CompiledHandleRef> =
        get(key)
            ?.takeIf(String::isNotEmpty)
            ?.split(',')
            ?.filter(String::isNotEmpty)
            ?.map { slug -> content.ref(kind, slug) }
            ?: default
}
