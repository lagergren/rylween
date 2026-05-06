/**
 * Pure compiled-script executor for the first J2 VM kernel slice.
 *
 * This file consumes validated script programs from `compiled/CompiledLoader.kt`
 * and mirrors the non-interactive behavior of
 * `frontend/terminal/TerminalScriptBridge.kt` without depending on terminal IO.
 * Runtime failures use `VmDiagnostic.kt`, while native service bindings share
 * the allowlist shape from `NativeServiceCatalog.kt`.
 */
package rylween.runtime.vm

import rylween.compiled.CompiledBinaryOp
import rylween.compiled.CompiledBoolExpr
import rylween.compiled.CompiledComparisonOp
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledInputBinding
import rylween.compiled.CompiledInstruction
import rylween.compiled.CompiledIntExpr
import rylween.compiled.CompiledLValue
import rylween.compiled.CompiledMenuOption
import rylween.compiled.CompiledNativeStringArg
import rylween.compiled.CompiledOpcode
import rylween.compiled.CompiledOperand
import rylween.compiled.CompiledRuntimeTextPart
import rylween.compiled.CompiledScriptProgram
import rylween.compiled.CompiledStringExpr
import rylween.compiled.CompiledSwitchCase
import rylween.compiled.CompiledTextAnswer
import rylween.compiled.CompiledTextBlock
import rylween.compiled.CompiledTextPart
import rylween.compiled.CompiledViewCommand
import rylween.compiled.LoadedCompiledContent
import rylween.dsl.GameMilestoneId
import rylween.dsl.Gender
import rylween.dsl.ImageId
import rylween.dsl.InputBindingModel
import rylween.dsl.InventorySaveSlotMax
import rylween.dsl.InventorySlotChoiceFilter
import rylween.dsl.ItemId
import rylween.dsl.ItemPoolId
import rylween.dsl.LocationId
import rylween.dsl.MemberChoiceFilter
import rylween.dsl.NativeAllowlist
import rylween.dsl.PartyMemberState
import rylween.dsl.PartyState
import rylween.dsl.PcClass
import rylween.dsl.PronounCase
import rylween.dsl.ScriptControlSignal
import rylween.dsl.TextBlock
import rylween.dsl.TextId
import rylween.dsl.TextPart
import rylween.dsl.TextPattern
import rylween.dsl.ViewCommand
import rylween.dsl.fallbackText
import rylween.dsl.inputKey
import rylween.dsl.matches
import rylween.dsl.pronoun
import rylween.engine.EngineRequest
import rylween.engine.EngineTraceEvent
import rylween.frontend.terminal.TerminalTextContext
import rylween.frontend.terminal.TerminalTextRenderer
import rylween.runtime.RuntimeSessionState
import rylween.runtime.countInventoryItems
import rylween.runtime.drawRandom
import rylween.runtime.inventoryItem
import rylween.runtime.scriptMemberField
import rylween.runtime.scriptMemberFlag
import rylween.runtime.scriptPartyScalar
import rylween.runtime.scriptRealmScalar
import rylween.runtime.scriptRuntimeFlag
import rylween.runtime.withFlagState
import rylween.runtime.withJournalLines
import rylween.runtime.withMilestone
import rylween.runtime.withScriptMemberField
import rylween.runtime.withScriptPartyScalar
import rylween.runtime.withScriptRealmScalar

/** Host callbacks used by [CompiledVm] for effects outside deterministic state mutation. */
interface CompiledVmHost {
    /**
     * Receives backend-neutral semantic output from executed instructions.
     *
     * Current tests use this as a parity stream against [rylween.engine.TraceEnginePort].
     * Future server and native hosts can translate the same event shape into their
     * own transport without depending on terminal rendering.
     */
    fun trace(event: EngineTraceEvent)

    /** Resolves a native integer service request. */
    fun readInt(request: EngineRequest): Int = 0

    /** Resolves a native string service request. */
    fun readString(request: EngineRequest): String = ""

    /** Reads a battle monster integer field for hosts that execute combat script expressions. */
    fun readMonsterInt(
        monster: Int,
        field: String,
    ): Int? = null

    /** Writes a battle monster integer field for hosts that execute combat script lvalues. */
    fun writeMonsterInt(
        monster: Int,
        field: String,
        value: Int,
    ): Boolean = false

    /** Reads a battle-scoped integer flag for hosts that execute combat script expressions. */
    fun readBattleInt(name: String): Int? = null

    /** Writes a battle-scoped integer flag for hosts that execute combat script lvalues. */
    fun writeBattleInt(
        name: String,
        value: Int,
    ): Boolean = false

    /** Resolves a battle query such as `first_alive_monster`. */
    fun readBattleQuery(name: String): Int? = null

    /**
     * Returns currently selectable monster target keys for an `AskMonster` prompt.
     *
     * Battle hosts can derive this from live encounter hit points; non-battle
     * hosts leave the default empty list so the prompt remains blocked until a
     * host with target context resumes it.
     */
    fun monsterTargetKeys(): List<Char> = emptyList()

    /** Reads a battle monster boolean flag for hosts that execute combat predicates. */
    fun readMonsterFlag(
        monster: Int,
        name: String,
    ): Boolean? = null

    /** Reads a battle-scoped boolean flag for hosts that execute combat predicates. */
    fun readBattleFlag(name: String): Boolean? = null

    /**
     * Renders journal text into persisted line entries.
     *
     * The default intentionally matches the terminal renderer boundary used by
     * `StatefulScriptServiceRunner`, while still keeping rendering injectable.
     */
    fun journalLines(
        text: TextBlock,
        runtime: RuntimeSessionState,
    ): List<String> = TerminalTextRenderer().lines(text, TerminalTextContext(runtime = runtime))
}

/** Test and adapter host that records VM trace events in memory. */
open class RecordingCompiledVmHost(
    private val ints: Map<EngineRequest, Int> = emptyMap(),
    private val strings: Map<EngineRequest, String> = emptyMap(),
) : CompiledVmHost {
    private val mutableEvents = mutableListOf<EngineTraceEvent>()

    /** Immutable snapshot of events recorded so far. */
    val events: List<EngineTraceEvent> get() = mutableEvents.toList()

    override fun trace(event: EngineTraceEvent) {
        mutableEvents += event
    }

    override fun readInt(request: EngineRequest): Int = ints[request] ?: 0

    override fun readString(request: EngineRequest): String = strings[request] ?: ""
}

/** Mutable input state for running a compiled script program. */
data class CompiledVmStart(
    val runtime: RuntimeSessionState,
    val locals: Map<String, Int> = emptyMap(),
    val textBindings: Map<String, String> = emptyMap(),
)

/** Input delivered to a blocked compiled VM continuation. */
sealed interface CompiledVmInput {
    /** Acknowledges a `WaitInput` prompt and resumes at the next instruction. */
    data object Continue : CompiledVmInput

    /** Selects one menu key. */
    data class MenuKey(
        val key: Char,
    ) : CompiledVmInput

    /** Supplies a raw token for an `AskKey` prompt, matched against authored bindings and aliases. */
    data class AskKey(
        val raw: String,
    ) : CompiledVmInput

    /** Selects a one-based party member number for an `AskMember` prompt. */
    data class AskMember(
        val memberNumber: Int,
    ) : CompiledVmInput

    /** Selects a zero-based monster target index for an `AskMonster` prompt. */
    data class AskMonster(
        val monsterIndex: Int,
    ) : CompiledVmInput

    /** Supplies a raw key token for an `AskInventorySlot` prompt. */
    data class AskInventorySlot(
        val raw: String,
    ) : CompiledVmInput

    /** Supplies raw line input for an `AskText` prompt. */
    data class AskText(
        val raw: String,
    ) : CompiledVmInput
}

/** Typed input request that stopped a compiled VM run. */
sealed interface CompiledVmPendingInput {
    /** Script root that owns the pending input. */
    val root: rylween.runtime.ScriptRootRef

    /** Instruction index of the blocking opcode in its current instruction block. */
    val instructionIndex: Int

    /** Operand path to the blocking opcode or nested prompt. */
    val operandPath: List<Int>

    /** Pending `WaitInput` acknowledgment. */
    data class WaitInput(
        override val root: rylween.runtime.ScriptRootRef,
        override val instructionIndex: Int,
        override val operandPath: List<Int>,
        val reason: String?,
        val prompt: TextBlock? = null,
    ) : CompiledVmPendingInput

    /** Pending menu choice. */
    data class Menu(
        override val root: rylween.runtime.ScriptRootRef,
        override val instructionIndex: Int,
        override val operandPath: List<Int>,
        val prompt: TextBlock,
        val options: List<CompiledVmMenuOption>,
        val hasDefault: Boolean,
    ) : CompiledVmPendingInput {
        /** Primary menu keys accepted by this prompt. */
        val keys: List<Char> get() = options.map { option -> option.key }
    }

    /** Pending authored key prompt that stores the chosen binding value in [localName]. */
    data class AskKey(
        override val root: rylween.runtime.ScriptRootRef,
        override val instructionIndex: Int,
        override val operandPath: List<Int>,
        val prompt: TextBlock,
        val localName: String,
        val bindings: List<InputBindingModel>,
    ) : CompiledVmPendingInput

    /** Pending party-member prompt that stores the chosen one-based member number in [localName]. */
    data class AskMember(
        override val root: rylween.runtime.ScriptRootRef,
        override val instructionIndex: Int,
        override val operandPath: List<Int>,
        val prompt: TextBlock,
        val localName: String,
        val filter: MemberChoiceFilter,
        val accepting: MemberChoiceFilter,
        val choices: List<Int>,
    ) : CompiledVmPendingInput

    /** Pending monster prompt that stores the chosen zero-based monster target index in [localName]. */
    data class AskMonster(
        override val root: rylween.runtime.ScriptRootRef,
        override val instructionIndex: Int,
        override val operandPath: List<Int>,
        val prompt: TextBlock,
        val localName: String,
        val targetKeys: List<Char>,
    ) : CompiledVmPendingInput {
        /** Zero-based monster target indices accepted by this prompt. */
        val targetIndices: List<Int> get() = targetKeys.map { key -> key.uppercaseChar().code - 'A'.code }
    }

    /** Pending inventory-slot prompt that stores the chosen slot number in [localName]. */
    data class AskInventorySlot(
        override val root: rylween.runtime.ScriptRootRef,
        override val instructionIndex: Int,
        override val operandPath: List<Int>,
        val prompt: TextBlock,
        val localName: String,
        val firstKey: Char,
        val slots: IntRange,
        val filter: InventorySlotChoiceFilter,
        val choices: List<CompiledVmInventorySlotChoice>,
    ) : CompiledVmPendingInput {
        /** Primary inventory keys accepted by this prompt. */
        val keys: List<Char> get() = choices.map { choice -> choice.key }
    }

