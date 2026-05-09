package rylween.compiledruntime

import rylween.compiled.CompiledInventorySlot
import rylween.dsl.InventorySaveSlotMax

private const val INVENTORY_FIRST_KEY = 'A'
private const val INVENTORY_ENTRY_WIDTH = 12
private const val INVENTORY_ITEM_WIDTH = 9

internal fun inventorySlotKey(slot: Int): Char = (INVENTORY_FIRST_KEY.code + slot - 1).toChar()

internal fun Char.inventorySlotFromKey(): Int? {
    val slot = uppercaseChar().code - INVENTORY_FIRST_KEY.code + 1
    return slot.takeIf { it in 1..InventorySaveSlotMax }
}

internal fun List<CompiledInventorySlot>.normalizedInventorySlots(): List<CompiledInventorySlot> {
    val bySlot = filter { it.slot in 1..InventorySaveSlotMax }.associateBy(CompiledInventorySlot::slot)
    return (1..InventorySaveSlotMax).map { slot -> bySlot[slot] ?: CompiledInventorySlot(slot, null) }
}

internal fun inventoryMenuLines(entries: List<Pair<Char, String>>): List<String> =
    entries
        .chunked(2)
        .map { row ->
            row
                .joinToString(" ") { (key, itemName) ->
                    "$key. ${itemName.take(INVENTORY_ITEM_WIDTH)}".padEnd(INVENTORY_ENTRY_WIDTH)
                }.trimEnd()
        }
