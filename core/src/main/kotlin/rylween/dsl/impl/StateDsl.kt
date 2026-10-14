/**
 * Authored and runtime state DSL.
 *
 * This file owns the builders for realm, location placement, character
 * placement, party, inventory, mission, member, and journal state.
 *
 * Content describes what can exist. These records describe a concrete world
 * snapshot that runtimes can boot from or save back out. The main game file
 * normally declares content, while optional scenario modules add authored
 * fixtures or runtime snapshots with [RylweenModel.withStateModule].
 */
package rylween.dsl.impl

import rylween.dsl.DslDefaultItems
import rylween.dsl.DslDefaultMaps
import rylween.dsl.GameBuilder
import rylween.dsl.GameMilestoneId
import rylween.dsl.ItemId
import rylween.dsl.LocationId
import rylween.dsl.NpcId
import rylween.dsl.PartyId
import rylween.dsl.RylNextDsl
import rylween.dsl.StateId
import rylween.dsl.TerrainId
import rylween.dsl.TextId
import rylween.dsl.TownId
import rylween.dsl.WorldMapId

@RylNextDsl
class RylweenBuilder {
    private var contentModel: GameModel? = null
    private val states = mutableListOf<StateModel>()

    fun content(body: GameBuilder.() -> Unit) {
        contentModel = GameBuilder().apply(body).build()
    }

    fun state(
        id: StateId,
        body: StateBuilder.() -> Unit,
    ) {
        states += StateBuilder(id, StateKind.AuthoredFixture).apply(body).build()
    }

    fun build(): RylweenModel =
        RylweenModel(
            content = checkNotNull(contentModel) { "rylween requires content { ... }" },
            states = states.toList(),
        )
}

fun rylween(body: RylweenBuilder.() -> Unit): RylweenModel = RylweenBuilder().apply(body).build()

@RylNextDsl
class RylweenStateModuleBuilder {
    private val states = mutableListOf<StateModel>()

    fun state(
        id: StateId,
        body: StateBuilder.() -> Unit,
    ) {
        stateFixture(id, body)
    }

    fun stateFixture(
        id: StateId,
        body: StateBuilder.() -> Unit,
    ) {
        states += StateBuilder(id, StateKind.AuthoredFixture).apply(body).build()
    }

    fun runtimeState(
        id: StateId,
        body: StateBuilder.() -> Unit,
    ) {
        states += StateBuilder(id, StateKind.RuntimeSnapshot).apply(body).build()
    }

    fun build(): List<StateModel> = states.toList()
}

fun RylweenModel.withStateModule(body: RylweenStateModuleBuilder.() -> Unit): RylweenModel =
    copy(states = states + RylweenStateModuleBuilder().apply(body).build())

@RylNextDsl
class StateBuilder(
    private val id: StateId,
    private val kind: StateKind,
) {
    private var realmState: RealmState? = null
    private val locationPlacements = mutableListOf<LocationPlacement>()
    private val npcPlacements = mutableListOf<NpcPlacement>()
    private val parties = mutableListOf<PartyState>()

    fun realm(body: RealmStateBuilder.() -> Unit) {
        realmState = RealmStateBuilder().apply(body).build()
    }

    fun locations(body: LocationStateBuilder.() -> Unit) {
        locationPlacements += LocationStateBuilder().apply(body).build()
    }

    fun npcs(body: NpcStateBuilder.() -> Unit) {
        npcPlacements += NpcStateBuilder().apply(body).build()
    }

    fun party(
        id: PartyId,
        body: PartyStateBuilder.() -> Unit,
    ) {
        parties += PartyStateBuilder(id).apply(body).build()
    }

    fun build(): StateModel =
        StateModel(
            id = id,
            kind = kind,
            realm = checkNotNull(realmState) { "state ${id.slug} requires realm { ... }" },
            locations = locationPlacements.toList(),
            npcs = npcPlacements.toList(),
            parties = parties.toList(),
        )
}

@RylNextDsl
class RealmStateBuilder {
    var day: Int = 0
    var hour: Int = 0
    var minute: Int = 0
    var rngState: Long? = null
    var activeParty: PartyId? = null
    private var currentMap: WorldMapId = DslDefaultMaps.wilderness
    var currentMode: RuntimeMode = RuntimeMode.Wilderness
    var currentTown: TownId? = null
    var banished: Boolean = false
    var partyMemberMax: Int = 4
    private var lastTerrain: TerrainId? = null

    fun wilderness(map: WorldMapId = DslDefaultMaps.wilderness) {
        enter(RuntimeMode.Wilderness, map)
    }

    fun town(
        town: TownId,
        map: WorldMapId = currentMap,
    ) {
        enter(RuntimeMode.Town, map, town)
    }

    fun dungeon(map: WorldMapId = currentMap) {
        enter(RuntimeMode.Dungeon, map)
    }

    fun battle(map: WorldMapId = currentMap) {
        enter(RuntimeMode.Battle, map)
    }

