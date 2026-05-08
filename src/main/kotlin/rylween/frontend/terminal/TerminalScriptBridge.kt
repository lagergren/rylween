/**
 * Terminal-driven bridge for resumable authored scenes and script continuations.
 *
 * This is frontend-facing glue over backend-neutral script/session models from
 * `runtime/ScriptContinuation.kt` and engine trace events from
 * `engine/EnginePort.kt`.
 */

package rylween.frontend.terminal

import rylween.dsl.BoolAst
import rylween.dsl.BuildingServiceModel
import rylween.dsl.GameMilestoneId
import rylween.dsl.Gender
import rylween.dsl.IntAst
import rylween.dsl.ItemPoolId
import rylween.dsl.LValueAst
import rylween.dsl.LocationId
import rylween.dsl.MenuOption
import rylween.dsl.NativeAllowlist
import rylween.dsl.NativeStringArg
import rylween.dsl.NpcJobId
import rylween.dsl.NpcRole
import rylween.dsl.Op
import rylween.dsl.PartyState
import rylween.dsl.PronounCase
import rylween.dsl.RealmState
import rylween.dsl.RuntimeContent
import rylween.dsl.RuntimeMode
import rylween.dsl.SceneModel
import rylween.dsl.ScriptControlSignal
import rylween.dsl.StrAst
import rylween.dsl.SwitchCase
import rylween.dsl.TextBlock
import rylween.dsl.TextPart
import rylween.dsl.building
import rylween.dsl.dungeon
import rylween.dsl.fallbackText
import rylween.dsl.inputKey
import rylween.dsl.item
import rylween.dsl.line
import rylween.dsl.lines
import rylween.dsl.location
import rylween.dsl.matches
import rylween.dsl.milestone
import rylween.dsl.monster
import rylween.dsl.pronoun
import rylween.dsl.rumour
import rylween.dsl.runtimeContent
import rylween.dsl.text
import rylween.dsl.town
import rylween.dsl.townNpcName
import rylween.dsl.worldMap
import rylween.engine.EngineRequest
import rylween.engine.EngineTraceEvent
import rylween.engine.KotlinHeadlessHost
import rylween.engine.RuntimeEventLevel
import rylween.engine.TraceEnginePort
import rylween.engine.runtimeEvent
import rylween.runtime.AuthoredScriptOracleApi
import rylween.runtime.IntAstEvaluator
import rylween.runtime.RuntimeSessionState
import rylween.runtime.ScriptBlockRef
import rylween.runtime.ScriptBranch
import rylween.runtime.ScriptContinuation
import rylween.runtime.ScriptFrame
import rylween.runtime.ScriptRootRef
import rylween.runtime.battle.BattleSession
import rylween.runtime.battle.BattleSessionStep
import rylween.runtime.consumeInventoryItems
import rylween.runtime.countInventoryItems
import rylween.runtime.drawRandom
import rylween.runtime.inventoryItem
import rylween.runtime.party
import rylween.runtime.prompt
import rylween.runtime.reward.RandomItemOfferSession
import rylween.runtime.reward.RewardGrantSession
import rylween.runtime.scriptMemberField
import rylween.runtime.scriptMemberFlag
import rylween.runtime.scriptPartyScalar
import rylween.runtime.scriptRealmScalar
import rylween.runtime.scriptRootForScene
import rylween.runtime.scriptRuntimeFlag
import rylween.runtime.town.ShopServiceSession
import rylween.runtime.town.SpeakRuntimeContent
import rylween.runtime.town.SpeakSession
import rylween.runtime.town.noRumourText
import rylween.runtime.town.selectRumour
import rylween.runtime.town.speakRuntimeContent
import rylween.runtime.vm.CompiledVmChildCompletion
import rylween.runtime.vm.CompiledVmChildRequest
import rylween.runtime.vm.CompiledVmContinuation
import rylween.runtime.vm.CompiledVmInput
import rylween.runtime.vm.CompiledVmPendingInput
import rylween.runtime.vm.CompiledVmResult
import rylween.runtime.vm.CompiledVmScriptRunner
import rylween.runtime.vm.CompiledVmWaitInputPrompt
import rylween.runtime.vm.RecordingCompiledVmHost
import rylween.runtime.wilderness.WildernessDungeonHandoff
import rylween.runtime.wilderness.WorldMapGrid
import rylween.runtime.wilderness.teleportParty
import rylween.runtime.withFlagState
import rylween.runtime.withInventoryItem
import rylween.runtime.withJournalLines
import rylween.runtime.withMilestone
import rylween.runtime.withScriptMemberField
import rylween.runtime.withScriptPartyScalar
import rylween.runtime.withScriptRealmScalar

/** Status returned after attempting to advance a terminal script bridge. */
sealed interface ScriptBridgeStatus {
    object Finished : ScriptBridgeStatus

    object Waiting : ScriptBridgeStatus
}

/** Engine state plus bridge status after one script bridge run. */
data class ScriptBridgeResult(
    val runtime: RuntimeSessionState,
    val status: ScriptBridgeStatus,
    val events: List<EngineTraceEvent> = emptyList(),
    val continuation: ScriptContinuation? = null,
    val rejection: TerminalInputRejection? = null,
    val dungeonHandoff: WildernessDungeonHandoff? = null,
) {
    val party: PartyState get() = runtime.activeParty
    val realm: RealmState get() = runtime.realm
}

/**
 * Root-level script invocation shared by authored and compiled bridge backends.
 *
 * [root] identifies the compiled-bundle program and save/diagnostic identity.
 * [rootOps] carries the authored source body for explicit authored-oracle
 * bridges; compiled bridges use [root] to find the loaded VM program.
 * [traceEvent] is emitted before execution when the script root also represents
 * a visible host transition, such as entering a town service.
 */
data class ScriptBridgeInvocation(
    val root: ScriptRootRef,
    val rootOps: List<Op>,
    val continuation: ScriptContinuation,
    val traceEvent: EngineTraceEvent? = null,
    val resumeModeAfterBattle: RuntimeMode? = null,
)

/** Frontend-facing bridge for running or resuming authored script blocks. */
interface ScriptBridge {
    fun run(invocation: ScriptBridgeInvocation): ScriptBridgeResult

    fun run(
        service: BuildingServiceModel,
        scene: SceneModel,
        continuation: ScriptContinuation,
    ): ScriptBridgeResult
}

/**
 * Error raised when a terminal adapter reaches a script root without a bridge.
 *
 * This is the adapter-level guard for the J2 compiled-runtime path: app
 * composition must choose [CompiledVmTerminalScriptBridge] for production
 * execution, or an explicit [StatefulScriptBridge] in a test/oracle harness,
 * before a script root can run.
 */
class MissingScriptBridgeException(
    owner: String,
    root: ScriptRootRef,
) : IllegalStateException(
        "$owner reached script root $root without an explicit ScriptBridge; " +
            "pass CompiledVmTerminalScriptBridge for production or StatefulScriptBridge for an oracle test.",
    )

/**
 * Sentinel bridge used by reusable terminal adapters when composition omitted a
 * real script bridge. Non-script adapter paths can still run, but reaching a
 * script root fails immediately instead of silently selecting the authored
 * interpreter.
 */
class MissingScriptBridge(
    private val owner: String,
) : ScriptBridge {
    override fun run(invocation: ScriptBridgeInvocation): ScriptBridgeResult = missing(invocation.root)

    override fun run(
        service: BuildingServiceModel,
        scene: SceneModel,
        continuation: ScriptContinuation,
    ): ScriptBridgeResult = missing(ScriptRootRef.scene(scene.id))

    private fun missing(root: ScriptRootRef): Nothing = throw MissingScriptBridgeException(owner, root)
}

/**
 * Stateful terminal implementation of [ScriptBridge] for explicit authored-oracle tests.
 *
 * This bridge runs the legacy authored `Op` interpreter. It remains available
 * for migration debugging and parity tests; production launchers compose
 * [CompiledVmTerminalScriptBridge] instead.
 */
@AuthoredScriptOracleApi
class StatefulScriptBridge(
    private val terminal: TerminalPort,
    private val engine: TraceEnginePort = TraceEnginePort(),
    private val speakContent: SpeakRuntimeContent = SpeakRuntimeContent(),
    private val battleContent: RuntimeContent = RuntimeContent(),
    private val worldMap: WorldMapGrid? = null,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(characters = speakContent.characters),
) : ScriptBridge {
    override fun run(invocation: ScriptBridgeInvocation): ScriptBridgeResult {
        val eventStart = engine.events.size
        invocation.traceEvent?.let(engine::trace)
        val result =
            StatefulScriptServiceRunner(
                terminal = terminal,
                engine = engine,
                rootOps = invocation.rootOps,
                continuation = invocation.continuation,
                speakContent = speakContent,
                battleContent = battleContent,
                worldMap = worldMap,
                textRenderer = renderer,
                resumeModeAfterBattle = invocation.resumeModeAfterBattle,
                scriptRoot = invocation.root,
            ).run()
        return ScriptBridgeResult(
            runtime = result.runtime,
            status = result.status,
            events = engine.events.drop(result.unwrittenEventStart.coerceAtLeast(eventStart)),
            continuation = result.continuation,
            rejection = result.rejection,
            dungeonHandoff = result.dungeonHandoff,
        )
    }

    override fun run(
        service: BuildingServiceModel,
        scene: SceneModel,
        continuation: ScriptContinuation,
    ): ScriptBridgeResult {
        val root = battleContent.scriptRootForScene(scene.id)
        return run(
            ScriptBridgeInvocation(
                root = root,
                rootOps = scene.ops,
                continuation = continuation,
                traceEvent = EngineTraceEvent.TownService(service, scene),
            ),
        )
    }
}