    /** Pending free-text prompt that resumes into either the match or miss branch. */
    data class AskText(
        override val root: rylween.runtime.ScriptRootRef,
        override val instructionIndex: Int,
        override val operandPath: List<Int>,
        val prompt: TextBlock,
        val accepted: List<String>,
        val caseSensitive: Boolean,
        val trim: Boolean,
        val maxLength: Int?,
    ) : CompiledVmPendingInput
}

/** One selectable menu option in a blocked compiled VM menu prompt. */
data class CompiledVmMenuOption(
    val key: Char,
    val optionIndex: Int,
)

/** One selectable inventory slot in a blocked compiled VM inventory prompt. */
data class CompiledVmInventorySlotChoice(
    val key: Char,
    val slot: Int,
)

/** Execution shape for a resumable [CompiledVmFrame]. */
enum class CompiledVmFrameKind {
    /** Plain instruction block, such as a root, branch, menu option, or text-answer body. */
    Block,

    /** Direct body block for one `Loop` iteration. */
    LoopBody,

    /** Direct body block for one `ForEachMember` member index. */
    ForEachMemberBody,
}

/** One resumable compiled VM instruction-block frame. */
data class CompiledVmFrame(
    val blockPath: List<Int>,
    val nextIndex: Int,
    val insideLoop: Boolean = false,
    val kind: CompiledVmFrameKind = CompiledVmFrameKind.Block,
    val enclosingInsideLoop: Boolean = insideLoop,
)

/** Saveable VM-local state needed to resume after [CompiledVmResult.Blocked]. */
data class CompiledVmContinuation(
    val root: rylween.runtime.ScriptRootRef,
    val runtime: RuntimeSessionState,
    val locals: Map<String, Int>,
    val textBindings: Map<String, String>,
    val frames: List<CompiledVmFrame>,
    val pending: CompiledVmPendingInput,
)

/** Completed or failed result from [CompiledVm.run]. */
sealed interface CompiledVmResult {
    /** Script ran to completion or hit a terminating `Stop`/outer `Break`. */
    data class Completed(
        val runtime: RuntimeSessionState,
        val locals: Map<String, Int>,
        val textBindings: Map<String, String>,
    ) : CompiledVmResult

    /** Script could not execute because the VM hit a typed diagnostic. */
    data class Failed(
        val diagnostic: VmDiagnostic,
    ) : CompiledVmResult

    /** Script reached a blocking prompt and can continue through [CompiledVm.resume]. */
    data class Blocked(
        val pending: CompiledVmPendingInput,
        val continuation: CompiledVmContinuation,
    ) : CompiledVmResult
}

/**
 * Executes validated compiled script programs for J2 non-interactive opcodes and expressions.
 *
 * [content] must come from [rylween.compiled.CompiledBundleLoader] so handle,
 * string, opcode-shape, and native-service checks have already run. This kernel
 * intentionally rejects opcodes whose host or continuation contract has not
 * landed yet, while implemented prompts return typed [CompiledVmPendingInput]
 * continuations instead of talking to terminal IO directly.
 */
