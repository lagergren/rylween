/**
 * Decision rules for autopilot playthroughs.
 *
 * The autopilot answers prompts the way a careful player would. Rules that hold
 * everywhere come first: pause pages, battles, and actions already under way.
 * Whatever is left is a command prompt or a scene question, which the
 * [AutopilotPlan] settles: the next wilderness or dungeon action, the errands
 * to run in a town, and the choices only a plan can judge. Prompts are
 * recognized from what the runtime shows, using the bundle's own key bindings
 * and text where possible. An unrecognized prompt fails the run instead of
 * guessing, so a changed game flow shows up as a clear test failure.
 */

package rylween.runtime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledHandleKind
import rylween.compiled.ExternalRuntimeCastle
import rylween.compiled.ExternalRuntimeCommand
import rylween.compiled.ExternalRuntimeInputBinding
import rylween.compiled.ExternalRuntimeSystems
import rylween.compiled.ExternalStandardBattle
import rylween.compiled.RuntimeCommandActionId
import rylween.compiled.RuntimeCommandGroupId
import rylween.dsl.impl.CombatActionKind
import rylween.dsl.impl.PcClass
import rylween.dsl.impl.RuntimeMode

/** What the autopilot has done so far, for tests that check a run used a game feature. */
internal class AutopilotStats {
    var priestSpells: Int = 0
    var mageSpells: Int = 0
    var battleItems: Int = 0
    var hunts: Int = 0
    var townVisits: Int = 0
}

/**
 * Chooses each key of a session played by [plan], from the bundle's [content] and runtime [systems]. With [creation],
 * the session starts at the title menu and the autopilot rolls a new party first.
 */