/**
 * Terminal [ScriptBridge] implementation backed by the compiled VM.
 *
 * This is the J2a adapter that lets existing town, wilderness, and terminal
 * session adapters opt into compiled script execution without replacing their
 * host-owned session state machines. The bridge consumes the same terminal
 * prompts as [StatefulScriptBridge], stores blocked VM frames inside
 * [ScriptContinuation.compiledVmContinuation], and returns the same
 * [ScriptBridgeResult] shape to callers.
 *
 * The bridge also owns terminal host-side child completions for conversation,
 * rumour, reward, random-offer, shop, battle, and dungeon handoff requests.
 * Those child sessions stay outside the VM, while the parent compiled frame
 * resumes through [CompiledVmInput.ChildSessionCompleted] after the host
 * reports the updated runtime.
 */
class CompiledVmTerminalScriptBridge(
    private val terminal: TerminalPort,
    private val runner: CompiledVmScriptRunner,
    private val vmHost: RecordingCompiledVmHost,
    private val content: RuntimeContent,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(),
) : ScriptBridge {
    override fun run(invocation: ScriptBridgeInvocation): ScriptBridgeResult {
        val root = invocation.root
        val continuation = invocation.continuation
        val eventStart = vmHost.events.size
        var writtenEventCount = eventStart
        invocation.traceEvent?.let(vmHost::trace)

        fun unwrittenEvents(): List<EngineTraceEvent> = vmHost.events.drop(writtenEventCount.coerceAtLeast(eventStart))

        fun flushEvents(runtime: RuntimeSessionState): Boolean {
            val events = unwrittenEvents()
            if (events.isEmpty()) return false
            terminal.writeScriptEvents(
                events = events,
                renderer = renderer,
                runtime = runtime,
                renderDungeonHandoff = false,
                renderTownService = false,
            )
            writtenEventCount = vmHost.events.size
            return true
        }

        fun waitingResult(
            blocked: CompiledVmResult.Blocked,
            runtime: RuntimeSessionState? = null,
            rejection: TerminalInputRejection? = null,
            speakSession: SpeakSession? = null,
            randomItemOfferSession: RandomItemOfferSession? = null,
            rewardGrantSession: RewardGrantSession? = null,
            shopServiceSession: ShopServiceSession? = null,
            battleSession: BattleSession? = null,
        ): ScriptBridgeResult {
            val resultRuntime = runtime ?: speakSession?.runtime ?: battleSession?.runtime ?: blocked.continuation.runtime
            return ScriptBridgeResult(
                runtime = resultRuntime,
                status = ScriptBridgeStatus.Waiting,
                events = unwrittenEvents(),
                continuation =
                    continuation
                        .withRoot(root)
                        .copy(
                            runtime = resultRuntime,
                            locals = blocked.continuation.locals,
                            speakSession = speakSession,
                            randomItemOfferSession = randomItemOfferSession,
                            rewardGrantSession = rewardGrantSession,
                            shopServiceSession = shopServiceSession,
                            battleSession = battleSession,
                            compiledVmContinuation = blocked.continuation,
                        ),
                rejection = rejection,
            )
        }

        var result: CompiledVmResult =
            continuation.compiledVmContinuation
                ?.takeIf { compiled -> compiled.root.sameRoot(root) }
                ?.let { compiled -> CompiledVmResult.Blocked(compiled.pending, compiled) }
                ?: runner.run(
                    root = root,
                    runtime = continuation.runtime,
                    locals = continuation.locals,
                    activeInventorySlot = continuation.activeInventorySlot,
                )

        repeat(512) {
            when (val current = result) {
                is CompiledVmResult.Completed ->
                    return ScriptBridgeResult(
                        runtime = current.runtime,
                        status = ScriptBridgeStatus.Finished,
                        events = unwrittenEvents(),
                    )
                is CompiledVmResult.Failed ->
                    error("compiled VM script ${root.kind}.${root.slug} failed: ${current.diagnostic}")
                is CompiledVmResult.Blocked -> {
                    val promptRendered = flushEvents(current.continuation.runtime)
                    when (
                        val childResult =
                            runChildSession(
                                blocked = current,
                                continuation = continuation,
                                resumeModeAfterBattle = invocation.resumeModeAfterBattle,
                            )
                    ) {
                        is ChildSessionResult.Resumed -> {
                            result = childResult.result
                            return@repeat
                        }
                        is ChildSessionResult.Finished ->
                            return ScriptBridgeResult(
                                runtime = childResult.runtime,
                                status = ScriptBridgeStatus.Finished,
                                events = unwrittenEvents(),
                            )
                        is ChildSessionResult.DungeonHandoff ->
                            return ScriptBridgeResult(
                                runtime = childResult.runtime,
                                status = ScriptBridgeStatus.Finished,
                                events = unwrittenEvents(),
                                continuation =
                                    continuation
                                        .withRoot(root)
                                        .copy(
                                            runtime = childResult.runtime,
                                            locals = childResult.continuation.locals,
                                            speakSession = null,
                                            battleSession = null,
                                            randomItemOfferSession = null,
                                            rewardGrantSession = null,
                                            shopServiceSession = null,
                                            compiledVmContinuation = childResult.continuation,
                                        ),
                                dungeonHandoff = childResult.handoff,
                            )
                        is ChildSessionResult.Waiting ->
                            return waitingResult(
                                current,
                                runtime = childResult.runtime,
                                rejection = childResult.rejection,
                                speakSession = childResult.speakSession,
                                randomItemOfferSession = childResult.randomItemOfferSession,
                                rewardGrantSession = childResult.rewardGrantSession,
                                shopServiceSession = childResult.shopServiceSession,
                                battleSession = childResult.battleSession,
                            )
                        null -> Unit
                    }
                    if (!promptRendered) {
                        writePendingPrompt(current.pending, current.continuation.runtime)
                    }
                    when (val input = readPendingInput(current.pending)) {
                        is TerminalInput.Value -> {
                            result = runner.resume(current.continuation, input.value)
                        }
                        is TerminalInput.Rejected -> return waitingResult(current, rejection = input.rejection)
                        TerminalInput.Unavailable -> return waitingResult(current)
                    }
                }
            }
        }
        error("compiled VM script ${root.kind}.${root.slug} exceeded terminal bridge step budget")
    }

    override fun run(
        service: BuildingServiceModel,
        scene: SceneModel,
        continuation: ScriptContinuation,
    ): ScriptBridgeResult {
        val root = content.scriptRootForScene(scene.id)
        return run(
            ScriptBridgeInvocation(
                root = root,
                rootOps = scene.ops,
                continuation = continuation,
                traceEvent = EngineTraceEvent.TownService(service, scene),
            ),
        )
    }

    private sealed interface ChildSessionResult {
        data class Resumed(
            val result: CompiledVmResult,
        ) : ChildSessionResult

        data class Finished(
            val runtime: RuntimeSessionState,
        ) : ChildSessionResult

        data class DungeonHandoff(
            val runtime: RuntimeSessionState,
            val handoff: WildernessDungeonHandoff,
            val continuation: CompiledVmContinuation,
        ) : ChildSessionResult

        data class Waiting(
            val runtime: RuntimeSessionState,
            val rejection: TerminalInputRejection?,
            val speakSession: SpeakSession? = null,
            val randomItemOfferSession: RandomItemOfferSession? = null,
            val rewardGrantSession: RewardGrantSession? = null,
            val shopServiceSession: ShopServiceSession? = null,
            val battleSession: BattleSession? = null,
        ) : ChildSessionResult
    }

    private fun runChildSession(
        blocked: CompiledVmResult.Blocked,
        continuation: ScriptContinuation,
        resumeModeAfterBattle: RuntimeMode?,
    ): ChildSessionResult? {
        val pending = blocked.pending as? CompiledVmPendingInput.ChildSession
        if (pending == null) {
            return null
        }
        val activeContinuation = continuation.takeIf { it.compiledVmContinuation?.pending.samePending(blocked.pending) }
        return when (val request = pending.request) {
            is CompiledVmChildRequest.Speak -> runSpeak(request, blocked, activeContinuation?.speakSession)
            is CompiledVmChildRequest.TellRumour -> ChildSessionResult.Resumed(completeTellRumour(request, blocked))
            is CompiledVmChildRequest.GrantReward -> runGrantReward(request, blocked, activeContinuation?.rewardGrantSession)
            is CompiledVmChildRequest.RandomItemOffer ->
                runRandomItemOffer(request, blocked, activeContinuation?.randomItemOfferSession)
            is CompiledVmChildRequest.ShopService -> runShopService(request, blocked, activeContinuation?.shopServiceSession)
            is CompiledVmChildRequest.Encounter ->
                runEncounter(request, blocked, activeContinuation?.battleSession, resumeModeAfterBattle)
            is CompiledVmChildRequest.EnterDungeon -> runEnterDungeon(request, blocked, continuation)
        }
    }

    private fun CompiledVmPendingInput?.samePending(other: CompiledVmPendingInput): Boolean {
        val pending = this
        if (pending == null) {
            return false
        }
        return pending.root.sameRoot(other.root) &&
            pending.instructionIndex == other.instructionIndex &&
            pending.operandPath == other.operandPath
    }

    private fun runSpeak(
        request: CompiledVmChildRequest.Speak,
        blocked: CompiledVmResult.Blocked,
        activeSession: SpeakSession?,
    ): ChildSessionResult {
        val childEngine = TraceEnginePort()
        val initial = activeSession ?: SpeakSession.start(content.speakRuntimeContent(), blocked.continuation.runtime, request.building)
        val result = SpeakSessionTraceRunner(terminal, childEngine, content).runUntilBlocked(initial)
        childEngine.events.forEach(vmHost::trace)
        return when (val status = result.status) {
            TerminalRunStatus.Finished ->
                ChildSessionResult.Resumed(
                    runner.resume(
                        blocked.continuation,
                        CompiledVmInput.ChildSessionCompleted(CompiledVmChildCompletion(result.session.runtime)),
                    ),
                )
            is TerminalRunStatus.Waiting ->
                ChildSessionResult.Waiting(
                    runtime = result.session.runtime,
                    rejection = status.rejection,
                    speakSession = result.session,
                )
            TerminalRunStatus.StepLimitReached ->
                error("compiled VM speak child for ${request.building.slug} exceeded terminal bridge step budget")
        }
    }

    private fun runGrantReward(
        request: CompiledVmChildRequest.GrantReward,
        blocked: CompiledVmResult.Blocked,
        activeSession: RewardGrantSession?,
    ): ChildSessionResult {
        val childEngine = TraceEnginePort()
        val initial =
            activeSession ?: RewardGrantSession.start(
                reward = latestTraceEvent<EngineTraceEvent.GrantReward>("GrantReward").reward,
                party = blocked.continuation.runtime.activeParty,
                gold = request.gold,
                content = content,
            )
        val result = RewardGrantSessionTraceRunner(terminal, childEngine).runUntilBlocked(initial)
        childEngine.events.forEach(vmHost::trace)
        val nextRuntime = blocked.continuation.runtime.withActiveParty(result.session.party)
        return when (val status = result.status) {
            TerminalRunStatus.Finished ->
                ChildSessionResult.Resumed(
                    runner.resume(
                        blocked.continuation,
                        CompiledVmInput.ChildSessionCompleted(CompiledVmChildCompletion(nextRuntime)),
                    ),
                )
            is TerminalRunStatus.Waiting ->
                ChildSessionResult.Waiting(
                    runtime = nextRuntime,
                    rejection = status.rejection,
                    rewardGrantSession = result.session,
                )
            TerminalRunStatus.StepLimitReached -> error("compiled VM reward child exceeded terminal bridge step budget")
        }
    }

    private fun runRandomItemOffer(
        request: CompiledVmChildRequest.RandomItemOffer,
        blocked: CompiledVmResult.Blocked,
        activeSession: RandomItemOfferSession?,
    ): ChildSessionResult {
        val childEngine = TraceEnginePort()
        val initial =
            activeSession ?: RandomItemOfferSession.start(
                offer = latestTraceEvent<EngineTraceEvent.RandomItemOffer>("RandomItemOffer").offer,
                party = blocked.continuation.runtime.activeParty,
                content = content,
                selectedIndex = request.selectedIndex,
            )
        val result = RandomItemOfferSessionTraceRunner(terminal, childEngine).runUntilBlocked(initial)
        childEngine.events.forEach(vmHost::trace)
        val nextRuntime = blocked.continuation.runtime.withActiveParty(result.session.party)
        return when (val status = result.status) {
            TerminalRunStatus.Finished ->
                ChildSessionResult.Resumed(
                    runner.resume(
                        blocked.continuation,
                        CompiledVmInput.ChildSessionCompleted(CompiledVmChildCompletion(nextRuntime)),
                    ),
                )
            is TerminalRunStatus.Waiting ->
                ChildSessionResult.Waiting(
                    runtime = nextRuntime,
                    rejection = status.rejection,
                    randomItemOfferSession = result.session,
                )
            TerminalRunStatus.StepLimitReached -> error("compiled VM random-item child exceeded terminal bridge step budget")
        }
    }

    private fun runShopService(
        request: CompiledVmChildRequest.ShopService,
        blocked: CompiledVmResult.Blocked,
        activeSession: ShopServiceSession?,
    ): ChildSessionResult {
        val initial =
            activeSession ?: ShopServiceSession.start(
                latestTraceEvent<EngineTraceEvent.ShopService>("ShopService").shop,
                blocked.continuation.runtime.activeParty,
                content,
            )
        val result = ShopServiceTerminalAdapter(terminal, renderer).runUntilBlocked(initial)
        val nextRuntime = blocked.continuation.runtime.withActiveParty(result.session.party)
        return when (val status = result.status) {
            TerminalRunStatus.Finished ->
                ChildSessionResult.Resumed(
                    runner.resume(
                        blocked.continuation,
                        CompiledVmInput.ChildSessionCompleted(CompiledVmChildCompletion(nextRuntime)),
                    ),
                )
            is TerminalRunStatus.Waiting ->
                ChildSessionResult.Waiting(
                    runtime = nextRuntime,
                    rejection = status.rejection,
                    shopServiceSession = result.session,
                )
            TerminalRunStatus.StepLimitReached ->
                error("compiled VM shop child for ${initial.shop.building.slug} exceeded terminal bridge step budget")
        }
    }

    private fun runEncounter(
        request: CompiledVmChildRequest.Encounter,
        blocked: CompiledVmResult.Blocked,
        activeSession: BattleSession?,
        resumeModeAfterBattle: RuntimeMode?,
    ): ChildSessionResult {
        if (!content.battleConfigured) {
            return completeChildSession(blocked, blocked.continuation.runtime)
        }
        val monster =
            checkNotNull(content.monsters.firstOrNull { monster -> monster.id == request.monster }) {
                "encounter references unknown monster ${request.monster.slug}"
            }
        val current =
            activeSession ?: BattleSession.startMonster(
                runtime = blocked.continuation.runtime,
                monster = monster,
                content = content,
                tier = request.tier ?: monster.tier,
                mandatory = request.mandatory,
            )
        val finished = current.step as? BattleSessionStep.Finished
        if (finished == null) {
            return ChildSessionResult.Waiting(
                runtime = current.runtime,
                rejection = null,
                battleSession = current,
            )
        }
        val nextRuntime = finished.runtime.afterCompiledScriptBattle(resumeModeAfterBattle)
        if (nextRuntime.realm.currentMode == RuntimeMode.GameOver) {
            return ChildSessionResult.Finished(nextRuntime)
        }
        return completeChildSession(blocked, nextRuntime)
    }

    private fun RuntimeSessionState.afterCompiledScriptBattle(resumeModeAfterBattle: RuntimeMode?): RuntimeSessionState {
        val mode = resumeModeAfterBattle ?: return this
        return takeUnless { realm.currentMode == RuntimeMode.GameOver }
            ?.withRealm(realm.copy(currentMode = mode, currentTown = null))
            ?: this
    }

    private fun runEnterDungeon(
        request: CompiledVmChildRequest.EnterDungeon,
        blocked: CompiledVmResult.Blocked,
        continuation: ScriptContinuation,
    ): ChildSessionResult {
        val canResumeFromDungeon =
            continuation.compiledVmContinuation
                ?.root
                ?.sameRoot(blocked.continuation.root) == true &&
                continuation.runtime.realm.currentMode != RuntimeMode.Dungeon
        if (canResumeFromDungeon) {
            if (continuation.runtime.realm.currentMode == RuntimeMode.GameOver) {
                return ChildSessionResult.Finished(continuation.runtime)
            }
            return completeChildSession(blocked, continuation.runtime)
        }
        return ChildSessionResult.DungeonHandoff(
            runtime = blocked.continuation.runtime,
            handoff = request.handoff,
            continuation = blocked.continuation,
        )
    }

    private fun completeTellRumour(
        request: CompiledVmChildRequest.TellRumour,
        blocked: CompiledVmResult.Blocked,
    ): CompiledVmResult {
        val speakContent = content.speakRuntimeContent()
        val selection = speakContent.selectRumour(request.table, blocked.continuation.runtime)
        val nextRuntime = selection?.runtime ?: blocked.continuation.runtime
        vmHost.trace(EngineTraceEvent.Screen(selection?.rumour?.text ?: speakContent.noRumourText()))
        return runner.resume(
            blocked.continuation,
            CompiledVmInput.ChildSessionCompleted(CompiledVmChildCompletion(nextRuntime)),
        )
    }

    private fun completeChildSession(
        blocked: CompiledVmResult.Blocked,
        runtime: RuntimeSessionState,
    ): ChildSessionResult.Resumed =
        ChildSessionResult.Resumed(
            runner.resume(
                blocked.continuation,
                CompiledVmInput.ChildSessionCompleted(CompiledVmChildCompletion(runtime)),
            ),
        )

    private inline fun <reified T : EngineTraceEvent> latestTraceEvent(childName: String): T =
        vmHost.events
            .asReversed()
            .filterIsInstance<T>()
            .firstOrNull()
            ?: error("compiled VM $childName child did not trace its semantic event")

    private fun writePendingPrompt(
        pending: CompiledVmPendingInput,
        runtime: RuntimeSessionState,
    ) {
        val context = TerminalTextContext(runtime = runtime)
        when (pending) {
            is CompiledVmPendingInput.WaitInput ->
                terminal.write(renderer.lines(pending.prompt ?: renderer.pausePrompt(), context))
            is CompiledVmPendingInput.Menu ->
                terminal.writeKeyPrompt(renderer, pending.prompt, pending.keys, context)
            is CompiledVmPendingInput.AskKey ->
                terminal.writeBindingPrompt(renderer, pending.prompt, pending.bindings, context)
            is CompiledVmPendingInput.AskText ->
                terminal.write(renderer.lines(pending.prompt, context))
            is CompiledVmPendingInput.AskMember ->
                terminal.write(renderer.lines(pending.prompt, context))
            is CompiledVmPendingInput.AskMonster ->
                terminal.write(renderer.lines(pending.prompt, context))
            is CompiledVmPendingInput.AskInventorySlot ->
                terminal.write(renderer.lines(pending.prompt, context))
            is CompiledVmPendingInput.ChildSession -> Unit
        }
    }

    private fun readPendingInput(pending: CompiledVmPendingInput): TerminalInput<CompiledVmInput> =
        when (pending) {
            is CompiledVmPendingInput.WaitInput ->
                when (terminal.readReturn()) {
                    true -> TerminalInput.Value(CompiledVmInput.Continue)
                    false,
                    null,
                    -> TerminalInput.Unavailable
                }
            is CompiledVmPendingInput.Menu -> readMenuInput(pending)
            is CompiledVmPendingInput.AskKey -> readAskKeyInput(pending)
            is CompiledVmPendingInput.AskMember ->
                terminal.readMemberNumber(pending.choices).map { member -> CompiledVmInput.AskMember(member) }
            is CompiledVmPendingInput.AskMonster -> readAskMonsterInput(pending)
            is CompiledVmPendingInput.AskInventorySlot -> readAskInventorySlotInput(pending)
            is CompiledVmPendingInput.AskText ->
                terminal
                    .readTextLine(pending.maxLength)
                    ?.let { raw -> TerminalInput.Value(CompiledVmInput.AskText(raw)) }
                    ?: TerminalInput.Unavailable
            is CompiledVmPendingInput.ChildSession -> TerminalInput.Unavailable
        }

    private fun readMenuInput(pending: CompiledVmPendingInput.Menu): TerminalInput<CompiledVmInput> {
        val raw = terminal.readLine() ?: return TerminalInput.Unavailable
        val key = raw.firstOrNull()?.uppercaseChar()
        if (key != null && key in pending.keys) {
            return TerminalInput.Value(CompiledVmInput.MenuKey(key))
        }
        return if (pending.hasDefault) {
            TerminalInput.Value(CompiledVmInput.MenuKey(key ?: '\u0000'))
        } else {
            TerminalInput.Rejected(TerminalInputRejection(raw, pending.keys.map { key -> key.toString() }))
        }
    }

    private fun readAskKeyInput(pending: CompiledVmPendingInput.AskKey): TerminalInput<CompiledVmInput> {
        val raw = terminal.readLine() ?: return TerminalInput.Unavailable
        return if (pending.bindings.any { binding -> binding.matches(raw) }) {
            TerminalInput.Value(CompiledVmInput.AskKey(raw))
        } else {
            TerminalInput.Rejected(TerminalInputRejection(raw, pending.bindings.map { binding -> binding.label }.distinct()))
        }
    }

    private fun readAskMonsterInput(pending: CompiledVmPendingInput.AskMonster): TerminalInput<CompiledVmInput> {
        val raw = terminal.readLine() ?: return TerminalInput.Unavailable
        val key = raw.firstOrNull()?.uppercaseChar()
        return if (key != null && key in pending.targetKeys) {
            TerminalInput.Value(CompiledVmInput.AskMonster(key.code - 'A'.code))
        } else {
            TerminalInput.Rejected(TerminalInputRejection(raw, pending.targetKeys.map { key -> key.toString() }))
        }
    }

    private fun readAskInventorySlotInput(pending: CompiledVmPendingInput.AskInventorySlot): TerminalInput<CompiledVmInput> {
        val raw = terminal.readLine() ?: return TerminalInput.Unavailable
        return if (pending.keys.any { key -> inputKey(key).matches(raw) }) {
            TerminalInput.Value(CompiledVmInput.AskInventorySlot(raw))
        } else {
            TerminalInput.Rejected(TerminalInputRejection(raw, pending.keys.map { key -> key.toString() }))
        }
    }
}

