/**
 * What an autopilot run used of everything the game offers: every command of
 * every command group, every spell, every battle action, and every quest. The
 * expected lists come from the bundle itself, so new content shows up as
 * something a run has not yet exercised.
 */

package rylween.runtime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.ExternalRuntimeCommand
import rylween.compiled.ExternalRuntimeSystems
import rylween.dsl.impl.FlagScope

internal class AutopilotCoverage(
    private val content: CompiledContentBundle,
    private val systems: ExternalRuntimeSystems,
) {
    private val used = mutableSetOf<String>()

    /** The debugging keys, which a player never meets. */
    private val debugGroups = setOf("wilderness.debug")

    /** Whether the run has used [name] of [kind]. */
    fun used(
        kind: String,
        name: String,
    ): Boolean = "$kind: $name" in used

    /** Records that the run used [name] of [kind], such as a command of a group, a spell, or a battle action. */
    fun record(
        kind: String,
        name: String,
    ) {
        used += "$kind: $name"
    }

    /** Records the command of [group] bound to [key], when there is one. */
    fun recordKey(
        group: String,
        key: Char,
    ) {
        systems.commands[group]?.firstOrNull { command -> command.matches(key) }?.let { command -> record(group, command.action) }
    }

    /**
     * Records the command labelled [label] chosen from a menu showing [menu]. Labels such as "Leave" belong to several
     * groups, so the choice counts only for a group whose labels include every option on the screen.
     */
    fun recordLabel(
        label: String,
        menu: List<String>,
    ) {
        systems.commands
            .filterKeys { group -> group !in debugGroups }
            .filterValues { commands -> menu.all { option -> commands.any { command -> command.label == option } } }
            .forEach { (group, commands) ->
                commands.firstOrNull { command -> command.label == label }?.let { command -> record(group, command.action) }
            }
    }

    /** Everything the game offers, as "kind: name" entries. */
    fun expected(): Set<String> =
        buildSet {
            systems.commands
                .filterKeys { group -> group !in debugGroups }
                .forEach { (group, commands) -> commands.forEach { command -> add("$group: ${command.action}") } }
            content.spells
                .map { spell -> content.slug(spell.id) }
                .filterNot { slug -> slug.startsWith(MONSTER_SPELLS) }
                .forEach { slug -> add("spell: $slug") }
            checkNotNull(systems.combat.standardBattle).actions.forEach { action -> add("battle: ${action.kind}") }
            pastimes.forEach { pastime -> add("$PASTIME: $pastime") }
            // Encounter-scoped flags track a single fight, not story progress.
            content.flags
                .filter { flag -> flag.scope == FlagScope.Party && flag.states.isNotEmpty() }
                .forEach { flag -> add("quest: ${content.slug(flag.states.last().id)}") }
        }

    /** What the game offers that the run never used; [state] is the final state, whose story flags finish quests. */
    fun missing(state: RuntimeState): List<String> {
        state.party.flags.forEach { flag -> record("quest", content.slug(flag)) }
        return (expected() - used).sorted()
    }

    private fun ExternalRuntimeCommand.matches(key: Char): Boolean =
        binding.primary.singleOrNull()?.equals(key, ignoreCase = true) == true ||
            binding.aliases.any { alias -> alias.singleOrNull()?.equals(key, ignoreCase = true) == true }

    companion object {
        const val PASTIME = "pastime"

        /** Town pastimes no menu of commands lists: what the tavern, the inn, the smith, and the guild offer. */
        val pastimes =
            listOf(
                "tavern drink",
                "tavern story",
                "tavern rumors",
                "upstairs",
                "inn supper",
                "inn drink",
                "inn lodging",
                "smith beer",
                "smith haggle",
                "guild",
            )

        private const val MONSTER_SPELLS = "monster."
    }
}
