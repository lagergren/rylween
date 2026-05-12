/**
 * Conversation and rumor builders.
 *
 * This file contains the mutable DSL builders for reusable town/wilderness NPC
 * conversation systems: standard menu shape, chitchat/question/rumor tables,
 * reusable screen text, and question-action scripts. The data model lives in
 * `WorldContentDsl.kt`; authored character/job-specific lines stay in the game
 * DSL so another game can reuse the machinery with different prose.
 */
package rylween.dsl.impl

import rylween.dsl.*

@RylNextDsl
class SpeakBuilder {
    private var standardConversationModel: StandardConversationModel? = null
    private var textTablesModel = SpeakTextTablesModel()
    private val questionActions = mutableListOf<QuestionActionScriptModel>()

    fun standardConversation(body: StandardConversationBuilder.() -> Unit) {
        standardConversationModel = StandardConversationBuilder().apply(body).build()
    }

    fun textTables(body: SpeakTextTablesBuilder.() -> Unit) {
        textTablesModel = SpeakTextTablesBuilder().apply(body).build()
    }

    fun questionAction(
        topic: QuestionTopic,
        job: NpcJobId,
        body: ScriptScope.() -> Unit,
    ) {
        questionActions += QuestionActionScriptModel(topic, job, recordScript(body))
    }

    fun build(): SpeakSystemModel =
        SpeakSystemModel(
            standardConversation = standardConversationModel,
            textTables = textTablesModel,
            questionActions = questionActions.toList(),
        )
}

@RylNextDsl
class SpeakTextTablesBuilder {
    private val screens = mutableListOf<ReusableScreenTextModel>()
    private val chitchatPrompts = mutableListOf<ChitchatPromptTextModel>()
    private val chitchatReplies = mutableListOf<ChitchatReplyTextModel>()
    private val questionReplies = mutableListOf<QuestionReplyTextModel>()
    private val rumorTables = mutableListOf<RumorTextTableModel>()

    fun screen(
        id: TextId,
        body: StructuredTextBlockBuilder.() -> Unit,
    ) {
        screens += ReusableScreenTextModel(id, StructuredTextBlockBuilder().apply(body).build())
    }

    fun chitchatPrompt(
        subject: ChitchatSubject,
        body: ChitchatPromptTextBuilder.() -> Unit,
    ) {
        chitchatPrompts += ChitchatPromptTextBuilder(subject).apply(body).build()
    }

    fun chitchatReplies(
        subject: ChitchatSubject,
        body: JobTextTableBuilder.() -> Unit,
    ) {
        chitchatReplies += ChitchatReplyTextModel(subject, JobTextTableBuilder().apply(body).build())
    }

    fun questionReplies(
        topic: QuestionTopic,
        body: JobTextTableBuilder.() -> Unit,
    ) {
        questionReplies += QuestionReplyTextModel(topic, JobTextTableBuilder().apply(body).build())
    }

    fun rumorTable(
        id: RumorTableId,
        body: RumorTextTableBuilder.() -> Unit,
    ) {
        rumorTables += RumorTextTableBuilder(id).apply(body).build()
    }

    fun build(): SpeakTextTablesModel =
        SpeakTextTablesModel(
            screens = screens.toList(),
            chitchatPrompts = chitchatPrompts.toList(),
            chitchatReplies = chitchatReplies.toList(),
            questionReplies = questionReplies.toList(),
            rumorTables = rumorTables.toList(),
        )
}

@RylNextDsl
class ChitchatPromptTextBuilder(
    private val subject: ChitchatSubject,
) {
    private val variants = mutableListOf<ChitchatPromptVariantModel>()

    fun any(body: StructuredTextBlockBuilder.() -> Unit) {
        variants +=
            ChitchatPromptVariantModel(
                ChitchatPromptAudience.Any,
                StructuredTextBlockBuilder().apply(body).build(),
            )
    }

    fun female(body: StructuredTextBlockBuilder.() -> Unit) {
        variants +=
            ChitchatPromptVariantModel(
                ChitchatPromptAudience.Female,
                StructuredTextBlockBuilder().apply(body).build(),
            )
    }

    fun male(body: StructuredTextBlockBuilder.() -> Unit) {
        variants +=
            ChitchatPromptVariantModel(
                ChitchatPromptAudience.Male,
                StructuredTextBlockBuilder().apply(body).build(),
            )
    }

    fun build(): ChitchatPromptTextModel = ChitchatPromptTextModel(subject, variants.toList())
}

@RylNextDsl
class JobTextTableBuilder {
    private val replies = mutableListOf<JobTextModel>()

    fun job(
        id: NpcJobId,
        body: StructuredTextBlockBuilder.() -> Unit,
    ) {
        replies += JobTextModel(id, StructuredTextBlockBuilder().apply(body).build())
    }

    fun build(): List<JobTextModel> = replies.toList()
}