/**
 * Builds a compiled-VM terminal bridge from a compiled headless host.
 *
 * The extension lives in the terminal package so `engine/KotlinHeadlessHost.kt`
 * stays frontend-neutral. The returned bridge is suitable for the
 * `serviceBridge` parameters on terminal session adapters.
 */
fun KotlinHeadlessHost.compiledVmTerminalScriptBridge(
    terminal: TerminalPort,
    renderer: TerminalTextRenderer = TerminalTextRenderer(),
    worldMap: WorldMapGrid? = null,
    vmHost: RecordingCompiledVmHost = TerminalCompiledVmHost(renderer, worldMap),
): ScriptBridge =
    CompiledVmTerminalScriptBridge(
        terminal = terminal,
        runner = compiledScriptRunner(vmHost),
        vmHost = vmHost,
        content = content.runtimeContent(),
        renderer = renderer,
    )

private class TerminalCompiledVmHost(
    private val renderer: TerminalTextRenderer,
    private val worldMap: WorldMapGrid?,
) : RecordingCompiledVmHost() {
    override fun waitInputPrompt(
        reason: String?,
        runtime: RuntimeSessionState,
    ): CompiledVmWaitInputPrompt {
        val range = renderer.pauseVerbIndexRange()
        if (range == null) {
            return CompiledVmWaitInputPrompt(runtime, renderer.pausePrompt())
        }
        val draw = runtime.drawRandom(range.first, range.last)
        return CompiledVmWaitInputPrompt(draw.runtime, renderer.pausePrompt(draw.value))
    }

    override fun teleportParty(
        runtime: RuntimeSessionState,
        directionKey: Int,
        distance: Int,
    ): RuntimeSessionState = runtime.teleportParty(directionKey, distance, worldMap)
}

