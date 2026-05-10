/**
 * Shared ANSI escape constants for terminal frontends.
 *
 * Runtime launchers need
 * cursor control and foreground/background colors. Keeping the escape values in
 * `:terminal` prevents each runtime from carrying its own numeric ANSI
 * table.
 */
package rylween.terminal

/** Canonical ANSI escape sequences and color code constants used by terminal clients. */
object AnsiCodes {
    const val ESC: String = "\u001B"
    const val CSI: String = "$ESC["
    const val RESET: String = "${CSI}0m"

    private const val FG_BLACK_CODE: Int = 30
    private const val FG_RED_CODE: Int = 31
    private const val FG_GREEN_CODE: Int = 32
    private const val FG_YELLOW_CODE: Int = 33
    private const val FG_BLUE_CODE: Int = 34
    private const val FG_MAGENTA_CODE: Int = 35
    private const val FG_CYAN_CODE: Int = 36
    private const val FG_WHITE_CODE: Int = 37

    private const val BG_BLACK_CODE: Int = 40
    private const val BG_BLUE_CODE: Int = 44

    @Suppress("unused")
    const val FG_BLACK: String = "${CSI}${FG_BLACK_CODE}m"
    const val FG_RED: String = "${CSI}${FG_RED_CODE}m"
    const val FG_GREEN: String = "${CSI}${FG_GREEN_CODE}m"
    const val FG_YELLOW: String = "${CSI}${FG_YELLOW_CODE}m"

    @Suppress("unused")
    const val FG_BLUE: String = "${CSI}${FG_BLUE_CODE}m"
    const val FG_MAGENTA: String = "${CSI}${FG_MAGENTA_CODE}m"
    const val FG_CYAN: String = "${CSI}${FG_CYAN_CODE}m"
    const val FG_WHITE: String = "${CSI}${FG_WHITE_CODE}m"
    const val BG_BLACK: String = "${CSI}${BG_BLACK_CODE}m"
    const val BG_BLUE: String = "${CSI}${BG_BLUE_CODE}m"

    private const val COLOR_BLACK = "black"
    private const val COLOR_RED = "red"
    private const val COLOR_GREEN = "green"
    private const val COLOR_YELLOW = "yellow"
    private const val COLOR_BLUE = "blue"
    private const val COLOR_MAGENTA = "magenta"
    private const val COLOR_CYAN = "cyan"
    private const val COLOR_WHITE = "white"

    /** Returns the foreground SGR code for a stable color slug. */
    private fun foregroundCode(
        colorSlug: String,
        defaultCode: Int = FG_WHITE_CODE,
    ): Int =
        when (colorSlug) {
            COLOR_BLACK -> FG_BLACK_CODE
            COLOR_RED -> FG_RED_CODE
            COLOR_GREEN -> FG_GREEN_CODE
            COLOR_YELLOW -> FG_YELLOW_CODE
            COLOR_BLUE -> FG_BLUE_CODE
            COLOR_MAGENTA -> FG_MAGENTA_CODE
            COLOR_CYAN -> FG_CYAN_CODE
            COLOR_WHITE -> FG_WHITE_CODE
            else -> defaultCode
        }

    /** Returns an SGR foreground escape for a stable color slug. */
    fun foreground(
        colorSlug: String,
        defaultCode: Int = FG_WHITE_CODE,
    ): String = "${CSI}${foregroundCode(colorSlug, defaultCode)}m"
}
