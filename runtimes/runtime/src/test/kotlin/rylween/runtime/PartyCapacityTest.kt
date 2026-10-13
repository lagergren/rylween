package rylween.runtime

import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledInventorySlot
import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeSystems
import rylween.dsl.impl.PcClass
import rylween.dsl.impl.Resource
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PartyCapacityTest {
    private val bundleFile = File(requiredRuntimeTestProperty("rylween.runtime.bundle"))
    private val testTmp = File(requiredRuntimeTestProperty("rylween.runtime.testTmp")).also(File::mkdirs)
    private val bundle = bundleFile.readExternalCompiledGameBundle()
    private val systems = bundleFile.readExternalRuntimeSystems()
    private val content = bundle.content
    private val leftBehind = systems.prompts.party.dropCapacityReduced.render({ null }, NoOpRuntimeScreen().textColumns)

    @Test
    fun aDonkeyCarriesAsMuchFoodAgainAsThePartyItself() {
        val party = runtime(food = 0, item("donkey")).party

        assertEquals(150, runtime(food = 0).party.resourceCapacity(content, systems.realm, Resource.Food))
        assertEquals(300, party.resourceCapacity(content, systems.realm, Resource.Food))
    }

    @Test
    fun droppingADonkeyLeavesTheFoodItCarriedBehind() {
        val screen = MessageRecordingScreen()
        val after = utility("drop-donkey.log", "A", screen) { actions -> actions.drop(runtime(food = 300, item("donkey"))) }

        assertEquals(150, after.party.resources.food)
        assertEquals(listOf(listOf("You drop the Donkey.") + leftBehind), screen.messages)
    }

    @Test
    fun droppingADonkeyKeepsTheFoodThePartyCanStillCarry() {
        val screen = MessageRecordingScreen()
        val after =
            utility("drop-one-donkey.log", "A", screen) { actions ->
                actions.drop(runtime(food = 300, item("donkey"), item("donkey")))
            }

        assertEquals(300, after.party.resources.food)
        assertEquals(listOf(listOf("You drop the Donkey.")), screen.messages)
    }

    @Test
    fun anItemTakingTheDonkeysPlaceLeavesItsFoodBehind() {
        val axe = item("axe")
        val screen = MessageRecordingScreen()
        val full = runtime(food = 300, item("donkey"), *Array(15) { axe })
        val armed = full.updateMember(0) { member -> member.copy(equipment = member.equipment.copy(weapon = item("sword"))) }
        val after = utility("replace-donkey.log", "1YA", screen) { actions -> actions.removeWeapon(armed) }

        assertEquals(item("sword"), after.party.inventoryItem(1))
        assertEquals(150, after.party.resources.food)
        assertEquals(listOf(leftBehind), screen.messages)
    }

    @Test
    fun huntingStopsAtWhatThePartyCanCarry() {
        val base = runtime(food = 145)
        val ranger = base.party.members.indexOfFirst { member -> member.pcClass == PcClass.Ranger }
        assertTrue(ranger >= 0, "the test party needs a ranger")
        // Without a random state every draw is its lowest value, so the hunt succeeds.
        val hunter = base.withParty(base.party.copy(rngState = null, resources = base.party.resources.copy(timeLeft = 100)))
        val result =
            RuntimeLog.open(testTmp.resolve("hunt-full.log")).use { log ->
                WildernessActions(
                    content = content,
                    systems = systems,
                    realm = systems.realm,
                    combat = systems.combat,
                    wilderness = systems.wilderness,
                    commands = systems.commands,
                    input = ScriptedInput("${ranger + 1}"),
                    screen = NoOpRuntimeScreen(),
                    log = log,
                ).hunt(hunter)
            }

        assertEquals(150, result.runtime.party.resources.food)
        assertTrue("5 food rations." in result.messages, "the text says what the party kept, found ${result.messages}")
    }

    private fun item(slug: String): CompiledHandleRef = content.ref(CompiledHandleKind.Item, slug)

    private fun runtime(
        food: Int,
        vararg items: CompiledHandleRef,
    ): RuntimeState {
        val state = bundle.states.single { state -> content.slug(state.state) == "fresh-replay" }
        val runtime = RuntimeState.from(state, content)
        return runtime.withParty(
            runtime.party.copy(
                resources = runtime.party.resources.copy(food = food),
                inventory = (1..16).map { index -> CompiledInventorySlot(index, items.getOrNull(index - 1)) },
            ),
        )
    }

    private fun utility(
        logName: String,
        commands: String,
        screen: RuntimeScreenPort,
        action: (UtilityActions) -> RuntimeState,
    ): RuntimeState =
        RuntimeLog.open(testTmp.resolve(logName)).use { log ->
            action(UtilityActions(content, systems, ScriptedInput(commands), screen, log))
        }
}
