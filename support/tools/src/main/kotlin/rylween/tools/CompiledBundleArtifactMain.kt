/**
 * Command-line entry points for writing, checking, and inspecting compiled
 * bundle artifacts. These tools bridge `game/RylweenCompiledGame` to the
 * compiled artifact readers in `compiled/ExternalBundleArtifact.kt`. They live
 * outside the runtime subprojects so bundle emission stays a compiler/tooling
 * concern.
 */
package rylween.tools

import rylween.compiled.CompiledGameBundle
import rylween.compiled.ExternalBundleSectionId
import rylween.compiled.inspectExternalBundleArtifact
import rylween.compiled.loadCompiledGame
import rylween.compiled.readExternalBundleArtifact
import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeGame
import rylween.compiled.readExternalRuntimeSystemsSummary
import rylween.compiled.toCompiledGameBundle
import rylween.compiled.writeExternalBundleArtifact
import rylween.dsl.RylweenValidationException
import rylween.game.RylweenCompiledGame
import rylween.runtime.RuntimeLog
import java.io.File
import java.io.IOException
import kotlin.system.exitProcess

object RylweenExternalBundleArtifactMain {
    @JvmStatic
    fun main(args: Array<String>): Unit =
        runRylweenToolCli(args) { cli ->
            val output = File(cli.positionals.singleOrNull() ?: "build/compiled/rylween-bundle.json")
            cli.diagnostics.event("external.emit.start output=${output.path}")
            val game = RylweenCompiledGame.nextRylween
            val bundle = game.toCompiledGameBundle()
            cli.diagnostics.event("external.compile ${bundle.compileSummary()}")
            bundle.loadCompiledGame()
            cli.diagnostics.event("external.validate loaded=true")
            game.writeExternalBundleArtifact(output)
            cli.diagnostics.event("external.emit.done output=${output.path} bytes=${output.length()}")
            @Suppress("ReplacePrintlnWithLogging")
            println("Wrote external compiled bundle: ${output.path}")
        }
}

object RylweenExternalBundleCheckMain {
    @JvmStatic
    fun main(args: Array<String>): Unit =
        runRylweenToolCli(args) { cli ->
            val input = File(cli.positionals.singleOrNull() ?: "build/compiled/rylween-bundle.json")
            cli.diagnostics.event("external.check.start input=${input.path}")
            val summary = input.readExternalBundleArtifact()
            val runtimeSystems = input.readExternalRuntimeSystemsSummary()
            val materialized = input.readExternalCompiledGameBundle()
            val runtimeGame = input.readExternalRuntimeGame()
            // The game needs text, handles, scripts, states, and at least one castle;
            // an empty table means the emitter dropped content.
            val emptyTables =
                mapOf(
                    "strings" to summary.section(ExternalBundleSectionId.Strings).rowCount,
                    "handles" to summary.section(ExternalBundleSectionId.Handles).rowCount,
                    "scripts" to materialized.content.scripts.roots.size,
                    "states" to materialized.states.size,
                    "castles" to runtimeSystems.castles.size,
                ).filterValues { count -> count == 0 }.keys
            check(emptyTables.isEmpty()) { "compiled bundle check failed: empty ${emptyTables.joinToString()}" }
            val checkSummary =
                buildString {
                    append("compiled bundle ok:")
                    append(" strings=${summary.section(ExternalBundleSectionId.Strings).rowCount}")
                    append(" handles=${summary.section(ExternalBundleSectionId.Handles).rowCount}")
                    append(" scripts=${materialized.content.scripts.roots.size}")
                    append(" states=${materialized.states.size}")
                    append(" runtimeStates=${runtimeGame.states.size}")
                    append(" runtimeSystems=${runtimeSystems.schemaVersion}")
                    append(" castles=${runtimeSystems.castles.size}")
                }
            cli.diagnostics.event("external.check.done $checkSummary")
            @Suppress("ReplacePrintlnWithLogging")
            println(checkSummary)
        }
}

