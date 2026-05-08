/**
 * Snapshot records for blocked compiled VM continuations.
 *
 * `CompiledVm.kt` owns the live executor state, while this file owns the
 * data-only subset that can be embedded in strict runtime snapshots. Like
 * `runtime/ScriptContinuation.kt`, the snapshot excludes the outer
 * `RuntimeSessionState`; restore code supplies the runtime when it turns the
 * snapshot back into a live [CompiledVmContinuation].
 *
 * This is also the recommended cross-language serialization shape. A server,
 * native, or bitmap runtime should store these plain records instead of
 * implementation objects: root identity, VM-local maps, frame paths, and one
 * typed pending input/child request. Runtime state remains in the normal
 * snapshot model so the VM continuation does not duplicate party/realm data.
 * A frontend still decides where player saves are legal; this type only says
 * what must be persisted if an engine boundary is intentionally snapshotted.
 */
package rylween.runtime.vm

import rylween.compiled.CompiledRandomItemOffer
import rylween.compiled.CompiledScriptReward
import rylween.compiled.CompiledShopService
import rylween.dsl.BuildingId
import rylween.dsl.EncounterTier
import rylween.dsl.InputBindingModel
import rylween.dsl.InventorySlotChoiceFilter
import rylween.dsl.MemberChoiceFilter
import rylween.dsl.MonsterId
import rylween.dsl.RumourTableId
import rylween.dsl.TextBlock
import rylween.runtime.RuntimeSessionState
import rylween.runtime.ScriptRootRef
import rylween.runtime.wilderness.WildernessDungeonHandoff

/**
 * Saveable subset of [CompiledVmContinuation] for one blocked VM prompt or child request.
 *
 * [frames] is never empty because a blocked VM must know where to resume after
 * input or child completion. [pending] repeats the root/index/path of the
 * blocking opcode so save readers can validate the shape before reconstructing
 * live VM state.
 */
data class CompiledVmContinuationSnapshot(
    val root: ScriptRootRef,
    val locals: Map<String, Int> = emptyMap(),
    val textBindings: Map<String, String> = emptyMap(),
    val activeInventorySlot: Int? = null,
    val frames: List<CompiledVmFrameSnapshot>,
    val pending: CompiledVmPendingInputSnapshot,
) {
    init {
        require(root.sameRoot(pending.root)) { "compiled VM continuation pending root must match continuation root" }
        require(frames.isNotEmpty()) { "compiled VM continuation snapshot requires at least one frame" }
    }
}

/**
 * Saveable subset of [CompiledVmFrame].
 *
 * The path fields are numeric by design. Do not serialize Kotlin class names or
 * enum-specific property names into save files; another implementation should
 * be able to follow the same integer path through the compiled operand tree.
 */
data class CompiledVmFrameSnapshot(
    val blockPath: List<Int>,
    val nextIndex: Int,
    val insideLoop: Boolean = false,
    val kind: CompiledVmFrameKind = CompiledVmFrameKind.Block,
    val enclosingInsideLoop: Boolean = insideLoop,
) {
    init {
        require(blockPath.all { segment -> segment >= 0 }) { "compiled VM frame blockPath cannot contain negative segments" }
        require(nextIndex >= 0) { "compiled VM frame nextIndex cannot be negative" }
    }
}

/**
 * Saveable typed pending input shape for [CompiledVmContinuationSnapshot].
 *
 * Every variant is data-only and frontend-neutral. A terminal can turn a menu
 * into key prompts, a server can turn it into JSON, and a native client can
 * draw buttons; all should resume with the matching [CompiledVmInput] variant.
 */
sealed interface CompiledVmPendingInputSnapshot {
    /** Script root that owns the pending input. */
    val root: ScriptRootRef

    /** Instruction index of the blocking opcode in its current instruction block. */
    val instructionIndex: Int

    /** Operand path to the blocking opcode or nested prompt. */
    val operandPath: List<Int>

