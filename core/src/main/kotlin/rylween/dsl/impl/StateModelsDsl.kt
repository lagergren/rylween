/**
 * Exact state model shared by authored fixtures, runtime saves, and compiled bundles.
 *
 * Content records describe the world rules and catalogs; these records describe
 * one concrete mutable world image: realm clock/mode, generated locations,
 * realized NPC positions, parties, resources, inventory, members, journal, and
 * runtime snapshot metadata identity. State builders live in `StateDsl.kt`, and
 * save/restore code serializes these records rather than a separate ad hoc
 * model.
 */
package rylween.dsl.impl

import rylween.dsl.*

enum class Gender { Male, Female }

enum class Race { Human, Dwarf, Elf, Halfling }

enum class PcClass { Fighter, Thief, Mage, Priest, Bard, Ranger }

val PcClass.sourceId: Int
    get() =
        when (this) {
            PcClass.Fighter -> 1
            PcClass.Thief -> 2
            PcClass.Mage -> 3
            PcClass.Priest -> 4
            PcClass.Bard -> 5
            PcClass.Ranger -> 6
        }

enum class RuntimeMode { Wilderness, Town, Dungeon, Battle, Castle, GameOver }

enum class Facing { North, East, South, West }

enum class StateKind { AuthoredFixture, RuntimeSnapshot }

const val InventorySaveSlotMax: Int = 16

data class StatPool(
    val current: Int,
    val max: Int,
)
typealias HitPoints = StatPool
typealias Mana = StatPool

infix fun Int.of(max: Int): StatPool = StatPool(this, max)

data class GridPoint(
    val x: Int,
    val y: Int,
)

data class GridOffset(
    val x: Int,
    val y: Int,
)

fun xy(
    x: Int,
    y: Int,
): GridPoint = GridPoint(x, y)

fun gridOffset(
    x: Int,
    y: Int,
): GridOffset = GridOffset(x, y)

operator fun GridPoint.plus(offset: GridOffset): GridPoint = GridPoint(x + offset.x, y + offset.y)

sealed interface WorldPosition {
    data class Wilderness(
        val point: GridPoint,
    ) : WorldPosition

    data class Town(
        val town: TownId,
        val building: BuildingId,
    ) : WorldPosition
}

object Wilderness {
    fun xy(
        x: Int,
        y: Int,
    ): WorldPosition = WorldPosition.Wilderness(GridPoint(x, y))
}

data class RealmState(
    val day: Int,
    val hour: Int,
    val minute: Int,
    val rngState: Long?,
    val activeParty: PartyId,
    val currentMap: WorldMapId,
    val currentMode: RuntimeMode,
    val currentTown: TownId?,
    val banished: Boolean,
    val partyMemberMax: Int,
    val lastTerrain: TerrainId? = null,
)

data class LocationPlacement(
    val location: LocationId,
    val point: GridPoint,
)

data class NpcPlacement(
    val npc: NpcId,
    val position: WorldPosition,
)

data class PartyResources(
    val gold: Int,
    val torches: Int,
    val food: Int,
    val timeLeft: Int,
    val restedHours: Int,
)

data class PartyCounters(
    val memberCount: Int?,
    val kills: Int,
    val waitingMail: Boolean,
    val attacked: Boolean,
    val attackedToday: List<PartyAttackRecord>,
)

data class PartyAttackRecord(
    val party: PartyId,
    val attacked: Boolean,
)

data class InventorySlotState(
    val slot: Int,
    val item: ItemId?,
)

data class EquipmentState(
    val weapon: ItemId,
    val armor: ItemId,
)

data class NpcVisitState(
    val npc: NpcId,
    val visited: Boolean,
)

sealed interface JournalEntryState {
    data class Text(
        val id: TextId,
    ) : JournalEntryState

    data class Line(
        val text: String,
    ) : JournalEntryState
}

data class PartyMemberState(
    val name: String,
    val gender: Gender,
    val race: Race,
    val pcClass: PcClass,
    val level: Int,
    val health: Int,
    val hitPoints: HitPoints,
    val weaponSkill: Int,
    val wisdom: Int,
    val mana: Mana,
    val charisma: Int,
    val equipment: EquipmentState,
    val experience: Int,
    val dead: Boolean,
) {
    val armor: ItemId get() = equipment.armor
    val weapon: ItemId get() = equipment.weapon
}

data class PartyState(
    val id: PartyId,
    val player: String,
    val name: String,
    val position: WorldPosition,
    val facing: Facing?,
    val resources: PartyResources,
    val counters: PartyCounters,
    val flags: List<FlagState>,
    val milestones: List<GameMilestoneId>,
    val inventory: List<InventorySlotState>,
    val members: List<PartyMemberState>,
    val visitedNpcs: List<NpcVisitState>,
    val journal: List<JournalEntryState>,
)

data class StateModel(
    val id: StateId,
    val kind: StateKind,
    val realm: RealmState,
    val locations: List<LocationPlacement>,
    val npcs: List<NpcPlacement>,
    val parties: List<PartyState>,
)

data class RylweenModel(
    val content: GameModel,
    val states: List<StateModel>,
)
