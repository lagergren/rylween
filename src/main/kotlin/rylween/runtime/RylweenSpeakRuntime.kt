// Reusable runtime session for the standard NPC conversation. Drives NPC
// selection, visited bits, dialogue branches, the C/Q/R/B menu, the L/M/P/N
// question submenu, chitchat/rumour selection, and rumour quiet-limit behavior.

package rylween.runtime

import rylween.dsl.*

data class SpeakRuntimeContent(
    val speak: SpeakSystemModel = SpeakSystemModel(null),
    val characters: List<CharacterModel> = emptyList(),
    val dialogue: List<DialogueModel> = emptyList(),
)

sealed interface SpeakStep {
    data class ChooseNpc(val npcs: List<CharacterModel>) : SpeakStep
    data class ScriptBranch(
        val npc: CharacterModel,
        val ops: List<Op>,
        val root: ScriptRootRef,
        val next: SpeakStep,
        val continuation: ScriptContinuation? = null,
    ) : SpeakStep
    data class MainMenu(val npc: CharacterModel, val actions: List<ConversationMenuActionModel>) : SpeakStep
    data class QuestionMenu(val npc: CharacterModel, val actions: List<QuestionMenuActionModel>) : SpeakStep
    data class Message(val blocks: List<TextBlock>, val next: SpeakStep) : SpeakStep
    data class Finished(val runtime: RuntimeSessionState) : SpeakStep {
        val party: PartyState get() = runtime.activeParty
    }
}