    /** Pending `WaitInput` acknowledgment. */
    data class WaitInput(
        override val root: ScriptRootRef,
        override val instructionIndex: Int,
        override val operandPath: List<Int>,
        val reason: String?,
        val prompt: TextBlock? = null,
    ) : CompiledVmPendingInputSnapshot {
        init {
            validatePendingShape()
        }
    }

    /** Pending menu choice. */
    data class Menu(
        override val root: ScriptRootRef,
        override val instructionIndex: Int,
        override val operandPath: List<Int>,
        val prompt: TextBlock,
        val options: List<CompiledVmMenuOptionSnapshot>,
        val hasDefault: Boolean,
    ) : CompiledVmPendingInputSnapshot {
        init {
            validatePendingShape()
        }
    }

    /** Pending authored key prompt that stores the chosen binding value in [localName]. */
    data class AskKey(
        override val root: ScriptRootRef,
        override val instructionIndex: Int,
        override val operandPath: List<Int>,
        val prompt: TextBlock,
        val localName: String,
        val bindings: List<InputBindingModel>,
    ) : CompiledVmPendingInputSnapshot {
        init {
            validatePendingShape()
            require(localName.isNotBlank()) { "compiled VM AskKey snapshot requires localName" }
        }
    }

    /** Pending party-member prompt that stores the chosen one-based member number in [localName]. */
    data class AskMember(
        override val root: ScriptRootRef,
        override val instructionIndex: Int,
        override val operandPath: List<Int>,
        val prompt: TextBlock,
        val localName: String,
        val filter: MemberChoiceFilter,
        val accepting: MemberChoiceFilter,
        val choices: List<Int>,
    ) : CompiledVmPendingInputSnapshot {
        init {
            validatePendingShape()
            require(localName.isNotBlank()) { "compiled VM AskMember snapshot requires localName" }
            require(choices.all { choice -> choice > 0 }) { "compiled VM AskMember choices must be one-based" }
        }
    }

    /** Pending monster prompt that stores the chosen zero-based monster target index in [localName]. */
    data class AskMonster(
        override val root: ScriptRootRef,
        override val instructionIndex: Int,
        override val operandPath: List<Int>,
        val prompt: TextBlock,
        val localName: String,
        val targetKeys: List<Char>,
    ) : CompiledVmPendingInputSnapshot {
        init {
            validatePendingShape()
            require(localName.isNotBlank()) { "compiled VM AskMonster snapshot requires localName" }
        }
    }

    /** Pending inventory-slot prompt that stores the chosen slot number in [localName]. */
    data class AskInventorySlot(
        override val root: ScriptRootRef,
        override val instructionIndex: Int,
        override val operandPath: List<Int>,
        val prompt: TextBlock,
        val localName: String,
        val firstKey: Char,
        val firstSlot: Int,
        val lastSlot: Int,
        val filter: InventorySlotChoiceFilter,
        val choices: List<CompiledVmInventorySlotChoiceSnapshot>,
    ) : CompiledVmPendingInputSnapshot {
        init {
            validatePendingShape()
            require(localName.isNotBlank()) { "compiled VM AskInventorySlot snapshot requires localName" }
            require(firstSlot <= lastSlot) { "compiled VM AskInventorySlot snapshot requires an ascending slot range" }
        }
    }

    /** Pending free-text prompt that resumes into either the match or miss branch. */
    data class AskText(
        override val root: ScriptRootRef,
        override val instructionIndex: Int,
        override val operandPath: List<Int>,
        val prompt: TextBlock,
        val accepted: List<String>,
        val caseSensitive: Boolean,
        val trim: Boolean,
        val maxLength: Int?,
    ) : CompiledVmPendingInputSnapshot {
        init {
            validatePendingShape()
            require(maxLength == null || maxLength >= 0) { "compiled VM AskText maxLength cannot be negative" }
        }
    }

