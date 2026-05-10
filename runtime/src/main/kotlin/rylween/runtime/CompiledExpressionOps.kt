package rylween.runtime

import rylween.compiled.CompiledBinaryOp
import rylween.compiled.CompiledComparisonOp

internal fun Int.applyCompiledBinary(
    op: CompiledBinaryOp,
    right: Int,
): Int =
    when (op) {
        CompiledBinaryOp.Add -> this + right
        CompiledBinaryOp.Sub -> this - right
        CompiledBinaryOp.Mul -> this * right
        CompiledBinaryOp.Div -> this / (right.takeUnless { value -> value == 0 } ?: 1)
        CompiledBinaryOp.Mod -> this % (right.takeUnless { value -> value == 0 } ?: 1)
    }

internal fun Int.compareCompiled(
    op: CompiledComparisonOp,
    right: Int,
): Boolean =
    when (op) {
        CompiledComparisonOp.Eq -> this == right
        CompiledComparisonOp.Ne -> this != right
        CompiledComparisonOp.Lt -> this < right
        CompiledComparisonOp.Lte -> this <= right
        CompiledComparisonOp.Gt -> this > right
        CompiledComparisonOp.Gte -> this >= right
    }
