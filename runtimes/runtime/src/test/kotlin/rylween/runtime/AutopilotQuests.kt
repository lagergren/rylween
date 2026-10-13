/**
 * The walkthrough the full-game autopilot follows: every quest step from a new
 * game to the castle, in order, with what the party must have before it starts
 * one. Mission steps fire once and cannot be retried, so a step only begins
 * when the party is strong enough and carries everything it needs.
 */

package rylween.runtime

import rylween.dsl.impl.GridPoint

/** What a quest step asks the party to do. */
internal sealed interface AutopilotObjective {
    /** Talk to the character named [npc], answering its questions with [answers] in order of preference. */
    data class TalkTo(
        val npc: String,
        val answers: List<String> = listOf("Yes", "Accept", "Buy"),
    ) : AutopilotObjective

    /** Walk onto the placed location [location], where its mission fires. */
    data class Reach(
        val location: String,
    ) : AutopilotObjective

    /** Walk onto the fixed square [point], where its mission fires. */
    data class ReachPoint(
        val point: GridPoint,
    ) : AutopilotObjective

    /** Keep walking, on [terrain] when given, until a random or terrain mission fires. */
    data class Wander(
        val terrain: String? = null,
    ) : AutopilotObjective
}

/**
 * One step of the walkthrough, done once story flag [flag] reaches [state] or a later one. The party starts it only
 * once its leader is at least [minimumLevel] and it has [torches], [gold], [freeSlots] inventory slots, [gems] altar
 * gems, and [carry] (an item slug) when given.
 */
internal data class AutopilotQuestStep(
    val name: String,
    val flag: String,
    val state: String,
    val objective: AutopilotObjective,
    val minimumLevel: Int = 1,
    val torches: Int = 0,
    val gold: Int = 0,
    val freeSlots: Int = 1,
    val gems: Int = 0,
    val carry: String? = null,
)

internal object AutopilotQuests {
    /** Item slugs the temple of Thun accepts as altar gems. */
    val altarGems = setOf("red_gem", "blue_gem", "green_gem")

    /** Typed answers, keyed by a phrase of the question they answer. */
    val textAnswers =
        listOf(
            "Not red, not white" to "water",
            "I swallow her" to "night",
            "all the king's horses" to "a well",
            "What's the password?" to "RISH EKKIN",
        )

    /**
     * The last step once every quest is done: storming Baron Ydarg's castle, whose fights come one after another, so
     * the party goes in as ready as for any boss. It has no story flag; the castle's own milestone ends the run.
     */
    val castle =
        AutopilotQuestStep(
            "storm Ydarg's castle",
            flag = "",
            state = "",
            objective = AutopilotObjective.Reach("castle"),
            minimumLevel = 9,
            freeSlots = 0,
        )

    /** Gold for a map: its price plus the town's entry tax, which a leader of level 5 or more pays on the gold carried. */
    private fun mapGold(price: Int): Int = price * 110 / 100

