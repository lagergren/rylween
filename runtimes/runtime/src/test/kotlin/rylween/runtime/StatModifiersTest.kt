package rylween.runtime

import org.junit.jupiter.api.io.TempDir
import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledInventorySlot
import rylween.compiled.CompiledOperand
import rylween.compiled.CompiledPartyMember
import rylween.compiled.CompiledShopService
import rylween.compiled.readExternalCompiledBundleContentHash
import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeSystems
import rylween.dsl.impl.GridPoint
import rylween.dsl.impl.INVENTORY_SAVE_SLOT_MAX
import rylween.dsl.impl.PcClass
import rylween.dsl.impl.RuntimeMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Dexterity and charisma in play: the ranger's hunting chance, the thief's castle trap disarm and dungeon chest check,
 * and the party's haggling, each with its cap. Without a random state every draw is its lowest value, 0, so a chance of 0 fails and
 * anything higher succeeds; [withNextRoll] sets the roll where a test needs another.
 */
class StatModifiersTest {
    private val bundleFile = File(requiredRuntimeTestProperty("rylween.runtime.bundle"))

    @TempDir
    private lateinit var testTmp: File
    private val bundle = bundleFile.readExternalCompiledGameBundle()
    private val systems = bundleFile.readExternalRuntimeSystems()
    private val content = bundle.content

    @Test
    fun aRangerHuntsWithDexterity() {
        assertTrue("...but fails." in hunt("hunt-dexterity-5.log", dexterity = 5).messages, "dexterity 5 gives a 0% chance")
        assertTrue("...and succeeds." in hunt("hunt-dexterity-6.log", dexterity = 6).messages, "dexterity 6 gives a 1% chance")

        hunt("hunt-dexterity-50.log", dexterity = 50)
        assertTrue("threshold_lt=45" in testTmp.resolve("hunt-dexterity-50.log").readText(), "a dexterity-50 ranger hunts at 45%")
        hunt("hunt-dexterity-200.log", dexterity = 200)
        assertTrue("threshold_lt=95" in testTmp.resolve("hunt-dexterity-200.log").readText(), "hunting caps at 95%")
    }

    @Test
    fun aThiefDisarmsACastleTrapWithDexterityNotWisdom() {
        val clumsy = castleTrap("trap-clumsy.log", dexterity = 0, wisdom = 100)
        val nimble = castleTrap("trap-nimble.log", dexterity = 1, wisdom = 0)
        val capped = castleTrap("trap-capped.log", dexterity = 200, wisdom = 0, nextRoll = 97)

        assertNotEquals(hitPoints(castleRuntime()), hitPoints(clumsy), "a thief with dexterity 0 never disarms the trap")
        assertEquals(hitPoints(castleRuntime()), hitPoints(nimble), "a thief with dexterity 1 disarms the trap")
        assertNotEquals(hitPoints(castleRuntime()), hitPoints(capped), "disarming caps at 95%, so a roll of 97 always springs the trap")
    }

    @Test
    fun theBestLivingCharismaHagglesForAtMostTwenty() {
        val axe = item("axe")
        val base = shopRuntime(axe).withCharisma(60)
        val talker = base.updateMember(0) { member -> member.copy(charisma = 202) }
        val price = sell("sell-plain.log", "SAYL", talker).party.resources.gold - base.party.resources.gold
        assertTrue(price > 0, "the smith buys the axe")

        // Charisma 202 adds the capped +20 to a roll of 60: 80 beats the smith's 76 and adds a sixth, but misses the
        // further quarter above 90 that an uncapped +76 would reach.
        val haggled = sell("sell-haggle.log", "SAHYL", talker.withNextRoll(60))
        assertEquals(base.party.resources.gold + price + price / 6, haggled.party.resources.gold)

        // With the talker dead, the best charisma is 60: its +5 makes the roll 65, and the smith refuses.
        val refused = sell("sell-refused.log", "SAHL", talker.updateMember(0) { member -> member.copy(dead = true) }.withNextRoll(60))
        assertEquals(axe, refused.party.inventoryItem(1))
        assertEquals(base.party.resources.gold, refused.party.resources.gold)
    }

