/**
 * How much of each resource the party can carry.
 *
 * Capacity is the realm's base amount plus the bonuses of carried items, such
 * as a donkey. Shops cap purchases at it, hunting stops at it, and an item that
 * carried food leaving the inventory leaves the food it carried behind.
 */

package rylween.runtime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.ExternalRuntimeRealm
import rylween.compiled.ExternalRuntimeSystems
import rylween.dsl.impl.Resource

/** The party after an inventory change, and the text to show when it had to leave food behind. */
internal data class CarriedFood(
    val party: PartyState,
    val notice: List<String>,
)

internal fun PartyState.resourceAmount(resource: Resource): Int =
    when (resource) {
        Resource.Food -> resources.food
        Resource.Torches -> resources.torches
    }

/** The most of [resource] the party can carry. A resource without an authored base is not limited. */
internal fun PartyState.resourceCapacity(
    content: CompiledContentBundle,
    realm: ExternalRuntimeRealm,
    resource: Resource,
): Int {
    val base = realm.baseResourceCapacity[resource] ?: resourceAmount(resource)
    val itemBonus =
        inventory
            .mapNotNull { slot -> slot.item?.let { item -> content.items.firstOrNull { record -> record.id == item } } }
            .sumOf { item -> item.resourceCapacityBonus[resource] ?: 0 }
    return base + itemBonus
}

/** Leaves behind the food the party can no longer carry, for after an item that carried food left the inventory. */
internal fun PartyState.leaveExcessFood(
    content: CompiledContentBundle,
    systems: ExternalRuntimeSystems,
    textColumns: Int?,
): CarriedFood {
    val capacity = resourceCapacity(content, systems.realm, Resource.Food)
    if (resources.food <= capacity) return CarriedFood(this, emptyList())
    return CarriedFood(
        party = withResource(Resource.Food, capacity),
        notice = systems.prompts.party.dropCapacityReduced.render({ null }, textColumns),
    )
}
