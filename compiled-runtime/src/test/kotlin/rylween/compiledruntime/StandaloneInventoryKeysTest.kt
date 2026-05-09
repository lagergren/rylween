package rylween.compiledruntime

import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledInventorySlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StandaloneInventoryKeysTest {
    @Test
    fun inventoryKeysCoverLegacySlotsAToP() {
        assertEquals('A', inventorySlotKey(1))
        assertEquals('P', inventorySlotKey(16))
        assertEquals(1, 'A'.inventorySlotFromKey())
        assertEquals(16, 'P'.inventorySlotFromKey())
        assertEquals(16, 'p'.inventorySlotFromKey())
        assertNull('Q'.inventorySlotFromKey())
    }

    @Test
    fun partialAuthoredInventoryExpandsToSixteenRuntimeSlots() {
        val potion = CompiledHandleRef(CompiledHandleKind.Item, 1)
        val slots =
            listOf(
                CompiledInventorySlot(1, potion),
                CompiledInventorySlot(12, potion),
            ).normalizedInventorySlots()

        assertEquals((1..16).toList(), slots.map(CompiledInventorySlot::slot))
        assertEquals(potion, slots.single { it.slot == 1 }.item)
        assertEquals(potion, slots.single { it.slot == 12 }.item)
        assertNull(slots.single { it.slot == 13 }.item)
        assertNull(slots.single { it.slot == 16 }.item)
    }
}
