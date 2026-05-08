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
import rylween.dsl.InventorySlotChoiceFilter
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
import rylween.dsl.dungeon
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
import rylween.runtime.inventoryItem
import rylween.runtime.town.SpeakRuntimeContent
import rylween.runtime.vm.CompiledVm
import rylween.runtime.vm.CompiledVmChildCompletion
import rylween.runtime.vm.CompiledVmContinuation
import rylween.runtime.vm.CompiledVmFrameKind
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
import rylween.runtime.vm.toContinuation
import rylween.runtime.vm.toSnapshot
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
    fun compiledVmTreatsAbsentStoryFlagAsNoneState() {
        val model =
            rylween {
                content {
                    flags {
                        story(VmStoryFlag) {
                            states(VmStoryFlag.none, VmStoryFlag.ready)
                        }
                    }
                    scene(VmNoneScene) {
                        whenever(VmStoryFlag.none) {
                            screen("none")
                        } otherwise {
                            screen("set")
                        }
                        mark(VmStoryFlag.ready)
                        whenever(VmStoryFlag.none) {
                            screen("still none")
                        } otherwise {
                            screen("ready")
                        }
                    }
                }
            }
        val (compiled, program) = compiledScene(model, VmNoneScene)
        val host = RecordingCompiledVmHost()

        val result =
            CompiledVm(
                compiled.content,
                host,
            ).run(program, CompiledVmStart(RuntimeSessionState.singleParty(testParty(), 12, null)))

        val completed = assertIs<CompiledVmResult.Completed>(result)
        assertTrue(completed.runtime.activeParty.hasFlagState(VmStoryFlag.ready))
        assertEquals(
            listOf(inlineText("none"), inlineText("ready")),
            host.events.filterIsInstance<EngineTraceEvent.Screen>().map { event -> event.text },
        )
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

        val resumed = resumeAfterSnapshot(vm, program, blocked.continuation, CompiledVmInput.Continue)

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

        val resumed = resumeAfterSnapshot(vm, program, blocked.continuation, CompiledVmInput.MenuKey('b'))

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

        val rejected = resumeAfterSnapshot(vm, program, blocked.continuation, CompiledVmInput.MenuKey('Z'))

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

        val resumed = resumeAfterSnapshot(vm, program, blocked.continuation, CompiledVmInput.MenuKey('Z'))

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

        val resumed = resumeAfterSnapshot(vm, program, blocked.continuation, CompiledVmInput.AskKey("bee"))

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

        val rejected = resumeAfterSnapshot(vm, program, blocked.continuation, CompiledVmInput.AskKey("Z"))

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
                    scene(VmUnsupportedScene) {
                        forEachMonster {
                            party.gold += 1
                        }
                    }
                }
            }
        val (compiled, program) = compiledScene(model, VmUnsupportedScene)

        val result =
            CompiledVm(compiled.content)
                .run(program, CompiledVmStart(RuntimeSessionState.singleParty(testParty(), 12, null)))

        val failed = assertIs<CompiledVmResult.Failed>(result)
        val diagnostic = assertIs<VmDiagnostic.UnsupportedHostService>(failed.diagnostic)
        assertEquals("ForEachMonster", diagnostic.service)
    }

    @Test
    fun compiledVmResumesForEachMonsterBodyWithoutSkippingRemainingMonsters() {
        val model =
            rylween {
                content {
                    scene(VmForEachMonsterScene) {
                        forEachMonster(includeDead = true) { monster ->
                            screen("monster")
                            waitInput()
                            party.gold += monster.hp
                        }
                    }
                }
            }
        val (compiled, program) = compiledScene(model, VmForEachMonsterScene)
        val host = MonsterIterationHost(mapOf(0 to 5, 1 to 8))
        val vm = CompiledVm(compiled.content, host)

        val firstBlocked =
            assertIs<CompiledVmResult.Blocked>(
                vm.run(program, CompiledVmStart(RuntimeSessionState.singleParty(testParty(), 12, null))),
            )

        assertIs<CompiledVmPendingInput.WaitInput>(firstBlocked.pending)
        assertEquals(
            CompiledVmFrameKind.ForEachMonsterBody,
            firstBlocked.continuation.frames
                .first()
                .kind,
        )
        assertEquals(
            listOf(
                EngineTraceEvent.Screen(inlineText("monster")),
                EngineTraceEvent.WaitInput(null),
            ),
            host.events,
        )

        val secondBlocked =
            assertIs<CompiledVmResult.Blocked>(
                resumeAfterSnapshot(vm, program, firstBlocked.continuation, CompiledVmInput.Continue),
            )

        assertIs<CompiledVmPendingInput.WaitInput>(secondBlocked.pending)
        assertEquals(
            CompiledVmFrameKind.ForEachMonsterBody,
            secondBlocked.continuation.frames
                .first()
                .kind,
        )
        assertEquals(
            listOf(
                EngineTraceEvent.Screen(inlineText("monster")),
                EngineTraceEvent.WaitInput(null),
                EngineTraceEvent.Screen(inlineText("monster")),
                EngineTraceEvent.WaitInput(null),
            ),
            host.events,
        )

        val completed =
            assertIs<CompiledVmResult.Completed>(
                resumeAfterSnapshot(vm, program, secondBlocked.continuation, CompiledVmInput.Continue),
            )

        assertEquals(15, completed.runtime.activeParty.resources.gold)
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

        val resumed = resumeAfterSnapshot(vm, program, blocked.continuation, CompiledVmInput.AskMember(2))

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

        val rejected = resumeAfterSnapshot(vm, program, blocked.continuation, CompiledVmInput.AskMember(1))

        val stillBlocked = assertIs<CompiledVmResult.Blocked>(rejected)
        assertEquals(blocked.pending, stillBlocked.pending)
        assertEquals(blocked.continuation, stillBlocked.continuation)
        assertEquals(1, host.events.size)
    }

    @Test
    fun compiledVmBlocksAndResumesAskMonsterWithoutReplayingPriorEffects() {
        val model =
            rylween {
                content {
                    scene(VmAskMonsterScene) {
                        screen("before")
                        val monster = ask.monsterAlive(inlineText("monster"))
                        party.gold set monster.hp
                        screen("after")
                    }
                }
            }
        val (compiled, program) = compiledScene(model, VmAskMonsterScene)
        val runtime = RuntimeSessionState.singleParty(testParty(), 12, null)
        val host = MonsterPromptHost(targetKeys = listOf('B'), monsterHp = 17)
        val vm = CompiledVm(compiled.content, host)

        val result = vm.run(program, CompiledVmStart(runtime))

        val blocked = assertIs<CompiledVmResult.Blocked>(result)
        val pending = assertIs<CompiledVmPendingInput.AskMonster>(blocked.pending)
        assertEquals(1, pending.instructionIndex)
        assertEquals(listOf(1), pending.operandPath)
        assertEquals(listOf('B'), pending.targetKeys)
        assertEquals(listOf(1), pending.targetIndices)
        assertEquals(listOf(CompiledVmFrameForTest(emptyList(), 2, false)), blocked.continuation.frames.map(::CompiledVmFrameForTest))
        assertEquals(listOf(EngineTraceEvent.Screen(inlineText("before"))), host.events)

        val resumed = resumeAfterSnapshot(vm, program, blocked.continuation, CompiledVmInput.AskMonster(1))

        val completed = assertIs<CompiledVmResult.Completed>(resumed)
        assertEquals(1, completed.locals[pending.localName])
        assertEquals(17, completed.runtime.activeParty.resources.gold)
        assertEquals(
            listOf(
                EngineTraceEvent.Screen(inlineText("before")),
                EngineTraceEvent.Screen(inlineText("after")),
            ),
            host.events,
        )
    }

    @Test
    fun compiledVmAskMonsterInvalidInputStaysBlockedWithoutAdvancing() {
        val model =
            rylween {
                content {
                    scene(VmAskMonsterScene) {
                        val monster = ask.monsterAlive(inlineText("monster"))
                        party.gold set monster.hp
                    }
                }
            }
        val (compiled, program) = compiledScene(model, VmAskMonsterScene)
        val host = MonsterPromptHost(targetKeys = listOf('B'))
        val vm = CompiledVm(compiled.content, host)
        val blocked =
            assertIs<CompiledVmResult.Blocked>(
                vm.run(program, CompiledVmStart(RuntimeSessionState.singleParty(testParty(), 12, null))),
            )

        val rejected = resumeAfterSnapshot(vm, program, blocked.continuation, CompiledVmInput.AskMonster(0))

        val stillBlocked = assertIs<CompiledVmResult.Blocked>(rejected)
        assertEquals(blocked.pending, stillBlocked.pending)
        assertEquals(blocked.continuation, stillBlocked.continuation)
        assertEquals(emptyList(), host.events)
    }

    @Test
    fun compiledVmBlocksAndResumesAskInventorySlotWithoutReplayingPriorEffects() {
        val model =
            rylween {
                content {
                    scene(VmAskInventorySlotScene) {
                        screen("before")
                        val slot =
                            ask.inventorySlot(
                                inlineText("slot"),
                                firstKey = 'C',
                                slots = 2..3,
                                filter = InventorySlotChoiceFilter.NonEmpty,
                            )
                        party.gold set slot.index
                        screen("after")
                    }
                }
            }
        val (compiled, program) = compiledScene(model, VmAskInventorySlotScene)
        val runtime = RuntimeSessionState.singleParty(testParty(inventoryItems = mapOf(3 to VmGem)), 12, null)
        val host = RecordingCompiledVmHost()
        val vm = CompiledVm(compiled.content, host)

        val result = vm.run(program, CompiledVmStart(runtime))

        val blocked = assertIs<CompiledVmResult.Blocked>(result)
        val pending = assertIs<CompiledVmPendingInput.AskInventorySlot>(blocked.pending)
        assertEquals(1, pending.instructionIndex)
        assertEquals(listOf(1), pending.operandPath)
        assertEquals('C', pending.firstKey)
        assertEquals(2..3, pending.slots)
        assertEquals(InventorySlotChoiceFilter.NonEmpty, pending.filter)
        assertEquals(listOf(CompiledVmInventorySlotChoiceForTest('D', 3)), pending.choices.map(::CompiledVmInventorySlotChoiceForTest))
        assertEquals(listOf(CompiledVmFrameForTest(emptyList(), 2, false)), blocked.continuation.frames.map(::CompiledVmFrameForTest))
        assertEquals(
            listOf(
                EngineTraceEvent.Screen(inlineText("before")),
                EngineTraceEvent.AskInventorySlot(pending.prompt, 'C', 2..3, InventorySlotChoiceFilter.NonEmpty),
            ),
            host.events,
        )

        val resumed = resumeAfterSnapshot(vm, program, blocked.continuation, CompiledVmInput.AskInventorySlot("d"))

        val completed = assertIs<CompiledVmResult.Completed>(resumed)
        assertEquals(3, completed.locals[pending.localName])
        assertEquals(3, completed.runtime.activeParty.resources.gold)
        assertEquals(
            listOf(
                EngineTraceEvent.Screen(inlineText("before")),
                EngineTraceEvent.AskInventorySlot(pending.prompt, 'C', 2..3, InventorySlotChoiceFilter.NonEmpty),
                EngineTraceEvent.Screen(inlineText("after")),
            ),
            host.events,
        )
    }

    @Test
    fun compiledVmAskInventorySlotInvalidInputStaysBlockedWithoutAdvancing() {
        val model =
            rylween {
                content {
                    scene(VmAskInventorySlotScene) {
                        val slot =
                            ask.inventorySlot(
                                inlineText("slot"),
                                firstKey = 'C',
                                slots = 2..3,
                                filter = InventorySlotChoiceFilter.NonEmpty,
                            )
                        party.gold set slot.index
                    }
                }
            }
        val (compiled, program) = compiledScene(model, VmAskInventorySlotScene)
        val host = RecordingCompiledVmHost()
        val vm = CompiledVm(compiled.content, host)
        val blocked =
            assertIs<CompiledVmResult.Blocked>(
                vm.run(
                    program,
                    CompiledVmStart(RuntimeSessionState.singleParty(testParty(inventoryItems = mapOf(3 to VmGem)), 12, null)),
                ),
            )

        val rejected = resumeAfterSnapshot(vm, program, blocked.continuation, CompiledVmInput.AskInventorySlot("C"))

        val stillBlocked = assertIs<CompiledVmResult.Blocked>(rejected)
        assertEquals(blocked.pending, stillBlocked.pending)
        assertEquals(blocked.continuation, stillBlocked.continuation)
        assertEquals(1, host.events.size)
    }

    @Test
    fun compiledVmBlocksAndResumesAskTextMatchBranchWithoutReplayingPriorEffects() {
        val model =
            rylween {
                content {
                    scene(VmAskTextScene) {
                        screen("before")
                        choice.textAnswer(
                            prompt = inlineText("answer"),
                            accepting = listOf("YES"),
                            maxLength = 3,
                            match = {
                                party.gold += 10
                            },
                            miss = {
                                party.gold += 20
                            },
                        )
                        screen("after")
                    }
                }
            }
        val (compiled, program) = compiledScene(model, VmAskTextScene)
        val runtime = RuntimeSessionState.singleParty(testParty(), 12, null)
        val host = RecordingCompiledVmHost()
        val vm = CompiledVm(compiled.content, host)

        val result = vm.run(program, CompiledVmStart(runtime))

        val blocked = assertIs<CompiledVmResult.Blocked>(result)
        val pending = assertIs<CompiledVmPendingInput.AskText>(blocked.pending)
        assertEquals(1, pending.instructionIndex)
        assertEquals(listOf(1), pending.operandPath)
        assertEquals(listOf("YES"), pending.accepted)
        assertEquals(false, pending.caseSensitive)
        assertEquals(true, pending.trim)
        assertEquals(3, pending.maxLength)
        assertEquals(listOf(CompiledVmFrameForTest(emptyList(), 2, false)), blocked.continuation.frames.map(::CompiledVmFrameForTest))
        assertEquals(
            listOf(
                EngineTraceEvent.Screen(inlineText("before")),
                EngineTraceEvent.AskText(pending.prompt, listOf("YES"), 3),
            ),
            host.events,
        )

        val resumed = resumeAfterSnapshot(vm, program, blocked.continuation, CompiledVmInput.AskText("yes please"))

        val completed = assertIs<CompiledVmResult.Completed>(resumed)
        assertEquals(12, completed.runtime.activeParty.resources.gold)
        assertEquals(
            listOf(
                EngineTraceEvent.Screen(inlineText("before")),
                EngineTraceEvent.AskText(pending.prompt, listOf("YES"), 3),
                EngineTraceEvent.Screen(inlineText("after")),
            ),
            host.events,
        )
    }

    @Test
    fun compiledVmAskTextMissBranchResumesWithoutReplayingPriorEffects() {
        val model =
            rylween {
                content {
                    scene(VmAskTextScene) {
                        choice.textAnswer(
                            prompt = inlineText("answer"),
                            accepting = listOf("YES"),
                            match = {
                                party.gold += 10
                            },
                            miss = {
                                party.gold += 20
                            },
                        )
                    }
                }
            }
        val (compiled, program) = compiledScene(model, VmAskTextScene)
        val host = RecordingCompiledVmHost()
        val vm = CompiledVm(compiled.content, host)
        val blocked =
            assertIs<CompiledVmResult.Blocked>(
                vm.run(program, CompiledVmStart(RuntimeSessionState.singleParty(testParty(), 12, null))),
            )

        val resumed = resumeAfterSnapshot(vm, program, blocked.continuation, CompiledVmInput.AskText("NO"))

        val completed = assertIs<CompiledVmResult.Completed>(resumed)
        assertEquals(22, completed.runtime.activeParty.resources.gold)
        assertEquals(1, host.events.size)
    }

    @Test
    fun compiledVmResumesLoopIterationFrameWithoutReplayingOrSkipping() {
        val model =
            rylween {
                content {
                    scene(VmLoopContinuationScene) {
                        val count = localInt(0, "loop_count")
                        loop {
                            count += 1
                            party.gold += 1
                            waitInput("loop")
                            whenever(count eq 2) {
                                breakLoop("done")
                            }
                        }
                        party.torches += 1
                        screen("after")
                    }
                }
            }
        val (compiled, program) = compiledScene(model, VmLoopContinuationScene)
        val host = RecordingCompiledVmHost()
        val vm = CompiledVm(compiled.content, host)

        val firstBlocked =
            assertIs<CompiledVmResult.Blocked>(
                vm.run(program, CompiledVmStart(RuntimeSessionState.singleParty(testParty(), 12, null))),
            )
        assertEquals(3, firstBlocked.continuation.runtime.activeParty.resources.gold)
        val firstLoopFrame = firstBlocked.continuation.frames.first()
        assertEquals(CompiledVmFrameKind.LoopBody, firstLoopFrame.kind)

        val secondResult = resumeAfterSnapshot(vm, program, firstBlocked.continuation, CompiledVmInput.Continue)

        val secondBlocked = assertIs<CompiledVmResult.Blocked>(secondResult)
        assertEquals(4, secondBlocked.continuation.runtime.activeParty.resources.gold)
        assertEquals(0, secondBlocked.continuation.runtime.activeParty.resources.torches)
        val secondLoopFrame = secondBlocked.continuation.frames.first()
        assertEquals(CompiledVmFrameKind.LoopBody, secondLoopFrame.kind)
        assertEquals(2, host.events.count { event -> event is EngineTraceEvent.WaitInput })

        val completed =
            assertIs<CompiledVmResult.Completed>(
                resumeAfterSnapshot(vm, program, secondBlocked.continuation, CompiledVmInput.Continue),
            )

        assertEquals(4, completed.runtime.activeParty.resources.gold)
        assertEquals(1, completed.runtime.activeParty.resources.torches)
        assertEquals(EngineTraceEvent.Screen(inlineText("after")), host.events.last())
    }

    @Test
    fun compiledVmResumesForEachMemberFrameAtNextMemberWithoutReplaying() {
        val model =
            rylween {
                content {
                    scene(VmMemberIterationContinuationScene) {
                        forEachMember(includeDead = true) { member ->
                            party.gold += member.level
                            waitInput("member")
                        }
                        screen("after")
                    }
                }
            }
        val (compiled, program) = compiledScene(model, VmMemberIterationContinuationScene)
        val host = RecordingCompiledVmHost()
        val vm = CompiledVm(compiled.content, host)

        val firstBlocked =
            assertIs<CompiledVmResult.Blocked>(
                vm.run(program, CompiledVmStart(RuntimeSessionState.singleParty(testParty(), 12, null))),
            )
        assertEquals(5, firstBlocked.continuation.runtime.activeParty.resources.gold)
        val firstMemberFrame = firstBlocked.continuation.frames.first()
        assertEquals(CompiledVmFrameKind.ForEachMemberBody, firstMemberFrame.kind)

        val secondResult = resumeAfterSnapshot(vm, program, firstBlocked.continuation, CompiledVmInput.Continue)

        val secondBlocked = assertIs<CompiledVmResult.Blocked>(secondResult)
        assertEquals(14, secondBlocked.continuation.runtime.activeParty.resources.gold)
        val secondMemberFrame = secondBlocked.continuation.frames.first()
        assertEquals(CompiledVmFrameKind.ForEachMemberBody, secondMemberFrame.kind)
        assertEquals(2, host.events.count { event -> event is EngineTraceEvent.WaitInput })

        val completed =
            assertIs<CompiledVmResult.Completed>(
                resumeAfterSnapshot(vm, program, secondBlocked.continuation, CompiledVmInput.Continue),
            )

        assertEquals(14, completed.runtime.activeParty.resources.gold)
        assertEquals(EngineTraceEvent.Screen(inlineText("after")), host.events.last())
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
    fun compiledVmExecutesRemainingRuntimeMutationOpcodes() {
        val model =
            rylween {
                content {
                    scene(VmRuntimeMutationScene) {
                        consumeActiveItem()
                        val clearSlot = let("clear_slot", 2)
                        party.inventory.clear(InventorySlotRef(clearSlot))
                        consumeTorch()
                        teleportParty(6, 2)
                        stop("done")
                    }
                }
            }
        val (compiled, program) = compiledScene(model, VmRuntimeMutationScene)
        val host = RecordingCompiledVmHost()

        val result =
            CompiledVm(compiled.content, host)
                .run(
                    program,
                    CompiledVmStart(
                        runtime =
                            RuntimeSessionState.singleParty(
                                testParty(inventoryItems = mapOf(1 to VmGem, 2 to VmCoin)),
                                hour = 12,
                                currentTown = null,
                            ),
                        activeInventorySlot = 1,
                    ),
                )

        val completed = assertIs<CompiledVmResult.Completed>(result)
        assertNull(completed.runtime.activeParty.inventoryItem(1))
        assertNull(completed.runtime.activeParty.inventoryItem(2))
        assertEquals(Wilderness.xy(4, 0), completed.runtime.activeParty.position)
        assertTrue(EngineTraceEvent.ConsumeTorch in host.events)
        assertTrue(EngineTraceEvent.TeleportParty(directionKey = 6, distance = 2) in host.events)
    }

    @Test
    fun compiledVmDiscoversCaveAndResumesAfterDungeonChildCompletion() {
        val model =
            rylween {
                content {
                    scene(VmDiscoverCaveScene) {
                        discoverCave(levels = 3, dungeon = VmCave, moveCost = 4)
                        screen("after cave")
                    }
                }
            }
        val (compiled, program) = compiledScene(model, VmDiscoverCaveScene)
        val host = RecordingCompiledVmHost()
        val vm = CompiledVm(compiled.content, host)

        val blocked =
            assertIs<CompiledVmResult.Blocked>(
                vm.run(program, CompiledVmStart(RuntimeSessionState.singleParty(testParty(), 12, null))),
            )
        val pending = assertIs<CompiledVmPendingInput.ChildSession>(blocked.pending)
        val request = assertIs<rylween.runtime.vm.CompiledVmChildRequest.EnterDungeon>(pending.request)
        assertEquals(VmCave, request.handoff.dungeon)
        assertEquals(3, request.handoff.levels)
        assertEquals(RuntimeMode.Dungeon, blocked.continuation.runtime.realm.currentMode)
        assertEquals(6, blocked.continuation.runtime.activeParty.resources.timeLeft)
        assertTrue(EngineTraceEvent.DiscoverCave(request.handoff, moveCost = 4) in host.events)
        assertTrue(EngineTraceEvent.DungeonHandoff(request.handoff) in host.events)

        val resumed =
            vm.resume(
                program,
                blocked.continuation,
                CompiledVmInput.ChildSessionCompleted(
                    CompiledVmChildCompletion(blocked.continuation.runtime),
                ),
            )
        val completed = assertIs<CompiledVmResult.Completed>(resumed)
        assertEquals(RuntimeMode.Dungeon, completed.runtime.realm.currentMode)
        assertEquals(EngineTraceEvent.Screen(inlineText("after cave")), host.events.last())
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
                    party.inventory.consume(VmTreasurePool, 1)
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

    private class MonsterPromptHost(
        private val targetKeys: List<Char>,
        private val monsterHp: Int = 17,
    ) : RecordingCompiledVmHost() {
        override fun monsterTargetKeys(): List<Char> = targetKeys

        override fun readMonsterInt(
            monster: Int,
            field: String,
        ): Int? =
            when {
                monster == 1 && field == "hit_points" -> monsterHp
                else -> null
            }
    }

    private class MonsterIterationHost(
        private val hitPoints: Map<Int, Int>,
    ) : RecordingCompiledVmHost() {
        override fun monsterIterationIndices(includeDead: Boolean): List<Int>? =
            hitPoints
                .filterValues { hp -> includeDead || hp > 0 }
                .keys
                .sorted()

        override fun readMonsterInt(
            monster: Int,
            field: String,
        ): Int? =
            when (field) {
                "hit_points" -> hitPoints[monster]
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

    private fun assertContinuationSnapshotRoundTrip(continuation: CompiledVmContinuation): CompiledVmContinuation {
        val snapshot = continuation.toSnapshot()
        val restored = snapshot.toContinuation(continuation.runtime)
        assertEquals(continuation, restored)
        assertEquals(snapshot, restored.toSnapshot())
        return restored
    }

    private fun resumeAfterSnapshot(
        vm: CompiledVm,
        program: CompiledScriptProgram,
        continuation: CompiledVmContinuation,
        input: CompiledVmInput,
    ): CompiledVmResult = vm.resume(program, assertContinuationSnapshotRoundTrip(continuation), input)

    private data class CompiledVmFrameForTest(
        val blockPath: List<Int>,
        val nextIndex: Int,
        val insideLoop: Boolean,
    ) {
        constructor(frame: rylween.runtime.vm.CompiledVmFrame) : this(frame.blockPath, frame.nextIndex, frame.insideLoop)
    }

    private data class CompiledVmInventorySlotChoiceForTest(
        val key: Char,
        val slot: Int,
    ) {
        constructor(choice: rylween.runtime.vm.CompiledVmInventorySlotChoice) : this(choice.key, choice.slot)
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
        val VmNoneScene = sceneId("vm.none")
        val VmMenuDefaultScene = sceneId("vm.menu_default")
        val VmAskKeyScene = sceneId("vm.ask_key")
        val VmAskMemberScene = sceneId("vm.ask_member")
        val VmAskInventorySlotScene = sceneId("vm.ask_inventory_slot")
        val VmAskTextScene = sceneId("vm.ask_text")
        val VmAskMonsterScene = sceneId("vm.ask_monster")
        val VmLoopContinuationScene = sceneId("vm.loop_continuation")
        val VmMemberIterationContinuationScene = sceneId("vm.member_iteration_continuation")
        val VmForEachMonsterScene = sceneId("vm.foreach_monster")
        val VmUnsupportedScene = sceneId("vm.unsupported")
        val VmExpressionScene = sceneId("vm.expressions")
        val VmBattleScene = sceneId("vm.battle_expr")
        val VmNestedExpressionScene = sceneId("vm.nested_expr")
        val VmRuntimeMutationScene = sceneId("vm.runtime_mutations")
        val VmDiscoverCaveScene = sceneId("vm.discover_cave")
        val VmGem = item("vm.gem")
        val VmCoin = item("vm.coin")
        val VmTreasurePool = itemPool("vm.treasure")
        val VmCave = dungeon("vm.cave")
    }
}

private object VmStoryFlag : FlagRef("vm.story") {
    val none = state("none")
    val ready = state("ready")
}

@Suppress("unused")
private val RegisteredVmStoryFlag = VmStoryFlag.declareFlag()