class CompiledVm(
    private val content: LoadedCompiledContent,
    private val host: CompiledVmHost = RecordingCompiledVmHost(),
    private val nativeServices: NativeServiceCatalog = NativeServiceCatalog.default,
) {
    private val index = CompiledVmContentIndex(content)

    /** Runs [program] from [start] and returns final state, blocked input, or a diagnostic. */
    fun run(
        program: CompiledScriptProgram,
        start: CompiledVmStart,
    ): CompiledVmResult {
        val state =
            ExecutionState(
                root = program.root,
                runtime = start.runtime,
                locals = start.locals.toMutableMap(),
                textBindings = start.textBindings.toMutableMap(),
            )
        return result(
            state,
            executeBlock(
                state = state,
                instructions = program.instructions,
                path = emptyList(),
                insideLoop = false,
                blockKind = CompiledVmFrameKind.Block,
                enclosingInsideLoop = false,
                startIndex = 0,
                parentFrames = emptyList(),
            ),
        )
    }

    /** Resumes a blocked [continuation] after receiving [input]. */
    fun resume(
        program: CompiledScriptProgram,
        continuation: CompiledVmContinuation,
        input: CompiledVmInput,
    ): CompiledVmResult {
        if (!continuation.root.sameRoot(program.root)) {
            return CompiledVmResult.Failed(
                VmDiagnostic.BadContinuationFrame(
                    root = program.root,
                    instructionIndex = 0,
                    operandPath = emptyList(),
                    frame = continuation.root.toString(),
                    reason = "ContinuationRootMismatch",
                ),
            )
        }
        if (!continuation.accepts(input)) {
            return CompiledVmResult.Failed(
                VmDiagnostic.BadContinuationFrame(
                    root = continuation.root,
                    instructionIndex = continuation.pending.instructionIndex,
                    operandPath = continuation.pending.operandPath,
                    frame = continuation.pending::class.simpleName.orEmpty(),
                    reason = "InputDoesNotMatchPendingPrompt",
                ),
            )
        }
        if (continuation.pending is CompiledVmPendingInput.Menu && input is CompiledVmInput.MenuKey) {
            return resumeMenu(program, continuation, input)
        }
        if (continuation.pending is CompiledVmPendingInput.AskKey && input is CompiledVmInput.AskKey) {
            return resumeAskKey(program, continuation, input)
        }
        if (continuation.pending is CompiledVmPendingInput.AskMember && input is CompiledVmInput.AskMember) {
            return resumeAskMember(program, continuation, input)
        }
        if (continuation.pending is CompiledVmPendingInput.AskMonster && input is CompiledVmInput.AskMonster) {
            return resumeAskMonster(program, continuation, input)
        }
        if (continuation.pending is CompiledVmPendingInput.AskInventorySlot && input is CompiledVmInput.AskInventorySlot) {
            return resumeAskInventorySlot(program, continuation, input)
        }
        if (continuation.pending is CompiledVmPendingInput.AskText && input is CompiledVmInput.AskText) {
            return resumeAskText(program, continuation, input)
        }
        val state =
            ExecutionState(
                root = continuation.root,
                runtime = continuation.runtime,
                locals = continuation.locals.toMutableMap(),
                textBindings = continuation.textBindings.toMutableMap(),
            )
        return result(state, executeFrames(state, program.instructions, continuation.frames))
    }

    private fun resumeMenu(
        program: CompiledScriptProgram,
        continuation: CompiledVmContinuation,
        input: CompiledVmInput.MenuKey,
    ): CompiledVmResult {
        val pending = continuation.pending as CompiledVmPendingInput.Menu
        val option = pending.options.firstOrNull { option -> option.key == input.key.uppercaseChar() }
        if (option == null && !pending.hasDefault) {
            return CompiledVmResult.Blocked(pending, continuation)
        }
        val state =
            ExecutionState(
                root = continuation.root,
                runtime = continuation.runtime,
                locals = continuation.locals.toMutableMap(),
                textBindings = continuation.textBindings.toMutableMap(),
            )
        val menuFrame = continuation.frames.firstOrNull()
        val branchFrame =
            CompiledVmFrame(
                blockPath =
                    if (option != null) {
                        pending.operandPath + listOf(1, option.optionIndex)
                    } else {
                        pending.operandPath + 2
                    },
                nextIndex = 0,
                insideLoop = menuFrame?.insideLoop ?: false,
            )
        return result(state, executeFrames(state, program.instructions, listOf(branchFrame) + continuation.frames))
    }

    private fun resumeAskKey(
        program: CompiledScriptProgram,
        continuation: CompiledVmContinuation,
        input: CompiledVmInput.AskKey,
    ): CompiledVmResult {
        val pending = continuation.pending as CompiledVmPendingInput.AskKey
        val binding = pending.bindings.firstOrNull { binding -> binding.matches(input.raw) }
        if (binding == null) {
            return CompiledVmResult.Blocked(pending, continuation)
        }
        val state =
            ExecutionState(
                root = continuation.root,
                runtime = continuation.runtime,
                locals = continuation.locals.toMutableMap(),
                textBindings = continuation.textBindings.toMutableMap(),
            )
        state.locals[pending.localName] = binding.compiledVmPromptValue()
        return result(state, executeFrames(state, program.instructions, continuation.frames))
    }

    private fun resumeAskMember(
        program: CompiledScriptProgram,
        continuation: CompiledVmContinuation,
        input: CompiledVmInput.AskMember,
    ): CompiledVmResult {
        val pending = continuation.pending as CompiledVmPendingInput.AskMember
        if (input.memberNumber !in pending.choices) {
            return CompiledVmResult.Blocked(pending, continuation)
        }
        val state =
            ExecutionState(
                root = continuation.root,
                runtime = continuation.runtime,
                locals = continuation.locals.toMutableMap(),
                textBindings = continuation.textBindings.toMutableMap(),
            )
        state.locals[pending.localName] = input.memberNumber
        return result(state, executeFrames(state, program.instructions, continuation.frames))
    }

    private fun resumeAskMonster(
        program: CompiledScriptProgram,
        continuation: CompiledVmContinuation,
        input: CompiledVmInput.AskMonster,
    ): CompiledVmResult {
        val pending = continuation.pending as CompiledVmPendingInput.AskMonster
        if (input.monsterIndex !in pending.targetIndices) {
            return CompiledVmResult.Blocked(pending, continuation)
        }
        val state =
            ExecutionState(
                root = continuation.root,
                runtime = continuation.runtime,
                locals = continuation.locals.toMutableMap(),
                textBindings = continuation.textBindings.toMutableMap(),
            )
        state.locals[pending.localName] = input.monsterIndex
        return result(state, executeFrames(state, program.instructions, continuation.frames))
    }

    private fun resumeAskInventorySlot(
        program: CompiledScriptProgram,
        continuation: CompiledVmContinuation,
        input: CompiledVmInput.AskInventorySlot,
    ): CompiledVmResult {
        val pending = continuation.pending as CompiledVmPendingInput.AskInventorySlot
        val choice = pending.choices.firstOrNull { choice -> inputKey(choice.key).matches(input.raw) }
        if (choice == null) {
            return CompiledVmResult.Blocked(pending, continuation)
        }
        val state =
            ExecutionState(
                root = continuation.root,
                runtime = continuation.runtime,
                locals = continuation.locals.toMutableMap(),
                textBindings = continuation.textBindings.toMutableMap(),
            )
        state.locals[pending.localName] = choice.slot
        return result(state, executeFrames(state, program.instructions, continuation.frames))
    }

    private fun resumeAskText(
        program: CompiledScriptProgram,
        continuation: CompiledVmContinuation,
        input: CompiledVmInput.AskText,
    ): CompiledVmResult {
        val pending = continuation.pending as CompiledVmPendingInput.AskText
        val branchIndex = if (pending.accepts(input.raw)) TEXT_ANSWER_MATCH_BRANCH else TEXT_ANSWER_MISS_BRANCH
        val state =
            ExecutionState(
                root = continuation.root,
                runtime = continuation.runtime,
                locals = continuation.locals.toMutableMap(),
                textBindings = continuation.textBindings.toMutableMap(),
            )
        val promptFrame = continuation.frames.firstOrNull()
        val branchFrame =
            CompiledVmFrame(
                blockPath = pending.operandPath + listOf(1, branchIndex),
                nextIndex = 0,
                insideLoop = promptFrame?.insideLoop ?: false,
            )
        return result(state, executeFrames(state, program.instructions, listOf(branchFrame) + continuation.frames))
    }

    private fun result(
        state: ExecutionState,
        signal: ControlSignal,
    ): CompiledVmResult =
        when (signal) {
            ControlSignal.Continue,
            ControlSignal.BreakNearestLoop,
            ControlSignal.StopScript,
            -> CompiledVmResult.Completed(state.runtime, state.locals.toMap(), state.textBindings.toMap())
            is ControlSignal.Failed -> CompiledVmResult.Failed(signal.diagnostic)
            is ControlSignal.Blocked ->
                CompiledVmResult.Blocked(
                    pending = signal.pending,
                    continuation =
                        CompiledVmContinuation(
                            root = state.root,
                            runtime = state.runtime,
                            locals = state.locals.toMap(),
                            textBindings = state.textBindings.toMap(),
                            frames = signal.frames,
                            pending = signal.pending,
                        ),
                )
        }

    private fun executeBlock(
        state: ExecutionState,
        instructions: List<CompiledInstruction>,
        path: List<Int>,
        insideLoop: Boolean,
        blockKind: CompiledVmFrameKind,
        enclosingInsideLoop: Boolean,
        startIndex: Int,
        parentFrames: List<CompiledVmFrame>,
    ): ControlSignal {
        for (instructionIndex in startIndex until instructions.size) {
            val instruction = instructions[instructionIndex]
            val location = VmLocation(state.root, instructionIndex, path, path + instructionIndex, instruction.opcode)
            when (val signal = executeInstruction(state, instruction, location, insideLoop, blockKind, enclosingInsideLoop, parentFrames)) {
                ControlSignal.Continue -> Unit
                ControlSignal.BreakNearestLoop,
                ControlSignal.StopScript,
                is ControlSignal.Failed,
                is ControlSignal.Blocked,
                -> return signal
            }
        }
        return ControlSignal.Continue
    }

    private fun executeFrames(
        state: ExecutionState,
        rootInstructions: List<CompiledInstruction>,
        frames: List<CompiledVmFrame>,
    ): ControlSignal {
        var remaining = frames
        while (remaining.isNotEmpty()) {
            val frame = remaining.first()
            when (val signal = executeFrame(state, rootInstructions, frame, remaining.drop(1))) {
                ControlSignal.Continue -> remaining = remaining.drop(1)
                ControlSignal.BreakNearestLoop,
                ControlSignal.StopScript,
                -> {
                    val afterLoop = remaining.drop(1).afterConsumedLoopSignal()
                    if (afterLoop == null) {
                        return signal
                    }
                    remaining = afterLoop
                }
                is ControlSignal.Failed -> return signal
                is ControlSignal.Blocked -> return signal
            }
        }
        return ControlSignal.Continue
    }

    private fun executeFrame(
        state: ExecutionState,
        rootInstructions: List<CompiledInstruction>,
        frame: CompiledVmFrame,
        parentFrames: List<CompiledVmFrame>,
    ): ControlSignal =
        when (frame.kind) {
            CompiledVmFrameKind.Block -> {
                val instructions = instructionsFor(rootInstructions, frame.blockPath)
                if (instructions == null) {
                    ControlSignal.Failed(frame.badContinuation(state, "UnknownInstructionBlock"))
                } else {
                    executeBlock(
                        state = state,
                        instructions = instructions,
                        path = frame.blockPath,
                        insideLoop = frame.insideLoop,
                        blockKind = CompiledVmFrameKind.Block,
                        enclosingInsideLoop = frame.enclosingInsideLoop,
                        startIndex = frame.nextIndex,
                        parentFrames = parentFrames,
                    )
                }
            }
            CompiledVmFrameKind.LoopBody -> executeLoopBodyFrame(state, rootInstructions, frame, parentFrames)
            CompiledVmFrameKind.ForEachMemberBody -> executeForEachMemberBodyFrame(state, rootInstructions, frame, parentFrames)
        }

    private fun executeInstruction(
        state: ExecutionState,
        instruction: CompiledInstruction,
        location: VmLocation,
        insideLoop: Boolean,
        blockKind: CompiledVmFrameKind,
        enclosingInsideLoop: Boolean,
        parentFrames: List<CompiledVmFrame>,
    ): ControlSignal =
        when (instruction.opcode) {
            CompiledOpcode.Screen -> {
                renderText(state, instruction.textBlock(0), location.child(0)).fold(
                    ok = { text ->
                        host.trace(EngineTraceEvent.Screen(text))
                        ControlSignal.Continue
                    },
                    failed = { ControlSignal.Failed(it) },
                )
            }
            CompiledOpcode.Emit -> {
                host.trace(EngineTraceEvent.Emit(TextId(index.slug(instruction.handle(0)))))
                ControlSignal.Continue
            }
            CompiledOpcode.EmitIndexed -> {
                val pattern = TextPattern(index.string(instruction.stringId(0)))
                val value = evalInt(state, instruction.intExpr(1), location.child(1))
                value.fold(
                    ok = {
                        host.trace(EngineTraceEvent.EmitIndexed(pattern, it))
                        ControlSignal.Continue
                    },
                    failed = { ControlSignal.Failed(it) },
                )
            }
            CompiledOpcode.Pause -> {
                host.trace(EngineTraceEvent.Pause(instruction.integer(0)))
                ControlSignal.Continue
            }
            CompiledOpcode.WaitInput -> {
                val pending =
                    CompiledVmPendingInput.WaitInput(
                        root = location.root,
                        instructionIndex = location.instructionIndex,
                        operandPath = location.operandPath,
                        reason = instruction.optionalString(0),
                    )
                host.trace(EngineTraceEvent.WaitInput(pending.reason, pending.prompt))
                ControlSignal.Blocked(
                    pending = pending,
                    frames =
                        listOf(
                            continuationFrame(
                                blockPath = location.blockPath,
                                nextIndex = location.instructionIndex + 1,
                                insideLoop = insideLoop,
                                kind = blockKind,
                                enclosingInsideLoop = enclosingInsideLoop,
                            ),
                        ) + parentFrames,
                )
            }
            CompiledOpcode.Menu -> {
                val promptAndOptions =
                    renderText(state, instruction.textBlock(0), location.child(0))
                        .flatMap { prompt ->
                            menuOptions(instruction, location.child(1)).map { options -> prompt to options }
                        }
                promptAndOptions.fold(
                    ok = { (prompt, options) ->
                        val pending =
                            CompiledVmPendingInput.Menu(
                                root = location.root,
                                instructionIndex = location.instructionIndex,
                                operandPath = location.operandPath,
                                prompt = prompt,
                                options = options,
                                hasDefault = instruction.instructions(2).isNotEmpty(),
                            )
                        host.trace(EngineTraceEvent.Menu(prompt, pending.keys))
                        ControlSignal.Blocked(
                            pending = pending,
                            frames =
                                listOf(
                                    continuationFrame(
                                        blockPath = location.blockPath,
                                        nextIndex = location.instructionIndex + 1,
                                        insideLoop = insideLoop,
                                        kind = blockKind,
                                        enclosingInsideLoop = enclosingInsideLoop,
                                    ),
                                ) + parentFrames,
                        )
                    },
                    failed = { ControlSignal.Failed(it) },
                )
            }
            CompiledOpcode.View -> {
                host.trace(EngineTraceEvent.View(viewCommand(instruction.view(0))))
                ControlSignal.Continue
            }
            CompiledOpcode.AskKey -> {
                val promptAndBindings =
                    renderText(state, instruction.textBlock(0), location.child(0))
                        .map { prompt -> prompt to instruction.inputBindings(2) }
                promptAndBindings.fold(
                    ok = { (prompt, bindings) ->
                        val pending =
                            CompiledVmPendingInput.AskKey(
                                root = location.root,
                                instructionIndex = location.instructionIndex,
                                operandPath = location.operandPath,
                                prompt = prompt,
                                localName = index.string(instruction.stringId(1)),
                                bindings = bindings,
                            )
                        host.trace(EngineTraceEvent.AskKey(prompt, bindings))
                        ControlSignal.Blocked(
                            pending = pending,
                            frames =
                                listOf(
                                    continuationFrame(
                                        blockPath = location.blockPath,
                                        nextIndex = location.instructionIndex + 1,
                                        insideLoop = insideLoop,
                                        kind = blockKind,
                                        enclosingInsideLoop = enclosingInsideLoop,
                                    ),
                                ) + parentFrames,
                        )
                    },
                    failed = { ControlSignal.Failed(it) },
                )
            }
            CompiledOpcode.AskMember -> {
                val prompt = renderText(state, instruction.textBlock(0), location.child(0))
                val filter = instruction.memberChoiceFilter(2)
                val accepting = instruction.memberChoiceFilter(3)
                prompt.fold(
                    ok = { text ->
                        val pending =
                            CompiledVmPendingInput.AskMember(
                                root = location.root,
                                instructionIndex = location.instructionIndex,
                                operandPath = location.operandPath,
                                prompt = text,
                                localName = index.string(instruction.stringId(1)),
                                filter = filter,
                                accepting = accepting,
                                choices = state.party.memberChoices(accepting),
                            )
                        host.trace(EngineTraceEvent.AskMember(text, filter))
                        ControlSignal.Blocked(
                            pending = pending,
                            frames =
                                listOf(
                                    continuationFrame(
                                        blockPath = location.blockPath,
                                        nextIndex = location.instructionIndex + 1,
                                        insideLoop = insideLoop,
                                        kind = blockKind,
                                        enclosingInsideLoop = enclosingInsideLoop,
                                    ),
                                ) + parentFrames,
                        )
                    },
                    failed = { ControlSignal.Failed(it) },
                )
            }
            CompiledOpcode.AskInventorySlot -> {
                val prompt = renderText(state, instruction.textBlock(0), location.child(0))
                val firstKey = instruction.character(2)
                val slots = instruction.integer(3)..instruction.integer(4)
                val filter = instruction.inventorySlotChoiceFilter(5)
                prompt.fold(
                    ok = { text ->
                        val pending =
                            CompiledVmPendingInput.AskInventorySlot(
                                root = location.root,
                                instructionIndex = location.instructionIndex,
                                operandPath = location.operandPath,
                                prompt = text,
                                localName = index.string(instruction.stringId(1)),
                                firstKey = firstKey,
                                slots = slots,
                                filter = filter,
                                choices = state.party.inventorySlotChoices(filter, slots, firstKey),
                            )
                        host.trace(EngineTraceEvent.AskInventorySlot(text, firstKey, slots, filter))
                        ControlSignal.Blocked(
                            pending = pending,
                            frames =
                                listOf(
                                    continuationFrame(
                                        blockPath = location.blockPath,
                                        nextIndex = location.instructionIndex + 1,
                                        insideLoop = insideLoop,
                                        kind = blockKind,
                                        enclosingInsideLoop = enclosingInsideLoop,
                                    ),
                                ) + parentFrames,
                        )
                    },
                    failed = { ControlSignal.Failed(it) },
                )
            }
            CompiledOpcode.AskMonster -> {
                val prompt = renderText(state, instruction.textBlock(0), location.child(0))
                prompt.fold(
                    ok = { text ->
                        val pending =
                            CompiledVmPendingInput.AskMonster(
                                root = location.root,
                                instructionIndex = location.instructionIndex,
                                operandPath = location.operandPath,
                                prompt = text,
                                localName = index.string(instruction.stringId(1)),
                                targetKeys = host.monsterTargetKeys().map { key -> key.uppercaseChar() },
                            )
                        ControlSignal.Blocked(
                            pending = pending,
                            frames =
                                listOf(
                                    continuationFrame(
                                        blockPath = location.blockPath,
                                        nextIndex = location.instructionIndex + 1,
                                        insideLoop = insideLoop,
                                        kind = blockKind,
                                        enclosingInsideLoop = enclosingInsideLoop,
                                    ),
                                ) + parentFrames,
                        )
                    },
                    failed = { ControlSignal.Failed(it) },
                )
            }
            CompiledOpcode.AskText -> {
                val prompt = renderText(state, instruction.textBlock(0), location.child(0))
                val answer = instruction.textAnswer(1)
                val accepted = answer.acceptedStrings.map(index::string)
                prompt.fold(
                    ok = { text ->
                        val pending =
                            CompiledVmPendingInput.AskText(
                                root = location.root,
                                instructionIndex = location.instructionIndex,
                                operandPath = location.operandPath,
                                prompt = text,
                                accepted = accepted,
                                caseSensitive = answer.caseSensitive,
                                trim = answer.trim,
                                maxLength = answer.maxLength,
                            )
                        host.trace(EngineTraceEvent.AskText(text, accepted, answer.maxLength))
                        ControlSignal.Blocked(
                            pending = pending,
                            frames =
                                listOf(
                                    continuationFrame(
                                        blockPath = location.blockPath,
                                        nextIndex = location.instructionIndex + 1,
                                        insideLoop = insideLoop,
                                        kind = blockKind,
                                        enclosingInsideLoop = enclosingInsideLoop,
                                    ),
                                ) + parentFrames,
                        )
                    },
                    failed = { ControlSignal.Failed(it) },
                )
            }
            CompiledOpcode.LetInt -> {
                val name = index.string(instruction.stringId(0))
                val value = evalInt(state, instruction.intExpr(1), location.child(1))
                value.fold(
                    ok = {
                        state.locals[name] = it
                        ControlSignal.Continue
                    },
                    failed = { ControlSignal.Failed(it) },
                )
            }
            CompiledOpcode.BindText -> {
                val name = index.string(instruction.stringId(0))
                val value = evalString(state, instruction.stringExpr(1), location.child(1))
                value.fold(
                    ok = {
                        state.textBindings[name] = it
                        ControlSignal.Continue
                    },
                    failed = { ControlSignal.Failed(it) },
                )
            }
            CompiledOpcode.Set ->
                writeLValue(
                    state = state,
                    target = instruction.lvalue(0),
                    value = evalInt(state, instruction.intExpr(1), location.child(1)),
                    location = location.child(0),
                    readCurrent = false,
                    transform = { _, next -> next },
                )
            CompiledOpcode.Add ->
                writeLValue(
                    state = state,
                    target = instruction.lvalue(0),
                    value = evalInt(state, instruction.intExpr(1), location.child(1)),
                    location = location.child(0),
                    transform = Int::plus,
                )
            CompiledOpcode.Sub ->
                writeLValue(
                    state = state,
                    target = instruction.lvalue(0),
                    value = evalInt(state, instruction.intExpr(1), location.child(1)),
                    location = location.child(0),
                    transform = Int::minus,
                )
            CompiledOpcode.AddClamped -> {
                val add = evalInt(state, instruction.intExpr(1), location.child(1))
                val max = evalInt(state, instruction.intExpr(2), location.child(2))
                add.zip(max).fold(
                    ok = { (addValue, maxValue) ->
                        writeLValue(
                            state = state,
                            target = instruction.lvalue(0),
                            value = EvalResult.Ok(addValue),
                            location = location.child(0),
                            transform = { current, next -> (current + next).coerceAtMost(maxValue) },
                        )
                    },
                    failed = { ControlSignal.Failed(it) },
                )
            }
            CompiledOpcode.SetFlag -> {
                val flag = index.slug(instruction.handle(0))
                val native = index.flagNative(instruction.handle(1))
                state.party = state.party.withFlagState(flag, native)
                host.trace(EngineTraceEvent.SetFlag(flag, native))
                ControlSignal.Continue
            }
            CompiledOpcode.AppendJournal -> {
                renderText(state, instruction.textBlock(0), location.child(0)).fold(
                    ok = { text ->
                        state.party = state.party.withJournalLines(host.journalLines(text, state.runtime.withActiveParty(state.party)))
                        host.trace(EngineTraceEvent.AppendJournal(text))
                        ControlSignal.Continue
                    },
                    failed = { ControlSignal.Failed(it) },
                )
            }
            CompiledOpcode.If -> {
                val cond = evalBool(state, instruction.boolExpr(0), location.child(0))
                cond.fold(
                    ok = { value ->
                        val branch = if (value) instruction.instructions(1) else instruction.instructions(2)
                        executeBlock(
                            state = state,
                            instructions = branch,
                            path = location.operandBranch(if (value) 1 else 2),
                            insideLoop = insideLoop,
                            blockKind = CompiledVmFrameKind.Block,
                            enclosingInsideLoop = insideLoop,
                            startIndex = 0,
                            parentFrames =
                                listOf(
                                    continuationFrame(
                                        blockPath = location.blockPath,
                                        nextIndex = location.instructionIndex + 1,
                                        insideLoop = insideLoop,
                                        kind = blockKind,
                                        enclosingInsideLoop = enclosingInsideLoop,
                                    ),
                                ) + parentFrames,
                        )
                    },
                    failed = { ControlSignal.Failed(it) },
                )
            }
            CompiledOpcode.Loop ->
                executeLoop(
                    state = state,
                    body = instruction.instructions(0),
                    location = location,
                    insideLoop = insideLoop,
                    blockKind = blockKind,
                    enclosingInsideLoop = enclosingInsideLoop,
                    parentFrames = parentFrames,
                )
            CompiledOpcode.Break -> {
                val reason = instruction.optionalString(0)
                val signal = instruction.optionalSignal(1) ?: ScriptControlSignal.BreakNearestLoop
                host.trace(EngineTraceEvent.Break(reason, signal))
                controlSignal(signal, insideLoop)
            }
            CompiledOpcode.Stop -> {
                val reason = instruction.optionalString(0)
                val signal = instruction.optionalSignal(1) ?: ScriptControlSignal.StopScript
                host.trace(EngineTraceEvent.Stop(reason, signal))
                controlSignal(signal, insideLoop)
            }
            CompiledOpcode.Switch -> {
                val expr = evalInt(state, instruction.intExpr(0), location.child(0))
                expr.fold(
                    ok = { value ->
                        val cases = instruction.switchCases(1)
                        val caseIndex = cases.indexOfFirst { case -> case.match == value || case.match == null }
                        if (caseIndex < 0) {
                            ControlSignal.Continue
                        } else {
                            executeBlock(
                                state = state,
                                instructions = cases[caseIndex].body,
                                path = location.operandBranch(1, caseIndex),
                                insideLoop = insideLoop,
                                blockKind = CompiledVmFrameKind.Block,
                                enclosingInsideLoop = insideLoop,
                                startIndex = 0,
                                parentFrames =
                                    listOf(
                                        continuationFrame(
                                            blockPath = location.blockPath,
                                            nextIndex = location.instructionIndex + 1,
                                            insideLoop = insideLoop,
                                            kind = blockKind,
                                            enclosingInsideLoop = enclosingInsideLoop,
                                        ),
                                    ) + parentFrames,
                            )
                        }
                    },
                    failed = { ControlSignal.Failed(it) },
                )
            }
            CompiledOpcode.ForEachMember ->
                executeForEachMember(
                    state = state,
                    instruction = instruction,
                    location = location,
                    insideLoop = insideLoop,
                    blockKind = blockKind,
                    enclosingInsideLoop = enclosingInsideLoop,
                    parentFrames = parentFrames,
                )
            CompiledOpcode.AchieveMilestone -> {
                val milestone = GameMilestoneId(index.slug(instruction.handle(0)))
                state.party = state.party.withMilestone(milestone)
                host.trace(EngineTraceEvent.AchieveMilestone(milestone))
                ControlSignal.Continue
            }
            else ->
                ControlSignal.Failed(
                    VmDiagnostic.UnsupportedHostService(
                        root = location.root,
                        instructionIndex = location.instructionIndex,
                        operandPath = location.operandPath,
                        service = instruction.opcode.name,
                        reason = "Opcode is outside the J2.1 non-interactive VM slice",
                    ),
                )
        }

    private fun executeLoop(
        state: ExecutionState,
        body: List<CompiledInstruction>,
        location: VmLocation,
        insideLoop: Boolean,
        blockKind: CompiledVmFrameKind,
        enclosingInsideLoop: Boolean,
        parentFrames: List<CompiledVmFrame>,
    ): ControlSignal {
        val afterLoopFrames =
            listOf(
                continuationFrame(
                    blockPath = location.blockPath,
                    nextIndex = location.instructionIndex + 1,
                    insideLoop = insideLoop,
                    kind = blockKind,
                    enclosingInsideLoop = enclosingInsideLoop,
                ),
            ) + parentFrames
        return executeLoopIterations(
            state = state,
            body = body,
            location = location,
            enclosingInsideLoop = insideLoop,
            afterLoopFrames = afterLoopFrames,
            startIteration = 0,
        )
    }

    private fun executeLoopIterations(
        state: ExecutionState,
        body: List<CompiledInstruction>,
        location: VmLocation,
        enclosingInsideLoop: Boolean,
        afterLoopFrames: List<CompiledVmFrame>,
        startIteration: Int,
    ): ControlSignal {
        for (iteration in startIteration until MAX_LOOP_ITERATIONS) {
            when (
                val signal =
                    executeBlock(
                        state = state,
                        instructions = body,
                        path = location.operandBranch(0, iteration),
                        insideLoop = true,
                        blockKind = CompiledVmFrameKind.LoopBody,
                        enclosingInsideLoop = enclosingInsideLoop,
                        startIndex = 0,
                        parentFrames = afterLoopFrames,
                    )
            ) {
                ControlSignal.Continue -> Unit
                ControlSignal.BreakNearestLoop,
                ControlSignal.StopScript,
                -> return ControlSignal.Continue
                is ControlSignal.Failed,
                is ControlSignal.Blocked,
                -> return signal
            }
        }
        return ControlSignal.Continue
    }

    private fun executeLoopBodyFrame(
        state: ExecutionState,
        rootInstructions: List<CompiledInstruction>,
        frame: CompiledVmFrame,
        parentFrames: List<CompiledVmFrame>,
    ): ControlSignal {
        val context =
            iterationFrameContext(
                state = state,
                rootInstructions = rootInstructions,
                frame = frame,
                expectedOpcode = CompiledOpcode.Loop,
                expectedOperand = 0,
            ) ?: return ControlSignal.Failed(frame.badContinuation(state, "UnknownLoopFrame"))
        val body = context.instruction.instructions(0)
        return when (
            val signal =
                executeBlock(
                    state = state,
                    instructions = body,
                    path = frame.blockPath,
                    insideLoop = true,
                    blockKind = CompiledVmFrameKind.LoopBody,
                    enclosingInsideLoop = frame.enclosingInsideLoop,
                    startIndex = frame.nextIndex,
                    parentFrames = parentFrames,
                )
        ) {
            ControlSignal.Continue ->
                executeLoopIterations(
                    state = state,
                    body = body,
                    location = context.location,
                    enclosingInsideLoop = frame.enclosingInsideLoop,
                    afterLoopFrames = parentFrames,
                    startIteration = context.cursor + 1,
                )
            ControlSignal.BreakNearestLoop,
            ControlSignal.StopScript,
            -> ControlSignal.Continue
            is ControlSignal.Failed,
            is ControlSignal.Blocked,
            -> signal
        }
    }

    private fun executeForEachMember(
        state: ExecutionState,
        instruction: CompiledInstruction,
        location: VmLocation,
        insideLoop: Boolean,
        blockKind: CompiledVmFrameKind,
        enclosingInsideLoop: Boolean,
        parentFrames: List<CompiledVmFrame>,
    ): ControlSignal {
        val afterForEachFrames =
            listOf(
                continuationFrame(
                    blockPath = location.blockPath,
                    nextIndex = location.instructionIndex + 1,
                    insideLoop = insideLoop,
                    kind = blockKind,
                    enclosingInsideLoop = enclosingInsideLoop,
                ),
            ) + parentFrames
        return executeForEachMemberIterations(
            state = state,
            instruction = instruction,
            location = location,
            insideLoop = insideLoop,
            afterForEachFrames = afterForEachFrames,
            startMemberIndex = 0,
        )
    }

    private fun executeForEachMemberBodyFrame(
        state: ExecutionState,
        rootInstructions: List<CompiledInstruction>,
        frame: CompiledVmFrame,
        parentFrames: List<CompiledVmFrame>,
    ): ControlSignal {
        val context =
            iterationFrameContext(
                state = state,
                rootInstructions = rootInstructions,
                frame = frame,
                expectedOpcode = CompiledOpcode.ForEachMember,
                expectedOperand = 2,
            ) ?: return ControlSignal.Failed(frame.badContinuation(state, "UnknownForEachMemberFrame"))
        return when (
            val signal =
                executeForEachMemberBody(
                    state = state,
                    instruction = context.instruction,
                    location = context.location,
                    memberIndex = context.cursor,
                    insideLoop = frame.insideLoop,
                    startIndex = frame.nextIndex,
                    afterForEachFrames = parentFrames,
                )
        ) {
            ControlSignal.Continue ->
                executeForEachMemberIterations(
                    state = state,
                    instruction = context.instruction,
                    location = context.location,
                    insideLoop = frame.insideLoop,
                    afterForEachFrames = parentFrames,
                    startMemberIndex = context.cursor + 1,
                )
            else -> signal
        }
    }

    private fun executeForEachMemberIterations(
        state: ExecutionState,
        instruction: CompiledInstruction,
        location: VmLocation,
        insideLoop: Boolean,
        afterForEachFrames: List<CompiledVmFrame>,
        startMemberIndex: Int,
    ): ControlSignal {
        for (memberIndex in startMemberIndex until state.party.members.size) {
            val signal =
                executeForEachMemberBody(
                    state = state,
                    instruction = instruction,
                    location = location,
                    memberIndex = memberIndex,
                    insideLoop = insideLoop,
                    startIndex = 0,
                    afterForEachFrames = afterForEachFrames,
                )
            when (signal) {
                ControlSignal.Continue -> Unit
                else -> return signal
            }
        }
        return ControlSignal.Continue
    }

    private fun executeForEachMemberBody(
        state: ExecutionState,
        instruction: CompiledInstruction,
        location: VmLocation,
        memberIndex: Int,
        insideLoop: Boolean,
        startIndex: Int,
        afterForEachFrames: List<CompiledVmFrame>,
    ): ControlSignal {
        val includeDead = instruction.boolean(1)
        val member = state.party.members.getOrNull(memberIndex) ?: return ControlSignal.Continue
        if (!includeDead && member.dead) {
            return ControlSignal.Continue
        }
        val name = index.string(instruction.stringId(0))
        state.locals[name] = memberIndex + 1
        return executeBlock(
            state = state,
            instructions = instruction.instructions(2),
            path = location.operandBranch(2, memberIndex),
            insideLoop = insideLoop,
            blockKind = CompiledVmFrameKind.ForEachMemberBody,
            enclosingInsideLoop = insideLoop,
            startIndex = startIndex,
            parentFrames = afterForEachFrames,
        )
    }

    private fun controlSignal(
        signal: ScriptControlSignal,
        insideLoop: Boolean,
    ): ControlSignal =
        when (signal) {
            ScriptControlSignal.Continue -> ControlSignal.Continue
            ScriptControlSignal.BreakNearestLoop -> if (insideLoop) ControlSignal.BreakNearestLoop else ControlSignal.StopScript
            ScriptControlSignal.StopScript -> ControlSignal.StopScript
        }

    private fun renderText(
        state: ExecutionState,
        block: CompiledTextBlock,
        location: VmLocation,
    ): EvalResult<TextBlock> =
        when (block) {
            is CompiledTextBlock.Ref -> EvalResult.Ok(TextBlock.Ref(TextId(index.slug(block.text))))
            is CompiledTextBlock.Inline -> EvalResult.Ok(TextBlock.Inline(block.lineStrings.map(index::string)))
            is CompiledTextBlock.Structured -> {
                val rendered = mutableListOf<String>()
                block.lines.forEachIndexed { lineIndex, line ->
                    val parts = mutableListOf<String>()
                    line.parts.forEachIndexed { partIndex, part ->
                        when (val value = renderTextPart(state, part, location.child(lineIndex).child(partIndex))) {
                            is EvalResult.Failed -> return value
                            is EvalResult.Ok -> parts += value.value
                        }
                    }
                    rendered += parts.joinToString(separator = "")
                }
                EvalResult.Ok(TextBlock.Inline(rendered))
            }
        }

    private fun renderTextPart(
        state: ExecutionState,
        part: CompiledTextPart,
        location: VmLocation,
    ): EvalResult<String> =
        when (part) {
            is CompiledTextPart.Literal -> EvalResult.Ok(index.string(part.string))
            is CompiledTextPart.IntValue ->
                evalInt(state, part.value, location).map(Int::toString)
            is CompiledTextPart.StringValue -> evalString(state, part.value, location)
            is CompiledTextPart.RoleName -> EvalResult.Ok("{${index.slug(part.role)}.name}")
            is CompiledTextPart.RoleLocation -> EvalResult.Ok("{${index.slug(part.role)}.location}")
            is CompiledTextPart.TownName -> EvalResult.Ok(index.slug(part.town))
            is CompiledTextPart.FuzzyTownLocation -> EvalResult.Ok("${index.slug(part.town)}.near")
            is CompiledTextPart.LocationCoordinate ->
                EvalResult.Ok(
                    locationCoordinate(state, index.slug(part.location))
                        ?: "{${index.slug(part.location)}.coordinates}",
                )
            is CompiledTextPart.Runtime -> EvalResult.Ok(runtimeTextPart(part.kind).fallbackText())
        }

    private fun runtimeTextPart(kind: CompiledRuntimeTextPart): TextPart =
        when (kind) {
            CompiledRuntimeTextPart.OfferedItemName -> TextPart.OfferedItemName
            CompiledRuntimeTextPart.OfferedItemPrice -> TextPart.OfferedItemPrice
            CompiledRuntimeTextPart.SelectedItemName -> TextPart.SelectedItemName
            CompiledRuntimeTextPart.ShopPrice -> TextPart.ShopPrice
            CompiledRuntimeTextPart.ShopCarryCap -> TextPart.ShopCarryCap
            CompiledRuntimeTextPart.HuntMemberName -> TextPart.HuntMemberName
            CompiledRuntimeTextPart.HuntMemberSubjectPronoun -> TextPart.HuntMemberSubjectPronoun
            CompiledRuntimeTextPart.HuntFoodGained -> TextPart.HuntFoodGained
            CompiledRuntimeTextPart.PartyMemberMax -> TextPart.PartyMemberMax
            CompiledRuntimeTextPart.WildernessDungeonName -> TextPart.WildernessDungeonName
            CompiledRuntimeTextPart.WildernessDungeonLevels -> TextPart.WildernessDungeonLevels
            CompiledRuntimeTextPart.DungeonDepth -> TextPart.DungeonDepth
            CompiledRuntimeTextPart.ConversationNpcName -> TextPart.ConversationNpcName
            CompiledRuntimeTextPart.CurrentTownName -> TextPart.CurrentTownName
            CompiledRuntimeTextPart.TownTaxPercent -> TextPart.TownTaxPercent
            CompiledRuntimeTextPart.TownTaxAmount -> TextPart.TownTaxAmount
            CompiledRuntimeTextPart.CreationMemberNumber -> TextPart.CreationMemberNumber
            CompiledRuntimeTextPart.CreationMemberName -> TextPart.CreationMemberName
            CompiledRuntimeTextPart.CreationWeaponSkill -> TextPart.CreationWeaponSkill
            CompiledRuntimeTextPart.CreationHealth -> TextPart.CreationHealth
            CompiledRuntimeTextPart.CreationWisdom -> TextPart.CreationWisdom
            CompiledRuntimeTextPart.CreationCharisma -> TextPart.CreationCharisma
        }

    private fun locationCoordinate(
        state: ExecutionState,
        slug: String,
    ): String? =
        state
            .runtime
            .locations
            .firstOrNull { it.location == LocationId(slug) }
            ?.point
            ?.let { "${it.x}x${it.y}" }

    private fun evalInt(
        state: ExecutionState,
        expr: CompiledIntExpr,
        location: VmLocation,
    ): EvalResult<Int> =
        when (expr) {
            is CompiledIntExpr.Literal -> EvalResult.Ok(expr.value)
            is CompiledIntExpr.Local -> EvalResult.Ok(state.locals[index.string(expr.nameString)] ?: 0)
            is CompiledIntExpr.Binary -> {
                val left = evalInt(state, expr.left, location)
                val right = evalInt(state, expr.right, location)
                left.zip(right).map { (a, b) ->
                    when (expr.op) {
                        CompiledBinaryOp.Add -> a + b
                        CompiledBinaryOp.Sub -> a - b
                        CompiledBinaryOp.Mul -> a * b
                        CompiledBinaryOp.Div -> if (b == 0) 0 else a / b
                        CompiledBinaryOp.Mod -> if (b == 0) 0 else a % b
                    }
                }
            }
            is CompiledIntExpr.If ->
                evalBool(state, expr.cond, location).flatMap { cond ->
                    evalInt(state, if (cond) expr.thenValue else expr.elseValue, location)
                }
            is CompiledIntExpr.Random -> {
                val lo = evalInt(state, expr.lo, location)
                val hi = evalInt(state, expr.hi, location)
                lo.zip(hi).flatMap { (loValue, hiValue) ->
                    if (loValue > hiValue) {
                        EvalResult.Failed(location.badOperand("RandomRange", "ascending range", "$loValue..$hiValue"))
                    } else {
                        val draw = state.runtime.drawRandom(loValue, hiValue)
                        state.runtime = draw.runtime
                        EvalResult.Ok(draw.value)
                    }
                }
            }
            is CompiledIntExpr.MemberField ->
                evalInt(state, expr.member, location).map { member ->
                    state.party.scriptMemberField(member, index.string(expr.fieldString))
                }
            is CompiledIntExpr.MonsterField ->
                evalInt(state, expr.monster, location).flatMap { monster ->
                    host.readMonsterInt(monster, index.string(expr.fieldString))?.let { EvalResult.Ok(it) }
                        ?: EvalResult.Failed(
                            location.unsupported(
                                "monster.${index.string(expr.fieldString)}",
                                "Host does not provide battle monster fields",
                            ),
                        )
                }
            is CompiledIntExpr.BattleFlag -> {
                val name = index.string(expr.nameString)
                host.readBattleInt(name)?.let { EvalResult.Ok(it) }
                    ?: EvalResult.Failed(location.unsupported(name, "Host does not provide battle integer flags"))
            }
            is CompiledIntExpr.BattleQuery ->
                host.readBattleQuery(index.string(expr.nameString))?.let { EvalResult.Ok(it) }
                    ?: EvalResult.Failed(location.unsupported(index.string(expr.nameString), "Host does not provide battle queries"))
            is CompiledIntExpr.RealmScalar -> EvalResult.Ok(state.runtime.scriptRealmScalar(index.string(expr.nameString), state.locals))
            is CompiledIntExpr.PartyScalar -> EvalResult.Ok(state.party.scriptPartyScalar(index.string(expr.nameString)))
            is CompiledIntExpr.InventoryPoolCount -> EvalResult.Ok(state.party.countInventoryItems(index.itemPool(expr.pool)))
            is CompiledIntExpr.InventorySlotCost ->
                evalInt(state, expr.slot, location).map { slot ->
                    state.party
                        .inventoryItem(slot)
                        ?.let(index::itemCost)
                        ?: 0
                }
            is CompiledIntExpr.NativeInt -> {
                val path = index.string(expr.pathString)
                val args = expr.argStrings.map(index::string)
                validateNativeService(
                    path = path,
                    expectedKind = NativeServiceKind.IntValue,
                    actualSlots = List(args.size) { NativeServiceArgSlot.StringValue },
                    location = location,
                ).map {
                    val request = EngineRequest(path, args)
                    val result = host.readInt(request)
                    host.trace(EngineTraceEvent.NativeInt(request, result))
                    result
                }
            }
        }

    private fun evalBool(
        state: ExecutionState,
        expr: CompiledBoolExpr,
        location: VmLocation,
    ): EvalResult<Boolean> =
        when (expr) {
            is CompiledBoolExpr.Cmp -> {
                val left = evalInt(state, expr.left, location)
                val right = evalInt(state, expr.right, location)
                left.zip(right).map { (a, b) ->
                    when (expr.op) {
                        CompiledComparisonOp.Eq -> a == b
                        CompiledComparisonOp.Ne -> a != b
                        CompiledComparisonOp.Lt -> a < b
                        CompiledComparisonOp.Lte -> a <= b
                        CompiledComparisonOp.Gt -> a > b
                        CompiledComparisonOp.Gte -> a >= b
                    }
                }
            }
            is CompiledBoolExpr.Not -> evalBool(state, expr.expr, location).map(Boolean::not)
            is CompiledBoolExpr.And ->
                evalBool(state, expr.left, location).flatMap { left ->
                    if (!left) EvalResult.Ok(false) else evalBool(state, expr.right, location)
                }
            is CompiledBoolExpr.Or ->
                evalBool(state, expr.left, location).flatMap { left ->
                    if (left) EvalResult.Ok(true) else evalBool(state, expr.right, location)
                }
            is CompiledBoolExpr.FlagIs ->
                EvalResult.Ok(state.party.flags.any { it.flag.slug == index.slug(expr.flag) && it.native == index.flagNative(expr.state) })
            is CompiledBoolExpr.MilestoneAchieved -> EvalResult.Ok(GameMilestoneId(index.slug(expr.milestone)) in state.party.milestones)
            is CompiledBoolExpr.MemberFlag ->
                evalInt(state, expr.member, location).map { member ->
                    state.party.scriptMemberFlag(member, index.string(expr.nameString))
                }
            is CompiledBoolExpr.MonsterFlag ->
                evalInt(state, expr.monster, location).flatMap { monster ->
                    host.readMonsterFlag(monster, index.string(expr.nameString))?.let { EvalResult.Ok(it) }
                        ?: EvalResult.Failed(
                            location.unsupported("monster.${index.string(expr.nameString)}", "Host does not provide battle monster flags"),
                        )
                }
            is CompiledBoolExpr.BattleFlag -> {
                val name = index.string(expr.nameString)
                host.readBattleFlag(name)?.let { EvalResult.Ok(it) }
                    ?: EvalResult.Failed(location.unsupported(name, "Host does not provide battle boolean flags"))
            }
            is CompiledBoolExpr.RuntimeFlag -> EvalResult.Ok(state.runtime.scriptRuntimeFlag(index.string(expr.nameString)))
        }

    private fun evalString(
        state: ExecutionState,
        expr: CompiledStringExpr,
        location: VmLocation,
    ): EvalResult<String> =
        when (expr) {
            is CompiledStringExpr.NativeString -> {
                val path = index.string(expr.pathString)
                val actualSlots = expr.args.map(::nativeStringArgSlot)
                when (val validation = validateNativeService(path, NativeServiceKind.StringValue, actualSlots, location)) {
                    is EvalResult.Failed -> return validation
                    is EvalResult.Ok -> Unit
                }
                val args = expr.args.map { arg -> stringArg(state, arg, location) }
                val failure = args.firstNotNullOfOrNull { (it as? EvalResult.Failed)?.diagnostic }
                if (failure != null) {
                    EvalResult.Failed(failure)
                } else {
                    val renderedArgs = args.map { (it as EvalResult.Ok<String>).value }
                    val builtin = builtinString(state, path, renderedArgs)
                    if (builtin != null) {
                        EvalResult.Ok(builtin)
                    } else {
                        val request = EngineRequest(path, renderedArgs)
                        val result = host.readString(request)
                        host.trace(EngineTraceEvent.NativeString(request, result))
                        EvalResult.Ok(result)
                    }
                }
            }
        }

    private fun nativeStringArgSlot(arg: CompiledNativeStringArg): NativeServiceArgSlot =
        when (arg) {
            is CompiledNativeStringArg.Literal -> NativeServiceArgSlot.StringValue
            is CompiledNativeStringArg.IntValue -> NativeServiceArgSlot.IntExprValue
        }

    private fun validateNativeService(
        path: String,
        expectedKind: NativeServiceKind,
        actualSlots: List<NativeServiceArgSlot>,
        location: VmLocation,
    ): EvalResult<Unit> {
        val descriptor =
            nativeServices.descriptor(path)
                ?: return EvalResult.Failed(
                    VmDiagnostic.MissingNativeOp(
                        root = location.root,
                        instructionIndex = location.instructionIndex,
                        operandPath = location.operandPath,
                        path = path,
                        reason = "UnknownNativeService",
                    ),
                )
        if (descriptor.kind != expectedKind) {
            return EvalResult.Failed(
                location.unsupported(path, "Native expression requires $expectedKind, got ${descriptor.kind}"),
            )
        }
        if (descriptor.argSlots != actualSlots) {
            return EvalResult.Failed(
                location.badOperand(
                    reason = "NativeServiceWrongShape",
                    expected = descriptor.argSlots.toString(),
                    actual = actualSlots.toString(),
                ),
            )
        }
        return EvalResult.Ok(Unit)
    }

    private fun stringArg(
        state: ExecutionState,
        arg: CompiledNativeStringArg,
        location: VmLocation,
    ): EvalResult<String> =
        when (arg) {
            is CompiledNativeStringArg.Literal -> EvalResult.Ok(index.string(arg.string))
            is CompiledNativeStringArg.IntValue -> evalInt(state, arg.value, location).map(Int::toString)
        }

    private fun builtinString(
        state: ExecutionState,
        path: String,
        args: List<String>,
    ): String? =
        when (path) {
            NativeAllowlist.partyMemberName.path ->
                state.party.members
                    .getOrNull(args.singleOrNull()?.toIntOrNull().orZero() - 1)
                    ?.name
                    .orEmpty()
            NativeAllowlist.partyPronounSubject.path ->
                state.party.pronoun(
                    args.singleOrNull()?.toIntOrNull().orZero(),
                    PronounCase.Subject,
                )
            NativeAllowlist.partyPronounObject.path -> state.party.pronoun(args.singleOrNull()?.toIntOrNull().orZero(), PronounCase.Object)
            NativeAllowlist.partyPronounPossessive.path ->
                state.party.pronoun(
                    args.singleOrNull()?.toIntOrNull().orZero(),
                    PronounCase.Possessive,
                )
            NativeAllowlist.townNpcName.path,
            NativeAllowlist.npcRoleName.path,
            NativeAllowlist.npcRoleLocation.path,
            -> null
            else -> null
        }

    private fun PartyState.pronoun(
        memberNumber: Int,
        case: PronounCase,
    ): String {
        val gender = members.getOrNull(memberNumber - 1)?.gender ?: Gender.Male
        return gender.pronoun(case)
    }

    private fun readLValue(
        state: ExecutionState,
        target: CompiledLValue,
        location: VmLocation,
    ): EvalResult<Int> =
        when (target) {
            is CompiledLValue.RealmScalar -> EvalResult.Ok(state.runtime.scriptRealmScalar(index.string(target.nameString), state.locals))
            is CompiledLValue.PartyScalar -> EvalResult.Ok(state.party.scriptPartyScalar(index.string(target.nameString)))
            is CompiledLValue.MemberField ->
                evalInt(state, target.member, location).map { member ->
                    state.party.scriptMemberField(member, index.string(target.fieldString))
                }
            is CompiledLValue.MonsterField ->
                evalInt(state, target.monster, location).flatMap { monster ->
                    host.readMonsterInt(monster, index.string(target.fieldString))?.let { EvalResult.Ok(it) }
                        ?: EvalResult.Failed(
                            location.invalidLValue(
                                "monster.${index.string(target.fieldString)}",
                                "Host does not provide battle monster fields",
                            ),
                        )
                }
            is CompiledLValue.BattleFlag -> {
                val name = index.string(target.nameString)
                host.readBattleInt(name)?.let { EvalResult.Ok(it) }
                    ?: EvalResult.Failed(location.invalidLValue(name, "Host does not provide battle integer flags"))
            }
        }

    private fun writeLValue(
        state: ExecutionState,
        target: CompiledLValue,
        value: EvalResult<Int>,
        location: VmLocation,
        readCurrent: Boolean = true,
        transform: (Int, Int) -> Int,
    ): ControlSignal =
        value.fold(
            ok = { incoming ->
                val current =
                    if (readCurrent) {
                        when (val read = readLValue(state, target, location)) {
                            is EvalResult.Failed -> return ControlSignal.Failed(read.diagnostic)
                            is EvalResult.Ok -> read.value
                        }
                    } else {
                        0
                    }
                val next = transform(current, incoming)
                when (target) {
                    is CompiledLValue.RealmScalar -> {
                        val name = index.string(target.nameString)
                        state.runtime = state.runtime.withScriptRealmScalar(name, next)
                            ?: state.runtime.also { state.locals[name] = next }
                        ControlSignal.Continue
                    }
                    is CompiledLValue.PartyScalar -> {
                        state.party = state.party.withScriptPartyScalar(index.string(target.nameString), next)
                        ControlSignal.Continue
                    }
                    is CompiledLValue.MemberField -> {
                        evalInt(state, target.member, location).fold(
                            ok = { member ->
                                state.party = state.party.withScriptMemberField(member, index.string(target.fieldString), next)
                                ControlSignal.Continue
                            },
                            failed = { ControlSignal.Failed(it) },
                        )
                    }
                    is CompiledLValue.MonsterField ->
                        evalInt(state, target.monster, location).fold(
                            ok = { monster ->
                                if (host.writeMonsterInt(monster, index.string(target.fieldString), next)) {
                                    ControlSignal.Continue
                                } else {
                                    ControlSignal.Failed(
                                        location.invalidLValue(
                                            "monster.${index.string(target.fieldString)}",
                                            "Host does not provide writable battle monster fields",
                                        ),
                                    )
                                }
                            },
                            failed = { ControlSignal.Failed(it) },
                        )
                    is CompiledLValue.BattleFlag -> {
                        val name = index.string(target.nameString)
                        if (host.writeBattleInt(name, next)) {
                            ControlSignal.Continue
                        } else {
                            ControlSignal.Failed(location.invalidLValue(name, "Host does not provide writable battle integer flags"))
                        }
                    }
                }
            },
            failed = { ControlSignal.Failed(it) },
        )

    private fun viewCommand(command: CompiledViewCommand): ViewCommand =
        when (command) {
            CompiledViewCommand.ClearText -> ViewCommand.ClearText
            CompiledViewCommand.ClearMapWindow -> ViewCommand.ClearMapWindow
            CompiledViewCommand.RenderMap -> ViewCommand.RenderMap
            CompiledViewCommand.StatusLine -> ViewCommand.StatusLine
            is CompiledViewCommand.ShowImage -> ViewCommand.ShowImage(ImageId(index.slug(command.image)))
        }

    private fun CompiledInstruction.integer(index: Int): Int = (operands[index] as CompiledOperand.Integer).value

    private fun CompiledInstruction.boolean(index: Int): Boolean = (operands[index] as CompiledOperand.BooleanValue).value

    private fun CompiledInstruction.character(index: Int): Char = (operands[index] as CompiledOperand.Character).value

    private fun CompiledInstruction.stringId(index: Int): Int = (operands[index] as CompiledOperand.StringId).id

    private fun CompiledInstruction.optionalString(index: Int): String? =
        (operands.getOrNull(index) as? CompiledOperand.StringId)?.id?.let(this@CompiledVm.index::string)

    private fun CompiledInstruction.optionalSignal(index: Int): ScriptControlSignal? =
        (operands.getOrNull(index) as? CompiledOperand.EnumName)?.name?.let(ScriptControlSignal::valueOf)

    private fun CompiledInstruction.memberChoiceFilter(index: Int): MemberChoiceFilter =
        MemberChoiceFilter.valueOf((operands[index] as CompiledOperand.EnumName).name)

    private fun CompiledInstruction.inventorySlotChoiceFilter(index: Int): InventorySlotChoiceFilter =
        InventorySlotChoiceFilter.valueOf((operands[index] as CompiledOperand.EnumName).name)

    private fun CompiledInstruction.handle(index: Int): CompiledHandleRef = (operands[index] as CompiledOperand.Handle).ref

    private fun CompiledInstruction.textBlock(index: Int): CompiledTextBlock = (operands[index] as CompiledOperand.TextBlockValue).value

    private fun CompiledInstruction.intExpr(index: Int): CompiledIntExpr = (operands[index] as CompiledOperand.IntExprValue).value

    private fun CompiledInstruction.boolExpr(index: Int): CompiledBoolExpr = (operands[index] as CompiledOperand.BoolExprValue).value

    private fun CompiledInstruction.stringExpr(index: Int): CompiledStringExpr = (operands[index] as CompiledOperand.StringExprValue).value

    private fun CompiledInstruction.lvalue(index: Int): CompiledLValue = (operands[index] as CompiledOperand.LValueValue).value

    private fun CompiledInstruction.view(index: Int): CompiledViewCommand = (operands[index] as CompiledOperand.ViewCommandValue).value

    private fun CompiledInstruction.instructions(index: Int): List<CompiledInstruction> =
        (operands[index] as CompiledOperand.Instructions).instructions

    private fun CompiledInstruction.switchCases(index: Int): List<CompiledSwitchCase> =
        (operands[index] as CompiledOperand.SwitchCases).cases

    private fun CompiledInstruction.menuOptions(index: Int): List<CompiledMenuOption> =
        (operands[index] as CompiledOperand.MenuOptions).options

    private fun CompiledInstruction.inputBindings(index: Int): List<InputBindingModel> =
        (operands[index] as CompiledOperand.InputBindings).bindings.map(::inputBinding)

    private fun CompiledInstruction.textAnswer(index: Int): CompiledTextAnswer = (operands[index] as CompiledOperand.TextAnswer).answer

    private fun inputBinding(binding: CompiledInputBinding): InputBindingModel =
        InputBindingModel(
            primary = index.string(binding.primaryString),
            aliases = binding.aliasStrings.map(index::string),
            label = index.string(binding.labelString),
        )

    private fun menuOptions(
        instruction: CompiledInstruction,
        location: VmLocation,
    ): EvalResult<List<CompiledVmMenuOption>> {
        val options = mutableListOf<CompiledVmMenuOption>()
        instruction.menuOptions(1).forEachIndexed { optionIndex, option ->
            val optionLocation = location.child(optionIndex)
            val primary = index.string(option.binding.primaryString)
            val key =
                primary.singleOrNull()?.uppercaseChar()
                    ?: return EvalResult.Failed(
                        optionLocation.badOperand(
                            reason = "InputBindingPrimary",
                            expected = "single character",
                            actual = primary,
                        ),
                    )
            options += CompiledVmMenuOption(key, optionIndex)
        }
        return EvalResult.Ok(options)
    }

    private data class IterationFrameContext(
        val instruction: CompiledInstruction,
        val location: VmLocation,
        val cursor: Int,
    )

    private fun iterationFrameContext(
        state: ExecutionState,
        rootInstructions: List<CompiledInstruction>,
        frame: CompiledVmFrame,
        expectedOpcode: CompiledOpcode,
        expectedOperand: Int,
    ): IterationFrameContext? {
        if (frame.blockPath.size < ITERATION_FRAME_SEGMENTS) {
            return null
        }
        val parentBlockPath = frame.blockPath.dropLast(ITERATION_FRAME_SEGMENTS)
        val instructionIndex = frame.blockPath[frame.blockPath.size - 3]
        val operandIndex = frame.blockPath[frame.blockPath.size - 2]
        val cursor = frame.blockPath[frame.blockPath.size - 1]
        if (operandIndex != expectedOperand || cursor < 0) {
            return null
        }
        val parentInstructions = instructionsFor(rootInstructions, parentBlockPath) ?: return null
        val instruction = parentInstructions.getOrNull(instructionIndex) ?: return null
        if (instruction.opcode != expectedOpcode) {
            return null
        }
        return IterationFrameContext(
            instruction = instruction,
            location =
                VmLocation(
                    root = state.root,
                    instructionIndex = instructionIndex,
                    blockPath = parentBlockPath,
                    operandPath = parentBlockPath + instructionIndex,
                    opcode = instruction.opcode,
                ),
            cursor = cursor,
        )
    }

    private fun instructionsFor(
        rootInstructions: List<CompiledInstruction>,
        blockPath: List<Int>,
    ): List<CompiledInstruction>? {
        var instructions = rootInstructions
        var cursor = 0
        while (cursor < blockPath.size) {
            val instructionIndex = blockPath.getOrNull(cursor++) ?: return null
            val instruction = instructions.getOrNull(instructionIndex) ?: return null
            val operandIndex = blockPath.getOrNull(cursor++) ?: return null
            val operand = instruction.operands.getOrNull(operandIndex) ?: return null
            instructions =
                when (operand) {
                    is CompiledOperand.Instructions -> {
                        if (instruction.opcode == CompiledOpcode.Loop || instruction.opcode == CompiledOpcode.ForEachMember) {
                            cursor += 1
                        }
                        operand.instructions
                    }
                    is CompiledOperand.SwitchCases -> {
                        val caseIndex = blockPath.getOrNull(cursor++) ?: return null
                        operand.cases.getOrNull(caseIndex)?.body ?: return null
                    }
                    is CompiledOperand.MenuOptions -> {
                        val optionIndex = blockPath.getOrNull(cursor++) ?: return null
                        operand.options.getOrNull(optionIndex)?.body ?: return null
                    }
                    is CompiledOperand.TextAnswer -> {
                        val answerBranch = blockPath.getOrNull(cursor++) ?: return null
                        when (answerBranch) {
                            0 -> operand.answer.matchOps
                            1 -> operand.answer.missOps
                            else -> return null
                        }
                    }
                    else -> return null
                }
        }
        return instructions
    }

    private companion object {
        const val MAX_LOOP_ITERATIONS: Int = 128
        const val TEXT_ANSWER_MATCH_BRANCH: Int = 0
        const val TEXT_ANSWER_MISS_BRANCH: Int = 1
        const val ITERATION_FRAME_SEGMENTS: Int = 3
    }
}

