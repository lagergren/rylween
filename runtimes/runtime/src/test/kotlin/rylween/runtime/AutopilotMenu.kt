/**
 * Reads the choices a runtime screen offers, so the autopilot can pick an
 * option by what it says instead of by a remembered key.
 */

package rylween.runtime

/** One selectable choice: the [key] to press and the [label] shown with it, such as `M` and "Merchant". */
internal data class AutopilotOption(
    val key: Char,
    val label: String,
) {
    /** The price in gold when the label ends in one, such as "Sword 160 au". */
    val price: Int? get() = pricePattern.find(label)?.let { match -> match.groupValues[1].toInt() }

    fun says(text: String): Boolean = label.contains(text, ignoreCase = true)

    private companion object {
        val pricePattern = Regex("""(\d+)\s*au$""")
    }
}

/** The choices offered by a block of screen lines, in screen order. */
internal data class AutopilotMenu(
    val options: List<AutopilotOption>,
) {
    fun isEmpty(): Boolean = options.isEmpty()

    fun offers(key: Char): Boolean = options.any { option -> option.key == key }

    /** The first option whose label contains [text], ignoring case. */
    fun find(text: String): AutopilotOption? = options.firstOrNull { option -> option.says(text) }

    /**
     * The inventory picker entry for the item called [name]. Pickers cut long names short ("Red Potio" for
     * "Red Potion"), so an entry matches when [name] starts with it.
     */
    fun findInventoryItem(name: String): AutopilotOption? =
        options.firstOrNull { option -> option.label == name || (option.label.length >= MIN_TRUNCATED && name.startsWith(option.label)) }

    /** The listed item called exactly [name], with or without a trailing price, such as "Sword 160 au" for "Sword". */
    fun findItem(name: String): AutopilotOption? =
        options.firstOrNull { option -> option.label == name || option.label.startsWith("$name ") }

    companion object {
        private const val MIN_TRUNCATED = 3

        /** A parenthesized key such as "(M)erchant", "(A) Torch  1 au", or "(Y)es / (N)o". */
        private val inlineOptionPattern = Regex("""\(([A-Z0-9])\)""")

        /** A listed key such as "A. Xvart 159/159 HP" or "1. Aragorn". */
        private val listedOptionPattern = Regex("""^\s*([A-Z0-9])\.\s+(.+)$""")

        /** The "RETURN leaves." line that ends a menu; it reads as a "Leave" option on RETURN. */
        private val leaveLinePattern = Regex("""^\s*RETURN leaves\.\s*$""")

        /** Multi-column rows, such as an inventory picker's "A. Sword<TAB>B. Bow", hold one choice per cell. */
        fun parse(lines: List<String>): AutopilotMenu =
            AutopilotMenu(lines.flatMap { line -> line.split(TEXT_COLUMN_SEPARATOR) }.flatMap(::optionsIn))

        private fun optionsIn(line: String): List<AutopilotOption> {
            if (leaveLinePattern.matches(line)) return listOf(AutopilotOption('\n', "Leave"))
            listedOptionPattern.find(line)?.let { match ->
                return listOf(AutopilotOption(match.groupValues[1].single(), match.groupValues[2].normalized()))
            }
            val matches = inlineOptionPattern.findAll(line).toList()
            return matches.mapIndexed { index, match ->
                val end = matches.getOrNull(index + 1)?.range?.first ?: line.length
                val tail = line.substring(match.range.last + 1, end).substringBefore(" / ")
                val key = match.groupValues[1].single()
                // "(M)erchant" continues the word after the key; "(A) Torch" starts a new one.
                val label = if (tail.firstOrNull()?.isLowerCase() == true) key + tail else tail
                AutopilotOption(key, label.normalized())
            }
        }

        private fun String.normalized(): String = trim().replace(Regex("""\s+"""), " ")
    }
}
