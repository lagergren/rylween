// Shared scalar/member bindings used by authored script expression evaluators.

package rylween.runtime

import rylween.dsl.*

private data class RealmScalarBinding(
    val read: RuntimeSessionState.() -> Int,
    val write: RuntimeSessionState.(Int) -> RuntimeSessionState,
)

private val runtimeFlagReaders: Map<String, RuntimeSessionState.() -> Boolean> = mapOf(
    "in_dungeon" to { realm.currentMode == RuntimeMode.Dungeon },
)

private val realmScalarBindings: Map<String, RealmScalarBinding> = mapOf(
    "day" to RealmScalarBinding(
        read = { realm.day },
        write = { value -> withRealm(realm.copy(day = value)) },
    ),
    "hour" to RealmScalarBinding(
        read = { realm.hour },
        write = { value -> withRealm(realm.copy(hour = value.positiveMod(24))) },
    ),
    "minute" to RealmScalarBinding(
        read = { realm.minute },
        write = { value -> withRealm(realm.copy(minute = value.positiveMod(60))) },
    ),
    "last_played_day" to RealmScalarBinding(
        read = { realm.lastPlayedDay },
        write = { value -> withRealm(realm.copy(lastPlayedDay = value)) },
    ),
    "party_member_max" to RealmScalarBinding(
        read = { realm.partyMemberMax },
        write = { value -> withRealm(realm.copy(partyMemberMax = value.coerceAtLeast(0))) },
    ),
    "banished" to RealmScalarBinding(
        read = { if (realm.banished) 1 else 0 },
        write = { value -> withRealm(realm.copy(banished = value != 0)) },
    ),
)

private val partyScalarReaders: Map<String, PartyState.() -> Int> = mapOf(
    "gold" to { resources.gold },
    "torches" to { resources.torches },
    "food" to { resources.food },
    "time_left" to { resources.timeLeft },
    "rested_hours" to { resources.restedHours },
    "member_count" to { counters.memberCount ?: members.count { !it.dead } },
)

private val partyScalarWriters: Map<String, PartyState.(Int) -> PartyState> = mapOf(
    "gold" to { value -> copy(resources = resources.copy(gold = value.coerceAtLeast(0))) },
    "torches" to { value -> copy(resources = resources.copy(torches = value.coerceAtLeast(0))) },
    "food" to { value -> copy(resources = resources.copy(food = value.coerceAtLeast(0))) },
    "time_left" to { value -> copy(resources = resources.copy(timeLeft = value)) },
    "rested_hours" to { value -> copy(resources = resources.copy(restedHours = value)) },
    "member_count" to { value -> copy(counters = counters.copy(memberCount = value.coerceAtLeast(0))) },
)

private val memberFieldReaders: Map<String, PartyMemberState.() -> Int> = mapOf(
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

private val memberFieldWriters: Map<String, PartyMemberState.(Int) -> PartyMemberState> = mapOf(
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

private val memberFlagReaders: Map<String, PartyMemberState.() -> Boolean> = mapOf(
    "dead" to { dead },
    "spellcaster" to { !dead && pcClass in setOf(PcClass.Mage, PcClass.Priest) },
)

internal fun scriptMemberFlagKnown(name: String): Boolean =
    name in memberFlagReaders

internal fun RuntimeSessionState.scriptRuntimeFlag(name: String): Boolean =
    runtimeFlagReaders[name]?.invoke(this) ?: false

internal fun RuntimeSessionState.scriptRealmScalar(name: String, locals: Map<String, Int>): Int =
    scriptRealmScalarOrNull(name) ?: locals[name] ?: 0

internal fun RuntimeSessionState.scriptRealmScalarOrNull(name: String): Int? =
    realmScalarBindings[name]?.read?.invoke(this)

internal fun RuntimeSessionState.withScriptRealmScalar(name: String, value: Int): RuntimeSessionState? =
    realmScalarBindings[name]?.write?.invoke(this, value)

internal fun PartyState.scriptPartyScalar(name: String): Int =
    scriptPartyScalarOrNull(name) ?: 0

internal fun PartyState.scriptPartyScalarOrNull(name: String): Int? =
    partyScalarReaders[name]?.invoke(this)

internal fun PartyState.withScriptPartyScalar(name: String, value: Int): PartyState =
    withScriptPartyScalarOrNull(name, value) ?: this

internal fun PartyState.withScriptPartyScalarOrNull(name: String, value: Int): PartyState? =
    partyScalarWriters[name]?.invoke(this, value)

internal fun PartyState.scriptMemberField(memberNumber: Int, field: String): Int =
    scriptMemberIndexField(memberNumber - 1, field)

internal fun PartyState.scriptMemberIndexField(memberIndex: Int, field: String): Int =
    members.getOrNull(memberIndex)?.scriptMemberFieldOrNull(field) ?: 0

internal fun PartyMemberState.scriptMemberFieldOrNull(field: String): Int? =
    memberFieldReaders[field]?.invoke(this)

internal fun PartyState.scriptMemberFlag(memberNumber: Int, name: String): Boolean =
    scriptMemberIndexFlag(memberNumber - 1, name)

internal fun PartyState.scriptMemberIndexFlag(memberIndex: Int, name: String): Boolean =
    members.getOrNull(memberIndex)?.scriptMemberFlagOrNull(name) ?: false

internal fun PartyMemberState.scriptMemberFlagOrNull(name: String): Boolean? =
    memberFlagReaders[name]?.invoke(this)

internal fun PartyState.withScriptMemberField(memberNumber: Int, field: String, value: Int): PartyState =
    withScriptMemberIndexField(memberNumber - 1, field, value)

internal fun PartyState.withScriptMemberIndexField(memberIndex: Int, field: String, value: Int): PartyState {
    val member = members.getOrNull(memberIndex) ?: return this
    val updated = member.withScriptMemberFieldOrNull(field, value) ?: member
    return copy(members = members.mapIndexed { index, existing -> if (index == memberIndex) updated else existing })
}

internal fun PartyMemberState.withScriptMemberFieldOrNull(field: String, value: Int): PartyMemberState? =
    memberFieldWriters[field]?.invoke(this, value)

private fun Int.positiveMod(divisor: Int): Int =
    ((this % divisor) + divisor) % divisor
