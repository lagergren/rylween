package rylween

import rylween.compiled.CompiledInstruction
import rylween.compiled.CompiledOpcode
import rylween.compiled.CompiledOpcodeContract
import rylween.compiled.CompiledOperand
import rylween.compiled.toCompiledGameBundle
import rylween.game.RylweenCompiledGame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * J2.0 contract test. Pins down [CompiledOpcodeContract] against the
 * actual emitted bundle, so a mismatch between the schema map and the
 * compiler in `CompiledBundle.kt` surfaces as a test failure rather
 * than a runtime VM mystery.
 */
class RylweenOpcodeContractTest {
    @Test
    fun everyOpcodeHasASchemaEntry() {
        val missing = CompiledOpcodeContract.validateAll()
        assertTrue(
            missing.isEmpty(),
            "CompiledOpcodeContract.schemas is missing entries for: $missing. " +
                "Add schemas for new opcodes before they ship in a bundle.",
        )
    }

    @Test
    fun emittedBundleInstructionsMatchTheirSchema() {
        val bundle = RylweenCompiledGame.nextRylween.toCompiledGameBundle()
        val violations = mutableListOf<String>()
        bundle.content.scripts.roots.forEach { root ->
            walk(root.instructions, root.rootHandle.toString(), violations)
        }
        assertTrue(
            violations.isEmpty(),
            "compiled bundle has ${violations.size} contract violation(s):\n" + violations.joinToString("\n"),
        )
    }

    @Test
    fun slotOfCoversEveryOperandSubtype() {
        // Smoke check: invoking slotOf on each operand variant must not
        // fall through. This catches a future CompiledOperand subtype
        // whose slot mapping wasn't added.
        val samples =
            listOf<CompiledOperand>(
                CompiledOperand.Integer(0),
                CompiledOperand.BooleanValue(false),
                CompiledOperand.Character(' '),
                CompiledOperand.StringId(0),
                CompiledOperand.StringIdList(emptyList()),
                CompiledOperand.EnumName(""),
            )
        samples.forEach { CompiledOpcodeContract.slotOf(it) }
        // The exhaustive `when` inside slotOf is the actual check; we
        // just need to exercise it. If the file compiles with a missing
        // branch the Kotlin compiler will already have caught it.
        assertEquals(samples.size, samples.map(CompiledOpcodeContract::slotOf).distinct().size)
    }

    private fun walk(
        instructions: List<CompiledInstruction>,
        path: String,
        violations: MutableList<String>,
    ) {
        instructions.forEachIndexed { index, instruction ->
            val here = "$path[$index] ${instruction.opcode}"
            when (val result = CompiledOpcodeContract.validateInstruction(instruction)) {
                CompiledOpcodeContract.Validation.Ok -> Unit
                is CompiledOpcodeContract.Validation.UnknownOpcode ->
                    violations += "$here: unknown opcode (no schema entry)"
                is CompiledOpcodeContract.Validation.WrongShape ->
                    violations +=
                        "$here: actual ${result.actual} does not match any of ${result.expected}"
            }
            instruction.operands.forEach { operand ->
                when (operand) {
                    is CompiledOperand.Instructions -> walk(operand.instructions, "$here.body", violations)
                    is CompiledOperand.SwitchCases ->
                        operand.cases.forEachIndexed { caseIndex, case ->
                            walk(case.body, "$here.case[$caseIndex]", violations)
                        }
                    is CompiledOperand.MenuOptions ->
                        operand.options.forEachIndexed { optionIndex, option ->
                            walk(option.body, "$here.option[$optionIndex]", violations)
                        }
                    is CompiledOperand.TextAnswer -> {
                        walk(operand.answer.matchOps, "$here.match", violations)
                        walk(operand.answer.missOps, "$here.miss", violations)
                    }
                    else -> Unit
                }
            }
        }
    }

    /** Sanity: the static enum is shaped the way we expect. */
    @Test
    fun opcodeEnumIsNonEmpty() {
        assertTrue(CompiledOpcode.entries.isNotEmpty())
    }
}
