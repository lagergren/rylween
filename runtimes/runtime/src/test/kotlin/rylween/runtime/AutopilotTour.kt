/**
 * Plays the parts of the game a careful player may never need, once each, so a
 * full run exercises everything the game offers: the utility commands in the
 * wilderness, in dungeons, and in the castle, every battle action and spell,
 * the castle's stair and treasure choices, and a cave. Each sight is taken at a
 * safe moment, and the run's [AutopilotCoverage] says which are still to see.
 */

package rylween.runtime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledWorldPosition
import rylween.compiled.ExternalRuntimeCommand
import rylween.compiled.ExternalRuntimeSystems
import rylween.compiled.RuntimeCommandActionId
import rylween.compiled.RuntimeCommandGroupId
import rylween.dsl.impl.CombatActionKind
import rylween.dsl.impl.PcClass
import rylween.dsl.impl.RuntimeMode

internal class AutopilotTour(
    private val content: CompiledContentBundle,
    private val systems: ExternalRuntimeSystems,
    private val coverage: AutopilotCoverage,
) {
    private val party = AutopilotParty(content)
    private val spellCosts = content.spells.associate { spell -> content.slug(spell.id) to spell.baseCost }
    private val spellNames = content.spells.associate { spell -> content.slug(spell.id) to content.strings.value(spell.nameString) }
    private val blank = content.items.firstOrNull { item -> content.slug(item.id) == BLANK }?.id

    /**
     * A utility command of [group] the run has not used yet, as an action to take now at its command prompt, or `null`
     * when there is none or the party is not safe enough. [dispensable] names an inventory item the plan can do
     * without, for the drop command.
     */
    fun commandStop(
        state: RuntimeState,
        group: String,
        dispensable: String?,
    ): AutopilotAction? {
        val commands = systems.commands[group] ?: return null
        val p = state.party
        if (party.dead(p).isNotEmpty() || party.living(p).any { member -> member.hitPointShare < SAFE_HEALTH }) return null

        fun pending(action: RuntimeCommandActionId): Boolean =
            commands.any { command -> command.action == action.id } && !coverage.used(group, action.id)

        return when {
            pending(RuntimeCommandActionId.Help) -> single(group, commands, RuntimeCommandActionId.Help)
            pending(RuntimeCommandActionId.ViewParty) -> viewParty(group, commands)
            pending(RuntimeCommandActionId.Pass) -> single(group, commands, RuntimeCommandActionId.Pass)
            pending(RuntimeCommandActionId.Save) -> single(group, commands, RuntimeCommandActionId.Save)
            pending(RuntimeCommandActionId.Drop) && dispensable != null -> drop(group, commands, dispensable)
            (pending(RuntimeCommandActionId.RemoveWeapon) || pending(RuntimeCommandActionId.ReadyWeapon)) ->
                swap(state, group, commands, AutopilotGear.Weapon)
            (pending(RuntimeCommandActionId.RemoveArmor) || pending(RuntimeCommandActionId.ReadyArmor)) ->
                swap(state, group, commands, AutopilotGear.Armor)
            else -> spellStop(state, group, commands, castPending = pending(RuntimeCommandActionId.CastSpell))
        }
    }

    /**
     * A battle action a member could take for the tour in a fight the party has well in hand: defending, a battle cry,
     * or, for a mage, a spell not yet cast on a foe it can affect. [mage] is the acting member when it is a mage.
     */
    fun battleAction(
        foes: AutopilotFoes?,
        mage: AutopilotCaster?,
    ): AutopilotBattleTour? {
        if (!coverage.used("battle", CombatActionKind.Defend.name)) return AutopilotBattleTour.Action(CombatActionKind.Defend)
        if (!coverage.used("battle", CombatActionKind.BattleCry.name)) return AutopilotBattleTour.Action(CombatActionKind.BattleCry)
        mage ?: return null
        if (foes?.monster?.immune == true) return null
        return mageSpells
            .firstOrNull { slug -> !coverage.used(SPELL, slug) && mage.mana >= (spellCosts[slug] ?: return null) + mage.level }
            ?.let { slug -> AutopilotBattleTour.Spell(spellNames.getValue(slug)) }
    }

    /**
     * Town pastimes the run has not tried that [state]'s town offers, as errands for this visit: the tavern's drink,
     * story, and rumors while it is open, the barmaid upstairs where one works, the inn's supper and drink, and the
     * guild. A party short of gold tries none.
     */
    fun townErrands(state: RuntimeState): List<AutopilotErrand> {
        val p = state.party
        val town = content.towns.firstOrNull { town -> town.position == p.position } ?: return emptyList()
        if (p.resources.gold < PASTIME_GOLD) return emptyList()
        val services = town.services.map(content::slug).toSet()
        val level = p.members.firstOrNull()?.level ?: 1

        fun pending(pastime: String): Boolean = !coverage.used(AutopilotCoverage.PASTIME, pastime)

        return buildList {
            if ("tavern" in services) {
                listOf("tavern drink", "tavern story", "tavern rumors").filter(::pending).forEach { pastime -> add(AutopilotErrand.Pastime(pastime, "Tavern")) }
                val bargirl =
                    content.characters.any { character ->
                        content.slug(character.job) == BARGIRL &&
                            (character.position(state) as? CompiledWorldPosition.Town)?.let { position ->
                                position.town == town.id && content.slug(position.building) == "tavern"
                            } == true
                    }
                if (bargirl && pending("upstairs") && p.resources.gold >= level * UPSTAIRS_PRICE_PER_LEVEL + PASTIME_GOLD) {
                    add(AutopilotErrand.Pastime("upstairs", "Tavern"))
                }
            }
            if ("inn" in services && (pending("inn supper") || pending("inn drink"))) add(AutopilotErrand.Pastime("inn supper", "Inn"))
            if ("guild" in services && pending("guild")) add(AutopilotErrand.Pastime("guild", "Guild"))
        }
    }

    /** Whether the run should enter the cave in front of it: it has not entered one yet, and the party can afford to. */
    fun wantsCave(state: RuntimeState): Boolean {
        val p = state.party
        return !coverage.used(RuntimeCommandGroupId.WildernessCave.id, RuntimeCommandActionId.Enter.id) &&
            party.dead(p).isEmpty() &&
            party.living(p).all { member -> member.hitPointShare >= SAFE_HEALTH } &&
            p.resources.torches >= CAVE_TORCHES &&
            p.resources.timeLeft > CAVE_MOVES
    }

    /** The stair choice the run has not made yet besides climbing on: leaving the stairs, then teleporting out. */
    fun stairsChoice(): RuntimeCommandActionId? {
        val group = systems.castleRows.single().id.let(RuntimeCommandGroupId::castleStairs)
        return listOf(RuntimeCommandActionId.Leave, RuntimeCommandActionId.TeleportOut).firstOrNull { action -> !coverage.used(group, action.id) }
    }

    /** Whether to leave castle treasure behind once, having taken some before. */
    fun leavesTreasure(): Boolean {
        val group = systems.castleRows.single().id.let(RuntimeCommandGroupId::castleTreasure)
        return coverage.used(group, RuntimeCommandActionId.Take.id) && !coverage.used(group, RuntimeCommandActionId.Leave.id)
    }

    private val mageSpells = content.spells.map { spell -> content.slug(spell.id) }.filter { slug -> slug.startsWith(MAGE_SPELLS) }

    /**
     * Revelation in a dungeon and Earthquake in the wilderness, the priest spells only those places allow, once each;
     * in the castle, any spell when the run has not cast one there ([castPending]).
     */
    private fun spellStop(
        state: RuntimeState,
        group: String,
        commands: List<ExternalRuntimeCommand>,
        castPending: Boolean,
    ): AutopilotAction? {
        val priest = party.firstLiving(state.party, PcClass.Priest) ?: return null
        val slug =
            when (state.currentMode) {
                RuntimeMode.Dungeon -> REVELATION
                RuntimeMode.Wilderness -> EARTHQUAKE.takeIf { state.party.resources.timeLeft > CAVE_MOVES }
                else -> HEALING
            } ?: return null
        if (if (state.currentMode == RuntimeMode.Castle) !castPending else coverage.used(SPELL, slug)) return null
        if (priest.member.mana.current < (spellCosts[slug] ?: return null) + priest.member.level) return null
        val name = spellNames.getValue(slug)
        val target = party.living(state.party).minByOrNull(AutopilotMember::hitPointShare)
        return AutopilotAction(
            "cast $name with ${priest.number}",
            buildList {
                add { key(group, commands, RuntimeCommandActionId.CastSpell) }
                add { view -> priest.digit.takeIf { view.text.any { line -> line.startsWith("Who?") } } }
                add { view -> AutopilotMenu.parse(view.text).find(name)?.key }
                if (slug == HEALING && target != null) add { view -> target.digit.takeIf { view.text.any { line -> line.startsWith("Who?") } } }
            },
        )
    }

    private fun single(
        group: String,
        commands: List<ExternalRuntimeCommand>,
        action: RuntimeCommandActionId,
    ): AutopilotAction = AutopilotAction("tour: ${action.id}", listOf { key(group, commands, action) })

    /** Views the party pages, leafs through a few, and returns. */
    private fun viewParty(
        group: String,
        commands: List<ExternalRuntimeCommand>,
    ): AutopilotAction =
        AutopilotAction(
            "tour: view party",
            listOf(
                { key(group, commands, RuntimeCommandActionId.ViewParty) },
                { view -> NEXT_PAGE.takeIf { view.partyPage() } },
                { view -> NEXT_PAGE.takeIf { view.partyPage() } },
                { view -> '\n'.takeIf { view.partyPage() } },
            ),
        )

    private fun drop(
        group: String,
        commands: List<ExternalRuntimeCommand>,
        itemName: String,
    ): AutopilotAction =
        AutopilotAction(
            "tour: drop $itemName",
            listOf(
                { key(group, commands, RuntimeCommandActionId.Drop) },
                { view -> AutopilotMenu.parse(view.text).findInventoryItem(itemName)?.key },
            ),
        )

    /** Takes off a member's weapon or armor and readies it again, which needs a free inventory slot for the moment. */
    private fun swap(
        state: RuntimeState,
        group: String,
        commands: List<ExternalRuntimeCommand>,
        gear: AutopilotGear,
    ): AutopilotAction? {
        if (party.freeSlots(state.party) < 1) return null
        val member =
            party.living(state.party).firstOrNull { member ->
                val item = if (gear == AutopilotGear.Weapon) member.member.equipment.weapon else member.member.equipment.armor
                item != blank
            } ?: return null
        val item = if (gear == AutopilotGear.Weapon) member.member.equipment.weapon else member.member.equipment.armor
        val name = party.itemName(item) ?: return null
        val remove = if (gear == AutopilotGear.Weapon) RuntimeCommandActionId.RemoveWeapon else RuntimeCommandActionId.RemoveArmor
        val ready = if (gear == AutopilotGear.Weapon) RuntimeCommandActionId.ReadyWeapon else RuntimeCommandActionId.ReadyArmor
        return AutopilotAction(
            "tour: take off and ready $name",
            listOf(
                { key(group, commands, remove) },
                { view -> member.digit.takeIf { view.text.any { line -> line.startsWith("Who?") } } },
                { key(group, commands, ready) },
                { view -> member.digit.takeIf { view.text.any { line -> line.startsWith("Who?") } } },
                { view -> AutopilotMenu.parse(view.text).findInventoryItem(name)?.key },
            ),
        )
    }

    private fun key(
        group: String,
        commands: List<ExternalRuntimeCommand>,
        action: RuntimeCommandActionId,
    ): Char = commands.key(action).also { coverage.record(group, action.id) }

    private fun AutopilotView.partyPage(): Boolean = text.any { line -> PARTY_PAGE_HINT in line }

    private companion object {
        const val SPELL = "spell"
        const val MAGE_SPELLS = "mage."
        const val REVELATION = "priest.revelation"
        const val EARTHQUAKE = "priest.earthquake"
        const val HEALING = "priest.heal"
        const val BLANK = "blank"
        const val NEXT_PAGE = '6'
        const val PARTY_PAGE_HINT = "Arrows: page"
        const val SAFE_HEALTH = 0.7
        const val CAVE_TORCHES = 5
        const val CAVE_MOVES = 40
        const val BARGIRL = "bargirl"
        const val UPSTAIRS_PRICE_PER_LEVEL = 50
        const val PASTIME_GOLD = 300
    }
}

/** The acting mage's level and spell points, for the tour's spell choice. */
internal data class AutopilotCaster(
    val level: Int,
    val mana: Int,
)

/** A battle choice the tour makes: an action such as defending, or a mage spell by its menu name. */
internal sealed interface AutopilotBattleTour {
    data class Action(
        val kind: CombatActionKind,
    ) : AutopilotBattleTour

    data class Spell(
        val name: String,
    ) : AutopilotBattleTour
}
