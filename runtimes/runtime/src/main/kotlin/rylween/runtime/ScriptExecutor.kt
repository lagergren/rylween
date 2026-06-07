/**
 * Direct compiled-script executor for the runtime.
 *
 * `RylweenRuntimeMain.kt` uses this class for scene roots loaded from
 * the J1 JSON bundle. The executor consumes `CompiledInstruction` rows,
 * compiled expressions, and typed host payloads directly; it does not call an
 * authored source interpreter or the Kotlin session adapters. Host gameplay
 * that is still a separate system, such as shops and standard battles, stays
 * behind runtime services in this package.
 */

package rylween.runtime

import rylween.compiled.CompiledBoolExpr
import rylween.compiled.CompiledCharacter
import rylween.compiled.CompiledCharacterPlacement
import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledInputBinding
import rylween.compiled.CompiledInstruction
import rylween.compiled.CompiledIntExpr
import rylween.compiled.CompiledInventorySlot
import rylween.compiled.CompiledItemRecord
import rylween.compiled.CompiledLValue
import rylween.compiled.CompiledMenuOption
import rylween.compiled.CompiledNativeStringArg
import rylween.compiled.CompiledNpcVisit
import rylween.compiled.CompiledOpcode
import rylween.compiled.CompiledOperand
import rylween.compiled.CompiledRandomItemOffer
import rylween.compiled.CompiledRuntimeTextPart
import rylween.compiled.CompiledScriptProgram
import rylween.compiled.CompiledScriptReward
import rylween.compiled.CompiledShopService
import rylween.compiled.CompiledStringExpr
import rylween.compiled.CompiledSwitchCase
import rylween.compiled.CompiledTextAnswer
import rylween.compiled.CompiledTextBlock
import rylween.compiled.CompiledViewCommand
import rylween.compiled.CompiledWorldPosition
import rylween.compiled.ExternalRumorEntry
import rylween.compiled.ExternalRuntimeCommand
import rylween.compiled.ExternalRuntimeSpeak
import rylween.compiled.ExternalRuntimeSystems
import rylween.compiled.ExternalRuntimeTextBlock
import rylween.compiled.ExternalRuntimeTextPart
import rylween.compiled.RuntimeCommandActionId
import rylween.compiled.RuntimeCommandGroupId
import rylween.compiled.applyCompiledBinary
import rylween.compiled.compareCompiled
import rylween.dsl.InventorySlotChoiceFilter
import rylween.dsl.MemberChoiceFilter
import rylween.dsl.NativeAllowlist
import rylween.dsl.PartyMemberSelection
import rylween.dsl.PronounCase
import rylween.dsl.ScriptControlSignal
import rylween.dsl.impl.ChitchatPromptAudience
import rylween.dsl.impl.Gender
import rylween.dsl.impl.GridPoint
import rylween.dsl.impl.ItemStorageKind
import rylween.dsl.impl.PcClass
import rylween.dsl.impl.QuestionTopic
import rylween.dsl.impl.Resource
import rylween.dsl.impl.RewardOverflowPolicy
import rylween.dsl.impl.RuntimeMode
import rylween.dsl.impl.of
import rylween.dsl.impl.sourceId
import rylween.dsl.pronoun

private const val STANDARD_TOWN_RUMOR_TABLE = "standard_town"
private const val SPEAK_NO_NPC_SCREEN = "speak.no_npc"
private const val SPEAK_NO_RUMOR_SCREEN = "speak.no_rumor"
private const val SCRIPT_ROOT_KIND_DIALOGUE_BRANCH = "DialogueBranch"
private const val SCRIPT_ROOT_KIND_QUESTION_ACTION = "QuestionAction"

private val SPEAK_COMMAND_GROUP = RuntimeCommandGroupId.Speak
private val SPEAK_QUESTION_COMMAND_GROUP = RuntimeCommandGroupId.SpeakQuestions

