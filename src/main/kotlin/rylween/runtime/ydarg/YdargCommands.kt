package rylween.runtime.ydarg

import rylween.dsl.TextBlock
import rylween.dsl.YdargKeysModel
import rylween.runtime.InputCommand
import rylween.runtime.cardinalDirectionCommands
import rylween.runtime.generatedHelpPages

fun YdargKeysModel.navigationCommands(): List<InputCommand<YdargSessionInput>> =
    cardinalDirectionCommands(
        moveNorth = moveNorth,
        moveSouth = moveSouth,
        moveWest = moveWest,
        moveEast = moveEast,
        input = YdargSessionInput::Move,
    ) +
        listOf(
            InputCommand(YdargSessionInput.Command(YdargCommand.Save), save, "Save Game"),
            InputCommand(YdargSessionInput.Command(YdargCommand.Help), help, "View Help"),
            InputCommand(YdargSessionInput.Command(YdargCommand.ViewParty), viewParty, "View Party"),
            InputCommand(YdargSessionInput.Command(YdargCommand.RemoveArmor), removeArmor, "Take off Armor"),
            InputCommand(YdargSessionInput.Command(YdargCommand.RemoveWeapon), removeWeapon, "Put down Weapon"),
            InputCommand(YdargSessionInput.Command(YdargCommand.Drop), drop, "Leave Item"),
            InputCommand(YdargSessionInput.Command(YdargCommand.ReadyWeapon), readyWeapon, "Wield Weapon"),
            InputCommand(YdargSessionInput.Command(YdargCommand.ReadyArmor), readyArmor, "Ready Armor"),
            InputCommand(YdargSessionInput.Command(YdargCommand.Quaff), quaff, "Quaff Potion"),
            InputCommand(YdargSessionInput.Command(YdargCommand.CastSpell), castSpell, "Cast Spell"),
        )

fun YdargKeysModel.treasureCommands(): List<InputCommand<YdargSessionInput>> =
    listOf(
        InputCommand(YdargSessionInput.TakeTreasure, treasureTake, "Take it"),
        InputCommand(YdargSessionInput.LeaveTreasure, treasureLeave, "Leave it"),
    )

fun YdargKeysModel.stairsCommands(): List<InputCommand<YdargSessionInput>> =
    listOf(
        InputCommand(YdargSessionInput.TeleportOut, stairsTeleport, "Teleport Out"),
        InputCommand(YdargSessionInput.GiveStairsItem, stairsGive, "Give an Item"),
        InputCommand(YdargSessionInput.LeaveStairs, stairsLeave, "Leave"),
    )

fun YdargKeysModel.navigationHelpPages(maxLinesPerPage: Int = 7): List<TextBlock> = navigationCommands().generatedHelpPages(maxLinesPerPage)
