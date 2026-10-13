/**
 * The full-game plan: plays a new party from its first steps outside Thord to
 * Baron Ydarg's defeat, following [AutopilotQuests] and looking after the party
 * the way a careful player would. The priest heals and revives, the ranger
 * hunts when food runs low, the party camps with a guard, buys and readies
 * better gear, and builds levels and gold between quest steps.
 */

package rylween.runtime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledItemRecord
import rylween.compiled.CompiledOperand
import rylween.compiled.CompiledPartyMember
import rylween.compiled.CompiledShopPrice
import rylween.compiled.CompiledTown
import rylween.compiled.CompiledWorldPosition
import rylween.compiled.ExternalRuntimeCastle
import rylween.compiled.ExternalRuntimeSystems
import rylween.dsl.impl.GridPoint
import rylween.dsl.impl.ItemKind
import rylween.dsl.impl.PcClass
import kotlin.math.abs
import kotlin.random.Random

/** An item a shop sells: the [label] its catalog shows, the item, and its fixed [price]. */
internal data class AutopilotOffer(
    val label: String,
    val item: CompiledItemRecord,
    val price: Int,
)

/** What the smith and the merchant sell, read from the shop catalogs compiled into the town scenes. */
internal class AutopilotShops(
    content: CompiledContentBundle,
) {
    private val offers: Map<String, List<AutopilotOffer>> =
        content.scripts.roots
            .asSequence()
            .flatMap { root -> root.instructions }
            .flatMap { instruction -> instruction.operands }
            .filterIsInstance<CompiledOperand.ShopService>()
            .map { operand -> operand.service }
            .distinctBy { service -> service.building }
            .associate { service ->
                content.slug(service.building) to
                    service.buy
                        ?.catalogs
                        .orEmpty()
                        .flatMap { catalog -> catalog.pages.flatMap { page -> page.entries } }
                        .mapNotNull { entry ->
                            val item = content.items.firstOrNull { item -> item.id == entry.item } ?: return@mapNotNull null
                            val label = entry.labelString?.let(content.strings::value) ?: content.strings.value(item.displayNameString)
                            val price = (entry.price as? CompiledShopPrice.Fixed)?.amount ?: item.cost
                            AutopilotOffer(label, item, price)
                        }
            }

    val weapons: List<AutopilotOffer> = offers["smith"].orEmpty().filter { offer -> offer.item.kind == ItemKind.Weapon }

    val armor: List<AutopilotOffer> = offers["smith"].orEmpty().filter { offer -> offer.item.kind == ItemKind.Armor }
}

