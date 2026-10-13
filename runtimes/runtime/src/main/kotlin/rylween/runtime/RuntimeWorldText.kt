/**
 * Runtime text for authored world references: towns, roles, NPCs, and map locations.
 *
 * `ScriptExecutor.kt` renders these references in conversation, rumor, and
 * journal text, and `ShopService.kt` renders them in shop interlude rumors.
 * Positions come from the [RuntimeState] each caller passes in, so an NPC that
 * has moved is reported where it is now.
 */

package rylween.runtime

import rylween.compiled.CompiledCharacter
import rylween.compiled.CompiledCharacterPlacement
import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledWorldPosition
import rylween.compiled.ExternalRuntimeTextBlock
import rylween.compiled.ExternalRuntimeTextPart
import rylween.dsl.impl.GridPoint

/** Resolves authored town, role, NPC, and location references in [content] to player-facing text. */
internal class RuntimeWorldText(
    private val content: CompiledContentBundle,
) {
    /**
     * Resolves a town, role, or NPC runtime text [part] against [runtime], or returns `null` for any other kind.
     * [journal] selects the journal wording, which spells out town coordinates.
     */
    fun value(
        part: ExternalRuntimeTextPart.Runtime,
        runtime: RuntimeState,
        journal: Boolean,
    ): String? =
        when (part.kind) {
            "townName" -> part.args["town"]?.let(::townNameBySlug)
            "fuzzyTownLocation" -> part.args["town"]?.let { town -> townLocation(town, part.args["radius"]?.toIntOrNull() ?: 0) }
            "roleName" -> part.args["role"]?.let(::roleName)
            "roleJob" -> part.args["role"]?.let(::roleJob)
            "roleLocation" ->
                part.args["role"]?.let { role -> if (journal) roleJournalLocation(role, runtime) else roleLocation(role, runtime) }

            "npcName" -> part.args["npc"]?.let(::npcName)
            "npcJob" -> part.args["npc"]?.let(::npcJob)
            "npcLocation" -> part.args["npc"]?.let { npc -> if (journal) npcJournalLocation(npc, runtime) else npcLocation(npc, runtime) }
            else -> null
        }

    /** Whether [text] names at least one place precisely enough to be recorded in the journal. */
    fun hasExactJournalLocation(
        text: ExternalRuntimeTextBlock,
        runtime: RuntimeState,
    ): Boolean =
        text.lines
            .flatten()
            .filterIsInstance<ExternalRuntimeTextPart.Runtime>()
            .any { part ->
                when (part.kind) {
                    "roleLocation" ->
                        part.args["role"]
                            ?.let(::roleCharacter)
                            ?.position(runtime)
                            ?.hasExactJournalCoordinate() == true

                    "npcLocation" ->
                        part.args["npc"]
                            ?.let(::npcCharacter)
                            ?.position(runtime)
                            ?.hasExactJournalCoordinate() == true

                    "fuzzyTownLocation" -> part.args["radius"]?.toIntOrNull() == 0
                    "locationCoordinate" -> true
                    else -> false
                }
            }

    private fun roleName(roleSlug: String): String? =
        roleCharacter(roleSlug)?.let { character -> content.strings.value(character.nameString) }

    fun roleName(role: CompiledHandleRef): String =
        roleCharacter(role)
            ?.let { character -> content.strings.value(character.nameString) }
            ?: content.slug(role)

    private fun roleJob(roleSlug: String): String? = roleCharacter(roleSlug)?.let(::characterJob)

    fun roleJob(role: CompiledHandleRef): String =
        roleCharacter(role)
            ?.let(::characterJob)
            ?: content.slug(role).readableJob()

    private fun roleLocation(
        roleSlug: String,
        runtime: RuntimeState,
    ): String? =
        roleCharacter(roleSlug)
            ?.position(runtime)
            ?.let(::positionText)

    private fun roleJournalLocation(
        roleSlug: String,
        runtime: RuntimeState,
    ): String? =
        roleCharacter(roleSlug)
            ?.position(runtime)
            ?.let(::journalPositionText)

    fun roleLocation(
        role: CompiledHandleRef,
        runtime: RuntimeState,
    ): String =
        roleCharacter(role)
            ?.position(runtime)
            ?.let(::positionText)
            ?: content.slug(role)

    fun roleJournalLocation(
        role: CompiledHandleRef,
        runtime: RuntimeState,
    ): String =
        roleCharacter(role)
            ?.position(runtime)
            ?.let(::journalPositionText)
            ?: content.slug(role)

    private fun npcName(npcSlug: String): String? = npcCharacter(npcSlug)?.let { character -> content.strings.value(character.nameString) }

    fun npcName(npc: CompiledHandleRef): String =
        npcCharacter(npc)
            ?.let { character -> content.strings.value(character.nameString) }
            ?: content.slug(npc)

    private fun npcJob(npcSlug: String): String? = npcCharacter(npcSlug)?.let(::characterJob)

    fun npcJob(npc: CompiledHandleRef): String =
        npcCharacter(npc)
            ?.let(::characterJob)
            ?: content.slug(npc).readableJob()

    private fun npcLocation(
        npcSlug: String,
        runtime: RuntimeState,
    ): String? =
        npcCharacter(npcSlug)
            ?.position(runtime)
            ?.let(::positionText)

    fun npcLocation(
        npc: CompiledHandleRef,
        runtime: RuntimeState,
    ): String =
        npcCharacter(npc)
            ?.position(runtime)
            ?.let(::positionText)
            ?: content.slug(npc)

    private fun npcJournalLocation(
        npcSlug: String,
        runtime: RuntimeState,
    ): String? =
        npcCharacter(npcSlug)
            ?.position(runtime)
            ?.let(::journalPositionText)

    fun npcJournalLocation(
        npc: CompiledHandleRef,
        runtime: RuntimeState,
    ): String =
        npcCharacter(npc)
            ?.position(runtime)
            ?.let(::journalPositionText)
            ?: content.slug(npc)

    private fun roleCharacter(roleSlug: String): CompiledCharacter? {
        val role = content.refOrNull(CompiledHandleKind.Role, roleSlug) ?: return null
        return roleCharacter(role)
    }

    private fun roleCharacter(role: CompiledHandleRef): CompiledCharacter? =
        content.characters.firstOrNull { character -> role in character.roles }

    private fun npcCharacter(npcSlug: String): CompiledCharacter? {
        val npc = content.refOrNull(CompiledHandleKind.Npc, npcSlug) ?: return null
        return npcCharacter(npc)
    }

    private fun npcCharacter(npc: CompiledHandleRef): CompiledCharacter? =
        content.characters.firstOrNull { character -> character.id == npc }

    private fun characterJob(character: CompiledCharacter): String = content.slug(character.job).readableJob()

    private fun townNameBySlug(townSlug: String): String =
        content.refOrNull(CompiledHandleKind.Town, townSlug)?.let { town -> content.townName(town) } ?: townSlug

    private fun townLocation(
        townSlug: String,
        radius: Int,
    ): String? {
        val town = content.refOrNull(CompiledHandleKind.Town, townSlug) ?: return null
        val position = content.towns.firstOrNull { current -> current.id == town }?.position ?: return null
        return pointText(fuzzyPoint(position, townSlug, radius))
    }

    /** The map coordinate of the location named by [locationSlug] in [runtime], or `null` for an unknown slug. */
    fun locationTextBySlug(
        locationSlug: String,
        runtime: RuntimeState,
    ): String? = content.refOrNull(CompiledHandleKind.Location, locationSlug)?.let { location -> locationText(location, runtime) }

    /** The map coordinate where [location] is placed in [runtime], or its slug while it is unplaced. */
    fun locationText(
        location: CompiledHandleRef,
        runtime: RuntimeState,
    ): String =
        runtime.locations
            .firstOrNull { placement -> placement.location == location }
            ?.point
            ?.let(::pointText)
            ?: content.slug(location)

    private fun positionText(position: CompiledWorldPosition): String =
        when (position) {
            is CompiledWorldPosition.Town -> "in ${content.townName(position.town)}"
            is CompiledWorldPosition.Wilderness -> "at ${pointText(position.point)}"
        }

    private fun CompiledWorldPosition.hasExactJournalCoordinate(): Boolean =
        when (this) {
            is CompiledWorldPosition.Town -> content.towns.any { town -> town.id == this.town }
            is CompiledWorldPosition.Wilderness -> true
        }

    private fun journalPositionText(position: CompiledWorldPosition): String =
        when (position) {
            is CompiledWorldPosition.Town -> {
                val townName = content.townName(position.town)
                val coordinate =
                    content.towns
                        .firstOrNull { town -> town.id == position.town }
                        ?.position
                        ?.let(::pointText)
                coordinate?.let { "in $townName at $it" } ?: "in $townName"
            }

            is CompiledWorldPosition.Wilderness -> "at ${pointText(position.point)}"
        }

    private fun pointText(point: GridPoint): String = "${point.x}x${point.y}"

    private fun fuzzyPoint(
        point: GridPoint,
        slug: String,
        radius: Int,
    ): GridPoint =
        when {
            radius <= 0 -> point
            else ->
                GridPoint(
                    x = point.x + stableOffset("$slug:x", radius),
                    y = point.y + stableOffset("$slug:y", radius),
                )
        }

    private fun stableOffset(
        key: String,
        radius: Int,
    ): Int {
        val span = radius * 2 + 1
        val hash = key.fold(0) { acc, char -> acc * 31 + char.code }
        return Math.floorMod(hash, span) - radius
    }

    private fun CompiledContentBundle.townName(ref: CompiledHandleRef): String =
        towns.firstOrNull { town -> town.id == ref }?.let { town -> strings.value(town.nameString) } ?: slug(ref)
}

/** Where this character is now: its live NPC placement in [runtime], else its fixed authored placement. */
internal fun CompiledCharacter.position(runtime: RuntimeState): CompiledWorldPosition? =
    runtime.npcs.firstOrNull { placement -> placement.npc == id }?.position
        ?: (placement as? CompiledCharacterPlacement.Fixed)?.position

/** The characters standing on the party's wilderness square, in authored order. */
internal fun RuntimeState.wildernessNpcsHere(content: CompiledContentBundle): List<CompiledCharacter> {
    val here = CompiledWorldPosition.Wilderness(party.position)
    return content.characters.filter { character -> character.position(this) == here }
}

/** Resolves the [kind] handle whose slug is [slug], or `null` when the bundle has no such handle. */
internal fun CompiledContentBundle.refOrNull(
    kind: CompiledHandleKind,
    slug: String,
): CompiledHandleRef? = strings.idOf(slug)?.let { slugString -> handles.refOrNull(kind, slugString) }
