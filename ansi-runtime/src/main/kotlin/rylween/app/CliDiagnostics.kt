/**
 * Shared CLI error handling for Rylween app entry points.
 *
 * `TerminalMain.kt`, `RylweenAnsiMain.kt`, and artifact tools use this wrapper
 * to turn validation/startup failures from `dsl/DslValidation.kt` and
 * `engine/Startup.kt` into concise process errors.
 */
@file:Suppress("ReplacePrintlnWithLogging", "UseOfSystemOutOrSystemErr")

package rylween.app

import rylween.dsl.RylweenValidationException
import java.io.IOException
import kotlin.system.exitProcess

internal inline fun runRylweenCli(block: () -> Unit) {
    runCatching(block).getOrElse { error ->
        when (error) {
            is RylweenValidationException -> {
                System.err.println("Rylween startup failed:")
                error.diagnostics.forEach { diagnostic ->
                    System.err.println("- $diagnostic")
                }
                exitProcess(1)
            }
            is IllegalArgumentException,
            is IllegalStateException,
            is IOException,
            -> printStartupFailure(error)
            else -> throw error
        }
    }
}

private fun printStartupFailure(error: Throwable): Nothing {
    System.err.println("Rylween startup failed: ${error.message ?: error::class.simpleName}")
    exitProcess(1)
}

internal fun List<String>.valueAfter(
    index: Int,
    option: String,
): String = getOrNull(index + 1) ?: error("$option requires a value")