private fun continuationFrame(
    blockPath: List<Int>,
    nextIndex: Int,
    insideLoop: Boolean,
    kind: CompiledVmFrameKind,
    enclosingInsideLoop: Boolean,
): CompiledVmFrame =
    when (kind) {
        CompiledVmFrameKind.Block ->
            CompiledVmFrame(
                blockPath = blockPath,
                nextIndex = nextIndex,
                insideLoop = insideLoop,
                kind = kind,
                enclosingInsideLoop = enclosingInsideLoop,
            )
        CompiledVmFrameKind.LoopBody ->
            CompiledVmFrame(
                blockPath = blockPath,
                nextIndex = nextIndex,
                insideLoop = true,
                kind = kind,
                enclosingInsideLoop = enclosingInsideLoop,
            )
        CompiledVmFrameKind.ForEachMemberBody ->
            CompiledVmFrame(
                blockPath = blockPath,
                nextIndex = nextIndex,
                insideLoop = insideLoop,
                kind = kind,
                enclosingInsideLoop = enclosingInsideLoop,
            )
    }

private fun List<CompiledVmFrame>.afterConsumedLoopSignal(): List<CompiledVmFrame>? {
    val loopFrame = indexOfFirst { frame -> frame.kind == CompiledVmFrameKind.LoopBody }
    return if (loopFrame < 0) null else drop(loopFrame + 1)
}

