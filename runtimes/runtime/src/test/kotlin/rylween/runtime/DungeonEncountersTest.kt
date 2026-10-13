package rylween.runtime

import rylween.compiled.CompiledHandleKind
import rylween.compiled.readExternalCompiledBundleContentHash
import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeSystems
import rylween.dsl.impl.PcClass
import rylween.dsl.impl.RuntimeMode
import rylween.dsl.impl.of
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Wandering monsters in dungeons: beaten ones stay beaten, and Banish Evil clears them all. */
class DungeonEncountersTest {
    private val bundleFile = File(requiredRuntimeTestProperty("rylween.runtime.bundle"))
    private val bundle = bundleFile.readExternalCompiledGameBundle()
    private val systems = bundleFile.readExternalRuntimeSystems()
    private val content = bundle.content
    private val hash = bundleFile.readExternalCompiledBundleContentHash()
    private val testTmp = File(requiredRuntimeTestProperty("rylween.runtime.testTmp")).resolve("dungeon-encounters").also(File::mkdirs)
    private val actions = AutopilotActions(systems)

    @Test
    fun monstersBeatenOnALevelAreGoneWhenThePartyComesBack() {
        val transcript = explore("revisit") { listOf(actions.descend(), actions.ascend(), actions.descend(), actions.ascend(), actions.ascend()) }

        assertEquals(1, transcript.count { line -> line.trim() == "You face" }, transcript.joinToString("\n"))
    }

    @Test
    fun banishEvilClearsTheWanderingMonstersFromTheDungeon() {
        val transcript =
            explore("banish") { state ->
                val priest = AutopilotParty(content).firstLiving(state.party, PcClass.Priest)!!
                listOf(actions.castPriestSpell(priest, "Banish Evil", commands = actions.commandsAt(state)), actions.descend(), actions.ascend(), actions.ascend())
            }

        assertEquals(0, transcript.count { line -> line.trim() == "You face" }, transcript.joinToString("\n"))
    }

    /** Plays [steps] in a three-level dungeon whose second level holds wandering monsters, with a strong party. */
    private fun explore(
        name: String,
        steps: (RuntimeState) -> List<AutopilotAction>,
    ): List<String> {
        val base = RuntimeState.from(bundle.states.single { state -> content.slug(state.state) == "strong-replay" }, content)
        val strong =
            base.withParty(
                base.party.copy(
                    rngState = SEED,
                    members = base.party.members.map { member -> member.copy(wisdom = MANA, mana = MANA of MANA) },
                ),
            )
        val inDungeon =
            RuntimeLog.open(testTmp.resolve("$name-setup.log")).use { log ->
                strong.enterDungeon(content, content.ref(CompiledHandleKind.Dungeon, "ogre_cave"), levels = 3, log = log)
            }
        val encounter = content.ref(CompiledHandleKind.DungeonEvent, "weak_encounter")
        val start = inDungeon.copy(dungeon = inDungeon.dungeon?.copy(levelEvents = mapOf(2 to encounter)))
        val save = testTmp.resolve("$name.rsave").also(File::delete)
        SaveFile.write(save, start, hash, content, "dungeon-test", null)
        val queue = ArrayDeque(steps(start))
        val plan =
            object : AutopilotPlan {
                override fun done(state: RuntimeState): Boolean = queue.isEmpty() && state.currentMode == RuntimeMode.Wilderness

                override fun wilderness(state: RuntimeState): AutopilotAction = throw AutopilotStuck("left the dungeon with steps to go")

                override fun dungeon(state: RuntimeState): AutopilotAction = queue.removeFirstOrNull() ?: throw AutopilotStuck("out of steps")
            }
        val ports = AutopilotPorts(Autopilot(content, systems, plan), KEYS, TRANSCRIPT_TAIL)
        RuntimeLog.open(testTmp.resolve("$name.log")).use { log ->
            RuntimeSessionFactory.load(bundle, systems, hash).start(ports, ports, RuntimeSessionConfig("dungeon-test", "strong-replay", save, save), log)
        }
        return ports.transcript
    }

    private companion object {
        const val SEED = 4_242L
        const val MANA = 300
        const val KEYS = 2_000
        const val TRANSCRIPT_TAIL = 80
    }
}