    fun castle(map: WorldMapId = currentMap) {
        enter(RuntimeMode.Castle, map)
    }

    fun gameOver(map: WorldMapId = currentMap) {
        enter(RuntimeMode.GameOver, map)
    }

    fun build(): RealmState =
        RealmState(
            day = day,
            hour = hour,
            minute = minute,
            rngState = rngState,
            activeParty = checkNotNull(activeParty) { "realm requires activeParty" },
            currentMap = currentMap,
            currentMode = currentMode,
            currentTown = currentTown,
            banished = banished,
            partyMemberMax = partyMemberMax,
            lastTerrain = lastTerrain,
        )

    private fun enter(
        mode: RuntimeMode,
        map: WorldMapId,
        town: TownId? = null,
    ) {
        currentMode = mode
        currentMap = map
        currentTown = town
    }
}

@RylNextDsl
class LocationStateBuilder {
    private val placements = mutableListOf<LocationPlacement>()

    infix fun LocationId.at(point: GridPoint) {
        placements += LocationPlacement(this, point)
    }

    fun build(): List<LocationPlacement> = placements.toList()
}

@RylNextDsl
class NpcStateBuilder {
    private val placements = mutableListOf<NpcPlacement>()

    infix fun NpcId.at(position: WorldPosition) {
        placements += NpcPlacement(this, position)
    }

    fun build(): List<NpcPlacement> = placements.toList()
}

@RylNextDsl
class PartyStateBuilder(
    private val id: PartyId,
) {
    var player: String = ""
    var name: String = ""
    var position: WorldPosition = Wilderness.xy(0, 0)
    var facing: Facing? = null
    private var resourcesState = PartyResources(0, 0, 0, 0, 0)
    private var countersState =
        PartyCounters(
            memberCount = null,
            kills = 0,
            waitingMail = false,
            attacked = false,
            attackedToday = emptyList(),
        )
    private val flags = mutableListOf<FlagState>()
    private val milestones = mutableListOf<GameMilestoneId>()
    private val inventory = mutableListOf<InventorySlotState>()
    private val members = mutableListOf<PartyMemberState>()
    private val visitedNpcs = mutableMapOf<NpcId, NpcVisitState>()
    private val journal = mutableListOf<JournalEntryState>()

    fun resources(body: PartyResourcesBuilder.() -> Unit) {
        resourcesState = PartyResourcesBuilder().apply(body).build()
    }

    fun counters(body: PartyCountersBuilder.() -> Unit) {
        countersState = PartyCountersBuilder().apply(body).build()
    }

    fun flags(body: StateFlagBuilder.() -> Unit) {
        flags += StateFlagBuilder().apply(body).build()
    }

    fun milestones(body: PartyMilestonesBuilder.() -> Unit) {
        milestones += PartyMilestonesBuilder().apply(body).build()
    }

    fun inventory(body: InventoryStateBuilder.() -> Unit) {
        inventory += InventoryStateBuilder().apply(body).build()
    }

    fun members(body: PartyMembersBuilder.() -> Unit) {
        members += PartyMembersBuilder().apply(body).build()
    }

    fun visited(vararg npcs: NpcId) {
        npcs.forEach { npc -> visitedNpcs[npc] = NpcVisitState(npc = npc, visited = true) }
    }

    fun npcVisits(body: NpcVisitBuilder.() -> Unit) {
        NpcVisitBuilder().apply(body).build().forEach { visitedNpcs[it.npc] = it }
    }

    fun journal(vararg entries: TextId) {
        journal += entries.map(JournalEntryState::Text)
    }

    fun journal(body: JournalBuilder.() -> Unit) {
        journal += JournalBuilder().apply(body).build()
    }

    fun build(): PartyState =
        PartyState(
            id = id,
            player = player,
            name = name,
            position = position,
            facing = facing,
            resources = resourcesState,
            counters = countersState,
            flags = flags.toList(),
            milestones = milestones.toList(),
            inventory = inventory.sortedBy { it.slot },
            members = members.toList(),
            visitedNpcs = visitedNpcs.values.sortedBy { it.npc.slug },
            journal = journal.toList(),
        )
}

@RylNextDsl
class PartyResourcesBuilder {
    var gold: Int = 0
    var torches: Int = 0
    var food: Int = 0
    var timeLeft: Int = 0
    var restedHours: Int = 0

    fun build(): PartyResources = PartyResources(gold, torches, food, timeLeft, restedHours)
}

@RylNextDsl
class PartyCountersBuilder {
    var memberCount: Int? = null
    var kills: Int = 0
    var waitingMail: Boolean = false
    var attacked: Boolean = false
    private val attackedToday = mutableMapOf<PartyId, PartyAttackRecord>()

    fun attackToday(body: AttackTodayBuilder.() -> Unit) {
        AttackTodayBuilder().apply(body).build().forEach { attackedToday[it.party] = it }
    }