    /**
     * Pending host-owned child session such as a battle encounter.
     *
     * Persisting this request lets a save taken while a child session is active
     * restore the parent VM frame exactly where it blocked, then let the host
     * continue or complete the child before resuming the parent script.
     */
    data class ChildSession(
        override val root: ScriptRootRef,
        override val instructionIndex: Int,
        override val operandPath: List<Int>,
        val request: CompiledVmChildRequestSnapshot,
    ) : CompiledVmPendingInputSnapshot {
        init {
            validatePendingShape()
        }
    }
}

/**
 * Saveable host-owned child request inside [CompiledVmPendingInputSnapshot.ChildSession].
 *
 * Add new variants here when J2.4 wires additional child/session opcodes. Keep
 * each variant stable, explicit, and independent from Kotlin runtime session
 * classes so other languages can persist and replay the same child handoff.
 */
sealed interface CompiledVmChildRequestSnapshot {
    /** Saveable conversation request emitted by a compiled `Speak` opcode. */
    data class Speak(
        val building: BuildingId,
    ) : CompiledVmChildRequestSnapshot {
        init {
            require(building.slug.isNotBlank()) { "compiled VM speak snapshot requires a building slug" }
        }
    }

    /** Saveable rumour request emitted by a compiled `TellRumour` opcode. */
    data class TellRumour(
        val table: RumourTableId,
    ) : CompiledVmChildRequestSnapshot {
        init {
            require(table.slug.isNotBlank()) { "compiled VM rumour snapshot requires a table slug" }
        }
    }

    /** Saveable encounter request emitted by a compiled `Encounter` opcode. */
    data class Encounter(
        val monster: MonsterId,
        val mandatory: Boolean,
        val tier: String?,
    ) : CompiledVmChildRequestSnapshot {
        init {
            require(monster.slug.isNotBlank()) { "compiled VM encounter snapshot requires a monster slug" }
            require(tier == null || tier.isNotBlank()) { "compiled VM encounter snapshot tier cannot be blank" }
        }
    }

    /** Saveable dungeon handoff request emitted by a compiled `EnterDungeon` opcode. */
    data class EnterDungeon(
        val handoff: WildernessDungeonHandoff,
    ) : CompiledVmChildRequestSnapshot {
        init {
            require(handoff.dungeon.slug.isNotBlank()) { "compiled VM dungeon snapshot requires a dungeon slug" }
            require(handoff.levels > 0) { "compiled VM dungeon snapshot levels must be positive" }
        }
    }

    /** Saveable reward child request emitted by a compiled `GrantReward` opcode. */
    data class GrantReward(
        val reward: CompiledScriptReward,
        val gold: Int,
    ) : CompiledVmChildRequestSnapshot

    /** Saveable random-offer child request emitted by a compiled `RandomItemOffer` opcode. */
    data class RandomItemOffer(
        val offer: CompiledRandomItemOffer,
        val selectedIndex: Int,
    ) : CompiledVmChildRequestSnapshot {
        init {
            require(selectedIndex >= 0) { "compiled VM random offer selectedIndex cannot be negative" }
        }
    }

    /** Saveable shop child request emitted by a compiled `ShopService` opcode. */
    data class ShopService(
        val shop: CompiledShopService,
    ) : CompiledVmChildRequestSnapshot
}

/** Saveable menu option inside [CompiledVmPendingInputSnapshot.Menu]. */
data class CompiledVmMenuOptionSnapshot(
    val key: Char,
    val optionIndex: Int,
) {
    init {
        require(optionIndex >= 0) { "compiled VM menu option index cannot be negative" }
    }
}

/** Saveable inventory choice inside [CompiledVmPendingInputSnapshot.AskInventorySlot]. */
data class CompiledVmInventorySlotChoiceSnapshot(
    val key: Char,
    val slot: Int,
) {
    init {
        require(slot >= 0) { "compiled VM inventory slot cannot be negative" }
    }
}

/** Converts a live compiled VM continuation into its saveable snapshot record. */
fun CompiledVmContinuation.toSnapshot(): CompiledVmContinuationSnapshot =
    CompiledVmContinuationSnapshot(
        root = root,
        locals = locals.toSortedMap(),
        textBindings = textBindings.toSortedMap(),
        activeInventorySlot = activeInventorySlot,
        frames = frames.map(CompiledVmFrame::toSnapshot),
        pending = pending.toSnapshot(),
    )

