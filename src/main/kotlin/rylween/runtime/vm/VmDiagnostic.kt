/**
 * Typed diagnostic model shared by the compiled loader and compiled VM.
 *
 * Loader-side opcode and native-service failures in `compiled/CompiledLoader.kt`
 * already flow through this file; the J2 VM kernel will add runtime failures
 * from `CompiledVm.kt` using the same script-root and operand-path shape.
 */
package rylween.runtime.vm

import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledOpcode
import rylween.runtime.ScriptRootRef

/**
 * Stable diagnostic categories for the compiled VM contract.
 *
 * This file is the public J2 diagnostic surface shared by
 * `compiled/CompiledLoader.kt` and the future `CompiledVm.kt` executor. Loader
 * failures currently map opcode-schema violations from
 * [rylween.compiled.CompiledOpcodeContract] into [BadOpcode] and [BadOperand];
 * the VM kernel will use the remaining variants for runtime evaluation,
 * continuation, handle, and host-service failures.
 */
enum class VmDiagnosticKind {
    BadOpcode,
    BadOperand,
    MissingHandle,
    MissingNativeOp,
    InvalidLValue,
    BadContinuationFrame,
    UnsupportedHostService,
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

    /** A compiled handle reference could not be resolved in the loaded bundle. */
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

    /** A `NativeCall` or native expression path is absent from [NativeServiceCatalog]. */
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

    /** A compiled lvalue cannot be assigned in the current VM context. */
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

    /** A saved compiled continuation frame is malformed or incompatible. */
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

    /** The VM requested a host service that the active host does not provide. */
    data class UnsupportedHostService(
        override val root: ScriptRootRef,
        override val instructionIndex: Int,
        override val operandPath: List<Int>,
        val service: String,
        val reason: String,
    ) : VmDiagnostic {
        override val kind: VmDiagnosticKind get() = VmDiagnosticKind.UnsupportedHostService

        override fun message(): String = "compiled VM diagnostic: $kind ${location()} service=$service reason=$reason"
    }
}

private fun ScriptRootRef.diagnosticName(): String =
    if (denseId == null) {
        "${kind.name}:$slug"
    } else {
        "${kind.name}#$denseId:$slug"
    }

private fun List<Int>.diagnosticPath(): String = joinToString(prefix = "[", postfix = "]")
