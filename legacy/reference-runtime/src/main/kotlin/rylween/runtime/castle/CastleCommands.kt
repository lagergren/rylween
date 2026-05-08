/**
 * Castle input-command builders.
 *
 * [CastleSession] owns semantic transitions; this file maps authored
 * [rylween.dsl.CastleKeysModel] bindings into command lists and generated help
 * pages for terminal and future host adapters.
 */
package rylween.runtime.castle

import rylween.dsl.CastleKeysModel
import rylween.dsl.TextBlock
import rylween.runtime.InputCommand
import rylween.runtime.cardinalDirectionCommands
import rylween.runtime.generatedHelpPages

/** Commands accepted while navigating castle rooms. */
fun CastleKeysModel.navigationCommands(): List<InputCommand<CastleSessionInput>> =
    cardinalDirectionCommands(
        moveNorth = moveNorth,
        moveSouth = moveSouth,
        moveWest = moveWest,
        moveEast = moveEast,
        input = CastleSessionInput::Move,
    ) +
        listOf(
            InputCommand(CastleSessionInput.Command(CastleCommand.Save), save, "Save Game"),
            InputCommand(CastleSessionInput.Command(CastleCommand.Help), help, "View Help"),
            InputCommand(CastleSessionInput.Command(CastleCommand.ViewParty), viewParty, "View Party"),
            InputCommand(CastleSessionInput.Command(CastleCommand.RemoveArmor), removeArmor, "Take off Armor"),
            InputCommand(CastleSessionInput.Command(CastleCommand.RemoveWeapon), removeWeapon, "Put down Weapon"),
            InputCommand(CastleSessionInput.Command(CastleCommand.Drop), drop, "Leave Item"),
            InputCommand(CastleSessionInput.Command(CastleCommand.ReadyWeapon), readyWeapon, "Wield Weapon"),
            InputCommand(CastleSessionInput.Command(CastleCommand.ReadyArmor), readyArmor, "Ready Armor"),
            InputCommand(CastleSessionInput.Command(CastleCommand.Quaff), quaff, "Quaff Potion"),
            InputCommand(CastleSessionInput.Command(CastleCommand.CastSpell), castSpell, "Cast Spell"),
        )

/** Commands accepted by treasure-offer prompts. */
fun CastleKeysModel.treasureCommands(): List<InputCommand<CastleSessionInput>> =
    listOf(
        InputCommand(CastleSessionInput.TakeTreasure, treasureTake, "Take it"),
        InputCommand(CastleSessionInput.LeaveTreasure, treasureLeave, "Leave it"),
    )

/** Commands accepted by stair prompts. */
fun CastleKeysModel.stairsCommands(): List<InputCommand<CastleSessionInput>> =
    listOf(
        InputCommand(CastleSessionInput.TeleportOut, stairsTeleport, "Teleport Out"),
        InputCommand(CastleSessionInput.GiveStairsItem, stairsGive, "Give an Item"),
        InputCommand(CastleSessionInput.LeaveStairs, stairsLeave, "Leave"),
    )

/** Generated help pages for castle navigation commands. */
fun CastleKeysModel.navigationHelpPages(maxLinesPerPage: Int = 7): List<TextBlock> =
    navigationCommands().generatedHelpPages(maxLinesPerPage)