private fun CompiledVmFrame.badContinuation(
    state: ExecutionState,
    reason: String,
): VmDiagnostic =
    VmDiagnostic.BadContinuationFrame(
        root = state.root,
        instructionIndex = nextIndex,
        operandPath = blockPath,
        frame = toString(),
        reason = reason,
    )

private data class ExecutionState(
    val root: rylween.runtime.ScriptRootRef,
    var runtime: RuntimeSessionState,
    val locals: MutableMap<String, Int>,
    val textBindings: MutableMap<String, String>,
) {
    var party: PartyState = runtime.activeParty
        set(value) {
            field = value
            runtime = runtime.withActiveParty(value)
        }
}

private class CompiledVmContentIndex(
    private val content: LoadedCompiledContent,
) {
    private val flagNatives: Map<CompiledHandleRef, String> =
        content.raw
            .flags
            .flatMap { flag -> flag.states.map { state -> state.id to string(state.nativeString) } }
            .toMap()

    private val itemCosts: Map<String, Int> =
        content.raw
            .items
            .associate { item -> slug(item.id) to item.cost }

    private val itemPools: Map<CompiledHandleRef, ItemPoolId> =
        content.raw
            .itemPools
            .associate { pool -> pool.id to ItemPoolId.authored(slug(pool.id), pool.items.map { ItemId.authored(slug(it)) }) }

    fun string(id: Int): String = content.string(id)

    fun slug(ref: CompiledHandleRef): String = string(content.handle(ref).slugString)

    fun flagNative(ref: CompiledHandleRef): String = flagNatives[ref] ?: slug(ref).substringAfterLast('.')

    fun itemPool(ref: CompiledHandleRef): ItemPoolId = itemPools[ref] ?: ItemPoolId.authored(slug(ref), emptyList())

    fun itemCost(item: ItemId): Int = itemCosts[item.slug] ?: 0
}

