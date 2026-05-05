package rylween.save

import rylween.dsl.StateId
import rylween.dsl.StateKind
import rylween.dsl.StateModel
import rylween.dsl.RuntimeMode
import rylween.dsl.GameModel
import rylween.dsl.InventorySaveSlotMax
import rylween.dsl.InventorySlotState
import rylween.dsl.NpcVisitState
import rylween.dsl.PartyAttackRecord
import rylween.dsl.PartyState
import rylween.runtime.RuntimeModeSnapshot
import rylween.runtime.RuntimeSnapshotModel
import rylween.runtime.RuntimeSessionState

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class RuntimeSaveOutcome(
    val saved: Boolean,
    val target: String? = null,
    val error: String? = null,
)

data class RuntimeSaveRequest(
    val runtime: RuntimeSessionState,
    val mode: RuntimeModeSnapshot? = null,
)

fun interface RuntimeSavePort {
    fun save(runtime: RuntimeSessionState): RuntimeSaveOutcome

    fun save(request: RuntimeSaveRequest): RuntimeSaveOutcome =
        save(request.runtime)
}

fun RuntimeSessionState.toRuntimeStateSnapshot(id: StateId): StateModel =
    StateModel(
        id = id,
        kind = StateKind.RuntimeSnapshot,
        realm = realm,
        locations = locations,
        npcs = npcs,
        parties = parties.map { party -> party.toRuntimeSnapshotParty(this) },
    )

private fun PartyState.toRuntimeSnapshotParty(runtime: RuntimeSessionState): PartyState {
    val partyIds = runtime.parties.map { it.id }
    val attackedTodayByParty = counters.attackedToday.associateBy { it.party }
    val npcIds = runtime.npcs.map { it.npc }
    val visitedByNpc = visitedNpcs.associateBy { it.npc }
    val inventoryBySlot = inventory.associateBy { it.slot }
    return copy(
        counters = counters.copy(
            memberCount = members.count { !it.dead },
            attackedToday = partyIds.map { partyId ->
                attackedTodayByParty[partyId] ?: PartyAttackRecord(partyId, attacked = false)
            },
        ),
        inventory = (0..InventorySaveSlotMax).map { slot ->
            inventoryBySlot[slot] ?: InventorySlotState(slot, null)
        },
        visitedNpcs = npcIds.map { npc ->
            visitedByNpc[npc] ?: NpcVisitState(npc = npc, visited = false)
        },
    )
}

class FileRuntimeSavePort(
    private val file: File,
    private val snapshotId: StateId,
    private val content: GameModel? = null,
) : RuntimeSavePort {
    override fun save(runtime: RuntimeSessionState): RuntimeSaveOutcome =
        save(RuntimeSaveRequest(runtime))

    override fun save(request: RuntimeSaveRequest): RuntimeSaveOutcome =
        runCatching {
            checkModeSnapshot(request)
            val snapshot = RuntimeSnapshotModel(
                state = request.runtime.toRuntimeStateSnapshot(snapshotId),
                mode = request.mode,
                metadata = content?.runtimeSnapshotMetadata(),
            )
            val rendered = renderRuntimeStateSnapshot(snapshot)
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile ?: File("."), "${file.name}.tmp")
            tmp.writeText(rendered)
            moveSaveFile(tmp, file)
            RuntimeSaveOutcome(saved = true, target = file.path)
        }.getOrElse { error ->
            RuntimeSaveOutcome(saved = false, target = file.path, error = error.message)
        }

    private fun checkModeSnapshot(request: RuntimeSaveRequest) {
        when (request.runtime.realm.currentMode) {
            RuntimeMode.Wilderness -> {
                if (request.mode != null) {
                    error("Wilderness runtime save must not include a session snapshot")
                }
            }
            RuntimeMode.YdargCastle -> {
                if (request.mode !is RuntimeModeSnapshot.YdargCastle) {
                    error("YdargCastle runtime save requires Ydarg session snapshot")
                }
            }
            RuntimeMode.Dungeon -> {
                if (request.mode !is RuntimeModeSnapshot.Dungeon) {
                    error("Dungeon runtime save requires dungeon session snapshot")
                }
            }
            else -> error(
                "Runtime mode ${request.runtime.realm.currentMode.name} is not file-saveable yet; " +
                    "return to wilderness before saving",
            )
        }
    }

    private fun moveSaveFile(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }
}
