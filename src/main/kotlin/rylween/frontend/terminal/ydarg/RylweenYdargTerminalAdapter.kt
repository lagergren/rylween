package rylween.frontend.terminal.ydarg

import rylween.dsl.*
import rylween.frontend.terminal.*
import rylween.runtime.commandBindings
import rylween.runtime.ydarg.*

class YdargSessionTerminalAdapter(
    private val terminal: TerminalPort,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(),
) {
    fun run(initial: YdargSession): YdargSession =
        runUntilBlocked(initial).session

    fun runUntilBlocked(initial: YdargSession): TerminalAdapterResult<YdargSession> {
        var session = initial
        repeat(512) {
            session = when (val step = session.step) {
                is YdargSessionStep.Messages -> {
                    step.image?.let { image ->
                        terminal.writeViewCommand(
                            ViewCommand.ShowImage(image),
                            renderer,
                            TerminalTextContext(runtime = session.runtime),
                        )
                    }
                    terminal.writeBlocks(step.blocks, renderer, TerminalTextContext(runtime = session.runtime))
                    session.apply(YdargSessionInput.Continue)
                }
                YdargSessionStep.RoomEntry -> session.enterRoom()
                YdargSessionStep.Navigate -> {
                    writeNavigation(session)
                    when (val input = terminal.readChoice(session.ydarg.keys.navigationCommands().terminalChoices())) {
                        is TerminalInput.Value -> session.apply(input.value)
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                is YdargSessionStep.TreasureOffer -> {
                    val keys = session.ydarg.keys
                    val commands = keys.treasureCommands()
                    terminal.writeBindingPrompt(
                        renderer,
                        session.treasureOfferBlock(step.item),
                        commands.commandBindings(),
                        TerminalTextContext(runtime = session.runtime),
                    )
                    when (val input = terminal.readChoice(commands.terminalChoices())) {
                        is TerminalInput.Value -> session.apply(input.value)
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                YdargSessionStep.StairsMenu -> {
                    val keys = session.ydarg.keys
                    val commands = keys.stairsCommands()
                    terminal.writeBindingPrompt(
                        renderer,
                        session.ydarg.text.stairsMenu,
                        commands.commandBindings(),
                        TerminalTextContext(runtime = session.runtime),
                    )
                    when (val input = terminal.readChoice(commands.terminalChoices())) {
                        is TerminalInput.Value -> session.apply(input.value)
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                YdargSessionStep.StairGive -> {
                    val choices = (1..5).map { digit ->
                        TerminalChoice(YdargSessionInput.StairItem(digit), inputKey(digit.digitToChar(), label = digit.toString()))
                    }
                    terminal.writeBindingPrompt(
                        renderer,
                        session.ydarg.text.giveMenu,
                        choices.map { it.binding },
                        TerminalTextContext(runtime = session.runtime),
                    )
                    when (val input = terminal.readChoice(choices)) {
                        is TerminalInput.Value -> session.apply(input.value)
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                is YdargSessionStep.Battle -> {
                    val result = BattleSessionTerminalAdapter(terminal, renderer).runUntilBlocked(step.session)
                    val updated = session.replaceBattleSession(result.session)
                    continueOrBlock(updated, result.status) { return it }
                }
                is YdargSessionStep.Finished -> return finished(session)
            }
        }
        return TerminalAdapterResult(session, TerminalRunStatus.StepLimitReached)
    }

    private fun writeNavigation(session: YdargSession) {
        session.navigationBlocks().forEach { block ->
            terminal.write(renderer.lines(block, TerminalTextContext(runtime = session.runtime)))
        }
    }

}
