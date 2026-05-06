/**
 * Serializable runtime snapshot model used by save/load.
 *
 * `save/StateSnapshot.kt` renders and parses these records, while dungeon and
 * Ydarg runtime sessions convert their nested progress into the mode-specific
 * snapshots declared here.
 */
package rylween.runtime

import rylween.dsl.DungeonEventId
import rylween.dsl.DungeonId
import rylween.dsl.GridPoint
import rylween.dsl.ItemId
import rylween.dsl.MissionId
import rylween.dsl.SceneId
import rylween.dsl.StateModel
import rylween.dsl.YdargRoomEvent

/** Full saveable runtime image: world state plus optional mode/session state. */
data class RuntimeSnapshotModel(
    val state: StateModel,
    val mode: RuntimeModeSnapshot? = null,
    val metadata: RuntimeSnapshotMetadata? = null,
)

/** Compatibility metadata stored with runtime snapshots. */
data class RuntimeSnapshotMetadata(
    val formatVersion: Int,
    val contentHash: String? = null,
    val universeId: String? = null,
    val universeVersion: String? = null,
)

/** Nested session snapshot for modes that cannot be reconstructed from realm state alone. */
sealed interface RuntimeModeSnapshot {
    data class YdargCastle(
        val session: YdargSessionSnapshot,
    ) : RuntimeModeSnapshot

    data class Dungeon(
        val session: DungeonSessionSnapshot,
        val postDungeon: MissionPostDungeonSnapshot? = null,
    ) : RuntimeModeSnapshot
}

/** Saveable state for a Ydarg castle traversal. */
data class YdargSessionSnapshot(
    val floor: Int,
    val point: GridPoint,
    val rooms: Map<GridPoint, YdargRoomEvent>,
    val step: YdargSavedStep = YdargSavedStep.Navigate,
    val treasureItem: ItemId? = null,
)

/** Persisted Ydarg step tokens that are stable across runtime implementation details. */
enum class YdargSavedStep {
    RoomEntry,
    Navigate,
    TreasureOffer,
    StairsMenu,
    StairGive,
}

/** Saveable state for a dungeon traversal. */
data class DungeonSessionSnapshot(
    val dungeon: DungeonId,
    val maxDepth: Int,
    val depth: Int,
    val levelEvents: Map<Int, DungeonEventId>,
    val step: DungeonSavedStep = DungeonSavedStep.Explore,
)

/** Persisted dungeon step tokens that are stable across runtime implementation details. */
enum class DungeonSavedStep {
    Explore,
}

/** Script continuation that resumes a mission after a dungeon handoff completes. */
data class MissionPostDungeonSnapshot(
    val mission: MissionId,
    val scene: SceneId,
    val continuation: ScriptContinuationSnapshot,
)
