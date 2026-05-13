package rylween.runtime

/**
 * Shared normalization from transport key tokens to the runtime's current
 * single-character command contract.
 *
 * Browser runtimes may report symbolic key names such as ARROW_UP while the
 * local terminal reports already-decoded keypad tokens. Keep that translation
 * here so web transports do not each grow their own game-command tables.
 */
object RuntimeInputTokens {
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
            "SPACE" -> ' '
            else -> normalized.singleOrNull()?.uppercaseChar()
        }
    }
}
