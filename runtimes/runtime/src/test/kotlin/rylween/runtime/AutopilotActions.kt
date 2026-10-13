/**
 * Multi-key undertakings the autopilot starts at a command prompt, such as
 * camping with a guard, hunting, casting a priest spell, or readying gear.
 *
 * An action is a list of steps, one per key. The first step answers the
 * command prompt; each later step answers the prompt the previous key caused.
 * A step that does not recognize its prompt ends the action, so a refusal such
 * as "Not tired." never receives keys meant for a prompt that did not appear.
 */

package rylween.runtime

import rylween.compiled.ExternalRuntimeCommand
import rylween.compiled.ExternalRuntimeSystems
import rylween.compiled.RuntimeCommandActionId
import rylween.compiled.RuntimeCommandGroupId
import rylween.dsl.impl.RuntimeMode

internal class AutopilotAction(
    val name: String,
    private val steps: List<(AutopilotView) -> Char?>,
) {
    private var index = 0

    val finished: Boolean get() = index >= steps.size

    /** The key for this step, or `null` when the action is over or does not recognize the prompt. */
    fun next(view: AutopilotView): Char? {
        if (finished) return null
        val key = steps[index](view)
        index = if (key == null) steps.size else index + 1
        return key
    }

    override fun toString(): String = name
}

/** The equipment slot a ready action fills. */
internal enum class AutopilotGear { Weapon, Armor }

