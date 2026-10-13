/**
 * Save/load support for the runtime.
 *
 * The on-disk shape is a shared-world envelope. Common rows describe the
 * world everyone is playing in; `user.N.*` rows describe each saved party and
 * where that party is currently playing.
 */

package rylween.runtime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledEquipment
import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledInventorySlot
import rylween.compiled.CompiledLocationPlacement
import rylween.compiled.CompiledNpcPlacement
import rylween.compiled.CompiledNpcVisit
import rylween.compiled.CompiledPartyAttackRecord
import rylween.compiled.CompiledPartyCounters
import rylween.compiled.CompiledPartyMember
import rylween.compiled.CompiledStateImage
import rylween.compiled.CompiledWorldPosition
import rylween.dsl.impl.GridPoint
import rylween.dsl.impl.PartyResources
import rylween.dsl.impl.RuntimeMode
import rylween.dsl.impl.StatPool
import java.io.File
import java.time.LocalDate

private const val SAVE_FORMAT = "rylween-save"
private const val SAVE_VERSION = "2"
private const val SAVE_MODEL = "shared-world"
private const val PARTY_MEMBER_RECORD_COUNT = "party.members"
private const val USERS_COUNT = "users.count"
private const val HEROES_COUNT = "heroes.count"

private val memberFieldKey = Regex("""^member\.(\d+)\.""")

/**
 * Encodes and decodes the runtime's sorted key/value save file.
 *
 * This is persistence syntax, not terminal or web protocol. It lives in this
 * file because only the runtime save boundary should know these escaping rules.
 */
internal object SaveCodec {
    fun encodeValues(values: Map<String, String>): String =
        values
            .toSortedMap()
            .entries
            .joinToString(separator = "\n", postfix = "\n") { (key, value) ->
                "${escapeLineField(key)}=${escapeLineField(value)}"
            }

    fun decodeValues(lines: List<String>): Map<String, String> =
        lines
            .asSequence()
            .map(String::trim)
            .filter { line -> line.isNotEmpty() && !line.startsWith("#") }
            .associate { line ->
                val separator = line.indexOf('=')
                require(separator > 0) { "invalid save line: $line" }
                unescapeLineField(line.substring(0, separator)) to unescapeLineField(line.substring(separator + 1))
            }

    fun encodeFrames(frames: List<ScriptFrameState>): String =
        frames.joinToString("|") { frame ->
            "${frame.blockPath.joinToString(",")}:${frame.nextIndex}:${frame.insideLoop}"
        }

    fun decodeFrames(value: String): List<ScriptFrameState> =
        value
            .split('|')
            .filter(String::isNotEmpty)
            .map { token ->
                val parts = token.split(':')
                ScriptFrameState(
                    blockPath =
                        parts[0]
                            .takeIf(String::isNotEmpty)
                            ?.split(',')
                            ?.map(String::toInt)
                            .orEmpty(),
                    nextIndex = parts[1].toInt(),
                    insideLoop = parts[2].toBooleanStrict(),
                )
            }

    fun encodeIntMap(values: Map<String, Int>): String =
        values
            .entries
            .sortedBy(Map.Entry<String, Int>::key)
            .joinToString("|") { (key, value) -> "${escapeSegment(key)}:$value" }

    fun decodeIntMap(value: String): Map<String, Int> = decodeStringMap(value).mapValues { (_, mapValue) -> mapValue.toInt() }

    fun encodeStringMap(values: Map<String, String>): String =
        values
            .entries
            .sortedBy(Map.Entry<String, String>::key)
            .joinToString("|") { (key, value) -> "${escapeSegment(key)}:${escapeSegment(value)}" }

    fun decodeStringMap(value: String): Map<String, String> {
        if (value.isEmpty()) return emptyMap()
        return value
            .split('|')
            .filter(String::isNotEmpty)
            .associate { token ->
                val separator = token.indexOf(':')
                require(separator >= 0) { "invalid compiled continuation map token: $token" }
                unescapeSegment(token.substring(0, separator)) to unescapeSegment(token.substring(separator + 1))
            }
    }

