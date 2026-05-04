package rylween.runtime

import rylween.dsl.GridPoint
import rylween.dsl.DungeonEventId
import rylween.dsl.DungeonId
import rylween.dsl.ItemId
import rylween.dsl.MissionId
import rylween.dsl.SceneId
import rylween.dsl.StateModel
import rylween.dsl.YdargRoomEvent

data class RuntimeSnapshotModel(
    val state: StateModel,
    val mode: RuntimeModeSnapshot? = null,
    val metadata: RuntimeSnapshotMetadata? = null,
)

data class RuntimeSnapshotMetadata(
    val formatVersion: Int,
    val contentHash: String? = null,
    val universeId: String? = null,
    val universeVersion: String? = null,
)

sealed interface RuntimeModeSnapshot {
    data class YdargCastle(val session: YdargSessionSnapshot) : RuntimeModeSnapshot
    data class Dungeon(
        val session: DungeonSessionSnapshot,
        val postDungeon: MissionPostDungeonSnapshot? = null,
    ) : RuntimeModeSnapshot
}

data class YdargSessionSnapshot(
    val floor: Int,
    val point: GridPoint,
    val rooms: Map<GridPoint, YdargRoomEvent>,
    val step: YdargSavedStep = YdargSavedStep.Navigate,
    val treasureItem: ItemId? = null,
)

enum class YdargSavedStep {
    RoomEntry,
    Navigate,
    TreasureOffer,
    StairsMenu,
    StairGive,
}

data class DungeonSessionSnapshot(
    val dungeon: DungeonId,
    val maxDepth: Int,
    val depth: Int,
    val levelEvents: Map<Int, DungeonEventId>,
    val step: DungeonSavedStep = DungeonSavedStep.Explore,
)

enum class DungeonSavedStep {
    Explore,
}

data class MissionPostDungeonSnapshot(
    val mission: MissionId,
    val scene: SceneId,
    val continuation: ScriptContinuationSnapshot,
)
