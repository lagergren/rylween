package rylween

import rylween.dsl.StateId
import rylween.dsl.ViewCommand
import rylween.frontend.terminal.TerminalAdapterResult
import rylween.frontend.terminal.TerminalPort
import rylween.frontend.terminal.TerminalTextContext
import rylween.frontend.terminal.TerminalTextRenderer
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScenarioTerminalPort(vararg inputs: String) : TerminalPort {
    private val queue = ArrayDeque(inputs.toList())
    val output: List<String> get() = mutableOutput
    val viewCommands: List<ViewCommand> get() = mutableViewCommands
    val imageSlugs: List<String> get() = mutableImageSlugs

    private val mutableOutput = mutableListOf<String>()
    private val mutableViewCommands = mutableListOf<ViewCommand>()
    private val mutableImageSlugs = mutableListOf<String>()

    override fun write(lines: List<String>) {
        mutableOutput += lines
    }

    override fun readLine(): String? =
        queue.removeFirstOrNull()

    override fun writeViewCommand(
        command: ViewCommand,
        renderer: TerminalTextRenderer,
        context: TerminalTextContext,
    ): Boolean {
        mutableViewCommands += command
        if (command is ViewCommand.ShowImage) {
            mutableImageSlugs += command.image.slug
        }
        return true
    }
}

data class TerminalScenarioCheckpoint<S>(
    val name: String,
    val verify: TerminalScenarioRun<S>.() -> Unit,
)

data class ScenarioFixture(
    val startState: StateId? = null,
    val seed: Long? = null,
    val notes: List<String> = emptyList(),
)

data class TerminalScenario<S>(
    val name: String,
    val fixture: ScenarioFixture = ScenarioFixture(),
    val inputs: List<String>,
    val start: () -> S,
    val drive: (ScenarioTerminalPort, S) -> TerminalAdapterResult<S>,
    val checkpoints: List<TerminalScenarioCheckpoint<S>> = emptyList(),
) {
    fun run(): TerminalScenarioRun<S> {
        val terminal = ScenarioTerminalPort(*inputs.toTypedArray())
        val result = drive(terminal, start())
        return TerminalScenarioRun(name, fixture, terminal, result).also { run ->
            checkpoints.forEach { checkpoint ->
                runCatching { checkpoint.verify(run) }
                    .getOrElse { error ->
                        throw AssertionError("scenario '$name' checkpoint '${checkpoint.name}' failed", error)
                    }
            }
        }
    }
}

data class TerminalScenarioRun<S>(
    val name: String,
    val fixture: ScenarioFixture,
    val terminal: ScenarioTerminalPort,
    val result: TerminalAdapterResult<S>,
)

data class HeadlessScenarioStep<S>(
    val name: String,
    val apply: (S) -> S,
)

data class HeadlessScenarioCheckpoint<S>(
    val name: String,
    val verify: HeadlessScenarioRun<S>.() -> Unit,
)

data class HeadlessScenario<S>(
    val name: String,
    val fixture: ScenarioFixture = ScenarioFixture(),
    val start: () -> S,
    val steps: List<HeadlessScenarioStep<S>>,
    val checkpoints: List<HeadlessScenarioCheckpoint<S>> = emptyList(),
) {
    fun run(): HeadlessScenarioRun<S> {
        val history = steps.map { it.name }
        val result = steps.fold(start()) { session, step -> step.apply(session) }
        return HeadlessScenarioRun(name, fixture, history, result).also { run ->
            checkpoints.forEach { checkpoint ->
                runCatching { checkpoint.verify(run) }
                    .getOrElse { error ->
                        throw AssertionError("scenario '$name' checkpoint '${checkpoint.name}' failed", error)
                    }
            }
        }
    }
}

data class HeadlessScenarioRun<S>(
    val name: String,
    val fixture: ScenarioFixture,
    val steps: List<String>,
    val result: S,
)

fun <S> checkpoint(name: String, verify: TerminalScenarioRun<S>.() -> Unit): TerminalScenarioCheckpoint<S> =
    TerminalScenarioCheckpoint(name, verify)

fun <S> headlessStep(name: String, apply: (S) -> S): HeadlessScenarioStep<S> =
    HeadlessScenarioStep(name, apply)

fun <S> headlessCheckpoint(name: String, verify: HeadlessScenarioRun<S>.() -> Unit): HeadlessScenarioCheckpoint<S> =
    HeadlessScenarioCheckpoint(name, verify)

fun <S> TerminalScenarioRun<S>.assertOutputContains(fragment: String) {
    assertTrue(
        terminal.output.any { fragment in it },
        "scenario '$name' output did not contain '$fragment'",
    )
}

fun <S> TerminalScenarioRun<S>.assertOutputDoesNotContain(fragment: String) {
    assertFalse(
        terminal.output.any { fragment in it },
        "scenario '$name' output unexpectedly contained '$fragment'",
    )
}

fun <S> TerminalScenarioRun<S>.assertImageShown(imageSlug: String) {
    assertTrue(
        imageSlug in terminal.imageSlugs,
        "scenario '$name' did not show image '$imageSlug'",
    )
}

fun <S> TerminalScenarioRun<S>.assertViewCommand(command: ViewCommand) {
    assertTrue(
        command in terminal.viewCommands,
        "scenario '$name' did not emit view command '$command'",
    )
}
