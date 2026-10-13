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

    /** Remembers the target list ("A. Knight 67/95 HP") so later round decisions use real hit points. */
    fun observeTargets(lines: List<String>) {
        val hitPoints = lines.mapNotNull(::targetHitPoints)
        if (hitPoints.isNotEmpty()) knownHitPoints = hitPoints
    }

    /** The key of the living target with the fewest hit points. */
    fun weakestTarget(lines: List<String>): Char? =
        lines
            .mapNotNull { line -> TARGET_HP.find(line)?.let { match -> line.trimStart().first() to match.groupValues[1].toInt() } }
            .minByOrNull { (_, hitPoints) -> hitPoints }
            ?.first

    /** Reads "You face" / "You still face" followed by "<count> <name>" from the battle text. */
    fun foes(lines: List<String>): AutopilotFoes? {
        val header = lines.indexOfFirst { line -> line.trim() == "You face" || line.trim() == "You still face" }
        if (header < 0) return null
        // "You face" opens a new battle, so hit points remembered from the previous one no longer apply.
        if (lines[header].trim() == "You face") knownHitPoints = null
        val match = FOE_LINE.find(lines.getOrNull(header + 1)?.trim().orEmpty()) ?: return null
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
        val range = foes.monster?.hitPoints ?: DEFAULT_HIT_POINTS
        val leaderLevel = party.members.firstOrNull()?.level ?: 1
        val averageLevel = party.members.map(CompiledPartyMember::level).average()
        val low = range.first + (averageLevel * 2).toInt() + if (leaderLevel > 2) leaderLevel * 2 else 0
        val high = range.last + leaderLevel * 3
        return List(foes.count) { (low + high) / 2 }
    }

    private fun targetHitPoints(line: String): Int? = TARGET_HP.find(line)?.let { match -> match.groupValues[1].toInt() }

    private fun weaponDamage(member: CompiledPartyMember): Int =
        content.items.firstOrNull { item -> item.id == member.equipment.weapon }?.weaponDamage ?: 1

    private fun armorClass(member: CompiledPartyMember): Int =
        content.items.firstOrNull { item -> item.id == member.equipment.armor }?.armorClass ?: 0

    private fun hitChance(chance: Int): Double = chance.coerceIn(0, 100) / 100.0

    private companion object {
        const val MAX_ROUNDS = 40
        const val DEFAULT_WEAPON_SKILL = 60
        const val SPELL_DAMAGE = 25.0
        val DEFAULT_HIT_POINTS = 50..100
        val FOE_LINE = Regex("""^(\d+)\s+(.+)$""")
        val TARGET_HP = Regex("""^\s*[A-Z]\.\s.*?(\d+)/\d+ HP""")
    }
}
