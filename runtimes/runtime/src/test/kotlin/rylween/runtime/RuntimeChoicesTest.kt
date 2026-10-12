package rylween.runtime

import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledInventorySlot
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

class RuntimeChoicesTest {
    private val yesNo: (String) -> Boolean = { line -> line.offersChoiceKey("Y") || line.offersChoiceKey("N") }

    @Test
    fun choiceGapSeparatesInlineAuthoredProseFromChoices() {
        assertEquals(
            listOf("Anything to drink?", "", "(Y)es", "(N)o"),
            listOf("Anything to drink?", "(Y)es", "(N)o").withChoiceGapBefore(yesNo),
        )
    }

    @Test
    fun choiceGapKeepsAuthoredGapsAndBareChoiceLists() {
        val gapped = listOf("Anything to drink?", "", "(Y)es", "(N)o")
        assertEquals(gapped, gapped.withChoiceGapBefore(yesNo))
        assertEquals(listOf("(Y)es", "(N)o"), listOf("(Y)es", "(N)o").withChoiceGapBefore(yesNo))
        assertEquals(emptyList(), emptyList<String>().withChoiceGap())
    }

    @Test
    fun choiceMenusUseOneColumnUntilTheWindowIsFull() {
        val screen = WindowScreen(textRows = 6)
        val choices = listOf("1. Ada", "2. Bryn", "3. Cato")

        assertEquals(
            listOf("Who?", "", "1. Ada", "2. Bryn", "3. Cato", "(N)one"),
            screen.choiceMenuLines(listOf("Who?"), choices, listOf("(N)one")),
        )
        assertEquals(
            listOf("Who?", "", "1. Ada\t2. Bryn", "3. Cato\t4. Dina", "(N)one"),
            screen.choiceMenuLines(listOf("Who?"), choices + "4. Dina", listOf("(N)one")),
        )
    }

    @Test
    fun inventoryPickersAlwaysUseTwoColumnsOfOccupiedSlotsInSlotOrder() {
        val sword = CompiledHandleRef(CompiledHandleKind.Item, 7)
        val torch = CompiledHandleRef(CompiledHandleKind.Item, 8)
        val slots = listOf(CompiledInventorySlot(3, torch), CompiledInventorySlot(2, null), CompiledInventorySlot(1, sword))
        val names = mapOf(sword to "Long sword", torch to "Torch")

        val items = slots.inventoryChoiceLines(names::getValue)

        assertEquals(listOf("A. Long sword", "C. Torch"), items)
        assertEquals(
            listOf("Drop what?", "", "A. Long sword\tC. Torch", "RETURN leaves."),
            WindowScreen(textRows = null).inventoryPickerLines(listOf("Drop what?"), items, listOf("RETURN leaves.")),
        )
    }
}

/** Only the text-window size is used by the layout helpers; any other call fails the test. */
private val unusedScreen =
    Proxy.newProxyInstance(RuntimeScreenPort::class.java.classLoader, arrayOf(RuntimeScreenPort::class.java)) { _, method, _ ->
        error("unexpected screen call ${method.name}")
    } as RuntimeScreenPort

private class WindowScreen(
    override val textRows: Int?,
) : RuntimeScreenPort by unusedScreen {
    override val textColumns: Int? = 26
}