@RylNextDsl
class RumorTextTableBuilder(
    private val id: RumorTableId,
) {
    private val entries = mutableListOf<RumorEntryModel>()

    fun rumor(body: RumorEntryBuilder.() -> Unit) {
        entries += RumorEntryBuilder(null).apply(body).build()
    }

    fun rumor(
        id: RumorId,
        body: RumorEntryBuilder.() -> Unit,
    ) {
        entries += RumorEntryBuilder(id).apply(body).build()
    }

    fun build(): RumorTextTableModel = RumorTextTableModel(id, entries.toList())
}

@RylNextDsl
class RumorEntryBuilder(
    private val id: RumorId?,
) : TextLineSink {
    private val text = StructuredTextBlockBuilder()
    private val excludedStates = mutableListOf<FlagState>()

    override fun addTextLine(line: TextLine) {
        text.addTextLine(line)
    }

    fun excludeWhen(vararg states: FlagState) {
        excludedStates += states
    }

    fun build(): RumorEntryModel = RumorEntryModel(id, text.build(), excludedStates.toList())
}

@RylNextDsl
class StandardConversationBuilder {
    var npcSelection: ConversationNpcSelection = ConversationNpcSelection.BuildingListOrWildernessLocation
    var visitPolicy: NpcVisitPolicy = NpcVisitPolicy.PerPartyVisitedBit
    var branchSelection: DialogueBranchSelection = DialogueBranchSelection.FirstMatchingStateThenDefault
    var missionHints: MissionHintPolicy = MissionHintPolicy.BeforeEachMenu

    private var menuModel =
        ConversationMenuBuilder()
            .apply {
                chitchat()
                question {
                    lodging()
                    magic()
                    people()
                    nothing()
                }
                rumor()
                bye()
            }.build()
    private var chitchatModel = ChitchatBuilder().build()
    private var rumorModel = ConversationRumorBuilder().build()
    private var promptsModel = ConversationPromptTextBuilder().build()

    fun menu(body: ConversationMenuBuilder.() -> Unit) {
        menuModel = ConversationMenuBuilder().apply(body).build()
    }

    fun chitchat(body: ChitchatBuilder.() -> Unit) {
        chitchatModel = ChitchatBuilder(chitchatModel).apply(body).build()
    }

    fun rumor(body: ConversationRumorBuilder.() -> Unit) {
        rumorModel = ConversationRumorBuilder(rumorModel).apply(body).build()
    }

    fun prompts(body: ConversationPromptTextBuilder.() -> Unit) {
        promptsModel = ConversationPromptTextBuilder(promptsModel).apply(body).build()
    }

    fun build(): StandardConversationModel =
        StandardConversationModel(
            npcSelection = npcSelection,
            visitPolicy = visitPolicy,
            branchSelection = branchSelection,
            missionHints = missionHints,
            menu = menuModel,
            chitchat = chitchatModel,
            rumor = rumorModel,
            prompts = promptsModel,
            nativeServices = defaultNativeServices,
        )

    companion object {
        private val defaultNativeServices =
            listOf(
                EngineServiceUse(EngineService.KeyboardInput, "NPC selection and conversation menu choices"),
                EngineServiceUse(EngineService.Rendering, "text-window clearing, menu rendering, and dialogue screens"),
                EngineServiceUse(EngineService.RandomNumbers, "chitchat subject and rumor selection"),
                EngineServiceUse(EngineService.NpcStateStorage, "NPC lookup, job/gender/role metadata, and visited bits"),
                EngineServiceUse(EngineService.PartyStateMutation, "dialogue scripts can advance flags, rewards, and journals"),
                EngineServiceUse(EngineService.TextFormatting, "NPC names, jobs, locations, and role-bound text placeholders"),
            )
    }
}

@RylNextDsl
class ConversationPromptTextBuilder(
    initial: ConversationPromptTextModel = ConversationPromptTextModel(),
) {
    var chooseNpc: TextBlock = initial.chooseNpc
    private var mainMenuText: TextBlock = initial.mainMenu

    fun mainMenu(body: ConversationMainMenuPromptBuilder.() -> Unit) {
        mainMenuText = ConversationMainMenuPromptBuilder().apply(body).build()
    }

    fun build(): ConversationPromptTextModel =
        ConversationPromptTextModel(
            chooseNpc = chooseNpc,
            mainMenu = mainMenuText,
        )
}

@RylNextDsl
class ConversationMainMenuPromptBuilder : TextLineSink {
    private val text = StructuredTextBlockBuilder()

    val npcName: TextPart = TextPart.ConversationNpcName

    override fun addTextLine(line: TextLine) {
        text.addTextLine(line)
    }

    fun build(): TextBlock = text.build()
}

@RylNextDsl
class ConversationMenuBuilder {
    private val actions = mutableListOf<ConversationMenuActionModel>()
    private var questionActions = emptyList<QuestionMenuActionModel>()

