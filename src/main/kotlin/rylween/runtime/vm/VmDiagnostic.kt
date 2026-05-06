package rylween.runtime.vm

import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledOpcode
import rylween.runtime.ScriptRootRef

enum class VmDiagnosticKind {
    BadOpcode,
    BadOperand,
    MissingHandle,
    MissingNativeOp,
    InvalidLValue,
    BadContinuationFrame,
    UnsupportedHostService,
}

sealed interface VmDiagnostic {
    val root: ScriptRootRef
    val instructionIndex: Int
    val operandPath: List<Int>
    val kind: VmDiagnosticKind

    fun message(): String

    fun location(): String =
        "root=${root.diagnosticName()} " +
            "instructionIndex=$instructionIndex " +
            "operandPath=${operandPath.diagnosticPath()}"

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
