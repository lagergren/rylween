// Reusable live runtime state, deterministic RNG, and NPC placement realization.
//
// This file is intentionally backend-neutral. It should hold state/session
// plumbing only; current-game rules discovered here should move into authored
// game DSL/model records.

package rylween.runtime

import rylween.dsl.BuildingId
import rylween.dsl.CharacterModel
import rylween.dsl.CharacterPlacementModel
import rylween.dsl.DslDefaultBuildings
import rylween.dsl.DslDefaultMaps
import rylween.dsl.GridPoint
import rylween.dsl.LocationPlacement
import rylween.dsl.NpcPlacement
import rylween.dsl.PartyState
import rylween.dsl.RealmState
import rylween.dsl.RuntimeMode
import rylween.dsl.StateModel
import rylween.dsl.TownId
import rylween.dsl.TownModel
import rylween.dsl.Wilderness
import rylween.dsl.WorldPosition
import rylween.dsl.character
import rylween.dsl.random
import rylween.dsl.town

data class RuntimeSessionState(
    val realm: RealmState,
    val parties: List<PartyState>,
    val npcs: List<NpcPlacement> = emptyList(),
    val locations: List<LocationPlacement> = emptyList(),
) {
    val activeParty: PartyState
        get() =
            parties.firstOrNull { party -> party.id == realm.activeParty }
                ?: error("active party ${realm.activeParty.slug} is missing from runtime state")

    fun withRealm(realm: RealmState): RuntimeSessionState = copy(realm = realm)

    fun withActiveParty(party: PartyState): RuntimeSessionState {
        val nextParties =
            if (parties.any { it.id == party.id }) {
                parties.map { existing -> if (existing.id == party.id) party else existing }
            } else {
                parties + party
            }
        return copy(
            realm = realm.copy(activeParty = party.id),
            parties = nextParties,
        )
    }

    companion object {
        fun singleParty(
            party: PartyState,
            hour: Int,
            currentTown: TownId?,
            rngState: Long? = null,
            npcs: List<NpcPlacement> = emptyList(),
            locations: List<LocationPlacement> = emptyList(),
        ): RuntimeSessionState =
            RuntimeSessionState(
                realm =
                    RealmState(
                        day = 0,
                        hour = hour,
                        minute = 0,
                        rngState = rngState,
                        activeParty = party.id,
                        currentMap = DslDefaultMaps.wilderness,
                        currentMode = RuntimeMode.Town,
                        currentTown = currentTown,
                        banished = false,
                        lastPlayedDay = 0,
                        partyMemberMax = 4,
                    ),
                parties = listOf(party),
                npcs = npcs,
                locations = locations,
            )
    }
}

fun StateModel.toRuntimeSessionState(rngState: Long? = realm.rngState): RuntimeSessionState =
    RuntimeSessionState(
        realm = realm.copy(rngState = rngState),
        parties = parties,
        npcs = npcs,
        locations = locations,
    )

data class RylweenRandomDraw(
    val value: Int,
    val nextSeed: Long,
)

data class RuntimeRandomDraw(
    val value: Int,
    val runtime: RuntimeSessionState,
)

interface RylweenRandomPort {
    fun draw(
        seed: Long,
        lo: Int,
        hi: Int,
    ): RylweenRandomDraw
}

object ReferenceRylweenRandom : RylweenRandomPort {
    private const val MULTIPLIER: Long = 0x2545F4914F6CDD1DL

    override fun draw(
        seed: Long,
        lo: Int,
        hi: Int,
    ): RylweenRandomDraw {
        require(lo <= hi) { "random range must be ascending: $lo..$hi" }
        var state = seed.takeUnless { it == 0L } ?: 1L
        state = state xor (state ushr 12)
        state = state xor (state shl 25)
        state = state xor (state ushr 27)
        val mixed = state * MULTIPLIER
        val span = hi.toLong() - lo.toLong() + 1L
        val offset = java.lang.Long.remainderUnsigned(mixed, span)
        return RylweenRandomDraw((lo.toLong() + offset).toInt(), state)
    }
}

fun RuntimeSessionState.drawRandom(
    lo: Int,
    hi: Int,
    random: RylweenRandomPort = ReferenceRylweenRandom,
): RuntimeRandomDraw {
    require(lo <= hi) { "random range must be ascending: $lo..$hi" }
    val seed = realm.rngState ?: return RuntimeRandomDraw(lo, this)
    val draw = random.draw(seed, lo, hi)
    return RuntimeRandomDraw(draw.value, withRealm(realm.copy(rngState = draw.nextSeed)))
}

