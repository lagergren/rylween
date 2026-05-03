// Terminal-driven script bridge for resumable authored scenes.
// This is frontend-facing glue over backend-neutral script/session models.

package rylween.frontend.terminal

import rylween.dsl.*
import rylween.engine.*
import rylween.runtime.*

sealed interface ScriptBridgeStatus {
    object Finished : ScriptBridgeStatus
    object Waiting : ScriptBridgeStatus
}

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

interface ScriptBridge {
    fun run(
        service: BuildingServiceModel,
        scene: SceneModel,
        continuation: ScriptContinuation,
    ): ScriptBridgeResult
}

class StatefulScriptBridge(
    private val terminal: TerminalPort,
    private val engine: TraceEnginePort = TraceEnginePort(),
    private val speakContent: SpeakRuntimeContent = SpeakRuntimeContent(),
    private val battleContent: RuntimeContent = RuntimeContent(),
    private val worldMap: WorldMapGrid? = null,
    private val renderer: TerminalTextRenderer = TerminalTextRenderer(characters = speakContent.characters),
) : ScriptBridge {
    override fun run(
        service: BuildingServiceModel,
        scene: SceneModel,
        continuation: ScriptContinuation,
    ): ScriptBridgeResult {
        val eventStart = engine.events.size
        engine.trace(EngineTraceEvent.TownService(service, scene))
        val result = StatefulScriptServiceRunner(
            terminal,
            engine,
            scene.ops,
            continuation,
            speakContent,
            battleContent,
            worldMap,
            renderer,
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
}

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
) {
    private var runtime = continuation.runtime
    private var party = continuation.party
    private val locals = continuation.locals.toMutableMap()
    private val realmInts = continuation.realmScalars.toMutableMap()
    private val stack = continuation.stack.toMutableList()
    private var speakSession = continuation.speakSession
    private var battleSession = continuation.battleSession
    private var randomItemOfferSession = continuation.randomItemOfferSession
    private var rewardGrantSession = continuation.rewardGrantSession
    private val activeInventorySlot = continuation.activeInventorySlot
    private val worldText = TerminalWorldTextResolver(
        towns = battleContent.towns,
        characters = speakContent.characters.ifEmpty { battleContent.characters },
    )
    private var rejection: TerminalInputRejection? = null
    private var dungeonHandoff: WildernessDungeonHandoff? = null
    private var waiting = false
    private var opBudget = 4096
    private var writtenEventCount = engine.events.size

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

    private fun run(op: Op, frame: ScriptFrame) {
        val opIndex = frame.nextIndex
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
                    stack += ScriptFrame(
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
        party = party.withJournalLines(
            textRenderer.lines(rendered, TerminalTextContext(runtime = currentRuntime())),
        )
        advanceTop()
        engine.trace(EngineTraceEvent.AppendJournal(rendered))
    }

    private fun waitInput(reason: String?) {
        val prompt = textRenderer.pauseVerbIndexRange()?.let { range ->
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

    private fun askText(op: Op.AskText, frame: ScriptFrame, opIndex: Int) {
        engine.trace(EngineTraceEvent.AskText(renderText(op.prompt), op.accepted, op.maxLength))
        flushScriptOutput()
        val raw = terminal.readTextLine(op.maxLength) ?: return wait()
        val branch = if (op.accepts(raw)) {
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

    private fun pushChild(block: ScriptBlockRef, loop: Boolean = false) {
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

    private fun currentRuntime(): RuntimeSessionState =
        runtime.withActiveParty(party)

    private fun RuntimeSessionState.afterScriptBattle(): RuntimeSessionState {
        val mode = resumeModeAfterBattle ?: return this
        return if (realm.currentMode == RuntimeMode.GameOver) {
            this
        } else {
            withRealm(
                realm.copy(
                    currentMode = mode,
                    currentTown = null,
                    currentMap = worldMap?.id ?: realm.currentMap,
                ),
            )
        }
    }

    private fun continuation(runtime: RuntimeSessionState): ScriptContinuation =
        ScriptContinuation(
            runtime = runtime,
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
        val text = selection?.also { selected ->
            runtime = selected.runtime
            party = runtime.activeParty
        }?.rumour?.text ?: speakContent.noRumourText()
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
            val monster = checkNotNull(battleContent.monsters.firstOrNull { it.id == op.foe }) {
                "encounter references unknown monster ${op.foe.slug}"
            }
            battleSession = BattleSession.startMonster(
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
        runtime = currentRuntime().withRealm(
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
        party = party.copy(
            resources = party.resources.copy(
                timeLeft = (party.resources.timeLeft - moveCost).coerceAtLeast(0),
            ),
        )
        runtime = currentRuntime().withRealm(
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
        val initial = randomItemOfferSession ?: run {
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
        val initial = rewardGrantSession ?: RewardGrantSession.start(
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
            ops = when (segment.branch) {
                ScriptBranch.IfThen -> (op as? Op.If)?.thenOps.orEmpty()
                ScriptBranch.IfElse -> (op as? Op.If)?.elseOps.orEmpty()
                ScriptBranch.LoopBody -> (op as? Op.Loop)?.body.orEmpty()
                ScriptBranch.ForEachMemberBody -> (op as? Op.ForEachMember)?.body.orEmpty()
                ScriptBranch.ForEachMonsterBody -> (op as? Op.ForEachMonster)?.body.orEmpty()
                ScriptBranch.SwitchCase ->
                    (op as? Op.Switch)?.cases?.getOrNull(segment.caseIndex ?: -1)?.body.orEmpty()
                ScriptBranch.MenuOption ->
                    (op as? Op.Menu)?.options?.firstOrNull { it.key == segment.key }?.body.orEmpty()
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
            is TextBlock.Inline -> block
            is TextBlock.Structured -> TextBlock.Inline(
                block.lines.map { line ->
                    line.parts.joinToString(separator = "") { part ->
                        when (part) {
                            is TextPart.Literal -> part.text
                            is TextPart.IntValue -> eval(part.value).toString()
                            is TextPart.StringValue -> evalString(part.value)
                            is TextPart.RoleName -> worldText.roleName(part.role)
                                ?: part.fallbackText()
                            is TextPart.RoleLocation -> worldText.roleLocation(part.role, currentRuntime())
                                ?: part.fallbackText()
                            is TextPart.TownName -> worldText.townName(part.town)
                            is TextPart.FuzzyTownLocation -> worldText.townLocation(part.town, part.radius)
                                ?: part.fallbackText()
                            is TextPart.LocationCoordinate -> locationCoordinate(part.location) ?: part.fallbackText()
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
                            TextPart.ConversationNpcName -> part.fallbackText()
                        }
                    }
                },
            )
        }

    private fun evalString(ast: StrAst): String =
        when (ast) {
            is StrAst.NativeString -> when (ast.path) {
                NativeAllowlist.partyMemberName.path ->
                    party.members.getOrNull(evalStringIntArg(ast.args.single()) - 1)?.name.orEmpty()
                NativeAllowlist.partyPronounSubject.path ->
                    pronoun(evalStringIntArg(ast.args.single()), PronounCase.Subject)
                NativeAllowlist.partyPronounObject.path ->
                    pronoun(evalStringIntArg(ast.args.single()), PronounCase.Object)
                NativeAllowlist.partyPronounPossessive.path ->
                    pronoun(evalStringIntArg(ast.args.single()), PronounCase.Possessive)
                NativeAllowlist.townNpcName.path -> {
                    val job = ast.args.singleOrNull()?.let(::evalStringLiteralArg)?.let(::NpcJobId)
                    job?.let { worldText.townNpcName(it, currentRuntime()) } ?: "{town.npc.name}"
                }
                NativeAllowlist.npcRoleName.path -> {
                    val role = ast.args.singleOrNull()?.let(::evalStringLiteralArg)?.let(::NpcRole)
                    role?.let(worldText::roleName) ?: "{npc.role.name}"
                }
                NativeAllowlist.npcRoleLocation.path -> {
                    val role = ast.args.singleOrNull()?.let(::evalStringLiteralArg)?.let(::NpcRole)
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

    private fun pronoun(memberNumber: Int, case: PronounCase): String {
        val gender = party.members.getOrNull(memberNumber - 1)?.gender ?: Gender.Male
        return gender.pronoun(case)
    }

    private fun locationCoordinate(location: LocationId): String? =
        currentRuntime()
            .locations
            .firstOrNull { it.location == location }
            ?.point
            ?.let { "${it.x}x${it.y}" }

    private val evaluator = object : IntAstEvaluator() {
        override fun local(name: String): Int = locals[name] ?: 0
        override fun random(lo: Int, hi: Int): Int {
            val draw = runtime.drawRandom(lo, hi)
            runtime = draw.runtime
            return draw.value
        }
        override fun memberField(member: Int, field: String): Int =
            this@StatefulScriptServiceRunner.memberField(member, field)
        override fun monsterField(monster: Int, field: String): Int =
            error("town/service scripts cannot read combat monster fields")
        override fun realmScalar(name: String): Int = this@StatefulScriptServiceRunner.realmScalar(name)
        override fun partyScalar(name: String): Int = this@StatefulScriptServiceRunner.partyScalar(name)
        override fun inventoryPoolCount(pool: ItemPoolId): Int = party.countInventoryItems(pool)
        override fun inventorySlotCost(slot: Int): Int =
            party.inventoryItem(slot)
                ?.let { item -> battleContent.itemRecords.firstOrNull { it.id == item }?.cost }
                ?: 0
        override fun nativeInt(path: String, args: List<String>): Int =
            engine.readInt(EngineRequest(path, args))
        override fun flagIs(slug: String, native: String): Boolean =
            party.flags.any { it.flag.slug == slug && it.native == native }
        override fun milestoneAchieved(milestone: GameMilestoneId): Boolean =
            milestone in party.milestones
        override fun memberFlag(member: Int, name: String): Boolean =
            this@StatefulScriptServiceRunner.memberFlag(member, name)
        override fun monsterFlag(monster: Int, name: String): Boolean =
            error("town/service scripts cannot read combat monster flags")
        override fun runtimeFlag(name: String): Boolean =
            runtime.scriptRuntimeFlag(name)
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

    private fun set(target: LValueAst, value: Int) {
        when (target) {
            is LValueAst.PartyScalar -> party = party.withScriptPartyScalar(target.name, value)
            is LValueAst.MemberField -> party = party.withScriptMemberField(eval(target.member), target.field, value)
            is LValueAst.RealmScalar -> setRealmScalar(target.name, value)
            is LValueAst.MonsterField -> error("town/service scripts cannot write combat monster fields")
            is LValueAst.BattleFlag -> error("town/service scripts cannot write combat battle flags")
        }
    }

    private fun realmScalar(name: String): Int =
        runtime.scriptRealmScalar(name, realmInts)

    private fun setRealmScalar(name: String, value: Int) {
        runtime = runtime.withScriptRealmScalar(name, value)
            ?: runtime.also { realmInts[name] = value }
    }

    private fun partyScalar(name: String): Int =
        party.scriptPartyScalar(name)

    private fun memberField(memberNumber: Int, field: String): Int {
        return party.scriptMemberField(memberNumber, field)
    }

    private fun memberFlag(memberNumber: Int, name: String): Boolean {
        return party.scriptMemberFlag(memberNumber, name)
    }
}

private fun Op.AskText.accepts(raw: String): Boolean {
    val input = raw.normalizedTextAnswer(trim, caseSensitive, maxLength)
    return accepted.any { it.normalizedTextAnswer(trim, caseSensitive, maxLength = null) == input }
}

private fun String.normalizedTextAnswer(trim: Boolean, caseSensitive: Boolean, maxLength: Int?): String {
    val limited = maxLength?.let { take(it) } ?: this
    val spaced = if (trim) limited.trim() else limited
    return if (caseSensitive) spaced else spaced.lowercase()
}
