/**
 * Shared scalar, flag, member, and inventory bindings for script expressions.
 *
 * The authored Op interpreter and future compiled VM use these helpers to keep
 * lvalue and query semantics consistent across wilderness, town, battle, and
 * script-continuation contexts.
 */

package rylween.runtime

import rylween.dsl.PartyMemberState
import rylween.dsl.PartyState
import rylween.dsl.PcClass
import rylween.dsl.RuntimeMode
import rylween.dsl.of
import rylween.dsl.sourceId

private data class RealmScalarBinding(
    val read: RuntimeSessionState.() -> Int,
    val write: RuntimeSessionState.(Int) -> RuntimeSessionState,
)

private val runtimeFlagReaders: Map<String, RuntimeSessionState.() -> Boolean> =
    mapOf(
        "in_dungeon" to { realm.currentMode == RuntimeMode.Dungeon },
    )

private val realmScalarBindings: Map<String, RealmScalarBinding> =
    mapOf(
        "day" to
            RealmScalarBinding(
                read = { realm.day },
                write = { value -> withRealm(realm.copy(day = value)) },
            ),
        "hour" to
            RealmScalarBinding(
                read = { realm.hour },
                write = { value -> withRealm(realm.copy(hour = value.positiveMod(24))) },
            ),
        "minute" to
            RealmScalarBinding(
                read = { realm.minute },
                write = { value -> withRealm(realm.copy(minute = value.positiveMod(60))) },
            ),
        "last_played_day" to
            RealmScalarBinding(
                read = { realm.lastPlayedDay },
                write = { value -> withRealm(realm.copy(lastPlayedDay = value)) },
            ),
        "party_member_max" to
            RealmScalarBinding(
                read = { realm.partyMemberMax },
                write = { value -> withRealm(realm.copy(partyMemberMax = value.coerceAtLeast(0))) },
            ),
        "banished" to
            RealmScalarBinding(
                read = { if (realm.banished) 1 else 0 },
                write = { value -> withRealm(realm.copy(banished = value != 0)) },
            ),
    )

private val partyScalarReaders: Map<String, PartyState.() -> Int> =
    mapOf(
        "gold" to { resources.gold },
        "torches" to { resources.torches },
        "food" to { resources.food },
        "time_left" to { resources.timeLeft },
        "rested_hours" to { resources.restedHours },
        "member_count" to { counters.memberCount ?: members.count { !it.dead } },
    )

private val partyScalarWriters: Map<String, PartyState.(Int) -> PartyState> =
    mapOf(
        "gold" to { value -> copy(resources = resources.copy(gold = value.coerceAtLeast(0))) },
        "torches" to { value -> copy(resources = resources.copy(torches = value.coerceAtLeast(0))) },
        "food" to { value -> copy(resources = resources.copy(food = value.coerceAtLeast(0))) },
        "time_left" to { value -> copy(resources = resources.copy(timeLeft = value)) },
        "rested_hours" to { value -> copy(resources = resources.copy(restedHours = value)) },
        "member_count" to { value -> copy(counters = counters.copy(memberCount = value.coerceAtLeast(0))) },
    )

private val memberFieldReaders: Map<String, PartyMemberState.() -> Int> =
    mapOf(
        "hit_points" to { hitPoints.current },
        "health" to { health },
        "magic" to { mana.current },
        "magic_max" to { mana.max },
        "level" to { level },
        "wisdom" to { wisdom },
        "charisma" to { charisma },
        "weapon_skill" to { weaponSkill },
        "class" to { pcClass.sourceId },
        "experience" to { experience },
        "dead" to { if (dead) 1 else 0 },
    )

private val memberFieldWriters: Map<String, PartyMemberState.(Int) -> PartyMemberState> =
    mapOf(
        "hit_points" to { value -> copy(hitPoints = hitPoints.copy(current = value.coerceAtLeast(0))) },
        "health" to { value ->
            val healthValue = value.coerceAtLeast(0)
            copy(health = healthValue, hitPoints = hitPoints.copy(max = healthValue))
        },
        "magic" to { value -> copy(mana = mana.copy(current = value.coerceAtLeast(0))) },
        "magic_max" to { value ->
            val maxValue = value.coerceAtLeast(0)
            copy(mana = mana.current.coerceAtMost(maxValue) of maxValue)
        },
        "wisdom" to { value -> copy(wisdom = value.coerceAtLeast(0)) },
        "charisma" to { value -> copy(charisma = value.coerceAtLeast(0)) },
        "weapon_skill" to { value -> copy(weaponSkill = value.coerceAtLeast(0)) },
        "level" to { value -> copy(level = value.coerceAtLeast(1)) },
        "experience" to { value -> copy(experience = value.coerceAtLeast(0)) },
        "dead" to { value -> copy(dead = value != 0) },
    )