internal class Autopilot(
    private val content: CompiledContentBundle,
    private val systems: ExternalRuntimeSystems,
    private val plan: AutopilotPlan,
    private val creation: AutopilotCreation? = null,
    /** What the run has used of everything the game offers; a later session of the same run passes it on. */
    val coverage: AutopilotCoverage = AutopilotCoverage(content, systems),
    private val title: AutopilotTitleTour? = null,
) {
    val stats = AutopilotStats()

    private val tour = AutopilotTour(content, systems, coverage)

    // Monsters cast spells of the same names, such as Frost Finger, so only the party's spells are looked up by name.
    private val spellSlugs =
        content.spells
            .filterNot { spell -> content.slug(spell.id).startsWith(MONSTER_SPELLS) }
            .associate { spell -> content.strings.value(spell.nameString) to content.slug(spell.id) }
    private val party = AutopilotParty(content)
    private val battle = checkNotNull(systems.combat.standardBattle) { "bundle has no standard battle rules" }
    private val attackKey = battle.actionKey(CombatActionKind.Attack)
    private val castKey = battle.actionKey(CombatActionKind.CastMageSpell)
    private val useItemKey = battle.actionKey(CombatActionKind.UseMagicItem)
    private val defendKey = battle.actionKey(CombatActionKind.Defend)
    private val battleCryKey = battle.actionKey(CombatActionKind.BattleCry)
    private val tactics = AutopilotBattle(content, systems)
    private val wildernessKeys = systems.commands.getValue(RuntimeCommandGroupId.Wilderness.id)
    private var action: AutopilotAction? = null
    private var town: AutopilotTownVisit? = null
    private var pendingSpell: String? = null
    private var pendingItem: AutopilotItemUse? = null
    private var saved = false
    private var latest: RuntimeState? = null
    private var lastSignature: String? = null

    /** The latest screen lines, so a text prompt can be matched to a question asked on the page before it. */
    private val recentLines = ArrayDeque<String>()
    private var repeats = 0

    /** Whether the goal is reached and saved, so no further session is needed. */
    val finished: Boolean get() = saved

    /** Whether the day from [start] to [end] went so badly that the run should start it over from the last save. */
    fun setback(
        start: RuntimeState,
        end: RuntimeState,
    ): Boolean = plan.setback(start, end)

    /** Forgets what was under way when the party fell, before the run starts over from the last save. */
    fun reload() {
        action = null
        town = null
        pendingSpell = null
        pendingItem = null
        recentLines.clear()
        lastSignature = null
        repeats = 0
        refreshNeeded = false
    }

    /**
     * Returns the next key for [view], or `null` when the run is over: the game ended, or the goal is
     * reached and the party has been saved back in the wilderness.
     */
    fun decide(view: AutopilotView): Char? {
        val state = view.state
        latest = state
        remember(view.text)
        plan.observe(view)
        view.text
            .firstNotNullOfOrNull { line ->
                antechamberPattern.find(line)
            }?.let { match -> antechamber = match.groupValues[1].toInt() }
        if (state.currentMode == RuntimeMode.GameOver || saved) return null
        if (plan.done(state) && state.currentMode == RuntimeMode.Wilderness && view.request == AutopilotRequest.Key) {
            saved = true
            return wildernessKeys.key(RuntimeCommandActionId.Save)
        }
        val key =
            title?.key(view)
                ?: creation?.key(view)
                ?: pausePrompt(view)
                ?: dayEnd(view)
                ?: battleKey(view)
                ?: action?.next(view)
                ?: townKey(view)
                ?: when (state.currentMode) {
                    RuntimeMode.Castle -> castle(view)
                    RuntimeMode.Dungeon -> scene(view) ?: command(view) { plan.dungeon(state) }
                    RuntimeMode.Wilderness -> scene(view) ?: command(view) { plan.wilderness(state) }
                    else -> null
                }
                ?: stuck("no rule for a ${state.currentMode} prompt", view)
        guardAgainstLoops(view, key)
        recordChoice(view, key)
        return key
    }

    /**
     * Returns the text to type for a text prompt, such as a riddle answer, or `null` when the autopilot has none. A
     * question may sit on a page that only continues on RETURN ("What's the password?") before a bare "Password:"
     * prompt, so the plan sees the recent lines as part of the prompt.
     */
    fun answer(view: AutopilotView): String? {
        remember(view.text)
        return creation?.answer(view) ?: plan.answer(view.copy(prompt = recentLines.toList()))
    }

    private fun remember(lines: List<String>) {
        recentLines.addAll(lines.filter(String::isNotBlank))
        while (recentLines.size > RECENT_LINES) recentLines.removeFirst()
    }

    /**
     * Pages that only continue on RETURN end in a "(<verb> RETURN key)" line or a bare RETURN label, and paged
     * documents such as the journal end in their own "(ENTER: more, ESC: exit)" prompt.
     */
    private fun pausePrompt(view: AutopilotView): Char? {
        val last = view.text.lastOrNull(String::isNotBlank)?.trim() ?: return null
        val returnName = systems.prompts.returnKeyName
        val isPause =
            (last.startsWith("(") && last.endsWith(")") && returnName in last) ||
                last == systems.prompts.returnKeyLabel() ||
                isRuntimeDocumentPrompt(last)
        return '\n'.takeIf { isPause }
    }

    /**
     * Ends the day once the party has no moves left: saves and quits. The first time, it quits without saving instead,
     * so that day starts over from its own beginning.
     */
    private fun dayEnd(view: AutopilotView): Char? {
        if (view.text.none { line -> OUT_OF_MOVES in line }) return null
        action = null
        val group = RuntimeCommandGroupId.WildernessOutOfMoves.id
        val choice = if (coverage.used(group, RuntimeCommandActionId.Quit.id)) RuntimeCommandActionId.Save else RuntimeCommandActionId.Quit
        return systems.commands.getValue(group).key(choice)
    }

    private fun battleKey(view: AutopilotView): Char? {
        if (view.state.currentMode != RuntimeMode.Battle) return null
        val lines = view.text
        tactics.observe(lines)
        pendingSpell?.let { spell ->
            pendingSpell = null
            val menu = AutopilotMenu.parse(lines)
            return menu.find(spell)?.key ?: menu.find("Leave")?.key
        }
        itemKey(lines)?.let { key -> return key }
        return when {
            lines.offers(battle.openingKeys.fight) && lines.offers(battle.openingKeys.run) -> {
                val foes = tactics.currentFoes
                val fight = foes == null || tactics.escapeHopeless || tactics.shouldFight(view.state.party, foes)
                (if (fight) battle.openingKeys.fight else battle.openingKeys.run).key()
            }

            lines.any { line -> line.offersChoiceKey(attackKey.toString()) } -> memberAction(view)
            else -> tactics.weakestTarget(lines) ?: stuck("no rule for a battle prompt", view)
        }
    }

    /**
     * Answers the prompts of a battle item use: the item from the "Use what?" list, then the member for items that ask
     * "Who?". An item missing from the list ends the use with RETURN, which the battle takes as no action.
     */
    private fun itemKey(lines: List<String>): Char? {
        val use = pendingItem ?: return null
        return when {
            lines.any { line -> line.startsWith("Use what?") } -> {
                if (use.target == null) pendingItem = null
                AutopilotMenu.parse(lines).findInventoryItem(use.item)?.key ?: '\n'.also { pendingItem = null }
            }

            lines.any { line -> line.startsWith("Who?") || line.startsWith("On Whom?") } -> {
                pendingItem = null
                use.target?.digit
            }

            else -> null.also { pendingItem = null }
        }
    }

    /**
     * A member's turn. In a fight the party cannot flee or would not choose, a magic item that helps comes first.
     * Otherwise a mage casts when a spell pays off and everyone else attacks. While the plan saves the mage's spell
     * points, the mage casts only in such fights.
     */
    private fun memberAction(view: AutopilotView): Char {
        val lines = view.text
        if (tactics.escapeHopeless || dangerous(view.state.party)) {
            tactics.battleItem(view.state.party)?.let { use ->
                pendingItem = use
                stats.battleItems++
                coverage.record("battle", CombatActionKind.UseMagicItem.name)
                return useItemKey
            }
        }
        val name = lines.getOrNull(lines.indexOfFirst { line -> line.trim() == "What now?" } - 1)?.trim()
        val actor = name?.let { party.named(view.state.party, it) }
        val mage =
            actor?.takeIf { member ->
                member.member.pcClass == PcClass.Mage &&
                    lines.any { line -> line.offersChoiceKey(castKey.toString()) }
            }
        if (!tactics.escapeHopeless && !dangerous(view.state.party)) {
            val caster = mage?.let { member -> AutopilotCaster(member.member.level, member.member.mana.current) }
            when (val choice = tour.battleAction(tactics.currentFoes, caster)) {
                is AutopilotBattleTour.Action -> {
                    coverage.record("battle", choice.kind.name)
                    return if (choice.kind == CombatActionKind.Defend) defendKey else battleCryKey
                }
                is AutopilotBattleTour.Spell -> return cast(choice.name)
                null -> Unit
            }
        }
        val spell =
            mage
                ?.takeIf { !plan.conserveMana(view.state) || tactics.escapeHopeless || dangerous(view.state.party) }
                ?.let { member -> tactics.mageSpell(member.member, view.state.party) }
        if (spell == null) return attackKey.also { coverage.record("battle", CombatActionKind.Attack.name) }
        return cast(spell)
    }

    /** Has the acting mage cast [spell]; the spell menu that follows is answered from [pendingSpell]. */
    private fun cast(spell: String): Char {
        pendingSpell = spell
        stats.mageSpells++
        coverage.record("battle", CombatActionKind.CastMageSpell.name)
        spellSlugs[spell]?.let { slug -> coverage.record("spell", slug) }
        return castKey
    }

    private fun dangerous(party: PartyState): Boolean =
        tactics.currentFoes?.let { foes -> !tactics.shouldFight(party, foes, acceptableShare = DANGEROUS_SHARE) } ?: true

    /**
     * Routes town screens to the town visit. Entering a town shows its tax prompt or menu before the runtime redraws
     * the state, so a visit starts from what the screen offers and lasts until it has left the town menu and no
     * longer recognizes the prompt.
     */
    private fun townKey(view: AutopilotView): Char? {
        val menu = AutopilotMenu.parse(view.text)
        val entering =
            menu.find("Pay to Enter") != null ||
                (menu.find("Leave") != null && listOf("Merchant", "Inn", "Smith").any { service -> menu.find(service) != null })
        val visit =
            town ?: if (entering || view.state.currentMode == RuntimeMode.Town) startTownVisit(view.state) else return null
        visit.respond(view)?.let { key -> return key }
        if (!visit.left) stuck("no rule for a town prompt", view)
        town = null
        return null
    }

    private fun startTownVisit(state: RuntimeState): AutopilotTownVisit =
        AutopilotTownVisit(
            errands =
                (plan.townErrands(state) + tour.townErrands(state)).filter { errand ->
                    content.serviceOpenAt(errand.service, state.hour)
                },
            readyOnLeave = { gear -> plan.readyOnLeave(latest ?: state, gear) },
            coverage = coverage,
            party = party,
        ).also { visit ->
            town = visit
            stats.townVisits++
        }

    /** Starts the plan's next action at a command prompt. */
    private fun command(
        view: AutopilotView,
        next: () -> AutopilotAction,
    ): Char? {
        if (view.request != AutopilotRequest.Key) return null
        // Wilderness utilities such as readying gear or casting do not redraw the screen, so the state the autopilot
        // last saw is stale until the game redraws it.
        if (refreshNeeded && view.state.currentMode == RuntimeMode.Wilderness) {
            refreshNeeded = false
            coverage.record(RuntimeCommandGroupId.Wilderness.id, RuntimeCommandActionId.Redraw.id)
            return wildernessKeys.key(RuntimeCommandActionId.Redraw)
        }
        val group =
            if (view.state.currentMode ==
                RuntimeMode.Dungeon
            ) {
                RuntimeCommandGroupId.Dungeon.id
            } else {
                RuntimeCommandGroupId.Wilderness.id
            }
        val touring = view.state.currentMode == RuntimeMode.Dungeon || view.state.party.resources.timeLeft > TOUR_MOVES
        val started = tour.commandStop(view.state, group, plan.dispensable(view.state)).takeIf { touring } ?: next()
        guardAgainstRepeatedActions(started, view.state, view)
        refreshNeeded = !started.name.startsWith("move ")
        return begin(started, view, group)
    }

    /** Starts [started] at a command prompt of [group], counting what it uses, and returns its first key. */
    private fun begin(
        started: AutopilotAction,
        view: AutopilotView,
        group: String,
    ): Char? {
        action = started
        when {
            started.name.startsWith("cast ") -> {
                stats.priestSpells++
                spellSlugs[started.name.removePrefix("cast ").substringBefore(" with")]?.let { slug -> coverage.record("spell", slug) }
            }
            started.name.startsWith("hunt ") -> stats.hunts++
        }
        return started.next(view)?.also { key -> coverage.recordKey(group, key) }
    }

    /**
     * Answers a question a mission scene, a dungeon event, or a random wilderness event asks. Unrecognized
     * questions go to the plan; with no answer there either, the run fails.
     */
    private fun scene(view: AutopilotView): Char? {
        val menu = AutopilotMenu.parse(view.text)
        val text = view.text.joinToString(" ")
        return when {
            menu.find("Red Square") != null -> colorRiddle(view) ?: stuck("the color riddle names no antechamber", view)
            // A full inventory offers to drop something for a reward; RETURN declines and keeps the inventory.
            "Drop an item?" in text -> '\n'
            menu.isEmpty() -> null
            "You find a cave!" in text ->
                (plan.sceneChoice(view, menu) ?: menu.find(if (tour.wantsCave(view.state)) "Enter" else "Leave")?.key)?.also { key ->
                    coverage.recordKey(RuntimeCommandGroupId.WildernessCave.id, key)
                }
            menu.find("Take it") != null -> (if (party.freeSlots(view.state.party) > 1) menu.find("Take it") else menu.find("Leave"))?.key
            // Chests can be trapped, so only a healthy party opens one.
            menu.find("Open chest") != null -> (if (healthy(view.state)) menu.find("Open chest") else menu.find("Leave"))?.key
            else -> plan.sceneChoice(view, menu) ?: defaultSceneChoice(menu) ?: stuck("no rule for a scene question", view)
        }
    }

    /**
     * The safe square of the color riddle: the dungeon level plus the square must make 20, and the plaque names
     * antechamber N on level 12 + N. The event runs before the runtime redraws the new level, so the plaque, not the
     * state, tells the level.
     */
    private fun colorRiddle(view: AutopilotView): Char? =
        antechamber?.let { number -> (COLOR_RIDDLE_SUM - COLOR_RIDDLE_FIRST_LEVEL - number).coerceIn(1, 7).digitToChar() }

    /** The antechamber number from the color riddle's plaque, which a pause page shows before the question. */
    private var antechamber: Int? = null

    private fun healthy(state: RuntimeState): Boolean =
        party.living(state.party).all { member -> member.hitPointShare > HEALTHY_SHARE } && party.dead(state.party).isEmpty()

    /** Mission scenes ask to go ahead; the plan only walks onto a mission when it is ready for it. */
    private fun defaultSceneChoice(menu: AutopilotMenu): Char? =
        listOf("Enter", "Yes", "Attack", "Fight").firstNotNullOfOrNull { label -> menu.find(label) }?.key

    private fun castle(view: AutopilotView): Char? {
        val state = view.state
        val castleState = state.castle ?: return null
        val castle = castleRow(castleState.castleId)
        val stairs = systems.commands.getValue(RuntimeCommandGroupId.castleStairs(castle.id))
        val treasure = systems.commands.getValue(RuntimeCommandGroupId.castleTreasure(castle.id))
        val navigation = systems.commands.getValue(RuntimeCommandGroupId.castleNavigation(castle.id))
        val lines = view.text
        val stairsGroup = RuntimeCommandGroupId.castleStairs(castle.id)
        val treasureGroup = RuntimeCommandGroupId.castleTreasure(castle.id)
        val navigationGroup = RuntimeCommandGroupId.castleNavigation(castle.id)
        when {
            lines.offers(stairs.binding(RuntimeCommandActionId.GiveItem)) -> {
                val climb = plan.climbCastle(state, lastFloor = castleState.floor == castle.stairs.floorCount)
                val choice = if (climb) tour.stairsChoice() ?: RuntimeCommandActionId.GiveItem else RuntimeCommandActionId.TeleportOut
                val key = stairs.key(choice)
                return key.also { coverage.recordKey(stairsGroup, it) }
            }
            lines.any { line -> line.trimStart().startsWith("1.") } -> return stairsItemDigit(state, castle)
            lines.offers(treasure.binding(RuntimeCommandActionId.Take)) -> {
                val take = party.freeSlots(state.party) >= TREASURE_FREE_SLOTS && !tour.leavesTreasure()
                val key = treasure.key(if (take) RuntimeCommandActionId.Take else RuntimeCommandActionId.Leave)
                return key.also { coverage.recordKey(treasureGroup, it) }
            }
        }
        // Between fights the plan may heal or revive first; castle utilities redraw the floor themselves.
        if (view.request == AutopilotRequest.Key) {
            (tour.commandStop(state, navigationGroup, plan.dispensable(state)) ?: plan.castle(state))?.let { started ->
                return begin(started, view, navigationGroup)
            }
        }
        return navigation.key(castleMove(castleState, castle.mapSize)).also { coverage.recordKey(navigationGroup, it) }
    }

    /**
     * The next step through a castle floor towards the stairs in the far corner: east along the first row, then south,
     * the shortest way through the rooms. Once, on its first floor, the party looks back west and north at rooms it
     * has already cleared before going on.
     */
    private fun castleMove(
        castle: CastleState,
        size: Int,
    ): RuntimeCommandActionId {
        val (x, y) = castle.point
        return when {
            !lookedWest && x == 2 && y == 1 -> RuntimeCommandActionId.MoveWest.also { lookedWest = true }
            !lookedNorth && y == 2 -> RuntimeCommandActionId.MoveNorth.also { lookedNorth = true }
            x < size -> RuntimeCommandActionId.MoveEast
            else -> RuntimeCommandActionId.MoveSouth
        }
    }

    private var lookedWest = false
    private var lookedNorth = false

    /**
     * Records the menu option [key] chose on [view]'s screen, for menus whose options are command labels, and the
     * answer to the end-of-day and quit questions, whose options are not.
     */
    private fun recordChoice(
        view: AutopilotView,
        key: Char,
    ) {
        val menu = AutopilotMenu.parse(view.text.ifEmpty { view.prompt })
        menu.options.firstOrNull { option -> option.key.equals(key, ignoreCase = true) }?.let { option ->
            coverage.recordLabel(option.label, menu.options.map { shown -> shown.label })
        }
        when {
            view.text.any { line -> OUT_OF_MOVES in line } -> coverage.recordKey(RuntimeCommandGroupId.WildernessOutOfMoves.id, key)
            view.text.any { line ->
                AutopilotActions.QUIT_QUESTION in line
            } -> coverage.recordKey(RuntimeCommandGroupId.WildernessQuitConfirm.id, key)
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

    /**
     * Fails when the plan keeps starting the same action without the game state changing, which means the action
     * achieves nothing, such as readying a weapon the runtime will not accept.
     */
    private fun guardAgainstRepeatedActions(
        started: AutopilotAction,
        state: RuntimeState,
        view: AutopilotView,
    ) {
        val p = state.party
        val signature =
            "${started.name}|${state.currentMode}|${p.position}|${state.dungeon?.depth}|${state.castle?.floor}|${state.castle?.point}|" +
                "${p.resources}|${p.members.map { member -> member.hitPoints }}|${p.inventory}|${p.flags.size}|" +
                "${p.members.map { member -> member.equipment }}"
        actionRepeats = if (signature == lastActionSignature) actionRepeats + 1 else 0
        lastActionSignature = signature
        if (actionRepeats >= ACTION_REPEAT_LIMIT) stuck("'${started.name}' changed nothing $ACTION_REPEAT_LIMIT times in a row", view)
    }

    private var refreshNeeded = false
    private var lastActionSignature: String? = null
    private var actionRepeats = 0

    private fun castleRow(castleId: String): ExternalRuntimeCastle =
        systems.castleRows.firstOrNull { castle -> castle.id == castleId } ?: error("bundle has no castle $castleId")

    private fun stuck(
        reason: String,
        view: AutopilotView,
    ): Nothing = throw AutopilotStuck("$reason; action: ${action ?: "none"}; current prompt: ${view.prompt.filter(String::isNotBlank)}")

    private companion object {
        const val LOOP_LIMIT = 20
        const val ACTION_REPEAT_LIMIT = 12
        const val COLOR_RIDDLE_SUM = 20
        const val COLOR_RIDDLE_FIRST_LEVEL = 12
        val antechamberPattern = Regex("""ANTECHAMBER #(\d+)""")
        const val HEALTHY_SHARE = 0.6
        const val TREASURE_FREE_SLOTS = 3
        const val MONSTER_SPELLS = "monster."
        const val OUT_OF_MOVES = "out of moves for today"

        /** Daily moves the tour leaves alone, so trying a command never strands the party at the end of a day. */
        const val TOUR_MOVES = 20

        /** A fight is dangerous once it is expected to cost more than this share of the party's hit points. */
        const val DANGEROUS_SHARE = 0.2
        const val RECENT_LINES = 12
    }
}

private fun ExternalRuntimeInputBinding.key(): Char = primary.single()

private fun ExternalStandardBattle.actionKey(kind: CombatActionKind): Char =
    actions
        .first { action -> action.kind == kind }
        .binding
        .key()

private fun List<String>.offers(binding: ExternalRuntimeInputBinding): Boolean = any { line -> line.offersChoiceKey(binding.primary) }

private fun List<ExternalRuntimeCommand>.binding(action: RuntimeCommandActionId): ExternalRuntimeInputBinding =
    first { command -> command.action == action.id }.binding