/** Full result of running an authored script continuation through terminal adapters. */
@AuthoredScriptOracleApi
data class StatefulScriptResult(
    val runtime: RuntimeSessionState,
    val status: ScriptBridgeStatus,
    val continuation: ScriptContinuation? = null,
    val rejection: TerminalInputRejection? = null,
    val dungeonHandoff: WildernessDungeonHandoff? = null,
    val unwrittenEventStart: Int = 0,
) {
    val party: PartyState get() = runtime.activeParty
}

/**
 * Low-level authored `Op` interpreter used by [StatefulScriptBridge].
 *
 * This class is the old Kotlin runtime for authored script bodies. It is kept
 * as an explicit test-oracle implementation while J2 retirement work moves
 * production hosts to the compiled VM. New runtime integrations should not
 * instantiate it directly.
 */
@AuthoredScriptOracleApi
class StatefulScriptServiceRunner(
    private val terminal: TerminalPort,
    private val engine: TraceEnginePort,
    private val rootOps: List<Op>,
    continuation: ScriptContinuation,
    private val speakContent: SpeakRuntimeContent,
    private val battleContent: RuntimeContent = RuntimeContent(),
    private val worldMap: WorldMapGrid? = null,
    private val textRenderer: TerminalTextRenderer = TerminalTextRenderer(characters = speakContent.characters),
    private val resumeModeAfterBattle: RuntimeMode? = null,
    private val scriptRoot: ScriptRootRef = continuation.root,
) {
    private val initialContinuation = continuation.withRoot(scriptRoot)
    private var runtime = initialContinuation.runtime
    private var party = initialContinuation.party
    private val locals = initialContinuation.locals.toMutableMap()
    private val realmInts = initialContinuation.realmScalars.toMutableMap()
    private val stack = initialContinuation.stack.toMutableList()
    private var speakSession = initialContinuation.speakSession
    private var battleSession = initialContinuation.battleSession
    private var randomItemOfferSession = initialContinuation.randomItemOfferSession
    private var rewardGrantSession = initialContinuation.rewardGrantSession
    private val activeInventorySlot = initialContinuation.activeInventorySlot
    private val worldText =
        TerminalWorldTextResolver(
            towns = battleContent.towns,
            characters = speakContent.characters.ifEmpty { battleContent.characters },
        )
    private var rejection: TerminalInputRejection? = null
    private var dungeonHandoff: WildernessDungeonHandoff? = null
    private var waiting = false
    private var opBudget = 4096
    private var writtenEventCount = engine.events.size

    /** Runs until the authored script finishes, blocks for input, or hands off to a child session. */
    fun run(): StatefulScriptResult {
        runUntilBlockedOrFinished()
        val nextRuntime = currentRuntime()
        val nextContinuation = if (waiting || dungeonHandoff != null) continuation(nextRuntime) else null
        return StatefulScriptResult(
            runtime = nextRuntime,
            status = if (waiting) ScriptBridgeStatus.Waiting else ScriptBridgeStatus.Finished,
            continuation = nextContinuation,
            rejection = rejection,
            dungeonHandoff = dungeonHandoff,
            unwrittenEventStart = writtenEventCount,
        )
    }

    private fun runUntilBlockedOrFinished() {
        while (stack.isNotEmpty() && !waiting && dungeonHandoff == null) {
            if (--opBudget <= 0) {
                stack.clear()
                return
            }
            val frame = stack.last()
            frame.localName?.let { name -> locals[name] = checkNotNull(frame.localValue) }
            val ops = opsFor(frame.block)
            if (frame.nextIndex >= ops.size) {
                finishFrame()
                continue
            }
            run(ops[frame.nextIndex], frame)
        }
    }

    private fun run(
        op: Op,
        frame: ScriptFrame,
    ) {
        val opIndex = frame.nextIndex
        engine.diagnostic(
            runtimeEvent(
                RuntimeEventLevel.Debug,
                "vm",
                "op",
                "rootKind" to scriptRoot.kind,
                "root" to scriptRoot.slug.ifBlank { null },
                "denseId" to scriptRoot.denseId,
                "block" to frame.block.diagnosticPath(),
                "index" to opIndex,
                "opcode" to op.vmOpcode(),
                "args" to op.vmArgs().takeIf { it.isNotBlank() },
            ),
        )
        when (op) {
            is Op.Screen -> {
                advanceTop()
                engine.trace(EngineTraceEvent.Screen(renderText(op.text)))
            }
            is Op.Emit -> {
                advanceTop()
                engine.trace(EngineTraceEvent.Emit(op.text))
            }
            is Op.EmitIndexed -> {
                advanceTop()
                engine.trace(EngineTraceEvent.EmitIndexed(op.pattern, eval(op.value)))
            }
            is Op.WaitInput -> waitInput(op.reason)
            is Op.Pause -> {
                advanceTop()
                engine.trace(EngineTraceEvent.Pause(op.millis))
            }
            is Op.View -> {
                advanceTop()
                engine.trace(EngineTraceEvent.View(op.command))
            }
            is Op.LetInt -> {
                locals[op.name] = eval(op.value)
                advanceTop()
            }
            is Op.BindText -> advanceTop()
            is Op.Set -> {
                set(op.target, eval(op.value))
                advanceTop()
            }
            is Op.Add -> {
                set(op.target, read(op.target) + eval(op.value))
                advanceTop()
            }
            is Op.Sub -> {
                set(op.target, read(op.target) - eval(op.value))
                advanceTop()
            }
            is Op.AddClamped -> {
                set(op.target, minOf(read(op.target) + eval(op.value), eval(op.max)))
                advanceTop()
            }
            is Op.SetFlag -> {
                party = party.withFlagState(op.slug, op.nativeValue)
                advanceTop()
                engine.trace(EngineTraceEvent.SetFlag(op.slug, op.nativeValue))
            }
            is Op.AppendJournal -> appendJournal(op.text)
            is Op.If -> {
                val branch = if (eval(op.cond)) ScriptBranch.IfThen else ScriptBranch.IfElse
                advanceTop()
                pushChild(frame.block.child(opIndex, branch))
            }
            is Op.Loop -> {
                advanceTop()
                pushChild(frame.block.child(opIndex, ScriptBranch.LoopBody), loop = true)
            }
            is Op.Break -> {
                advanceTop()
                engine.trace(EngineTraceEvent.Break(op.reason, op.signal))
                handleControlSignal(op.signal)
            }
            is Op.Stop -> {
                advanceTop()
                engine.trace(EngineTraceEvent.Stop(op.reason, op.signal))
                handleControlSignal(op.signal)
            }
            is Op.Switch -> {
                val value = eval(op.expr)
                val caseIndex = op.cases.indexOfFirst { it.match == value || it.match == null }
                advanceTop()
                if (caseIndex >= 0) {
                    pushChild(
                        frame.block.child(
                            opIndex,
                            ScriptBranch.SwitchCase,
                            caseIndex = caseIndex,
                        ),
                    )
                }
            }
            is Op.Menu -> runMenu(op)
            is Op.AskKey -> askKey(op)
            is Op.AskMember -> askMember(op)
            is Op.AskInventorySlot -> askInventorySlot(op)
            is Op.AskText -> askText(op, frame, opIndex)
            is Op.AskMonster -> advanceTop()
            is Op.ForEachMember -> {
                advanceTop()
                op.memberFrames(party, frame, opIndex).asReversed().forEach { (memberNumber, block) ->
                    stack +=
                        ScriptFrame(
                            block = block,
                            nextIndex = 0,
                            localName = op.varName,
                            localValue = memberNumber,
                        )
                }
            }
            is Op.ForEachMonster -> advanceTop()
            is Op.Speak -> runSpeak(op)
            is Op.TellRumour -> tellRumour(op)
            is Op.RunScene -> {
                runScene(op)
            }
            is Op.Encounter -> {
                runEncounter(op)
            }
            is Op.EnterDungeon -> runEnterDungeon(op)
            Op.ConsumeActiveItem -> {
                activeInventorySlot?.let { slot ->
                    party = party.withInventoryItem(slot, null)
                }
                advanceTop()
            }
            is Op.ClearInventorySlot -> {
                party = party.withInventoryItem(eval(op.slot), null)
                advanceTop()
            }
            is Op.ConsumeInventoryPool -> {
                val quantity = eval(op.quantity)
                party = party.consumeInventoryItems(op.pool, quantity)
                advanceTop()
                engine.trace(EngineTraceEvent.ConsumeInventoryPool(op.pool, quantity))
            }
            Op.ConsumeTorch -> {
                advanceTop()
                engine.trace(EngineTraceEvent.ConsumeTorch)
            }
            is Op.TeleportParty -> runTeleportParty(op)
            is Op.DiscoverCave -> runDiscoverCave(op)
            is Op.GrantReward -> runGrantReward(op)
            is Op.RandomItemOffer -> runRandomItemOffer(op)
            is Op.ShopService -> {
                advanceTop()
                engine.trace(EngineTraceEvent.ShopService(op.shop))
            }
            is Op.AchieveMilestone -> {
                party = party.withMilestone(op.milestone)
                advanceTop()
                engine.trace(EngineTraceEvent.AchieveMilestone(op.milestone))
            }
            is Op.NativeCall -> {
                advanceTop()
                engine.effect(EngineRequest(op.path, op.args))
            }
        }
    }

    private fun appendJournal(text: TextBlock) {
        val rendered = renderText(text)
        party =
            party.withJournalLines(
                textRenderer.lines(rendered, TerminalTextContext(runtime = currentRuntime())),
            )
        advanceTop()
        engine.trace(EngineTraceEvent.AppendJournal(rendered))
    }

    private fun waitInput(reason: String?) {
        val prompt =
            textRenderer.pauseVerbIndexRange()?.let { range ->
                val draw = currentRuntime().drawRandom(range.first, range.last)
                runtime = draw.runtime
                party = runtime.activeParty
                textRenderer.pausePrompt(draw.value)
            } ?: textRenderer.pausePrompt()
        engine.trace(EngineTraceEvent.WaitInput(reason, prompt))
        flushScriptOutput()
        when (terminal.readReturn()) {
            true -> advanceTop()
            false -> wait()
            null -> wait()
        }
    }

    private fun runMenu(op: Op.Menu) {
        val frame = stack.last()
        val opIndex = frame.nextIndex
        val choices = op.options.map { option -> TerminalChoice(option, option.binding) }
        engine.trace(EngineTraceEvent.Menu(renderText(op.prompt), op.options.map { it.key }))
        flushScriptOutput()
        when (val input = terminal.readChoice(choices)) {
            is TerminalInput.Value -> {
                advanceTop()
                pushChild(
                    frame.block.child(opIndex, ScriptBranch.MenuOption, key = input.value.key),
                )
            }
            is TerminalInput.Rejected -> {
                if (op.defaultOps.isEmpty()) {
                    wait(input.rejection)
                } else {
                    advanceTop()
                    pushChild(frame.block.child(opIndex, ScriptBranch.MenuDefault))
                }
            }
            TerminalInput.Unavailable -> wait()
        }
    }

    private fun askKey(op: Op.AskKey) {
        engine.trace(EngineTraceEvent.AskKey(renderText(op.prompt), op.bindings))
        val choices = op.bindings.map { binding -> TerminalChoice(binding.promptValue(), binding) }
        flushScriptOutput()
        terminal.readChoice(choices).scriptOrWait { value ->
            locals[op.varName] = value
            advanceTop()
        }
    }

    private inline fun <T> TerminalInput<T>.scriptOrWait(onValue: (T) -> Unit) {
        when (this) {
            is TerminalInput.Value -> onValue(value)
            is TerminalInput.Rejected -> wait(rejection)
            TerminalInput.Unavailable -> wait()
        }
    }

    private fun askMember(op: Op.AskMember) {
        engine.trace(EngineTraceEvent.AskMember(renderText(op.prompt), op.filter))
        val choices = party.memberChoices(op.accepting)
        flushScriptOutput()
        terminal.readMemberNumber(choices).scriptOrWait { value ->
            locals[op.varName] = value
            advanceTop()
        }
    }

    private fun askInventorySlot(op: Op.AskInventorySlot) {
        engine.trace(EngineTraceEvent.AskInventorySlot(renderText(op.prompt), op.firstKey, op.slots, op.filter))
        val choices = party.inventorySlotChoices(op.filter, op.slots, op.firstKey)
        val keys = choices.map { it.first }
        flushScriptOutput()
        terminal.readKey(keys).scriptOrWait { key ->
            locals[op.varName] = checkNotNull(choices.firstOrNull { it.first == key }).second
            advanceTop()
        }
    }

    private fun askText(
        op: Op.AskText,
        frame: ScriptFrame,
        opIndex: Int,
    ) {
        engine.trace(EngineTraceEvent.AskText(renderText(op.prompt), op.accepted, op.maxLength))
        flushScriptOutput()
        val raw = terminal.readTextLine(op.maxLength) ?: return wait()
        val branch =
            if (op.accepts(raw)) {
                ScriptBranch.TextAnswerMatch
            } else {
                ScriptBranch.TextAnswerMiss
            }
        advanceTop()
        pushChild(frame.block.child(opIndex, branch))
    }

    private fun wait(inputRejection: TerminalInputRejection? = null) {
        waiting = true
        rejection = inputRejection
    }

    private fun flushScriptOutput() {
        terminal.writeScriptEvents(engine.events.drop(writtenEventCount), textRenderer, currentRuntime())
        writtenEventCount = engine.events.size
    }

    private fun advanceTop() {
        val index = stack.lastIndex
        stack[index] = stack[index].copy(nextIndex = stack[index].nextIndex + 1)
    }

    private fun pushChild(
        block: ScriptBlockRef,
        loop: Boolean = false,
    ) {
        if (opsFor(block).isNotEmpty() || loop) {
            stack += ScriptFrame(block, nextIndex = 0, loop = loop)
        }
    }

    private fun finishFrame() {
        val frame = stack.removeLast()
        if (frame.loop) {
            if (frame.iteration < 127) {
                stack += frame.copy(nextIndex = 0, iteration = frame.iteration + 1)
            } else {
                stack.clear()
            }
        }
    }

    private fun handleControlSignal(signal: ScriptControlSignal) {
        when (signal) {
            ScriptControlSignal.Continue -> Unit
            ScriptControlSignal.BreakNearestLoop -> breakNearestLoop()
            ScriptControlSignal.StopScript -> stopCurrentBranch()
        }
    }

    private fun breakNearestLoop() {
        while (stack.isNotEmpty()) {
            val frame = stack.removeLast()
            if (frame.loop) return
        }
    }

    private fun stopCurrentBranch() {
        val loopIndex = stack.indexOfLast { frame -> frame.loop }
        if (loopIndex < 0) {
            stack.clear()
            return
        }
        while (stack.lastIndex > loopIndex) {
            stack.removeLast()
        }
        val loopFrame = stack[loopIndex]
        stack[loopIndex] = loopFrame.copy(nextIndex = opsFor(loopFrame.block).size)
    }

    private fun currentRuntime(): RuntimeSessionState = runtime.withActiveParty(party)

    private fun RuntimeSessionState.afterScriptBattle(): RuntimeSessionState {
        val mode = resumeModeAfterBattle
        if (mode == null) {
            return this
        }
        if (realm.currentMode == RuntimeMode.GameOver) {
            return this
        }
        return withRealm(
            realm.copy(
                currentMode = mode,
                currentTown = null,
                currentMap = worldMap?.id ?: realm.currentMap,
            ),
        )
    }

    private fun continuation(runtime: RuntimeSessionState): ScriptContinuation =
        ScriptContinuation(
            runtime = runtime,
            root = initialContinuation.root,
            locals = locals.toMap(),
            realmScalars = realmInts.toMap(),
            stack = stack.toList(),
            speakSession = speakSession,
            battleSession = battleSession,
            randomItemOfferSession = randomItemOfferSession,
            rewardGrantSession = rewardGrantSession,
            activeInventorySlot = activeInventorySlot,
        )

    private inline fun <S> handleChildResult(
        result: TerminalAdapterResult<S>,
        clear: () -> Unit,
        keepWaiting: (S) -> Unit,
    ) {
        when (result.status) {
            TerminalRunStatus.Finished -> {
                clear()
                advanceTop()
            }
            TerminalRunStatus.StepLimitReached -> {
                clear()
                stack.clear()
            }
            is TerminalRunStatus.Waiting -> {
                keepWaiting(result.session)
                wait(result.status.rejection)
            }
        }
    }

    private fun runSpeak(op: Op.Speak) {
        engine.trace(EngineTraceEvent.Speak(op.building))
        val initial = speakSession ?: SpeakSession.start(speakContent, currentRuntime(), op.building)
        val result = SpeakSessionTraceRunner(terminal, engine, battleContent).runUntilBlocked(initial)
        runtime = result.session.runtime
        party = runtime.activeParty
        handleChildResult(
            result = result,
            clear = { speakSession = null },
            keepWaiting = { waitingSession -> speakSession = waitingSession },
        )
    }

    private fun runScene(op: Op.RunScene) {
        advanceTop()
        engine.trace(EngineTraceEvent.RunScene(op.scene, op.bindings))
    }

    private fun tellRumour(op: Op.TellRumour) {
        advanceTop()
        val selection = speakContent.selectRumour(op.table, currentRuntime())
        val text =
            selection
                ?.also { selected ->
                    runtime = selected.runtime
                    party = runtime.activeParty
                }?.rumour
                ?.text ?: speakContent.noRumourText()
        engine.trace(EngineTraceEvent.Screen(text))
    }

    private fun runEncounter(op: Op.Encounter) {
        engine.trace(EngineTraceEvent.Encounter(op.foe, op.mandatory, op.tier))
        if (!battleContent.battleConfigured) {
            advanceTop()
            return
        }
        val current = battleSession
        if (current == null) {
            val monster =
                checkNotNull(battleContent.monsters.firstOrNull { it.id == op.foe }) {
                    "encounter references unknown monster ${op.foe.slug}"
                }
            battleSession =
                BattleSession.startMonster(
                    runtime = currentRuntime(),
                    monster = monster,
                    content = battleContent,
                    tier = op.tier ?: monster.tier,
                    mandatory = op.mandatory,
                )
            wait()
            return
        }

        if (current.step !is BattleSessionStep.Finished) {
            runtime = current.runtime
            party = runtime.activeParty
            wait()
            return
        }

        runtime = current.runtime.afterScriptBattle()
        party = runtime.activeParty
        battleSession = null
        if (runtime.realm.currentMode == RuntimeMode.GameOver) {
            stack.clear()
        } else {
            advanceTop()
        }
    }

    private fun runEnterDungeon(op: Op.EnterDungeon) {
        val handoff = WildernessDungeonHandoff(op.dungeon, op.levels)
        runtime =
            currentRuntime().withRealm(
                runtime.realm.copy(
                    currentMode = RuntimeMode.Dungeon,
                    currentTown = null,
                ),
            )
        party = runtime.activeParty
        dungeonHandoff = handoff
        advanceTop()
        engine.trace(EngineTraceEvent.DungeonHandoff(handoff))
    }

    private fun runTeleportParty(op: Op.TeleportParty) {
        val directionKey = eval(op.directionKey)
        val distance = eval(op.distance)
        runtime = currentRuntime().teleportParty(directionKey, distance, worldMap)
        party = runtime.activeParty
        advanceTop()
        engine.trace(EngineTraceEvent.TeleportParty(directionKey, distance))
    }

    private fun runDiscoverCave(op: Op.DiscoverCave) {
        val levels = eval(op.levels)
        val moveCost = eval(op.moveCost)
        val handoff = WildernessDungeonHandoff(op.dungeon, levels)
        party =
            party.copy(
                resources =
                    party.resources.copy(
                        timeLeft = (party.resources.timeLeft - moveCost).coerceAtLeast(0),
                    ),
            )
        runtime =
            currentRuntime().withRealm(
                runtime.realm.copy(
                    currentMode = RuntimeMode.Dungeon,
                    currentTown = null,
                ),
            )
        party = runtime.activeParty
        dungeonHandoff = handoff
        advanceTop()
        engine.trace(EngineTraceEvent.DiscoverCave(handoff, moveCost))
        engine.trace(EngineTraceEvent.DungeonHandoff(handoff))
    }

    private fun runRandomItemOffer(op: Op.RandomItemOffer) {
        engine.trace(EngineTraceEvent.RandomItemOffer(op.offer))
        val initial =
            randomItemOfferSession ?: run {
                val draw = runtime.drawRandom(0, op.offer.pool.items.lastIndex)
                runtime = draw.runtime
                RandomItemOfferSession.start(
                    op.offer,
                    party,
                    content = battleContent,
                    selectedIndex = draw.value,
                )
            }
        val result = RandomItemOfferSessionTraceRunner(terminal, engine).runUntilBlocked(initial)
        party = result.session.party
        runtime = runtime.withActiveParty(party)
        handleChildResult(
            result = result,
            clear = { randomItemOfferSession = null },
            keepWaiting = { waitingSession -> randomItemOfferSession = waitingSession },
        )
    }

    private fun runGrantReward(op: Op.GrantReward) {
        engine.trace(EngineTraceEvent.GrantReward(op.reward))
        val initial =
            rewardGrantSession ?: RewardGrantSession.start(
                reward = op.reward,
                party = party,
                gold = op.reward.gold?.let(::eval) ?: 0,
                content = battleContent,
            )
        val result = RewardGrantSessionTraceRunner(terminal, engine).runUntilBlocked(initial)
        party = result.session.party
        runtime = runtime.withActiveParty(party)
        handleChildResult(
            result = result,
            clear = { rewardGrantSession = null },
            keepWaiting = { waitingSession -> rewardGrantSession = waitingSession },
        )
    }

    private fun opsFor(block: ScriptBlockRef): List<Op> {
        var ops = rootOps
        block.segments.forEach { segment ->
            val op = ops.getOrNull(segment.opIndex) ?: return emptyList()
            ops =
                when (segment.branch) {
                    ScriptBranch.IfThen -> (op as? Op.If)?.thenOps.orEmpty()
                    ScriptBranch.IfElse -> (op as? Op.If)?.elseOps.orEmpty()
                    ScriptBranch.LoopBody -> (op as? Op.Loop)?.body.orEmpty()
                    ScriptBranch.ForEachMemberBody -> (op as? Op.ForEachMember)?.body.orEmpty()
                    ScriptBranch.ForEachMonsterBody -> (op as? Op.ForEachMonster)?.body.orEmpty()
                    ScriptBranch.SwitchCase ->
                        (op as? Op.Switch)
                            ?.cases
                            ?.getOrNull(segment.caseIndex ?: -1)
                            ?.body
                            .orEmpty()
                    ScriptBranch.MenuOption ->
                        (op as? Op.Menu)
                            ?.options
                            ?.firstOrNull { it.key == segment.key }
                            ?.body
                            .orEmpty()
                    ScriptBranch.MenuDefault -> (op as? Op.Menu)?.defaultOps.orEmpty()
                    ScriptBranch.TextAnswerMatch -> (op as? Op.AskText)?.matchOps.orEmpty()
                    ScriptBranch.TextAnswerMiss -> (op as? Op.AskText)?.missOps.orEmpty()
                }
        }
        return ops
    }

    private fun Op.ForEachMember.memberFrames(
        party: PartyState,
        frame: ScriptFrame,
        opIndex: Int,
    ): List<Pair<Int, ScriptBlockRef>> {
        val block = frame.block.child(opIndex, ScriptBranch.ForEachMemberBody)
        return party.members.mapIndexedNotNull { index, member ->
            val memberNumber = index + 1
            (memberNumber to block).takeIf { includeDead || !member.dead }
        }
    }

    private fun renderText(block: TextBlock): TextBlock =
        when (block) {
            is TextBlock.Ref,
            is TextBlock.Inline,
            -> block
            is TextBlock.Structured ->
                TextBlock.Inline(
                    block.lines.map { line ->
                        line.parts.joinToString(separator = "") { part ->
                            when (part) {
                                is TextPart.Literal -> part.text
                                is TextPart.IntValue -> eval(part.value).toString()
                                is TextPart.StringValue -> evalString(part.value)
                                is TextPart.RoleName ->
                                    worldText.roleName(part.role)
                                        ?: part.fallbackText()
                                is TextPart.RoleLocation ->
                                    worldText.roleLocation(part.role, currentRuntime())
                                        ?: part.fallbackText()
                                is TextPart.TownName -> worldText.townName(part.town)
                                is TextPart.FuzzyTownLocation ->
                                    worldText.townLocation(part.town, part.radius)
                                        ?: part.fallbackText()
                                is TextPart.LocationCoordinate -> locationCoordinate(part.location) ?: part.fallbackText()
                                // CPD-OFF
                                TextPart.OfferedItemName,
                                TextPart.OfferedItemPrice,
                                TextPart.SelectedItemName,
                                TextPart.ShopPrice,
                                TextPart.ShopCarryCap,
                                TextPart.HuntMemberName,
                                TextPart.HuntMemberSubjectPronoun,
                                TextPart.HuntFoodGained,
                                TextPart.PartyMemberMax,
                                TextPart.WildernessDungeonName,
                                TextPart.WildernessDungeonLevels,
                                TextPart.DungeonDepth,
                                TextPart.ConversationNpcName,
                                TextPart.CurrentTownName,
                                TextPart.TownTaxPercent,
                                TextPart.TownTaxAmount,
                                TextPart.CreationMemberNumber,
                                TextPart.CreationMemberName,
                                TextPart.CreationWeaponSkill,
                                TextPart.CreationHealth,
                                TextPart.CreationWisdom,
                                TextPart.CreationCharisma,
                                -> part.fallbackText()
                                // CPD-ON
                            }
                        }
                    },
                )
        }

    private fun evalString(ast: StrAst): String =
        when (ast) {
            is StrAst.NativeString ->
                when (ast.path) {
                    NativeAllowlist.partyMemberName.path ->
                        party.members
                            .getOrNull(evalStringIntArg(ast.args.single()) - 1)
                            ?.name
                            .orEmpty()
                    NativeAllowlist.partyPronounSubject.path ->
                        pronoun(evalStringIntArg(ast.args.single()), PronounCase.Subject)
                    NativeAllowlist.partyPronounObject.path ->
                        pronoun(evalStringIntArg(ast.args.single()), PronounCase.Object)
                    NativeAllowlist.partyPronounPossessive.path ->
                        pronoun(evalStringIntArg(ast.args.single()), PronounCase.Possessive)
                    NativeAllowlist.townNpcName.path -> {
                        val job =
                            ast.args
                                .singleOrNull()
                                ?.let(::evalStringLiteralArg)
                                ?.let(::NpcJobId)
                        job?.let { worldText.townNpcName(it, currentRuntime()) } ?: "{town.npc.name}"
                    }
                    NativeAllowlist.npcRoleName.path -> {
                        val role =
                            ast.args
                                .singleOrNull()
                                ?.let(::evalStringLiteralArg)
                                ?.let(::NpcRole)
                        role?.let(worldText::roleName) ?: "{npc.role.name}"
                    }
                    NativeAllowlist.npcRoleLocation.path -> {
                        val role =
                            ast.args
                                .singleOrNull()
                                ?.let(::evalStringLiteralArg)
                                ?.let(::NpcRole)
                        role?.let { worldText.roleLocation(it, currentRuntime()) } ?: "{npc.role.location}"
                    }
                    else -> "{${ast.path}}"
                }
        }

    private fun evalStringIntArg(arg: NativeStringArg): Int =
        when (arg) {
            is NativeStringArg.IntValue -> eval(arg.value)
            is NativeStringArg.Literal -> arg.value.toIntOrNull() ?: 0
        }

    private fun evalStringLiteralArg(arg: NativeStringArg): String =
        when (arg) {
            is NativeStringArg.IntValue -> eval(arg.value).toString()
            is NativeStringArg.Literal -> arg.value
        }

    private fun pronoun(
        memberNumber: Int,
        case: PronounCase,
    ): String {
        val gender = party.members.getOrNull(memberNumber - 1)?.gender ?: Gender.Male
        return gender.pronoun(case)
    }

    private fun locationCoordinate(location: LocationId): String? =
        currentRuntime()
            .locations
            .firstOrNull { it.location == location }
            ?.point
            ?.let { "${it.x}x${it.y}" }

    private val evaluator =
        object : IntAstEvaluator() {
            override fun local(name: String): Int = locals[name] ?: 0

            override fun random(
                lo: Int,
                hi: Int,
            ): Int {
                val draw = runtime.drawRandom(lo, hi)
                runtime = draw.runtime
                return draw.value
            }

            override fun memberField(
                member: Int,
                field: String,
            ): Int = this@StatefulScriptServiceRunner.memberField(member, field)

            override fun monsterField(
                monster: Int,
                field: String,
            ): Int = error("town/service scripts cannot read combat monster fields")

            override fun realmScalar(name: String): Int = this@StatefulScriptServiceRunner.realmScalar(name)

            override fun partyScalar(name: String): Int = this@StatefulScriptServiceRunner.partyScalar(name)

            override fun inventoryPoolCount(pool: ItemPoolId): Int = party.countInventoryItems(pool)

            override fun inventorySlotCost(slot: Int): Int =
                party
                    .inventoryItem(slot)
                    ?.let { item -> battleContent.itemRecords.firstOrNull { it.id == item }?.cost }
                    ?: 0

            override fun nativeInt(
                path: String,
                args: List<String>,
            ): Int = engine.readInt(EngineRequest(path, args))

            override fun flagIs(
                slug: String,
                native: String,
            ): Boolean = party.flags.any { it.flag.slug == slug && it.native == native }

            override fun milestoneAchieved(milestone: GameMilestoneId): Boolean = milestone in party.milestones

            override fun memberFlag(
                member: Int,
                name: String,
            ): Boolean = this@StatefulScriptServiceRunner.memberFlag(member, name)

            override fun monsterFlag(
                monster: Int,
                name: String,
            ): Boolean = error("town/service scripts cannot read combat monster flags")

            override fun runtimeFlag(name: String): Boolean = runtime.scriptRuntimeFlag(name)
        }

    private fun eval(ast: IntAst): Int = evaluator.int(ast)

    private fun eval(ast: BoolAst): Boolean = evaluator.bool(ast)

    private fun read(target: LValueAst): Int =
        when (target) {
            is LValueAst.PartyScalar -> partyScalar(target.name)
            is LValueAst.MemberField -> memberField(eval(target.member), target.field)
            is LValueAst.RealmScalar -> realmScalar(target.name)
            is LValueAst.MonsterField -> error("town/service scripts cannot read combat monster fields")
            is LValueAst.BattleFlag -> error("town/service scripts cannot read combat battle flags")
        }

    private fun set(
        target: LValueAst,
        value: Int,
    ) {
        when (target) {
            is LValueAst.PartyScalar -> party = party.withScriptPartyScalar(target.name, value)
            is LValueAst.MemberField -> party = party.withScriptMemberField(eval(target.member), target.field, value)
            is LValueAst.RealmScalar -> setRealmScalar(target.name, value)
            is LValueAst.MonsterField -> error("town/service scripts cannot write combat monster fields")
            is LValueAst.BattleFlag -> error("town/service scripts cannot write combat battle flags")
        }
    }

    private fun realmScalar(name: String): Int = runtime.scriptRealmScalar(name, realmInts)

    private fun setRealmScalar(
        name: String,
        value: Int,
    ) {
        runtime = runtime.withScriptRealmScalar(name, value)
            ?: runtime.also { realmInts[name] = value }
    }

    private fun partyScalar(name: String): Int = party.scriptPartyScalar(name)

    private fun memberField(
        memberNumber: Int,
        field: String,
    ): Int = party.scriptMemberField(memberNumber, field)

    private fun memberFlag(
        memberNumber: Int,
        name: String,
    ): Boolean = party.scriptMemberFlag(memberNumber, name)
}