data class SpeakSession(
    val content: SpeakRuntimeContent,
    val runtime: RuntimeSessionState,
    val building: BuildingId,
    val step: SpeakStep,
    val selectedNpc: CharacterModel? = null,
    val rumourTold: Boolean = false,
) {
    private val conversation: StandardConversationModel
        get() = requireNotNull(content.speak.standardConversation) {
            "standard conversation system is not configured"
        }

    fun apply(input: SpeakInput): SpeakSession =
        when (val current = step) {
            is SpeakStep.ChooseNpc -> {
                val npc = (input as? SpeakInput.Npc)
                    ?: error("speak session is choosing an NPC")
                val character = current.npcs.firstOrNull { it.id == npc.npc }
                    ?: error("NPC ${npc.npc.slug} is not available for ${building.slug}")
                val nextRuntime = runtime.withActiveParty(runtime.activeParty.withNpcVisit(character.id, visited = true))
                val branch = content.firstMatchingDialogueBranch(character.id, nextRuntime.activeParty)
                val menu = mainMenu(character)
                copy(
                    runtime = nextRuntime,
                    selectedNpc = character,
                    step = branch?.let {
                        SpeakStep.ScriptBranch(
                            npc = character,
                            ops = it.ops,
                            root = content.scriptRootForDialogueBranch(character.id, it),
                            next = menu,
                        )
                    } ?: menu,
                )
            }
            is SpeakStep.ScriptBranch -> error("speak session is running an authored script branch")
            is SpeakStep.MainMenu -> {
                val choice = (input as? SpeakInput.Main)
                    ?: error("speak session is at the main conversation menu")
                when (choice.choice) {
                    SpeakMainChoice.Chitchat -> chitchat(current.npc)
                    SpeakMainChoice.Question -> copy(step = questionMenu(current.npc))
                    SpeakMainChoice.Rumour -> rumour(current.npc)
                    SpeakMainChoice.Bye -> copy(step = SpeakStep.Finished(runtime))
                }
            }
            is SpeakStep.QuestionMenu -> {
                val question = (input as? SpeakInput.Question)
                    ?: error("speak session is at the question menu")
                when (question.topic) {
                    QuestionTopic.Nothing -> {
                        copy(step = mainMenu(current.npc))
                    }
                    else -> {
                        val action = content.firstMatchingQuestionAction(question.topic, current.npc.job)
                        copy(
                            step = action
                                ?.let {
                                    SpeakStep.ScriptBranch(
                                        npc = current.npc,
                                        ops = it.ops,
                                        root = content.scriptRootForQuestionAction(it),
                                        next = mainMenu(current.npc),
                                    )
                                }
                                ?: message(questionReply(question.topic, current.npc), mainMenu(current.npc)),
                        )
                    }
                }
            }
            is SpeakStep.Message -> {
                require(input is SpeakInput.Continue) { "speak session is showing message text" }
                copy(step = current.next)
            }
            is SpeakStep.Finished -> error("speak session is already finished: $current")
        }

    private fun chitchat(npc: CharacterModel): SpeakSession {
        val subjects = conversation.chitchat.subjects.ifEmpty { listOf(ChitchatSubject.Well) }
        val draw = runtime.drawRandom(0, subjects.lastIndex)
        val subject = subjects[draw.value]
        return copy(
            runtime = draw.runtime,
            step = message(
                listOf(chitchatPrompt(subject), chitchatReply(subject, npc)),
                mainMenu(npc),
            ),
        )
    }

    private fun rumour(npc: CharacterModel): SpeakSession {
        val selection = if (rumourTold) null else content.selectStandardRumour(runtime)
        val text = selection?.rumour?.text ?: content.noRumourText()
        return copy(
            runtime = selection?.runtime ?: runtime,
            rumourTold = true,
            step = message(text, mainMenu(npc)),
            )
    }

    private fun mainMenu(npc: CharacterModel): SpeakStep.MainMenu =
        SpeakStep.MainMenu(npc, conversation.menu.actions)

    private fun questionMenu(npc: CharacterModel): SpeakStep.QuestionMenu =
        SpeakStep.QuestionMenu(npc, conversation.menu.questions)

    private fun message(text: TextBlock, next: SpeakStep): SpeakStep.Message =
        message(listOf(text), next)

    private fun message(blocks: List<TextBlock>, next: SpeakStep): SpeakStep.Message =
        SpeakStep.Message(blocks, next)

    private fun chitchatPrompt(subject: ChitchatSubject): TextBlock {
        val leaderGender = runtime.activeParty.members.firstOrNull()?.gender
        val variants = content.speak.textTables.chitchatPrompts.firstOrNull { it.subject == subject }?.variants.orEmpty()
        return variants.firstOrNull { it.audience.matches(leaderGender) }?.text
            ?: variants.firstOrNull()?.text
            ?: inlineText("- Hello.")
    }

    private fun chitchatReply(subject: ChitchatSubject, npc: CharacterModel): TextBlock =
        content.speak.textTables.chitchatReplies
            .firstOrNull { it.subject == subject }
            ?.replies
            ?.firstOrNull { it.job == npc.job }
            ?.text
            ?: inlineText("- I have nothing to say.")

    private fun questionReply(topic: QuestionTopic, npc: CharacterModel): TextBlock =
        content.speak.textTables.questionReplies
            .firstOrNull { it.topic == topic }
            ?.replies
            ?.firstOrNull { it.job == npc.job }
            ?.text
            ?: content.noRumourText()

    companion object {
        fun start(
            content: SpeakRuntimeContent,
            runtime: RuntimeSessionState,
            building: BuildingId,
        ): SpeakSession {
            val npcs = content.charactersFor(runtime, building)
            val step = if (npcs.isEmpty()) {
                SpeakStep.Message(
                    blocks = listOf(
                        content.speak.textTables.screens.firstOrNull { it.id == DslDefaultText.Speak.noNpc }?.text
                            ?: inlineText("Noone is here!"),
                    ),
                    next = SpeakStep.Finished(runtime),
                )
            } else {
                SpeakStep.ChooseNpc(npcs)
            }
            return SpeakSession(content, runtime, building, step)
        }
    }
}

data class RumourSelection(
    val rumour: RumourEntryModel,
    val runtime: RuntimeSessionState,
)