    private fun escapeLineField(value: String): String =
        buildString {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '=' -> append("\\=")
                    else -> append(char)
                }
            }
        }

    private fun unescapeLineField(value: String): String =
        unescape(value) { marker ->
            when (marker) {
                'n' -> '\n'
                'r' -> '\r'
                else -> marker
            }
        }

    private fun escapeSegment(value: String): String =
        buildString {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '|' -> append("\\p")
                    ':' -> append("\\c")
                    else -> append(char)
                }
            }
        }

    private fun unescapeSegment(value: String): String =
        unescape(value) { marker ->
            when (marker) {
                'p' -> '|'
                'c' -> ':'
                else -> marker
            }
        }

    private fun unescape(
        value: String,
        mapped: (Char) -> Char,
    ): String =
        buildString {
            var escaped = false
            value.forEach { char ->
                if (escaped) {
                    append(mapped(char))
                    escaped = false
                } else if (char == '\\') {
                    escaped = true
                } else {
                    append(char)
                }
            }
            if (escaped) append('\\')
        }
}

/** Reads and writes runtime shared-world saves. */
internal object SaveFile {
    fun write(
        file: File,
        runtime: RuntimeState,
        bundleHash: String,
        content: CompiledContentBundle,
        userId: String,
        hallOfHeroesCompletedBy: String?,
    ) = SaveFileStorage.update(file) { target ->
        val activeUser = userId.normalizedSaveUser()
        val existingValues = target.existingSaveValuesOrEmpty()
        if (target.isFile) existingValues.requireReadableSave()
        val preservedUsers = existingValues.userRecords().filterNot { record -> record.id == activeUser }
        val userRecords = preservedUsers + UserSaveRecord(activeUser, runtime.userSaveValues(content))
        val scoreParties =
            runtime.scoreParties
                .filterNot { party -> party.userId == activeUser } +
                runtime.party.toRuntimeScoreParty(activeUser)
        val heroes =
            existingValues
                .heroEntries()
                .withDerivedHeroes(scoreParties, hallOfHeroesCompletedBy, content)
        val values =
            runtime
                .worldSaveValues(bundleHash, content)
                .toMutableMap()
        values.putHeroEntries(heroes)
        values.putUserRecords(userRecords)
        SaveFileStorage.replace(target, SaveCodec.encodeValues(values))
    }

    fun read(
        file: File,
        content: CompiledContentBundle,
        stateLookup: (String) -> CompiledStateImage,
        userId: String,
        hallOfHeroesCompletedBy: String? = null,
    ): RuntimeState {
        val values = file.readSaveValues()
        values.requireReadableSave()

        val saveUser = userId.normalizedSaveUser()
        val baseState = RuntimeState.from(stateLookup(values.required("state")), content)
        val userValues = values.userValuesFor(userId)
        if (userValues.hasPartyFields()) validateSavedPartyMemberCount(userValues, baseState.partyMemberMax)
        val activeParty = userValues.readParty(baseState.party, content)
        val scoreParties = values.scoreParties(baseState.party, content, saveUser)
        return baseState.copy(
            day = values.intOrDefault("realm.day", baseState.day),
            hour = values.intOrDefault("realm.hour", baseState.hour),
            minute = values.intOrDefault("realm.minute", baseState.minute),
            currentMap = userValues.handleOrDefault("realm.currentMap", CompiledHandleKind.WorldMap, content, baseState.currentMap),
            currentMode = userValues.runtimeModeOrDefault("realm.currentMode", baseState.currentMode),
            currentTown = userValues.optionalHandle("realm.currentTown", CompiledHandleKind.Town, content) ?: baseState.currentTown,
            lastTerrain = userValues.optionalHandle("realm.lastTerrain", CompiledHandleKind.Terrain, content) ?: baseState.lastTerrain,
            lastPlayedDate =
                userValues.optionalDate("realm.lastPlayedDate")
                    ?: values.optionalDate("realm.lastPlayedDate")
                    ?: baseState.lastPlayedDate,
            partyMemberMax = values.intOrDefault("realm.partyMemberMax", baseState.partyMemberMax),
            locations = values.readLocations(baseState.locations, content),
            npcs = values.readNpcs(baseState.npcs, content),
            completedMissions =
                values.handleListOrDefault("missions.completed", CompiledHandleKind.Mission, content, baseState.completedMissions),
            dungeon = userValues.readDungeon(content) ?: baseState.dungeon,
            castle = userValues.readCastle() ?: baseState.castle,
            party = activeParty,
            activeUserId = saveUser,
            scoreParties = scoreParties,
            hallOfHeroes = values.heroEntries().withDerivedHeroes(scoreParties, hallOfHeroesCompletedBy, content),
        )
    }

