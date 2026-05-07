/**
 * Regression tests for compiled VM child-session handoff boundaries.
 *
 * These exercise the J2.4 seam in `runtime/vm/CompiledVm.kt`: the VM emits a
 * typed child request, a host runs the child session, and the VM resumes from
 * the child completion without replaying parent-script effects.
 */
package rylween

import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledScriptProgram
import rylween.compiled.LoadedCompiledGame
import rylween.compiled.loadCompiledGame
import rylween.compiled.toCompiledGameBundle
import rylween.dsl.BuildingId
import rylween.dsl.DungeonId
import rylween.dsl.EncounterTier
import rylween.dsl.EquipmentState
import rylween.dsl.Facing
import rylween.dsl.Gender
import rylween.dsl.ImageId
import rylween.dsl.InventorySaveSlotMax
import rylween.dsl.InventorySlotState
import rylween.dsl.ItemId
import rylween.dsl.ItemPoolId
import rylween.dsl.MonsterId
import rylween.dsl.NativeAllowlist
import rylween.dsl.PartyCounters
import rylween.dsl.PartyMemberState
import rylween.dsl.PartyResources
import rylween.dsl.PartyState
import rylween.dsl.PcClass
import rylween.dsl.Race
import rylween.dsl.RumourTableId
import rylween.dsl.RuntimeMode
import rylween.dsl.RylweenModel
import rylween.dsl.SceneId
import rylween.dsl.StaticScreenId
import rylween.dsl.Wilderness
import rylween.dsl.encounterTier
import rylween.dsl.image
import rylween.dsl.inlineText
import rylween.dsl.item
import rylween.dsl.itemPool
import rylween.dsl.line
import rylween.dsl.lit
import rylween.dsl.monster
import rylween.dsl.of
import rylween.dsl.partyId
import rylween.dsl.rumourTable
import rylween.dsl.rylween
import rylween.dsl.sceneId
import rylween.dsl.staticScreen
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

    @Test
    fun compiledVmBlocksSpeakAndRumourAsHostChildRequests() {
        val speak = runUntilChild(speakModel(), speakScene)
        val speakRequest = assertIs<CompiledVmChildRequest.Speak>(speak.pending.request)
        assertEquals(childBuilding, speakRequest.building)
        assertEquals(listOf(EngineTraceEvent.Speak(childBuilding)), speak.host.events)

        val rumour = runUntilChild(rumourModel(), rumourScene)
        val rumourRequest = assertIs<CompiledVmChildRequest.TellRumour>(rumour.pending.request)
        assertEquals(childRumourTable, rumourRequest.table)
        assertEquals(listOf(EngineTraceEvent.Rumour(childRumourTable)), rumour.host.events)
    }

    @Test
    fun compiledVmBlocksDungeonHandoffAndResumesParentFrames() {
        val child = runUntilChild(dungeonModel(), dungeonScene)
        val request = assertIs<CompiledVmChildRequest.EnterDungeon>(child.pending.request)

        assertEquals(childDungeon, request.handoff.dungeon)
        assertEquals(3, request.handoff.levels)
        assertEquals(RuntimeMode.Dungeon, child.blocked.continuation.runtime.realm.currentMode)
        assertEquals(listOf(EngineTraceEvent.DungeonHandoff(request.handoff)), child.host.events)

        val resumed =
            child.vm.resume(
                child.program,
                child.blocked.continuation
                    .toSnapshot()
                    .toContinuation(child.blocked.continuation.runtime),
                CompiledVmInput.ChildSessionCompleted(CompiledVmChildCompletion(runtime(gold = 30))),
            )

        val completed = assertIs<CompiledVmResult.Completed>(resumed)
        assertEquals(34, completed.runtime.activeParty.resources.gold)
        assertEquals(
            listOf(
                EngineTraceEvent.DungeonHandoff(request.handoff),
                EngineTraceEvent.Screen(inlineText("after dungeon")),
            ),
            child.host.events,
        )
    }

    @Test
    fun compiledVmBlocksRewardOfferAndShopAsHostChildRequests() {
        val reward = runUntilChild(rewardModel(), rewardScene)
        val rewardRequest = assertIs<CompiledVmChildRequest.GrantReward>(reward.pending.request)
        assertEquals(7, rewardRequest.gold)
        assertTrue(reward.host.events.single() is EngineTraceEvent.GrantReward)

        val offer = runUntilChild(randomOfferModel(), randomOfferScene)
        val offerRequest = assertIs<CompiledVmChildRequest.RandomItemOffer>(offer.pending.request)
        assertEquals(0, offerRequest.selectedIndex)
        assertEquals(childPool, offerRequest.offer.pool.toPoolId(offer.compiled))
        assertTrue(offer.host.events.single() is EngineTraceEvent.RandomItemOffer)

        val shop = runUntilChild(shopModel(), shopScene)
        val shopRequest = assertIs<CompiledVmChildRequest.ShopService>(shop.pending.request)
        assertEquals(childBuilding, shopRequest.shop.building.toBuildingId(shop.compiled))
        assertTrue(shop.host.events.single() is EngineTraceEvent.ShopService)
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

    private fun speakModel(): RylweenModel =
        rylween {
            content {
                towns {
                    town("vm.child.town") {
                        services {
                            service(childBuilding)
                        }
                    }
                }
                scene(speakScene) {
                    speak(childBuilding)
                }
            }
        }

    private fun rumourModel(): RylweenModel =
        rylween {
            content {
                speak {
                    textTables {
                        rumourTable(childRumourTable) {
                            rumour {
                                line("A test rumour.")
                            }
                        }
                    }
                }
                scene(rumourScene) {
                    tellRumour(childRumourTable)
                }
            }
        }

    private fun dungeonModel(): RylweenModel =
        rylween {
            content {
                dungeons {
                    scenario(childDungeon) {
                        name("Child Dungeon")
                        levels(3)
                    }
                }
                scene(dungeonScene) {
                    enterDungeon(childDungeon, levels = 3)
                    party.gold += 4
                    screen("after dungeon")
                }
            }
        }

    private fun rewardModel(): RylweenModel =
        rylween {
            content {
                scene(rewardScene) {
                    reward {
                        gold(7)
                    }
                }
            }
        }

    private fun randomOfferModel(): RylweenModel =
        rylween {
            content {
                items {
                    treasure(childItem, "Child Gem", cost = 9)
                    pool(childPool, childItem)
                }
                scene(randomOfferScene) {
                    choice.randomItemOffer(childPool) {
                        offer {
                            line("Offer ", itemName, " for ", itemPrice)
                        }
                        broke("Broke.")
                        full("Full.")
                        deal("Deal.")
                        declined("Declined.")
                    }
                }
            }
        }

    private fun shopModel(): RylweenModel =
        rylween {
            content {
                visuals {
                    staticScreens {
                        image(childScreen, childShopImage)
                    }
                }
                towns {
                    town("vm.child.shop_town") {
                        services {
                            service(childBuilding)
                        }
                    }
                }
                scene(shopScene) {
                    shopService(childBuilding) {
                        image = childShopImage
                        intro("Intro.")
                        menu("(L)eave")
                    }
                }
            }
        }

    private fun runUntilChild(
        model: RylweenModel,
        scene: SceneId,
    ): ChildRun {
        val (compiled, program) = compiledScene(model, scene)
        val host = RecordingCompiledVmHost()
        val vm = CompiledVm(compiled.content, host)
        val blocked = assertIs<CompiledVmResult.Blocked>(vm.run(program, CompiledVmStart(runtime())))
        val pending = assertIs<CompiledVmPendingInput.ChildSession>(blocked.pending)
        assertEquals(blocked.continuation, blocked.continuation.toSnapshot().toContinuation(blocked.continuation.runtime))
        return ChildRun(compiled, program, vm, host, blocked, pending)
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
        val speakScene: SceneId = sceneId("vm.child.speak")
        val rumourScene: SceneId = sceneId("vm.child.rumour")
        val dungeonScene: SceneId = sceneId("vm.child.dungeon")
        val rewardScene: SceneId = sceneId("vm.child.reward")
        val randomOfferScene: SceneId = sceneId("vm.child.offer")
        val shopScene: SceneId = sceneId("vm.child.shop")
        val encounterMonster: MonsterId = monster("vm.child.monster")
        val encounterTier: EncounterTier = encounterTier("vm.child.tier")
        val childBuilding: BuildingId = BuildingId("vm.child.building")
        val childRumourTable: RumourTableId = rumourTable("vm.child.rumours")
        val childDungeon: DungeonId = DungeonId("vm.child.dungeon")
        val childItem: ItemId = item("vm.child.gem")
        val childPool: ItemPoolId = itemPool("vm.child.pool")
        val childShopImage: ImageId = image("vm.child.shop.image")
        val childScreen: StaticScreenId = staticScreen("vm.child.shop.screen")
    }

    private data class ChildRun(
        val compiled: LoadedCompiledGame,
        val program: CompiledScriptProgram,
        val vm: CompiledVm,
        val host: RecordingCompiledVmHost,
        val blocked: CompiledVmResult.Blocked,
        val pending: CompiledVmPendingInput.ChildSession,
    )
}

private fun CompiledHandleRef.toPoolId(compiled: LoadedCompiledGame): ItemPoolId =
    itemPool(compiled.content.string(compiled.content.handle(this).slugString))

private fun CompiledHandleRef.toBuildingId(compiled: LoadedCompiledGame): BuildingId =
    BuildingId(compiled.content.string(compiled.content.handle(this).slugString))
