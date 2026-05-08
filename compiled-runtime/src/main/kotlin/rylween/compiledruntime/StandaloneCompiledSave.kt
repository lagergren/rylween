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

/** Reads and writes standalone compiled-runtime wilderness saves. */
internal object StandaloneCompiledSave {
    fun write(
        file: File,
        runtime: StandaloneRuntimeState,
        bundleHash: String,
        content: CompiledContentBundle,
    ) {
        require(runtime.currentMode == RuntimeMode.Wilderness) {
            "standalone compiled runtime currently saves only wilderness state"
        }
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
                putParty(runtime.party, content)
            }
        file.parentFile?.mkdirs()
        file.writeText(
            values
                .toSortedMap()
                .entries
                .joinToString(separator = "\n", postfix = "\n") { (key, value) ->
                    "${escape(key)}=${escape(value)}"
                },
        )
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
        return baseState.copy(
            day = values.intOrDefault("realm.day", baseState.day),
            hour = values.intOrDefault("realm.hour", baseState.hour),
            minute = values.intOrDefault("realm.minute", baseState.minute),
            currentMap = values.handleOrDefault("realm.currentMap", CompiledHandleKind.WorldMap, content, baseState.currentMap),
            currentMode = values["realm.currentMode"]?.let(RuntimeMode::valueOf) ?: baseState.currentMode,
            currentTown = values.optionalHandle("realm.currentTown", CompiledHandleKind.Town, content) ?: baseState.currentTown,
            lastTerrain = values.optionalHandle("realm.lastTerrain", CompiledHandleKind.Terrain, content) ?: baseState.lastTerrain,
            partyMemberMax = values.intOrDefault("realm.partyMemberMax", baseState.partyMemberMax),
            party = values.readParty(baseState.party, content),
        )
    }

    private fun MutableMap<String, String>.putParty(
        party: StandaloneParty,
        content: CompiledContentBundle,
    ) {
        put("party.id", content.slug(party.id))
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
            put("member.$number.hitPoints", member.hitPoints.current.toString())
            put("member.$number.magic", member.mana.current.toString())
            put("member.$number.dead", member.dead.toString())
        }
    }

    private fun Map<String, String>.readParty(
        base: StandaloneParty,
        content: CompiledContentBundle,
    ): StandaloneParty =
        base.copy(
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
            rngState = optionalLong("party.rngState") ?: base.rngState,
        )

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

    private fun File.readSaveValues(): Map<String, String> =
        readLines()
            .asSequence()
            .map(String::trim)
            .filter { line -> line.isNotEmpty() && !line.startsWith("#") }
            .associate { line ->
                val separator = line.indexOf('=')
                require(separator > 0) { "invalid standalone compiled save line: $line" }
                unescape(line.substring(0, separator)) to unescape(line.substring(separator + 1))
            }

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

    private fun escape(value: String): String =
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

    private fun unescape(value: String): String {
        val out = StringBuilder()
        var escaped = false
        value.forEach { char ->
            if (escaped) {
                when (char) {
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    else -> out.append(char)
                }
                escaped = false
            } else {
                when (char) {
                    '\\' -> escaped = true
                    else -> out.append(char)
                }
            }
        }
        if (escaped) out.append('\\')
        return out.toString()
    }
}
