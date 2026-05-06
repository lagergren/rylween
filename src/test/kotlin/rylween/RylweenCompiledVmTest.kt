package rylween

import rylween.compiled.CompiledOpcode
import rylween.compiled.CompiledScriptProgram
import rylween.compiled.LoadedCompiledGame
import rylween.compiled.loadCompiledGame
import rylween.compiled.toCompiledGameBundle
import rylween.dsl.EquipmentState
import rylween.dsl.Facing
import rylween.dsl.FlagRef
import rylween.dsl.Gender
import rylween.dsl.InventorySaveSlotMax
import rylween.dsl.InventorySlotRef
import rylween.dsl.InventorySlotState
import rylween.dsl.ItemId
import rylween.dsl.JournalEntryState
import rylween.dsl.MemberChoiceFilter
import rylween.dsl.NativeAllowlist
import rylween.dsl.PartyCounters
import rylween.dsl.PartyMemberState
import rylween.dsl.PartyResources
import rylween.dsl.PartyState
import rylween.dsl.PcClass
import rylween.dsl.Race
import rylween.dsl.RealmState
import rylween.dsl.RuntimeMode
import rylween.dsl.RylweenModel
import rylween.dsl.SceneId
import rylween.dsl.SceneModel
import rylween.dsl.StateKind
import rylween.dsl.StateModel
import rylween.dsl.TextPattern
import rylween.dsl.Wilderness
import rylween.dsl.declareFlag
import rylween.dsl.eq
import rylween.dsl.inlineText
import rylween.dsl.inputKey
import rylween.dsl.item
import rylween.dsl.itemPool
import rylween.dsl.line
import rylween.dsl.milestone
import rylween.dsl.of
import rylween.dsl.partyId
import rylween.dsl.partyName
import rylween.dsl.runtimeContent
import rylween.dsl.rylween
import rylween.dsl.sceneId
import rylween.dsl.stateId
import rylween.dsl.text
import rylween.dsl.v
import rylween.dsl.worldMap
import rylween.engine.EngineRequest
import rylween.engine.EngineTraceEvent
import rylween.engine.TraceEnginePort
import rylween.frontend.terminal.ScriptBridgeStatus
import rylween.frontend.terminal.StatefulScriptServiceRunner
import rylween.frontend.terminal.TerminalPort
import rylween.runtime.RuntimeSessionState
import rylween.runtime.ScriptContinuation
import rylween.runtime.ScriptRootRef
import rylween.runtime.hasFlagState
import rylween.runtime.town.SpeakRuntimeContent
import rylween.runtime.vm.CompiledVm
import rylween.runtime.vm.CompiledVmInput
import rylween.runtime.vm.CompiledVmPendingInput
import rylween.runtime.vm.CompiledVmResult
import rylween.runtime.vm.CompiledVmStart
import rylween.runtime.vm.NativeServiceArgSlot
import rylween.runtime.vm.NativeServiceCatalog
import rylween.runtime.vm.NativeServiceDescriptor
import rylween.runtime.vm.NativeServiceKind
import rylween.runtime.vm.RecordingCompiledVmHost
import rylween.runtime.vm.VmDiagnostic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RylweenCompiledVmTest {
    @Test
    fun compiledVmMatchesAuthoredInterpreterForPureSceneRoot() {
        val model = pureSceneModel()
        val scene = model.content.scenes.single()
        val runtime = RuntimeSessionState.singleParty(testParty(), hour = 12, currentTown = null)
        val authored = runAuthored(model, scene, runtime)
        val (compiled, program) = compiledScene(model, scene.id)
        val host = RecordingCompiledVmHost()

        val result = CompiledVm(compiled.content, host).run(program, CompiledVmStart(runtime))

        val completed = assertIs<CompiledVmResult.Completed>(result)
        assertEquals(authored.first.realm, completed.runtime.realm)
        assertEquals(
            authored.first.activeParty.copy(flags = emptyList()),
            completed.runtime.activeParty.copy(flags = emptyList()),
        )
        assertEquals(authored.first.activeParty.flagTokens(), completed.runtime.activeParty.flagTokens())
        assertEquals(authored.second, host.events)
        assertEquals("Ada", completed.textBindings["leader"])
        assertEquals(23, completed.runtime.activeParty.resources.gold)
        assertEquals(75, completed.runtime.activeParty.resources.timeLeft)
        assertEquals(1, completed.runtime.activeParty.resources.torches)
        assertTrue(completed.runtime.activeParty.hasFlagState(VmStoryFlag.ready))
        assertTrue(VmMilestone in completed.runtime.activeParty.milestones)
        assertEquals(listOf(JournalEntryState.Line("journal line")), completed.runtime.activeParty.journal)
    }

    @Test
    fun compiledVmMatchesAuthoredInterpreterForInventoryAndNativeExpressions() {
        val model = expressionSceneModel()
        val scene = model.content.scenes.single()
        val runtime =
            RuntimeSessionState.singleParty(
                testParty(inventoryItems = mapOf(1 to VmGem, 2 to VmCoin)),
                hour = 12,
                currentTown = null,
            )
        val request = EngineRequest(NativeAllowlist.inputKeyUpper.path)
        val authored = runAuthored(model, scene, runtime, TraceEnginePort(ints = mapOf(request to 5)))
        val (compiled, program) = compiledScene(model, scene.id)
        val host = RecordingCompiledVmHost(ints = mapOf(request to 5))

        val result = CompiledVm(compiled.content, host).run(program, CompiledVmStart(runtime))

        val completed = assertIs<CompiledVmResult.Completed>(result)
        assertEquals(authored.first.realm, completed.runtime.realm)
        assertEquals(authored.first.activeParty, completed.runtime.activeParty)
        assertEquals(authored.second, host.events)
        assertEquals(50, completed.runtime.activeParty.resources.gold)
    }

    @Test
    fun compiledVmBlocksAndResumesWaitInputWithoutReplayingPriorEffects() {
        val model =
            rylween {
                content {
                    scene(VmWaitScene) {
                        screen("before")
                        waitInput("interactive")
                        party.gold += 4
                        screen("after")
                    }
                }
            }
        val (compiled, program) = compiledScene(model, VmWaitScene)
        val runtime = RuntimeSessionState.singleParty(testParty(), 12, null)
        val host = RecordingCompiledVmHost()
        val vm = CompiledVm(compiled.content, host)

        val result = vm.run(program, CompiledVmStart(runtime))

        val blocked = assertIs<CompiledVmResult.Blocked>(result)
        val pending = assertIs<CompiledVmPendingInput.WaitInput>(blocked.pending)
        assertEquals("interactive", pending.reason)
        assertEquals(1, pending.instructionIndex)
        assertEquals(listOf(1), pending.operandPath)
        assertEquals(listOf(CompiledVmFrameForTest(emptyList(), 2, false)), blocked.continuation.frames.map(::CompiledVmFrameForTest))
        assertEquals(
            listOf(
                EngineTraceEvent.Screen(inlineText("before")),
                EngineTraceEvent.WaitInput("interactive"),
            ),
            host.events,
        )

        val resumed = vm.resume(program, blocked.continuation, CompiledVmInput.Continue)

        val completed = assertIs<CompiledVmResult.Completed>(resumed)
        assertEquals(6, completed.runtime.activeParty.resources.gold)
        assertEquals(
            listOf(
                EngineTraceEvent.Screen(inlineText("before")),
                EngineTraceEvent.WaitInput("interactive"),
                EngineTraceEvent.Screen(inlineText("after")),
            ),
            host.events,
        )
    }

    @Test
    fun compiledVmBlocksAndResumesMenuOptionWithoutReplayingPriorEffects() {
        val model =
            rylween {
                content {
                    scene(VmMenuScene) {
                        screen("before")
                        menu("pick") {
                            option('A') {
                                party.gold += 10
                            }
                            option('B') {
                                party.gold += 20
                            }
                        }
                        screen("after")
                    }
                }
            }
        val (compiled, program) = compiledScene(model, VmMenuScene)
        val runtime = RuntimeSessionState.singleParty(testParty(), 12, null)
        val host = RecordingCompiledVmHost()
        val vm = CompiledVm(compiled.content, host)

        val result = vm.run(program, CompiledVmStart(runtime))

        val blocked = assertIs<CompiledVmResult.Blocked>(result)
        val pending = assertIs<CompiledVmPendingInput.Menu>(blocked.pending)
        assertEquals(listOf('A', 'B'), pending.keys)
        assertEquals(1, pending.instructionIndex)
        assertEquals(listOf(1), pending.operandPath)
        assertEquals(listOf(CompiledVmFrameForTest(emptyList(), 2, false)), blocked.continuation.frames.map(::CompiledVmFrameForTest))
        assertEquals(
            listOf(
                EngineTraceEvent.Screen(inlineText("before")),
                EngineTraceEvent.Menu(pending.prompt, listOf('A', 'B')),
            ),
            host.events,
        )

        val resumed = vm.resume(program, blocked.continuation, CompiledVmInput.MenuKey('b'))

        val completed = assertIs<CompiledVmResult.Completed>(resumed)
        assertEquals(22, completed.runtime.activeParty.resources.gold)
        assertEquals(
            listOf(
                EngineTraceEvent.Screen(inlineText("before")),
                EngineTraceEvent.Menu(pending.prompt, listOf('A', 'B')),
                EngineTraceEvent.Screen(inlineText("after")),
            ),
            host.events,
        )
    }

    @Test
    fun compiledVmMenuInvalidInputStaysBlockedWithoutAdvancing() {
        val model =
            rylween {
                content {
                    scene(VmMenuScene) {
                        menu("pick") {
                            option('A') {
                                party.gold += 10
                            }
                        }
                    }
                }
            }
        val (compiled, program) = compiledScene(model, VmMenuScene)
        val host = RecordingCompiledVmHost()
        val vm = CompiledVm(compiled.content, host)
        val blocked =
            assertIs<CompiledVmResult.Blocked>(
                vm.run(program, CompiledVmStart(RuntimeSessionState.singleParty(testParty(), 12, null))),
            )

        val rejected = vm.resume(program, blocked.continuation, CompiledVmInput.MenuKey('Z'))

        val stillBlocked = assertIs<CompiledVmResult.Blocked>(rejected)
        assertEquals(blocked.pending, stillBlocked.pending)
        assertEquals(blocked.continuation, stillBlocked.continuation)
        assertEquals(1, host.events.size)
    }

    @Test
    fun compiledVmMenuInvalidInputUsesDefaultBranchWhenPresent() {
        val model =
            rylween {
                content {
                    scene(VmMenuDefaultScene) {
                        menu("pick") {
                            option('A') {
                                party.gold += 10
                            }
                            default {
                                party.gold += 5
                            }
                        }
                    }
                }
            }
        val (compiled, program) = compiledScene(model, VmMenuDefaultScene)
        val vm = CompiledVm(compiled.content)
        val blocked =
            assertIs<CompiledVmResult.Blocked>(
                vm.run(program, CompiledVmStart(RuntimeSessionState.singleParty(testParty(), 12, null))),
            )

        val resumed = vm.resume(program, blocked.continuation, CompiledVmInput.MenuKey('Z'))

        val completed = assertIs<CompiledVmResult.Completed>(resumed)
        assertEquals(7, completed.runtime.activeParty.resources.gold)
    }

    @Test
    fun compiledVmBlocksAndResumesAskKeyWithoutReplayingPriorEffects() {
        val model =
            rylween {
                content {
                    scene(VmAskKeyScene) {
                        screen("before")
                        val key = ask.key(inlineText("key"), inputKey('A'), inputKey('B', "bee"))
                        party.gold set key
                        screen("after")
                    }
                }
            }
        val (compiled, program) = compiledScene(model, VmAskKeyScene)
        val runtime = RuntimeSessionState.singleParty(testParty(), 12, null)
        val host = RecordingCompiledVmHost()
        val vm = CompiledVm(compiled.content, host)

        val result = vm.run(program, CompiledVmStart(runtime))

        val blocked = assertIs<CompiledVmResult.Blocked>(result)
        val pending = assertIs<CompiledVmPendingInput.AskKey>(blocked.pending)
        assertEquals(1, pending.instructionIndex)
        assertEquals(listOf(1), pending.operandPath)
        assertEquals(listOf("A", "B"), pending.bindings.map { binding -> binding.primary })
        assertEquals(listOf(emptyList(), listOf("bee")), pending.bindings.map { binding -> binding.aliases })
        assertEquals(listOf(CompiledVmFrameForTest(emptyList(), 2, false)), blocked.continuation.frames.map(::CompiledVmFrameForTest))
        assertEquals(
            listOf(
                EngineTraceEvent.Screen(inlineText("before")),
                EngineTraceEvent.AskKey(pending.prompt, pending.bindings),
            ),
            host.events,
        )

        val resumed = vm.resume(program, blocked.continuation, CompiledVmInput.AskKey("bee"))

        val completed = assertIs<CompiledVmResult.Completed>(resumed)
        assertEquals('B'.code, completed.locals[pending.localName])
        assertEquals('B'.code, completed.runtime.activeParty.resources.gold)
        assertEquals(
            listOf(
                EngineTraceEvent.Screen(inlineText("before")),
                EngineTraceEvent.AskKey(pending.prompt, pending.bindings),
                EngineTraceEvent.Screen(inlineText("after")),
            ),
            host.events,
        )
    }

    @Test
    fun compiledVmAskKeyInvalidInputStaysBlockedWithoutAdvancing() {
        val model =
            rylween {
                content {
                    scene(VmAskKeyScene) {
                        val key = ask.key(inlineText("key"), 'A')
                        party.gold set key
                    }
                }
            }
        val (compiled, program) = compiledScene(model, VmAskKeyScene)
        val host = RecordingCompiledVmHost()
        val vm = CompiledVm(compiled.content, host)
        val blocked =
            assertIs<CompiledVmResult.Blocked>(
                vm.run(program, CompiledVmStart(RuntimeSessionState.singleParty(testParty(), 12, null))),
            )

        val rejected = vm.resume(program, blocked.continuation, CompiledVmInput.AskKey("Z"))

        val stillBlocked = assertIs<CompiledVmResult.Blocked>(rejected)
        assertEquals(blocked.pending, stillBlocked.pending)
        assertEquals(blocked.continuation, stillBlocked.continuation)
        assertEquals(1, host.events.size)
    }

    @Test
    fun compiledVmStillRejectsUnimplementedInteractiveOpcodeFamilies() {
        val model =
            rylween {
                content {
                    scene(VmAskInventorySlotScene) {
                        ask.inventorySlot(inlineText("slot"))
                    }
                }
            }
        val (compiled, program) = compiledScene(model, VmAskInventorySlotScene)

        val result =
            CompiledVm(compiled.content)
                .run(program, CompiledVmStart(RuntimeSessionState.singleParty(testParty(), 12, null)))

        val failed = assertIs<CompiledVmResult.Failed>(result)
        val diagnostic = assertIs<VmDiagnostic.UnsupportedHostService>(failed.diagnostic)
        assertEquals("AskInventorySlot", diagnostic.service)
    }

    @Test
    fun compiledVmBlocksAndResumesAskMemberWithoutReplayingPriorEffects() {
        val model =
            rylween {
                content {
                    scene(VmAskMemberScene) {
                        screen("before")
                        val member =
                            ask.member(
                                inlineText("who"),
                                filter = MemberChoiceFilter.Any,
                                accepting = MemberChoiceFilter.DeadOnly,
                            )
                        party.gold set member.level
                        screen("after")
                    }
                }
            }
        val (compiled, program) = compiledScene(model, VmAskMemberScene)
        val runtime = RuntimeSessionState.singleParty(testParty(), 12, null)
        val host = RecordingCompiledVmHost()
        val vm = CompiledVm(compiled.content, host)

        val result = vm.run(program, CompiledVmStart(runtime))

        val blocked = assertIs<CompiledVmResult.Blocked>(result)
        val pending = assertIs<CompiledVmPendingInput.AskMember>(blocked.pending)
        assertEquals(1, pending.instructionIndex)
        assertEquals(listOf(1), pending.operandPath)
        assertEquals(MemberChoiceFilter.Any, pending.filter)
        assertEquals(MemberChoiceFilter.DeadOnly, pending.accepting)
        assertEquals(listOf(2), pending.choices)
        assertEquals(listOf(CompiledVmFrameForTest(emptyList(), 2, false)), blocked.continuation.frames.map(::CompiledVmFrameForTest))
        assertEquals(
            listOf(
                EngineTraceEvent.Screen(inlineText("before")),
                EngineTraceEvent.AskMember(pending.prompt, MemberChoiceFilter.Any),
            ),
            host.events,
        )

        val resumed = vm.resume(program, blocked.continuation, CompiledVmInput.AskMember(2))

        val completed = assertIs<CompiledVmResult.Completed>(resumed)
        assertEquals(2, completed.locals[pending.localName])
        assertEquals(9, completed.runtime.activeParty.resources.gold)
        assertEquals(
            listOf(
                EngineTraceEvent.Screen(inlineText("before")),
                EngineTraceEvent.AskMember(pending.prompt, MemberChoiceFilter.Any),
                EngineTraceEvent.Screen(inlineText("after")),
            ),
            host.events,
        )
    }

    @Test
    fun compiledVmAskMemberInvalidInputStaysBlockedWithoutAdvancing() {
        val model =
            rylween {
                content {
                    scene(VmAskMemberScene) {
                        val member = ask.member(inlineText("who"), accepting = MemberChoiceFilter.DeadOnly)
                        party.gold set member.level
                    }
                }
            }
        val (compiled, program) = compiledScene(model, VmAskMemberScene)
        val host = RecordingCompiledVmHost()
        val vm = CompiledVm(compiled.content, host)
        val blocked =
            assertIs<CompiledVmResult.Blocked>(
                vm.run(program, CompiledVmStart(RuntimeSessionState.singleParty(testParty(), 12, null))),
            )

        val rejected = vm.resume(program, blocked.continuation, CompiledVmInput.AskMember(1))

        val stillBlocked = assertIs<CompiledVmResult.Blocked>(rejected)
        assertEquals(blocked.pending, stillBlocked.pending)
        assertEquals(blocked.continuation, stillBlocked.continuation)
        assertEquals(1, host.events.size)
    }

    @Test
    fun compiledVmRejectsUnknownNativeExpressionPathWithDiagnostic() {
        val model = expressionSceneModel()
        val (compiled, program) = compiledScene(model, VmExpressionScene)

        val result =
            CompiledVm(compiled.content, nativeServices = NativeServiceCatalog(emptyMap()))
                .run(program, CompiledVmStart(RuntimeSessionState.singleParty(testParty(), 12, null)))

        val failed = assertIs<CompiledVmResult.Failed>(result)
        val diagnostic = assertIs<VmDiagnostic.MissingNativeOp>(failed.diagnostic)
        assertTrue(diagnostic.root.sameRoot(ScriptRootRef.scene(VmExpressionScene)))
        assertEquals(1, diagnostic.instructionIndex)
        assertEquals(listOf(1, 1), diagnostic.operandPath)
        assertEquals(NativeAllowlist.inputKeyUpper.path, diagnostic.path)
    }

    @Test
    fun compiledVmRejectsNativeStringWrongShapeWithDiagnostic() {
        val model = pureSceneModel()
        val scene = model.content.scenes.single()
        val (compiled, program) = compiledScene(model, scene.id)
        val badCatalog =
            NativeServiceCatalog(
                NativeServiceCatalog.default.entries +
                    (
                        NativeAllowlist.partyMemberName.path to
                            NativeServiceDescriptor(
                                path = NativeAllowlist.partyMemberName.path,
                                kind = NativeServiceKind.StringValue,
                                argSlots = listOf(NativeServiceArgSlot.StringValue),
                                reason = NativeAllowlist.partyMemberName.reason,
                            )
                    ),
            )

        val result =
            CompiledVm(compiled.content, nativeServices = badCatalog)
                .run(program, CompiledVmStart(RuntimeSessionState.singleParty(testParty(), 12, null)))

        val failed = assertIs<CompiledVmResult.Failed>(result)
        val diagnostic = assertIs<VmDiagnostic.BadOperand>(failed.diagnostic)
        assertTrue(diagnostic.root.sameRoot(ScriptRootRef.scene(scene.id)))
        assertEquals(7, diagnostic.instructionIndex)
        assertEquals(listOf(7, 1), diagnostic.operandPath)
        assertEquals(CompiledOpcode.BindText, diagnostic.opcode)
        assertEquals("NativeServiceWrongShape", diagnostic.reason)
        assertEquals("[StringValue]", diagnostic.expected)
        assertEquals("[IntExprValue]", diagnostic.actual)
    }

    @Test
    fun compiledVmUsesBattleExpressionHostHooks() {
        val model =
            rylween {
                content {
                    scene(VmBattleScene) {
                        val foe = battle.firstAliveMonster()
                        whenever(!foe.dead) {
                            battle.monsterWeaponSkill set (battle.monsterWeaponSkill + foe.hp)
                            foe.hp -= 3
                            party.gold set foe.hp
                            battle.monsterConfused set true
                        } otherwise {
                            party.gold set 1
                        }
                        stop("done")
                    }
                }
            }
        val (compiled, program) = compiledScene(model, VmBattleScene)
        val host = BattleExpressionHost()

        val result =
            CompiledVm(compiled.content, host)
                .run(program, CompiledVmStart(RuntimeSessionState.singleParty(testParty(), 12, null)))

        val completed = assertIs<CompiledVmResult.Completed>(result)
        assertEquals(14, completed.runtime.activeParty.resources.gold)
        assertEquals(14, host.monsterHp)
        assertEquals(59, host.battleWrites["monster_weapon_skill"])
        assertEquals(1, host.battleWrites["monster_confused"])
    }

    @Test
    fun compiledVmReportsNestedExpressionOperandPath() {
        val model =
            rylween {
                content {
                    scene(VmNestedExpressionScene) {
                        val monsterWeaponSkill = battle.monsterWeaponSkill
                        whenever(party.gold eq 2) {
                            screen {
                                line(monsterWeaponSkill)
                            }
                        }
                    }
                }
            }
        val (compiled, program) = compiledScene(model, VmNestedExpressionScene)

        val result =
            CompiledVm(compiled.content)
                .run(program, CompiledVmStart(RuntimeSessionState.singleParty(testParty(), 12, null)))

        val failed = assertIs<CompiledVmResult.Failed>(result)
        val diagnostic = assertIs<VmDiagnostic.UnsupportedHostService>(failed.diagnostic)
        assertTrue(diagnostic.root.sameRoot(ScriptRootRef.scene(VmNestedExpressionScene)))
        assertEquals(0, diagnostic.instructionIndex)
        assertEquals(listOf(0, 1, 0, 0, 0, 0), diagnostic.operandPath)
        assertEquals("monster_weapon_skill", diagnostic.service)
    }

    private fun pureSceneModel(): RylweenModel {
        val content =
            rylween {
                content {
                    flags {
                        story(VmStoryFlag) {
                            states(VmStoryFlag.ready)
                        }
                    }
                    gameProgress {
                        milestone(VmMilestone) {
                        }
                    }
                    scene(VmPureScene) {
                        clearText()
                        screen("before")
                        emit(VmEmitText)
                        emitIndexed(TextPattern("score.%d"), party.gold)
                        pause(1)
                        val bonus = let("bonus", 4)
                        let("time_cap", 75)
                        bind("leader", partyName(party.leader))
                        party.gold set 10
                        party.gold += bonus
                        party.gold -= 3
                        party.timeLeft.addClamped(90, v("time_cap"))
                        mark(VmStoryFlag.ready)
                        journal("journal line")
                        whenever(party.gold eq 11) {
                            party.gold += 2
                        } otherwise {
                            party.gold += 20
                        }
                        let("branch", 2)
                        choose(v("branch")) {
                            case(1) {
                                party.gold += 100
                            }
                            case(2) {
                                party.gold += 7
                            }
                            default {
                                party.gold += 200
                            }
                        }
                        loop {
                            party.torches += 1
                            breakLoop("loop done")
                        }
                        forEachMember {
                            party.gold += it.level
                        }
                        achieve(VmMilestone)
                        stop("done")
                        screen(inlineText("after"))
                    }
                }
            }.content
        return RylweenModel(content, listOf(textHandleState()))
    }

    private fun expressionSceneModel(): RylweenModel =
        rylween {
            content {
                items {
                    treasure(VmGem, "Gem", cost = 25)
                    treasure(VmCoin, "Coin", cost = 7)
                    pool(VmTreasurePool, VmGem, VmCoin)
                }
                scene(VmExpressionScene) {
                    val slot = let("slot", 1)
                    val key = let("key", native.input.keyUpper())
                    val poolCount = let("pool_count", party.inventory.count(VmTreasurePool))
                    val slotCost = let("slot_cost", party.inventory.itemCost(InventorySlotRef(slot)))
                    party.gold set (key + (poolCount * 10) + slotCost)
                    screen {
                        line("expr=", key, "/", poolCount, "/", slotCost)
                    }
                    stop("done")
                }
            }
        }

    private fun runAuthored(
        model: RylweenModel,
        scene: SceneModel,
        runtime: RuntimeSessionState,
        engine: TraceEnginePort = TraceEnginePort(),
    ): Pair<RuntimeSessionState, List<EngineTraceEvent>> {
        val result =
            StatefulScriptServiceRunner(
                terminal = NoopTerminalPort,
                engine = engine,
                rootOps = scene.ops,
                continuation = ScriptContinuation.start(runtime, ScriptRootRef.scene(scene.id)),
                speakContent = SpeakRuntimeContent(),
                battleContent = model.content.runtimeContent(),
                scriptRoot = ScriptRootRef.scene(scene.id),
            ).run()
        assertEquals(ScriptBridgeStatus.Finished, result.status)
        assertNull(result.continuation)
        return result.runtime to engine.events
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

    private fun textHandleState(): StateModel {
        val party = testParty(journal = listOf(JournalEntryState.Text(VmEmitText)))
        return StateModel(
            id = stateId("vm.handle_state"),
            kind = StateKind.AuthoredFixture,
            realm =
                RealmState(
                    day = 0,
                    hour = 12,
                    minute = 0,
                    rngState = null,
                    activeParty = party.id,
                    currentMap = worldMap("vm.map"),
                    currentMode = RuntimeMode.Town,
                    currentTown = null,
                    banished = false,
                    lastPlayedDay = 0,
                    partyMemberMax = 4,
                ),
            locations = emptyList(),
            npcs = emptyList(),
            parties = listOf(party),
        )
    }

    private fun testParty(
        journal: List<JournalEntryState> = emptyList(),
        inventoryItems: Map<Int, ItemId> = emptyMap(),
    ): PartyState =
        PartyState(
            id = partyId("vm.party"),
            player = "VM",
            name = "VM",
            position = Wilderness.xy(0, 0),
            facing = Facing.South,
            resources = PartyResources(gold = 2, torches = 0, food = 0, timeLeft = 10, restedHours = 0),
            counters = PartyCounters(memberCount = 1, kills = 0, waitingMail = false, attacked = false, attackedToday = emptyList()),
            flags = emptyList(),
            milestones = emptyList(),
            inventory = (0..InventorySaveSlotMax).map { slot -> InventorySlotState(slot, inventoryItems[slot]) },
            members =
                listOf(
                    testMember("Ada", level = 3, dead = false),
                    testMember("Bob", level = 9, dead = true),
                ),
            visitedNpcs = emptyList(),
            journal = journal,
        )

    private class BattleExpressionHost : RecordingCompiledVmHost() {
        var monsterHp: Int = 17
        val battleWrites: MutableMap<String, Int> = mutableMapOf()

        override fun readMonsterInt(
            monster: Int,
            field: String,
        ): Int? =
            when {
                monster == 0 && field == "hit_points" -> monsterHp
                else -> null
            }

        override fun writeMonsterInt(
            monster: Int,
            field: String,
            value: Int,
        ): Boolean =
            if (monster == 0 && field == "hit_points") {
                monsterHp = value
                true
            } else {
                false
            }

        override fun readBattleInt(name: String): Int? =
            when (name) {
                "monster_weapon_skill" -> battleWrites[name] ?: 42
                else -> null
            }

        override fun writeBattleInt(
            name: String,
            value: Int,
        ): Boolean =
            when (name) {
                "monster_weapon_skill",
                "monster_confused",
                -> {
                    battleWrites[name] = value
                    true
                }
                else -> false
            }

        override fun readBattleQuery(name: String): Int? =
            when (name) {
                "first_alive_monster" -> 0
                else -> null
            }

        override fun readMonsterFlag(
            monster: Int,
            name: String,
        ): Boolean? =
            when {
                monster == 0 && name == "dead" -> monsterHp <= 0
                else -> null
            }
    }

    private fun testMember(
        name: String,
        level: Int,
        dead: Boolean,
    ): PartyMemberState =
        PartyMemberState(
            name = name,
            gender = Gender.Female,
            race = Race.Human,
            pcClass = PcClass.Fighter,
            level = level,
            health = 10,
            hitPoints = 10 of 10,
            weaponSkill = 10,
            wisdom = 10,
            mana = 0 of 0,
            charisma = 10,
            equipment = EquipmentState(item("none"), item("none")),
            experience = 0,
            dead = dead,
        )

    private fun PartyState.flagTokens(): List<Pair<String, String>> = flags.map { state -> state.flag.slug to state.native }

    private data class CompiledVmFrameForTest(
        val blockPath: List<Int>,
        val nextIndex: Int,
        val insideLoop: Boolean,
    ) {
        constructor(frame: rylween.runtime.vm.CompiledVmFrame) : this(frame.blockPath, frame.nextIndex, frame.insideLoop)
    }

    private object NoopTerminalPort : TerminalPort {
        override fun write(lines: List<String>) = Unit

        override fun readLine(): String? = null
    }

    private companion object {
        val VmEmitText = text("vm.emit")
        val VmMilestone = milestone("vm.won")
        val VmPureScene = sceneId("vm.pure")
        val VmWaitScene = sceneId("vm.wait")
        val VmMenuScene = sceneId("vm.menu")
        val VmMenuDefaultScene = sceneId("vm.menu_default")
        val VmAskKeyScene = sceneId("vm.ask_key")
        val VmAskMemberScene = sceneId("vm.ask_member")
        val VmAskInventorySlotScene = sceneId("vm.ask_inventory_slot")
        val VmExpressionScene = sceneId("vm.expressions")
        val VmBattleScene = sceneId("vm.battle_expr")
        val VmNestedExpressionScene = sceneId("vm.nested_expr")
        val VmGem = item("vm.gem")
        val VmCoin = item("vm.coin")
        val VmTreasurePool = itemPool("vm.treasure")
    }
}

private object VmStoryFlag : FlagRef("vm.story") {
    val ready = state("ready")
}

@Suppress("unused")
private val RegisteredVmStoryFlag = VmStoryFlag.declareFlag()