    fun hasUserParty(
        file: File,
        userId: String,
    ): Boolean =
        file.isFile &&
            runCatching {
                val values = file.readSaveValues()
                values.requireReadableSave()
                values.hasUserParty(userId)
            }.getOrDefault(false)

    fun removeUser(
        file: File,
        userId: String,
    ): Boolean =
        SaveFileStorage.update(file) { target ->
            if (!target.isFile) return@update true
            val values = target.readSaveValues()
            values.requireReadableSave()
            val records = values.userRecords()
            if (records.isEmpty()) return@update target.delete()
            val nextRecords = records.filterNot { record -> record.id == userId.normalizedSaveUser() }
            val nextValues = values.withoutUserRecords().toMutableMap()
            nextValues.putUserRecords(nextRecords)
            SaveFileStorage.replace(target, SaveCodec.encodeValues(nextValues))
            true
        }

    fun lines(file: File): List<String> = file.readLines()

    /** Content hash of the bundle that last wrote [file], or null when there is no readable save. */
    fun savedBundleHash(file: File): String? = runCatching { file.readSaveValues()["bundleHash"] }.getOrNull()

    private fun RuntimeState.worldSaveValues(
        bundleHash: String,
        content: CompiledContentBundle,
    ): Map<String, String> =
        buildMap {
            put("format", SAVE_FORMAT)
            put("version", SAVE_VERSION)
            put("saveModel", SAVE_MODEL)
            put("bundleHash", bundleHash)
            put("state", stateSlug)
            put("realm.day", day.toString())
            put("realm.hour", hour.toString())
            put("realm.minute", minute.toString())
            put("realm.lastPlayedDate", lastPlayedDate?.toString().orEmpty())
            put("realm.partyMemberMax", partyMemberMax.toString())
            put("missions.completed", completedMissions.joinToString(",") { mission -> content.slug(mission) })
            putLocations(locations, content)
            putNpcs(npcs, content)
        }

    private fun RuntimeState.userSaveValues(content: CompiledContentBundle): Map<String, String> =
        buildMap {
            put("realm.currentMap", content.slug(currentMap))
            put("realm.currentMode", currentMode.name)
            put("realm.currentTown", currentTown?.let(content::slug).orEmpty())
            put("realm.lastTerrain", lastTerrain?.let(content::slug).orEmpty())
            put("realm.lastPlayedDate", lastPlayedDate?.toString().orEmpty())
            dungeon?.let { state -> putDungeon(state, content) }
            castle?.let { state -> putCastle(state) }
            putParty(party, content)
        }

    private fun MutableMap<String, String>.putLocations(
        locations: List<CompiledLocationPlacement>,
        content: CompiledContentBundle,
    ) {
        val rows = locations.sortedBy { placement -> content.slug(placement.location) }
        put("locations.count", rows.size.toString())
        rows.forEachIndexed { index, placement ->
            val prefix = oneBasedRecordPrefix("location", index)
            put("$prefix.id", content.slug(placement.location))
            put("$prefix.x", placement.point.x.toString())
            put("$prefix.y", placement.point.y.toString())
        }
    }

    private fun MutableMap<String, String>.putNpcs(
        npcs: List<CompiledNpcPlacement>,
        content: CompiledContentBundle,
    ) {
        val rows = npcs.sortedBy { placement -> content.slug(placement.npc) }
        put("npcs.count", rows.size.toString())
        rows.forEachIndexed { index, placement ->
            val prefix = oneBasedRecordPrefix("npc", index)
            put("$prefix.id", content.slug(placement.npc))
            putWorldPosition(prefix, placement.position, content)
        }
    }

    private fun MutableMap<String, String>.putWorldPosition(
        prefix: String,
        position: CompiledWorldPosition,
        content: CompiledContentBundle,
    ) {
        when (position) {
            is CompiledWorldPosition.Wilderness -> {
                put("$prefix.position", "wilderness")
                put("$prefix.x", position.point.x.toString())
                put("$prefix.y", position.point.y.toString())
            }

            is CompiledWorldPosition.Town -> {
                put("$prefix.position", "town")
                put("$prefix.town", content.slug(position.town))
                put("$prefix.building", content.slug(position.building))
            }
        }
    }

    private fun MutableMap<String, String>.putDungeon(
        dungeon: DungeonState,
        content: CompiledContentBundle,
    ) {
        put("dungeon.id", content.slug(dungeon.dungeon))
        put("dungeon.maxDepth", dungeon.maxDepth.toString())
        put("dungeon.depth", dungeon.depth.toString())
        put(
            "dungeon.levelEvents",
            dungeon.levelEvents.entries
                .sortedBy(Map.Entry<Int, CompiledHandleRef>::key)
                .joinToString(",") { (level, event) -> "$level:${content.slug(event)}" },
        )
        dungeon.postDungeon?.let { continuation -> putPostDungeon(continuation, content) }
    }