/** Restores a live compiled VM continuation from this snapshot and live [runtime]. */
fun CompiledVmContinuationSnapshot.toContinuation(runtime: RuntimeSessionState): CompiledVmContinuation =
    CompiledVmContinuation(
        root = root,
        runtime = runtime,
        locals = locals.toSortedMap(),
        textBindings = textBindings.toSortedMap(),
        activeInventorySlot = activeInventorySlot,
        frames = frames.map(CompiledVmFrameSnapshot::toFrame),
        pending = pending.toPendingInput(),
    )

private fun CompiledVmFrame.toSnapshot(): CompiledVmFrameSnapshot =
    CompiledVmFrameSnapshot(
        blockPath = blockPath,
        nextIndex = nextIndex,
        insideLoop = insideLoop,
        kind = kind,
        enclosingInsideLoop = enclosingInsideLoop,
    )

private fun CompiledVmFrameSnapshot.toFrame(): CompiledVmFrame =
    CompiledVmFrame(
        blockPath = blockPath,
        nextIndex = nextIndex,
        insideLoop = insideLoop,
        kind = kind,
        enclosingInsideLoop = enclosingInsideLoop,
    )

private fun CompiledVmPendingInput.toSnapshot(): CompiledVmPendingInputSnapshot =
    when (this) {
        is CompiledVmPendingInput.WaitInput ->
            CompiledVmPendingInputSnapshot.WaitInput(root, instructionIndex, operandPath, reason, prompt)
        is CompiledVmPendingInput.Menu ->
            CompiledVmPendingInputSnapshot.Menu(
                root = root,
                instructionIndex = instructionIndex,
                operandPath = operandPath,
                prompt = prompt,
                options = options.map { option -> CompiledVmMenuOptionSnapshot(option.key, option.optionIndex) },
                hasDefault = hasDefault,
            )
        is CompiledVmPendingInput.AskKey ->
            CompiledVmPendingInputSnapshot.AskKey(root, instructionIndex, operandPath, prompt, localName, bindings)
        is CompiledVmPendingInput.AskMember ->
            CompiledVmPendingInputSnapshot.AskMember(root, instructionIndex, operandPath, prompt, localName, filter, accepting, choices)
        is CompiledVmPendingInput.AskMonster ->
            CompiledVmPendingInputSnapshot.AskMonster(root, instructionIndex, operandPath, prompt, localName, targetKeys)
        is CompiledVmPendingInput.AskInventorySlot ->
            CompiledVmPendingInputSnapshot.AskInventorySlot(
                root = root,
                instructionIndex = instructionIndex,
                operandPath = operandPath,
                prompt = prompt,
                localName = localName,
                firstKey = firstKey,
                firstSlot = slots.first,
                lastSlot = slots.last,
                filter = filter,
                choices = choices.map { choice -> CompiledVmInventorySlotChoiceSnapshot(choice.key, choice.slot) },
            )
        is CompiledVmPendingInput.AskText ->
            CompiledVmPendingInputSnapshot.AskText(
                root = root,
                instructionIndex = instructionIndex,
                operandPath = operandPath,
                prompt = prompt,
                accepted = accepted,
                caseSensitive = caseSensitive,
                trim = trim,
                maxLength = maxLength,
            )
        is CompiledVmPendingInput.ChildSession ->
            CompiledVmPendingInputSnapshot.ChildSession(root, instructionIndex, operandPath, request.toSnapshot())
    }

