/**
 * Checks the adventurer's journal against the world, the way a player relies
 * on it: every coordinate it gives must be a place there is something to find,
 * every "lives" entry must say where that character really is, and the place
 * a quest step sends the party to must be written down by the time the step
 * begins.
 */

package rylween.runtime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledTrigger
import rylween.compiled.CompiledWorldPosition
import rylween.dsl.impl.GridPoint

internal class AutopilotJournal(
    private val content: CompiledContentBundle,
) {
    /**
     * What is wrong with the journal [lines] in [state]; [target] names the place the current step heads for and where
     * it is, or `null` when the step heads for no place.
     */
    fun problems(
        state: RuntimeState,
        lines: List<String>,
        target: Pair<String, GridPoint?>?,
    ): List<String> {
        val text = lines.joinToString(" ")
        val places = places(state)
        return buildList {
            coordinatePattern.findAll(text).forEach { match ->
                val point = GridPoint(match.groupValues[1].toInt(), match.groupValues[2].toInt())
                if (point !in places) add("the journal gives ${match.value}, where there is nothing to find")
            }
            livesPattern.findAll(text).forEach { match ->
                val (subject, where) = match.destructured
                // "A fighter named Groth knows of the Crown. He lives in Wilor": a pronoun stands for the last name given.
                val name =
                    if (subject in pronouns) {
                        namedPattern.findAll(text.substring(0, match.range.first)).lastOrNull()?.groupValues?.get(1) ?: return@forEach
                    } else {
                        subject
                    }
                val character = content.characters.firstOrNull { character -> content.strings.value(character.nameString) == name }
                when (val position = character?.position(state)) {
                    null -> add("the journal says $name lives $where, but there is no $name")
                    is CompiledWorldPosition.Town ->
                        townName(position).takeIf { town -> town !in where }?.let { town ->
                            add("the journal says $name lives $where, but $name is in $town")
                        }
                    is CompiledWorldPosition.Wilderness ->
                        position.point.text().takeIf { point -> point !in where }?.let { point ->
                            add("the journal says $name lives $where, but $name is at $point")
                        }
                }
            }
            target?.let { (name, point) ->
                if (point == null || point.text() !in text) add("the journal does not say where $name is (${point?.text() ?: "unplaced"})")
            }
        }
    }

    /** Every square the journal may point at: quest locations, towns, fixed mission squares, and characters' huts. */
    private fun places(state: RuntimeState): Set<GridPoint> =
        buildSet {
            state.locations.forEach { placement -> add(placement.point) }
            content.towns.forEach { town -> add(town.position) }
            content.missions.forEach { mission -> (mission.trigger as? CompiledTrigger.AtPosition)?.let { trigger -> add(trigger.point) } }
            content.characters.forEach { character ->
                (character.position(state) as? CompiledWorldPosition.Wilderness)?.let { position -> add(position.point) }
            }
        }

    private fun townName(position: CompiledWorldPosition.Town): String =
        content.towns.first { town -> town.id == position.town }.let { town -> content.strings.value(town.nameString) }

    private fun GridPoint.text(): String = "${x}x$y"

    private companion object {
        val coordinatePattern = Regex("""\b(\d{1,2})x(\d{1,2})\b""")
        /** "Marraga, the ranger lives at 46x16": the name, without the job the journal adds, and where, wherever it wraps. */
        val livesPattern = Regex("""([A-Z][\w']+)(?:, the [a-z ]+?)? lives (in [A-Z]\w+(?: at \d+x\d+)?|at \d+x\d+)""")
        val namedPattern = Regex("""named ([A-Z][\w']+)""")
        val pronouns = setOf("He", "She")
    }
}