internal class AutopilotFullGamePlan(
    private val content: CompiledContentBundle,
    systems: ExternalRuntimeSystems,
    castle: ExternalRuntimeCastle,
    seed: Long,
) : AutopilotPlan {
    private val party = AutopilotParty(content)
    private val actions = AutopilotActions(systems)
    private val navigation = AutopilotNavigation(content)
    private val crawl = AutopilotDungeonCrawl(actions, ::weakened)
    private val castleStep = AutopilotQuests.castle
    private val shops = AutopilotShops(content)
    private val endgame = AutopilotEndgamePlan(content, systems, castle)
    private val random = Random(seed)
    private val campFoodPerMember = systems.realm.camp.foodCostPerMember
    private val redPotion = content.items.first { item -> content.slug(item.id) == RED_POTION }
    private val redPotionName = content.strings.value(redPotion.displayNameString)
    private val spellCosts = content.spells.associate { spell -> content.strings.value(spell.nameString) to spell.baseCost }
    private val flagStates: Map<String, List<String>> =
        content.flags.associate { flag ->
            val name = content.slug(flag.id)
            name to flag.states.map { state -> content.slug(state.id).removePrefix("$name.") }
        }
    private val journal = AutopilotJournal(content)
    private var endingDay = false

    /** The dungeon whose wandering monsters the priest has banished on this visit. */
    private var banishedIn: CompiledHandleRef? = null

    /** The step whose start the journal was last read for, and the journal's lines while a reading is under way. */
    private var journalReadFor: String? = null
    private var journalLines: MutableList<String>? = null

    /** How often the party has read its journal. */
    var journalReads: Int = 0
        private set
    private var hutPartner: String? = null
    private var hutTalked = false

    /** The quest step the plan is working on, for failure reports. */
    var currentStepName: String = "-"
        private set

    override fun done(state: RuntimeState): Boolean = endgame.done(state)

    override fun wilderness(state: RuntimeState): AutopilotAction {
        crawl.left()
        banishedIn = null
        hutPartner = null
        endOfDay(state)?.let { return it }
        readJournal(state)?.let { return it }
        upkeep(state)?.let { return it }
        quaff(state)?.let { return it }
        camp(state)?.let { return it }
        hunt(state)?.let { return it }
        readyUpgrade(state)?.let { return it }
        dropJunk(state)?.let { return it }
        val step = currentStep(state)
        currentStepName = step.name
        townTrip(state, step)?.let { return it }
        if (!ready(state, step)) return grind(state, step)
        return if (step == castleStep) endgame.wilderness(state) else pursue(state, step)
    }

    override fun dungeon(state: RuntimeState): AutopilotAction = upkeep(state) ?: quaff(state) ?: banish(state) ?: crawl.next(state)

    /** On arriving in a long dungeon the priest banishes its wandering monsters, so only its fixed fights remain. */
    private fun banish(state: RuntimeState): AutopilotAction? {
        val dungeon = state.dungeon ?: return null
        val priest = party.firstLiving(state.party, PcClass.Priest) ?: return null
        if (dungeon.maxDepth < LONG_DUNGEON || dungeon.depth != 1 || banishedIn == dungeon.dungeon || !canCast(state.party, BANISH_EVIL)) {
            return null
        }
        banishedIn = dungeon.dungeon
        return actions.castPriestSpell(priest, BANISH_EVIL, commands = actions.commandsAt(state))
    }

    /**
     * The party climbs to the final battle only when it is as ready as for any boss, and climbs the other stairs unless
     * it is too weak to go on; otherwise the guardian teleports it out, and it comes back to the same floor once ready.
     */
    override fun climbCastle(
        state: RuntimeState,
        lastFloor: Boolean,
    ): Boolean = if (lastFloor) blockers(state, castleStep).isEmpty() else !weakened(state)

    /** Between castle fights the priest heals and revives, and potions top the party up. */
    override fun castle(state: RuntimeState): AutopilotAction? = upkeep(state) ?: quaff(state)

    override fun townErrands(state: RuntimeState): List<AutopilotErrand> {
        val town = content.towns.firstOrNull { town -> town.position == state.party.position } ?: return emptyList()
        val step = currentStep(state)
        val p = state.party
        // Survival comes before saving: raising, healing, food, and torches may spend the map money; gear may not.
        var gold = p.resources.gold
        val leaderLevel = leaderLevel(p)
        val (smithJunk, merchantJunk) = junk(p)
        return buildList {
            if (smithJunk.isNotEmpty()) add(AutopilotErrand.Sell("Smith", smithJunk))
            if (merchantJunk.isNotEmpty()) add(AutopilotErrand.Sell("Merchant", merchantJunk))
            if (town.offers("church")) {
                party.dead(p).filter { !canRebirth(p) }.forEach { member ->
                    val cost = member.member.level * RAISE_COST_PER_LEVEL
                    if (gold >= cost) {
                        add(AutopilotErrand.Raise(member))
                        gold -= cost
                    }
                }
                party.living(p).filter { member -> member.hitPointShare < healBelow(step) && !priestHeals(p, member) }.forEach { member ->
                    val cost = member.hurt * HEAL_COST_PER_POINT
                    if (gold >= cost) {
                        add(AutopilotErrand.Heal(member))
                        gold -= cost
                    }
                }
                lowCasters(p, step).forEach { member ->
                    val cost = energizeCost(member)
                    if (gold >= cost) {
                        add(AutopilotErrand.Energize(member))
                        gold -= cost
                    }
                }
            }
            val food = (FOOD_TARGET - p.resources.food).coerceAtMost(gold / (leaderLevel + 1))
            if (food > FOOD_MIN_PURCHASE) {
                add(AutopilotErrand.BuyBulk("Food Ration", food))
                gold -= food * (leaderLevel + 1)
            }
            val torches = (torchTarget(step) - p.resources.torches).coerceAtMost(gold)
            if (torches > 0) {
                add(AutopilotErrand.BuyBulk("Torch", torches))
                gold -= torches
            }
            val potionPrice = redPotion.cost + leaderLevel * POTION_PRICE_PER_LEVEL
            val potions =
                minOf(
                    RED_POTIONS_KEPT - party.inventoryCount(p, RED_POTION),
                    party.freeSlots(p) - maxOf(step.freeSlots, MIN_FREE_SLOTS),
                    (gold - reserve(step)) / potionPrice,
                )
            repeat(potions.coerceAtLeast(0)) {
                add(AutopilotErrand.BuyItem(HEALING_POTION))
                gold -= potionPrice
            }
            step.carry?.takeIf { slug -> party.inventoryCount(p, slug) == 0 }?.let { slug ->
                content.items.firstOrNull { item -> content.slug(item.id) == slug }?.let { item ->
                    add(AutopilotErrand.BuyItem(content.strings.value(item.displayNameString)))
                    gold -= item.cost
                }
            }
            gearUpgrades(p, gold - reserve(step)).forEach(::add)
            val objective = step.objective as? AutopilotObjective.TalkTo
            val position = objective?.let { talk -> npcPosition(state, talk.npc) } as? CompiledWorldPosition.Town
            if (objective != null && position != null && position.town == town.id && ready(state, step)) {
                add(AutopilotErrand.Talk(objective.npc, tavern = content.slug(position.building) == "tavern", answers = objective.answers))
            }
            if (p.resources.restedHours <= 0 && gold - reserve(step) >= leaderLevel * leaderLevel * INN_COST_PER_LEVEL_SQUARED) {
                add(AutopilotErrand.Lodge)
            }
        }
    }

    override fun readyOnLeave(
        state: RuntimeState,
        gear: AutopilotGear,
    ): Pair<AutopilotMember, String>? = bestUnreadied(state, gear).firstOrNull { (member, _) -> strength(member.member, gear) == 0 }

    override fun sceneChoice(
        view: AutopilotView,
        menu: AutopilotMenu,
    ): Char? {
        val text = view.text.joinToString(" ")
        val partner = hutPartner
        return when {
            menu.find("Talk") != null && menu.find("Leave") != null ->
                if (partner != null && !hutTalked) menu.find("Talk")?.key else menu.find("Leave")?.key
            "Speak with whom?" in text && partner != null -> {
                hutTalked = true
                menu.options.firstOrNull { option -> option.label.substringBefore(',') == partner }?.key
            }
            menu.find("Bye") != null -> menu.find("Bye")?.key
            "You find a cave!" in text -> menu.find(if (wantsCave(view.state)) "Enter" else "Leave")?.key
            partner != null -> talkAnswers(view.state).firstNotNullOfOrNull { answer -> menu.find(answer) }?.key
            else -> null
        }
    }

    /** A boss step is next: the mage keeps spell points for it instead of spending them on ordinary fights. */
    override fun conserveMana(state: RuntimeState): Boolean = currentStep(state).minimumLevel >= BOSS_LEVEL

    /**
     * Answers the latest known question on screen, so an earlier riddle's words never answer a later prompt. Lines
     * are joined because the screen wraps a question wherever the column runs out.
     */
    override fun observe(view: AutopilotView) {
        journalLines?.addAll(view.text)
    }

    /**
     * Reads the journal whenever a new quest step begins, as a player looks up where to go next, and checks it once the
     * reading is over: it must give the place the step heads for and agree with the world.
     */
    private fun readJournal(state: RuntimeState): AutopilotAction? {
        val step = currentStep(state)
        journalLines?.let { lines ->
            journalLines = null
            val target = (step.objective as? AutopilotObjective.Reach)?.location?.takeIf { step != castleStep }
            val problems = journal.problems(state, lines, target)
            if (problems.isNotEmpty()) throw AutopilotStuck("journal at '${step.name}': ${problems.joinToString("; ")}")
        }
        if (journalReadFor == step.name) return null
        journalReadFor = step.name
        journalLines = mutableListOf()
        journalReads++
        return actions.readJournal()
    }

    override fun answer(view: AutopilotView): String? {
        val text = view.prompt.joinToString(" ")
        return AutopilotQuests.textAnswers
            .filter { (phrase, _) -> phrase in text }
            .maxByOrNull { (phrase, _) -> text.lastIndexOf(phrase) }
            ?.second
    }

    private fun talkAnswers(state: RuntimeState): List<String> =
        (currentStep(state).objective as? AutopilotObjective.TalkTo)?.answers ?: listOf("Yes", "Accept")

    /** Saves and quits once the day's moves are spent: the next session gets a fresh day's allowance. */
    private fun endOfDay(state: RuntimeState): AutopilotAction? {
        if (state.party.resources.timeLeft > 0) {
            endingDay = false
            return null
        }
        endingDay = !endingDay
        return if (endingDay) actions.save() else actions.quit()
    }

    /**
     * Drinks a healing potion for the most hurt member when the priest cannot heal: the potion heals more the higher
     * the drinker's level, and costs far less than the church. Before a boss step the party tops up this way.
     */
    private fun quaff(state: RuntimeState): AutopilotAction? {
        val p = state.party
        if (party.inventoryCount(p, RED_POTION) == 0) return null
        val hurt = party.living(p).minByOrNull(AutopilotMember::hitPointShare) ?: return null
        if (priestHeals(p, hurt)) return null
        return actions.quaff(redPotionName, hurt, actions.commandsAt(state)).takeIf { hurt.hitPointShare < healBelow(currentStep(state)) }
    }

    /** Hit-point share below which a member gets healed: nearly full before a boss step, otherwise badly hurt. */
    private fun healBelow(step: AutopilotQuestStep): Double = if (step.minimumLevel >= BOSS_LEVEL) BOSS_HEALTH else HEAL_BELOW

    /** Whether the party should leave a dungeon: someone is dead or badly hurt and the priest cannot fix it. */
    private fun weakened(state: RuntimeState): Boolean {
        val p = state.party
        return party.dead(p).isNotEmpty() && !canRebirth(p) ||
            party.living(p).any { member -> member.hitPointShare < RETREAT_BELOW } && !canHeal(p)
    }

    /** The priest revives the dead with Rebirth and heals the hurt with Healing, when mana allows. */
    private fun upkeep(state: RuntimeState): AutopilotAction? {
        val p = state.party
        val priest = party.firstLiving(p, PcClass.Priest) ?: return null
        val dead = party.dead(p).firstOrNull()
        if (dead != null && canRebirth(p)) return actions.castPriestSpell(priest, REBIRTH, dead, actions.commandsAt(state))
        val hurt = party.living(p).minByOrNull(AutopilotMember::hitPointShare)
        if (hurt != null && hurt.hitPointShare < healBelow(currentStep(state)) && priestHeals(p, hurt)) {
            return actions.castPriestSpell(priest, HEALING, hurt, actions.commandsAt(state))
        }
        return null
    }

    private fun canRebirth(party: PartyState): Boolean = canCast(party, REBIRTH)

    private fun canHeal(party: PartyState): Boolean = canCast(party, HEALING)

    /**
     * Whether the priest heals [hurt] now. Once the priest can hold enough spell points to cast Rebirth when a member
     * falls, it keeps them: it heals only while that reserve stays whole, or when [hurt] is badly hurt, and potions and
     * the church cover the rest. A priest too green to hold the reserve heals freely.
     */
    private fun priestHeals(
        p: PartyState,
        hurt: AutopilotMember,
    ): Boolean {
        val priest = party.firstLiving(p, PcClass.Priest)?.member ?: return false
        if (!canHeal(p)) return false
        if (hurt.hitPointShare < RETREAT_BELOW) return true
        val healing = (spellCosts[HEALING] ?: return false) + priest.level
        val rebirth = (spellCosts[REBIRTH] ?: return true) + priest.level
        return priest.mana.max < healing + rebirth || priest.mana.current - healing >= rebirth
    }

    private fun canCast(
        p: PartyState,
        spell: String,
    ): Boolean {
        val priest = party.firstLiving(p, PcClass.Priest)?.member ?: return false
        return priest.mana.current >= (spellCosts[spell] ?: return false) + priest.level
    }

    /** Camps with the healthiest member on guard once the party is tired and has the food. */
    private fun camp(state: RuntimeState): AutopilotAction? {
        val p = state.party
        val living = party.living(p)
        if (p.resources.restedHours > 0 || p.resources.food < campFoodPerMember * living.size) return null
        if (content.towns.any { town -> town.position == p.position }) return null
        return actions.camp(living.maxBy(AutopilotMember::hitPointShare))
    }

    /** The ranger traps food once supplies run low. */
    private fun hunt(state: RuntimeState): AutopilotAction? {
        val p = state.party
        if (p.resources.food >= HUNT_BELOW || p.resources.timeLeft < HUNT_MOVES) return null
        val ranger = party.firstLiving(p, PcClass.Ranger) ?: return null
        return actions.hunt(ranger)
    }

    /**
     * Drops junk once the inventory runs short of room while the party saves for a map: selling would mean entering
     * a town, whose entry tax on the gold carried costs far more than the junk is worth.
     */
    private fun dropJunk(state: RuntimeState): AutopilotAction? {
        val p = state.party
        val step = currentStep(state)
        if (!saving(p, step) || party.freeSlots(p) >= maxOf(step.freeSlots, MIN_FREE_SLOTS)) return null
        return junkNames(p).firstOrNull()?.let(actions::drop)
    }

    /** Whether the party is saving for a map while its leader pays town entry tax. */
    private fun saving(
        p: PartyState,
        step: AutopilotQuestStep,
    ): Boolean = step.gold > 0 && leaderLevel(p) >= TAX_LEVEL

    private fun junkNames(p: PartyState): List<String> = junk(p).let { (smith, merchant) -> smith + merchant }

    /** Readies a weapon or armor from the inventory that beats what a living member uses now. */
    private fun readyUpgrade(state: RuntimeState): AutopilotAction? =
        AutopilotGear.entries.firstNotNullOfOrNull { gear ->
            bestUnreadied(state, gear).firstOrNull()?.let { (member, name) -> actions.ready(gear, member, name) }
        }

    /** Living members paired with the best inventory item of [gear] that beats their own, weakest member first. */
    private fun bestUnreadied(
        state: RuntimeState,
        gear: AutopilotGear,
    ): List<Pair<AutopilotMember, String>> {
        val p = state.party
        val owned = party.inventory(p).filter { item -> strength(item, gear) > 0 }
        return party
            .living(p)
            .sortedBy { member -> strength(member.member, gear) }
            .mapNotNull { member ->
                owned
                    .filter { item -> item.equippableBy.isEmpty() || member.member.pcClass in item.equippableBy }
                    .maxByOrNull { item -> strength(item, gear) }
                    ?.takeIf { item -> strength(item, gear) > strength(member.member, gear) }
                    ?.let { item -> member to content.strings.value(item.displayNameString) }
            }
    }

    /** How good [item] is as [gear]; zero when it is not that kind of gear, as wands are not weapons. */
    private fun strength(
        item: CompiledItemRecord,
        gear: AutopilotGear,
    ): Int =
        when {
            gear == AutopilotGear.Weapon && item.kind == ItemKind.Weapon -> item.weaponDamage ?: 0
            gear == AutopilotGear.Armor && item.kind == ItemKind.Armor -> item.armorClass ?: 0
            else -> 0
        }

    private fun strength(
        member: CompiledPartyMember,
        gear: AutopilotGear,
    ): Int = if (gear == AutopilotGear.Weapon) party.weaponDamage(member) else party.armorClass(member)

    /**
     * Inventory items worth selling, as the smith's and the merchant's share: gear no member would ready, treasure,
     * magic items beyond the few battle items worth carrying into hard fights, red potions beyond the stock, and altar
     * gems beyond what the quests still need. Camping and pack gear stay.
     */
    private fun junk(p: PartyState): Pair<List<String>, List<String>> {
        val smith = mutableListOf<String>()
        val merchant = mutableListOf<String>()
        var redPotions = 0
        var gems = 0
        val gemsKept = gemsNeeded(p)
        val battleItemsKept =
            party
                .inventory(p)
                .map { item -> content.slug(item.id) }
                .filter { slug -> slug in AutopilotBattleItems.keepOrder }
                .sortedBy(AutopilotBattleItems.keepOrder::indexOf)
                .take(BATTLE_ITEMS_KEPT)
                .toMutableList()
        party.inventory(p).forEach { item ->
            val slug = content.slug(item.id)
            val name = content.strings.value(item.displayNameString)
            val gear = if (item.kind == ItemKind.Weapon) AutopilotGear.Weapon else AutopilotGear.Armor
            when {
                slug in keepItems -> Unit
                slug in AutopilotQuests.altarGems && gems < gemsKept -> gems++
                battleItemsKept.remove(slug) -> Unit
                item.kind == ItemKind.Weapon || item.kind == ItemKind.Armor -> {
                    val wanted =
                        party.members(p).any { member ->
                            (item.equippableBy.isEmpty() || member.member.pcClass in item.equippableBy) &&
                                strength(item, gear) > strength(member.member, gear)
                        }
                    if (!wanted) smith += name
                }
                slug == RED_POTION && redPotions < RED_POTIONS_KEPT -> redPotions++
                item.kind in sellableAtMerchant -> merchant += name
            }
        }
        return smith to merchant
    }

    /** Smith purchases that beat a member's gear, best affordable first, within [budget] gold. */
    private fun gearUpgrades(
        p: PartyState,
        budget: Int,
    ): List<AutopilotErrand> {
        var gold = budget
        val bought = mutableListOf<AutopilotErrand>()
        val inventoryFree = party.freeSlots(p) - 1
        AutopilotGear.entries.forEach { gear ->
            val offers = if (gear == AutopilotGear.Weapon) shops.weapons else shops.armor
            party
                .living(p)
                .sortedBy { member -> strength(member.member, gear) }
                .forEach { member ->
                    if (bought.size >= inventoryFree) return@forEach
                    val best =
                        offers
                            .filter { offer -> offer.price <= gold && strength(offer.item, gear) > strength(member.member, gear) }
                            .filter { offer -> offer.item.equippableBy.isEmpty() || member.member.pcClass in offer.item.equippableBy }
                            .maxByOrNull { offer -> strength(offer.item, gear) }
                            ?: return@forEach
                    bought += AutopilotErrand.BuyGear(gear, best.label)
                    gold -= best.price
                }
        }
        return bought
    }

    /** Heads for a town when the party needs healing, food, torches, an item for the next step, or better gear. */
    private fun townTrip(
        state: RuntimeState,
        step: AutopilotQuestStep,
    ): AutopilotAction? {
        val needs = townNeeds(state, step)
        if (needs.isEmpty()) return null
        val needsChurch = needs.any { need -> need in churchNeeds }
        val p = state.party
        val towns = content.towns.filter { town -> !needsChurch || town.offers("church") }
        val town = towns.minByOrNull { town -> distance(p.position, town.position) } ?: return null
        return walkTo(state, town.position) ?: actions.enter()
    }

    /**
     * Why the party should head for a town before [step], such as "raise" or "food"; empty when it need not. Each need
     * counts only when the party can pay for it, so a broke party camps and fights for gold instead of walking into
     * a town that cannot help it.
     */
    fun townNeeds(
        state: RuntimeState,
        step: AutopilotQuestStep = currentStep(state),
    ): List<String> {
        val p = state.party
        val gold = p.resources.gold
        return buildList {
            if (party.dead(p).isNotEmpty() && !canRebirth(p) && gold >= party.dead(p).first().member.level * RAISE_COST_PER_LEVEL) add("raise")
            if (party.living(p).any { member -> member.hitPointShare < healBelow(step) && !priestHeals(p, member) } && gold > HEAL_TRIP_GOLD) {
                add("heal")
            }
            if (lowCasters(p, step).any { member -> gold >= energizeCost(member) }) add("energize")
            if (p.resources.food < FOOD_LOW && gold >= FOOD_MIN_PURCHASE * (leaderLevel(p) + 1)) add("food")
            if (p.resources.torches < torchTarget(step) - TORCH_SLACK && gold > torchTarget(step)) add("torches")
            val carried = step.carry?.let { slug -> content.items.first { item -> content.slug(item.id) == slug } }
            if (carried != null && party.inventoryCount(p, step.carry) == 0 && gold >= carried.cost) add("carry")
            if (gearUpgrades(p, gold - reserve(step)).isNotEmpty() && gold > reserve(step) + GEAR_TRIP_GOLD) add("gear")
            if (!saving(p, step) && party.freeSlots(p) < maxOf(step.freeSlots, MIN_FREE_SLOTS) && junkNames(p).isNotEmpty()) add("sell")
        }
    }

    /** Works on [step]: talks to its character, walks onto its location, or wanders until its event fires. */
    private fun pursue(
        state: RuntimeState,
        step: AutopilotQuestStep,
    ): AutopilotAction =
        when (val objective = step.objective) {
            is AutopilotObjective.TalkTo ->
                when (val position = npcPosition(state, objective.npc)) {
                    is CompiledWorldPosition.Town -> {
                        val town = content.towns.first { town -> town.id == position.town }
                        walkTo(state, town.position) ?: actions.enter()
                    }

                    is CompiledWorldPosition.Wilderness ->
                        walkTo(state, position.point) ?: actions.enter().also {
                            hutPartner = objective.npc
                            hutTalked = false
                        }

                    null -> throw AutopilotStuck("${objective.npc} is not placed anywhere")
                }

            is AutopilotObjective.Reach -> {
                val point =
                    state.locations.firstOrNull { placement -> content.slug(placement.location) == objective.location }?.point
                        ?: throw AutopilotStuck("${objective.location} is not placed")
                // Standing on the location already means its mission did not fire on arrival: step off and back on.
                walkTo(state, point) ?: wander(state, avoid = emptySet())
            }

            is AutopilotObjective.Wander -> {
                val terrain = objective.terrain
                val here = navigation.terrainSlugAt(state.currentMap, state.party.position)
                if (terrain != null && here != terrain) {
                    navigation.stepToTerrain(state.currentMap, state.party.position, terrain, avoid = locationSquares(state))
                        ?.let(actions::move)
                        ?: throw AutopilotStuck("no walkable $terrain")
                } else {
                    wander(state, avoid = locationSquares(state), stayOn = terrain)
                }
            }
        }

    /** Builds levels and gold: wanders near the nearest town, where random fights come to the party. */
    private fun grind(
        state: RuntimeState,
        step: AutopilotQuestStep,
    ): AutopilotAction {
        val p = state.party
        val home = content.towns.minBy { town -> distance(p.position, town.position) }
        if (distance(p.position, home.position) > GRIND_RADIUS) return walkTo(state, home.position) ?: wander(state, locationSquares(state))
        if (step.gems > altarGems(p) && wantsCave(state) && canCast(p, EARTHQUAKE)) {
            party.firstLiving(p, PcClass.Priest)?.let { priest -> return actions.castPriestSpell(priest, EARTHQUAKE) }
        }
        return wander(state, locationSquares(state))
    }

    /** Random caves pay off only while the party still lacks altar gems and can afford the trip. */
    private fun wantsCave(state: RuntimeState): Boolean {
        val p = state.party
        val step = currentStep(state)
        return step.gems > altarGems(p) &&
            p.resources.torches >= CAVE_TORCHES &&
            p.resources.timeLeft > CAVE_MOVES &&
            party.freeSlots(p) > 2 &&
            party.dead(p).isEmpty()
    }

    /** One step towards [target], teleporting when the priest can shorten a long walk; `null` when already there. */
    private fun walkTo(
        state: RuntimeState,
        target: GridPoint,
    ): AutopilotAction? {
        val p = state.party
        if (p.position == target) return null
        teleportTowards(state, target)?.let { return it }
        // A location can be the only way out of a pocket, such as the crypt entrance beside a dead end, so the last
        // resort walks across it.
        val step =
            navigation.stepTowards(state.currentMap, p.position, target, locationSquares(state), dangerousTerrain(state))
                ?: navigation.stepTowards(state.currentMap, p.position, target, locationSquares(state))
                ?: navigation.stepTowards(state.currentMap, p.position, target)
                ?: throw AutopilotStuck("no walkable path to ${target.x},${target.y}")
        return actions.move(step)
    }

    /** Teleports along a straight line towards [target] when it saves a long walk and lands on safe, open ground. */
    private fun teleportTowards(
        state: RuntimeState,
        target: GridPoint,
    ): AutopilotAction? {
        val p = state.party
        val priest = party.firstLiving(p, PcClass.Priest) ?: return null
        val cost = (spellCosts[TELEPORT] ?: return null) + priest.member.level
        if (distance(p.position, target) < TELEPORT_MIN_DISTANCE || priest.member.mana.current < cost + MANA_RESERVE) return null
        val dx = target.x - p.position.x
        val dy = target.y - p.position.y
        val direction =
            if (abs(dx) >= abs(dy)) {
                if (dx > 0) AutopilotDirection.East else AutopilotDirection.West
            } else {
                if (dy > 0) AutopilotDirection.South else AutopilotDirection.North
            }
        val span = maxOf(abs(dx), abs(dy))
        val miles = (span / 2).coerceIn(1, 9)
        val landing = GridPoint(p.position.x + direction.dx * miles * 2, p.position.y + direction.dy * miles * 2)
        val terrain = navigation.terrainSlugAt(state.currentMap, landing) ?: return null
        val safe =
            terrain !in dangerousTerrain(state) &&
                landing !in locationSquares(state) &&
                content.towns.none { town -> town.position == landing } &&
                navigation.stepTowards(state.currentMap, landing, target, locationSquares(state)) != null
        return if (safe) actions.teleport(priest, direction, miles) else null
    }

    private fun wander(
        state: RuntimeState,
        avoid: Set<GridPoint>,
        stayOn: String? = null,
    ): AutopilotAction {
        val towns = content.towns.map(CompiledTown::position).toSet()
        val step =
            navigation.wander(state.currentMap, state.party.position, random, stayOn, avoid + towns, dangerousTerrain(state))
                ?: navigation.wander(state.currentMap, state.party.position, random, stayOn)
                ?: throw AutopilotStuck("nowhere to wander")
        return actions.move(step)
    }

    /** Placed quest locations; walking across one fires its mission, so routes go around them. */
    private fun locationSquares(state: RuntimeState): Set<GridPoint> = state.locations.map { placement -> placement.point }.toSet()

    /** Terrain whose missions the party is not ready for: foothills hold Jazzy once his ogres are gone. */
    private fun dangerousTerrain(state: RuntimeState): Set<String> {
        val jazzy = AutopilotQuests.steps.first { step -> step.objective == AutopilotObjective.Wander(terrain = "foothills") }
        val waiting = party.flagState(state.party, jazzy.flag) == "ogres_cleared" && !ready(state, jazzy)
        return if (waiting) setOf("foothills") else emptySet()
    }

    /** The first quest step not yet done, or storming the castle once every quest is. */
    private fun currentStep(state: RuntimeState): AutopilotQuestStep =
        AutopilotQuests.steps.firstOrNull { step -> !done(state.party, step) } ?: castleStep

    private fun done(
        p: PartyState,
        step: AutopilotQuestStep,
    ): Boolean {
        val order = flagStates[step.flag] ?: throw AutopilotStuck("unknown story flag ${step.flag}")
        return order.indexOf(party.flagState(p, step.flag)) >= order.indexOf(step.state)
    }

    /** Altar gems the remaining quest steps still ask for; the rest are treasure to sell. */
    private fun gemsNeeded(p: PartyState): Int = AutopilotQuests.steps.filterNot { step -> done(p, step) }.maxOfOrNull { step -> step.gems } ?: 0

    private fun ready(
        state: RuntimeState,
        step: AutopilotQuestStep,
    ): Boolean = blockers(state, step).isEmpty()

    /** What keeps the party from starting [step], such as "level" or "health"; empty once it is ready. */
    fun blockers(
        state: RuntimeState,
        step: AutopilotQuestStep = currentStep(state),
    ): List<String> {
        val p = state.party
        return buildList {
            if (leaderLevel(p) < step.minimumLevel) add("level")
            if (p.resources.torches < step.torches) add("torches")
            if (p.resources.gold < step.gold) add("gold")
            if (party.freeSlots(p) < step.freeSlots) add("slots")
            if (altarGems(p) < step.gems) add("gems")
            if (step.carry != null && party.inventoryCount(p, step.carry) == 0) add("carry")
            if (party.dead(p).isNotEmpty()) add("dead")
            if (party.living(p).any { member -> member.hitPointShare < maxOf(READY_HEALTH, healBelow(step)) }) add("health")
            if (step.minimumLevel >= BOSS_LEVEL && party.living(p).any { member -> manaShare(member) < READY_MANA }) add("mana")
            if (banishFirst(step) && !canCast(p, BANISH_EVIL)) add("banish")
        }
    }

    /** Steps that lead down a long dungeon, whose wandering monsters the priest banishes on arrival. */
    private fun banishFirst(step: AutopilotQuestStep): Boolean = step.torches > LONG_DUNGEON

    /**
     * Casters the church should energize before [step]: battle casters short of mana for a boss, who fight it with full
     * spell points, and a priest who cannot yet banish the wandering monsters of the long dungeon ahead.
     */
    private fun lowCasters(
        p: PartyState,
        step: AutopilotQuestStep,
    ): List<AutopilotMember> =
        party.living(p).filter { member ->
            member.member.pcClass in battleCasters && step.minimumLevel >= BOSS_LEVEL && manaShare(member) < READY_MANA ||
                member.member.pcClass == PcClass.Priest && banishFirst(step) && !canCast(p, BANISH_EVIL)
        }

    /** The church's price to restore [member]'s spell points: level times missing points times two. */
    private fun energizeCost(member: AutopilotMember): Int =
        member.member.level * (member.member.wisdom - member.member.mana.current).coerceAtLeast(0) * ENERGIZE_FACTOR

    /**
     * How full a battle caster's mana is; everyone else counts as full. Only mages cast in battle: a priest's spell
     * points heal and revive between fights, so a boss step does not wait for them.
     */
    private fun manaShare(member: AutopilotMember): Double =
        if (member.member.pcClass !in battleCasters) 1.0 else rawManaShare(member)

    private fun rawManaShare(member: AutopilotMember): Double =
        member.member.mana.max
            .takeIf { max -> max > 0 }
            ?.let { max -> member.member.mana.current.toDouble() / max } ?: 1.0

    /** Gold to keep unspent for the next map purchase. */
    private fun reserve(step: AutopilotQuestStep): Int = step.gold

    private fun torchTarget(step: AutopilotQuestStep): Int = maxOf(step.torches, BASE_TORCHES) + TORCH_SLACK

    private fun altarGems(p: PartyState): Int = AutopilotQuests.altarGems.sumOf { slug -> party.inventoryCount(p, slug) }

    private fun leaderLevel(p: PartyState): Int = p.members.firstOrNull()?.level ?: 1

    private fun npcPosition(
        state: RuntimeState,
        name: String,
    ): CompiledWorldPosition? = content.characters.firstOrNull { character -> content.strings.value(character.nameString) == name }?.position(state)

    private fun CompiledTown.offers(service: String): Boolean = services.any { building -> content.slug(building) == service }

    private fun distance(
        a: GridPoint,
        b: GridPoint,
    ): Int = abs(a.x - b.x) + abs(a.y - b.y)

    private companion object {
        const val HEALING = "Healing"
        const val REBIRTH = "Rebirth"
        const val TELEPORT = "Teleport"
        const val EARTHQUAKE = "Earthquake"
        const val BANISH_EVIL = "Banish Evil"
        const val LONG_DUNGEON = 10
        const val HEAL_BELOW = 0.6
        const val RETREAT_BELOW = 0.4
        const val BOSS_HEALTH = 0.9
        const val HEALING_POTION = "Healing Potion"
        const val POTION_PRICE_PER_LEVEL = 3
        const val READY_HEALTH = 0.7
        const val READY_MANA = 0.8
        const val BOSS_LEVEL = 9
        const val HEAL_COST_PER_POINT = 7
        const val HEAL_TRIP_GOLD = 100
        const val RAISE_COST_PER_LEVEL = 650
        const val ENERGIZE_FACTOR = 2
        const val INN_COST_PER_LEVEL_SQUARED = 15
        const val FOOD_TARGET = 140
        const val FOOD_LOW = 40
        const val FOOD_MIN_PURCHASE = 10
        const val HUNT_BELOW = 70
        const val HUNT_MOVES = 5
        const val BASE_TORCHES = 8
        const val TORCH_SLACK = 2
        const val GEAR_TRIP_GOLD = 150
        const val GRIND_RADIUS = 6
        const val CAVE_TORCHES = 17
        const val CAVE_MOVES = 30
        const val TELEPORT_MIN_DISTANCE = 16
        const val MANA_RESERVE = 20
        const val MIN_FREE_SLOTS = 3
        const val TAX_LEVEL = 5
        const val RED_POTION = "red_potion"
        const val RED_POTIONS_KEPT = 6
        const val BATTLE_ITEMS_KEPT = 3
        val keepItems = setOf("tent", "blanket", "donkey")
        val battleCasters = setOf(PcClass.Mage)
        val churchNeeds = setOf("raise", "heal", "energize")
        val sellableAtMerchant = setOf(ItemKind.Treasure, ItemKind.Potion, ItemKind.MagicItem, ItemKind.Gear)
    }
}
