package rylween.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class AutopilotMenuTest {
    @Test
    fun readsTheChoiceFormatsTheRuntimeShows() {
        val menu =
            AutopilotMenu.parse(
                listOf(
                    "Entering Thord",
                    "(M)erchant",
                    "(B)uy item",
                    "(A) Torch          1 au",
                    "(1) Sword    160 au",
                    "(Y)es / (N)o",
                    "A. Xvart 159/159 HP",
                    "1. Aragorn",
                    "B. Bow\tC. Trident",
                    "(8/↑) Move North",
                ),
            )

        assertEquals(
            listOf(
                AutopilotOption('M', "Merchant"),
                AutopilotOption('B', "Buy item"),
                AutopilotOption('A', "Torch 1 au"),
                AutopilotOption('1', "Sword 160 au"),
                AutopilotOption('Y', "Yes"),
                AutopilotOption('N', "No"),
                AutopilotOption('A', "Xvart 159/159 HP"),
                AutopilotOption('1', "Aragorn"),
                AutopilotOption('B', "Bow"),
                AutopilotOption('C', "Trident"),
            ),
            menu.options,
        )
        assertEquals(160, menu.find("Sword")?.price)
    }
}
