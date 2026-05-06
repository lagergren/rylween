/**
 * Wilderness input-command builders.
 *
 * [WildernessSession] owns semantic transitions; this file maps authored
 * [rylween.dsl.WildernessKeysModel] bindings into command lists and generated
 * help pages for terminal and future host adapters.
 */
package rylween.runtime.wilderness

import rylween.dsl.TextBlock
import rylween.dsl.WildernessKeysModel
import rylween.runtime.InputCommand
import rylween.runtime.cardinalDirectionCommands
import rylween.runtime.generatedHelpPages

/** Commands accepted while the wilderness session is in its main explore step. */
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

/** Commands accepted by cave entry prompts. */
fun WildernessKeysModel.caveCommands(): List<InputCommand<WildernessSessionInput>> =
    listOf(
        InputCommand(WildernessSessionInput.CaveAnswer(true), caveEnter, "Enter"),
        InputCommand(WildernessSessionInput.CaveAnswer(false), caveLeave, "Leave"),
    )

/** Commands accepted by the out-of-moves prompt. */
fun WildernessKeysModel.outOfMovesCommands(): List<InputCommand<WildernessSessionInput>> =
    listOf(
        InputCommand(WildernessSessionInput.OutOfMovesAnswer(true), outOfMovesSave, "Save"),
        InputCommand(WildernessSessionInput.OutOfMovesAnswer(false), outOfMovesQuit, "Quit"),
    )

/** Commands accepted by the quit-confirmation prompt. */
fun WildernessKeysModel.quitConfirmCommands(): List<InputCommand<WildernessSessionInput>> =
    listOf(
        InputCommand(WildernessSessionInput.QuitAnswer(true), quitYes, "Yes"),
        InputCommand(WildernessSessionInput.QuitAnswer(false), quitNo, "No"),
    )

/** Returns authored wilderness help pages, or generated command help when absent. */
fun WildernessKeysModel.effectiveHelpPages(maxLinesPerPage: Int = 7): List<TextBlock> =
    helpPages.ifEmpty { exploreCommands().generatedHelpPages(maxLinesPerPage) }