    @Test
    fun aThiefsDexterityCutsTheChestTrapChanceToNoLessThanFour() {
        // The chest's trap roll is 14: under the 40% unchecked chance and a dexterity-50 thief's 20%, but not under a
        // dexterity-70 thief's 12%.
        val runs =
            listOf(null, 50, 70).map { dexterity ->
                openChest("chest-dexterity-${dexterity ?: "none"}", ROLL_14_SEED, thiefDexterity = dexterity)
            }
        assertEquals(listOf(14, 14, 14), runs.map(ChestRun::roll))
        assertEquals(listOf(40, 20, 12), runs.map(ChestRun::chance))
        assertEquals(listOf(false, true, true), runs.map(ChestRun::thiefChecked))
        assertEquals(listOf(true, true, false), runs.map(ChestRun::trapSprung))

        // A roll of 0 still springs the trap for a dexterity-200 thief: the chance never drops below 4%.
        val floor = openChest("chest-dexterity-200", ROLL_0_SEED, thiefDexterity = 200)
        assertEquals(0 to 4, floor.roll to floor.chance)
        assertTrue(floor.trapSprung)
    }

    /** What opening a chest showed, and its trap roll: the d100 [roll] and the trap [chance] it was compared with. */
    private class ChestRun(
        val transcript: List<String>,
        val roll: Int,
        val chance: Int,
    ) {
        val thiefChecked: Boolean get() = transcript.any { line -> "chest for traps." in line }
        val trapSprung: Boolean get() = transcript.any { line -> line.trim() == "A TRAP!" }
    }

    /**
     * Opens the chest on level 2 of a three-level dungeon with the strong party, its first member made a thief with
     * [thiefDexterity] unless that is null, and returns what the screen showed and the chest's trap roll.
     */
    private fun openChest(
        name: String,
        seed: Long,
        thiefDexterity: Int?,
    ): ChestRun {
        val base = RuntimeState.from(bundle.states.single { state -> content.slug(state.state) == "strong-replay" }, content)
        val seeded = base.withParty(base.party.copy(rngState = seed))
        val adventurers =
            thiefDexterity?.let { dexterity -> seeded.updateMember(0) { member -> member.asThief(dexterity, member.wisdom) } } ?: seeded
        val inDungeon =
            RuntimeLog.open(testTmp.resolve("$name-setup.log")).use { log ->
                adventurers.enterDungeon(content, content.ref(CompiledHandleKind.Dungeon, "ogre_cave"), levels = 3, log = log)
            }
        val chest = content.ref(CompiledHandleKind.DungeonEvent, "gold_chest")
        val start = inDungeon.copy(dungeon = inDungeon.dungeon?.copy(levelEvents = mapOf(2 to chest)))
        val save = testTmp.resolve("$name.rsave").also(File::delete)
        val hash = bundleFile.readExternalCompiledBundleContentHash()
        SaveFile.write(save, start, hash, content, "chest-test", null)
        val actions = AutopilotActions(systems)
        val queue = ArrayDeque(listOf(actions.descend(), actions.ascend(), actions.ascend()))
        val plan =
            object : AutopilotPlan {
                override fun done(state: RuntimeState): Boolean = queue.isEmpty() && state.currentMode == RuntimeMode.Wilderness

                override fun wilderness(state: RuntimeState): AutopilotAction = throw AutopilotStuck("left the dungeon with steps to go")

                override fun dungeon(state: RuntimeState): AutopilotAction =
                    queue.removeFirstOrNull() ?: throw AutopilotStuck("out of steps")
            }
        val ports = AutopilotPorts(Autopilot(content, systems, plan), CHEST_KEYS, CHEST_TRANSCRIPT_TAIL)
        val log = testTmp.resolve("$name.log")
        RuntimeLog.open(log).use { runtimeLog ->
            RuntimeSessionFactory
                .load(bundle, systems, hash)
                .start(ports, ports, RuntimeSessionConfig("chest-test", "strong-replay", save, save), runtimeLog)
        }
        // The chest rolls `random(0..99) lt trapChance`, logged as a script chance with the trap chance as its threshold,
        // and then the d100 of its gold, logged as a script random.
        val lines = log.readLines()
        val goldRoll = lines.indexOfFirst { line -> "rng.draw context=script.random range=0..99 " in line }
        assertTrue(goldRoll >= 0, "the chest was opened and paid out its gold")
        val trapRoll = lines.take(goldRoll).last { line -> "rng.draw context=script.chance range=0..99 " in line }
        val (roll, chance) = Regex("""value=(\d+) .*threshold=(\d+)""").find(trapRoll)!!.destructured
        return ChestRun(ports.transcript, roll.toInt(), chance.toInt())
    }

