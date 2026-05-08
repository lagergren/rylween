/**
 * Backend-neutral engine boundary for executing or tracing DSL scripts.
 *
 * The Kotlin DSL should not care whether the implementation behind this port is
 * the current Kotlin runtime, a future compiled VM, a server process, or a test
 * tracer. Runtime-host capability metadata lives in `RuntimeHostContract.kt`.
 */

package rylween.engine

import rylween.dsl.BuildingId
import rylween.dsl.BuildingServiceModel
import rylween.dsl.EncounterTier
import rylween.dsl.GameMilestoneId
import rylween.dsl.InputBindingModel
import rylween.dsl.InventorySlotChoiceFilter
import rylween.dsl.ItemPoolId
import rylween.dsl.MemberChoiceFilter
import rylween.dsl.MonsterId
import rylween.dsl.RandomItemOfferModel
import rylween.dsl.RumourTableId
import rylween.dsl.SceneId
import rylween.dsl.SceneModel
import rylween.dsl.ScriptControlSignal
import rylween.dsl.ScriptRewardModel
import rylween.dsl.ShopServiceModel
import rylween.dsl.TextBlock
import rylween.dsl.TextId
import rylween.dsl.TextPattern
import rylween.dsl.ViewCommand
import rylween.dsl.building
import rylween.dsl.dungeon
import rylween.dsl.milestone
import rylween.dsl.monster
import rylween.runtime.EquipmentActionResult
import rylween.runtime.town.ShopSellQuoteResult
import rylween.runtime.town.ShopTransactionResult
import rylween.runtime.wilderness.WildernessDungeonHandoff

/** Native service request emitted by authored scripts or expression evaluators. */
data class EngineRequest(
    val path: String,
    val args: List<String> = emptyList(),
)

/** Host callback surface used by authored script execution. */
interface RylweenEnginePort {
    fun effect(request: EngineRequest)

    fun readInt(request: EngineRequest): Int

    fun readString(request: EngineRequest): String
}

/** Traceable semantic event emitted while executing scripts or child sessions. */
sealed interface EngineTraceEvent {
    data class View(
        val command: ViewCommand,
    ) : EngineTraceEvent

    data class NativeEffect(
        val request: EngineRequest,
    ) : EngineTraceEvent

    data class NativeInt(
        val request: EngineRequest,
        val result: Int,
    ) : EngineTraceEvent

    data class NativeString(
        val request: EngineRequest,
        val result: String,
    ) : EngineTraceEvent

    data class Screen(
        val text: TextBlock,
    ) : EngineTraceEvent

    data class Emit(
        val id: TextId,
    ) : EngineTraceEvent

    data class EmitIndexed(
        val pattern: TextPattern,
        val value: Int,
    ) : EngineTraceEvent

    data class WaitInput(
        val reason: String?,
        val prompt: TextBlock? = null,
    ) : EngineTraceEvent

    data class Pause(
        val ms: Int,
    ) : EngineTraceEvent

    data class SetFlag(
        val flag: String,
        val state: String,
    ) : EngineTraceEvent

    data class AppendJournal(
        val text: TextBlock,
    ) : EngineTraceEvent

    data class Menu(
        val prompt: TextBlock,
        val keys: List<Char>,
    ) : EngineTraceEvent

    data class AskKey(
        val prompt: TextBlock,
        val bindings: List<InputBindingModel>,
    ) : EngineTraceEvent

    data class AskMember(
        val prompt: TextBlock,
        val filter: MemberChoiceFilter,
    ) : EngineTraceEvent

    data class AskInventorySlot(
        val prompt: TextBlock,
        val firstKey: Char,
        val slots: IntRange,
        val filter: InventorySlotChoiceFilter,
    ) : EngineTraceEvent

    data class AskText(
        val prompt: TextBlock,
        val accepted: List<String>,
        val maxLength: Int?,
    ) : EngineTraceEvent

    data class GrantReward(
        val reward: ScriptRewardModel,
    ) : EngineTraceEvent

    data class AchieveMilestone(
        val milestone: GameMilestoneId,
    ) : EngineTraceEvent

    data class Speak(
        val building: BuildingId,
    ) : EngineTraceEvent

    data class Rumour(
        val table: RumourTableId,
    ) : EngineTraceEvent

    data class RunScene(
        val scene: SceneId,
        val bindings: Map<String, String>,
    ) : EngineTraceEvent

    data class Encounter(
        val monster: MonsterId,
        val mandatory: Boolean,
        val tier: EncounterTier?,
    ) : EngineTraceEvent

    data class DungeonHandoff(
        val handoff: WildernessDungeonHandoff,
    ) : EngineTraceEvent

