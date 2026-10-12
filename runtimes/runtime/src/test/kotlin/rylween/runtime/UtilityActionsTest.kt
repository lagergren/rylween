package rylween.runtime

import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledInventorySlot
import rylween.compiled.ExternalRuntimeCastle
import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeSystems
import rylween.dsl.impl.EquipmentCommand
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UtilityActionsTest {
    private val bundleFile = File(requiredUtilityRuntimeProperty("rylween.runtime.bundle"))
    private val testTmp = File(requiredUtilityRuntimeProperty("rylween.runtime.testTmp")).also(File::mkdirs)
    private val bundle = bundleFile.readExternalCompiledGameBundle()
    private val systems = bundleFile.readExternalRuntimeSystems()
    private val content = bundle.content

    @Test
    fun priestSpellMenuLineSeparatesKeyAndSpellName() {
        assertEquals("(A) Healing", priestSpellMenuLine("A".single(), "Healing"))
    }

    @Test
    fun readyWeaponMovesItemOutOfInventory() {
        val sword = item("sword")
        val runtime = runtimeWithInventory(sword)
        val after = runUtility("ready-weapon.log", "1A") { actions -> actions.readyWeapon(runtime) }

        assertEquals(sword, after.party.members[0].equipment.weapon)
        assertEquals(null, after.party.inventoryItem(1))
    }

    @Test
    fun readyWeaponSwapsPreviousReadiedWeaponIntoFreedInventorySlot() {
        val sword = item("sword")
        val axe = item("axe")
        val runtime = runtimeWithInventory(sword).withFirstMemberWeapon(axe)
        val after = runUtility("swap-weapon.log", "1A") { actions -> actions.readyWeapon(runtime) }

        assertEquals(sword, after.party.members[0].equipment.weapon)
        assertEquals(axe, after.party.inventoryItem(1))
    }

    @Test
    fun removeWeaponReturnsReadiedWeaponToFreeInventorySlot() {
        val sword = item("sword")
        val blank = item("blank")
        val runtime = runtimeWithInventory(null).withFirstMemberWeapon(sword)
        val after = runUtility("remove-weapon-free.log", "1") { actions -> actions.removeWeapon(runtime) }

        assertEquals(blank, after.party.members[0].equipment.weapon)
        assertEquals(sword, after.party.inventoryItem(1))
    }

    @Test
    fun removeWeaponCanCancelWhenInventoryIsFull() {
        val sword = item("sword")
        val axe = item("axe")
        val runtime = runtimeWithInventory(*Array(16) { axe }).withFirstMemberWeapon(sword)
        val after = runUtility("remove-weapon-cancel.log", "1\n") { actions -> actions.removeWeapon(runtime) }

        assertEquals(sword, after.party.members[0].equipment.weapon)
        assertEquals(axe, after.party.inventoryItem(1))
    }

    @Test
    fun removeWeaponCanReplaceCarriedItemWhenInventoryIsFull() {
        val sword = item("sword")
        val axe = item("axe")
        val blank = item("blank")
        val runtime = runtimeWithInventory(*Array(16) { axe }).withFirstMemberWeapon(sword)
        val after = runUtility("remove-weapon-replace.log", "1YA") { actions -> actions.removeWeapon(runtime) }

        assertEquals(blank, after.party.members[0].equipment.weapon)
        assertEquals(sword, after.party.inventoryItem(1))
    }

    @Test
    fun leavingTownAsksAgainWhileAnEmptyHandedMemberCanUseACarriedItem() {
        val sword = item("sword")
        val leather = item("leather")
        val runtime = runtimeWithInventory(sword, sword, leather)
        val input = UtilityInput("Y1A" + "Y2B" + "Y1C")
        val after = runUtility("leave-town-ready.log", input) { actions -> actions.readyBeforeLeavingTown(runtime, leavePrompts()).runtime }

        assertEquals(sword, after.party.members[0].equipment.weapon)
        assertEquals(sword, after.party.members[1].equipment.weapon)
        assertEquals(leather, after.party.members[0].equipment.armor)
        assertTrue(after.party.inventory.all { slot -> slot.item == null })
        assertTrue(input.exhausted)
    }

    @Test
    fun leavingTownMovesOnToArmorWhenTheWeaponQuestionIsDeclined() {
        val sword = item("sword")
        val leather = item("leather")
        val runtime = runtimeWithInventory(sword, leather)
        val after =
            runUtility("leave-town-decline.log", UtilityInput("N" + "Y1B")) { actions ->
                actions.readyBeforeLeavingTown(runtime, leavePrompts()).runtime
            }

        assertEquals(item("blank"), after.party.members[0].equipment.weapon)
        assertEquals(leather, after.party.members[0].equipment.armor)
        assertEquals(sword, after.party.inventoryItem(1))
    }

    @Test
    fun leavingTownAsksNothingWhenEveryLivingMemberIsArmed() {
        val sword = item("sword")
        val axe = item("axe")
        val runtime = runtimeWithInventory(sword)
        val armed = runtime.party.members.indices.fold(runtime) { state, index -> state.withMemberWeapon(index, axe) }
        val input = UtilityInput("Y1A")
        runUtility("leave-town-armed.log", input) { actions -> actions.readyBeforeLeavingTown(armed, leavePrompts()).runtime }

        assertFalse(input.exhausted)
    }

    private fun leavePrompts(): List<EquipmentCommand> =
        checkNotNull(systems.town).leavePrompts.also { prompts ->
            assertEquals(listOf(EquipmentCommand.ReadyWeapon, EquipmentCommand.ReadyArmor), prompts)
        }

    private fun item(slug: String): CompiledHandleRef = content.ref(CompiledHandleKind.Item, slug)

    private fun runtimeWithInventory(vararg items: CompiledHandleRef?): RuntimeState {
        val state = bundle.states.single { state -> content.slug(state.state) == "fresh-replay" }
        val runtime = RuntimeState.from(state, content)
        return runtime.withParty(
            runtime.party.copy(
                inventory = (1..16).map { index -> CompiledInventorySlot(index, items.getOrNull(index - 1)) },
            ),
        )
    }

    private fun RuntimeState.withFirstMemberWeapon(item: CompiledHandleRef): RuntimeState = withMemberWeapon(0, item)

    private fun RuntimeState.withMemberWeapon(
        index: Int,
        item: CompiledHandleRef,
    ): RuntimeState =
        updateMember(index) { member ->
            member.copy(equipment = member.equipment.copy(weapon = item))
        }

    private fun runUtility(
        logName: String,
        commands: String,
        action: (UtilityActions) -> RuntimeState,
    ): RuntimeState = runUtility(logName, UtilityInput(commands), action)

    private fun runUtility(
        logName: String,
        input: UtilityInput,
        action: (UtilityActions) -> RuntimeState,
    ): RuntimeState =
        RuntimeLog.open(testTmp.resolve(logName)).use { log ->
            action(UtilityActions(content, systems, input, UtilityScreen(), log))
        }
}