    fun build(): PartyCounters = PartyCounters(memberCount, kills, waitingMail, attacked, attackedToday.values.sortedBy { it.party.slug })
}

@RylNextDsl
class AttackTodayBuilder {
    private val records = mutableMapOf<PartyId, PartyAttackRecord>()

    fun party(
        party: PartyId,
        attacked: Boolean,
    ) {
        records[party] = PartyAttackRecord(party = party, attacked = attacked)
    }

    fun attacked(party: PartyId) {
        party(party, attacked = true)
    }

    fun clear(party: PartyId) {
        party(party, attacked = false)
    }

    fun build(): List<PartyAttackRecord> = records.values.sortedBy { it.party.slug }
}

@RylNextDsl
class StateFlagBuilder {
    private val flags = mutableListOf<FlagState>()

    fun mark(state: FlagState) {
        flags += state
    }

    fun build(): List<FlagState> = flags.toList()
}

@RylNextDsl
class PartyMilestonesBuilder {
    private val milestones = mutableListOf<GameMilestoneId>()

    fun achieved(milestone: GameMilestoneId) {
        milestones += milestone
    }

    fun build(): List<GameMilestoneId> = milestones.toList()
}

@RylNextDsl
class NpcVisitBuilder {
    private val visits = mutableMapOf<NpcId, NpcVisitState>()

    fun visited(npc: NpcId) {
        visits[npc] = NpcVisitState(npc = npc, visited = true)
    }

    fun notVisited(npc: NpcId) {
        visits[npc] = NpcVisitState(npc = npc, visited = false)
    }

    fun build(): List<NpcVisitState> = visits.values.sortedBy { it.npc.slug }
}

@RylNextDsl
class JournalBuilder {
    private val entries = mutableListOf<JournalEntryState>()

    fun entry(id: TextId) {
        entries += JournalEntryState.Text(id)
    }

    fun line(text: String) {
        entries += JournalEntryState.Line(text)
    }

    fun build(): List<JournalEntryState> = entries.toList()
}

@RylNextDsl
class InventoryStateBuilder {
    private val slots = mutableMapOf<Int, InventorySlotState>()

    fun slot(index: Int): InventorySlotBuilder = InventorySlotBuilder(index) { slot -> slots[index] = slot }

    fun build(): List<InventorySlotState> = slots.values.sortedBy { it.slot }
}

@RylNextDsl
class InventorySlotBuilder(
    private val index: Int,
    private val record: (InventorySlotState) -> Unit,
) {
    fun empty() {
        record(InventorySlotState(index, null))
    }

    fun item(item: ItemId) {
        record(InventorySlotState(index, item))
    }
}

@RylNextDsl
class PartyMembersBuilder {
    private val members = mutableListOf<PartyMemberState>()

    fun member(
        name: String,
        body: PartyMemberBuilder.() -> Unit,
    ) {
        members += PartyMemberBuilder(name).apply(body).build()
    }

    fun build(): List<PartyMemberState> = members.toList()
}

@RylNextDsl
class PartyMemberBuilder(
    private val name: String,
) {
    var gender: Gender = Gender.Male
    var race: Race = Race.Human
    var klass: PcClass = PcClass.Thief
    var level: Int = 1
    var health: Int = 1
    var hitPoints: HitPoints = 1 of 1
    var weaponSkill: Int = 0
    var wisdom: Int = 0
    var mana: Mana = 0 of 0
    var charisma: Int = 0
    var dexterity: Int = 0
    private var equipmentState: EquipmentState = EquipmentState(DslDefaultItems.none, DslDefaultItems.none)
    var armor: ItemId
        get() = equipmentState.armor
        set(value) {
            equipmentState = equipmentState.copy(armor = value)
        }
    var weapon: ItemId
        get() = equipmentState.weapon
        set(value) {
            equipmentState = equipmentState.copy(weapon = value)
        }
    var experience: Int = 0
    private var dead: Boolean = false

    fun equipped(body: EquipmentBuilder.() -> Unit) {
        equipmentState = EquipmentBuilder(equipmentState).apply(body).build()
    }

    fun alive() {
        dead = false
    }

    fun dead() {
        dead = true
    }

    fun build(): PartyMemberState =
        PartyMemberState(
            name = name,
            gender = gender,
            race = race,
            pcClass = klass,
            level = level,
            health = health,
            hitPoints = hitPoints,
            weaponSkill = weaponSkill,
            wisdom = wisdom,
            mana = mana,
            charisma = charisma,
            dexterity = dexterity,
            equipment = equipmentState,
            experience = experience,
            dead = dead,
        )
}

@RylNextDsl
class EquipmentBuilder(
    initial: EquipmentState = EquipmentState(DslDefaultItems.none, DslDefaultItems.none),
) {
    var weapon: ItemId = initial.weapon
    var armor: ItemId = initial.armor

    fun build(): EquipmentState = EquipmentState(weapon, armor)
}