private data class VmLocation(
    val root: rylween.runtime.ScriptRootRef,
    val instructionIndex: Int,
    val blockPath: List<Int>,
    val operandPath: List<Int>,
    val opcode: CompiledOpcode,
) {
    fun child(segment: Int): VmLocation = copy(operandPath = operandPath + segment)

    fun operandBranch(
        operandIndex: Int,
        branchIndex: Int? = null,
    ): List<Int> = operandPath + listOfNotNull(operandIndex, branchIndex)

    fun unsupported(
        service: String,
        reason: String,
    ): VmDiagnostic =
        VmDiagnostic.UnsupportedHostService(
            root = root,
            instructionIndex = instructionIndex,
            operandPath = operandPath,
            service = service,
            reason = reason,
        )

    fun invalidLValue(
        lvalue: String,
        reason: String,
    ): VmDiagnostic =
        VmDiagnostic.InvalidLValue(
            root = root,
            instructionIndex = instructionIndex,
            operandPath = operandPath,
            lvalue = lvalue,
            reason = reason,
        )

    fun badOperand(
        reason: String,
        expected: String,
        actual: String,
    ): VmDiagnostic =
        VmDiagnostic.BadOperand(
            root = root,
            instructionIndex = instructionIndex,
            operandPath = operandPath,
            opcode = opcode,
            reason = reason,
            expected = expected,
            actual = actual,
        )
}