private fun Op.AskText.accepts(raw: String): Boolean {
    val input = raw.normalizedTextAnswer(trim, caseSensitive, maxLength)
    return accepted.any { it.normalizedTextAnswer(trim, caseSensitive, maxLength = null) == input }
}

private fun ScriptBlockRef.diagnosticPath(): String =
    if (segments.isEmpty()) {
        "root"
    } else {
        segments.joinToString("/") { segment ->
            buildString {
                append(segment.opIndex).append(':').append(segment.branch.name)
                segment.caseIndex?.let { append(':').append(it) }
                segment.key?.let { append(':').append(it) }
            }
        }
    }

private fun Op.vmOpcode(): String =
    when (this) {
        is Op.Screen -> "Screen"
        is Op.Emit -> "Emit"
        is Op.EmitIndexed -> "EmitIndexed"
        is Op.WaitInput -> "WaitInput"
        is Op.Pause -> "Pause"
        is Op.View -> "View"
        is Op.LetInt -> "LetInt"
        is Op.BindText -> "BindText"
        is Op.Set -> "Set"
        is Op.Add -> "Add"
        is Op.Sub -> "Sub"
        is Op.AddClamped -> "AddClamped"
        is Op.SetFlag -> "SetFlag"
        is Op.AppendJournal -> "AppendJournal"
        is Op.If -> "If"
        is Op.Loop -> "Loop"
        is Op.Break -> "Break"
        is Op.Stop -> "Stop"
        is Op.Switch -> "Switch"
        is Op.Menu -> "Menu"
        is Op.AskKey -> "AskKey"
        is Op.AskMember -> "AskMember"
        is Op.AskInventorySlot -> "AskInventorySlot"
        is Op.AskText -> "AskText"
        is Op.AskMonster -> "AskMonster"
        is Op.ForEachMember -> "ForEachMember"
        is Op.ForEachMonster -> "ForEachMonster"
        is Op.Speak -> "Speak"
        is Op.TellRumour -> "TellRumour"
        is Op.RunScene -> "RunScene"
        is Op.Encounter -> "Encounter"
        is Op.EnterDungeon -> "EnterDungeon"
        Op.ConsumeActiveItem -> "ConsumeActiveItem"
        is Op.ClearInventorySlot -> "ClearInventorySlot"
        is Op.ConsumeInventoryPool -> "ConsumeInventoryPool"
        Op.ConsumeTorch -> "ConsumeTorch"
        is Op.TeleportParty -> "TeleportParty"
        is Op.DiscoverCave -> "DiscoverCave"
        is Op.GrantReward -> "GrantReward"
        is Op.RandomItemOffer -> "RandomItemOffer"
        is Op.ShopService -> "ShopService"
        is Op.AchieveMilestone -> "AchieveMilestone"
        is Op.NativeCall -> "NativeCall"
    }

