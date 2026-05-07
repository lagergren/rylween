/**
 * J2.5 headless-host coverage for running script roots through the compiled VM.
 *
 * `engine/KotlinHeadlessHost.kt` exposes the first playable Kotlin host surface
 * that advertises `RuntimeScriptExecutionStrategy.CompiledVm`. These tests keep
 * the current authored-op runner as an oracle while proving scene, dungeon
 * event, spell, and item-use roots execute from the loaded compiled bundle.
 */
package rylween

import rylween.compiled.toExternalBundleArtifact
import rylween.dsl.EquipmentState
import rylween.dsl.Facing
import rylween.dsl.Gender
import rylween.dsl.InventorySaveSlotMax
import rylween.dsl.InventorySlotState
import rylween.dsl.ItemConsumePolicy
import rylween.dsl.Op
import rylween.dsl.PartyCounters
import rylween.dsl.PartyMemberState
import rylween.dsl.PartyResources
import rylween.dsl.PartyState
import rylween.dsl.PcClass
import rylween.dsl.Race
import rylween.dsl.RylweenModel
import rylween.dsl.Wilderness
import rylween.dsl.building
import rylween.dsl.dungeonEvent
import rylween.dsl.image
import rylween.dsl.item
import rylween.dsl.itemPool
import rylween.dsl.keyAction
import rylween.dsl.line
import rylween.dsl.npc
import rylween.dsl.npcJob
import rylween.dsl.of
import rylween.dsl.partyId
import rylween.dsl.rumourTable
import rylween.dsl.runtimeContent
import rylween.dsl.rylween
import rylween.dsl.sceneId
import rylween.dsl.spellId
import rylween.dsl.staticScreen
import rylween.dsl.town
import rylween.dsl.townPosition
import rylween.engine.EngineTraceEvent
import rylween.engine.KotlinHeadlessHost
import rylween.engine.RuntimeScriptExecutionStrategy
import rylween.engine.RylweenRuntimeHostContract
import rylween.engine.TraceEnginePort
import rylween.frontend.terminal.ScriptBridgeStatus
import rylween.frontend.terminal.StatefulScriptServiceRunner
import rylween.frontend.terminal.TerminalRunStatus
import rylween.frontend.terminal.TownSessionTerminalAdapter
import rylween.frontend.terminal.compiledVmTerminalScriptBridge
import rylween.runtime.RuntimeSessionState
import rylween.runtime.ScriptContinuation
import rylween.runtime.ScriptRootRef
import rylween.runtime.scriptRootForDungeonEvent
import rylween.runtime.scriptRootForItemUse
import rylween.runtime.scriptRootForScene
import rylween.runtime.scriptRootForSpell
import rylween.runtime.town.SpeakRuntimeContent
import rylween.runtime.town.TownSessionStep
import rylween.runtime.vm.CompiledVmResult
import rylween.runtime.vm.RecordingCompiledVmHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RylweenKotlinHeadlessCompiledVmHostTest {
    @Test
    fun authoredHeadlessHostDoesNotExposeCompiledVmRunner() {
        val host = KotlinHeadlessHost.fromExternalBundle(hostModel().toExternalBundleArtifact())

        assertEquals(RylweenRuntimeHostContract.kotlinHeadless, host.surface)
        assertFailsWith<IllegalStateException> {
            host.compiledScriptRunner()
        }
    }

    @Test
    fun compiledVmHeadlessHostExecutesRootFamiliesWithoutAuthoredFallback() {
        val model = hostModel()
        val runtimeContent = model.content.runtimeContent()
        val host = KotlinHeadlessHost.fromExternalBundleCompiledVm(model.toExternalBundleArtifact())

        assertEquals(RylweenRuntimeHostContract.kotlinHeadlessCompiledVm, host.surface)
        assertEquals(RuntimeScriptExecutionStrategy.CompiledVm, host.surface.scriptExecution)

        assertCompiledRootMatchesAuthored(
            model = model,
            host = host,
            root = runtimeContent.scriptRootForScene(hostScene),
            ops =
                model.content.scenes
                    .single { scene -> scene.id == hostScene }
                    .ops,
        ) { runtime, vmHost ->
            runCompiledScene(hostScene, runtime, vmHost)
        }
        assertCompiledRootMatchesAuthored(
            model = model,
            host = host,
            root = runtimeContent.scriptRootForDungeonEvent(hostEvent),
            ops =
                model.content.dungeons.events
                    .single { event -> event.id == hostEvent }
                    .ops,
        ) { runtime, vmHost ->
            runCompiledDungeonEvent(hostEvent, runtime, vmHost)
        }
        assertCompiledRootMatchesAuthored(
            model = model,
            host = host,
            root = runtimeContent.scriptRootForSpell(hostSpell),
            ops =
                model.content.spells
                    .single { spell -> spell.id == hostSpell }
                    .ops,
        ) { runtime, vmHost ->
            runCompiledSpell(hostSpell, runtime, vmHost)
        }
        assertCompiledRootMatchesAuthored(
            model = model,
            host = host,
            root = runtimeContent.scriptRootForItemUse(hostPotion),
            ops =
                model.content.itemUses
                    .single { use -> use.id == hostPotion }
                    .ops,
        ) { runtime, vmHost ->
            runCompiledItemUse(hostPotion, runtime, vmHost)
        }
    }

    @Test
    fun compiledVmTerminalBridgeResumesThroughTownSessionContinuation() {
        val host = KotlinHeadlessHost.fromExternalBundleCompiledVm(hostModel().toExternalBundleArtifact())
        val initial = host.townSession(testRuntime(), hostTown)
        val firstTerminal = ScenarioTerminalPort("H")

        val first =
            TownSessionTerminalAdapter(
                terminal = firstTerminal,
                serviceBridge = host.compiledVmTerminalScriptBridge(firstTerminal),
            ).runUntilBlocked(initial)

        assertIs<TerminalRunStatus.Waiting>(first.status)
        assertTrue(firstTerminal.output.any { line -> "compiled town service" in line })
        val blockedStep = assertIs<TownSessionStep.NativeService>(first.session.step)
        assertNotNull(blockedStep.continuation?.compiledVmContinuation)
        assertEquals(2, first.session.runtime.activeParty.resources.gold)

        val secondTerminal = ScenarioTerminalPort("")
        val second =
            TownSessionTerminalAdapter(
                terminal = secondTerminal,
                serviceBridge = host.compiledVmTerminalScriptBridge(secondTerminal),
            ).runUntilBlocked(first.session)

        assertIs<TerminalRunStatus.Waiting>(second.status)
        assertEquals(15, second.session.runtime.activeParty.resources.gold)
        assertIs<TownSessionStep.Menu>(second.session.step)
    }

    @Test
    fun compiledVmTerminalBridgeCompletesRumourChildSession() {
        val host = KotlinHeadlessHost.fromExternalBundleCompiledVm(hostModel().toExternalBundleArtifact())
        val terminal = ScenarioTerminalPort("R")

        val result =
            TownSessionTerminalAdapter(
                terminal = terminal,
                serviceBridge = host.compiledVmTerminalScriptBridge(terminal),
            ).runUntilBlocked(host.townSession(testRuntime(), hostTown))

        assertIs<TerminalRunStatus.Waiting>(result.status)
        assertTrue(terminal.output.any { line -> "A host rumour from the compiled VM." in line })
        assertEquals(19, result.session.runtime.activeParty.resources.gold)
        assertIs<TownSessionStep.Menu>(result.session.step)
    }

    @Test
    fun compiledVmTerminalBridgeResumesSpeakChildSession() {
        val host = KotlinHeadlessHost.fromExternalBundleCompiledVm(hostModel().toExternalBundleArtifact())
        val firstTerminal = ScenarioTerminalPort("S", "1")

        val first =
            TownSessionTerminalAdapter(
                terminal = firstTerminal,
                serviceBridge = host.compiledVmTerminalScriptBridge(firstTerminal),
            ).runUntilBlocked(host.townSession(testRuntime(), hostTown))

        assertIs<TerminalRunStatus.Waiting>(first.status)
        assertTrue(firstTerminal.output.any { line -> "Chatting with" in line })
        val blockedStep = assertIs<TownSessionStep.NativeService>(first.session.step)
        val blockedContinuation = assertNotNull(blockedStep.continuation)
        assertNotNull(blockedContinuation.compiledVmContinuation)
        assertNotNull(blockedContinuation.speakSession)
        assertTrue(
            first.session.runtime.activeParty.visitedNpcs.any { visit ->
                visit.npc == hostNpc && visit.visited
            },
        )

        val secondTerminal = ScenarioTerminalPort("B")
        val second =
            TownSessionTerminalAdapter(
                terminal = secondTerminal,
                serviceBridge = host.compiledVmTerminalScriptBridge(secondTerminal),
            ).runUntilBlocked(first.session)

        assertIs<TerminalRunStatus.Waiting>(second.status)
        assertEquals(25, second.session.runtime.activeParty.resources.gold)
        assertIs<TownSessionStep.Menu>(second.session.step)
    }

    @Test
    fun compiledVmTerminalBridgeCompletesRewardOfferAndShopChildren() {
        val host = KotlinHeadlessHost.fromExternalBundleCompiledVm(hostModel().toExternalBundleArtifact())

        val rewardTerminal = ScenarioTerminalPort("G")
        val reward =
            TownSessionTerminalAdapter(
                terminal = rewardTerminal,
                serviceBridge = host.compiledVmTerminalScriptBridge(rewardTerminal),
            ).runUntilBlocked(host.townSession(testRuntime(), hostTown))
        assertIs<TerminalRunStatus.Waiting>(reward.status)
        assertEquals(34, reward.session.runtime.activeParty.resources.gold)
        assertIs<TownSessionStep.Menu>(reward.session.step)

        val offerTerminal = ScenarioTerminalPort("O", "N")
        val offer =
            TownSessionTerminalAdapter(
                terminal = offerTerminal,
                serviceBridge = host.compiledVmTerminalScriptBridge(offerTerminal),
            ).runUntilBlocked(host.townSession(testRuntime(), hostTown))
        assertIs<TerminalRunStatus.Waiting>(offer.status)
        assertTrue(offerTerminal.output.any { line -> "Declined offer." in line })
        assertEquals(31, offer.session.runtime.activeParty.resources.gold)
        assertIs<TownSessionStep.Menu>(offer.session.step)

        val firstShopTerminal = ScenarioTerminalPort("P")
        val firstShop =
            TownSessionTerminalAdapter(
                terminal = firstShopTerminal,
                serviceBridge = host.compiledVmTerminalScriptBridge(firstShopTerminal),
            ).runUntilBlocked(host.townSession(testRuntime(), hostTown))
        assertIs<TerminalRunStatus.Waiting>(firstShop.status)
        assertTrue(firstShopTerminal.output.any { line -> "Compiled shop intro." in line })
        val blockedShop = assertIs<TownSessionStep.NativeService>(firstShop.session.step)
        val shopContinuation = assertNotNull(blockedShop.continuation)
        assertNotNull(shopContinuation.compiledVmContinuation)
        assertNotNull(shopContinuation.shopServiceSession)

        val secondShopTerminal = ScenarioTerminalPort("L")
        val secondShop =
            TownSessionTerminalAdapter(
                terminal = secondShopTerminal,
                serviceBridge = host.compiledVmTerminalScriptBridge(secondShopTerminal),
            ).runUntilBlocked(firstShop.session)
        assertIs<TerminalRunStatus.Waiting>(secondShop.status)
        assertTrue(secondShopTerminal.output.any { line -> "Compiled shop goodbye." in line })
        assertEquals(33, secondShop.session.runtime.activeParty.resources.gold)
        assertIs<TownSessionStep.Menu>(secondShop.session.step)
    }

    private fun assertCompiledRootMatchesAuthored(
        model: RylweenModel,
        host: KotlinHeadlessHost,
        root: ScriptRootRef,
        ops: List<Op>,
        runCompiled: KotlinHeadlessHost.(RuntimeSessionState, RecordingCompiledVmHost) -> CompiledVmResult,
    ) {
        val runtime = testRuntime()
        val authored = runAuthored(model, ops, runtime, root)
        val vmHost = RecordingCompiledVmHost()
        val result = host.runCompiled(runtime, vmHost)

        val completed = assertIs<CompiledVmResult.Completed>(result)
        assertEquals(authored.first.realm, completed.runtime.realm)
        assertEquals(authored.first.activeParty, completed.runtime.activeParty)
        assertEquals(authored.second, vmHost.events)
    }

    private fun hostModel(): RylweenModel =
        rylween {
            content {
                visuals {
                    staticScreens {
                        image(hostShopScreen, hostShopImage)
                    }
                }
                items {
                    potion(hostPotion, "Host Potion", cost = 1)
                    treasure(hostOfferItem, "Host Offer Gem", cost = 9)
                    pool(hostOfferPool, hostOfferItem)
                }
                scene(hostScene) {
                    screen("scene root")
                    party.gold += 3
                    stop("scene done")
                }
                scene(hostTownScene) {
                    screen("compiled town service")
                    waitInput("town service")
                    party.gold += 13
                    stop("town done")
                }
                scene(hostRumourScene) {
                    tellRumour(hostRumourTable)
                    party.gold += 17
                    stop("rumour done")
                }
                scene(hostSpeakScene) {
                    speak(hostSpeakBuilding)
                    party.gold += 23
                    stop("speak done")
                }
                scene(hostRewardScene) {
                    reward {
                        gold(31)
                    }
                    party.gold += 1
                    stop("reward done")
                }
                scene(hostOfferScene) {
                    choice.randomItemOffer(hostOfferPool) {
                        offer {
                            line("Offer ", itemName, " for ", itemPrice)
                        }
                        broke("Broke.")
                        full("Full.")
                        deal("Deal.")
                        declined("Declined offer.")
                    }
                    party.gold += 29
                    stop("offer done")
                }
                scene(hostShopScene) {
                    shopService(hostShopChildBuilding) {
                        image = hostShopImage
                        intro("Compiled shop intro.")
                        menu("(L)eave")
                        goodbye("Compiled shop goodbye.")
                    }
                    party.gold += 31
                    stop("shop done")
                }
                speak {
                    standardConversation {}
                    textTables {
                        rumourTable(hostRumourTable) {
                            rumour {
                                line("A host rumour from the compiled VM.")
                            }
                        }
                    }
                }
                dungeons {
                    event(hostEvent) {
                        run {
                            screen("event root")
                            party.gold += 5
                            stop("event done")
                        }
                    }
                }
                spell(hostSpell, slot = 1, baseCost = 0, name = "Host Spell") {
                    screen("spell root")
                    party.gold += 7
                    stop("spell done")
                }
                itemUse(hostPotion) {
                    inventory()
                    consume = ItemConsumePolicy.NeverConsumes
                    effect {
                        screen("item root")
                        party.gold += 11
                        stop("item done")
                    }
                }
                towns {
                    town(hostTown.slug) {
                        name = "Host Town"
                        services {
                            service(hostBuilding)
                            service(hostRumourBuilding)
                            service(hostSpeakBuilding)
                            service(hostRewardBuilding)
                            service(hostOfferBuilding)
                            service(hostShopServiceBuilding)
                        }
                    }
                }
                characters {
                    character(hostNpc, "Edda", hostNpcJob) {
                        fixed(townPosition(hostTown, hostSpeakBuilding))
                    }
                }
                townSystem {
                    menu {
                        service('H', hostBuilding, hostAction)
                        service('R', hostRumourBuilding, hostRumourAction)
                        service('S', hostSpeakBuilding, hostSpeakAction)
                        service('G', hostRewardBuilding, hostRewardAction)
                        service('O', hostOfferBuilding, hostOfferAction)
                        service('P', hostShopServiceBuilding, hostShopAction)
                        leave('L')
                    }
                    buildingServices {
                        building(hostBuilding, hostAction, hostTownScene) {
                            alwaysOpen()
                        }
                        building(hostRumourBuilding, hostRumourAction, hostRumourScene) {
                            alwaysOpen()
                        }
                        building(hostSpeakBuilding, hostSpeakAction, hostSpeakScene) {
                            alwaysOpen()
                        }
                        building(hostRewardBuilding, hostRewardAction, hostRewardScene) {
                            alwaysOpen()
                        }
                        building(hostOfferBuilding, hostOfferAction, hostOfferScene) {
                            alwaysOpen()
                        }
                        building(hostShopServiceBuilding, hostShopAction, hostShopScene) {
                            alwaysOpen()
                        }
                    }
                }
            }
        }

    private fun runAuthored(
        model: RylweenModel,
        ops: List<Op>,
        runtime: RuntimeSessionState,
        root: ScriptRootRef,
    ): Pair<RuntimeSessionState, List<EngineTraceEvent>> {
        val engine = TraceEnginePort()
        val result =
            StatefulScriptServiceRunner(
                terminal = ScenarioTerminalPort(),
                engine = engine,
                rootOps = ops,
                continuation = ScriptContinuation.start(runtime, root),
                speakContent = SpeakRuntimeContent(),
                battleContent = model.content.runtimeContent(),
                scriptRoot = root,
            ).run()
        assertEquals(ScriptBridgeStatus.Finished, result.status)
        assertNull(result.continuation)
        return result.runtime to engine.events
    }

    private fun testRuntime(): RuntimeSessionState = RuntimeSessionState.singleParty(testParty(), hour = 12, currentTown = null)

    private fun testParty(): PartyState =
        PartyState(
            id = partyId("vm.host.party"),
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
        val hostScene = sceneId("vm.host.scene")
        val hostTownScene = sceneId("vm.host.town-service")
        val hostRumourScene = sceneId("vm.host.rumour-service")
        val hostSpeakScene = sceneId("vm.host.speak-service")
        val hostRewardScene = sceneId("vm.host.reward-service")
        val hostOfferScene = sceneId("vm.host.offer-service")
        val hostShopScene = sceneId("vm.host.shop-service")
        val hostRumourTable = rumourTable("vm.host.rumours")
        val hostEvent = dungeonEvent("vm.host.event")
        val hostSpell = spellId("vm.host.spell")
        val hostPotion = item("vm.host.potion")
        val hostOfferItem = item("vm.host.offer-gem")
        val hostOfferPool = itemPool("vm.host.offer-pool")
        val hostShopScreen = staticScreen("vm.host.shop-screen")
        val hostShopImage = image("vm.host.shop-image")
        val hostTown = town("vm.host.town")
        val hostBuilding = building("vm.host.building")
        val hostAction = keyAction("vm.host.action")
        val hostRumourBuilding = building("vm.host.rumour-building")
        val hostRumourAction = keyAction("vm.host.rumour-action")
        val hostSpeakBuilding = building("vm.host.speak-building")
        val hostSpeakAction = keyAction("vm.host.speak-action")
        val hostNpc = npc("vm.host.edda")
        val hostNpcJob = npcJob("vm.host.guide")
        val hostRewardBuilding = building("vm.host.reward-building")
        val hostRewardAction = keyAction("vm.host.reward-action")
        val hostOfferBuilding = building("vm.host.offer-building")
        val hostOfferAction = keyAction("vm.host.offer-action")
        val hostShopServiceBuilding = building("vm.host.shop-service-building")
        val hostShopChildBuilding = building("vm.host.shop-child-building")
        val hostShopAction = keyAction("vm.host.shop-action")
    }
}