/** Executes one compiled script root against [RuntimeState]. */
internal class ScriptExecutor(
    private val content: CompiledContentBundle,
    private val systems: ExternalRuntimeSystems,
    private val input: RuntimeInputPort,
    private val screen: RuntimeScreenPort,
    initialRuntime: RuntimeState,
    initialLocals: Map<String, Int> = emptyMap(),
    private var activeInventorySlot: Int? = null,
    private val log: RuntimeLog,
) {
    private var runtime: RuntimeState = initialRuntime
    private var locals: Map<String, Int> = initialLocals
    private var textBindings: Map<String, String> = emptyMap()
    private var currentTextLines: List<String> = emptyList()
    private val pausePrompts = PausePrompts(systems.prompts)

    fun execute(program: CompiledScriptProgram): RuntimeState {
        executeBlock(
            instructions = program.instructions,
            context =
                ScriptBlockContext(
                    program = program,
                    blockPath = emptyList(),
                    parentFrames = emptyList(),
                    insideLoop = false,
                ),
        )
        return runtime
    }

    fun execute(continuation: ScriptContinuationState): RuntimeState {
        val program =
            content.scripts.roots.firstOrNull { root -> root.rootHandle == continuation.root }
                ?: error("missing compiled script root ${content.slug(continuation.root)}")
        locals = continuation.locals
        textBindings = continuation.textBindings
        activeInventorySlot = continuation.activeInventorySlot
        executeFrames(program, continuation.frames)
        return runtime
    }

    private fun executeFrames(
        program: CompiledScriptProgram,
        frames: List<ScriptFrameState>,
    ): ScriptSignal {
        var pending = frames
        while (pending.isNotEmpty()) {
            val frame = pending.first()
            val signal =
                executeBlock(
                    instructions = program.blockAt(frame.blockPath),
                    context =
                        ScriptBlockContext(
                            program = program,
                            blockPath = frame.blockPath,
                            parentFrames = pending.drop(1),
                            insideLoop = frame.insideLoop,
                        ),
                    startIndex = frame.nextIndex,
                )
            if (signal != ScriptSignal.Continue) return signal
            pending = pending.drop(1)
        }
        return ScriptSignal.Continue
    }

    private fun executeBlock(
        instructions: List<CompiledInstruction>,
        context: ScriptBlockContext,
        startIndex: Int = 0,
    ): ScriptSignal {
        for (index in startIndex until instructions.size) {
            val signal = execute(instructions[index], context, index, instructions.size)
            if (signal != ScriptSignal.Continue) return signal
        }
        return ScriptSignal.Continue
    }

    private fun execute(
        instruction: CompiledInstruction,
        context: ScriptBlockContext,
        instructionIndex: Int,
        instructionCount: Int,
    ): ScriptSignal =
        when (instruction.opcode) {
            CompiledOpcode.Screen ->
                continueAfter {
                    writeText(instruction.textBlockOperand(0).renderScriptLines())
                }

            CompiledOpcode.Emit ->
                continueAfter {
                    screen.writeText(runtimeDiagnosticText("[text:${content.slug(instruction.handleOperand(0))}]"))
                }

            CompiledOpcode.WaitInput ->
                continueAfter {
                    waitForCurrentText()
                }
            CompiledOpcode.Pause -> continueAfter { input.pause(instruction.integerOperand(0)) }
            CompiledOpcode.View -> continueAfter { writeViewCommand(instruction.viewOperand(0).toRuntimeViewCommand()) }
            CompiledOpcode.LetInt ->
                continueAfter {
                    locals = locals + (content.strings.value(instruction.stringIdOperand(0)) to int(instruction.intOperand(1)))
                }

            CompiledOpcode.BindText ->
                continueAfter {
                    textBindings =
                        textBindings + (content.strings.value(instruction.stringIdOperand(0)) to string(instruction.stringExprOperand(1)))
                }

            CompiledOpcode.Set -> continueAfter { write(instruction.lvalueOperand(0), int(instruction.intOperand(1))) }
            CompiledOpcode.Add ->
                continueAfter {
                    val target = instruction.lvalueOperand(0)
                    write(target, read(target) + int(instruction.intOperand(1)))
                }

            CompiledOpcode.Sub ->
                continueAfter {
                    val target = instruction.lvalueOperand(0)
                    write(target, read(target) - int(instruction.intOperand(1)))
                }

            CompiledOpcode.AddClamped ->
                continueAfter {
                    val target = instruction.lvalueOperand(0)
                    val next = read(target) + int(instruction.intOperand(1))
                    write(target, next.coerceAtMost(int(instruction.intOperand(2))))
                }

            CompiledOpcode.SetFlag ->
                continueAfter {
                    runtime = runtime.withParty(runtime.party.withFlagState(instruction.handleOperand(0), instruction.handleOperand(1)))
                }

            CompiledOpcode.AppendJournal ->
                continueAfter {
                    runtime =
                        runtime.withParty(
                            runtime.party.copy(
                                journal = runtime.party.journal + instruction.textBlockOperand(0).renderJournalLines(),
                            ),
                        )
                }

            CompiledOpcode.If ->
                bool(instruction.boolOperand(0)).let { branch ->
                    val operandIndex = if (branch) 1 else 2
                    executeBlock(
                        instructions = instruction.instructionsOperand(operandIndex),
                        context =
                            context.childContext(
                                instructionIndex = instructionIndex,
                                instructionCount = instructionCount,
                                childPath = listOf(instructionIndex, operandIndex),
                                insideLoop = context.insideLoop,
                            ),
                    )
                }

            CompiledOpcode.Loop -> executeLoop(instruction.instructionsOperand(0), context, instructionIndex, instructionCount)
            CompiledOpcode.Break ->
                instruction
                    .scriptSignal(
                        default = ScriptControlSignal.BreakNearestLoop,
                    ).toRuntimeSignal(context.insideLoop)

            CompiledOpcode.Stop -> instruction.scriptSignal(default = ScriptControlSignal.StopScript).toRuntimeSignal(context.insideLoop)
            CompiledOpcode.Switch -> executeSwitch(instruction, int(instruction.intOperand(0)), context, instructionIndex, instructionCount)
            CompiledOpcode.Menu -> executeMenu(instruction, context, instructionIndex, instructionCount)
            CompiledOpcode.AskKey -> executeAskKey(instruction)
            CompiledOpcode.AskMember -> executeAskMember(instruction)
            CompiledOpcode.AskInventorySlot -> executeAskInventorySlot(instruction)
            CompiledOpcode.AskText -> executeAskText(instruction, context, instructionIndex, instructionCount)
            CompiledOpcode.AskMonster -> continueAfter { locals = locals + (content.strings.value(instruction.stringIdOperand(1)) to 0) }
            CompiledOpcode.ForEachMember -> executeForEachMember(instruction, context, instructionIndex, instructionCount)
            CompiledOpcode.ForEachMonster -> ScriptSignal.Continue
            CompiledOpcode.Speak -> executeSpeak(instruction.handleOperand(0), context.insideLoop)
            CompiledOpcode.TellRumor -> continueAfter { appendRumor(instruction.handleOperand(0)) }
            CompiledOpcode.RunScene -> executeScene(instruction.handleOperand(0), context.insideLoop)
            CompiledOpcode.Encounter -> executeEncounter(instruction.handleOperand(0), instruction.booleanOperand(1))
            CompiledOpcode.EnterDungeon ->
                stopAfter {
                    runtime =
                        runtime.enterDungeon(
                            content = content,
                            dungeon = instruction.handleOperand(0),
                            levels = instruction.integerOperand(1),
                            postDungeon = context.continuationAfter(instructionIndex, instructionCount),
                            log = log,
                        )
                }

            CompiledOpcode.ConsumeActiveItem ->
                continueAfter {
                    activeInventorySlot?.let { slot ->
                        runtime = runtime.withParty(runtime.party.withInventoryItem(slot, null))
                    }
                }

            CompiledOpcode.ClearInventorySlot ->
                continueAfter { runtime = runtime.withParty(runtime.party.withInventoryItem(int(instruction.intOperand(0)), null)) }

            CompiledOpcode.ConsumeInventoryPool ->
                continueAfter {
                    runtime =
                        runtime.withParty(
                            runtime.party.consumeInventoryPool(
                                pool = instruction.handleOperand(0),
                                quantity = int(instruction.intOperand(1)),
                            ),
                        )
                }

            CompiledOpcode.TeleportParty ->
                continueAfter {
                    val direction = teleportDelta(int(instruction.intOperand(0)))
                    val steps = int(instruction.intOperand(1)).coerceIn(1, 9) * 2
                    val destination =
                        direction?.let { delta ->
                            (1..steps).fold(runtime.party.position) { point, _ ->
                                GridPoint(point.x + delta.x, point.y + delta.y)
                            }
                        } ?: runtime.party.position
                    runtime =
                        runtime.withParty(
                            runtime.party.copy(
                                position = destination,
                            ),
                        )
                }

            CompiledOpcode.DiscoverCave ->
                stopAfter {
                    val moveCost = int(instruction.intOperand(2))
                    runtime =
                        runtime
                            .withParty(
                                runtime.party.copy(
                                    resources =
                                        runtime.party.resources.copy(
                                            timeLeft = (runtime.party.resources.timeLeft - moveCost).coerceAtLeast(0),
                                        ),
                                ),
                            ).enterDungeon(
                                content = content,
                                dungeon = instruction.handleOperand(0),
                                levels = int(instruction.intOperand(1)),
                                postDungeon = context.continuationAfter(instructionIndex, instructionCount),
                                log = log,
                            )
                }

            CompiledOpcode.GrantReward -> continueAfter { runtime = runtime.withParty(applyReward(instruction.rewardOperand(0))) }
            CompiledOpcode.RandomItemOffer ->
                continueAfter {
                    runtime =
                        runtime.withParty(runRandomItemOffer(instruction.randomItemOfferOperand(0)))
                }

            CompiledOpcode.ShopService ->
                continueAfter {
                    runtime =
                        ShopService(content, systems, input, screen, log)
                            .run(instruction.shopServiceOperand(0), runtime)
                }

            CompiledOpcode.AchieveMilestone ->
                continueAfter {
                    val milestone = instruction.handleOperand(0)
                    runtime = runtime.withParty(runtime.party.copy(milestones = (runtime.party.milestones + milestone).distinct()))
                }
        }

    private fun continueAfter(body: () -> Unit): ScriptSignal {
        body()
        return ScriptSignal.Continue
    }

    private fun stopAfter(body: () -> Unit): ScriptSignal {
        body()
        return ScriptSignal.StopScript
    }

    private fun writeText(lines: List<String>) {
        currentTextLines = lines
        screen.writeText(lines)
    }

    private fun waitForCurrentText() {
        screen.writeText(currentTextLines.withPausePrompt(pausePrompts))
        input.readCommand()
    }

    private fun writeViewCommand(command: RuntimeViewCommand) {
        if (command == RuntimeViewCommand.ClearText) currentTextLines = emptyList()
        screen.writeViewCommand(command)
    }

    private fun executeLoop(
        instructions: List<CompiledInstruction>,
        parentContext: ScriptBlockContext,
        instructionIndex: Int,
        instructionCount: Int,
    ): ScriptSignal {
        val context =
            parentContext.childContext(
                instructionIndex = instructionIndex,
                instructionCount = instructionCount,
                childPath = listOf(instructionIndex, 0),
                insideLoop = true,
            )
        while (true) {
            when (val signal = executeBlock(instructions, context)) {
                ScriptSignal.Continue -> Unit
                ScriptSignal.BreakNearestLoop -> return ScriptSignal.Continue
                ScriptSignal.StopScript -> return signal
            }
            if (input.exhausted) return ScriptSignal.StopScript
        }
    }

    private fun executeSwitch(
        instruction: CompiledInstruction,
        value: Int,
        context: ScriptBlockContext,
        instructionIndex: Int,
        instructionCount: Int,
    ): ScriptSignal {
        val cases = instruction.switchCasesOperand(1)
        val caseIndex = cases.indexOfFirst { case -> case.match == value || case.match == null }
        if (caseIndex < 0) return ScriptSignal.Continue
        return executeBlock(
            instructions = cases[caseIndex].body,
            context =
                context.childContext(
                    instructionIndex = instructionIndex,
                    instructionCount = instructionCount,
                    childPath = listOf(instructionIndex, 1, caseIndex),
                    insideLoop = context.insideLoop,
                ),
        )
    }

    private fun executeMenu(
        instruction: CompiledInstruction,
        context: ScriptBlockContext,
        instructionIndex: Int,
        instructionCount: Int,
    ): ScriptSignal {
        val options = instruction.menuOptionsOperand(1)
        val promptLines = instruction.textBlockOperand(0).renderScriptLines()
        val baseLines =
            when {
                promptLines.onlyListMenuOptions(options) && currentTextLines.isNotEmpty() -> currentTextLines + promptLines
                else -> promptLines.withGeneratedMenuOptions(options)
            }
        writeText(baseLines)
        var choice = input.readCommand() ?: return ScriptSignal.StopScript
        val defaultBody = instruction.instructionsOperand(2)
        while (true) {
            if (choice == '!') {
                screen.redrawCurrentView(baseLines)
                currentTextLines = baseLines
                choice = input.readCommand() ?: return ScriptSignal.StopScript
                continue
            }
            val optionIndex = options.indexOfFirst { menu -> menu.binding.matchesInput(content, choice) }
            val selected = options.getOrNull(optionIndex)
            if (selected != null || defaultBody.isNotEmpty()) {
                val body = selected?.body ?: defaultBody
                return executeBlock(
                    instructions = body,
                    context =
                        context.childContext(
                            instructionIndex = instructionIndex,
                            instructionCount = instructionCount,
                            childPath = selected?.let { listOf(instructionIndex, 1, optionIndex) } ?: listOf(instructionIndex, 2),
                            insideLoop = context.insideLoop,
                        ),
                )
            }
            screen.redrawCurrentView(baseLines)
            currentTextLines = baseLines
            choice = input.readCommand() ?: return ScriptSignal.StopScript
        }
    }

    private fun executeAskKey(instruction: CompiledInstruction): ScriptSignal {
        val bindings = instruction.inputBindingsOperand(2)
        val lines = instruction.textBlockOperand(0).renderScriptLines()
        writeText(lines)
        var choice = input.readCommand() ?: return ScriptSignal.StopScript
        while (true) {
            if (choice == '!') {
                screen.redrawCurrentView(lines)
                currentTextLines = lines
                choice = input.readCommand() ?: return ScriptSignal.StopScript
                continue
            }
            val binding =
                bindings.firstOrNull { current -> current.matchesInput(content, choice) }
            if (binding != null) {
                locals = locals + (content.strings.value(instruction.stringIdOperand(1)) to binding.promptValue())
                return ScriptSignal.Continue
            }
            screen.redrawCurrentView(lines)
            currentTextLines = lines
            choice = input.readCommand() ?: return ScriptSignal.StopScript
        }
    }

    private fun List<CompiledMenuOption>.menuOptionLines(): List<String> =
        map { option -> option.binding.mnemonicLine(content, content.strings.value(option.binding.labelString)) }

    private fun List<String>.alreadyListMenuOptions(options: List<CompiledMenuOption>): Boolean =
        options.all { option ->
            val key = option.binding.primaryInputLabel(content)
            any { line -> "($key)" in line || "$key." in line }
        }

    private fun List<String>.withGeneratedMenuOptions(options: List<CompiledMenuOption>): List<String> =
        takeIf { lines -> lines.alreadyListMenuOptions(options) } ?: (this + options.menuOptionLines())

    private fun List<String>.onlyListMenuOptions(options: List<CompiledMenuOption>): Boolean {
        val nonBlank = filter(String::isNotBlank)
        return nonBlank.isNotEmpty() &&
            nonBlank.all { line ->
                options.any { option ->
                    val key = option.binding.primaryInputLabel(content)
                    "($key)" in line || line.trimStart().startsWith("$key.")
                }
            }
    }

    private fun executeAskMember(instruction: CompiledInstruction): ScriptSignal {
        val accepting = enumValueOf<MemberChoiceFilter>(instruction.enumOperand(3))
        val choices = runtime.party.memberChoices(accepting)
        if (choices.isEmpty()) {
            locals = locals + (content.strings.value(instruction.stringIdOperand(1)) to 0)
            return ScriptSignal.Continue
        }
        val lines = instruction.textBlockOperand(0).renderScriptLines().withChoiceGap() + memberChoiceLines(choices)
        screen.writeText(lines)
        val member =
            input.readValidDigitChoice(
                screen = screen,
                lines = lines,
                validDigits = choices.toSet(),
            )
        locals = locals + (content.strings.value(instruction.stringIdOperand(1)) to (member ?: 0))
        return ScriptSignal.Continue
    }

    private fun executeAskInventorySlot(instruction: CompiledInstruction): ScriptSignal {
        val firstKey = instruction.characterOperand(2)
        val slots = instruction.integerOperand(3)..instruction.integerOperand(4)
        val filter = enumValueOf<InventorySlotChoiceFilter>(instruction.enumOperand(5))
        val choices = runtime.party.inventorySlotChoices(filter, slots, firstKey)
        if (choices.isEmpty()) {
            locals = locals + (content.strings.value(instruction.stringIdOperand(1)) to 0)
            return ScriptSignal.Continue
        }
        val lines = instruction.textBlockOperand(0).renderScriptLines() + inventoryChoiceLines(choices).withReturnCancelLine()
        screen.writeText(lines)
        val choice =
            input.readValidChoice(screen, lines) { candidate ->
                candidate == '\n' ||
                    choices.any { current -> candidate.uppercaseChar() == current.key.uppercaseChar() }
            }
        val slot =
            when (choice?.uppercaseChar()) {
                null -> 0
                else -> choices.firstOrNull { current -> choice.uppercaseChar() == current.key.uppercaseChar() }?.slot ?: 0
            }
        locals = locals + (content.strings.value(instruction.stringIdOperand(1)) to slot)
        return ScriptSignal.Continue
    }

    private fun List<String>.withReturnCancelLine(): List<String> {
        val returnLabel = systems.prompts.returnKeyLabel()
        val hasReturn =
            any { line ->
                line.contains(returnLabel, ignoreCase = true) ||
                    line.contains(systems.prompts.returnKeyName, ignoreCase = true)
            }
        return takeIf { hasReturn } ?: (this + systems.prompts.returnLeavesLine())
    }

    private fun executeAskText(
        instruction: CompiledInstruction,
        context: ScriptBlockContext,
        instructionIndex: Int,
        instructionCount: Int,
    ): ScriptSignal {
        val answer = instruction.textAnswerOperand(1)
        screen.writeText(instruction.textBlockOperand(0).renderScriptLines())
        val raw = input.readText(answer.maxLength) ?: return ScriptSignal.StopScript
        val accepted = answer.accepts(raw)
        return executeBlock(
            instructions = if (accepted) answer.matchOps else answer.missOps,
            context =
                context.childContext(
                    instructionIndex = instructionIndex,
                    instructionCount = instructionCount,
                    childPath = listOf(instructionIndex, 1, if (accepted) 0 else 1),
                    insideLoop = context.insideLoop,
                ),
        )
    }

    private fun executeForEachMember(
        instruction: CompiledInstruction,
        context: ScriptBlockContext,
        instructionIndex: Int,
        instructionCount: Int,
    ): ScriptSignal {
        val name = content.strings.value(instruction.stringIdOperand(0))
        val includeDead = instruction.booleanOperand(1)
        val bodyContext =
            context.childContext(
                instructionIndex = instructionIndex,
                instructionCount = instructionCount,
                childPath = listOf(instructionIndex, 2),
                insideLoop = context.insideLoop,
            )
        runtime.party.members.forEachIndexed { index, member ->
            if (includeDead || !member.dead) {
                val previousLoopValue = locals[name]
                locals = locals + (name to index + 1)
                val signal = executeBlock(instruction.instructionsOperand(2), bodyContext)
                locals =
                    previousLoopValue
                        ?.let { value -> locals + (name to value) }
                        ?: (locals - name)
                if (signal != ScriptSignal.Continue) return signal
            }
        }
        return ScriptSignal.Continue
    }

    private fun executeScene(
        scene: CompiledHandleRef,
        insideLoop: Boolean,
    ): ScriptSignal {
        val program =
            content.scripts.roots.firstOrNull { root ->
                root.root.kind.name == "Scene" && root.root.slug == content.slug(scene)
            } ?: return ScriptSignal.Continue
        return executeProgram(program, insideLoop)
    }

    private fun executeSpeak(
        building: CompiledHandleRef,
        insideLoop: Boolean,
    ): ScriptSignal {
        val speak = systems.speak ?: return ScriptSignal.Continue
        val conversation = speak.standardConversation ?: return ScriptSignal.Continue
        val npcs = charactersFor(building)
        if (npcs.isEmpty()) {
            screen.writeText(speak.screen(SPEAK_NO_NPC_SCREEN)?.renderExternal().orEmpty())
            return ScriptSignal.Continue
        }

        val chooseNpcLines = conversation.prompts.chooseNpc.renderExternal() + npcChoiceLines(npcs)
        screen.writeText(chooseNpcLines)
        val npc =
            input
                .readValidDigitChoice(
                    screen = screen,
                    lines = chooseNpcLines,
                    validDigits = (1..npcs.size).toSet(),
                )?.let { index -> npcs.getOrNull(index - 1) }
                ?: return ScriptSignal.StopScript
        runtime = runtime.withParty(runtime.party.withNpcVisit(npc.id, visited = true))
        val branchSignal = executeDialogueBranch(npc, insideLoop)
        if (branchSignal != ScriptSignal.Continue) return branchSignal

        var rumorTold = false
        while (true) {
            val lines = conversation.prompts.mainMenu.renderExternal(npc) + systems.commands.mnemonicMenuLines(SPEAK_COMMAND_GROUP)
            screen.writeText(lines)
            val command =
                input
                    .readValidCommandChoice(
                        screen = screen,
                        lines = lines,
                        commands = systems.commands[SPEAK_COMMAND_GROUP.id].orEmpty(),
                    )
                    ?: return ScriptSignal.StopScript
            val signal =
                when (command.action) {
                    RuntimeCommandActionId.SpeakChitchat.id ->
                        continueAfter {
                            chitchat(speak, npc)
                            input.pause(systems.pacing.speak.responsePauseMs)
                        }

                    RuntimeCommandActionId.SpeakQuestion.id -> executeQuestionMenu(speak, npc, insideLoop)
                    RuntimeCommandActionId.SpeakRumor.id ->
                        continueAfter {
                            if (rumorTold) {
                                writeText(speak.noRumorLines())
                            } else {
                                tellStandardTownRumor()
                                rumorTold = true
                            }
                            waitForCurrentText()
                        }

                    RuntimeCommandActionId.SpeakBye.id -> return ScriptSignal.Continue
                    else -> ScriptSignal.Continue
                }
            if (signal != ScriptSignal.Continue) return signal
        }
    }

    private fun executeQuestionMenu(
        speak: ExternalRuntimeSpeak,
        npc: CompiledCharacter,
        insideLoop: Boolean,
    ): ScriptSignal {
        val lines = systems.commands.mnemonicMenuLines(SPEAK_QUESTION_COMMAND_GROUP)
        screen.writeText(lines)
        val command =
            input
                .readValidCommandChoice(
                    screen = screen,
                    lines = lines,
                    commands = systems.commands[SPEAK_QUESTION_COMMAND_GROUP.id].orEmpty(),
                )
                ?: return ScriptSignal.StopScript
        val topic = command.questionTopic() ?: return ScriptSignal.Continue
        if (topic == QuestionTopic.Nothing) return ScriptSignal.Continue
        val action =
            speak.questionActions.firstOrNull { candidate ->
                candidate.topic == topic && candidate.job == content.slug(npc.job)
            }
        if (action != null) return executeQuestionAction(action.scriptRoot, insideLoop)
        screen.writeText(speak.questionReply(topic, npc)?.renderExternal(npc) ?: speak.noRumorLines())
        input.pause(systems.pacing.speak.responsePauseMs)
        return ScriptSignal.Continue
    }

    private fun executeDialogueBranch(
        npc: CompiledCharacter,
        insideLoop: Boolean,
    ): ScriptSignal {
        val dialoguePrefix = "dialogue.${content.slug(npc.id)}."
        val dialogueRoots =
            content.scripts.roots.filter { program ->
                program.root.kind.name == SCRIPT_ROOT_KIND_DIALOGUE_BRANCH &&
                    program.root.slug.startsWith(dialoguePrefix)
            }
        val root =
            dialogueRoots.firstOrNull { program ->
                val branchState = program.root.slug.removePrefix(dialoguePrefix)
                branchState != "default" && matchesBranchState(branchState)
            } ?: dialogueRoots.firstOrNull { program ->
                program.root.slug == "${dialoguePrefix}default"
            }
        return root?.let { program -> executeProgram(program, insideLoop) } ?: ScriptSignal.Continue
    }

    private fun executeQuestionAction(
        slug: String,
        insideLoop: Boolean,
    ): ScriptSignal =
        content.scripts.roots
            .firstOrNull { program ->
                program.root.kind.name == SCRIPT_ROOT_KIND_QUESTION_ACTION && program.root.slug == slug
            }?.let { program -> executeProgram(program, insideLoop) }
            ?: ScriptSignal.Continue

    private fun executeProgram(
        program: CompiledScriptProgram,
        insideLoop: Boolean,
    ): ScriptSignal =
        executeBlock(
            instructions = program.instructions,
            context =
                ScriptBlockContext(
                    program = program,
                    blockPath = emptyList(),
                    parentFrames = emptyList(),
                    insideLoop = insideLoop,
                ),
        )

    private fun chitchat(
        speak: ExternalRuntimeSpeak,
        npc: CompiledCharacter,
    ) {
        val subjects = speak.standardConversation?.chitchatSubjects.orEmpty()
        val subject = subjects.randomElementOrNull("speak.chitchat.subject") ?: return
        val prompt = speak.textTables.chitchatPrompts.firstOrNull { prompt -> prompt.subject == subject }
        val variants = prompt?.variants.orEmpty()
        val promptVariant =
            variants.firstOrNull { variant -> variant.audience.matches(npc.gender) }
                ?: variants.firstOrNull()
        val reply =
            speak.textTables.chitchatReplies
                .firstOrNull { reply -> reply.subject == subject }
                ?.replies
                ?.firstOrNull { reply -> reply.job == content.slug(npc.job) }
                ?.text
        val promptLines = promptVariant?.text?.renderExternal(npc).orEmpty()
        val replyLines = reply?.renderExternal(npc).orEmpty()
        screen.writeText(promptLines + "")
        input.pause(systems.pacing.speak.promptReplyPauseMs)
        screen.writeText(promptLines + "" + replyLines)
    }

    private fun appendRumor(table: CompiledHandleRef) {
        writeRumor(content.slug(table), prefixLines = currentTextLines)
    }

    private fun tellStandardTownRumor() {
        writeRumor(STANDARD_TOWN_RUMOR_TABLE, prefixLines = emptyList())
    }

    private fun writeRumor(
        tableSlug: String,
        prefixLines: List<String>,
    ) {
        val speak = systems.speak ?: return
        val entries =
            speak.textTables.rumorTables
                .firstOrNull { table -> table.id == tableSlug }
                ?.entries
                ?.filter { entry ->
                    entry.excludedBy.none { state ->
                        flagStateRef("${state.flag}.${state.native}")?.let(::matchesFlagState) == true
                    }
                }.orEmpty()
        val entry = entries.randomElementOrNull("speak.rumor.entry")
        if (entry == null) {
            writeText(speak.noRumorLines())
            return
        }
        val lines = entry.text.renderExternal()
        appendRumorJournalHint(entry)
        writeText(prefixLines + lines)
    }

    private fun appendRumorJournalHint(entry: ExternalRumorEntry) {
        if (!entry.text.hasExactJournalLocation()) return
        val journalLine =
            entry.text
                .renderExternalForJournal()
                .joinToString(separator = " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        if (journalLine.isNotBlank() && journalLine !in runtime.party.journal) {
            runtime = runtime.withParty(runtime.party.copy(journal = runtime.party.journal + journalLine))
        }
    }

    private fun runEncounter(
        monster: CompiledHandleRef,
        mandatory: Boolean,
    ) {
        val battle =
            BattleRunner(content, systems.combat, systems.pacing.battle, input, screen, log, pausePrompts)
                .runMonster(runtime, monster, mandatory = mandatory)
        runtime = battle.runtime
        if (battle.messages.isNotEmpty()) screen.writeText(battle.messages)
    }

    private fun executeEncounter(
        monster: CompiledHandleRef,
        mandatory: Boolean,
    ): ScriptSignal {
        runEncounter(monster, mandatory)
        return if (runtime.currentMode == RuntimeMode.GameOver) ScriptSignal.StopScript else ScriptSignal.Continue
    }

    private fun applyReward(reward: CompiledScriptReward): PartyState {
        var party = reward.gold?.let { runtime.party.withGold(runtime.party.resources.gold + int(it)) } ?: runtime.party
        reward.items.forEach { item ->
            party = applyItemReward(party, item, reward)
        }
        return party
    }

    private fun applyItemReward(
        party: PartyState,
        item: CompiledHandleRef,
        reward: CompiledScriptReward,
    ): PartyState =
        when (val placed = party.placeItem(item, reward.application.overflow)) {
            is ItemPlacement.Placed ->
                placed.party.also {
                    reward.messages.pickedUp?.let { text -> screen.writeText(text.renderRewardLines(item)) }
                }

            ItemPlacement.Full ->
                party.also {
                    reward.messages.cantCarry?.let { text -> screen.writeText(text.renderRewardLines(item)) }
                }

            ItemPlacement.NeedsDropSlot ->
                reward.messages.cantCarry
                    ?.let { prompt ->
                        chooseDropSlot(party, prompt.renderRewardLines(item))
                            ?.let { slot ->
                                party
                                    .withInventoryItem(slot, item)
                                    .also {
                                        reward.messages.pickedUp?.let { text -> screen.writeText(text.renderRewardLines(item)) }
                                    }
                            }
                    } ?: party
        }

    private fun runRandomItemOffer(offer: CompiledRandomItemOffer): PartyState {
        val pool = content.itemPools.firstOrNull { itemPool -> itemPool.id == offer.pool }
        val items = pool?.items.orEmpty()
        if (items.isEmpty()) return runtime.party
        val draw = runtime.party.drawRandom(0, items.lastIndex)
        val item = items[draw.value]
        val price = offer.priceOverride ?: itemRecord(item).cost
        log.rngDraw(
            context = "script.random_item_offer.item",
            lo = 0,
            hi = items.lastIndex,
            value = draw.value,
            details = mapOf("pool" to content.slug(offer.pool), "item" to content.slug(item), "price" to price),
        )
        val party = draw.party
        val lines = offer.offer.renderRewardLines(item, price) + offer.choicePrompt.renderRewardLines(item, price)
        screen.writeText(lines)
        val choice =
            input.readValidChoice(screen, lines) { candidate ->
                offer.decline.matchesInput(content, candidate) || offer.buy.matchesInput(content, candidate)
            } ?: return party
        return when {
            offer.decline.matchesInput(content, choice) ->
                party.also {
                    log.event("script.random_item_offer.declined item=${content.slug(item)} price=$price")
                    offer.declined?.let { text -> screen.writeText(text.renderRewardLines(item, price)) }
                    input.pause(offer.pauseMillis.declined)
                }

            party.resources.gold < price ->
                party.also {
                    log.event("script.random_item_offer.broke item=${content.slug(item)} price=$price gold=${party.resources.gold}")
                    screen.writeText(offer.broke.renderRewardLines(item, price))
                    input.pause(offer.pauseMillis.broke)
                }

            else -> {
                when (val placed = party.placeItem(item, offer.application.overflow)) {
                    is ItemPlacement.Placed ->
                        placed.party
                            .withGold(party.resources.gold - price)
                            .also {
                                log.event("script.random_item_offer.bought item=${content.slug(item)} price=$price")
                                screen.updateStatus(runtime.withParty(it))
                                screen.writeText(offer.deal.renderRewardLines(item, price))
                                input.pause(offer.pauseMillis.deal)
                            }

                    ItemPlacement.Full ->
                        party.also {
                            log.event("script.random_item_offer.full item=${content.slug(item)}")
                            offer.messages.cantCarry?.let { text -> screen.writeText(text.renderRewardLines(item, price)) }
                            input.pause(offer.pauseMillis.full)
                        }

                    ItemPlacement.NeedsDropSlot ->
                        offer.messages.cantCarry
                            ?.let { prompt ->
                                chooseDropSlot(party, prompt.renderRewardLines(item, price))
                                    ?.let { slot ->
                                        party
                                            .withInventoryItem(slot, item)
                                            .withGold(party.resources.gold - price)
                                            .also {
                                                log.event(
                                                    "script.random_item_offer.bought " +
                                                        "item=${content.slug(item)} price=$price droppedSlot=$slot",
                                                )
                                                screen.updateStatus(runtime.withParty(it))
                                                screen.writeText(offer.deal.renderRewardLines(item, price))
                                                input.pause(offer.pauseMillis.deal)
                                            }
                                    }
                            } ?: party
                }
            }
        }
    }

    private fun chooseDropSlot(
        party: PartyState,
        leadLines: List<String>,
    ): Int? {
        val confirmLines =
            leadLines + "" +
                systems.prompts.party.dropOverflowConfirm
                    .renderSimplePrompt()
        screen.writeText(confirmLines)
        val confirm =
            input
                .readValidChoice(screen, confirmLines) { choice -> choice.uppercaseChar() == 'Y' || choice == '\n' }
                ?.uppercaseChar()
                ?: return null
        if (confirm != 'Y') return null
        val slotLines = inventoryReplaceLines(party)
        screen.writeText(slotLines)
        return input.readValidInventorySlotChoice(
            screen = screen,
            lines = slotLines,
            validSlots = party.inventory.filter { slot -> slot.item != null }.mapTo(mutableSetOf(), CompiledInventorySlot::slot),
            cancelKeys = setOf('\n'),
        )
    }

    private fun CompiledTextBlock.renderRewardLines(
        item: CompiledHandleRef,
        price: Int? = null,
    ): List<String> =
        renderTextLines(
            CompiledTextRenderContext(
                content = content,
                flowColumns = screen.textColumns,
                intValue = { expr -> int(expr).toString() },
                stringValue = ::string,
                runtimeValue = { part ->
                    when (part) {
                        CompiledRuntimeTextPart.OfferedItemName,
                        CompiledRuntimeTextPart.SelectedItemName,
                        CompiledRuntimeTextPart.BattleLootName,
                        -> itemName(item)

                        CompiledRuntimeTextPart.OfferedItemPrice,
                        CompiledRuntimeTextPart.ShopPrice,
                        -> price?.toString() ?: itemRecord(item).cost.toString()

                        else -> runtimeText(part)
                    }
                },
            ),
        )

    private fun PartyState.placeItem(
        item: CompiledHandleRef,
        overflow: RewardOverflowPolicy,
    ): ItemPlacement {
        check(itemRecord(item).storage == ItemStorageKind.SlottedInventory) {
            "compiled reward item ${content.slug(item)} is not slotted inventory"
        }
        val freeSlot = inventory.firstOrNull { slot -> slot.item == null }?.slot
        if (freeSlot != null) return ItemPlacement.Placed(withInventoryItem(freeSlot, item))
        return when (overflow) {
            RewardOverflowPolicy.SkipIfInventoryFull -> ItemPlacement.Full
            RewardOverflowPolicy.PromptDropThenPlace -> ItemPlacement.NeedsDropSlot
        }
    }

    private fun inventoryReplaceLines(party: PartyState): List<String> =
        systems.prompts.party.dropOverflowPrompt
            .renderSimplePrompt() +
            inventoryMenuLines(
                party.inventory.map { slot ->
                    inventorySlotKey(slot.slot) to (slot.item?.let(::itemName) ?: "")
                },
            )

    private fun ExternalRuntimeTextBlock.renderSimplePrompt(): List<String> = render({ null }, screen.textColumns)

    private fun memberChoiceLines(choices: List<Int>): List<String> =
        choices.map { member ->
            "$member. ${runtime.party.memberName(content, member - 1)}"
        }

    private fun inventoryChoiceLines(choices: List<InventorySlotChoice>): List<String> =
        inventoryMenuLines(
            choices.map { choice ->
                val item = runtime.party.inventoryItem(choice.slot)?.let(::itemName) ?: ""
                choice.key to item
            },
        )

    private fun List<String>.withChoiceGap(): List<String> = if (lastOrNull().isNullOrBlank()) this else this + ""

    private fun int(expr: CompiledIntExpr): Int =
        when (expr) {
            is CompiledIntExpr.Literal -> expr.value
            is CompiledIntExpr.Local -> locals[content.strings.value(expr.nameString)] ?: 0
            is CompiledIntExpr.Binary -> int(expr.left).applyCompiledBinary(expr.op, int(expr.right))
            is CompiledIntExpr.If ->
                when {
                    bool(expr.cond) -> int(expr.thenValue)
                    else -> int(expr.elseValue)
                }

            is CompiledIntExpr.Random -> random(int(expr.lo), int(expr.hi))
            is CompiledIntExpr.MemberField -> memberField(int(expr.member), content.strings.value(expr.fieldString))
            is CompiledIntExpr.PartyMemberQuery -> partyMemberQuery(expr.selection, expr.filter)
            is CompiledIntExpr.MonsterField,
            is CompiledIntExpr.BattleFlag,
            is CompiledIntExpr.BattleQuery,
            -> 0

            is CompiledIntExpr.RealmScalar -> realmScalar(content.strings.value(expr.nameString))
            is CompiledIntExpr.PartyScalar -> partyScalar(content.strings.value(expr.nameString))
            is CompiledIntExpr.InventoryPoolCount -> runtime.party.countInventoryPool(expr.pool)
            is CompiledIntExpr.InventorySlotCost ->
                runtime.party
                    .inventoryItem(int(expr.slot))
                    ?.let(::itemRecord)
                    ?.cost ?: 0
        }

    private fun bool(expr: CompiledBoolExpr): Boolean =
        when (expr) {
            is CompiledBoolExpr.Cmp -> compare(expr)
            is CompiledBoolExpr.Not -> !bool(expr.expr)
            is CompiledBoolExpr.And -> bool(expr.left) && bool(expr.right)
            is CompiledBoolExpr.Or -> bool(expr.left) || bool(expr.right)
            is CompiledBoolExpr.FlagIs -> hasFlagState(expr.flag, expr.state)
            is CompiledBoolExpr.MilestoneAchieved -> expr.milestone in runtime.party.milestones
            is CompiledBoolExpr.MemberFlag -> memberFlag(int(expr.member), content.strings.value(expr.nameString))
            is CompiledBoolExpr.MonsterFlag,
            is CompiledBoolExpr.BattleFlag,
            -> false

            is CompiledBoolExpr.RuntimeFlag ->
                when (content.strings.value(expr.nameString)) {
                    "in_dungeon" -> runtime.currentMode == RuntimeMode.Dungeon
                    "in_castle" -> runtime.currentMode == RuntimeMode.Castle
                    else -> false
                }
        }

    private fun random(
        lo: Int,
        hi: Int,
    ): Int {
        val draw = runtime.party.drawRandom(lo, hi)
        runtime = runtime.withParty(draw.party)
        log.rngDraw("script.random", lo, hi, draw.value)
        return draw.value
    }

    private fun partyMemberQuery(
        selection: PartyMemberSelection,
        filter: MemberChoiceFilter,
    ): Int {
        val choices = runtime.party.memberChoices(filter)
        if (selection == PartyMemberSelection.First || choices.isEmpty()) {
            return choices.selectPartyMember(selection) { range -> range.first }
        }
        val draw = runtime.party.drawRandom(0, choices.lastIndex)
        runtime = runtime.withParty(draw.party)
        log.rngDraw(
            context = "script.party_member_query",
            lo = 0,
            hi = choices.lastIndex,
            value = draw.value,
            details = mapOf("selection" to selection.name, "filter" to filter.name, "member" to choices[draw.value]),
        )
        return choices[draw.value]
    }

    private fun compare(expr: CompiledBoolExpr.Cmp): Boolean {
        val random = expr.left as? CompiledIntExpr.Random ?: return int(expr.left).compareCompiled(expr.op, int(expr.right))
        val lo = int(random.lo)
        val hi = int(random.hi)
        val draw = runtime.party.drawRandom(lo, hi)
        runtime = runtime.withParty(draw.party)
        val right = int(expr.right)
        val result = draw.value.compareCompiled(expr.op, right)
        log.rngDraw(
            context = "script.chance",
            lo = lo,
            hi = hi,
            value = draw.value,
            hit = result,
            details = mapOf("op" to expr.op, "threshold" to right),
        )
        return result
    }

    private fun read(target: CompiledLValue): Int =
        when (target) {
            is CompiledLValue.RealmScalar -> realmScalar(content.strings.value(target.nameString))
            is CompiledLValue.PartyScalar -> partyScalar(content.strings.value(target.nameString))
            is CompiledLValue.MemberField -> memberField(int(target.member), content.strings.value(target.fieldString))
            is CompiledLValue.MonsterField,
            is CompiledLValue.BattleFlag,
            -> 0
        }

    private fun write(
        target: CompiledLValue,
        value: Int,
    ) {
        val before = runtime
        when (target) {
            is CompiledLValue.RealmScalar -> writeRealmScalar(content.strings.value(target.nameString), value)
            is CompiledLValue.PartyScalar -> writePartyScalar(content.strings.value(target.nameString), value)
            is CompiledLValue.MemberField -> writeMemberField(int(target.member), content.strings.value(target.fieldString), value)
            is CompiledLValue.MonsterField,
            is CompiledLValue.BattleFlag,
            -> Unit
        }
        if (runtime != before) screen.updateStatus(runtime)
    }

    private fun realmScalar(name: String): Int =
        when (name) {
            "hour" -> runtime.hour
            "party_member_max" -> runtime.partyMemberMax
            "banished" -> 0
            else -> locals[name] ?: 0
        }

    private fun writeRealmScalar(
        name: String,
        value: Int,
    ) {
        runtime =
            when (name) {
                "hour" -> runtime.copy(hour = value)
                else -> runtime
            }
    }

    private fun partyScalar(name: String): Int =
        when (name) {
            "gold" -> runtime.party.resources.gold
            "torches" -> runtime.party.resources.torches
            "food" -> runtime.party.resources.food
            "time_left" -> runtime.party.resources.timeLeft
            "rested_hours" -> runtime.party.resources.restedHours
            "member_count" -> runtime.party.counters.memberCount ?: runtime.party.members.count { member -> !member.dead }
            else -> 0
        }

    private fun writePartyScalar(
        name: String,
        value: Int,
    ) {
        runtime =
            runtime.withParty(
                when (name) {
                    "gold" -> runtime.party.withGold(value)
                    "torches" -> runtime.party.withResource(Resource.Torches, value)
                    "food" -> runtime.party.withResource(Resource.Food, value)
                    "time_left" -> runtime.party.copy(resources = runtime.party.resources.copy(timeLeft = value.coerceAtLeast(0)))
                    "rested_hours" -> runtime.party.copy(resources = runtime.party.resources.copy(restedHours = value.coerceAtLeast(0)))
                    "member_count" ->
                        runtime.party.copy(counters = runtime.party.counters.copy(memberCount = value.coerceAtLeast(0)))

                    else -> runtime.party
                },
            )
    }

    private fun memberField(
        memberNumber: Int,
        field: String,
    ): Int {
        val member = runtime.party.members.getOrNull(memberNumber - 1) ?: return 0
        return when (field) {
            "hit_points" -> member.hitPoints.current
            "health" -> member.health
            "magic" -> member.mana.current
            "magic_max" -> member.mana.max
            "level" -> member.level
            "wisdom" -> member.wisdom
            "charisma" -> member.charisma
            "dexterity" -> member.dexterity
            "weapon_skill" -> member.weaponSkill
            "class" -> member.pcClass.sourceId
            "experience" -> member.experience
            "dead" -> if (member.dead) 1 else 0
            else -> 0
        }
    }

    private fun memberFlag(
        memberNumber: Int,
        name: String,
    ): Boolean {
        val member = runtime.party.members.getOrNull(memberNumber - 1) ?: return name == "dead"
        return when (name) {
            "dead" -> member.dead
            "spellcaster" -> !member.dead && member.pcClass in setOf(PcClass.Mage, PcClass.Priest)
            else -> false
        }
    }

    private fun writeMemberField(
        memberNumber: Int,
        field: String,
        value: Int,
    ) {
        val index = memberNumber - 1
        val member = runtime.party.members.getOrNull(index) ?: return
        val next =
            when (field) {
                "hit_points" -> member.copy(hitPoints = member.hitPoints.copy(current = value.coerceAtLeast(0)))
                "health" ->
                    value.coerceAtLeast(0).let { health ->
                        member.copy(health = health, hitPoints = member.hitPoints.copy(max = health))
                    }

                "magic" -> member.copy(mana = member.mana.copy(current = value.coerceAtLeast(0)))
                "magic_max" -> value.coerceAtLeast(0).let { max -> member.copy(mana = member.mana.current.coerceAtMost(max) of max) }
                "level" -> member.copy(level = value.coerceAtLeast(1))
                "wisdom" -> member.copy(wisdom = value.coerceAtLeast(0))
                "charisma" -> member.copy(charisma = value.coerceAtLeast(0))
                "weapon_skill" -> member.copy(weaponSkill = value.coerceAtLeast(0))
                "dexterity" -> member.copy(dexterity = value.coerceAtLeast(0))
                "experience" -> member.copy(experience = value.coerceAtLeast(0))
                "dead" -> member.copy(dead = value != 0)
                else -> member
            }
        runtime =
            runtime.withParty(
                runtime.party
                    .copy(
                        members =
                            runtime.party.members.mapIndexed { memberIndex, current ->
                                current.takeUnless { memberIndex == index } ?: next
                            },
                    ).withLivingCount(),
            )
    }

    private fun charactersFor(building: CompiledHandleRef): List<CompiledCharacter> =
        content.characters
            .filter { character ->
                val position = character.position()
                when (val town = runtime.currentTown) {
                    null -> position is CompiledWorldPosition.Wilderness && content.slug(building) == "none"
                    else -> position == CompiledWorldPosition.Town(town, building)
                }
            }.sortedBy { character -> content.strings.value(character.nameString) }

    private fun CompiledCharacter.position(): CompiledWorldPosition? =
        runtime.npcs.firstOrNull { placement -> placement.npc == id }?.position
            ?: (placement as? CompiledCharacterPlacement.Fixed)?.position

    private fun npcChoiceLines(npcs: List<CompiledCharacter>): List<String> =
        npcs.mapIndexed { index, npc ->
            "${index + 1}. ${content.strings.value(npc.nameString)}, ${content.slug(npc.job).readableSlug()}"
        }

    private fun ExternalRuntimeSpeak.screen(id: String): ExternalRuntimeTextBlock? =
        textTables.screens.firstOrNull { screen -> screen.id == id }?.text

    private fun ExternalRuntimeSpeak.noRumorLines(): List<String> = screen(SPEAK_NO_RUMOR_SCREEN)?.renderExternal().orEmpty()

    private fun ExternalRuntimeSpeak.questionReply(
        topic: QuestionTopic,
        npc: CompiledCharacter,
    ): ExternalRuntimeTextBlock? =
        textTables.questionReplies
            .firstOrNull { reply -> reply.topic == topic }
            ?.replies
            ?.firstOrNull { reply -> reply.job == content.slug(npc.job) }
            ?.text

    private fun ExternalRuntimeTextBlock.renderExternal(npc: CompiledCharacter? = null): List<String> =
        renderParts(
            values = { part -> externalTextValue(part, npc, journal = false) },
            wrapWidth = screen.textColumns,
        )

    private fun ExternalRuntimeTextBlock.renderExternalForJournal(): List<String> =
        renderParts { part -> externalTextValue(part, npc = null, journal = true) }

    private fun externalTextValue(
        part: ExternalRuntimeTextPart.Runtime,
        npc: CompiledCharacter?,
        journal: Boolean,
    ): String? =
        runtime.runtimeTextValue(content, part.kind)
            ?: when (part.kind) {
                "conversationNpcName" -> npc?.let { content.strings.value(it.nameString) }
                "townName" -> part.args["town"]?.let(::townNameBySlug)
                "fuzzyTownLocation" -> part.args["town"]?.let { town -> townLocation(town, part.args["radius"]?.toIntOrNull() ?: 0) }
                "roleName" -> part.args["role"]?.let(::roleName)
                "roleJob" -> part.args["role"]?.let(::roleJob)
                "roleLocation" -> part.args["role"]?.let { role -> if (journal) roleJournalLocation(role) else roleLocation(role) }
                "npcName" -> part.args["npc"]?.let(::npcName)
                "npcJob" -> part.args["npc"]?.let(::npcJob)
                "npcLocation" -> part.args["npc"]?.let { npc -> if (journal) npcJournalLocation(npc) else npcLocation(npc) }
                "locationCoordinate" -> part.args["location"]?.let(::locationTextBySlug)
                else -> null
            }

    private fun ExternalRuntimeTextBlock.hasExactJournalLocation(): Boolean =
        lines
            .flatten()
            .filterIsInstance<ExternalRuntimeTextPart.Runtime>()
            .any { part ->
                when (part.kind) {
                    "roleLocation" ->
                        part.args["role"]
                            ?.let(::roleCharacter)
                            ?.position()
                            ?.hasExactJournalCoordinate() == true

                    "npcLocation" ->
                        part.args["npc"]
                            ?.let(::npcCharacter)
                            ?.position()
                            ?.hasExactJournalCoordinate() == true

                    "fuzzyTownLocation" -> part.args["radius"]?.toIntOrNull() == 0
                    "locationCoordinate" -> true
                    else -> false
                }
            }

    private fun roleName(roleSlug: String): String? =
        roleCharacter(roleSlug)?.let { character -> content.strings.value(character.nameString) }

    private fun roleName(role: CompiledHandleRef): String =
        roleCharacter(role)
            ?.let { character -> content.strings.value(character.nameString) }
            ?: content.slug(role)

    private fun roleJob(roleSlug: String): String? = roleCharacter(roleSlug)?.let(::characterJob)

    private fun roleJob(role: CompiledHandleRef): String =
        roleCharacter(role)
            ?.let(::characterJob)
            ?: content.slug(role).readableJob()

    private fun roleLocation(roleSlug: String): String? =
        roleCharacter(roleSlug)
            ?.position()
            ?.let(::positionText)

    private fun roleJournalLocation(roleSlug: String): String? =
        roleCharacter(roleSlug)
            ?.position()
            ?.let(::journalPositionText)

    private fun roleLocation(role: CompiledHandleRef): String =
        roleCharacter(role)
            ?.position()
            ?.let(::positionText)
            ?: content.slug(role)

    private fun roleJournalLocation(role: CompiledHandleRef): String =
        roleCharacter(role)
            ?.position()
            ?.let(::journalPositionText)
            ?: content.slug(role)

    private fun npcName(npcSlug: String): String? = npcCharacter(npcSlug)?.let { character -> content.strings.value(character.nameString) }

    private fun npcName(npc: CompiledHandleRef): String =
        npcCharacter(npc)
            ?.let { character -> content.strings.value(character.nameString) }
            ?: content.slug(npc)

    private fun npcJob(npcSlug: String): String? = npcCharacter(npcSlug)?.let(::characterJob)

    private fun npcJob(npc: CompiledHandleRef): String =
        npcCharacter(npc)
            ?.let(::characterJob)
            ?: content.slug(npc).readableJob()

    private fun npcLocation(npcSlug: String): String? =
        npcCharacter(npcSlug)
            ?.position()
            ?.let(::positionText)

    private fun npcLocation(npc: CompiledHandleRef): String =
        npcCharacter(npc)
            ?.position()
            ?.let(::positionText)
            ?: content.slug(npc)

    private fun npcJournalLocation(npcSlug: String): String? =
        npcCharacter(npcSlug)
            ?.position()
            ?.let(::journalPositionText)

    private fun npcJournalLocation(npc: CompiledHandleRef): String =
        npcCharacter(npc)
            ?.position()
            ?.let(::journalPositionText)
            ?: content.slug(npc)

    private fun roleCharacter(roleSlug: String): CompiledCharacter? {
        val role = content.refOrNull(CompiledHandleKind.Role, roleSlug) ?: return null
        return roleCharacter(role)
    }

    private fun roleCharacter(role: CompiledHandleRef): CompiledCharacter? =
        content.characters.firstOrNull { character -> role in character.roles }

    private fun npcCharacter(npcSlug: String): CompiledCharacter? {
        val npc = content.refOrNull(CompiledHandleKind.Npc, npcSlug) ?: return null
        return npcCharacter(npc)
    }

    private fun npcCharacter(npc: CompiledHandleRef): CompiledCharacter? =
        content.characters.firstOrNull { character -> character.id == npc }

    private fun characterJob(character: CompiledCharacter): String = content.slug(character.job).readableJob()

    private fun townNameBySlug(townSlug: String): String =
        content.refOrNull(CompiledHandleKind.Town, townSlug)?.let { town -> content.townName(town) } ?: townSlug

    private fun townLocation(
        townSlug: String,
        radius: Int,
    ): String? {
        val town = content.refOrNull(CompiledHandleKind.Town, townSlug) ?: return null
        val position = content.towns.firstOrNull { current -> current.id == town }?.position ?: return null
        return pointText(fuzzyPoint(position, townSlug, radius))
    }

    private fun locationTextBySlug(locationSlug: String): String? =
        content.refOrNull(CompiledHandleKind.Location, locationSlug)?.let(::locationText)

    private fun positionText(position: CompiledWorldPosition): String =
        when (position) {
            is CompiledWorldPosition.Town -> "in ${content.townName(position.town)}"
            is CompiledWorldPosition.Wilderness -> "at ${pointText(position.point)}"
        }

    private fun CompiledWorldPosition.hasExactJournalCoordinate(): Boolean =
        when (this) {
            is CompiledWorldPosition.Town -> content.towns.any { town -> town.id == this.town }
            is CompiledWorldPosition.Wilderness -> true
        }

    private fun journalPositionText(position: CompiledWorldPosition): String =
        when (position) {
            is CompiledWorldPosition.Town -> {
                val townName = content.townName(position.town)
                val coordinate =
                    content.towns
                        .firstOrNull { town -> town.id == position.town }
                        ?.position
                        ?.let(::pointText)
                coordinate?.let { "in $townName at $it" } ?: "in $townName"
            }

            is CompiledWorldPosition.Wilderness -> "at ${pointText(position.point)}"
        }

    private fun pointText(point: GridPoint): String = "${point.x}x${point.y}"

    private fun fuzzyPoint(
        point: GridPoint,
        slug: String,
        radius: Int,
    ): GridPoint =
        when {
            radius <= 0 -> point
            else ->
                GridPoint(
                    x = point.x + stableOffset("$slug:x", radius),
                    y = point.y + stableOffset("$slug:y", radius),
                )
        }

    private fun stableOffset(
        key: String,
        radius: Int,
    ): Int {
        val span = radius * 2 + 1
        val hash = key.fold(0) { acc, char -> acc * 31 + char.code }
        return Math.floorMod(hash, span) - radius
    }

    private fun Map<String, List<ExternalRuntimeCommand>>.mnemonicMenuLines(group: RuntimeCommandGroupId): List<String> =
        get(group.id).orEmpty().map { command -> command.mnemonicLine() }

    private fun ExternalRuntimeCommand.questionTopic(): QuestionTopic? =
        topic?.let { value ->
            QuestionTopic.entries.firstOrNull { topic -> topic.name.equals(value, ignoreCase = true) }
        }

    private fun matchesBranchState(slug: String): Boolean = flagStateRef(slug)?.let(::matchesFlagState) ?: false

    private fun matchesFlagState(state: CompiledHandleRef): Boolean =
        flagForState(state)?.let { flag -> hasFlagState(flag, state) } ?: false

    private fun hasFlagState(
        flag: CompiledHandleRef,
        state: CompiledHandleRef,
    ): Boolean =
        state in runtime.party.flags ||
            flagStateNative(state) == "none" &&
            runtime.party.flags.none { current ->
                current in
                    flagStates(
                        flag,
                    )
            }

    private fun flagStates(flag: CompiledHandleRef): Set<CompiledHandleRef> =
        content.flags
            .firstOrNull { current -> current.id == flag }
            ?.states
            ?.map { current -> current.id }
            ?.toSet()
            .orEmpty()

    private fun flagForState(state: CompiledHandleRef): CompiledHandleRef? =
        content.flags.firstOrNull { flag -> flag.states.any { current -> current.id == state } }?.id

    private fun flagStateNative(state: CompiledHandleRef): String? =
        content.flags
            .asSequence()
            .flatMap { flag -> flag.states.asSequence() }
            .firstOrNull { current -> current.id == state }
            ?.let { current -> content.strings.value(current.nativeString) }

    private fun flagStateRef(slug: String): CompiledHandleRef? = content.refOrNull(CompiledHandleKind.FlagState, slug)

    private fun PartyState.withNpcVisit(
        npc: CompiledHandleRef,
        visited: Boolean,
    ): PartyState {
        val visits = visitedNpcs.associateBy(CompiledNpcVisit::npc) + (npc to CompiledNpcVisit(npc, visited))
        return copy(visitedNpcs = visits.values.sortedBy { visit -> content.slug(visit.npc) })
    }

    private fun <T> List<T>.randomElementOrNull(context: String): T? {
        if (isEmpty()) return null
        val draw = runtime.party.drawRandom(0, lastIndex)
        runtime = runtime.withParty(draw.party)
        log.rngDraw(context, 0, lastIndex, draw.value)
        return this[draw.value]
    }

    private fun ChitchatPromptAudience.matches(gender: Gender?): Boolean =
        when (this) {
            ChitchatPromptAudience.Any -> true
            ChitchatPromptAudience.Female -> gender == Gender.Female
            ChitchatPromptAudience.Male -> gender == Gender.Male
        }

    private fun CompiledTextBlock.renderScriptLines(): List<String> =
        renderTextLines(
            CompiledTextRenderContext(
                content = content,
                flowColumns = screen.textColumns,
                intValue = { expr -> int(expr).toString() },
                stringValue = ::string,
                roleNameValue = ::roleName,
                roleJobValue = ::roleJob,
                roleLocationValue = ::roleLocation,
                npcNameValue = ::npcName,
                npcJobValue = ::npcJob,
                npcLocationValue = ::npcLocation,
                locationValue = ::locationText,
                runtimeValue = ::runtimeText,
            ),
        )

    private fun CompiledTextBlock.renderJournalLines(): List<String> =
        renderTextLines(
            CompiledTextRenderContext(
                content,
                intValue = { expr -> int(expr).toString() },
                stringValue = ::string,
                roleNameValue = ::roleName,
                roleJobValue = ::roleJob,
                roleLocationValue = ::roleJournalLocation,
                npcNameValue = ::npcName,
                npcJobValue = ::npcJob,
                npcLocationValue = ::npcJournalLocation,
                locationValue = ::locationText,
                runtimeValue = ::runtimeText,
            ),
        )

    private fun locationText(location: CompiledHandleRef): String =
        runtime.locations
            .firstOrNull { placement -> placement.location == location }
            ?.point
            ?.let(::pointText)
            ?: content.slug(location)

    private fun string(expr: CompiledStringExpr): String =
        when (expr) {
            is CompiledStringExpr.NativeString ->
                when (content.strings.value(expr.pathString)) {
                    NativeAllowlist.partyMemberName.path -> memberName(expr.args.single().argInt())
                    NativeAllowlist.partyPronounSubject.path -> memberPronoun(expr.args.single().argInt(), PronounCase.Subject)
                    NativeAllowlist.partyPronounObject.path -> memberPronoun(expr.args.single().argInt(), PronounCase.Object)
                    NativeAllowlist.partyPronounPossessive.path -> memberPronoun(expr.args.single().argInt(), PronounCase.Possessive)
                    NativeAllowlist.townNpcName.path -> townNpcName(expr.args.single())
                    NativeAllowlist.combatMonsterName.path -> ""
                    else -> ""
                }
        }

    private fun runtimeText(part: CompiledRuntimeTextPart): String =
        runtime.runtimeTextValue(content, part)
            ?: when (part) {
                CompiledRuntimeTextPart.DungeonDepth -> locals["dungeon_level"]?.toString().orEmpty()
                else -> "[${part.name}]"
            }

    private fun CompiledNativeStringArg.argInt(): Int =
        when (this) {
            is CompiledNativeStringArg.IntValue -> int(value)
            is CompiledNativeStringArg.Literal -> content.strings.value(string).toIntOrNull() ?: 0
        }

    private fun memberName(memberNumber: Int): String =
        runtime.party.members
            .getOrNull(memberNumber - 1)
            ?.let { member -> content.strings.value(member.nameString) }
            .orEmpty()

    private fun memberPronoun(
        memberNumber: Int,
        pronounCase: PronounCase,
    ): String =
        runtime.party.members
            .getOrNull(memberNumber - 1)
            ?.gender
            .orDefault()
            .pronoun(pronounCase)

    private fun townNpcName(arg: CompiledNativeStringArg): String {
        val jobSlug =
            when (arg) {
                is CompiledNativeStringArg.Literal -> content.strings.value(arg.string)
                is CompiledNativeStringArg.IntValue -> int(arg.value).toString()
            }
        val job = content.refOrNull(CompiledHandleKind.NpcJob, jobSlug)
        val currentTown = runtime.currentTown
        val townCharacter =
            currentTown?.let { town ->
                content.characters.firstOrNull { character ->
                    character.job == job &&
                        (character.position() as? CompiledWorldPosition.Town)?.town == town
                }
            }
        return (townCharacter ?: content.characters.firstOrNull { character -> character.job == job })
            ?.let { character -> content.strings.value(character.nameString) }
            .orEmpty()
    }

    private fun Gender?.orDefault(): Gender = this ?: Gender.Male

    private fun itemName(item: CompiledHandleRef): String = content.strings.value(itemRecord(item).displayNameString)

    private fun itemRecord(item: CompiledHandleRef): CompiledItemRecord =
        checkNotNull(content.items.firstOrNull { record -> record.id == item }) {
            "unknown compiled item ${content.slug(item)}"
        }

    private fun PartyState.inventorySlotChoices(
        filter: InventorySlotChoiceFilter,
        slots: IntRange,
        firstKey: Char,
    ): List<InventorySlotChoice> {
        val itemBySlot = inventory.associate { slot -> slot.slot to slot.item }
        return slots
            .filter { slot ->
                when (filter) {
                    InventorySlotChoiceFilter.Any -> true
                    InventorySlotChoiceFilter.NonEmpty -> itemBySlot[slot] != null
                    InventorySlotChoiceFilter.Empty -> itemBySlot[slot] == null
                }
            }.map { slot -> InventorySlotChoice(firstKey + (slot - slots.first), slot) }
    }

    private fun PartyState.consumeInventoryPool(
        pool: CompiledHandleRef,
        quantity: Int,
    ): PartyState {
        val pooledItems =
            content.itemPools
                .firstOrNull { itemPool -> itemPool.id == pool }
                ?.items
                ?.toSet()
                .orEmpty()
        val consumedSlots =
            inventory
                .asSequence()
                .filter { slot -> slot.item in pooledItems }
                .take(quantity.coerceAtLeast(0))
                .map { slot -> slot.slot }
                .toSet()
        return copy(inventory = inventory.map { slot -> slot.copy(item = slot.item.takeUnless { slot.slot in consumedSlots }) })
    }

    private fun PartyState.countInventoryPool(pool: CompiledHandleRef): Int {
        val pooledItems =
            content.itemPools
                .firstOrNull { itemPool -> itemPool.id == pool }
                ?.items
                ?.toSet()
                .orEmpty()
        return inventory.count { slot -> slot.item in pooledItems }
    }

    private fun PartyState.withFlagState(
        flag: CompiledHandleRef,
        state: CompiledHandleRef,
    ): PartyState {
        val existingStates =
            content.flags
                .firstOrNull { current -> current.id == flag }
                ?.states
                ?.map { current -> current.id }
                ?.toSet()
                .orEmpty()
        return copy(flags = ((flags - existingStates) + state).distinct())
    }

    private fun PartyState.withLivingCount(): PartyState =
        copy(counters = counters.copy(memberCount = members.count { member -> !member.dead }))

    private fun CompiledInputBinding.promptValue(): Int {
        val primary = content.strings.value(primaryString)
        return primary.singleOrNull()?.let { char ->
            char.digitToIntOrNull() ?: char.uppercaseChar().code
        } ?: primary.uppercase().hashCode()
    }

    private fun CompiledTextAnswer.accepts(raw: String): Boolean {
        val inputValue = raw.normalized(trim, caseSensitive, maxLength)
        return acceptedStrings
            .map(content.strings::value)
            .any { accepted -> accepted.normalized(trim, caseSensitive, maxLength = null) == inputValue }
    }

    private fun String.normalized(
        trim: Boolean,
        caseSensitive: Boolean,
        maxLength: Int?,
    ): String {
        val limited = maxLength?.let(::take) ?: this
        val spaced = limited.takeIf { !trim } ?: limited.trim()
        return spaced.takeIf { caseSensitive } ?: spaced.lowercase()
    }

    private fun CompiledViewCommand.toRuntimeViewCommand(): RuntimeViewCommand =
        when (this) {
            CompiledViewCommand.ClearText -> RuntimeViewCommand.ClearText
            CompiledViewCommand.ClearMapWindow -> RuntimeViewCommand.ClearMapWindow
            CompiledViewCommand.RenderMap -> RuntimeViewCommand.RenderMap
            CompiledViewCommand.StatusLine -> RuntimeViewCommand.StatusLine
            is CompiledViewCommand.ShowImage -> RuntimeViewCommand.ShowImage(content.slug(image))
        }

    private fun teleportDelta(directionKey: Int): GridPoint? =
        when (directionKey) {
            8 -> GridPoint(0, -1)
            2 -> GridPoint(0, 1)
            4 -> GridPoint(-1, 0)
            6 -> GridPoint(1, 0)
            else -> null
        }

    private fun ScriptControlSignal.toRuntimeSignal(insideLoop: Boolean): ScriptSignal =
        when (this) {
            ScriptControlSignal.Continue -> ScriptSignal.Continue
            ScriptControlSignal.BreakNearestLoop ->
                ScriptSignal.BreakNearestLoop.takeIf { insideLoop } ?: ScriptSignal.StopScript

            ScriptControlSignal.StopScript -> ScriptSignal.StopScript
        }

    private fun CompiledInstruction.scriptSignal(default: ScriptControlSignal): ScriptControlSignal =
        operands
            .filterIsInstance<CompiledOperand.EnumName>()
            .firstOrNull()
            ?.name
            ?.let(ScriptControlSignal::valueOf)
            ?: default

    private fun ScriptBlockContext.continuationAfter(
        instructionIndex: Int,
        instructionCount: Int,
    ): ScriptContinuationState? {
        val program = program ?: return null
        val frames = framesAfter(instructionIndex, instructionCount)
        if (frames.isEmpty()) return null
        return ScriptContinuationState(
            root = program.rootHandle,
            frames = frames,
            locals = locals,
            textBindings = textBindings,
            activeInventorySlot = activeInventorySlot,
        )
    }

    private fun ScriptBlockContext.childContext(
        instructionIndex: Int,
        instructionCount: Int,
        childPath: List<Int>,
        insideLoop: Boolean,
    ): ScriptBlockContext =
        ScriptBlockContext(
            program = program,
            blockPath = blockPath + childPath,
            parentFrames = framesAfter(instructionIndex, instructionCount),
            insideLoop = insideLoop,
        )

    private fun ScriptBlockContext.framesAfter(
        instructionIndex: Int,
        instructionCount: Int,
    ): List<ScriptFrameState> =
        buildList {
            if (instructionIndex + 1 < instructionCount) {
                add(ScriptFrameState(blockPath, instructionIndex + 1, insideLoop))
            }
            addAll(parentFrames)
        }

    private fun CompiledScriptProgram.blockAt(path: List<Int>): List<CompiledInstruction> {
        var block = instructions
        var cursor = 0
        while (cursor < path.size) {
            val instruction = block.getOrNull(path[cursor++]) ?: error("compiled continuation block path is out of range: $path")
            val selector = path.getOrNull(cursor++) ?: error("compiled continuation block path is incomplete: $path")
            block =
                when (instruction.opcode) {
                    CompiledOpcode.If,
                    CompiledOpcode.Loop,
                    CompiledOpcode.ForEachMember,
                    -> instruction.instructionsOperand(selector)

                    CompiledOpcode.Switch -> {
                        val caseIndex = path.getOrNull(cursor++) ?: error("compiled switch continuation path is incomplete: $path")
                        instruction.switchCasesOperand(selector).getOrNull(caseIndex)?.body
                            ?: error("compiled switch continuation case is out of range: $path")
                    }

                    CompiledOpcode.Menu -> {
                        if (selector == 2) {
                            instruction.instructionsOperand(selector)
                        } else {
                            val optionIndex = path.getOrNull(cursor++) ?: error("compiled menu continuation path is incomplete: $path")
                            instruction.menuOptionsOperand(selector).getOrNull(optionIndex)?.body
                                ?: error("compiled menu continuation option is out of range: $path")
                        }
                    }

                    CompiledOpcode.AskText -> {
                        val branch = path.getOrNull(cursor++) ?: error("compiled text-answer continuation path is incomplete: $path")
                        val answer = instruction.textAnswerOperand(selector)
                        if (branch == 0) answer.matchOps else answer.missOps
                    }

                    else -> error("compiled continuation path cannot descend through ${instruction.opcode}: $path")
                }
        }
        return block
    }

    private fun CompiledInstruction.textBlockOperand(index: Int): CompiledTextBlock =
        (operands[index] as CompiledOperand.TextBlockValue).value

    private fun CompiledInstruction.intOperand(index: Int): CompiledIntExpr = (operands[index] as CompiledOperand.IntExprValue).value

    private fun CompiledInstruction.boolOperand(index: Int): CompiledBoolExpr = (operands[index] as CompiledOperand.BoolExprValue).value

    private fun CompiledInstruction.lvalueOperand(index: Int): CompiledLValue = (operands[index] as CompiledOperand.LValueValue).value

    private fun CompiledInstruction.stringExprOperand(index: Int): CompiledStringExpr =
        (operands[index] as CompiledOperand.StringExprValue).value

    private fun CompiledInstruction.viewOperand(index: Int): CompiledViewCommand =
        (operands[index] as CompiledOperand.ViewCommandValue).value

    private fun CompiledInstruction.handleOperand(index: Int): CompiledHandleRef = (operands[index] as CompiledOperand.Handle).ref

    private fun CompiledInstruction.stringIdOperand(index: Int): Int = (operands[index] as CompiledOperand.StringId).id

    private fun CompiledInstruction.booleanOperand(index: Int): Boolean = (operands[index] as CompiledOperand.BooleanValue).value

    private fun CompiledInstruction.characterOperand(index: Int): Char = (operands[index] as CompiledOperand.Character).value

    private fun CompiledInstruction.integerOperand(index: Int): Int = (operands[index] as CompiledOperand.Integer).value

    private fun CompiledInstruction.enumOperand(index: Int): String = (operands[index] as CompiledOperand.EnumName).name

    private fun CompiledInstruction.instructionsOperand(index: Int): List<CompiledInstruction> =
        (operands[index] as CompiledOperand.Instructions).instructions

    private fun CompiledInstruction.switchCasesOperand(index: Int): List<CompiledSwitchCase> =
        (operands[index] as CompiledOperand.SwitchCases).cases

    private fun CompiledInstruction.menuOptionsOperand(index: Int) = (operands[index] as CompiledOperand.MenuOptions).options

    private fun CompiledInstruction.inputBindingsOperand(index: Int): List<CompiledInputBinding> =
        (operands[index] as CompiledOperand.InputBindings).bindings

    private fun CompiledInstruction.textAnswerOperand(index: Int): CompiledTextAnswer =
        (operands[index] as CompiledOperand.TextAnswer).answer

    private fun CompiledInstruction.rewardOperand(index: Int): CompiledScriptReward = (operands[index] as CompiledOperand.Reward).reward

    private fun CompiledInstruction.randomItemOfferOperand(index: Int): CompiledRandomItemOffer =
        (operands[index] as CompiledOperand.RandomItemOffer).offer

    private fun CompiledInstruction.shopServiceOperand(index: Int): CompiledShopService =
        (operands[index] as CompiledOperand.ShopService).service

    private fun CompiledContentBundle.refOrNull(
        kind: CompiledHandleKind,
        slug: String,
    ): CompiledHandleRef? = strings.idOf(slug)?.let { slugString -> handles.refOrNull(kind, slugString) }

    private fun CompiledContentBundle.townName(ref: CompiledHandleRef): String =
        towns.firstOrNull { town -> town.id == ref }?.let { town -> strings.value(town.nameString) } ?: slug(ref)
}

private enum class ScriptSignal { Continue, BreakNearestLoop, StopScript }

private data class ScriptBlockContext(
    val program: CompiledScriptProgram?,
    val blockPath: List<Int>,
    val parentFrames: List<ScriptFrameState>,
    val insideLoop: Boolean,
)

private sealed interface ItemPlacement {
    data class Placed(
        val party: PartyState,
    ) : ItemPlacement

    data object NeedsDropSlot : ItemPlacement

    data object Full : ItemPlacement
}

private data class InventorySlotChoice(
    val key: Char,
    val slot: Int,
)