    data class TeleportParty(
        val directionKey: Int,
        val distance: Int,
    ) : EngineTraceEvent

    data class DiscoverCave(
        val handoff: WildernessDungeonHandoff,
        val moveCost: Int,
    ) : EngineTraceEvent

    data class Stop(
        val reason: String?,
        val signal: ScriptControlSignal,
    ) : EngineTraceEvent

    data class Break(
        val reason: String?,
        val signal: ScriptControlSignal,
    ) : EngineTraceEvent

    data class RandomItemOffer(
        val offer: RandomItemOfferModel,
    ) : EngineTraceEvent

    data class ShopService(
        val shop: ShopServiceModel,
    ) : EngineTraceEvent

    data class ShopSellQuote(
        val quote: ShopSellQuoteResult,
    ) : EngineTraceEvent

    data class ShopTransaction(
        val result: ShopTransactionResult,
    ) : EngineTraceEvent

    data class EquipmentAction(
        val result: EquipmentActionResult,
    ) : EngineTraceEvent

    data class TownService(
        val service: BuildingServiceModel,
        val scene: SceneModel,
    ) : EngineTraceEvent

    object ConsumeTorch : EngineTraceEvent

    data class ConsumeInventoryPool(
        val pool: ItemPoolId,
        val quantity: Int,
    ) : EngineTraceEvent
}

/** Recording [RylweenEnginePort] used by tests and terminal script bridges. */
class TraceEnginePort(
    private val ints: Map<EngineRequest, Int> = emptyMap(),
    private val strings: Map<EngineRequest, String> = emptyMap(),
    private val eventSink: RuntimeEventSink = RuntimeEventSink.Noop,
) : RylweenEnginePort {
    private val mutableEvents = mutableListOf<EngineTraceEvent>()
    val events: List<EngineTraceEvent> get() = mutableEvents.toList()

    override fun effect(request: EngineRequest) {
        mutableEvents += EngineTraceEvent.NativeEffect(request)
    }

    override fun readInt(request: EngineRequest): Int =
        ints.getOrElse(request) { 0 }.also { result ->
            mutableEvents += EngineTraceEvent.NativeInt(request, result)
        }

    override fun readString(request: EngineRequest): String =
        strings.getOrElse(request) { "" }.also { result ->
            mutableEvents += EngineTraceEvent.NativeString(request, result)
        }

    /** Records a semantic engine event and mirrors loggable events to the runtime event sink. */
    fun trace(event: EngineTraceEvent) {
        mutableEvents += event
        event.toRuntimeEvent()?.let(eventSink::emit)
    }

    /** Emits a diagnostic event without adding a user-visible script trace entry. */
    fun diagnostic(event: RuntimeEvent) {
        eventSink.emit(event)
    }
}

