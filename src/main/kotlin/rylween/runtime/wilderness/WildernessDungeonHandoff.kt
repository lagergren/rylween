/**
 * Portable dungeon-handoff record shared by compiled bundle snapshots.
 *
 * The old live wilderness session that produced this handoff is archived under
 * `legacy/reference-runtime`. Active code keeps this small DTO so compiled VM
 * continuations, save images, and non-JVM runtimes can describe "enter this
 * dungeon for this many levels" without depending on that session graph.
 */
package rylween.runtime.wilderness

import rylween.dsl.DungeonId

/** Stable data carried when a script or wilderness event transfers control into a dungeon. */
data class WildernessDungeonHandoff(
    val dungeon: DungeonId,
    val levels: Int,
) {
    init {
        require(dungeon.slug.isNotBlank()) { "dungeon handoff requires a dungeon slug" }
        require(levels > 0) { "dungeon handoff levels must be positive" }
    }
}
