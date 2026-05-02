// Backend-neutral engine boundary for executing or tracing DSL scripts.
// The Kotlin DSL should not care whether the implementation behind this port is
// the current C engine, a pure Kotlin runtime, a server process, or a test tracer.

package rylween.engine

import rylween.dsl.*
import rylween.runtime.*

data class EngineRequest(
    val path: String,
    val args: List<String> = emptyList(),
)

interface RylweenEnginePort {
    fun effect(request: EngineRequest)
    fun readInt(request: EngineRequest): Int
    fun readString(request: EngineRequest): String
}

sealed interface EngineTraceEvent {
    data class View(val command: ViewCommand) : EngineTraceEvent
    data class NativeEffect(val request: EngineRequest) : EngineTraceEvent
    data class NativeInt(val request: EngineRequest, val result: Int) : EngineTraceEvent
    data class NativeString(val request: EngineRequest, val result: String) : EngineTraceEvent
    data class Screen(val text: TextBlock) : EngineTraceEvent
    data class Emit(val id: TextId) : EngineTraceEvent
    data class EmitIndexed(val pattern: TextPattern, val value: Int) : EngineTraceEvent
    data class WaitInput(val reason: String?, val prompt: TextBlock? = null) : EngineTraceEvent
    data class Pause(val ms: Int) : EngineTraceEvent
    data class SetFlag(val flag: String, val state: String) : EngineTraceEvent
    data class AppendJournal(val text: TextBlock) : EngineTraceEvent
    data class Menu(val prompt: TextBlock, val keys: List<Char>) : EngineTraceEvent
    data class AskKey(val prompt: TextBlock, val bindings: List<InputBindingModel>) : EngineTraceEvent
    data class AskMember(val prompt: TextBlock, val filter: MemberChoiceFilter) : EngineTraceEvent
    data class AskInventorySlot(
        val prompt: TextBlock,
        val firstKey: Char,
        val slots: IntRange,
        val filter: InventorySlotChoiceFilter,
    ) : EngineTraceEvent
    data class AskText(val prompt: TextBlock, val accepted: List<String>, val maxLength: Int?) : EngineTraceEvent
    data class GrantReward(val reward: ScriptRewardModel) : EngineTraceEvent
    data class AchieveMilestone(val milestone: GameMilestoneId) : EngineTraceEvent
    data class Speak(val building: BuildingId) : EngineTraceEvent
    data class RunScene(val scene: SceneId, val bindings: Map<String, String>) : EngineTraceEvent
    data class Encounter(val monster: MonsterId, val mandatory: Boolean, val tier: EncounterTier?) : EngineTraceEvent
    data class DungeonHandoff(val handoff: WildernessDungeonHandoff) : EngineTraceEvent
    data class TeleportParty(val directionKey: Int, val distance: Int) : EngineTraceEvent
    data class DiscoverCave(val handoff: WildernessDungeonHandoff, val moveCost: Int) : EngineTraceEvent
    data class Stop(val reason: String?, val signal: ScriptControlSignal) : EngineTraceEvent
    data class Break(val reason: String?, val signal: ScriptControlSignal) : EngineTraceEvent
    data class RandomItemOffer(val offer: RandomItemOfferModel) : EngineTraceEvent
    data class ShopService(val shop: ShopServiceModel) : EngineTraceEvent
    data class ShopSellQuote(val quote: ShopSellQuoteResult) : EngineTraceEvent
    data class ShopTransaction(val result: ShopTransactionResult) : EngineTraceEvent
    data class EquipmentAction(val result: EquipmentActionResult) : EngineTraceEvent
    data class TownService(val service: BuildingServiceModel, val scene: SceneModel) : EngineTraceEvent
    object ConsumeTorch : EngineTraceEvent
    data class ConsumeInventoryPool(val pool: ItemPoolId, val quantity: Int) : EngineTraceEvent
}

class TraceEnginePort(
    private val ints: Map<EngineRequest, Int> = emptyMap(),
    private val strings: Map<EngineRequest, String> = emptyMap(),
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

    internal fun trace(event: EngineTraceEvent) {
        mutableEvents += event
    }
}