    fun chitchat(
        key: Char = 'C',
        action: KeyActionId = DslDefaultKeyActions.speakChitchat,
    ) {
        chitchat(inputKey(key), action)
    }

    fun chitchat(
        binding: InputBindingModel,
        action: KeyActionId = DslDefaultKeyActions.speakChitchat,
    ) {
        actions += ConversationMenuActionModel(ConversationTopic.Chitchat, binding, action)
    }

    fun question(
        key: Char = 'Q',
        action: KeyActionId = DslDefaultKeyActions.speakQuestion,
        body: QuestionMenuBuilder.() -> Unit,
    ) {
        question(inputKey(key), action, body)
    }

    fun question(
        binding: InputBindingModel,
        action: KeyActionId = DslDefaultKeyActions.speakQuestion,
        body: QuestionMenuBuilder.() -> Unit,
    ) {
        actions += ConversationMenuActionModel(ConversationTopic.Question, binding, action)
        questionActions = QuestionMenuBuilder().apply(body).build()
    }

    fun rumor(
        key: Char = 'R',
        action: KeyActionId = DslDefaultKeyActions.speakRumor,
    ) {
        rumor(inputKey(key), action)
    }

    fun rumor(
        binding: InputBindingModel,
        action: KeyActionId = DslDefaultKeyActions.speakRumor,
    ) {
        actions += ConversationMenuActionModel(ConversationTopic.Rumor, binding, action)
    }

    fun bye(
        key: Char = 'B',
        action: KeyActionId = DslDefaultKeyActions.speakBye,
    ) {
        bye(inputKey(key), action)
    }

    fun bye(
        binding: InputBindingModel,
        action: KeyActionId = DslDefaultKeyActions.speakBye,
    ) {
        actions += ConversationMenuActionModel(ConversationTopic.Bye, binding, action)
    }

    fun build(): ConversationMenuModel = ConversationMenuModel(actions.toList(), questionActions)
}

@RylNextDsl
class QuestionMenuBuilder {
    private val actions = mutableListOf<QuestionMenuActionModel>()

    fun lodging(
        key: Char = 'L',
        action: KeyActionId = DslDefaultKeyActions.questionLodging,
    ) {
        lodging(inputKey(key), action)
    }

    fun lodging(
        binding: InputBindingModel,
        action: KeyActionId = DslDefaultKeyActions.questionLodging,
    ) {
        actions += QuestionMenuActionModel(QuestionTopic.Lodging, binding, action)
    }

    fun magic(
        key: Char = 'M',
        action: KeyActionId = DslDefaultKeyActions.questionMagic,
    ) {
        magic(inputKey(key), action)
    }

    fun magic(
        binding: InputBindingModel,
        action: KeyActionId = DslDefaultKeyActions.questionMagic,
    ) {
        actions += QuestionMenuActionModel(QuestionTopic.Magic, binding, action)
    }

    fun people(
        key: Char = 'P',
        action: KeyActionId = DslDefaultKeyActions.questionPeople,
    ) {
        people(inputKey(key), action)
    }

    fun people(
        binding: InputBindingModel,
        action: KeyActionId = DslDefaultKeyActions.questionPeople,
    ) {
        actions += QuestionMenuActionModel(QuestionTopic.People, binding, action)
    }

    fun nothing(
        key: Char = 'N',
        action: KeyActionId = DslDefaultKeyActions.questionNothing,
    ) {
        nothing(inputKey(key), action)
    }

    fun nothing(
        binding: InputBindingModel,
        action: KeyActionId = DslDefaultKeyActions.questionNothing,
    ) {
        actions += QuestionMenuActionModel(QuestionTopic.Nothing, binding, action)
    }

    fun build(): List<QuestionMenuActionModel> = actions.toList()
}

@RylNextDsl
class ChitchatBuilder(
    initial: ChitchatModel? = null,
) {
    private var subjectModels: List<ChitchatSubject> =
        initial?.subjects ?: listOf(ChitchatSubject.Well, ChitchatSubject.Weather, ChitchatSubject.Health)
    var replies: ChitchatReplyPolicy =
        initial?.replies ?: ChitchatReplyPolicy.BySubjectAndNpcJob

    fun subjects(vararg subjects: ChitchatSubject) {
        subjectModels = subjects.toList()
    }

    fun build(): ChitchatModel = ChitchatModel(subjectModels, replies)
}

@RylNextDsl
class ConversationRumorBuilder(
    initial: ConversationRumorModel? = null,
) {
    var usage: RumorUsagePolicy =
        initial?.usage ?: RumorUsagePolicy.OncePerConversationThenFallback
    var source: RumorSourcePolicy =
        initial?.source ?: RumorSourcePolicy.StandardTownRumors

    fun build(): ConversationRumorModel = ConversationRumorModel(usage, source)
}
