/**
 * Creates a new party from the title menu the way a player rolls one: each
 * member's stats are rerolled until they meet that member's limits, and the
 * member then gets a name, gender, race, and class. The party plays with
 * whatever the dice gave it, so a run tests the game against an ordinary party
 * rather than a hand-made one.
 */

package rylween.runtime

import rylween.compiled.ExternalRuntimeSystems
import rylween.compiled.RuntimeCommandActionId
import rylween.compiled.RuntimeCommandGroupId
import rylween.dsl.impl.CharacterCreationStat

/**
 * A member to create: [name], the [gender], [race], and [pcClass] labels the creation menus show, and the lowest roll
 * of each stat in [minimums] the player accepts.
 */
internal data class AutopilotRecruit(
    val name: String,
    val gender: String,
    val race: String,
    val pcClass: String,
    val minimums: Map<CharacterCreationStat, Int>,
)

internal class AutopilotCreation(
    systems: ExternalRuntimeSystems,
    private val partyName: String,
    private val recruits: List<AutopilotRecruit>,
) {
    private val createKey = systems.commands.getValue(RuntimeCommandGroupId.Title.id).key(RuntimeCommandActionId.CreateNewParty)

    /** The title-menu key this creation pressed, for coverage. */
    val titleKey: Char get() = createKey
    private var started = false
    private var member = 0

    private val rollCounts = sortedMapOf<Int, Int>()
    private val acceptedStats = sortedMapOf<Int, Map<CharacterCreationStat, Int>>()

    /** Every stat roll seen, per member number, accepted or not. */
    val rolls: Map<Int, Int> get() = rollCounts

    /** The stats each member was created with, per member number. */
    val accepted: Map<Int, Map<CharacterCreationStat, Int>> get() = acceptedStats

    /** The key for a title or creation screen, or `null` when [view] shows neither. */
    fun key(view: AutopilotView): Char? {
        // The title splash takes its first key as a title-menu choice.
        if (!started) {
            started = true
            return createKey
        }
        val text = view.text.joinToString(" ")
        val menu = AutopilotMenu.parse(view.text)
        return when {
            menu.find(ROLL_YOUR_OWN) != null -> menu.find(ROLL_YOUR_OWN)?.key
            STATS_PAGE in text -> reroll(view.text)
            GENDER in text -> menu.find(recruit().gender)?.key
            RACE in text -> menu.find(recruit().race)?.key
            CLASS in text -> menu.find(recruit().pcClass)?.key
            else -> null
        }
    }

    /** The party or member name a creation prompt asks for, or `null` for any other text prompt. */
    fun answer(view: AutopilotView): String? {
        val text = view.prompt.joinToString(" ")
        return when {
            PARTY_NAME in text -> partyName
            MEMBER_NAME in text -> recruit().name
            else -> null
        }
    }

    /** Rerolls ('Y') until the stats page meets the member's limits, then keeps the roll ('N'). */
    private fun reroll(lines: List<String>): Char {
        member =
            lines.firstNotNullOf { line ->
                memberPattern
                    .find(line)
                    ?.groupValues
                    ?.get(1)
                    ?.toInt()
            }
        rollCounts.merge(member, 1, Int::plus)
        val stats =
            lines
                .mapNotNull { line -> statPattern.find(line.trim()) }
                .associate { match ->
                    enumValueOf<CharacterCreationStat>(match.groupValues[1].replace(" ", "")) to
                        match.groupValues[2].toInt()
                }
        val good = recruit().minimums.all { (stat, minimum) -> (stats[stat] ?: 0) >= minimum }
        if (good) acceptedStats[member] = stats
        return if (good) 'N' else 'Y'
    }

    private fun recruit(): AutopilotRecruit = recruits[member - 1]

    private companion object {
        const val ROLL_YOUR_OWN = "Roll your own"
        const val STATS_PAGE = "Stats for char:"
        const val GENDER = "What gender is"
        const val RACE = "What race is"
        const val CLASS = "Pick a class"
        const val PARTY_NAME = "name of your party"
        const val MEMBER_NAME = "a name:"
        val memberPattern = Regex("""Stats for char: (\d+)""")
        val statPattern = Regex("""^(Weapon Skill|Health|Wisdom|Charisma|Dexterity)\s*:\s*(\d+)$""")
    }
}
