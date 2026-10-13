/**
 * How hard a full autopilot run was, for keeping the game's balance in bounds.
 *
 * The figures come from the run itself: how many days it took, how often the
 * party fell and had to start over from its last save, how many members died
 * along the way, and how many fights it fought or fled. The [score] weighs them
 * into one number, so a balance change that makes the game much harder or much
 * easier shows up as a score outside the bounds a test sets.
 */

package rylween.runtime

internal data class AutopilotDifficulty(
    val days: Int,
    val reloads: Int,
    val deaths: Int,
    /** Members brought back by the priest's Rebirth, by the church, and by a Gem of Resurrection. */
    val rebirths: Int,
    val churchRaises: Int,
    val gemRevivals: Int,
    val battles: Int,
    val fled: Int,
    /** How often the party left Ydarg's castle at a stair guardian to recover before climbing on. */
    val castleRetreats: Int,
    /** How often the priest banished a dungeon's wandering monsters. */
    val banishes: Int,
    val townVisits: Int,
    val battleItems: Int,
    val finalLevel: Int,
    /** Where the party fell, such as "day 63: 5 Winged Things", in order. */
    val falls: List<String>,
) {
    /** Days played, plus a week for every start over from the last save and a day for every member death. */
    val score: Int get() = days + reloads * RELOAD_DAYS + deaths * DEATH_DAYS

    override fun toString(): String =
        "score=$score days=$days reloads=$reloads deaths=$deaths rebirths=$rebirths churchRaises=$churchRaises " +
            "gemRevivals=$gemRevivals castleRetreats=$castleRetreats banishes=$banishes battles=$battles fled=$fled " +
            "towns=$townVisits battleItems=$battleItems level=$finalLevel falls=$falls"

    companion object {
        const val RELOAD_DAYS = 7
        const val DEATH_DAYS = 1

        /** Scores [run], which [stats] counted as it played. */
        fun of(
            run: AutopilotRunResult,
            stats: AutopilotStats,
        ): AutopilotDifficulty {
            val shown = shownLines(run.transcript)
            return AutopilotDifficulty(
                days = run.days,
                reloads = run.reloads,
                deaths = shown.count { line -> "DIES!" in line },
                rebirths = shown.count { line -> "'s eyelids flicker" in line },
                churchRaises = shown.count { line -> line.startsWith("The priestess raises") },
                gemRevivals = shown.count { line -> "opens" in line && "eyes!" in line && "eyelids" !in line },
                battles = shown.count { line -> line.trim() == "You face" },
                fled = shown.count { line -> line.startsWith("You escaped") || line.startsWith("Coward.") },
                castleRetreats = shown.count { line -> line.startsWith("And you are outside") },
                banishes = shown.count { line -> "deafening clap" in line },
                townVisits = stats.townVisits,
                battleItems = stats.battleItems,
                finalLevel = run.state.party.members.maxOf { member -> member.level },
                falls = falls(run.transcript),
            )
        }

        /**
         * The transcript with each line kept once per screen: a page that waits for RETURN is recorded again with its
         * prompt, so the same event would otherwise count twice. A screen ends at the key pressed after it.
         */
        private fun shownLines(transcript: List<String>): List<String> {
            val screen = mutableSetOf<String>()
            return transcript.filter { line ->
                if (line.startsWith("> ")) screen.clear()
                screen.add(line)
            }
        }

        private fun falls(transcript: List<String>): List<String> {
            var day = ""
            var foes = ""
            return buildList {
                transcript.forEachIndexed { index, line ->
                    when {
                        line.startsWith("=== day ") -> day = line.removePrefix("=== ").removeSuffix(" ===")
                        line.trim() == "You face" || line.trim() == "You still face" -> foes = transcript.getOrNull(index + 1)?.trim().orEmpty()
                        line.startsWith("The party has fallen") -> add("$day: $foes")
                    }
                }
            }
        }
    }
}
