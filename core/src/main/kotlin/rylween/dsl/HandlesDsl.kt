/**
 * Typed handle wrappers and handle factory helpers.
 *
 * The DSL author writes handles such as `Items.fireWand`, `Towns.thord`, or
 * `Scenes.Missions.bandits`; generated catalogs and these helper functions
 * produce stable typed ids instead of raw strings. The runtime may serialize
 * slugs for diagnostics or saves, but game-author code should stay on these
 * typed handles.
 */
package rylween.dsl

/** Typed item handle used by catalogs, runtime state, and scripts. */
class ItemId private constructor(
    val slug: String,
) {
    override fun equals(other: Any?): Boolean = other is ItemId && slug == other.slug

    override fun hashCode(): Int = slug.hashCode()

    override fun toString(): String = "ItemId(slug=$slug)"

    companion object {
        /** Creates an authored item handle from a stable slug. */
        fun authored(slug: String): ItemId = ItemId(slug)
    }
}

/** Typed item-pool handle used by loot, rewards, and inventory-count expressions. */
class ItemPoolId private constructor(
    val slug: String,
    val items: List<ItemId> = emptyList(),
) {
    override fun equals(other: Any?): Boolean = other is ItemPoolId && slug == other.slug

    override fun hashCode(): Int = slug.hashCode()

    override fun toString(): String = "ItemPoolId(slug=$slug)"

    companion object {
        /** Creates an authored item-pool handle from a stable slug and optional fixed members. */
        fun authored(
            slug: String,
            items: List<ItemId> = emptyList(),
        ): ItemPoolId = ItemPoolId(slug, items)
    }
}

data class MonsterId(
    val slug: String,
)

data class EncounterTier(
    val slug: String,
)

data class WorldMapId(
    val slug: String,
)

data class MapLayerId(
    val slug: String,
)

data class TerrainId(
    val slug: String,
    val sourceMapCode: Char? = null,
)

data class TerrainTagId(
    val slug: String,
)

data class ImageId(
    val slug: String,
)

data class StaticScreenId(
    val slug: String,
)

data class PaletteId(
    val slug: String,
)

data class PaletteColorId(
    val slug: String,
)

data class LayoutId(
    val slug: String,
)

data class ScreenRegionId(
    val slug: String,
)

data class SceneId(
    val slug: String,
)

data class SpellId(
    val slug: String,
)

data class MissionId(
    val slug: String,
)

data class NpcRole(
    val slug: String,
)

data class NpcJobId(
    val slug: String,
)

data class NativeOpId(
    val slug: String,
)

data class StateId(
    val slug: String,
)

data class PartyId(
    val slug: String,
)

data class NpcId(
    val slug: String,
)

data class LocationId(
    val slug: String,
)

data class TownId(
    val slug: String,
)

data class BuildingId(
    val slug: String,
)

data class FormulaId(
    val slug: String,
)

data class KeyActionId(
    val slug: String,
)

data class DungeonId(
    val slug: String,
)

data class DungeonEventId(
    val slug: String,
)

data class CastleId(
    val slug: String,
)

data class GameMilestoneId(
    val slug: String,
)

data class LootTableId(
    val slug: String,
)

data class RewardBundleId(
    val slug: String,
)

data class RumorTableId(
    val slug: String,
)

data class RumorId(
    val slug: String,
)

fun text(id: String): TextId = TextId(id)

fun item(slug: String): ItemId = ItemId.authored(slug)

fun itemPool(
    slug: String,
    items: List<ItemId> = emptyList(),
): ItemPoolId = ItemPoolId.authored(slug, items)

fun monster(slug: String): MonsterId = MonsterId(slug)

fun encounterTier(slug: String): EncounterTier = EncounterTier(slug)

fun worldMap(slug: String): WorldMapId = WorldMapId(slug)

fun mapLayer(slug: String): MapLayerId = MapLayerId(slug)

fun terrain(
    slug: String,
    sourceMapCode: Char? = null,
): TerrainId = TerrainId(slug, sourceMapCode)

fun image(slug: String): ImageId = ImageId(slug)

fun staticScreen(slug: String): StaticScreenId = StaticScreenId(slug)

fun paletteColor(slug: String): PaletteColorId = PaletteColorId(slug)

fun layout(slug: String): LayoutId = LayoutId(slug)

fun screenRegion(slug: String): ScreenRegionId = ScreenRegionId(slug)

fun sceneId(slug: String): SceneId = SceneId(slug)

fun spellId(slug: String): SpellId = SpellId(slug)

fun missionId(slug: String): MissionId = MissionId(slug)

fun npcRole(slug: String): NpcRole = NpcRole(slug)

fun npcJob(slug: String): NpcJobId = NpcJobId(slug)

fun nativeOp(slug: String): NativeOpId = NativeOpId(slug)

fun stateId(slug: String): StateId = StateId(slug)

fun partyId(slug: String): PartyId = PartyId(slug)

fun npc(slug: String): NpcId = NpcId(slug)

fun location(slug: String): LocationId = LocationId(slug)

fun town(slug: String): TownId = TownId(slug)

fun building(slug: String): BuildingId = BuildingId(slug)

fun formula(slug: String): FormulaId = FormulaId(slug)

fun keyAction(slug: String): KeyActionId = KeyActionId(slug)

fun dungeon(slug: String): DungeonId = DungeonId(slug)

fun dungeonEvent(slug: String): DungeonEventId = DungeonEventId(slug)

fun castle(slug: String): CastleId = CastleId(slug)

fun milestone(slug: String): GameMilestoneId = GameMilestoneId(slug)

fun lootTable(slug: String): LootTableId = LootTableId(slug)

fun rewardBundle(slug: String): RewardBundleId = RewardBundleId(slug)

fun rumorTable(slug: String): RumorTableId = RumorTableId(slug)

fun rumor(slug: String): RumorId = RumorId(slug)

// Engine-native operation handles are part of the reusable runtime boundary,
// not game content.
class NativeOpRefs {
    val inventoryAdd: NativeOpId = nativeOp("inventory.add")
    val townEnterService: NativeOpId = nativeOp("town.enter_service")
    val inputKeyUpper: NativeOpId = nativeOp("input.key_upper")
    val partyMemberName: NativeOpId = nativeOp("party.member.name")
    val combatMonsterName: NativeOpId = nativeOp("combat.monster.name")
    val partyPronounSubject: NativeOpId = nativeOp("party.member.pronoun.subject")
    val partyPronounObject: NativeOpId = nativeOp("party.member.pronoun.object")
    val partyPronounPossessive: NativeOpId = nativeOp("party.member.pronoun.possessive")
    val townNpcName: NativeOpId = nativeOp("town.npc.name")
    val npcRoleName: NativeOpId = nativeOp("npc.role.name")
    val npcRoleLocation: NativeOpId = nativeOp("npc.role.location")
}

val NativeOps: NativeOpRefs = NativeOpRefs()
