package rylween

import rylween.compiled.loadCompiledGame
import rylween.compiled.toCompiledGameBundle
import rylween.dsl.EquipmentState
import rylween.dsl.Facing
import rylween.dsl.Gender
import rylween.dsl.InventorySaveSlotMax
import rylween.dsl.InventorySlotState
import rylween.dsl.PartyCounters
import rylween.dsl.PartyMemberState
import rylween.dsl.PartyResources
import rylween.dsl.PartyState
import rylween.dsl.PcClass
import rylween.dsl.Race
import rylween.dsl.RylweenModel
import rylween.dsl.SceneModel
import rylween.dsl.Wilderness
import rylween.dsl.item
import rylween.dsl.of
import rylween.dsl.partyId
import rylween.dsl.runtimeContent
import rylween.dsl.rylween
import rylween.dsl.sceneId
import rylween.engine.EngineTraceEvent
import rylween.engine.TraceEnginePort
import rylween.frontend.terminal.ScriptBridgeStatus
import rylween.frontend.terminal.StatefulScriptServiceRunner
import rylween.runtime.RuntimeSessionState
import rylween.runtime.ScriptContinuation
import rylween.runtime.ScriptRootRef
import rylween.runtime.town.SpeakRuntimeContent
import rylween.runtime.vm.CompiledVmResult
import rylween.runtime.vm.CompiledVmScriptRunner
import rylween.runtime.vm.RecordingCompiledVmHost
import rylween.runtime.vm.VmDiagnostic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RylweenCompiledVmScriptRunnerTest {
    @Test
    fun scriptRunnerExecutesLoadedRootByStableScriptRootRef() {
        val model = runnerModel()
        val scene = model.content.scenes.single { scene -> scene.id == runnerPureScene }
        val runtime = RuntimeSessionState.singleParty(testParty(), hour = 12, currentTown = null)
        val root = ScriptRootRef.scene(runnerPureScene)
        val authored = runAuthored(model, scene, runtime, root)
        val loaded = model.toCompiledGameBundle().loadCompiledGame()
        val host = RecordingCompiledVmHost()
        val runner = CompiledVmScriptRunner(loaded.content, host)

        val program = assertNotNull(runner.program(root))
        val result = runner.run(root, runtime)

        assertTrue(program.root.sameRoot(root))
        val completed = assertIs<CompiledVmResult.Completed>(result)
        assertEquals(authored.first.realm, completed.runtime.realm)
        assertEquals(authored.first.activeParty, completed.runtime.activeParty)
        assertEquals(authored.second, host.events)
        assertEquals(9, completed.runtime.activeParty.resources.gold)
    }

    @Test
    fun scriptRunnerReportsMissingRootsAsVmDiagnostics() {
        val loaded = runnerModel().toCompiledGameBundle().loadCompiledGame()
        val runner = CompiledVmScriptRunner(loaded.content)
        val missingRoot = ScriptRootRef.scene(sceneId("vm.runner.missing"))

        val result = runner.run(missingRoot, RuntimeSessionState.singleParty(testParty(), hour = 12, currentTown = null))

        val failed = assertIs<CompiledVmResult.Failed>(result)
        val diagnostic = assertIs<VmDiagnostic.MissingHandle>(failed.diagnostic)
        assertEquals(missingRoot, diagnostic.root)
        assertEquals("script root scene.vm.runner.missing", diagnostic.owner)
    }

    @Test
    fun scriptRunnerSurfacesUnsupportedOpcodeDiagnosticsWithoutAuthoredFallback() {
        val loaded = runnerModel().toCompiledGameBundle().loadCompiledGame()
        val runner = CompiledVmScriptRunner(loaded.content)
        val root = ScriptRootRef.scene(runnerUnsupportedScene)

        val result = runner.run(root, RuntimeSessionState.singleParty(testParty(), hour = 12, currentTown = null))

        val failed = assertIs<CompiledVmResult.Failed>(result)
        val diagnostic = assertIs<VmDiagnostic.UnsupportedHostService>(failed.diagnostic)
        assertTrue(diagnostic.root.sameRoot(root))
        assertEquals("ForEachMonster", diagnostic.service)
    }

    private fun runnerModel(): RylweenModel =
        rylween {
            content {
                scene(runnerPureScene) {
                    screen("runner-start")
                    party.gold += 7
                    stop("done")
                }
                scene(runnerUnsupportedScene) {
                    forEachMonster {
                        party.gold += 1
                    }
                }
            }
        }

    private fun runAuthored(
        model: RylweenModel,
        scene: SceneModel,
        runtime: RuntimeSessionState,
        root: ScriptRootRef,
    ): Pair<RuntimeSessionState, List<EngineTraceEvent>> {
        val engine = TraceEnginePort()
        val result =
            StatefulScriptServiceRunner(
                terminal = ScenarioTerminalPort(),
                engine = engine,
                rootOps = scene.ops,
                continuation = ScriptContinuation.start(runtime, root),
                speakContent = SpeakRuntimeContent(),
                battleContent = model.content.runtimeContent(),
                scriptRoot = root,
            ).run()
        assertEquals(ScriptBridgeStatus.Finished, result.status)
        assertNull(result.continuation)
        return result.runtime to engine.events
    }

    private fun testParty(): PartyState =
        PartyState(
            id = partyId("vm.runner.party"),
            player = "VM",
            name = "VM",
            position = Wilderness.xy(0, 0),
            facing = Facing.South,
            resources = PartyResources(gold = 2, torches = 0, food = 0, timeLeft = 10, restedHours = 0),
            counters = PartyCounters(memberCount = 1, kills = 0, waitingMail = false, attacked = false, attackedToday = emptyList()),
            flags = emptyList(),
            milestones = emptyList(),
            inventory = (0..InventorySaveSlotMax).map { slot -> InventorySlotState(slot, item = null) },
            members = listOf(testMember()),
            visitedNpcs = emptyList(),
            journal = emptyList(),
        )

    private fun testMember(): PartyMemberState =
        PartyMemberState(
            name = "Ada",
            gender = Gender.Female,
            race = Race.Human,
            pcClass = PcClass.Fighter,
            level = 1,
            health = 10,
            hitPoints = 10 of 10,
            weaponSkill = 10,
            wisdom = 10,
            mana = 0 of 0,
            charisma = 10,
            equipment = EquipmentState(item("none"), item("none")),
            experience = 0,
            dead = false,
        )

    private companion object {
        val runnerPureScene = sceneId("vm.runner.pure")
        val runnerUnsupportedScene = sceneId("vm.runner.unsupported")
    }
}