    private fun MutableMap<String, String>.putCastle(castle: CastleState) {
        put("castle.id", castle.castleId)
        put("castle.floor", castle.floor.toString())
        put("castle.x", castle.point.x.toString())
        put("castle.y", castle.point.y.toString())
        put("castle.roomEntryPending", castle.roomEntryPending.toString())
        put("castle.visited", castle.visited.encodePointSet())
        put(
            "castle.rooms",
            castle.rooms.entries
                .sortedWith(compareBy<Map.Entry<GridPoint, String>> { it.key.y }.thenBy { it.key.x })
                .joinToString(",") { (point, event) -> "${point.x}:${point.y}:$event" },
        )
    }

    private fun MutableMap<String, String>.putParty(
        party: PartyState,
        content: CompiledContentBundle,
    ) {
        put("party.id", content.slug(party.id))
        put("party.playerString", party.playerString.toString())
        put("party.name", party.name)
        put("party.x", party.position.x.toString())
        put("party.y", party.position.y.toString())
        put("party.facing", party.facing?.name.orEmpty())
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
        put("party.monsterKills", party.monsterKills.entries.joinToString(",") { (monster, count) -> "${content.slug(monster)}:$count" })
        put("party.castleFloorsOpened", party.castleFloorsOpened.entries.joinToString(",") { (castle, floors) -> "$castle:$floors" })
        put("party.waitingMail", party.counters.waitingMail.toString())
        put("party.attacked", party.counters.attacked.toString())
        put(
            "party.attackedToday",
            party.counters.attackedToday.joinToString(",") { record -> "${content.slug(record.party)}:${record.attacked}" },
        )
        put("party.rngState", party.rngState?.toString().orEmpty())
        put("party.flags", party.flags.joinToString(",") { flag -> content.slug(flag) })
        put("party.milestones", party.milestones.joinToString(",") { milestone -> content.slug(milestone) })
        put(
            "party.visitedNpcs",
            party.visitedNpcs
                .filter(CompiledNpcVisit::visited)
                .joinToString(",") { visit -> content.slug(visit.npc) },
        )
        put("party.journal.count", party.journal.size.toString())
        party.journal.forEachIndexed { index, line ->
            put(oneBasedRecordPrefix("party.journal", index), line)
        }
        party.inventory.forEach { slot ->
            put("inventory.${slot.slot}", slot.item?.let(content::slug).orEmpty())
        }
        party.members.forEachIndexed { index, member ->
            putMember(index + 1, member, party.memberNames.getOrNull(index).orEmpty(), content)
        }
    }

    private fun MutableMap<String, String>.putMember(
        number: Int,
        member: CompiledPartyMember,
        memberName: String,
        content: CompiledContentBundle,
    ) {
        val prefix = "member.$number"
        put("$prefix.name", memberName)
        put("$prefix.nameString", member.nameString.toString())
        put("$prefix.gender", member.gender.name)
        put("$prefix.race", member.race.name)
        put("$prefix.class", member.pcClass.name)
        put("$prefix.level", member.level.toString())
        put("$prefix.health", member.health.toString())
        put("$prefix.hitPoints", member.hitPoints.current.toString())
        put("$prefix.hitPointsMax", member.hitPoints.max.toString())
        put("$prefix.weaponSkill", member.weaponSkill.toString())
        put("$prefix.wisdom", member.wisdom.toString())
        put("$prefix.magic", member.mana.current.toString())
        put("$prefix.magicMax", member.mana.max.toString())
        put("$prefix.charisma", member.charisma.toString())
        put("$prefix.dexterity", member.dexterity.toString())
        put("$prefix.weapon", content.slug(member.equipment.weapon))
        put("$prefix.armor", content.slug(member.equipment.armor))
        put("$prefix.experience", member.experience.toString())
        put("$prefix.dead", member.dead.toString())
    }

