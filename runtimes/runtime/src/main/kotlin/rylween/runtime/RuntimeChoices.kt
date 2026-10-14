/** Shared runtime input validation helpers for one-key menu choices. */

package rylween.runtime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledInputBinding
import rylween.compiled.CompiledInventorySlot
import rylween.compiled.ExternalRuntimeCommand
import rylween.compiled.ExternalRuntimePrompts
import rylween.dsl.AudioCue

internal data class MenuChoice<T>(
    val key: Char,
    val value: T,
)

/** The one way a keyed menu entry reads, such as `(A) Healing`. */
internal fun choiceLine(
    key: Char,
    label: String,
): String = "($key) $label"

/** A numbered menu entry, such as `(1) Aragorn`. */
internal fun choiceLine(
    number: Int,
    label: String,
): String = "($number) $label"

internal fun <T> List<T>.withMenuKeys(firstKey: Char = 'A'): List<MenuChoice<T>> =
    mapIndexed { index, value -> MenuChoice((firstKey.code + index).toChar(), value) }

/** Reads keys until one is accepted. A rejected key plays the invalid-choice sound when [audio] is given. */
internal fun RuntimeInputPort.readValidChoice(
    screen: RuntimeScreenPort,
    lines: List<String>,
    audio: RuntimeAudio? = null,
    accepts: (Char) -> Boolean,
): Char? {
    var choice = readCommand() ?: return null
    while (true) {
        if (choice == '!') {
            screen.redrawCurrentView(lines)
        } else if (accepts(choice)) {
            return choice
        } else {
            audio?.cue(AudioCue.InvalidChoice)
        }
        choice = readCommand() ?: return null
    }
}

internal fun RuntimeInputPort.readValidDigitChoice(
    screen: RuntimeScreenPort,
    lines: List<String>,
    validDigits: Set<Int>,
    cancelKeys: Set<Char> = emptySet(),
    audio: RuntimeAudio? = null,
): Int? {
    val choice =
        readValidChoice(screen, lines, audio) { candidate ->
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
    audio: RuntimeAudio? = null,
): Int? {
    val choice =
        readValidChoice(screen, lines, audio) { candidate ->
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
    audio: RuntimeAudio? = null,
): ExternalRuntimeCommand? {
    val choice =
        readValidChoice(screen, lines, audio) { candidate -> commands.any { command -> command.matches(candidate) } }
            ?: return null
    return commands.firstOrNull { command -> command.matches(choice) }
}

internal fun ExternalRuntimePrompts.returnKeyLabel(): String =
    returnKeyName
        .removeSuffix(" key")
        .removeSuffix(" Key")
        .ifBlank { "RETURN" }

internal fun ExternalRuntimePrompts.returnLeavesLine(): String = "${returnKeyLabel()} leaves."

/** How [choiceMenuLines] arranges choices: one per row while the menu fits the text window, or always two per row. */
internal enum class ChoiceLayout {
    FitWindow,
    TwoColumns,
}

/**
 * Composes a text-window menu from [prompt], a blank line, the [choices], and
 * [trailer] lines such as a cancel hint. Choices go one per row when the whole
 * menu fits in [RuntimeScreenPort.textRows] and two per row otherwise, so
 * every list stays inside the window. Inventory pickers always use two columns.
 */
internal fun RuntimeScreenPort.choiceMenuLines(
    prompt: List<String>,
    choices: List<String>,
    trailer: List<String> = emptyList(),
    layout: ChoiceLayout = ChoiceLayout.FitWindow,
): List<String> {
    val head = prompt.withChoiceGap()
    val rows = textRows
    val alreadyColumns = choices.any { choice -> TEXT_COLUMN_SEPARATOR in choice }
    val fits = rows == null || head.size + choices.size + trailer.size <= rows
    val oneColumn = alreadyColumns || (layout == ChoiceLayout.FitWindow && fits)
    val body = if (oneColumn) choices else choices.chunked(2).map { row -> row.joinToString(TEXT_COLUMN_SEPARATOR) }
    return head + body + trailer
}

/** The one inventory picker layout: [prompt], the [items] two per row, then [trailer]. */
internal fun RuntimeScreenPort.inventoryPickerLines(
    prompt: List<String>,
    items: List<String>,
    trailer: List<String> = emptyList(),
): List<String> = choiceMenuLines(prompt, items, trailer, ChoiceLayout.TwoColumns)

/** Picker rows for the occupied slots, keyed by slot letter. */
internal fun List<CompiledInventorySlot>.inventoryChoiceLines(itemName: (CompiledHandleRef) -> String): List<String> =
    sortedBy(CompiledInventorySlot::slot).mapNotNull { slot ->
        slot.item?.let { item -> choiceLine(inventorySlotKey(slot.slot), itemName(item)) }
    }

/** Ends prompt text with one blank line so the key choices that follow never sit directly under it. */
internal fun List<String>.withChoiceGap(): List<String> = if (lastOrNull().isNullOrBlank()) this else this + ""

/** Applies [withChoiceGap] to the text before the first choice line in an already composed menu. */
internal fun List<String>.withChoiceGapBefore(isChoiceLine: (String) -> Boolean): List<String> {
    val firstChoice = indexOfFirst(isChoiceLine)
    if (firstChoice <= 0) return this
    return take(firstChoice).withChoiceGap() + drop(firstChoice)
}

/** Recognizes a menu line offering [key], either as an `(X)` mnemonic or an `X.` list entry. */
internal fun String.offersChoiceKey(key: String): Boolean = "($key)" in this || trimStart().startsWith("$key.")

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
