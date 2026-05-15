/**
 * Declarative operand-schema contract for compiled script opcodes.
 *
 * The compiled bundle writer emits [CompiledInstruction] values,
 * [CompiledBundleLoader] enforces this contract when loading J1 artifacts, and
 * `runtime/vm/VmDiagnostic.kt` supplies typed diagnostics for future VM use.
 */
package rylween.compiled

/**
 * The single source of truth for what operand shape every [CompiledOpcode]
 * accepts.
 *
 * J2.0 ("contract freeze") needs an executable spec for opcode arity and
 * operand kinds before the compiled-VM kernel starts caring about
 * malformed input. Today the schema is implicit in the compiler at
 * the compiled bundle writer; one slip there silently produces a bundle
 * the executor can't reason about. This object lifts that schema out so
 * that:
 *
 *   - the test suite can assert every opcode is covered;
 *   - the test suite can walk a real bundle and refuse any instruction
 *     that doesn't match its schema;
 *   - the loader-side validator can call [validateInstruction] directly.
 *
 * Adding a new opcode without an entry here will fail [validateAll];
 * changing an opcode's operand shape without updating the entry will
 * fail [validateInstruction] on the first emitted instance.
 */
object CompiledOpcodeContract {
    /**
     * Identifies which subtype of [CompiledOperand] a slot expects. One
     * variant per [CompiledOperand] sealed-interface case so that the
     * mapping from runtime operand to expected slot is total and
     * unambiguous.
     */
    enum class Slot {
        Integer,
        BooleanValue,
        Character,
        StringId,
        StringIdList,
        Handle,
        HandleList,
        TextBlockValue,
        IntExprValue,
        BoolExprValue,
        StringExprValue,
        LValueValue,
        ViewCommandValue,
        Instructions,
        SwitchCases,
        MenuOptions,
        InputBindings,
        TextAnswer,
        Reward,
        RandomItemOffer,
        ShopService,
        EnumName,
    }

    /**
     * Allowed operand shapes for a single opcode. Most opcodes have one
     * fixed arity; a handful (WaitInput / Break / Stop / Encounter) have
     * a permitted shorter form when an optional operand is absent. The
     * actual operand list must match exactly one of [arities].
     */
    class Schema private constructor(
        val arities: List<List<Slot>>,
    ) {
        init {
            require(arities.isNotEmpty()) { "schema must list at least one arity" }
            require(arities.distinct().size == arities.size) { "schema has duplicate arities: $arities" }
        }

        companion object {
            /** Creates a schema with exactly one allowed operand shape. */
            fun of(vararg slots: Slot): Schema = Schema(listOf(slots.toList()))

            /** Creates a schema with multiple allowed operand shapes. */
            fun ofAny(vararg arities: List<Slot>): Schema = Schema(arities.toList())

            /** Creates a schema for opcodes that take no operands. */
            fun empty(): Schema = Schema(listOf(emptyList()))
        }
    }

    /**
     * Outcome of validating a single instruction. [Ok] is produced when
     * the operand list matches one of the schema's arities; the failure
     * variants carry enough context to point at the offending opcode.
     */
    sealed interface Validation {
        data object Ok : Validation

        data class UnknownOpcode(
            val opcode: CompiledOpcode,
        ) : Validation

        data class WrongShape(
            val opcode: CompiledOpcode,
            val expected: List<List<Slot>>,
            val actual: List<Slot>,
        ) : Validation
    }

