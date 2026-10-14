/**
 * Battle tactics for the autopilot: fight what the party can beat, run from
 * the rest, and wear the enemy down from its weakest member.
 *
 * Decisions come from an expected-value model of the bundle's own combat
 * rules: a party member hits with chance weapon skill + bonus for weapon
 * damage plus a spread plus current hit points over a divisor, and each living
 * monster hits with chance weapon skill + bonus for its current hit points over
 * a divisor plus a spread, less the target's armor.
 */

package rylween.runtime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledMonster
import rylween.compiled.CompiledPartyMember
import rylween.compiled.ExternalRuntimeSystems
import rylween.compiled.ExternalStandardBattle

/** A magic item to use in battle: its inventory [item] name and, for items that ask, the member to use it on. */
internal data class AutopilotItemUse(
    val item: String,
    val target: AutopilotMember? = null,
)

/** A group of foes as the battle screen names them, such as "5 Knights". */
internal data class AutopilotFoes(
    val count: Int,
    val monster: CompiledMonster?,
    /** Current hit points per living foe, when the target list has shown them. */
    val hitPoints: List<Int>?,
)

internal class AutopilotBattle(
    private val content: CompiledContentBundle,
    systems: ExternalRuntimeSystems,
) {
    private val rules: ExternalStandardBattle = checkNotNull(systems.combat.standardBattle) { "bundle has no standard battle rules" }
    private val monstersByName =
        content.monsters
            .map { monster -> content.strings.value(monster.nameString) to monster }
            .sortedByDescending { (name, _) -> name.length }

    /** Hit points of the living foes from the most recent target list, until the battle ends. */
    private var knownHitPoints: List<Int>? = null

    /** The foes of the battle in progress, as the latest "You face" / "You still face" text named them. */
    var currentFoes: AutopilotFoes? = null
        private set

    private val mageSpells =
        content.spells
            .filter { spell -> content.slug(spell.id).startsWith("mage.") }
            .associateBy { spell -> content.strings.value(spell.nameString) }

    private var failedEscapes = 0

    private val members = AutopilotParty(content)

    /** Battle items already used in the battle in progress; each is used at most once per battle. */
    private val usedItems = mutableSetOf<String>()

    /**
     * Whether running is hopeless in this battle. Mandatory fights never let the party escape, while an ordinary
     * escape fails only one time in twenty, so two failures in a row mean the fight cannot be fled.
     */
    val escapeHopeless: Boolean get() = failedEscapes >= HOPELESS_AFTER_FAILED_ESCAPES

    /** Updates what the autopilot knows about the battle from a battle screen's [lines]. */
    fun observe(lines: List<String>) {
        if (lines.any { line -> line.trim() == "You face" }) {
            failedEscapes = 0
            usedItems.clear()
        }
        if (lines.any { line -> "fail to get away" in line || "desperately try to get away" in line }) failedEscapes++
        observeTargets(lines)
        foes(lines)?.let { foes -> currentFoes = foes }
    }

    /**
     * The battle spell the mage [caster] should cast for [party], or `null` to attack instead: Anti Magic silences foes
     * whose spells could kill a member outright, Fire Cloud burns every foe when several still stand, and Frost Finger
     * strikes a lone one. Nothing is cast at foes immune to magic, and nothing the caster's mana cannot pay for (a
     * spell costs its base cost plus the caster's level).
     */
    fun mageSpell(
        caster: CompiledPartyMember,
        party: PartyState,
    ): String? {
        val foes = currentFoes
        if (foes?.monster?.immune == true) return null
        val standing = knownHitPoints?.count { hitPoints -> hitPoints > 0 } ?: foes?.count ?: 1

        fun affordable(name: String): Boolean =
            mageSpells[name]?.let { spell -> caster.mana.current >= spell.baseCost + caster.level } == true

        return when {
            lethalCaster(foes, party) && affordable(ANTI_MAGIC) -> ANTI_MAGIC
            standing >= 2 && affordable(FIRE_CLOUD) -> FIRE_CLOUD
            affordable(FROST_FINGER) -> FROST_FINGER
            else -> null
        }
    }

    /**
     * The magic item to use in a fight the party cannot flee or would not choose, or `null` to fight on without one.
     * The dead come back first, then a badly hurt party is healed; next the foes are blinded, which leaves their
     * weapon skill at almost nothing, and then struck all at once while several stand. Foes immune to magic waste
     * every item, so nothing is used on them.
     */
    fun battleItem(party: PartyState): AutopilotItemUse? {
        val foes = currentFoes ?: return null
        if (foes.monster?.immune == true) return null
        val owned = members.inventory(party).map { item -> content.slug(item.id) }.toSet() - usedItems
        val living = members.living(party)
        val standing = knownHitPoints?.count { hitPoints -> hitPoints > 0 } ?: foes.count
        val hitPoints = living.sumOf { member -> member.member.hitPoints.current }
        val partyShare = hitPoints.toDouble() / living.sumOf { member -> member.member.hitPoints.max }.coerceAtLeast(1)
        val weakest = living.minByOrNull(AutopilotMember::hitPointShare)
        val dead = members.dead(party).firstOrNull()
        val (slug, target) =
            with(AutopilotBattleItems) {
                when {
                    dead != null && GEM_OF_RESURRECTION in owned -> GEM_OF_RESURRECTION to dead
                    partyShare < CURE_BELOW && STAFF_OF_CURING in owned -> STAFF_OF_CURING to null
                    weakest != null && weakest.hitPointShare < ITEM_HEAL_BELOW && STAFF_OF_HEALING in owned -> STAFF_OF_HEALING to weakest
                    GEM_OF_BLINDNESS in owned -> GEM_OF_BLINDNESS to null
                    standing >= GROUP && KAELS_AXE in owned -> KAELS_AXE to null
                    standing >= GROUP && HOLY_HAND_GRENADE in owned -> HOLY_HAND_GRENADE to null
                    standing >= 2 && FIRE_WAND in owned -> FIRE_WAND to null
                    standing == 1 && DRUMS_OF_DEATH in owned -> DRUMS_OF_DEATH to null
                    BLESSING_SCROLL in owned -> BLESSING_SCROLL to null
                    else -> return null
                }
            }
        usedItems += slug
        val item = content.items.first { record -> content.slug(record.id) == slug }
        return AutopilotItemUse(content.strings.value(item.displayNameString), target)
    }

    /**
     * Whether the foes cast spells that could kill the weakest living member outright. A monster's spells strike for a
     * fifth of the encounter's top hit points, which grow by three per level of the party's leader, so a boss such as
     * Erethor kills a member with a single Frost Finger. Anti Magic silences them for a round.
     */
    private fun lethalCaster(
        foes: AutopilotFoes?,
        party: PartyState,
    ): Boolean {
        val monster = foes?.monster ?: return false
        if (monster.spellPower <= 0) return false
        val leaderLevel = party.members.firstOrNull()?.level ?: 1
        val spellDamage = (monster.hitPoints.last + leaderLevel * LEVEL_HIT_POINTS) / SPELL_DAMAGE_DIVISOR
        val weakest = party.members.filterNot(CompiledPartyMember::dead).minOfOrNull { member -> member.hitPoints.current } ?: return false
        return spellDamage >= weakest
    }

    /**
     * Whether the party should fight on: the expected battle costs at most [acceptableShare] of
     * its current hit points and leaves every member alive on average.
     */
    fun shouldFight(
        party: PartyState,
        foes: AutopilotFoes,
        acceptableShare: Double = 0.45,
    ): Boolean {
        val living = party.members.filterNot(CompiledPartyMember::dead)
        if (living.isEmpty()) return false
        val partyHitPoints = living.sumOf { member -> member.hitPoints.current }
        val foeHitPoints = foes.hitPoints ?: estimatedHitPoints(party, foes)
        val damageTaken = expectedDamageTaken(living, foes, foeHitPoints)
        val weakest = living.minOf { member -> member.hitPoints.current }
        return damageTaken <= partyHitPoints * acceptableShare && damageTaken / living.size < weakest * 0.75
    }

    /** Remembers the target list ("(A) Knight 67/95 HP") so later round decisions use real hit points. */
    fun observeTargets(lines: List<String>) {
        val hitPoints = lines.mapNotNull(::targetHitPoints)
        if (hitPoints.isNotEmpty()) knownHitPoints = hitPoints
    }

    /** The key of the living target with the fewest hit points. */
    fun weakestTarget(lines: List<String>): Char? =
        lines
            .mapNotNull { line ->
                targetHitPointsPattern.find(line)?.let { match ->
                    match.groupValues[1].single() to
                        match.groupValues[2].toInt()
                }
            }.minByOrNull { (_, hitPoints) -> hitPoints }
            ?.first

    /** Reads "You face" / "You still face" followed by "<count> <name>" from the battle text. */
    fun foes(lines: List<String>): AutopilotFoes? {
        val header = lines.indexOfFirst { line -> line.trim() == "You face" || line.trim() == "You still face" }
        if (header < 0) return null
        // "You face" opens a new battle, so hit points remembered from the previous one no longer apply.
        if (lines[header].trim() == "You face") knownHitPoints = null
        val match = foeLinePattern.find(lines.getOrNull(header + 1)?.trim().orEmpty()) ?: return null
        val count = match.groupValues[1].toInt()
        val name = match.groupValues[2]
        val monster = monstersByName.firstOrNull { (monsterName, _) -> name.startsWith(monsterName) }?.second
        return AutopilotFoes(count, monster, knownHitPoints?.takeIf { known -> known.size == count })
    }

    private fun expectedDamageTaken(
        living: List<CompiledPartyMember>,
        foes: AutopilotFoes,
        foeHitPoints: List<Int>,
    ): Double {
        val attack = rules.playerRound.attack
        val partyDamagePerRound =
            living.sumOf { member ->
                hitChance(member.weaponSkill + attack.hitBonus) *
                    (weaponDamage(member) + attack.damageSpreadMax / 2.0 + member.hitPoints.current / attack.hpDefenseDivisor)
            }
        if (partyDamagePerRound <= 0.0) return Double.MAX_VALUE
        val melee = rules.monsterRound.melee
        val weaponSkill = foes.monster?.weaponSkill ?: DEFAULT_WEAPON_SKILL
        val spellChance =
            foes.monster
                ?.spellPower
                ?.takeIf { power -> power > 0 }
                ?.let { power -> (rules.monsterRound.spellChanceBasePercent + power / rules.monsterRound.spellPowerDivisor) / 100.0 }
                ?: 0.0
        val armor = living.map(::armorClass).average()
        // Kill the weakest foe first: it stops attacking soonest.
        val remaining =
            foeHitPoints
                .filter { hitPoints -> hitPoints > 0 }
                .sorted()
                .map(Int::toDouble)
                .toMutableList()
        var taken = 0.0
        var rounds = 0
        while (remaining.isNotEmpty() && rounds < MAX_ROUNDS) {
            taken +=
                remaining.sumOf { hitPoints ->
                    val hit = maxOf(melee.damageMin.toDouble(), hitPoints / melee.damageDivisor + melee.damageSpreadMax / 2.0 - armor)
                    hitChance(weaponSkill + melee.hitBonus) * hit * (1 - spellChance) + spellChance * SPELL_DAMAGE
                }
            var damage = partyDamagePerRound
            while (damage > 0 && remaining.isNotEmpty()) {
                val dealt = minOf(damage, remaining[0])
                remaining[0] -= dealt
                damage -= dealt
                if (remaining[0] <= 0.0) remaining.removeAt(0)
            }
            rounds++
        }
        return if (remaining.isEmpty()) taken else Double.MAX_VALUE
    }

    /** Estimated hit points for a fresh encounter, from the monster's range scaled like the encounter formulas. */
    private fun estimatedHitPoints(
        party: PartyState,
        foes: AutopilotFoes,
    ): List<Int> {
        val range = foes.monster?.hitPoints ?: defaultHitPoints
        val leaderLevel = party.members.firstOrNull()?.level ?: 1
        val averageLevel = party.members.map(CompiledPartyMember::level).average()
        val low = range.first + (averageLevel * 2).toInt() + if (leaderLevel > 2) leaderLevel * 2 else 0
        val high = range.last + leaderLevel * 3
        return List(foes.count) { (low + high) / 2 }
    }

    private fun targetHitPoints(line: String): Int? = targetHitPointsPattern.find(line)?.let { match -> match.groupValues[2].toInt() }

    private fun weaponDamage(member: CompiledPartyMember): Int =
        content.items.firstOrNull { item -> item.id == member.equipment.weapon }?.weaponDamage ?: 1

    private fun armorClass(member: CompiledPartyMember): Int =
        content.items.firstOrNull { item -> item.id == member.equipment.armor }?.armorClass ?: 0

    private fun hitChance(chance: Int): Double = chance.coerceIn(0, 100) / 100.0

    private companion object {
        const val HOPELESS_AFTER_FAILED_ESCAPES = 2
        const val FIRE_CLOUD = "Fire Cloud"
        const val FROST_FINGER = "Frost Finger"
        const val ANTI_MAGIC = "Anti Magic"
        const val LEVEL_HIT_POINTS = 3
        const val SPELL_DAMAGE_DIVISOR = 5
        const val CURE_BELOW = 0.5
        const val ITEM_HEAL_BELOW = 0.3
        const val GROUP = 3
        const val MAX_ROUNDS = 40
        const val DEFAULT_WEAPON_SKILL = 60
        const val SPELL_DAMAGE = 25.0
        val defaultHitPoints = 50..100
        val foeLinePattern = Regex("""^(\d+)\s+(.+)$""")
        val targetHitPointsPattern = Regex("""^\s*\(([A-Z])\)\s.*?(\d+)/\d+ HP""")
    }
}

/** The magic items the autopilot knows how to use in battle, as item slugs. */
internal object AutopilotBattleItems {
    const val GEM_OF_RESURRECTION = "gem_of_resurrection"
    const val STAFF_OF_CURING = "staff_of_curing"
    const val STAFF_OF_HEALING = "staff_of_healing"
    const val GEM_OF_BLINDNESS = "gem_of_blindness"
    const val KAELS_AXE = "kaels_axe"
    const val HOLY_HAND_GRENADE = "holy_hand_grenade"
    const val FIRE_WAND = "fire_wand"
    const val DRUMS_OF_DEATH = "drums_of_death"
    const val BLESSING_SCROLL = "blessing_scroll"

    /** The items worth carrying into hard fights, the most decisive first. */
    val keepOrder =
        listOf(
            GEM_OF_BLINDNESS,
            STAFF_OF_CURING,
            GEM_OF_RESURRECTION,
            KAELS_AXE,
            HOLY_HAND_GRENADE,
            DRUMS_OF_DEATH,
            FIRE_WAND,
            STAFF_OF_HEALING,
            BLESSING_SCROLL,
        )
}
