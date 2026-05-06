package rylween

import rylween.compiled.CompiledGameBundle
import rylween.compiled.CompiledInstruction
import rylween.compiled.CompiledOpcode
import rylween.compiled.CompiledOpcodeContract
import rylween.compiled.CompiledOperand
import rylween.compiled.loadCompiledGame
import rylween.compiled.toCompiledGameBundle
import rylween.game.RylweenCompiledGame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun loaderRejectsRealBundleInstructionWithWrongOperandShape() {
        val bundle = RylweenCompiledGame.nextRylween.toCompiledGameBundle()
        val rootIndex =
            bundle.content.scripts.roots.indexOfFirst { root ->
                root.instructions.any(::hasFirstOperandOtherThanHandleList)
            }
        check(rootIndex >= 0) { "expected a script root with a non-HandleList operand" }
        val root = bundle.content.scripts.roots[rootIndex]
        val instructionIndex = root.instructions.indexOfFirst(::hasFirstOperandOtherThanHandleList)
        val original = root.instructions[instructionIndex]
        val originalFirstSlot = CompiledOpcodeContract.slotOf(original.operands.first())
        val mutatedOperands = original.operands.toMutableList()
        mutatedOperands[0] = CompiledOperand.HandleList(emptyList())
        val mutatedBundle =
            bundle.withScriptInstruction(
                rootIndex = rootIndex,
                instructionIndex = instructionIndex,
                instruction = original.copy(operands = mutatedOperands),
            )

        val failure =
            assertFailsWith<IllegalArgumentException> {
                mutatedBundle.loadCompiledGame()
            }
        val message = failure.message.orEmpty()

        assertTrue("WrongShape" in message, message)
        assertTrue(original.opcode.name in message, message)
        assertTrue(originalFirstSlot.name in message, message)
        assertTrue("HandleList" in message, message)
        assertTrue(root.root.slug in message, message)
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

    private fun hasFirstOperandOtherThanHandleList(instruction: CompiledInstruction): Boolean =
        instruction.operands
            .firstOrNull()
            ?.let { operand ->
                CompiledOpcodeContract.slotOf(operand) != CompiledOpcodeContract.Slot.HandleList
            } == true

    private fun CompiledGameBundle.withScriptInstruction(
        rootIndex: Int,
        instructionIndex: Int,
        instruction: CompiledInstruction,
    ): CompiledGameBundle {
        val root = content.scripts.roots[rootIndex]
        val instructions = root.instructions.toMutableList()
        instructions[instructionIndex] = instruction
        val roots = content.scripts.roots.toMutableList()
        roots[rootIndex] = root.copy(instructions = instructions)
        return copy(content = content.copy(scripts = content.scripts.copy(roots = roots)))
    }
}
