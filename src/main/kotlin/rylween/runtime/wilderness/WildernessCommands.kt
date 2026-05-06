package rylween.runtime.wilderness

import rylween.dsl.TextBlock
import rylween.dsl.WildernessKeysModel
import rylween.runtime.InputCommand
import rylween.runtime.cardinalDirectionCommands
import rylween.runtime.generatedHelpPages

fun WildernessKeysModel.exploreCommands(): List<InputCommand<WildernessSessionInput>> =
    cardinalDirectionCommands(
        moveNorth = moveNorth,
        moveSouth = moveSouth,
        moveWest = moveWest,
        moveEast = moveEast,
        input = WildernessSessionInput::Move,
    ) +
        listOf(
            InputCommand(WildernessSessionInput.Pass, pass, "Pass"),
            InputCommand(WildernessSessionInput.Save, save, "Save Game"),
            InputCommand(WildernessSessionInput.ReadyWeapon, readyWeapon, "Wield Weapon"),
            InputCommand(WildernessSessionInput.ReadyArmor, readyArmor, "Ready Armor"),
            InputCommand(WildernessSessionInput.Redraw, redraw, "Redraw Screen"),
            InputCommand(WildernessSessionInput.Journal, journal, "View Journal"),
            InputCommand(WildernessSessionInput.Help, help, "View Help"),
            InputCommand(WildernessSessionInput.Hunt, hunt, "Hunt for Food"),
            InputCommand(WildernessSessionInput.ViewParty, viewParty, "View Party"),
            InputCommand(WildernessSessionInput.RemoveArmor, removeArmor, "Take off Armor"),
            InputCommand(WildernessSessionInput.RemoveWeapon, removeWeapon, "Put down Weapon"),
            InputCommand(WildernessSessionInput.Enter, enter, "Enter"),
            InputCommand(WildernessSessionInput.Drop, drop, "Leave Item"),
            InputCommand(WildernessSessionInput.Camp, camp, "Camp"),
            InputCommand(WildernessSessionInput.CastSpell, castSpell, "Cast Spell"),
            InputCommand(WildernessSessionInput.Quaff, quaff, "Quaff Potion"),
            InputCommand(WildernessSessionInput.Quit, quit, "Quit Game"),
        )

fun WildernessKeysModel.caveCommands(): List<InputCommand<WildernessSessionInput>> =
    listOf(
        InputCommand(WildernessSessionInput.CaveAnswer(true), caveEnter, "Enter"),
        InputCommand(WildernessSessionInput.CaveAnswer(false), caveLeave, "Leave"),
    )

fun WildernessKeysModel.outOfMovesCommands(): List<InputCommand<WildernessSessionInput>> =
    listOf(
        InputCommand(WildernessSessionInput.OutOfMovesAnswer(true), outOfMovesSave, "Save"),
        InputCommand(WildernessSessionInput.OutOfMovesAnswer(false), outOfMovesQuit, "Quit"),
    )

fun WildernessKeysModel.quitConfirmCommands(): List<InputCommand<WildernessSessionInput>> =
    listOf(
        InputCommand(WildernessSessionInput.QuitAnswer(true), quitYes, "Yes"),
        InputCommand(WildernessSessionInput.QuitAnswer(false), quitNo, "No"),
    )

fun WildernessKeysModel.effectiveHelpPages(maxLinesPerPage: Int = 7): List<TextBlock> =
    helpPages.ifEmpty { exploreCommands().generatedHelpPages(maxLinesPerPage) }