    private fun MutableMap<String, String>.putPostDungeon(
        continuation: ScriptContinuationState,
        content: CompiledContentBundle,
    ) {
        put("dungeon.post.root", content.slug(continuation.root))
        put("dungeon.post.frames", SaveCodec.encodeFrames(continuation.frames))
        put("dungeon.post.locals", SaveCodec.encodeIntMap(continuation.locals))
        put("dungeon.post.textBindings", SaveCodec.encodeStringMap(continuation.textBindings))
        put("dungeon.post.activeInventorySlot", continuation.activeInventorySlot?.toString().orEmpty())
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

    private fun Map<String, String>.readParty(
        base: PartyState,
        content: CompiledContentBundle,
    ): PartyState =
        base.copy(
            id = optionalHandle("party.id", CompiledHandleKind.Party, content) ?: base.id,
            playerString = intOrDefault("party.playerString", base.playerString),
            name = get("party.name")?.takeIf(String::isNotEmpty) ?: base.name,
            position = GridPoint(intOrDefault("party.x", base.position.x), intOrDefault("party.y", base.position.y)),
            facing = optionalEnumOrDefault("party.facing", base.facing),
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
                    attackedToday = readAttackedToday(base.counters.attackedToday, content),
                ),
            flags = handleListOrDefault("party.flags", CompiledHandleKind.FlagState, content, base.flags),
            milestones = handleListOrDefault("party.milestones", CompiledHandleKind.Milestone, content, base.milestones),
            visitedNpcs = readVisitedNpcs(base.visitedNpcs, content),
            journal = readJournal(base.journal),
            inventory = readInventory(base.inventory, content),
            members = readMembers(base.members, content),
            memberNames = readMemberNames(base.memberNames),
            rngState = optionalLong("party.rngState") ?: base.rngState,
            monsterKills = readMonsterKills(base.monsterKills, content),
            castleFloorsOpened = readCastleFloorsOpened(base.castleFloorsOpened),
        )

    private fun Map<String, String>.readLocations(
        base: List<CompiledLocationPlacement>,
        content: CompiledContentBundle,
    ): List<CompiledLocationPlacement> {
        val count = optionalInt("locations.count") ?: return base
        return (1..count).map { number ->
            CompiledLocationPlacement(
                location = content.ref(CompiledHandleKind.Location, required("location.$number.id")),
                point = GridPoint(required("location.$number.x").toInt(), required("location.$number.y").toInt()),
            )
        }
    }

    private fun Map<String, String>.readNpcs(
        base: List<CompiledNpcPlacement>,
        content: CompiledContentBundle,
    ): List<CompiledNpcPlacement> {
        val count = optionalInt("npcs.count") ?: return base
        return (1..count).map { number ->
            CompiledNpcPlacement(
                npc = content.ref(CompiledHandleKind.Npc, required("npc.$number.id")),
                position = readWorldPosition("npc.$number", content),
            )
        }
    }

    private fun Map<String, String>.readWorldPosition(
        prefix: String,
        content: CompiledContentBundle,
    ): CompiledWorldPosition {
        val position = required("$prefix.position")
        if (position == "wilderness") {
            return CompiledWorldPosition.Wilderness(
                GridPoint(required("$prefix.x").toInt(), required("$prefix.y").toInt()),
            )
        }
        if (position == "town") {
            return CompiledWorldPosition.Town(
                town = content.ref(CompiledHandleKind.Town, required("$prefix.town")),
                building = content.ref(CompiledHandleKind.Building, required("$prefix.building")),
            )
        }
        error("unknown compiled save world position $position")
    }

    private fun Map<String, String>.readDungeon(content: CompiledContentBundle): DungeonState? {
        val dungeon = optionalHandle("dungeon.id", CompiledHandleKind.Dungeon, content) ?: return null
        return DungeonState(
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
            postDungeon = readPostDungeon(content),
        )
    }

    private fun Map<String, String>.readPostDungeon(content: CompiledContentBundle): ScriptContinuationState? {
        val root = optionalHandle("dungeon.post.root", CompiledHandleKind.ScriptRoot, content) ?: return null
        val frames = get("dungeon.post.frames")?.takeIf(String::isNotEmpty)?.let(SaveCodec::decodeFrames).orEmpty()
        if (frames.isEmpty()) return null
        return ScriptContinuationState(
            root = root,
            frames = frames,
            locals = SaveCodec.decodeIntMap(get("dungeon.post.locals").orEmpty()),
            textBindings = SaveCodec.decodeStringMap(get("dungeon.post.textBindings").orEmpty()),
            activeInventorySlot = optionalInt("dungeon.post.activeInventorySlot"),
        )
    }