private fun requiredUtilityRuntimeProperty(name: String): String =
    System.getProperty(name)
        ?: error("missing required test system property " + name)

private class UtilityInput(
    commands: String,
) : RuntimeInputPort {
    private val commands = ArrayDeque(commands.toList())

    override val exhausted: Boolean get() = commands.isEmpty()

    override fun pause(ms: Int) = Unit

    override fun readCommand(): Char? = commands.removeFirstOrNull()

    override fun readInt(maxDigits: Int): Int? = readCommand()?.digitToIntOrNull()

    override fun readText(maxLength: Int?): String? = null
}

private class UtilityScreen : RuntimeScreenPort {
    override val textColumns: Int = 36

    override fun bootFrame() = Unit

    override fun showStaticScreen(
        screenSlug: String,
        runtime: RuntimeState,
    ) = Unit

    override fun showPagedStaticScreen(
        screenSlug: String,
        runtime: RuntimeState,
        advance: () -> Char?,
    ) = Unit

    override fun renderWilderness(
        runtime: RuntimeState,
        world: WorldMap,
    ) = Unit

    override fun showDebugWorldMap(
        runtime: RuntimeState,
        world: WorldMap,
        advance: () -> Char?,
    ) = Unit

    override fun renderDungeon(runtime: RuntimeState) = Unit

    override fun renderCastle(
        runtime: RuntimeState,
        castle: ExternalRuntimeCastle,
    ) = Unit

    override fun writeText(lines: List<String>) = Unit

    override fun showMessage(lines: List<String>) = Unit

    override fun updateStatus(runtime: RuntimeState) = Unit

    override fun writeViewCommand(command: RuntimeViewCommand) = Unit

    override fun showImage(imageSlug: String) = Unit

    override fun redrawCurrentView(lines: List<String>) = Unit

    override fun flashLogLine(line: String) = Unit
}