    private fun hunt(
        logName: String,
        dexterity: Int,
    ): WildernessActionResult {
        val base = freshRuntime()
        val ranger = base.party.members.indexOfFirst { member -> member.pcClass == PcClass.Ranger }
        assertTrue(ranger >= 0, "the test party needs a ranger")
        val hunter =
            base
                .updateMember(ranger) { member -> member.copy(dexterity = dexterity) }
                .let { runtime ->
                    runtime.withParty(runtime.party.copy(resources = runtime.party.resources.copy(food = 0, timeLeft = 100)))
                }
        return RuntimeLog.open(testTmp.resolve(logName)).use { log ->
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
    }

    private fun castleTrap(
        logName: String,
        dexterity: Int,
        wisdom: Int,
        nextRoll: Int? = null,
    ): RuntimeState {
        val thief = castleRuntime().updateMember(0) { member -> member.asThief(dexterity, wisdom) }
        val runtime = nextRoll?.let { roll -> thief.withNextRoll(roll) } ?: thief
        return RuntimeLog.open(testTmp.resolve(logName)).use { log ->
            val save =
                RuntimeSaveContext(testTmp.resolve("$logName.rsave"), bundleFile.readExternalCompiledBundleContentHash(), "test", null)
            CastleRuntime(RuntimeLoopContext(content, systems, ScriptedInput(""), NoOpRuntimeScreen(), save, log)).run(runtime)
        }
    }

    /** The party standing in an unsprung trap room on the castle's first floor, with a thief to try to disarm it. */
    private fun castleRuntime(): RuntimeState {
        val room = GridPoint(1, 1)
        val castle = systems.castleRows.single()
        return freshRuntime()
            .updateMember(0) { member -> member.asThief(dexterity = 50, wisdom = 50) }
            .copy(
                currentMode = RuntimeMode.Castle,
                castle = CastleState(castle.id, floor = 1, point = room, rooms = mapOf(room to "Pitfall"), roomEntryPending = true),
            )
    }

    private fun sell(
        logName: String,
        commands: String,
        runtime: RuntimeState,
    ): RuntimeState =
        RuntimeLog.open(testTmp.resolve(logName)).use { log ->
            ShopService(content, systems, ScriptedInput(commands), NoOpRuntimeScreen(), log).run(smith(), runtime)
        }

    /** The smith, without the beer-and-rumor interlude that would otherwise open every visit in a test. */
    private fun smith(): CompiledShopService =
        content.scripts.roots
            .asSequence()
            .flatMap { root -> root.instructions }
            .flatMap { instruction -> instruction.operands }
            .filterIsInstance<CompiledOperand.ShopService>()
            .map { operand -> operand.service }
            .first { service -> content.slug(service.building) == "smith" }
            .copy(interlude = null)

    private fun shopRuntime(item: CompiledHandleRef): RuntimeState =
        freshRuntime().let { runtime ->
            runtime.withParty(
                runtime.party.copy(
                    inventory = (1..INVENTORY_SAVE_SLOT_MAX).map { index -> CompiledInventorySlot(index, item.takeIf { index == 1 }) },
                ),
            )
        }

    private fun freshRuntime(): RuntimeState {
        val state = bundle.states.single { state -> content.slug(state.state) == "fresh-replay" }
        val runtime = RuntimeState.from(state, content)
        return runtime.withParty(runtime.party.copy(rngState = null))
    }

    /** This runtime with a random state whose next d100 roll is [roll]. */
    private fun RuntimeState.withNextRoll(roll: Int): RuntimeState {
        val seed = (1L..100_000L).first { seed -> party.copy(rngState = seed).drawRandom(0, 99).value == roll }
        return withParty(party.copy(rngState = seed))
    }

    private fun RuntimeState.withCharisma(charisma: Int): RuntimeState =
        withParty(party.copy(members = party.members.map { member -> member.copy(charisma = charisma) }))

    private fun CompiledPartyMember.asThief(
        dexterity: Int,
        wisdom: Int,
    ): CompiledPartyMember = copy(pcClass = PcClass.Thief, dexterity = dexterity, wisdom = wisdom)

    private fun hitPoints(runtime: RuntimeState): List<Int> = runtime.party.members.map { member -> member.hitPoints.current }

    private fun item(slug: String): CompiledHandleRef = content.ref(CompiledHandleKind.Item, slug)

    private companion object {
        // Party random states whose chest trap roll, on level 2 of the test dungeon, is 14 and 0.
        const val ROLL_14_SEED = 21L
        const val ROLL_0_SEED = 14L
        const val CHEST_KEYS = 2_000
        const val CHEST_TRANSCRIPT_TAIL = 200
    }
}