    val steps: List<AutopilotQuestStep> =
        listOf(
            // Accepting arms a random, mandatory fight with up to six bandits, so the party accepts only once it can win it.
            AutopilotQuestStep("accept Garth's cargo", "garth.bandits", "accepted", AutopilotObjective.TalkTo("Garth"), minimumLevel = 5),
            AutopilotQuestStep("recover Garth's cargo", "garth.bandits", "recovered", AutopilotObjective.Wander(), minimumLevel = 5),
            AutopilotQuestStep("collect Garth's reward", "garth.bandits", "closed", AutopilotObjective.TalkTo("Garth")),
            // Side quests, taken early for their swords: each step is one conversation in the right order.
            AutopilotQuestStep("take Nrim'to's letter", "ranger.letter", "accepted", AutopilotObjective.TalkTo("Nrim'to")),
            AutopilotQuestStep("bring Marraga her brother's letter", "ranger.letter", "delivered", AutopilotObjective.TalkTo("Marraga")),
            AutopilotQuestStep("hear Areth about his book", "fighter.priest_book", "priest_told", AutopilotObjective.TalkTo("Areth")),
            AutopilotQuestStep("ask Gorm about Areth's book", "fighter.priest_book", "priest_visited", AutopilotObjective.TalkTo("Gorm")),
            AutopilotQuestStep("get the book back from Nevaak", "fighter.priest_book", "wizard_robbed", AutopilotObjective.TalkTo("Nevaak")),
            AutopilotQuestStep("bring Areth his book", "fighter.priest_book", "fighter_grateful", AutopilotObjective.TalkTo("Areth")),
            // Ron's wife is held in the orc cave at 42,23; Ron pays with armor, battle items, and gold, five items in all.
            AutopilotQuestStep("hear Ron's plea", "peasant.wife", "accepted", AutopilotObjective.TalkTo("Ron"), minimumLevel = 6),
            AutopilotQuestStep(
                "free Tammy from the orc cave",
                "peasant.wife",
                "rescued",
                AutopilotObjective.ReachPoint(GridPoint(42, 23)),
                minimumLevel = 6,
                torches = 8,
            ),
            AutopilotQuestStep("bring Tammy home to Ron", "peasant.wife", "paid", AutopilotObjective.TalkTo("Ron"), freeSlots = 5),
            AutopilotQuestStep("hear Groth on the nymph", "nymph.seeker", "told", AutopilotObjective.TalkTo("Groth")),
            AutopilotQuestStep("take Ithienn's first task", "master_crown", "task1_assigned", AutopilotObjective.TalkTo("Ithienn")),
            AutopilotQuestStep(
                "raid the kobold village",
                "master_crown",
                "task1_done",
                AutopilotObjective.Reach("kobold_village"),
                minimumLevel = 7,
            ),
            AutopilotQuestStep("take Ithienn's second task", "master_crown", "task2_assigned", AutopilotObjective.TalkTo("Ithienn")),
            AutopilotQuestStep("find skullweed", "master_crown", "task2_done", AutopilotObjective.Wander(terrain = "swamp")),
            AutopilotQuestStep("take Ithienn's third task", "master_crown", "task3_assigned", AutopilotObjective.TalkTo("Ithienn")),
            AutopilotQuestStep("meet Borin", "master_crown", "borin_met", AutopilotObjective.Reach("borin_camp")),
            AutopilotQuestStep(
                "take Reebar's dagger from Nolrim's crypt",
                "master_crown",
                "dagger_taken",
                AutopilotObjective.Reach("nolrim_crypt"),
                minimumLevel = 9,
                torches = 21,
            ),
            AutopilotQuestStep("bring Borin the dagger", "master_crown", "borin_paid", AutopilotObjective.Reach("borin_camp"), freeSlots = 3),
            AutopilotQuestStep("learn where the Master Crown is", "master_crown", "crown_told", AutopilotObjective.TalkTo("Ithienn")),
            AutopilotQuestStep(
                "take the Master Crown",
                "master_crown",
                "crown_taken",
                AutopilotObjective.Reach("master_crown_dungeon"),
                minimumLevel = 11,
                torches = 11,
            ),
            AutopilotQuestStep(
                "accept the hermit's task",
                "hermit.troll_tooth",
                "accepted",
                AutopilotObjective.TalkTo("Marcus"),
                minimumLevel = 9,
            ),
            AutopilotQuestStep("pull a troll tooth", "hermit.troll_tooth", "extracted", AutopilotObjective.Wander(), minimumLevel = 9),
            AutopilotQuestStep("bring the hermit the tooth", "hermit.troll_tooth", "delivered", AutopilotObjective.TalkTo("Marcus")),
            AutopilotQuestStep(
                "take the Fire Shield",
                "hermit.troll_tooth",
                "fire_shield_acquired",
                AutopilotObjective.Reach("hermit_dungeon"),
                minimumLevel = 13,
                torches = 21,
            ),
            AutopilotQuestStep(
                "trade Leander a tent for the ogre lair",
                "jazzy.ogre",
                "accepted",
                AutopilotObjective.TalkTo("Leander", answers = listOf("Tent", "Yes", "Accept")),
                carry = "tent",
            ),
            AutopilotQuestStep(
                "clear the ogre cave",
                "jazzy.ogre",
                "ogres_cleared",
                AutopilotObjective.Reach("beast_cave"),
                minimumLevel = 14,
                freeSlots = 2,
            ),
            AutopilotQuestStep(
                "take the Foot of Jazzy",
                "jazzy.ogre",
                "jazzy_defeated",
                AutopilotObjective.Wander(terrain = "foothills"),
                // Jazzy's Cloud of Fire burns every member for about (580 + 3 x leader level) / 5 points right after his
                // minions' fight, so the mage needs the hit points of a level well past 20 to live through one.
                minimumLevel = 24,
            ),
            AutopilotQuestStep(
                "buy Bernak's map",
                "silver_key",
                "map_bought",
                AutopilotObjective.TalkTo("Bernak"),
                gold = mapGold(50_000),
            ),
            AutopilotQuestStep(
                "find the note in the Silver Sanctum",
                "silver_key",
                "note_found",
                AutopilotObjective.Reach("silver_key_pit"),
                torches = 21,
            ),
            AutopilotQuestStep("squeeze Gotharp", "silver_key", "thief_squeezed", AutopilotObjective.TalkTo("Gotharp")),
            AutopilotQuestStep("hear the smith confess", "silver_key", "smith_confessed", AutopilotObjective.TalkTo("Azaaza")),
            AutopilotQuestStep("catch the hobgoblin caravan", "silver_key", "cave_located", AutopilotObjective.Wander(), minimumLevel = 13),
            AutopilotQuestStep(
                "take the Silver Key from the hobgoblin stronghold",
                "silver_key",
                "key_taken",
                AutopilotObjective.Reach("hobgoblin_cave"),
                minimumLevel = 15,
                torches = 23,
            ),
            AutopilotQuestStep(
                "buy Harry's map to the temple of Thun",
                "bracelets.thun",
                "map_bought",
                AutopilotObjective.TalkTo("Harry"),
                gold = mapGold(10_000),
                gems = 5,
            ),
            AutopilotQuestStep(
                "take the Bracelets of Thun",
                "bracelets.thun",
                "bracelets_taken",
                AutopilotObjective.Reach("thun_temple"),
                torches = 19,
                gems = 5,
            ),
            // Erethor's temple at 6,20 ends in four fights in a row, two of them with Erethor, whose spells strike for about
            // 190 points, so the party goes last, at its strongest. Kaal rewards the lute with the Cloak of Gods.
            AutopilotQuestStep("hear Tybalt on Erethor's lute", "erethor.lute", "rumor_told", AutopilotObjective.TalkTo("Tybalt")),
            AutopilotQuestStep(
                "take Erethor's lute",
                "erethor.lute",
                "lute_taken",
                AutopilotObjective.ReachPoint(GridPoint(6, 20)),
                minimumLevel = 24,
                torches = 14,
            ),
            AutopilotQuestStep("give Tybalt the lute", "erethor.lute", "note_received", AutopilotObjective.TalkTo("Tybalt")),
            AutopilotQuestStep("bring Kaal Tybalt's note", "erethor.lute", "cloak_received", AutopilotObjective.TalkTo("Kaal")),
        )
}