    private fun Map<String, String>.readCastle(): CastleState? {
        val castleId = get("castle.id")?.takeIf(String::isNotEmpty) ?: return null
        val point = GridPoint(intOrDefault("castle.x", 1), intOrDefault("castle.y", 1))
        return CastleState(
            castleId = castleId,
            floor = intOrDefault("castle.floor", 1),
            point = point,
            roomEntryPending = booleanOrDefault("castle.roomEntryPending", false),
            rooms =
                get("castle.rooms")
                    ?.takeIf(String::isNotEmpty)
                    ?.split(',')
                    ?.associate { token ->
                        val parts = token.split(':')
                        GridPoint(parts[0].toInt(), parts[1].toInt()) to parts[2]
                    }.orEmpty(),
            visited = get("castle.visited").decodePointSet().ifEmpty { setOf(point) },
        )
    }

    private fun Set<GridPoint>.encodePointSet(): String =
        sortedWith(compareBy<GridPoint> { point -> point.y }.thenBy { point -> point.x })
            .joinToString(",") { point -> "${point.x}:${point.y}" }

    private fun String?.decodePointSet(): Set<GridPoint> =
        orEmpty()
            .takeIf(String::isNotEmpty)
            ?.split(',')
            ?.mapTo(mutableSetOf()) { token ->
                val parts = token.split(':')
                GridPoint(parts[0].toInt(), parts[1].toInt())
            }.orEmpty()

    private fun Map<String, String>.readCastleFloorsOpened(base: Map<String, Int>): Map<String, Int> {
        val raw = get("party.castleFloorsOpened") ?: return base
        return raw
            .split(',')
            .filter(String::isNotEmpty)
            .associate { token ->
                val (castle, floors) = token.split(':')
                castle to floors.toInt()
            }
    }

    private fun Map<String, String>.readMonsterKills(
        base: Map<CompiledHandleRef, Int>,
        content: CompiledContentBundle,
    ): Map<CompiledHandleRef, Int> {
        val raw = get("party.monsterKills") ?: return base
        return raw
            .split(',')
            .filter(String::isNotEmpty)
            .associate { token ->
                val (monster, count) = token.split(':')
                content.ref(CompiledHandleKind.Monster, monster) to count.toInt()
            }
    }

