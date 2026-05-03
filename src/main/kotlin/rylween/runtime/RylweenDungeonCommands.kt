package rylween.runtime

import rylween.dsl.DungeonKeysModel
import rylween.dsl.TextBlock

fun DungeonKeysModel.exploreCommands(): List<InputCommand<DungeonSessionInput>> =
    listOf(
        InputCommand(DungeonSessionInput.Descend, descend, "Descend"),
        InputCommand(DungeonSessionInput.Ascend, ascend, "Ascend"),
        InputCommand(DungeonSessionInput.Help, help, "View Help"),
        InputCommand(DungeonSessionInput.Drop, drop, "Leave Item"),
        InputCommand(DungeonSessionInput.RemoveArmor, removeArmor, "Take off Armor"),
        InputCommand(DungeonSessionInput.RemoveWeapon, removeWeapon, "Put down Weapon"),
        InputCommand(DungeonSessionInput.ReadyWeapon, readyWeapon, "Wield Weapon"),
        InputCommand(DungeonSessionInput.ReadyArmor, readyArmor, "Ready Armor"),
        InputCommand(DungeonSessionInput.Quaff, quaff, "Quaff Potion"),
        InputCommand(DungeonSessionInput.CastSpell, castSpell, "Cast Spell"),
        InputCommand(DungeonSessionInput.ViewParty, viewParty, "View Party"),
    )

fun DungeonKeysModel.effectiveHelpPages(maxLinesPerPage: Int = 7): List<TextBlock> =
    helpPages.ifEmpty { exploreCommands().generatedHelpPages(maxLinesPerPage) }