internal class AutopilotActions(
    private val systems: ExternalRuntimeSystems,
) {
    private val wilderness = systems.commands.getValue(RuntimeCommandGroupId.Wilderness.id)
    private val dungeon = systems.commands.getValue(RuntimeCommandGroupId.Dungeon.id)

    /** The commands of the place the party is in: the wilderness, a dungeon, or a castle floor. */
    fun commandsAt(state: RuntimeState): List<ExternalRuntimeCommand> =
        when (state.currentMode) {
            RuntimeMode.Dungeon -> dungeon
            RuntimeMode.Castle -> systems.commands.getValue(RuntimeCommandGroupId.castleNavigation(checkNotNull(state.castle).castleId))
            else -> wilderness
        }

    fun move(direction: AutopilotDirection): AutopilotAction = single("move ${direction.name.lowercase()}", compass(direction))

    fun enter(): AutopilotAction = single("enter", wilderness.key(RuntimeCommandActionId.Enter))

    fun save(): AutopilotAction = single("save", wilderness.key(RuntimeCommandActionId.Save))

    fun quit(): AutopilotAction = single("quit", wilderness.key(RuntimeCommandActionId.Quit))

    fun descend(): AutopilotAction = single("descend", dungeon.key(RuntimeCommandActionId.Descend))

    fun ascend(): AutopilotAction = single("ascend", dungeon.key(RuntimeCommandActionId.Ascend))

    /** Camps with [guard] standing watch: an unguarded camp's ambush kills a sleeping member. */
    fun camp(guard: AutopilotMember): AutopilotAction =
        AutopilotAction(
            "camp, ${guard.number} on guard",
            listOf(
                { wilderness.key(RuntimeCommandActionId.Camp) },
                { view -> 'Y'.takeIf { view.text.any { line -> "guard" in line } } },
                { guard.digit },
            ),
        )

    /** Sends the ranger [hunter] to trap food. */
    fun hunt(hunter: AutopilotMember): AutopilotAction =
        AutopilotAction(
            "hunt with ${hunter.number}",
            listOf(
                { wilderness.key(RuntimeCommandActionId.Hunt) },
                { view -> hunter.digit.takeIf { view.text.any { line -> line.startsWith("Who?") } } },
            ),
        )

    /**
     * Casts the priest spell labelled [spell] (such as "Healing") with [caster], on [target] when the spell asks
     * for one, using the casting key of [commands].
     */
    fun castPriestSpell(
        caster: AutopilotMember,
        spell: String,
        target: AutopilotMember? = null,
        commands: List<ExternalRuntimeCommand> = wilderness,
    ): AutopilotAction =
        AutopilotAction(
            "cast $spell with ${caster.number}" + target?.let { " on ${it.number}" }.orEmpty(),
            buildList {
                add { commands.key(RuntimeCommandActionId.CastSpell) }
                add { view -> caster.digit.takeIf { view.text.any { line -> line.startsWith("Who?") } } }
                add { view -> AutopilotMenu.parse(view.text).find(spell)?.key }
                if (target != null) add { view -> target.digit.takeIf { view.text.any { line -> line.startsWith("Who?") } } }
            },
        )

    /**
     * Has the priest [caster] teleport the party [miles] miles (two squares each) towards [direction]. The spell asks
     * for the direction as a keypad digit and then for the distance.
     */
    fun teleport(
        caster: AutopilotMember,
        direction: AutopilotDirection,
        miles: Int,
    ): AutopilotAction =
        AutopilotAction(
            "cast Teleport with ${caster.number}, $miles miles ${direction.name.lowercase()}",
            listOf(
                { wilderness.key(RuntimeCommandActionId.CastSpell) },
                { view -> caster.digit.takeIf { view.text.any { line -> line.startsWith("Who?") } } },
                { view -> AutopilotMenu.parse(view.text).find("Teleport")?.key },
                { view -> compass(direction).takeIf { view.text.any { line -> "Teleport" in line } } },
                { view -> miles.digitToChar().takeIf { view.text.any { line -> "miles" in line } } },
            ),
        )

    private fun compass(direction: AutopilotDirection): Char =
        wilderness.key(
            when (direction) {
                AutopilotDirection.North -> RuntimeCommandActionId.MoveNorth
                AutopilotDirection.South -> RuntimeCommandActionId.MoveSouth
                AutopilotDirection.West -> RuntimeCommandActionId.MoveWest
                AutopilotDirection.East -> RuntimeCommandActionId.MoveEast
            },
        )

    /** Has [target] drink the inventory potion named [itemName], using the quaff key of [commands]. */
    fun quaff(
        itemName: String,
        target: AutopilotMember,
        commands: List<ExternalRuntimeCommand> = wilderness,
    ): AutopilotAction =
        AutopilotAction(
            "quaff $itemName for ${target.number}",
            listOf(
                { commands.key(RuntimeCommandActionId.Quaff) },
                { view -> AutopilotMenu.parse(view.text).findInventoryItem(itemName)?.key },
                { view -> target.digit.takeIf { view.text.any { line -> line.startsWith("Who?") } } },
            ),
        )

    /** Drops the inventory item named [itemName]; the runtime asks for no confirmation. */
    fun drop(itemName: String): AutopilotAction =
        AutopilotAction(
            "drop $itemName",
            listOf(
                { wilderness.key(RuntimeCommandActionId.Drop) },
                { view -> AutopilotMenu.parse(view.text).findInventoryItem(itemName)?.key },
            ),
        )

    /** Readies the inventory item named [itemName] as [member]'s weapon or armor. */
    fun ready(
        gear: AutopilotGear,
        member: AutopilotMember,
        itemName: String,
        commands: List<ExternalRuntimeCommand> = wilderness,
    ): AutopilotAction {
        val action = if (gear == AutopilotGear.Weapon) RuntimeCommandActionId.ReadyWeapon else RuntimeCommandActionId.ReadyArmor
        return AutopilotAction(
            "ready $itemName for ${member.number}",
            listOf(
                { commands.key(action) },
                { view -> member.digit.takeIf { view.text.any { line -> line.startsWith("Who?") } } },
                { view -> AutopilotMenu.parse(view.text).findInventoryItem(itemName)?.key },
            ),
        )
    }

    private fun single(
        name: String,
        key: Char,
    ): AutopilotAction = AutopilotAction(name, listOf { key })
}

internal fun List<ExternalRuntimeCommand>.key(action: RuntimeCommandActionId): Char =
    first { command -> command.action == action.id }.binding.primary.single()