private fun CompiledVmPendingInputSnapshot.toPendingInput(): CompiledVmPendingInput =
    when (this) {
        is CompiledVmPendingInputSnapshot.WaitInput ->
            CompiledVmPendingInput.WaitInput(root, instructionIndex, operandPath, reason, prompt)
        is CompiledVmPendingInputSnapshot.Menu ->
            CompiledVmPendingInput.Menu(
                root = root,
                instructionIndex = instructionIndex,
                operandPath = operandPath,
                prompt = prompt,
                options = options.map { option -> CompiledVmMenuOption(option.key, option.optionIndex) },
                hasDefault = hasDefault,
            )
        is CompiledVmPendingInputSnapshot.AskKey ->
            CompiledVmPendingInput.AskKey(root, instructionIndex, operandPath, prompt, localName, bindings)
        is CompiledVmPendingInputSnapshot.AskMember ->
            CompiledVmPendingInput.AskMember(root, instructionIndex, operandPath, prompt, localName, filter, accepting, choices)
        is CompiledVmPendingInputSnapshot.AskMonster ->
            CompiledVmPendingInput.AskMonster(root, instructionIndex, operandPath, prompt, localName, targetKeys)
        is CompiledVmPendingInputSnapshot.AskInventorySlot ->
            CompiledVmPendingInput.AskInventorySlot(
                root = root,
                instructionIndex = instructionIndex,
                operandPath = operandPath,
                prompt = prompt,
                localName = localName,
                firstKey = firstKey,
                slots = firstSlot..lastSlot,
                filter = filter,
                choices = choices.map { choice -> CompiledVmInventorySlotChoice(choice.key, choice.slot) },
            )
        is CompiledVmPendingInputSnapshot.AskText ->
            CompiledVmPendingInput.AskText(root, instructionIndex, operandPath, prompt, accepted, caseSensitive, trim, maxLength)
        is CompiledVmPendingInputSnapshot.ChildSession ->
            CompiledVmPendingInput.ChildSession(root, instructionIndex, operandPath, request.toRequest())
    }

private fun CompiledVmPendingInputSnapshot.validatePendingShape() {
    require(instructionIndex >= 0) { "compiled VM pending instructionIndex cannot be negative" }
    require(operandPath.all { segment -> segment >= 0 }) { "compiled VM pending operandPath cannot contain negative segments" }
}

private fun CompiledVmChildRequest.toSnapshot(): CompiledVmChildRequestSnapshot =
    when (this) {
        is CompiledVmChildRequest.Speak -> CompiledVmChildRequestSnapshot.Speak(building)
        is CompiledVmChildRequest.TellRumour -> CompiledVmChildRequestSnapshot.TellRumour(table)
        is CompiledVmChildRequest.Encounter -> CompiledVmChildRequestSnapshot.Encounter(monster, mandatory, tier?.slug)
        is CompiledVmChildRequest.EnterDungeon -> CompiledVmChildRequestSnapshot.EnterDungeon(handoff)
        is CompiledVmChildRequest.GrantReward -> CompiledVmChildRequestSnapshot.GrantReward(reward, gold)
        is CompiledVmChildRequest.RandomItemOffer -> CompiledVmChildRequestSnapshot.RandomItemOffer(offer, selectedIndex)
        is CompiledVmChildRequest.ShopService -> CompiledVmChildRequestSnapshot.ShopService(shop)
    }

private fun CompiledVmChildRequestSnapshot.toRequest(): CompiledVmChildRequest =
    when (this) {
        is CompiledVmChildRequestSnapshot.Speak -> CompiledVmChildRequest.Speak(building)
        is CompiledVmChildRequestSnapshot.TellRumour -> CompiledVmChildRequest.TellRumour(table)
        is CompiledVmChildRequestSnapshot.Encounter ->
            CompiledVmChildRequest.Encounter(
                monster = monster,
                mandatory = mandatory,
                tier = tier?.let(::EncounterTier),
            )
        is CompiledVmChildRequestSnapshot.EnterDungeon -> CompiledVmChildRequest.EnterDungeon(handoff)
        is CompiledVmChildRequestSnapshot.GrantReward -> CompiledVmChildRequest.GrantReward(reward, gold)
        is CompiledVmChildRequestSnapshot.RandomItemOffer -> CompiledVmChildRequest.RandomItemOffer(offer, selectedIndex)
        is CompiledVmChildRequestSnapshot.ShopService -> CompiledVmChildRequest.ShopService(shop)
    }
