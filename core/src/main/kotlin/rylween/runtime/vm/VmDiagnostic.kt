/**
 * Typed diagnostic model shared by the compiled loader and compiled VM.
 *
 * Loader-side opcode and native-service failures in `compiled/CompiledLoader.kt`
 * flow through this file, as do runtime failures from `CompiledVm.kt`. The
 * categories are intentionally language-neutral so a native/server VM can emit
 * the same error kind, root, instruction index, and operand path as Kotlin.
 */
package rylween.runtime.vm

import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledOpcode
import rylween.runtime.ScriptRootRef

/**
 * Stable diagnostic categories for the compiled VM contract.
 *
 * This file is the public J2 diagnostic surface shared by
 * `compiled/CompiledLoader.kt` and `CompiledVm.kt`. Loader failures map
 * opcode-schema violations from [rylween.compiled.CompiledOpcodeContract] into
 * [BadOpcode] and [BadOperand]; the VM uses the remaining variants for runtime
 * evaluation, continuation, handle, native-service, and host-service failures.
 */
enum class VmDiagnosticKind {
    BadOpcode,
    BadOperand,
    MissingHandle,
    MissingNativeOp,
    InvalidLValue,
    BadContinuationFrame,
}

/**
 * A location-aware compiled VM failure.
 *
 * Every diagnostic identifies the [root], the current [instructionIndex] inside
 * the current instruction list, and an [operandPath] for nested instruction
 * bodies. The path uses integer segments so structures such as `If.thenOps`,
 * `Switch.cases`, menu options, and text-answer branches can be addressed
 * without tying the diagnostic format to Kotlin-only class names.
 */
sealed interface VmDiagnostic {
    /** Script root being loaded or executed when the failure was detected. */
    val root: ScriptRootRef

    /** Instruction index within the current instruction list. */
    val instructionIndex: Int

    /** Numeric path to the operand or nested body that owns the failure. */
    val operandPath: List<Int>

    /** Stable category token for serialization, logs, and conformance tests. */
    val kind: VmDiagnosticKind

    /** Human-readable rendering used by loader exceptions and VM failures. */
    fun message(): String

    /** Shared root/index/path rendering used by all diagnostic variants. */
    fun location(): String =
        "root=${root.diagnosticName()} " +
            "instructionIndex=$instructionIndex " +
            "operandPath=${operandPath.diagnosticPath()}"

    /** The opcode itself is invalid for the VM contract. */
    data class BadOpcode(
        override val root: ScriptRootRef,
        override val instructionIndex: Int,
        override val operandPath: List<Int>,
        val opcode: CompiledOpcode,
        val reason: String,
    ) : VmDiagnostic {
        override val kind: VmDiagnosticKind get() = VmDiagnosticKind.BadOpcode

        override fun message(): String = "compiled VM diagnostic: $kind ${location()} opcode=$opcode reason=$reason"
    }

    /** An opcode or VM service received operands whose shape is invalid. */
    data class BadOperand(
        override val root: ScriptRootRef,
        override val instructionIndex: Int,
        override val operandPath: List<Int>,
        val opcode: CompiledOpcode,
        val reason: String,
        val expected: String,
        val actual: String,
    ) : VmDiagnostic {
        override val kind: VmDiagnosticKind get() = VmDiagnosticKind.BadOperand

        override fun message(): String =
            "compiled VM diagnostic: $kind ${location()} " +
                "opcode=$opcode reason=$reason expected=$expected actual=$actual"
    }

    /**
     * A compiled handle reference could not be resolved in the loaded bundle.
     *
     * Kept as public diagnostic contract even while current Kotlin paths do not
     * emit it; native/server VMs should be able to report the same category.
     */
    @Suppress("unused", "MemberVisibilityCanBePrivate")
    data class MissingHandle(
        override val root: ScriptRootRef,
        override val instructionIndex: Int,
        override val operandPath: List<Int>,
        val handle: CompiledHandleRef,
        val owner: String,
    ) : VmDiagnostic {
        override val kind: VmDiagnosticKind get() = VmDiagnosticKind.MissingHandle

        override fun message(): String = "compiled VM diagnostic: $kind ${location()} owner=$owner handle=${handle.kind}#${handle.id}"
    }

    /** A native expression path is absent from [NativeServiceCatalog]. */
    data class MissingNativeOp(
        override val root: ScriptRootRef,
        override val instructionIndex: Int,
        override val operandPath: List<Int>,
        val path: String,
        val reason: String,
    ) : VmDiagnostic {
        override val kind: VmDiagnosticKind get() = VmDiagnosticKind.MissingNativeOp

        override fun message(): String = "compiled VM diagnostic: $kind ${location()} path=$path reason=$reason"
    }

    /**
     * A compiled lvalue cannot be assigned in the current VM context.
     *
     * [lvalue] stays public for serialized diagnostics and conformance checks,
     * even though the Kotlin renderer currently only reads it in [message].
     */
    @Suppress("unused", "MemberVisibilityCanBePrivate")
    data class InvalidLValue(
        override val root: ScriptRootRef,
        override val instructionIndex: Int,
        override val operandPath: List<Int>,
        val lvalue: String,
        val reason: String,
    ) : VmDiagnostic {
        override val kind: VmDiagnosticKind get() = VmDiagnosticKind.InvalidLValue

        override fun message(): String = "compiled VM diagnostic: $kind ${location()} lvalue=$lvalue reason=$reason"
    }

    /**
     * A saved compiled continuation frame is malformed or incompatible.
     *
     * This is a reserved continuation-resume diagnostic for parity with native
     * and server VMs; the current Kotlin runtime does not emit it yet.
     */
    @Suppress("unused")
    data class BadContinuationFrame(
        override val root: ScriptRootRef,
        override val instructionIndex: Int,
        override val operandPath: List<Int>,
        val frame: String,
        val reason: String,
    ) : VmDiagnostic {
        override val kind: VmDiagnosticKind get() = VmDiagnosticKind.BadContinuationFrame

        override fun message(): String = "compiled VM diagnostic: $kind ${location()} frame=$frame reason=$reason"
    }
}

private fun ScriptRootRef.diagnosticName(): String =
    if (denseId == null) {
        "${kind.name}:$slug"
    } else {
        "${kind.name}#$denseId:$slug"
    }

private fun List<Int>.diagnosticPath(): String = joinToString(prefix = "[", postfix = "]")