/** Converts trace events that should appear in runtime logs into [RuntimeEvent] values. */
fun EngineTraceEvent.toRuntimeEvent(): RuntimeEvent? =
    when (this) {
        is EngineTraceEvent.View -> runtimeEvent(RuntimeEventLevel.Debug, "view", "command", "kind" to command::class.simpleName)
        is EngineTraceEvent.NativeEffect -> runtimeEvent(RuntimeEventLevel.Info, "native", "effect", "path" to request.path)
        is EngineTraceEvent.NativeInt ->
            runtimeEvent(
                RuntimeEventLevel.Debug,
                "native",
                "readInt",
                "path" to request.path,
                "result" to result,
            )
        is EngineTraceEvent.NativeString ->
            runtimeEvent(
                RuntimeEventLevel.Debug,
                "native",
                "readString",
                "path" to request.path,
                "result" to result,
            )
        is EngineTraceEvent.Screen -> runtimeEvent(RuntimeEventLevel.Debug, "script", "screen")
        is EngineTraceEvent.Emit -> runtimeEvent(RuntimeEventLevel.Debug, "script", "emit", "text" to id.id)
        is EngineTraceEvent.EmitIndexed ->
            runtimeEvent(RuntimeEventLevel.Debug, "script", "emitIndexed", "pattern" to pattern.pattern, "value" to value)
        is EngineTraceEvent.WaitInput ->
            runtimeEvent(RuntimeEventLevel.Info, "script", "waitInput", "reason" to reason)
        is EngineTraceEvent.Pause -> runtimeEvent(RuntimeEventLevel.Debug, "script", "pause", "ms" to ms)
        is EngineTraceEvent.SetFlag ->
            runtimeEvent(RuntimeEventLevel.Audit, "story", "setFlag", "flag" to flag, "state" to state)
        is EngineTraceEvent.AppendJournal -> runtimeEvent(RuntimeEventLevel.Audit, "story", "appendJournal")
        is EngineTraceEvent.Menu ->
            runtimeEvent(RuntimeEventLevel.Debug, "script", "menu", "keys" to keys.joinToString(""))
        is EngineTraceEvent.AskKey ->
            runtimeEvent(RuntimeEventLevel.Debug, "script", "askKey", "keys" to bindings.joinToString("") { it.label })
        is EngineTraceEvent.AskMember ->
            runtimeEvent(RuntimeEventLevel.Debug, "script", "askMember", "filter" to filter)
        is EngineTraceEvent.AskInventorySlot ->
            runtimeEvent(
                RuntimeEventLevel.Debug,
                "script",
                "askInventorySlot",
                "firstKey" to firstKey,
                "slots" to "${slots.first}..${slots.last}",
                "filter" to filter,
            )
        is EngineTraceEvent.AskText ->
            runtimeEvent(RuntimeEventLevel.Debug, "script", "askText", "accepted" to accepted.size, "maxLength" to maxLength)
        is EngineTraceEvent.GrantReward ->
            runtimeEvent(RuntimeEventLevel.Audit, "reward", "grant", "gold" to reward.gold, "items" to reward.items.size)
        is EngineTraceEvent.AchieveMilestone ->
            runtimeEvent(RuntimeEventLevel.Audit, "story", "milestone", "id" to milestone.slug)
        is EngineTraceEvent.Speak -> runtimeEvent(RuntimeEventLevel.Info, "speak", "start", "building" to building.slug)
        is EngineTraceEvent.Rumour -> runtimeEvent(RuntimeEventLevel.Info, "speak", "rumour", "table" to table.slug)
        is EngineTraceEvent.RunScene -> runtimeEvent(RuntimeEventLevel.Info, "script", "runScene", "scene" to scene.slug)
        is EngineTraceEvent.Encounter ->
            runtimeEvent(
                RuntimeEventLevel.Audit,
                "battle",
                "encounter",
                "monster" to monster.slug,
                "mandatory" to mandatory,
                "tier" to tier?.slug,
            )
        is EngineTraceEvent.DungeonHandoff ->
            runtimeEvent(
                RuntimeEventLevel.Audit,
                "dungeon",
                "handoff",
                "dungeon" to handoff.dungeon.slug,
                "levels" to handoff.levels,
            )
        is EngineTraceEvent.TeleportParty ->
            runtimeEvent(RuntimeEventLevel.Audit, "wilderness", "teleport", "key" to directionKey, "distance" to distance)
        is EngineTraceEvent.DiscoverCave ->
            runtimeEvent(
                RuntimeEventLevel.Audit,
                "wilderness",
                "discoverCave",
                "dungeon" to handoff.dungeon.slug,
                "levels" to handoff.levels,
                "moveCost" to moveCost,
            )
        is EngineTraceEvent.Stop ->
            runtimeEvent(RuntimeEventLevel.Debug, "script", "stop", "reason" to reason, "signal" to signal)
        is EngineTraceEvent.Break ->
            runtimeEvent(RuntimeEventLevel.Debug, "script", "break", "reason" to reason, "signal" to signal)
        is EngineTraceEvent.RandomItemOffer ->
            runtimeEvent(RuntimeEventLevel.Info, "reward", "randomItemOffer", "pool" to offer.pool.slug)
        is EngineTraceEvent.ShopService ->
            runtimeEvent(RuntimeEventLevel.Info, "shop", "service", "building" to shop.building.slug)
        is EngineTraceEvent.ShopSellQuote ->
            runtimeEvent(RuntimeEventLevel.Debug, "shop", "sellQuote", "status" to quote.status)
        is EngineTraceEvent.ShopTransaction ->
            runtimeEvent(RuntimeEventLevel.Audit, "shop", "transaction", "status" to result.status)
        is EngineTraceEvent.EquipmentAction ->
            runtimeEvent(RuntimeEventLevel.Audit, "equipment", "action", "status" to result::class.simpleName)
        is EngineTraceEvent.TownService ->
            runtimeEvent(
                RuntimeEventLevel.Info,
                "town",
                "service",
                "building" to service.building.slug,
                "scene" to scene.id.slug,
            )
        EngineTraceEvent.ConsumeTorch -> runtimeEvent(RuntimeEventLevel.Audit, "inventory", "consumeTorch")
        is EngineTraceEvent.ConsumeInventoryPool ->
            runtimeEvent(RuntimeEventLevel.Audit, "inventory", "consumePool", "pool" to pool.slug, "quantity" to quantity)
    }
