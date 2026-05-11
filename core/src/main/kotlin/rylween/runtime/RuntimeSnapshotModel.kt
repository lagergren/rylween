/**
 * Serializable runtime snapshot model used by save/load.
 *
 * Active bundle code keeps these plain records as the portable save-state
 * contract for runtimes.
 */
package rylween.runtime

import rylween.dsl.CastleId
import rylween.dsl.CastleRoomEvent
import rylween.dsl.DungeonEventId
import rylween.dsl.DungeonId
import rylween.dsl.GridPoint
import rylween.dsl.ItemId
import rylween.dsl.MissionId
import rylween.dsl.SceneId
import rylween.dsl.StateModel

/** Full savable runtime image: world state plus optional mode/session state. */
data class RuntimeSnapshotModel(
    val state: StateModel,
    val mode: RuntimeModeSnapshot? = null,
    val metadata: RuntimeSnapshotMetadata? = null,
)

/** Compatibility metadata stored with runtime snapshots. */
data class RuntimeSnapshotMetadata(
    val formatVersion: Int,
    val contentHash: String? = null,
    val compiledBundleHash: String? = null,
    val universeId: String? = null,
    val universeVersion: String? = null,
)

/** Nested session snapshot for modes that cannot be reconstructed from realm state alone. */
sealed interface RuntimeModeSnapshot {
    data class Castle(
        val session: CastleSessionSnapshot,
    ) : RuntimeModeSnapshot

    data class Dungeon(
        val session: DungeonSessionSnapshot,
        val postDungeon: MissionPostDungeonSnapshot? = null,
    ) : RuntimeModeSnapshot
}

/** Saveable state for an authored castle traversal. */
data class CastleSessionSnapshot(
    val castle: CastleId,
    val floor: Int,
    val point: GridPoint,
    val rooms: Map<GridPoint, CastleRoomEvent>,
    val step: CastleSavedStep = CastleSavedStep.Navigate,
    val treasureItem: ItemId? = null,
    val scriptScene: SceneId? = null,
    val scriptContinuation: ScriptContinuationSnapshot? = null,
    val scriptNext: CastleScriptContinuation? = null,
)

/** Persisted castle step tokens that are stable across runtime implementation details. */
enum class CastleSavedStep {
    RoomEntry,
    Navigate,
    TreasureOffer,
    StairsMenu,
    StairGive,
    Script,
}

/** Continuation point after an authored castle hook script finishes. */
enum class CastleScriptContinuation {
    RoomEntry,
    Finished,
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
