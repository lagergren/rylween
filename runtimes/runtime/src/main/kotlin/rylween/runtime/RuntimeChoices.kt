/** Shared runtime input validation helpers for one-key menu choices. */

package rylween.runtime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledInputBinding
import rylween.compiled.ExternalRuntimeCommand
import rylween.compiled.ExternalRuntimePrompts

internal data class MenuChoice<T>(
    val key: Char,
    val value: T,
)

internal fun <T> List<T>.withMenuKeys(firstKey: Char = 'A'): List<MenuChoice<T>> =
    mapIndexed { index, value -> MenuChoice((firstKey.code + index).toChar(), value) }

internal fun RuntimeInputPort.readValidChoice(
    screen: RuntimeScreenPort,
    lines: List<String>,
    accepts: (Char) -> Boolean,
): Char? {
    var choice = readCommand() ?: return null
    while (true) {
        if (choice == '!') {
            screen.redrawCurrentView(lines)
        } else if (accepts(choice)) {
            return choice
        }
        choice = readCommand() ?: return null
    }
}

internal fun RuntimeInputPort.readValidDigitChoice(
    screen: RuntimeScreenPort,
    lines: List<String>,
    validDigits: Set<Int>,
    cancelKeys: Set<Char> = emptySet(),
): Int? {
    val choice =
        readValidChoice(screen, lines) { candidate ->
            val key = candidate.uppercaseChar()
            key in cancelKeys || candidate.digitToIntOrNull() in validDigits
        } ?: return null
    if (choice.uppercaseChar() in cancelKeys) return null
    return choice.digitToIntOrNull()
}

internal fun RuntimeInputPort.readValidInventorySlotChoice(
    screen: RuntimeScreenPort,
    lines: List<String>,
    validSlots: Set<Int>,
    cancelKeys: Set<Char> = emptySet(),
): Int? {
    val choice =
        readValidChoice(screen, lines) { candidate ->
            val key = candidate.uppercaseChar()
            key in cancelKeys || candidate.inventorySlotFromKey() in validSlots
        } ?: return null
    if (choice.uppercaseChar() in cancelKeys) return null
    return choice.inventorySlotFromKey()
}

internal fun RuntimeInputPort.readValidCommandChoice(
    screen: RuntimeScreenPort,
    lines: List<String>,
    commands: List<ExternalRuntimeCommand>,
): ExternalRuntimeCommand? {
    val choice = readValidChoice(screen, lines) { candidate -> commands.any { command -> command.matches(candidate) } } ?: return null
    return commands.firstOrNull { command -> command.matches(choice) }
}

internal fun ExternalRuntimePrompts.returnKeyLabel(): String =
    returnKeyName
        .removeSuffix(" key")
        .removeSuffix(" Key")
        .ifBlank { "RETURN" }

internal fun ExternalRuntimePrompts.returnLeavesLine(): String = "${returnKeyLabel()} leaves."

internal fun CompiledInputBinding.matchesInput(
    content: CompiledContentBundle,
    choice: Char,
): Boolean {
    val accepted = listOf(primaryString) + aliasStrings
    return accepted
        .map(content.strings::value)
        .any { value -> value.equals(choice.toString(), ignoreCase = true) }
}

internal fun CompiledInputBinding.primaryInputLabel(content: CompiledContentBundle): String = content.strings.value(primaryString)

internal fun CompiledInputBinding.mnemonicLine(
    content: CompiledContentBundle,
    text: String,
): String = "(${primaryInputLabel(content).uppercase()})${text.afterMnemonic(primaryInputLabel(content))}"

internal fun ExternalRuntimeCommand.matches(choice: Char): Boolean = binding.matches(choice)

internal fun ExternalRuntimeCommand.mnemonicLine(): String {
    val text = label ?: binding.label
    val primary = binding.primary.uppercase()
    val suffix = text.removePrefix(primary, ignoreCase = true)
    return suffix?.let { "($primary)$it" } ?: "($primary) $text"
}

private fun String.afterMnemonic(key: String): String =
    removePrefix(key)
        .removePrefix(key.uppercase())
        .removePrefix(key.lowercase())

private fun String.removePrefix(
    prefix: String,
    ignoreCase: Boolean,
): String? = takeIf { startsWith(prefix, ignoreCase) }?.drop(prefix.length)
