/** Compiled-script writes to party member fields, shared by scene scripts and combat scripts. */

package rylween.runtime

import rylween.compiled.CompiledPartyMember
import rylween.dsl.impl.of

/**
 * Returns this member with the compiled member [field] set to [value], or `null` when [field] is not a writable
 * member field.
 *
 * Counters clamp at zero and `level` at one. Writing `health` also sets the hit-point maximum, and writing
 * `magic_max` caps current magic at the new maximum.
 */
internal fun CompiledPartyMember.withCompiledField(
    field: String,
    value: Int,
): CompiledPartyMember? =
    when (field) {
        "hit_points" -> copy(hitPoints = hitPoints.copy(current = value.coerceAtLeast(0)))
        "health" ->
            value.coerceAtLeast(0).let { health ->
                copy(health = health, hitPoints = hitPoints.copy(max = health))
            }

        "magic" -> copy(mana = mana.copy(current = value.coerceAtLeast(0)))
        "magic_max" -> value.coerceAtLeast(0).let { max -> copy(mana = mana.current.coerceAtMost(max) of max) }
        "level" -> copy(level = value.coerceAtLeast(1))
        "wisdom" -> copy(wisdom = value.coerceAtLeast(0))
        "charisma" -> copy(charisma = value.coerceAtLeast(0))
        "weapon_skill" -> copy(weaponSkill = value.coerceAtLeast(0))
        "dexterity" -> copy(dexterity = value.coerceAtLeast(0))
        "experience" -> copy(experience = value.coerceAtLeast(0))
        "dead" -> copy(dead = value != 0)
        else -> null
    }
