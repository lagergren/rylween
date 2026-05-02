package rylween.frontend.terminal.ydarg

import rylween.dsl.*
import rylween.frontend.terminal.*
import rylween.runtime.WildernessDirection
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
                    step.blocks.forEach { block ->
                        terminal.write(renderer.lines(block, TerminalTextContext(runtime = session.runtime)))
                    }
                    session.apply(YdargSessionInput.Continue)
                }
                YdargSessionStep.RoomEntry -> session.enterRoom()
                YdargSessionStep.Navigate -> {
                    writeNavigation(session)
                    when (val input = terminal.readChoice(navigationChoices(session.ydarg.keys))) {
                        is TerminalInput.Value -> session.apply(input.value)
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                is YdargSessionStep.TreasureOffer -> {
                    val keys = session.ydarg.keys
                    val choices = listOf(
                        TerminalChoice(YdargSessionInput.TakeTreasure, keys.treasureTake),
                        TerminalChoice(YdargSessionInput.LeaveTreasure, keys.treasureLeave),
                    )
                    terminal.writeBindingPrompt(
                        renderer,
                        session.treasureOfferBlock(step.item),
                        choices.map { it.binding },
                        TerminalTextContext(runtime = session.runtime),
                    )
                    when (val input = terminal.readChoice(choices)) {
                        is TerminalInput.Value -> session.apply(input.value)
                        is TerminalInput.Rejected -> return waiting(session, input.rejection)
                        TerminalInput.Unavailable -> return waiting(session)
                    }
                }
                YdargSessionStep.StairsMenu -> {
                    val keys = session.ydarg.keys
                    val choices = listOf(
                        TerminalChoice(YdargSessionInput.TeleportOut, keys.stairsTeleport),
                        TerminalChoice(YdargSessionInput.GiveStairsItem, keys.stairsGive),
                        TerminalChoice(YdargSessionInput.LeaveStairs, keys.stairsLeave),
                    )
                    terminal.writeBindingPrompt(
                        renderer,
                        session.ydarg.text.stairsMenu,
                        choices.map { it.binding },
                        TerminalTextContext(runtime = session.runtime),
                    )
                    when (val input = terminal.readChoice(choices)) {
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
                    when (result.status) {
                        TerminalRunStatus.Finished -> updated
                        TerminalRunStatus.StepLimitReached ->
                            return TerminalAdapterResult(updated, TerminalRunStatus.StepLimitReached)
                        is TerminalRunStatus.Waiting ->
                            return TerminalAdapterResult(updated, result.status)
                    }
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

    private fun navigationChoices(keys: YdargKeysModel): List<TerminalChoice<YdargSessionInput>> =
        listOf(
            TerminalChoice(YdargSessionInput.Move(WildernessDirection.North), keys.moveNorth),
            TerminalChoice(YdargSessionInput.Move(WildernessDirection.South), keys.moveSouth),
            TerminalChoice(YdargSessionInput.Move(WildernessDirection.West), keys.moveWest),
            TerminalChoice(YdargSessionInput.Move(WildernessDirection.East), keys.moveEast),
            TerminalChoice(YdargSessionInput.Command(YdargCommand.Help), keys.help),
            TerminalChoice(YdargSessionInput.Command(YdargCommand.ViewParty), keys.viewParty),
            TerminalChoice(YdargSessionInput.Command(YdargCommand.RemoveArmor), keys.removeArmor),
            TerminalChoice(YdargSessionInput.Command(YdargCommand.RemoveWeapon), keys.removeWeapon),
            TerminalChoice(YdargSessionInput.Command(YdargCommand.Drop), keys.drop),
            TerminalChoice(YdargSessionInput.Command(YdargCommand.ReadyWeapon), keys.readyWeapon),
            TerminalChoice(YdargSessionInput.Command(YdargCommand.ReadyArmor), keys.readyArmor),
            TerminalChoice(YdargSessionInput.Command(YdargCommand.Quaff), keys.quaff),
            TerminalChoice(YdargSessionInput.Command(YdargCommand.CastSpell), keys.castSpell),
        )
}
