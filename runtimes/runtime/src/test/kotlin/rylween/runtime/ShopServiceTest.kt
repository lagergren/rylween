package rylween.runtime

import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledInventorySlot
import rylween.compiled.CompiledOperand
import rylween.compiled.CompiledShopService
import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeSystems
import rylween.dsl.impl.INVENTORY_SAVE_SLOT_MAX
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShopServiceTest {
    private val bundleFile = File(requiredRuntimeTestProperty("rylween.runtime.bundle"))
    private val testTmp = File(requiredRuntimeTestProperty("rylween.runtime.testTmp")).also(File::mkdirs)
    private val bundle = bundleFile.readExternalCompiledGameBundle()
    private val systems = bundleFile.readExternalRuntimeSystems()
    private val content = bundle.content

    @Test
    fun sellPickerSellsTheItemInSlotN() {
        val gem = item("red_gem")
        val runtime = runtimeWithInventory(List(INVENTORY_SAVE_SLOT_MAX) { gem })
        val after = runShop("sell-slot-n.log", "SNY", merchant(), runtime)

        assertEquals(null, after.party.inventoryItem(SLOT_N))
        assertEquals(INVENTORY_SAVE_SLOT_MAX - 1, after.party.inventory.count { slot -> slot.item != null })
        assertTrue(after.party.resources.gold > runtime.party.resources.gold)
    }

    @Test
    fun sellPickerLeavesOnReturn() {
        val gem = item("red_gem")
        val runtime = runtimeWithInventory(List(INVENTORY_SAVE_SLOT_MAX) { gem })
        val after = runShop("sell-return.log", "S\n", merchant(), runtime)

        assertEquals(runtime.party.inventory, after.party.inventory)
        assertEquals(runtime.party.resources.gold, after.party.resources.gold)
    }

    @Test
    fun leavingShowsTheGoodbyeBeforeReturning() {
        val input = PauseRecordingInput("L")
        val screen = ShopTextScreen()
        val runtime = runtimeWithInventory(emptyList())
        val after = runShop("leave-goodbye.log", input, merchant(), runtime, screen)

        assertEquals(listOf("Well, bye then..."), screen.screens.last())
        assertEquals(listOf(systems.pacing.shop.noticePauseMs), input.pauses)
        assertEquals(runtime.party, after.party)
    }

    private fun merchant(): CompiledShopService =
        content.scripts.roots
            .asSequence()
            .flatMap { root -> root.instructions }
            .flatMap { instruction -> instruction.operands }
            .filterIsInstance<CompiledOperand.ShopService>()
            .map { operand -> operand.service }
            .first { service -> content.slug(service.building) == "merchant" }

    private fun item(slug: String): CompiledHandleRef = content.ref(CompiledHandleKind.Item, slug)

    private fun runtimeWithInventory(items: List<CompiledHandleRef>): RuntimeState {
        val state = bundle.states.single { state -> content.slug(state.state) == "fresh-replay" }
        val runtime = RuntimeState.from(state, content)
        return runtime.withParty(
            runtime.party.copy(
                inventory = (1..INVENTORY_SAVE_SLOT_MAX).map { index -> CompiledInventorySlot(index, items.getOrNull(index - 1)) },
            ),
        )
    }

    private fun runShop(
        logName: String,
        commands: String,
        shop: CompiledShopService,
        runtime: RuntimeState,
    ): RuntimeState = runShop(logName, ScriptedInput(commands), shop, runtime, NoOpRuntimeScreen())

    private fun runShop(
        logName: String,
        input: RuntimeInputPort,
        shop: CompiledShopService,
        runtime: RuntimeState,
        screen: RuntimeScreenPort,
    ): RuntimeState =
        RuntimeLog.open(testTmp.resolve(logName)).use { log ->
            ShopService(content, systems, input, screen, log).run(shop, runtime)
        }

    private companion object {
        const val SLOT_N = 14
    }
}

/** Scripted input that also records the pauses the shop asks for. */
private class PauseRecordingInput(
    commands: String,
) : RuntimeInputPort by ScriptedInput(commands) {
    val pauses = mutableListOf<Int>()

    override fun pause(ms: Int) {
        pauses += ms
    }
}

/** Records each text screen the shop writes. */
private class ShopTextScreen : NoOpRuntimeScreen() {
    val screens = mutableListOf<List<String>>()

    override fun writeText(lines: List<String>) {
        screens += lines
    }
}