private sealed interface ControlSignal {
    data object Continue : ControlSignal

    data object BreakNearestLoop : ControlSignal

    data object StopScript : ControlSignal

    data class Failed(
        val diagnostic: VmDiagnostic,
    ) : ControlSignal

    data class Blocked(
        val pending: CompiledVmPendingInput,
        val frames: List<CompiledVmFrame>,
    ) : ControlSignal
}

private fun CompiledVmContinuation.accepts(input: CompiledVmInput): Boolean =
    when (pending) {
        is CompiledVmPendingInput.WaitInput -> input is CompiledVmInput.Continue
        is CompiledVmPendingInput.Menu -> input is CompiledVmInput.MenuKey
        is CompiledVmPendingInput.AskKey -> input is CompiledVmInput.AskKey
        is CompiledVmPendingInput.AskMember -> input is CompiledVmInput.AskMember
        is CompiledVmPendingInput.AskMonster -> input is CompiledVmInput.AskMonster
        is CompiledVmPendingInput.AskInventorySlot -> input is CompiledVmInput.AskInventorySlot
        is CompiledVmPendingInput.AskText -> input is CompiledVmInput.AskText
    }

private sealed interface EvalResult<out T> {
    data class Ok<T>(
        val value: T,
    ) : EvalResult<T>