data class WildernessNpcPlacementPolicy(
    val x: IntRange = 8..57,
    val y: IntRange = 8..27,
    val allowedPoints: Set<GridPoint>? = null,
    val maxAttempts: Int = 1024,
) {
    init {
        require(x.first <= x.last) { "wilderness NPC x range must be ascending: $x" }
        require(y.first <= y.last) { "wilderness NPC y range must be ascending: $y" }
        require(maxAttempts > 0) { "wilderness NPC placement attempts must be positive" }
    }

    fun allows(point: GridPoint): Boolean = allowedPoints?.contains(point) ?: true
}

data class NpcPlacementRealizerConfig(
    val townSlack: Int = 1,
    val townBuildings: List<BuildingId> = listOf(DslDefaultBuildings.inn, DslDefaultBuildings.tavern),
    val wilderness: WildernessNpcPlacementPolicy = WildernessNpcPlacementPolicy(),
    val deterministicSeed: Long = 1L,
)

fun RuntimeSessionState.realizeNpcPlacements(
    characters: List<CharacterModel>,
    towns: List<TownModel>,
    config: NpcPlacementRealizerConfig = NpcPlacementRealizerConfig(),
    random: RylweenRandomPort = ReferenceRylweenRandom,
): RuntimeSessionState {
    require(config.townBuildings.isNotEmpty()) { "NPC placement requires at least one town building target" }
    val bins = listOf<TownId?>(null) + towns.map { it.id }
    val randomTownOrWildernessCount = characters.count { it.placement == CharacterPlacementModel.Randomized }
    val capacity = ((randomTownOrWildernessCount + bins.lastIndex) / bins.size) + config.townSlack.coerceAtLeast(0)
    val counts = IntArray(bins.size)
    val placements = mutableListOf<NpcPlacement>()
    val occupiedWilderness =
        characters
            .mapNotNull { (it.placement as? CharacterPlacementModel.Fixed)?.position as? WorldPosition.Wilderness }
            .mapTo(mutableSetOf()) { it.point }
    val fixtureRng = realm.rngState == null
    var runtime = if (fixtureRng) withRealm(realm.copy(rngState = config.deterministicSeed)) else this

    characters.forEach { character ->
        val position =
            when (val placement = character.placement) {
                is CharacterPlacementModel.Fixed -> placement.position
                CharacterPlacementModel.Randomized -> {
                    val draw = runtime.drawRandom(0, bins.lastIndex, random)
                    runtime = draw.runtime
                    val bin = firstAvailableBin(draw.value, counts, capacity)
                    counts[bin]++
                    val realized =
                        bins[bin]?.let { town -> runtime.drawTownBuilding(town, config.townBuildings, random) }
                            ?: runtime.drawWildernessHutPoint(occupiedWilderness, config.wilderness, random)
                    runtime = realized.runtime
                    realized.position
                }
                CharacterPlacementModel.RandomizedWilderness -> {
                    val realized = runtime.drawWildernessHutPoint(occupiedWilderness, config.wilderness, random)
                    runtime = realized.runtime
                    realized.position
                }
            }
        placements += NpcPlacement(character.id, position)
        (position as? WorldPosition.Wilderness)?.let { occupiedWilderness += it.point }
    }

    val realizedRuntime = if (fixtureRng) runtime.withRealm(runtime.realm.copy(rngState = null)) else runtime
    return realizedRuntime.copy(npcs = placements)
}

private fun firstAvailableBin(
    start: Int,
    counts: IntArray,
    capacity: Int,
): Int {
    require(capacity > 0) { "NPC placement bin capacity must be positive" }
    var bin = start
    repeat(counts.size) {
        if (counts[bin] < capacity) return bin
        bin = (bin + 1) % counts.size
    }
    error("no NPC placement bin has remaining capacity")
}

private data class NpcPositionDraw(
    val runtime: RuntimeSessionState,
    val position: WorldPosition,
)

private fun RuntimeSessionState.drawTownBuilding(
    town: TownId,
    buildings: List<BuildingId>,
    random: RylweenRandomPort,
): NpcPositionDraw {
    val draw = drawRandom(0, buildings.lastIndex, random)
    return NpcPositionDraw(draw.runtime, WorldPosition.Town(town, buildings[draw.value]))
}

private fun RuntimeSessionState.drawWildernessHutPoint(
    occupied: Set<GridPoint>,
    policy: WildernessNpcPlacementPolicy,
    random: RylweenRandomPort,
): NpcPositionDraw {
    var runtime = this
    repeat(policy.maxAttempts) {
        val x = runtime.drawRandom(policy.x.first, policy.x.last, random)
        val y = x.runtime.drawRandom(policy.y.first, policy.y.last, random)
        runtime = y.runtime
        val point = GridPoint(x.value, y.value)
        if (point !in occupied && policy.allows(point)) {
            return NpcPositionDraw(runtime, WorldPosition.Wilderness(point))
        }
    }
    error("could not place wilderness NPC after ${policy.maxAttempts} attempts")
}