fun SpeakRuntimeContent.charactersFor(runtime: RuntimeSessionState, building: BuildingId): List<CharacterModel> {
    val realizedByNpc = runtime.npcs.associateBy { it.npc }
    val town = runtime.realm.currentTown
    val wildernessPoint = runtime.activeParty.position.let { (it as? WorldPosition.Wilderness)?.point }
    return characters
        .filter { character ->
            val position = realizedByNpc[character.id]?.position
                ?: (character.placement as? CharacterPlacementModel.Fixed)?.position
            if (town != null) {
                position == WorldPosition.Town(town, building)
            } else {
                building == DslDefaultBuildings.none && position == wildernessPoint?.let { WorldPosition.Wilderness(it) }
            }
        }
        .sortedBy { it.name }
}

fun RuntimeContent.speakRuntimeContent(): SpeakRuntimeContent =
    SpeakRuntimeContent(
        speak = speak,
        characters = characters,
        dialogue = dialogue,
    )

fun SpeakRuntimeContent.firstMatchingDialogueBranch(npc: NpcId, party: PartyState): DialogueBranchModel? {
    val branches = dialogue.firstOrNull { it.npc == npc }?.branches.orEmpty()
    return branches.firstOrNull { branch ->
        branch.whenState?.let(party::hasFlagState) == true
    } ?: branches.firstOrNull { it.whenState == null }
}

fun SpeakRuntimeContent.firstMatchingQuestionAction(topic: QuestionTopic, job: NpcJobId): QuestionActionScriptModel? =
    speak.questionActions.firstOrNull { it.topic == topic && it.job == job }

fun SpeakRuntimeContent.standardRumours(party: PartyState): List<RumourEntryModel> =
    rumours(DslDefaultRumourTables.standardTown, party)

fun SpeakRuntimeContent.rumours(table: RumourTableId, party: PartyState): List<RumourEntryModel> =
    speak.textTables.rumourTables
        .firstOrNull { it.id == table }
        ?.entries
        ?.filter { entry -> entry.excludedBy.none(party::hasFlagState) }
        .orEmpty()

fun SpeakRuntimeContent.selectStandardRumour(runtime: RuntimeSessionState): RumourSelection? {
    return selectRumour(DslDefaultRumourTables.standardTown, runtime)
}

fun SpeakRuntimeContent.selectRumour(table: RumourTableId, runtime: RuntimeSessionState): RumourSelection? {
    val entries = rumours(table, runtime.activeParty)
    if (entries.isEmpty()) return null
    val draw = runtime.drawRandom(0, entries.lastIndex)
    return RumourSelection(entries[draw.value], draw.runtime)
}

fun SpeakRuntimeContent.noRumourText(): TextBlock =
    speak.textTables.screens.firstOrNull { it.id == DslDefaultText.Speak.noRumour }?.text
        ?: inlineText("- Sorry. There's", "nothing new I can", "tell you.", "")

fun PartyState.withNpcVisit(npc: NpcId, visited: Boolean): PartyState {
    val visits = visitedNpcs.associateBy { it.npc } + (npc to NpcVisitState(npc = npc, visited = visited))
    return copy(visitedNpcs = visits.values.sortedBy { it.npc.slug })
}

fun PartyState.withFlagState(flag: String, native: String): PartyState =
    copy(
        flags = (flags.filterNot { it.flag.slug == flag } + runtimeFlagState(flag, native))
            .sortedWith(compareBy({ it.flag.slug }, { it.native })),
    )

fun PartyState.hasFlagState(state: FlagState): Boolean =
    flags.any { it.flag == state.flag && it.native == state.native }

private fun ChitchatPromptAudience.matches(gender: Gender?): Boolean =
    when (this) {
        ChitchatPromptAudience.Any -> true
        ChitchatPromptAudience.Female -> gender == Gender.Female
        ChitchatPromptAudience.Male -> gender == Gender.Male
    }
