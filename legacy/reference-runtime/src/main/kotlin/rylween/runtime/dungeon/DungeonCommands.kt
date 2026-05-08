/**
 * Dungeon input-command builders.
 *
 * [DungeonSession] owns semantic state transitions; this file translates
 * authored [rylween.dsl.DungeonKeysModel] bindings into reusable command lists
 * and generated help pages for terminal or future non-terminal hosts.
 */
package rylween.runtime.dungeon

import rylween.dsl.DungeonKeysModel
import rylween.dsl.TextBlock
import rylween.runtime.InputCommand
import rylween.runtime.generatedHelpPages

/** Commands accepted while a dungeon session is in its explore step. */
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
        InputCommand(DungeonSessionInput.Save, save, "Save Game"),
    )

/** Returns authored dungeon help pages, or generated command help when absent. */
fun DungeonKeysModel.effectiveHelpPages(maxLinesPerPage: Int = 7): List<TextBlock> =
    helpPages.ifEmpty { exploreCommands().generatedHelpPages(maxLinesPerPage) }
