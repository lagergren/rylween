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
import rylween.compiled.CompiledInstruction
import rylween.compiled.CompiledIntExpr
import rylween.compiled.CompiledLValue
import rylween.compiled.CompiledNativeStringArg
import rylween.compiled.CompiledOpcode
import rylween.compiled.CompiledOperand
import rylween.compiled.CompiledRuntimeTextPart
import rylween.compiled.CompiledScriptProgram
import rylween.compiled.CompiledStringExpr
import rylween.compiled.CompiledSwitchCase
import rylween.compiled.CompiledTextBlock
import rylween.compiled.CompiledTextPart
import rylween.compiled.CompiledViewCommand
import rylween.compiled.LoadedCompiledContent
import rylween.dsl.GameMilestoneId
import rylween.dsl.Gender
import rylween.dsl.ImageId
import rylween.dsl.ItemId
import rylween.dsl.ItemPoolId
import rylween.dsl.LocationId
import rylween.dsl.NativeAllowlist
import rylween.dsl.PartyState
import rylween.dsl.PronounCase
import rylween.dsl.ScriptControlSignal
import rylween.dsl.TextBlock
import rylween.dsl.TextId
import rylween.dsl.TextPart
import rylween.dsl.TextPattern
import rylween.dsl.ViewCommand
import rylween.dsl.fallbackText
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
}

/**
 * Executes validated compiled script programs for J2 non-interactive opcodes and expressions.
 *
 * [content] must come from [rylween.compiled.CompiledBundleLoader] so handle,
 * string, opcode-shape, and native-service checks have already run. This kernel
 * intentionally rejects interactive and child-session opcodes until later J2.1
 * continuation work gives them precise suspension semantics.
 */
class CompiledVm(
    private val content: LoadedCompiledContent,
    private val host: CompiledVmHost = RecordingCompiledVmHost(),
    private val nativeServices: NativeServiceCatalog = NativeServiceCatalog.default,
) {
    private val index = CompiledVmContentIndex(content)

    /** Runs [program] from [start] and returns either final VM state or a diagnostic. */
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
        return when (val signal = executeBlock(state, program.instructions, emptyList(), insideLoop = false)) {
            ControlSignal.Continue,
            ControlSignal.BreakNearestLoop,
            ControlSignal.StopScript,
            -> CompiledVmResult.Completed(state.runtime, state.locals.toMap(), state.textBindings.toMap())
            is ControlSignal.Failed -> CompiledVmResult.Failed(signal.diagnostic)
        }
    }

    private fun executeBlock(
        state: ExecutionState,
        instructions: List<CompiledInstruction>,
        path: List<Int>,
        insideLoop: Boolean,
    ): ControlSignal {
        instructions.forEachIndexed { instructionIndex, instruction ->
            val location = VmLocation(state.root, instructionIndex, path + instructionIndex, instruction.opcode)
            when (val signal = executeInstruction(state, instruction, location, insideLoop)) {
                ControlSignal.Continue -> Unit
                ControlSignal.BreakNearestLoop,
                ControlSignal.StopScript,
                is ControlSignal.Failed,
                -> return signal
            }
        }
        return ControlSignal.Continue
    }

    private fun executeInstruction(
        state: ExecutionState,
        instruction: CompiledInstruction,
        location: VmLocation,
        insideLoop: Boolean,
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
            CompiledOpcode.View -> {
                host.trace(EngineTraceEvent.View(viewCommand(instruction.view(0))))
                ControlSignal.Continue
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
                        executeBlock(state, branch, location.operandBranch(if (value) 1 else 2), insideLoop)
                    },
                    failed = { ControlSignal.Failed(it) },
                )
            }
            CompiledOpcode.Loop -> executeLoop(state, instruction.instructions(0), location, insideLoop)
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
                            executeBlock(state, cases[caseIndex].body, location.operandBranch(1, caseIndex), insideLoop)
                        }
                    },
                    failed = { ControlSignal.Failed(it) },
                )
            }
            CompiledOpcode.ForEachMember -> {
                val name = index.string(instruction.stringId(0))
                val includeDead = instruction.boolean(1)
                val body = instruction.instructions(2)
                state.party.members.forEachIndexed { index, member ->
                    if (includeDead || !member.dead) {
                        state.locals[name] = index + 1
                        when (val signal = executeBlock(state, body, location.operandBranch(2, index), insideLoop)) {
                            ControlSignal.Continue -> Unit
                            else -> return signal
                        }
                    }
                }
                ControlSignal.Continue
            }
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
    ): ControlSignal {
        repeat(MAX_LOOP_ITERATIONS) {
            when (val signal = executeBlock(state, body, location.operandBranch(0, it), insideLoop = true)) {
                ControlSignal.Continue -> Unit
                ControlSignal.BreakNearestLoop,
                ControlSignal.StopScript,
                -> return ControlSignal.Continue
                is ControlSignal.Failed -> return signal
            }
        }
        return ControlSignal.Continue
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

    private fun CompiledInstruction.stringId(index: Int): Int = (operands[index] as CompiledOperand.StringId).id

    private fun CompiledInstruction.optionalString(index: Int): String? =
        (operands.getOrNull(index) as? CompiledOperand.StringId)?.id?.let(this@CompiledVm.index::string)

    private fun CompiledInstruction.optionalSignal(index: Int): ScriptControlSignal? =
        (operands.getOrNull(index) as? CompiledOperand.EnumName)?.name?.let(ScriptControlSignal::valueOf)

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

    private companion object {
        const val MAX_LOOP_ITERATIONS: Int = 128
    }
}

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
