/** Renderer-neutral runtime input, screen, and party-view contracts. */

package rylween.runtime

import rylween.compiled.ExternalRuntimeCastle
import rylween.dsl.impl.Gender
import rylween.dsl.impl.PcClass
import rylween.dsl.impl.Race

/**
 * Renderer-neutral input consumed by the compiled-bundle runtime loop.
 *
 * The ANSI runtime backs this with a terminal. Browser runtimes can back it
 * with WebSocket queues without reimplementing command semantics.
 */
interface RuntimeInputPort {
    val exhausted: Boolean

    fun pause(ms: Int)

    fun readCommand(): Char?

    fun readInt(maxDigits: Int = 6): Int?

    fun readText(maxLength: Int? = null): String?
}

/**
 * Shared normalization from transport key tokens to the runtime's current
 * single-character command contract.
 *
 * Browser runtimes may report symbolic key names such as ARROW_UP while the
 * local terminal reports already-decoded keypad tokens. Keep that translation
 * here so transports do not grow their own game-command tables.
 */
object RuntimeInputTokens {
    const val ESCAPE_COMMAND: Char = '\u001B'

    fun commandChar(token: String): Char? {
        if (token == " ") return ' '
        val normalized = token.trim()
        if (normalized.isEmpty()) return '\n'
        return when (normalized.uppercase()) {
            "ARROW_UP",
            "UP",
            -> '8'
            "ARROW_DOWN",
            "DOWN",
            -> '2'
            "ARROW_LEFT",
            "LEFT",
            -> '4'
            "ARROW_RIGHT",
            "RIGHT",
            -> '6'
            "ENTER",
            "RETURN",
            -> '\n'
            "ESCAPE" -> ESCAPE_COMMAND
            "SPACE" -> ' '
            else -> normalized.singleOrNull()?.uppercaseChar()
        }
    }
}

/**
 * Renderer-neutral screen consumed by the compiled-bundle runtime loop.
 *
 * Implementations choose how to turn the compiled runtime state into pixels,
 * ANSI cells, logs, or test snapshots.
 */
interface RuntimeScreenPort {
    val textColumns: Int?
    val documentColumns: Int? get() = textColumns
    val documentPageLines: Int? get() = null

    fun bootFrame()

    fun showStaticScreen(
        screenSlug: String,
        runtime: RuntimeState,
    )

    fun showPagedStaticScreen(
        screenSlug: String,
        runtime: RuntimeState,
        advance: () -> Char?,
    )

    fun showDocumentPage(
        title: String,
        subtitle: String,
        lines: List<String>,
        runtime: RuntimeState,
    ) {
        writeText(lines)
    }

    fun showTitleMenu(lines: List<String>) {
        bootFrame()
        writeText(lines)
    }

    fun showCharacterPortrait(preview: RuntimeCharacterPortraitPreview?): Unit = Unit

    fun showPartyPage(
        page: RuntimePartyPageView,
        pauseLine: String,
    ) {
        writeText(page.lines + "" + pauseLine)
    }

    fun renderWilderness(
        runtime: RuntimeState,
        world: WorldMap,
    )

    fun showDebugWorldMap(
        runtime: RuntimeState,
        world: WorldMap,
        advance: () -> Char?,
    )

    fun renderDungeon(runtime: RuntimeState)

    fun renderCastle(
        runtime: RuntimeState,
        castle: ExternalRuntimeCastle,
    )

    /**
     * Replaces the renderer's owned text panel with blocking prose, menus, prompts,
     * or scene text. This clears any sticky non-blocking notice previously shown
     * with [showMessage].
     */
    fun writeText(lines: List<String>)

    /**
     * Adds blocking prose to the currently displayed text panel without changing
     * the screen mode. Renderers that cannot append efficiently may redraw the
     * combined panel, but should preserve the already displayed lines.
     */
    fun appendText(lines: List<String>) {
        writeText(lines)
    }

    /**
     * Shows a non-blocking notice produced by free-roaming runtime actions, such
     * as impassable terrain, terrain transition descriptions, or upkeep warnings.
     *
     * Renderers should keep this in their normal text/notice region until the next
     * [writeText], clear-text command, or major mode change. It must not pause the
     * game and must not cover the map/image region with an unrelated overlay.
     */
    fun showMessage(lines: List<String>)

    fun updateStatus(runtime: RuntimeState)

    fun writeViewCommand(command: RuntimeViewCommand)

    fun showImage(imageSlug: String)

    fun redrawCurrentView(lines: List<String>)

    fun flashLogLine(line: String)
}

data class RuntimeCharacterPortraitPreview(
    val memberNumber: Int,
    val memberName: String?,
    val gender: Gender?,
    val genderLabel: String?,
    val race: Race?,
    val raceLabel: String?,
    val pcClass: PcClass?,
    val classLabel: String?,
    val imageSlug: String,
)

enum class RuntimePartyPageKind { Summary, Member, Inventory }

data class RuntimePartyPageView(
    val kind: RuntimePartyPageKind,
    val lines: List<String>,
    val summary: RuntimePartySummaryView? = null,
    val member: RuntimePartyMemberView? = null,
    val inventory: RuntimePartyInventoryView? = null,
) {
    val memberIndex: Int? get() = member?.index
}

data class RuntimePartySummaryView(
    val partyName: String,
    val gold: Int,
    val food: Int,
    val torches: Int,
    val moves: Int,
    val rest: Int,
    val kills: Int,
    val memberCount: Int,
)

data class RuntimePartyMemberView(
    val index: Int,
    val name: String,
    val gender: String,
    val race: String,
    val pcClass: String,
    val weaponSkill: Int,
    val health: Int,
    val wisdom: Int,
    val charisma: Int,
    val hitPoints: Int,
    val hitPointMax: Int,
    val mana: Int,
    val manaMax: Int,
    val level: Int,
    val experience: Int,
    val weapon: String,
    val armor: String,
    val status: String,
)

data class RuntimePartyInventoryView(
    val entries: List<String>,
)
