/**
 * Decision rules for autopilot playthroughs.
 *
 * The autopilot answers prompts the way a careful player would: it fights every
 * battle, attacks the first living monster with each member, camps with a guard
 * when the party is tired, and walks towards its goal. Prompts are recognized
 * from what the runtime shows, using the bundle's own key bindings and text
 * where possible. An unrecognized prompt fails the run instead of guessing, so a
 * changed game flow shows up as a clear test failure.
 */

package rylween.runtime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledHandleKind
import rylween.compiled.ExternalRuntimeCastle
import rylween.compiled.ExternalRuntimeCommand
import rylween.compiled.ExternalRuntimeInputBinding
import rylween.compiled.ExternalRuntimeSystems
import rylween.compiled.RuntimeCommandActionId
import rylween.compiled.RuntimeCommandGroupId
import rylween.dsl.impl.CombatActionKind
import rylween.dsl.impl.GridPoint
import rylween.dsl.impl.RuntimeMode

/** What an autopilot run is trying to achieve. */
internal sealed interface AutopilotGoal {
    /** Walk to [castleId]'s entrance, enter it, and climb every floor until the castle is completed. */
    data class CompleteCastle(
        val castleId: String,
    ) : AutopilotGoal
}

/** Chooses each key of a session played towards [goal], from the bundle's [content] and runtime [systems]. */
internal class Autopilot(
    private val content: CompiledContentBundle,
    private val systems: ExternalRuntimeSystems,
    private val goal: AutopilotGoal,
) {
    private val battle = checkNotNull(systems.combat.standardBattle) { "bundle has no standard battle rules" }
    private val attackKey =
        battle.actions
            .first { action -> action.kind == CombatActionKind.Attack }
            .binding
            .key()
    private val wildernessKeys = systems.commands.getValue(RuntimeCommandGroupId.Wilderness.id)
    private var camp = CampStep.None
    private var saved = false
    private var lastSignature: String? = null
    private var repeats = 0

    /**
     * Returns the next key for [view], or `null` when the run is over: the game ended, or the goal is
     * reached and the party has been saved back in the wilderness.
     */
    fun decide(view: AutopilotView): Char? {
        val state = view.state
        if (state.currentMode == RuntimeMode.GameOver || saved) return null
        if (goalReached(state) && state.currentMode == RuntimeMode.Wilderness && view.request == AutopilotRequest.Key) {
            saved = true
            return wildernessKeys.key(RuntimeCommandActionId.Save)
        }
        val key =
            pausePrompt(view)
                ?: when (state.currentMode) {
                    RuntimeMode.Battle -> battle(view)
                    RuntimeMode.Castle -> castle(view)
                    RuntimeMode.Wilderness -> wilderness(view)
                    else -> null
                }
                ?: stuck("no rule for a ${state.currentMode} prompt", view)
        guardAgainstLoops(view, key)
        return key
    }

    private fun goalReached(state: RuntimeState): Boolean =
        when (goal) {
            is AutopilotGoal.CompleteCastle ->
                castleRow(goal.castleId).completionMilestone?.let { slug ->
                    content.ref(CompiledHandleKind.Milestone, slug) in state.party.milestones
                } ?: false
        }

    /** Pages that only continue on RETURN end in a "(<verb> RETURN key)" line or a bare RETURN label. */
    private fun pausePrompt(view: AutopilotView): Char? {
        val last = view.text.lastOrNull(String::isNotBlank)?.trim() ?: return null
        val returnName = systems.prompts.returnKeyName
        val isPause =
            (last.startsWith("(") && last.endsWith(")") && returnName in last) ||
                last == systems.prompts.returnKeyLabel()
        return '\n'.takeIf { isPause }
    }

    private fun battle(view: AutopilotView): Char? {
        val lines = view.text
        return when {
            lines.offers(battle.openingKeys.fight) && lines.offers(battle.openingKeys.run) -> battle.openingKeys.fight.key()
            lines.any { line -> line.offersChoiceKey(attackKey.toString()) } -> attackKey
            else -> lines.firstNotNullOfOrNull(::targetKey)
        }
    }

    /** The letter of a battle target line such as "A. Xvart 159/159 HP"; dead monsters are listed without one. */
    private fun targetKey(line: String): Char? = TARGET_LINE.find(line)?.let { match -> match.groupValues[1].single() }

    private fun castle(view: AutopilotView): Char? {
        val state = view.state
        val castleState = state.castle ?: return null
        val castle = castleRow(castleState.castleId)
        val stairs = systems.commands.getValue(RuntimeCommandGroupId.castleStairs(castle.id))
        val treasure = systems.commands.getValue(RuntimeCommandGroupId.castleTreasure(castle.id))
        val lines = view.text
        return when {
            lines.offers(stairs.binding(RuntimeCommandActionId.GiveItem)) -> stairs.key(RuntimeCommandActionId.GiveItem)
            lines.any { line -> line.trimStart().startsWith("1.") } -> stairsItemDigit(state, castle)
            lines.offers(treasure.binding(RuntimeCommandActionId.Take)) -> treasure.key(RuntimeCommandActionId.Leave)
            else -> castleStep(castleState, castle)
        }
    }

    /** Offers the guardian the first artifact whose milestone the party still holds. */
    private fun stairsItemDigit(
        state: RuntimeState,
        castle: ExternalRuntimeCastle,
    ): Char? =
        castle.stairs.items
            .firstOrNull { item -> content.ref(CompiledHandleKind.Milestone, item.milestone) in state.party.milestones }
            ?.digit
            ?.digitToChar()

    /** Heads for the stairs in the far corner: east along the first row, then south. */
    private fun castleStep(
        state: CastleState,
        castle: ExternalRuntimeCastle,
    ): Char {
        val navigation = systems.commands.getValue(RuntimeCommandGroupId.castleNavigation(castle.id))
        return if (state.point.x < castle.mapSize) {
            navigation.key(RuntimeCommandActionId.MoveEast)
        } else {
            navigation.key(RuntimeCommandActionId.MoveSouth)
        }
    }

    private fun wilderness(view: AutopilotView): Char? {
        val state = view.state
        when (camp) {
            CampStep.PostGuard -> {
                camp = CampStep.ChooseGuard
                return 'Y'
            }

            CampStep.ChooseGuard -> {
                camp = CampStep.None
                return (state.party.members.indexOfFirst { member -> !member.dead } + 1).digitToChar()
            }

            CampStep.None -> Unit
        }
        if (view.request != AutopilotRequest.Key) return null
        if (needsCamp(state)) {
            camp = CampStep.PostGuard
            return wildernessKeys.key(RuntimeCommandActionId.Camp)
        }
        val target =
            when (goal) {
                is AutopilotGoal.CompleteCastle -> castleRow(goal.castleId).entry
            }
        if (state.party.position == target) return wildernessKeys.key(RuntimeCommandActionId.Enter)
        return wildernessStep(state, target) ?: stuck("no walkable path to ${target.x},${target.y}", view)
    }

    /**
     * Camps once the party has no rested hours left and can pay for the food. A guard is always
     * posted: an unguarded camp's ambush kills a sleeping member before the party can react.
     */
    private fun needsCamp(state: RuntimeState): Boolean {
        val living = state.party.members.count { member -> !member.dead }
        return state.party.resources.restedHours <= 0 && state.party.resources.food >= systems.realm.camp.foodCostPerMember * living
    }

    /** First move of a shortest walkable path from the party's position to [target]. */
    private fun wildernessStep(
        state: RuntimeState,
        target: GridPoint,
    ): Char? {
        val world = WorldMap.from(content, state.currentMap)
        val start = state.party.position
        val firstStep = mutableMapOf<GridPoint, Char>()
        val queue = ArrayDeque(listOf(start))
        val seen = mutableSetOf(start)
        while (queue.isNotEmpty()) {
            val point = queue.removeFirst()
            if (point == target) return firstStep[point]
            WILDERNESS_MOVES.forEach { (action, dx, dy) ->
                val next = GridPoint(point.x + dx, point.y + dy)
                if (next !in seen && world.terrainAt(next)?.passable == true) {
                    seen += next
                    firstStep[next] = firstStep[point] ?: wildernessKeys.key(action)
                    queue.addLast(next)
                }
            }
        }
        return null
    }

    /** Fails when the same key keeps answering an unchanged screen, which means the runtime ignores it. */
    private fun guardAgainstLoops(
        view: AutopilotView,
        key: Char,
    ) {
        val state = view.state
        val signature =
            "$key|${state.currentMode}|${state.party.position}|${state.castle?.floor}|${state.castle?.point}|${view.text}"
        repeats = if (signature == lastSignature) repeats + 1 else 0
        lastSignature = signature
        if (repeats >= LOOP_LIMIT) stuck("the runtime ignored '$key' $LOOP_LIMIT times in a row", view)
    }

    private fun castleRow(castleId: String): ExternalRuntimeCastle =
        systems.castleRows.firstOrNull { castle -> castle.id == castleId } ?: error("bundle has no castle $castleId")

    private fun stuck(
        reason: String,
        view: AutopilotView,
    ): Nothing = throw AutopilotStuck("$reason; current prompt: ${view.prompt.filter(String::isNotBlank)}")

    /** The camp dialogue asks whether to post a guard, then which member stands guard. */
    private enum class CampStep { None, PostGuard, ChooseGuard }

    private companion object {
        const val LOOP_LIMIT = 20

        val TARGET_LINE = Regex("""^\s*([A-Z])\.\s.*\d+/\d+ HP""")

        val WILDERNESS_MOVES =
            listOf(
                Triple(RuntimeCommandActionId.MoveNorth, 0, -1),
                Triple(RuntimeCommandActionId.MoveSouth, 0, 1),
                Triple(RuntimeCommandActionId.MoveWest, -1, 0),
                Triple(RuntimeCommandActionId.MoveEast, 1, 0),
            )
    }
}

private fun ExternalRuntimeInputBinding.key(): Char = primary.single()

private fun List<String>.offers(binding: ExternalRuntimeInputBinding): Boolean = any { line -> line.offersChoiceKey(binding.primary) }

private fun List<ExternalRuntimeCommand>.binding(action: RuntimeCommandActionId): ExternalRuntimeInputBinding =
    first { command -> command.action == action.id }.binding

private fun List<ExternalRuntimeCommand>.key(action: RuntimeCommandActionId): Char = binding(action).key()