private val memberFlagReaders: Map<String, PartyMemberState.() -> Boolean> =
    mapOf(
        "dead" to { dead },
        "spellcaster" to { !dead && pcClass in setOf(PcClass.Mage, PcClass.Priest) },
    )

/** Returns true when [name] is a supported member boolean flag in script expressions. */
fun scriptMemberFlagKnown(name: String): Boolean = name in memberFlagReaders

/** Reads a runtime-wide boolean flag by script binding name, defaulting missing flags to false. */
fun RuntimeSessionState.scriptRuntimeFlag(name: String): Boolean = runtimeFlagReaders[name]?.invoke(this) ?: false

/** Reads a realm scalar by script binding name, falling back to local script integers and then zero. */
fun RuntimeSessionState.scriptRealmScalar(
    name: String,
    locals: Map<String, Int>,
): Int = scriptRealmScalarOrNull(name) ?: locals[name] ?: 0

/** Reads a realm scalar by script binding name, or null when no binding exists. */
fun RuntimeSessionState.scriptRealmScalarOrNull(name: String): Int? = realmScalarBindings[name]?.read?.invoke(this)

/** Returns a runtime with the named realm scalar updated, or null when the binding is unknown. */
fun RuntimeSessionState.withScriptRealmScalar(
    name: String,
    value: Int,
): RuntimeSessionState? = realmScalarBindings[name]?.write?.invoke(this, value)

/** Reads a party scalar by script binding name, defaulting missing values to zero. */
fun PartyState.scriptPartyScalar(name: String): Int = scriptPartyScalarOrNull(name) ?: 0

/** Reads a party scalar by script binding name, or null when no binding exists. */
fun PartyState.scriptPartyScalarOrNull(name: String): Int? = partyScalarReaders[name]?.invoke(this)

/** Returns a party with the named scalar updated, leaving the party unchanged when the binding is unknown. */
fun PartyState.withScriptPartyScalar(
    name: String,
    value: Int,
): PartyState = withScriptPartyScalarOrNull(name, value) ?: this

/** Returns a party with the named scalar updated, or null when no binding exists. */
fun PartyState.withScriptPartyScalarOrNull(
    name: String,
    value: Int,
): PartyState? = partyScalarWriters[name]?.invoke(this, value)

/** Reads a one-based party member integer field by script binding name, defaulting missing values to zero. */
fun PartyState.scriptMemberField(
    memberNumber: Int,
    field: String,
): Int = scriptMemberIndexField(memberNumber - 1, field)

/** Reads a zero-based party member integer field by script binding name, defaulting missing values to zero. */
fun PartyState.scriptMemberIndexField(
    memberIndex: Int,
    field: String,
): Int = members.getOrNull(memberIndex)?.scriptMemberFieldOrNull(field) ?: 0

/** Reads a member integer field by script binding name, or null when no binding exists. */
fun PartyMemberState.scriptMemberFieldOrNull(field: String): Int? = memberFieldReaders[field]?.invoke(this)

/** Reads a one-based party member boolean flag by script binding name, defaulting missing values to false. */
fun PartyState.scriptMemberFlag(
    memberNumber: Int,
    name: String,
): Boolean = scriptMemberIndexFlag(memberNumber - 1, name)

/** Reads a zero-based party member boolean flag by script binding name, defaulting missing values to false. */
fun PartyState.scriptMemberIndexFlag(
    memberIndex: Int,
    name: String,
): Boolean = members.getOrNull(memberIndex)?.scriptMemberFlagOrNull(name) ?: false

/** Reads a member boolean flag by script binding name, or null when no binding exists. */
fun PartyMemberState.scriptMemberFlagOrNull(name: String): Boolean? = memberFlagReaders[name]?.invoke(this)

/** Returns a party with a one-based member integer field updated by script binding name. */
fun PartyState.withScriptMemberField(
    memberNumber: Int,
    field: String,
    value: Int,
): PartyState = withScriptMemberIndexField(memberNumber - 1, field, value)

/** Returns a party with a zero-based member integer field updated by script binding name. */
fun PartyState.withScriptMemberIndexField(
    memberIndex: Int,
    field: String,
    value: Int,
): PartyState {
    val member = members.getOrNull(memberIndex) ?: return this
    val updated = member.withScriptMemberFieldOrNull(field, value) ?: member
    return copy(members = members.mapIndexed { index, existing -> if (index == memberIndex) updated else existing })
}

/** Returns a member with an integer field updated by script binding name, or null when no binding exists. */
fun PartyMemberState.withScriptMemberFieldOrNull(
    field: String,
    value: Int,
): PartyMemberState? = memberFieldWriters[field]?.invoke(this, value)

private fun Int.positiveMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor
