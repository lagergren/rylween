package rylween.runtime

import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledInventorySlot
import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeSystems
import rylween.dsl.impl.EquipmentCommand
import rylween.dsl.impl.PcClass
import rylween.dsl.impl.of
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UtilityActionsTest {
    private val bundleFile = File(requiredRuntimeTestProperty("rylween.runtime.bundle"))
    private val testTmp = File(requiredRuntimeTestProperty("rylween.runtime.testTmp")).also(File::mkdirs)
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
        val input = ScriptedInput("Y1A" + "Y2B" + "Y1C")
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
            runUtility("leave-town-decline.log", ScriptedInput("N" + "Y1B")) { actions ->
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
        val input = ScriptedInput("Y1A")
        runUtility("leave-town-armed.log", input) { actions -> actions.readyBeforeLeavingTown(armed, leavePrompts()).runtime }

        assertFalse(input.exhausted)
    }

    @Test
    fun healingPotionRestoresMoreHitPointsAtHigherLevels() {
        val runtime =
            runtimeWithInventory(item("red_potion")).updateMember(0) { member -> member.copy(level = 20, health = 300, hitPoints = 10 of 300) }
        val after = runUtility("quaff-red.log", "A1") { actions -> actions.quaff(runtime) }

        assertEquals(10 + 12 + 3 * 20, after.party.members[0].hitPoints.current)
        assertEquals(null, after.party.inventoryItem(1))
    }

    @Test
    fun poisonPotionTakesMoreHitPointsAtHigherLevels() {
        val runtime =
            runtimeWithInventory(item("blue_potion")).updateMember(0) { member -> member.copy(level = 5, health = 300, hitPoints = 100 of 300) }
        val after = runUtility("quaff-blue.log", "A1") { actions -> actions.quaff(runtime) }

        assertEquals(100 - (12 + 3 * 5), after.party.members[0].hitPoints.current)
    }

    @Test
    fun manaPotionRestoresMoreSpellPointsAtHigherLevels() {
        val base = runtimeWithInventory(item("amber_potion"))
        val caster = base.party.members.indexOfFirst { member -> member.pcClass in setOf(PcClass.Mage, PcClass.Priest) }
        val runtime = base.updateMember(caster) { member -> member.copy(level = 10, mana = 0 of 200) }
        val after = runUtility("quaff-amber.log", "A${caster + 1}") { actions -> actions.quaff(runtime) }

        assertEquals(12 + 3 * 10, after.party.members[caster].mana.current)
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
    ): RuntimeState = runUtility(logName, ScriptedInput(commands), action)

    private fun runUtility(
        logName: String,
        input: ScriptedInput,
        action: (UtilityActions) -> RuntimeState,
    ): RuntimeState =
        RuntimeLog.open(testTmp.resolve(logName)).use { log ->
            action(UtilityActions(content, systems, input, NoOpRuntimeScreen(), log))
        }
}
