/**
 * Plans tell the autopilot what to do whenever the game waits for a command:
 * which action to take in the wilderness or a dungeon, which errands to run in a
 * town, and how to answer the questions only the plan can judge.
 */

package rylween.runtime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledHandleRef
import rylween.compiled.ExternalRuntimeCastle
import rylween.compiled.ExternalRuntimeSystems
import rylween.dsl.impl.GridPoint

internal interface AutopilotPlan {
    /** Whether the plan's goal is reached; the autopilot then saves in the wilderness and ends the run. */
    fun done(state: RuntimeState): Boolean

    /** The next action at the wilderness command prompt. */
    fun wilderness(state: RuntimeState): AutopilotAction

    /** The next action at the dungeon command prompt. */
    fun dungeon(state: RuntimeState): AutopilotAction

    /** An action to take at a castle floor's command prompt before walking on, or `null` to walk on. */
    fun castle(state: RuntimeState): AutopilotAction? = null

    /**
     * Whether to give the stair guardian an item and climb on, or to teleport out of the castle and come back stronger.
     * [lastFloor] is the climb to the final battle.
     */
    fun climbCastle(
        state: RuntimeState,
        lastFloor: Boolean,
    ): Boolean = true

    /** The errands to run in the town the party has just entered. */
    fun townErrands(state: RuntimeState): List<AutopilotErrand> = emptyList()

    /** The member and item to ready at a "ready any weapons/armors before leaving?" prompt, or `null` to decline. */
    fun readyOnLeave(
        state: RuntimeState,
        gear: AutopilotGear,
    ): Pair<AutopilotMember, String>? = null

    /** The choice for a scene question the autopilot's own rules do not settle, or `null` when the plan has none. */
    fun sceneChoice(
        view: AutopilotView,
        menu: AutopilotMenu,
    ): Char? = null

    /** The text to type at a text prompt, such as a riddle answer, or `null` when the plan has none. */
    fun answer(view: AutopilotView): String? = null

    /** An inventory item the plan can do without, which the tour may drop to try the drop command, or `null`. */
    fun dispensable(state: RuntimeState): String? = null

    /** Sees every screen before the autopilot answers it, for plans that read what the game shows, such as the journal. */
    fun observe(view: AutopilotView) = Unit

    /** Whether the mage saves spell points for a coming fight, casting only when a battle turns dangerous. */
    fun conserveMana(state: RuntimeState): Boolean = false
}

/**
 * Descends to the bottom of a dungeon, where its quest events wait, then climbs back out. Every level down burns a
 * torch, so the party turns back early when the torches run out, and it turns back for good once [retreat] says the
 * party is too weak to go deeper.
 */
internal class AutopilotDungeonCrawl(
    private val actions: AutopilotActions,
    private val retreat: (RuntimeState) -> Boolean = { false },
) {
    private var visit: CompiledHandleRef? = null
    private var descending = true

    fun next(state: RuntimeState): AutopilotAction {
        val dungeon = checkNotNull(state.dungeon) { "dungeon command prompt without a dungeon" }
        if (visit != dungeon.dungeon) {
            visit = dungeon.dungeon
            descending = true
        }
        if (dungeon.depth >= dungeon.maxDepth || state.party.resources.torches <= 0 || retreat(state)) descending = false
        return if (descending) actions.descend() else actions.ascend()
    }

    /** Forgets the current dungeon, so the next one starts with a descent. */
    fun left() {
        visit = null
    }
}

/** Walks to [castle]'s entrance from wherever the party is, camping when tired, and climbs it to the end. */
internal class AutopilotEndgamePlan(
    private val content: CompiledContentBundle,
    systems: ExternalRuntimeSystems,
    private val castle: ExternalRuntimeCastle,
) : AutopilotPlan {
    private val party = AutopilotParty(content)
    private val actions = AutopilotActions(systems)
    private val navigation = AutopilotNavigation(content)
    private val crawl = AutopilotDungeonCrawl(actions)
    private val campFoodPerMember = systems.realm.camp.foodCostPerMember
    private val victory: CompiledHandleRef? = castle.completionMilestone?.let { slug -> content.ref(CompiledHandleKind.Milestone, slug) }

    override fun done(state: RuntimeState): Boolean = victory != null && victory in state.party.milestones

    override fun wilderness(state: RuntimeState): AutopilotAction {
        crawl.left()
        val living = party.living(state.party)
        if (state.party.resources.restedHours <= 0 && state.party.resources.food >= campFoodPerMember * living.size) {
            return actions.camp(living.first())
        }
        return walkTo(state, castle.entry) ?: actions.enter()
    }

    override fun dungeon(state: RuntimeState): AutopilotAction = crawl.next(state)

    private fun walkTo(
        state: RuntimeState,
        target: GridPoint,
    ): AutopilotAction? {
        if (state.party.position == target) return null
        val step =
            navigation.stepTowards(state.currentMap, state.party.position, target)
                ?: throw AutopilotStuck("no walkable path to ${target.x},${target.y}")
        return actions.move(step)
    }
}
