/**
 * Regression tests for compiled VM child-session handoff boundaries.
 *
 * These exercise the J2.4 seam in `runtime/vm/CompiledVm.kt`: the VM emits a
 * typed child request, a host runs the child session, and the VM resumes from
 * the child completion without replaying parent-script effects.
 */
package rylween

import rylween.compiled.CompiledScriptProgram
import rylween.compiled.LoadedCompiledGame
import rylween.compiled.loadCompiledGame
import rylween.compiled.toCompiledGameBundle
import rylween.dsl.EncounterTier
import rylween.dsl.EquipmentState
import rylween.dsl.Facing
import rylween.dsl.Gender
import rylween.dsl.InventorySaveSlotMax
import rylween.dsl.InventorySlotState
import rylween.dsl.MonsterId
import rylween.dsl.NativeAllowlist
import rylween.dsl.PartyCounters
import rylween.dsl.PartyMemberState
import rylween.dsl.PartyResources
import rylween.dsl.PartyState
import rylween.dsl.PcClass
import rylween.dsl.Race
import rylween.dsl.RylweenModel
import rylween.dsl.SceneId
import rylween.dsl.Wilderness
import rylween.dsl.encounterTier
import rylween.dsl.inlineText
import rylween.dsl.item
import rylween.dsl.lit
import rylween.dsl.monster
import rylween.dsl.of
import rylween.dsl.partyId
import rylween.dsl.rylween
import rylween.dsl.sceneId
import rylween.engine.EngineRequest
import rylween.engine.EngineTraceEvent
import rylween.runtime.RuntimeSessionState
import rylween.runtime.ScriptRootRef
import rylween.runtime.vm.CompiledVm
import rylween.runtime.vm.CompiledVmChildCompletion
import rylween.runtime.vm.CompiledVmChildRequest
import rylween.runtime.vm.CompiledVmInput
import rylween.runtime.vm.CompiledVmPendingInput
import rylween.runtime.vm.CompiledVmResult
import rylween.runtime.vm.CompiledVmStart
import rylween.runtime.vm.RecordingCompiledVmHost
import rylween.runtime.vm.toContinuation
import rylween.runtime.vm.toSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RylweenCompiledVmChildSessionTest {
    @Test
    fun compiledVmTracesRunSceneWithoutRequiringAuthoredInterpreterFallback() {
        val model = runSceneModel()
        val (compiled, program) = compiledScene(model, parentScene)
        val host = RecordingCompiledVmHost()

        val result = CompiledVm(compiled.content, host).run(program, CompiledVmStart(runtime()))

        assertIs<CompiledVmResult.Completed>(result)
        assertEquals(
            listOf(EngineTraceEvent.RunScene(childScene, mapOf("speaker" to "Ada"))),
            host.events,
        )
    }

    @Test
    fun compiledVmBlocksEncounterAsChildSessionAndResumesAfterCompletion() {
        val (compiled, program) = compiledScene(encounterModel(), encounterScene)
        val host = RecordingCompiledVmHost()
        val vm = CompiledVm(compiled.content, host)

        val result = vm.run(program, CompiledVmStart(runtime()))

        val blocked = assertIs<CompiledVmResult.Blocked>(result)
        val pending = assertIs<CompiledVmPendingInput.ChildSession>(blocked.pending)
        val request = assertIs<CompiledVmChildRequest.Encounter>(pending.request)
        assertEquals(encounterMonster, request.monster)
        assertEquals(encounterTier, request.tier)
        assertTrue(request.mandatory)
        assertEquals(1, pending.instructionIndex)
        assertEquals(listOf(1), pending.operandPath)
        assertEquals(
            listOf(
                EngineTraceEvent.Screen(inlineText("before")),
                EngineTraceEvent.Encounter(encounterMonster, mandatory = true, tier = encounterTier),
            ),
            host.events,
        )

        val childRuntime = runtime(gold = 20)
        val restored = blocked.continuation.toSnapshot().toContinuation(blocked.continuation.runtime)
        val resumed = vm.resume(program, restored, CompiledVmInput.ChildSessionCompleted(CompiledVmChildCompletion(childRuntime)))

        val completed = assertIs<CompiledVmResult.Completed>(resumed)
        assertEquals(24, completed.runtime.activeParty.resources.gold)
        assertEquals(
            listOf(
                EngineTraceEvent.Screen(inlineText("before")),
                EngineTraceEvent.Encounter(encounterMonster, mandatory = true, tier = encounterTier),
                EngineTraceEvent.Screen(inlineText("after")),
            ),
            host.events,
        )
    }

    @Test
    fun compiledVmExecutesAllowlistedNativeEffectCallsThroughHostTrace() {
        val (compiled, program) = compiledScene(nativeCallModel(), nativeCallScene)
        val host = RecordingCompiledVmHost()

        val result = CompiledVm(compiled.content, host).run(program, CompiledVmStart(runtime()))

        assertIs<CompiledVmResult.Completed>(result)
        assertEquals(
            listOf(EngineTraceEvent.NativeEffect(EngineRequest(NativeAllowlist.inventoryAdd.path, listOf("Lit(value=7)")))),
            host.events,
        )
    }

    private fun runSceneModel(): RylweenModel =
        rylween {
            content {
                scene(parentScene) {
                    run(childScene, "speaker" to "Ada")
                }
                scene(childScene) {
                    screen("child")
                }
            }
        }

    private fun encounterModel(): RylweenModel =
        rylween {
            content {
                scene(encounterScene) {
                    screen("before")
                    encounter(encounterMonster, mandatory = true, tier = encounterTier)
                    party.gold += 4
                    screen("after")
                }
            }
        }

    private fun nativeCallModel(): RylweenModel =
        rylween {
            content {
                scene(nativeCallScene) {
                    native.inventory.add(lit(7))
                }
            }
        }

    private fun compiledScene(
        model: RylweenModel,
        scene: SceneId,
    ): Pair<LoadedCompiledGame, CompiledScriptProgram> {
        val compiled = model.toCompiledGameBundle().loadCompiledGame()
        val program =
            compiled.content.scripts.values.single {
                it.root.sameRoot(ScriptRootRef.scene(scene))
            }
        return compiled to program
    }

    private fun runtime(gold: Int = 2): RuntimeSessionState =
        RuntimeSessionState.singleParty(testParty(gold), hour = 12, currentTown = null)

    private fun testParty(gold: Int): PartyState =
        PartyState(
            id = partyId("vm.child.party"),
            player = "VM",
            name = "VM",
            position = Wilderness.xy(0, 0),
            facing = Facing.South,
            resources = PartyResources(gold = gold, torches = 0, food = 0, timeLeft = 10, restedHours = 0),
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
        val parentScene: SceneId = sceneId("vm.child.parent")
        val childScene: SceneId = sceneId("vm.child.child")
        val encounterScene: SceneId = sceneId("vm.child.encounter")
        val nativeCallScene: SceneId = sceneId("vm.child.native")
        val encounterMonster: MonsterId = monster("vm.child.monster")
        val encounterTier: EncounterTier = encounterTier("vm.child.tier")
    }
}