object RylweenExternalBundleInspectMain {
    @JvmStatic
    fun main(args: Array<String>): Unit =
        runRylweenToolCli(args) { cli ->
            val input = File(cli.positionals.getOrNull(0) ?: "build/compiled/rylween-bundle.json")
            val output = cli.positionals.getOrNull(1)?.let(::File)
            cli.diagnostics.event("external.inspect.start input=${input.path} output=${output?.path.orEmpty()}")
            val inspection = input.inspectExternalBundleArtifact()
            @Suppress("ReplacePrintlnWithLogging")
            if (output == null) {
                print(inspection)
                cli.diagnostics.event("external.inspect.done stdout=true lines=${inspection.lineSequence().count()}")
            } else {
                output.parentFile?.mkdirs()
                output.writeText(inspection)
                cli.diagnostics.event("external.inspect.done output=${output.path} bytes=${output.length()}")
                println("Wrote external compiled bundle inspection: ${output.path}")
            }
        }
}

private data class ToolCli(
    val positionals: List<String>,
    val diagnostics: ToolDiagnostics,
)

private class ToolDiagnostics(
    private val log: RuntimeLog?,
) {
    fun event(message: String) {
        log?.event("tool.bundle.$message")
    }
}

private data class ParsedToolArgs(
    val positionals: List<String>,
    val logFile: File?,
)

private fun parseToolArgs(args: Array<String>): ParsedToolArgs {
    var logFile = System.getenv("RYLWEEN_TOOL_LOG")?.takeIf { value -> value.isNotBlank() }?.let(::File)
    var index = 0
    val positionals =
        buildList {
            while (index < args.size) {
                val arg = args[index]
                when (arg) {
                    "--log" -> {
                        require(index + 1 < args.size) { "--log requires a file path" }
                        index += 1
                        logFile = File(args[index])
                    }
                    else -> {
                        if (arg.startsWith("--log=")) {
                            logFile = File(arg.substringAfter("--log="))
                        } else {
                            add(arg)
                        }
                    }
                }
                index += 1
            }
        }
    return ParsedToolArgs(positionals, logFile)
}

private fun runRylweenToolCli(
    args: Array<String>,
    block: (ToolCli) -> Unit,
) {
    val parsed =
        runCatching { parseToolArgs(args) }
            .getOrElse { error ->
                printToolFailure(error)
                exitProcess(1)
            }
    val log = parsed.logFile?.let(RuntimeLog::open)
    val diagnostics = ToolDiagnostics(log)
    var exitCode = 0
    var unexpected: Throwable? = null
    try {
        block(ToolCli(parsed.positionals, diagnostics))
    } catch (error: Throwable) {
        diagnostics.event("failure type=${error::class.simpleName.orEmpty()} message=${error.message.orEmpty()}")
        when (error) {
            is RylweenValidationException -> printValidationFailure(error)
            is IllegalArgumentException,
            is IllegalStateException,
            is IOException,
            -> printToolFailure(error)
            else -> unexpected = error
        }
        if (unexpected == null) exitCode = 1
    } finally {
        log?.close()
    }
    unexpected?.let { throw it }
    if (exitCode != 0) exitProcess(exitCode)
}

private fun printValidationFailure(error: RylweenValidationException) {
    System.err.println("Rylween bundle tool failed:")
    error.diagnostics.forEach { diagnostic ->
        System.err.println("- $diagnostic")
    }
}

private fun printToolFailure(error: Throwable) {
    System.err.println("Rylween bundle tool failed: ${error.message ?: error::class.simpleName}")
}

private fun CompiledGameBundle.compileSummary(): String =
    "strings=${content.strings.entries.size} " +
        "handles=${content.handles.handles.size} " +
        "scripts=${content.scripts.roots.size} " +
        "states=${states.size} " +
        "instructions=${content.scripts.roots.sumOf { root -> root.instructions.size }} " +
        "operands=${content.scripts.roots.sumOf { root -> root.instructions.sumOf { instruction -> instruction.operands.size } }}"
