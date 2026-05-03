package rylween.runtime

import rylween.dsl.GridPoint
import rylween.dsl.ItemId
import rylween.dsl.StateModel
import rylween.dsl.YdargRoomEvent

data class RuntimeSnapshotModel(
    val state: StateModel,
    val mode: RuntimeModeSnapshot? = null,
)

sealed interface RuntimeModeSnapshot {
    data class YdargCastle(val session: YdargSessionSnapshot) : RuntimeModeSnapshot
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