private fun Op.vmArgs(): String =
    when (this) {
        is Op.Emit -> "text=${text.id}"
        is Op.EmitIndexed -> "pattern=${pattern.pattern} value=${value.diagnosticExpr()}"
        is Op.WaitInput -> listOfNotNull(reason?.let { "reason=$it" }).joinToString(" ")
        is Op.Pause -> "millis=$millis"
        is Op.View -> "command=${command::class.simpleName}"
        is Op.LetInt -> "name=$name value=${value.diagnosticExpr()}"
        is Op.BindText -> "name=$name value=$value"
        is Op.Set -> "target=${target.diagnosticTarget()} value=${value.diagnosticExpr()}"
        is Op.Add -> "target=${target.diagnosticTarget()} value=${value.diagnosticExpr()}"
        is Op.Sub -> "target=${target.diagnosticTarget()} value=${value.diagnosticExpr()}"
        is Op.AddClamped -> "target=${target.diagnosticTarget()} value=${value.diagnosticExpr()} max=${max.diagnosticExpr()}"
        is Op.SetFlag -> "flag=$slug state=$nativeValue"
        is Op.Break -> "signal=$signal"
        is Op.Stop -> "signal=$signal"
        is Op.Switch -> "expr=${expr.diagnosticExpr()} cases=${cases.size}"
        is Op.Menu -> "options=${options.joinToString("") { it.key.toString() }}"
        is Op.AskKey -> "var=$varName keys=${bindings.joinToString("") { it.label }}"
        is Op.AskMember -> "var=$varName filter=$filter accepting=$accepting"
        is Op.AskInventorySlot -> "var=$varName firstKey=$firstKey slots=${slots.first}..${slots.last} filter=$filter"
        is Op.AskText -> "accepted=${accepted.size} maxLength=$maxLength"
        is Op.AskMonster -> "var=$varName"
        is Op.ForEachMember -> "var=$varName includeDead=$includeDead"
        is Op.ForEachMonster -> "var=$varName includeDead=$includeDead"
        is Op.Speak -> "building=${building.slug}"
        is Op.TellRumour -> "table=${table.slug}"
        is Op.RunScene -> "scene=${scene.slug} bindings=${bindings.size}"
        is Op.Encounter -> "monster=${foe.slug} mandatory=$mandatory tier=${tier?.slug.orEmpty()}"
        is Op.EnterDungeon -> "dungeon=${dungeon.slug} levels=$levels"
        is Op.ClearInventorySlot -> "slot=${slot.diagnosticExpr()}"
        is Op.ConsumeInventoryPool -> "pool=${pool.slug} quantity=${quantity.diagnosticExpr()}"
        is Op.TeleportParty -> "directionKey=${directionKey.diagnosticExpr()} distance=${distance.diagnosticExpr()}"
        is Op.DiscoverCave -> "dungeon=${dungeon.slug} levels=${levels.diagnosticExpr()} moveCost=${moveCost.diagnosticExpr()}"
        is Op.GrantReward -> "gold=${reward.gold?.diagnosticExpr().orEmpty()} items=${reward.items.size}"
        is Op.RandomItemOffer -> "pool=${offer.pool.slug}"
        is Op.ShopService -> "building=${shop.building.slug}"
        is Op.AchieveMilestone -> "milestone=${milestone.slug}"
        is Op.NativeCall -> "path=$path args=${args.joinToString(",")}"
        is Op.Screen,
        is Op.AppendJournal,
        is Op.If,
        is Op.Loop,
        Op.ConsumeActiveItem,
        Op.ConsumeTorch,
        -> ""
    }

private fun IntAst.diagnosticExpr(): String = toString().replace(Regex("\\s+"), "")

private fun LValueAst.diagnosticTarget(): String =
    when (this) {
        is LValueAst.PartyScalar -> "party.$name"
        is LValueAst.MemberField -> "member.${member.diagnosticExpr()}.$field"
        is LValueAst.RealmScalar -> "realm.$name"
        is LValueAst.MonsterField -> "monster.${monster.diagnosticExpr()}.$field"
        is LValueAst.BattleFlag -> "battle.$name"
    }

private fun String.normalizedTextAnswer(
    trim: Boolean,
    caseSensitive: Boolean,
    maxLength: Int?,
): String {
    val limited = maxLength?.let { take(it) } ?: this
    val spaced = if (trim) limited.trim() else limited
    return if (caseSensitive) spaced else spaced.lowercase()
}