    private fun Map<String, String>.readAttackedToday(
        base: List<CompiledPartyAttackRecord>,
        content: CompiledContentBundle,
    ): List<CompiledPartyAttackRecord> {
        val raw = get("party.attackedToday") ?: return base
        if (raw.isEmpty()) return emptyList()
        return raw
            .split(',')
            .filter(String::isNotEmpty)
            .map { token ->
                val parts = token.split(':')
                CompiledPartyAttackRecord(
                    party = content.ref(CompiledHandleKind.Party, parts[0]),
                    attacked = parts.getOrElse(1) { "false" }.toBooleanStrict(),
                )
            }
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

    private fun Map<String, String>.readMembers(
        base: List<CompiledPartyMember>,
        content: CompiledContentBundle,
    ): List<CompiledPartyMember> =
        base.mapIndexed { index, member ->
            val number = index + 1
            val prefix = "member.$number"
            member.copy(
                nameString = intOrDefault("$prefix.nameString", member.nameString),
                gender = enumOrDefault("$prefix.gender", member.gender),
                race = enumOrDefault("$prefix.race", member.race),
                pcClass = enumOrDefault("$prefix.class", member.pcClass),
                level = intOrDefault("$prefix.level", member.level),
                health = intOrDefault("$prefix.health", member.health),
                hitPoints =
                    StatPool(
                        intOrDefault("$prefix.hitPoints", member.hitPoints.current),
                        intOrDefault("$prefix.hitPointsMax", member.hitPoints.max),
                    ),
                weaponSkill = intOrDefault("$prefix.weaponSkill", member.weaponSkill),
                wisdom = intOrDefault("$prefix.wisdom", member.wisdom),
                mana =
                    StatPool(
                        intOrDefault("$prefix.magic", member.mana.current),
                        intOrDefault("$prefix.magicMax", member.mana.max),
                    ),
                charisma = intOrDefault("$prefix.charisma", member.charisma),
                dexterity = requiredInt("$prefix.dexterity"),
                equipment = readEquipment(prefix, member.equipment, content),
                experience = intOrDefault("$prefix.experience", member.experience),
                dead = booleanOrDefault("$prefix.dead", member.dead),
            )
        }

    private fun Map<String, String>.readEquipment(
        prefix: String,
        base: CompiledEquipment,
        content: CompiledContentBundle,
    ): CompiledEquipment =
        CompiledEquipment(
            weapon = optionalHandle("$prefix.weapon", CompiledHandleKind.Item, content) ?: base.weapon,
            armor = optionalHandle("$prefix.armor", CompiledHandleKind.Item, content) ?: base.armor,
        )

    private fun Map<String, String>.readMemberNames(base: List<String>): List<String> =
        base.mapIndexed { index, name ->
            get("member.${index + 1}.name")?.takeIf(String::isNotEmpty) ?: name
        }

    private fun Map<String, String>.readJournal(base: List<String>): List<String> {
        val count = optionalInt("party.journal.count") ?: return base
        return (1..count).mapNotNull { index -> get("party.journal.$index")?.takeIf(String::isNotEmpty) }
    }

    private fun File.readSaveValues(): Map<String, String> = SaveCodec.decodeValues(readLines())

    private fun File.existingSaveValuesOrEmpty(): Map<String, String> {
        if (!isFile) return emptyMap()
        return readSaveValues()
    }

    private fun Map<String, String>.userValuesFor(userId: String): Map<String, String> {
        val users = userRecords()
        val user = users.firstOrNull { record -> record.id == userId.normalizedSaveUser() }
        if (user != null) return user.values
        if (users.isNotEmpty()) return emptyMap()
        if (hasPartyFields()) return this
        return emptyMap()
    }

    private fun Map<String, String>.hasUserParty(userId: String): Boolean {
        val users = userRecords()
        if (users.isEmpty()) return hasPartyFields()
        val user = users.firstOrNull { record -> record.id == userId.normalizedSaveUser() }
        return user?.values?.hasPartyFields() == true
    }

    private fun Map<String, String>.hasPartyFields(): Boolean =
        keys.any { key ->
            key.startsWith("party.") ||
                key.startsWith("member.") ||
                key.startsWith("inventory.")
        }

    private fun Map<String, String>.userRecords(): List<UserSaveRecord> {
        val count = optionalInt(USERS_COUNT) ?: return emptyList()
        return (1..count).mapNotNull { number ->
            val prefix = "user.$number."
            val id = get("${prefix}id")?.takeIf(String::isNotBlank)?.normalizedSaveUser()
            id?.let { saveUser ->
                UserSaveRecord(
                    id = saveUser,
                    values =
                        entries
                            .asSequence()
                            .filter { (key, _) -> key.startsWith(prefix) && key != "${prefix}id" }
                            .associate { (key, value) -> key.removePrefix(prefix) to value },
                )
            }
        }
    }

    private fun Map<String, String>.scoreParties(
        base: PartyState,
        content: CompiledContentBundle,
        legacyUserId: String,
    ): List<RuntimeScoreParty> {
        val records = userRecords()
        val parties =
            if (records.isEmpty()) {
                if (hasPartyFields()) listOf(UserSaveRecord(legacyUserId, this)) else emptyList()
            } else {
                records.filter { record -> record.values.hasPartyFields() }
            }
        return parties
            .map { record -> record.values.readParty(base, content).toRuntimeScoreParty(record.id) }
            .sortedWith(
                compareByDescending<RuntimeScoreParty> {
                    it.averageExperience
                }.thenByDescending { it.averageLevel }.thenBy { it.partyName },
            )
    }

    private fun MutableMap<String, String>.putUserRecords(records: List<UserSaveRecord>) {
        val sorted = records.sortedBy(UserSaveRecord::id)
        put(USERS_COUNT, sorted.size.toString())
        sorted.forEachIndexed { index, record ->
            val prefix = oneBasedRecordPrefix("user", index)
            put("$prefix.id", record.id)
            record.values.entries.sortedBy(Map.Entry<String, String>::key).forEach { (key, value) ->
                put("$prefix.$key", value)
            }
        }
    }

    private fun Map<String, String>.withoutUserRecords(): Map<String, String> =
        filterKeys { key ->
            key != USERS_COUNT && !key.startsWith("user.")
        }

    private fun Map<String, String>.heroEntries(): List<RuntimeHeroEntry> {
        val count = optionalInt(HEROES_COUNT) ?: return emptyList()
        return (1..count).mapNotNull { number ->
            val prefix = "hero.$number"
            val userId = get("$prefix.user")?.takeIf(String::isNotBlank)?.normalizedSaveUser()
            val partyName = get("$prefix.party")?.takeIf(String::isNotBlank)
            if (userId == null || partyName == null) null else RuntimeHeroEntry(userId, partyName)
        }
    }

    private fun MutableMap<String, String>.putHeroEntries(entries: List<RuntimeHeroEntry>) {
        val sorted = entries.distinctBy { entry -> entry.userId to entry.partyName }.sortedBy(RuntimeHeroEntry::partyName)
        put(HEROES_COUNT, sorted.size.toString())
        sorted.forEachIndexed { index, entry ->
            val prefix = oneBasedRecordPrefix("hero", index)
            put("$prefix.user", entry.userId)
            put("$prefix.party", entry.partyName)
        }
    }

    private fun List<RuntimeHeroEntry>.withDerivedHeroes(
        scoreParties: List<RuntimeScoreParty>,
        completedBy: String?,
        content: CompiledContentBundle,
    ): List<RuntimeHeroEntry> {
        if (completedBy == null) return this
        val derived =
            scoreParties
                .filter { party -> party.milestones.any { milestone -> content.slug(milestone) == completedBy } }
                .map { party -> RuntimeHeroEntry(party.userId, party.partyName) }
        return (this + derived)
            .distinctBy { entry -> entry.userId to entry.partyName }
            .sortedBy(RuntimeHeroEntry::partyName)
    }

    private fun Map<String, String>.savedMemberSlots(): Set<Int> =
        keys
            .mapNotNull { key ->
                memberFieldKey
                    .find(key)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
            }.toSet()

    private fun oneBasedRecordPrefix(
        family: String,
        zeroBasedIndex: Int,
    ): String = "$family.${zeroBasedIndex + 1}"

    private fun Map<String, String>.required(key: String): String = get(key) ?: error("compiled save missing $key")

    // Saves name content by slug, so a save written by an older bundle still
    // loads after content changes; only a slug that no longer exists fails.
    private fun Map<String, String>.requireReadableSave() {
        requireValue("format", SAVE_FORMAT)
        requireValue("version", SAVE_VERSION)
    }

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

    private fun Map<String, String>.requiredInt(key: String): Int = required(key).toInt()

    private fun Map<String, String>.optionalInt(key: String): Int? = get(key)?.takeIf(String::isNotEmpty)?.toInt()

    private fun Map<String, String>.optionalLong(key: String): Long? = get(key)?.takeIf(String::isNotEmpty)?.toLong()

    private fun Map<String, String>.optionalDate(key: String): LocalDate? =
        get(key)
            ?.takeIf(String::isNotEmpty)
            ?.let(LocalDate::parse)

    private fun Map<String, String>.booleanOrDefault(
        key: String,
        default: Boolean,
    ): Boolean = get(key)?.takeIf(String::isNotEmpty)?.toBooleanStrict() ?: default

    private inline fun <reified T : Enum<T>> Map<String, String>.enumOrDefault(
        key: String,
        default: T,
    ): T = get(key)?.takeIf(String::isNotEmpty)?.let { value -> enumValueOf<T>(value) } ?: default

    private inline fun <reified T : Enum<T>> Map<String, String>.optionalEnumOrDefault(
        key: String,
        default: T?,
    ): T? = get(key)?.takeIf(String::isNotEmpty)?.let { value -> enumValueOf<T>(value) } ?: default

    private fun Map<String, String>.runtimeModeOrDefault(
        key: String,
        default: RuntimeMode,
    ): RuntimeMode = enumOrDefault(key, default)

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
    ): CompiledHandleRef? = get(key)?.takeIf(String::isNotEmpty)?.let { slug -> content.ref(kind, slug.legacySaveSlug(kind)) }

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
            ?.map { slug -> content.ref(kind, slug.legacySaveSlug(kind)) }
            ?: default

    private fun String.legacySaveSlug(kind: CompiledHandleKind): String =
        when {
            kind == CompiledHandleKind.Item && (this == "halbard" || this == "halbdrd") -> "halberd"
            else -> this
        }
}

internal fun String.normalizedSaveUser(): String {
    val normalized =
        trim()
            .lowercase()
            .map { char ->
                when {
                    char.isLetterOrDigit() -> char
                    char == '-' -> char
                    char == '_' -> char
                    else -> '_'
                }
            }.joinToString("")
    require(normalized.any(Char::isLetterOrDigit)) { "runtime user id must contain a letter or digit" }
    return normalized
}

private data class UserSaveRecord(
    val id: String,
    val values: Map<String, String>,
)