    /** Operand schemas for every opcode emitted by the compiled bundle writer. */
    private val schemas: Map<CompiledOpcode, Schema> =
        mapOf(
            CompiledOpcode.Screen to Schema.of(Slot.TextBlockValue),
            CompiledOpcode.Emit to Schema.of(Slot.Handle),
            CompiledOpcode.WaitInput to
                Schema.ofAny(
                    emptyList(),
                    listOf(Slot.StringId),
                ),
            CompiledOpcode.Pause to Schema.of(Slot.Integer),
            CompiledOpcode.View to Schema.of(Slot.ViewCommandValue),
            CompiledOpcode.LetInt to Schema.of(Slot.StringId, Slot.IntExprValue),
            CompiledOpcode.BindText to Schema.of(Slot.StringId, Slot.StringExprValue),
            CompiledOpcode.Set to Schema.of(Slot.LValueValue, Slot.IntExprValue),
            CompiledOpcode.Add to Schema.of(Slot.LValueValue, Slot.IntExprValue),
            CompiledOpcode.Sub to Schema.of(Slot.LValueValue, Slot.IntExprValue),
            CompiledOpcode.AddClamped to Schema.of(Slot.LValueValue, Slot.IntExprValue, Slot.IntExprValue),
            CompiledOpcode.SetFlag to Schema.of(Slot.Handle, Slot.Handle),
            CompiledOpcode.AppendJournal to Schema.of(Slot.TextBlockValue),
            CompiledOpcode.If to Schema.of(Slot.BoolExprValue, Slot.Instructions, Slot.Instructions),
            CompiledOpcode.Loop to Schema.of(Slot.Instructions),
            CompiledOpcode.Break to
                Schema.ofAny(
                    listOf(Slot.EnumName),
                    listOf(Slot.StringId, Slot.EnumName),
                ),
            CompiledOpcode.Stop to
                Schema.ofAny(
                    listOf(Slot.EnumName),
                    listOf(Slot.StringId, Slot.EnumName),
                ),
            CompiledOpcode.Switch to Schema.of(Slot.IntExprValue, Slot.SwitchCases),
            CompiledOpcode.Menu to Schema.of(Slot.TextBlockValue, Slot.MenuOptions, Slot.Instructions),
            CompiledOpcode.AskKey to Schema.of(Slot.TextBlockValue, Slot.StringId, Slot.InputBindings),
            CompiledOpcode.AskMember to Schema.of(Slot.TextBlockValue, Slot.StringId, Slot.EnumName, Slot.EnumName),
            CompiledOpcode.AskInventorySlot to
                Schema.of(Slot.TextBlockValue, Slot.StringId, Slot.Character, Slot.Integer, Slot.Integer, Slot.EnumName),
            CompiledOpcode.AskText to Schema.of(Slot.TextBlockValue, Slot.TextAnswer),
            CompiledOpcode.AskMonster to Schema.of(Slot.TextBlockValue, Slot.StringId),
            CompiledOpcode.ForEachMember to Schema.of(Slot.StringId, Slot.BooleanValue, Slot.Instructions),
            CompiledOpcode.ForEachMonster to Schema.of(Slot.StringId, Slot.BooleanValue, Slot.Instructions),
            CompiledOpcode.Speak to Schema.of(Slot.Handle),
            CompiledOpcode.TellRumor to Schema.of(Slot.Handle),
            CompiledOpcode.RunScene to Schema.of(Slot.Handle, Slot.StringIdList),
            CompiledOpcode.Encounter to
                Schema.ofAny(
                    listOf(Slot.Handle, Slot.BooleanValue),
                    listOf(Slot.Handle, Slot.BooleanValue, Slot.Handle),
                ),
            CompiledOpcode.EnterDungeon to Schema.of(Slot.Handle, Slot.Integer),
            CompiledOpcode.ConsumeActiveItem to Schema.empty(),
            CompiledOpcode.ClearInventorySlot to Schema.of(Slot.IntExprValue),
            CompiledOpcode.ConsumeInventoryPool to Schema.of(Slot.Handle, Slot.IntExprValue),
            CompiledOpcode.TeleportParty to Schema.of(Slot.IntExprValue, Slot.IntExprValue),
            CompiledOpcode.DiscoverCave to Schema.of(Slot.Handle, Slot.IntExprValue, Slot.IntExprValue),
            CompiledOpcode.GrantReward to Schema.of(Slot.Reward),
            CompiledOpcode.RandomItemOffer to Schema.of(Slot.RandomItemOffer),
            CompiledOpcode.ShopService to Schema.of(Slot.ShopService),
            CompiledOpcode.AchieveMilestone to Schema.of(Slot.Handle),
        )

    /** Maps an actual compiled operand instance to its schema slot kind. */
    fun slotOf(operand: CompiledOperand): Slot =
        when (operand) {
            is CompiledOperand.Integer -> Slot.Integer
            is CompiledOperand.BooleanValue -> Slot.BooleanValue
            is CompiledOperand.Character -> Slot.Character
            is CompiledOperand.StringId -> Slot.StringId
            is CompiledOperand.StringIdList -> Slot.StringIdList
            is CompiledOperand.Handle -> Slot.Handle
            is CompiledOperand.HandleList -> Slot.HandleList
            is CompiledOperand.TextBlockValue -> Slot.TextBlockValue
            is CompiledOperand.IntExprValue -> Slot.IntExprValue
            is CompiledOperand.BoolExprValue -> Slot.BoolExprValue
            is CompiledOperand.StringExprValue -> Slot.StringExprValue
            is CompiledOperand.LValueValue -> Slot.LValueValue
            is CompiledOperand.ViewCommandValue -> Slot.ViewCommandValue
            is CompiledOperand.Instructions -> Slot.Instructions
            is CompiledOperand.SwitchCases -> Slot.SwitchCases
            is CompiledOperand.MenuOptions -> Slot.MenuOptions
            is CompiledOperand.InputBindings -> Slot.InputBindings
            is CompiledOperand.TextAnswer -> Slot.TextAnswer
            is CompiledOperand.Reward -> Slot.Reward
            is CompiledOperand.RandomItemOffer -> Slot.RandomItemOffer
            is CompiledOperand.ShopService -> Slot.ShopService
            is CompiledOperand.EnumName -> Slot.EnumName
        }

    /** Validates a single [instruction] against the schema for its opcode. */
    fun validateInstruction(instruction: CompiledInstruction): Validation {
        val schema = schemas[instruction.opcode] ?: return Validation.UnknownOpcode(instruction.opcode)
        val actual = instruction.operands.map(::slotOf)
        return if (actual in schema.arities) {
            Validation.Ok
        } else {
            Validation.WrongShape(instruction.opcode, schema.arities, actual)
        }
    }

    /** Returns opcodes that do not yet have a declared schema entry. */
    fun validateAll(): List<CompiledOpcode> = CompiledOpcode.entries.filter { it !in schemas }
}