    data class Failed(
        val diagnostic: VmDiagnostic,
    ) : EvalResult<Nothing>
}

private inline fun <T, R> EvalResult<T>.map(transform: (T) -> R): EvalResult<R> =
    when (this) {
        is EvalResult.Ok -> EvalResult.Ok(transform(value))
        is EvalResult.Failed -> this
    }

private inline fun <T, R> EvalResult<T>.flatMap(transform: (T) -> EvalResult<R>): EvalResult<R> =
    when (this) {
        is EvalResult.Ok -> transform(value)
        is EvalResult.Failed -> this
    }

private fun <A, B> EvalResult<A>.zip(other: EvalResult<B>): EvalResult<Pair<A, B>> =
    when (this) {
        is EvalResult.Failed -> this
        is EvalResult.Ok ->
            when (other) {
                is EvalResult.Failed -> other
                is EvalResult.Ok -> EvalResult.Ok(value to other.value)
            }
    }

private fun InputBindingModel.compiledVmPromptValue(): Int =
    primary.singleOrNull()?.let { char ->
        char.digitToIntOrNull() ?: char.uppercaseChar().code
    } ?: primary.uppercase().hashCode()

private fun PartyState.memberChoices(filter: MemberChoiceFilter): List<Int> =
    members
        .mapIndexedNotNull { index, member ->
            (index + 1).takeIf { member.matches(filter) }
        }

private fun PartyMemberState.matches(filter: MemberChoiceFilter): Boolean =
    when (filter) {
        MemberChoiceFilter.Any -> true
        MemberChoiceFilter.AliveOnly -> !dead
        MemberChoiceFilter.DeadOnly -> dead
        MemberChoiceFilter.SpellcasterOnly -> !dead && pcClass in setOf(PcClass.Mage, PcClass.Priest)
    }

private fun PartyState.inventorySlotChoices(
    filter: InventorySlotChoiceFilter,
    slots: IntRange,
    firstKey: Char,
): List<CompiledVmInventorySlotChoice> {
    val itemsBySlot = inventory.associate { slot -> slot.slot to slot.item }
    return slots
        .filter { slot ->
            slot in 1..InventorySaveSlotMax &&
                when (filter) {
                    InventorySlotChoiceFilter.Any -> true
                    InventorySlotChoiceFilter.NonEmpty -> itemsBySlot[slot] != null
                    InventorySlotChoiceFilter.Empty -> itemsBySlot[slot] == null
                }
        }.map { slot -> CompiledVmInventorySlotChoice(firstKey + (slot - slots.first), slot) }
}

private fun CompiledVmPendingInput.AskText.accepts(raw: String): Boolean {
    val input = raw.normalizedTextAnswer(trim, caseSensitive, maxLength)
    return accepted.any { value -> value.normalizedTextAnswer(trim, caseSensitive, maxLength = null) == input }
}

private fun String.normalizedTextAnswer(
    trim: Boolean,
    caseSensitive: Boolean,
    maxLength: Int?,
): String {
    val limited = maxLength?.let { take(it) } ?: this
    val spaced = if (trim) limited.trim() else limited
    return if (caseSensitive) spaced else spaced.lowercase()
}

private inline fun <T, R> EvalResult<T>.fold(
    ok: (T) -> R,
    failed: (VmDiagnostic) -> R,
): R =
    when (this) {
        is EvalResult.Ok -> ok(value)
        is EvalResult.Failed -> failed(diagnostic)
    }

private fun <T> EvalResult<T>.valueOrFallback(fallback: T): T =
    when (this) {
        is EvalResult.Ok -> value
        is EvalResult.Failed -> fallback
    }

private fun Int?.orZero(): Int = this ?: 0
