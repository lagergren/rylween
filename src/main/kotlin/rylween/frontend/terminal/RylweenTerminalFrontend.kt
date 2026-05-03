// Line-oriented terminal frontend API and rendering helpers.
// Runtime sessions expose semantic steps and bindings; this layer decides how a text terminal displays them.

package rylween.frontend.terminal

import rylween.dsl.*
import rylween.engine.*
import rylween.runtime.*

interface TerminalPort {
    fun write(lines: List<String>)
    fun writeBlocks(
        blocks: List<TextBlock>,
        renderer: TerminalTextRenderer,
        context: TerminalTextContext,
    ) {
        write(blocks.flatMap { block -> renderer.lines(block, context) })
    }

    fun readLine(): String?
    fun readTextLine(maxLength: Int? = null): String? = readLine()
    fun readReturn(): Boolean? = readLine()?.let { true }
    fun pause(ms: Int) {}
    fun writeViewCommand(
        command: ViewCommand,
        renderer: TerminalTextRenderer,
        context: TerminalTextContext,
    ): Boolean = false
    fun writeEngineEvent(
        event: EngineTraceEvent,
        renderer: TerminalTextRenderer,
        context: TerminalTextContext,
    ): Boolean =
        when (event) {
            is EngineTraceEvent.View -> writeViewCommand(event.command, renderer, context)
            else -> false
        }
}

fun TerminalPort.writeKeyPrompt(
    renderer: TerminalTextRenderer,
    block: TextBlock,
    keys: List<Char>,
    context: TerminalTextContext = TerminalTextContext(),
) {
    write(renderer.keyPromptLines(block, keys, context))
}

fun TerminalPort.writeBindingPrompt(
    renderer: TerminalTextRenderer,
    block: TextBlock,
    bindings: List<InputBindingModel>,
    context: TerminalTextContext = TerminalTextContext(),
) {
    write(renderer.bindingPromptLines(block, bindings, context))
}

fun TerminalPort.writeLabelPrompt(
    renderer: TerminalTextRenderer,
    block: TextBlock,
    labels: List<String>,
    context: TerminalTextContext = TerminalTextContext(),
) {
    write(renderer.choicePromptLines(block, labels, markerKeys = emptyList(), context))
}

data class TerminalTextContext(
    val shopPrice: Int? = null,
    val shopCarryCap: Int? = null,
    val offeredItemName: String? = null,
    val offeredItemPrice: Int? = null,
    val runtime: RuntimeSessionState? = null,
    val huntMemberNumber: Int? = null,
    val huntFoodGained: Int? = null,
)

data class TerminalInputRejection(
    val input: String,
    val expected: List<String>,
)

sealed interface TerminalRunStatus {
    object Finished : TerminalRunStatus
    data class Waiting(val rejection: TerminalInputRejection? = null) : TerminalRunStatus
    object StepLimitReached : TerminalRunStatus
}

data class TerminalAdapterResult<S>(
    val session: S,
    val status: TerminalRunStatus,
)

fun GameModel.terminalTextRenderer(references: Map<String, List<String>> = emptyMap()): TerminalTextRenderer =
    TerminalTextRenderer(
        references = references,
        towns = towns,
        characters = characters,
        prompts = prompts,
    )

class TerminalTextRenderer(
    private val references: Map<String, List<String>> = emptyMap(),
    private val towns: List<TownModel> = emptyList(),
    private val characters: List<CharacterModel> = emptyList(),
    private val prompts: GamePromptsModel = GamePromptsModel(),
) {
    fun lines(block: TextBlock, context: TerminalTextContext = TerminalTextContext()): List<String> =
        when (block) {
            is TextBlock.Inline -> block.lines
            is TextBlock.Ref -> references[block.id.id] ?: listOf("[${block.id.id}]")
            is TextBlock.Structured -> block.lines.map { line ->
                line.parts.joinToString(separator = "") { part -> render(part, context) }
            }
        }

    fun worldMapViewportLines(viewport: WorldMapViewport): List<String> =
        viewport.rows.map { row ->
            row.joinToString(separator = "") { cell ->
                if (cell.isCenter) "@" else (cell.tile ?: ' ').toString()
            }
        }

    fun keyPromptLines(
        block: TextBlock,
        keys: List<Char>,
        context: TerminalTextContext = TerminalTextContext(),
    ): List<String> =
        choicePromptLines(block, keys.map { it.toString() }, markerKeys = keys, context)

    fun bindingPromptLines(
        block: TextBlock,
        bindings: List<InputBindingModel>,
        context: TerminalTextContext = TerminalTextContext(),
    ): List<String> =
        choicePromptLines(
            block,
            labels = bindings.map { it.label },
            markerKeys = bindings.map { it.primaryChar },
            context = context,
        )

    fun choicePromptLines(
        block: TextBlock,
        labels: List<String>,
        markerKeys: List<Char>,
        context: TerminalTextContext = TerminalTextContext(),
    ): List<String> {
        val rendered = lines(block, context)
        val options = if (labels.isEmpty() || rendered.containsKeyMarkers(markerKeys)) {
            emptyList()
        } else {
            listOf("Options: ${labels.joinToString("/")}")
        }
        return rendered + options
    }

    private fun render(part: TextPart, context: TerminalTextContext): String =
        when (part) {
            is TextPart.Literal -> part.text
            is TextPart.IntValue -> render(part.value)
            is TextPart.StringValue -> render(part.value, context)
            is TextPart.RoleName -> characterFor(part.role)?.name ?: "{${part.role.slug}.name}"
            is TextPart.RoleLocation -> roleLocation(part.role, context.runtime) ?: "{${part.role.slug}.location}"
            is TextPart.TownName -> townName(part.town)
            is TextPart.FuzzyTownLocation -> townLocation(part.town, part.radius) ?: "${part.town.slug}.location"
            is TextPart.LocationCoordinate -> locationCoordinate(part.location, context.runtime)
                ?: "{${part.location.slug}.coordinates}"
            TextPart.OfferedItemName -> context.offeredItemName ?: "{item_name}"
            TextPart.OfferedItemPrice -> context.offeredItemPrice?.toString() ?: "{item_price}"
            TextPart.ShopPrice -> context.shopPrice?.toString() ?: "{price}"
            TextPart.ShopCarryCap -> context.shopCarryCap?.toString() ?: "{cap}"
            TextPart.HuntMemberName -> huntMember(context)?.name ?: "{who_name}"
            TextPart.HuntMemberSubjectPronoun -> huntMemberSubjectPronoun(context) ?: "{pro_sub}"
            TextPart.HuntFoodGained -> context.huntFoodGained?.toString() ?: "{fdplus}"
            TextPart.PartyMemberMax -> context.runtime?.realm?.partyMemberMax?.toString() ?: "{party_max}"
        }

    fun pausePrompt(verbIndex: Int? = null): TextBlock {
        val verb = verbIndex
            ?.let { prompts.pauseVerbs.getOrNull(it) }
            ?: prompts.pauseVerbs.firstOrNull()
            ?: "Press"
        return inlineText("($verb ${prompts.returnKeyName})")
    }

    fun pauseVerbIndexRange(): IntRange? =
        prompts.pauseVerbs.takeIf { it.isNotEmpty() }?.indices

    private fun render(ast: StrAst, context: TerminalTextContext): String =
        when (ast) {
            is StrAst.NativeString -> when (ast.path) {
                NativeAllowlist.partyMemberName.path -> {
                    val index = ast.args.singleOrNull()?.literalInt()
                    index?.let { context.runtime?.activeParty?.members?.getOrNull(it - 1)?.name } ?: "{party.name}"
                }
                NativeAllowlist.partyPronounSubject.path ->
                    renderPronoun(ast, context, PronounCase.Subject)
                NativeAllowlist.partyPronounObject.path ->
                    renderPronoun(ast, context, PronounCase.Object)
                NativeAllowlist.partyPronounPossessive.path ->
                    renderPronoun(ast, context, PronounCase.Possessive)
                else -> "{${ast.path}}"
            }
        }

    private fun renderPronoun(ast: StrAst.NativeString, context: TerminalTextContext, case: PronounCase): String {
        val index = ast.args.singleOrNull()?.literalInt()
        val gender = index?.let { context.runtime?.activeParty?.members?.getOrNull(it - 1)?.gender } ?: return "{pronoun}"
        return when (case) {
            PronounCase.Subject -> if (gender == Gender.Female) "she" else "he"
            PronounCase.Object -> if (gender == Gender.Female) "her" else "him"
            PronounCase.Possessive -> if (gender == Gender.Female) "her" else "his"
        }
    }

    private fun huntMember(context: TerminalTextContext): PartyMemberState? {
        val memberNumber = context.huntMemberNumber ?: return null
        return context.runtime?.activeParty?.members?.getOrNull(memberNumber - 1)
    }

    private fun huntMemberSubjectPronoun(context: TerminalTextContext): String? =
        huntMember(context)?.gender?.let { gender ->
            if (gender == Gender.Female) "she" else "he"
        }

    private fun NativeStringArg.literalInt(): Int? =
        when (this) {
            is NativeStringArg.IntValue -> (value as? IntAst.Lit)?.value
            is NativeStringArg.Literal -> value.toIntOrNull()
        }

    private fun characterFor(role: NpcRole): CharacterModel? =
        characters.firstOrNull { role in it.roles }

    private fun roleLocation(role: NpcRole, runtime: RuntimeSessionState?): String? {
        val character = characterFor(role) ?: return null
        val placement = runtime
            ?.npcs
            ?.firstOrNull { it.npc == character.id }
            ?.position
            ?: (character.placement as? CharacterPlacementModel.Fixed)?.position
            ?: return null
        return position(placement)
    }

    private fun position(position: WorldPosition): String =
        when (position) {
            is WorldPosition.Town -> "in ${townName(position.town)}"
            is WorldPosition.Wilderness -> "at ${point(position.point)}"
        }

    private fun townName(town: TownId): String =
        towns.firstOrNull { it.id == town }?.name ?: town.slug

    private fun townLocation(town: TownId, radius: Int): String? {
        val model = towns.firstOrNull { it.id == town } ?: return null
        return point(fuzzyPoint(model.position, town.slug, radius))
    }

    private fun locationCoordinate(location: LocationId, runtime: RuntimeSessionState?): String? =
        runtime
            ?.locations
            ?.firstOrNull { it.location == location }
            ?.point
            ?.let(::point)

    private fun fuzzyPoint(point: GridPoint, slug: String, radius: Int): GridPoint =
        if (radius <= 0) {
            point
        } else {
            GridPoint(
                x = point.x + stableOffset("$slug:x", radius),
                y = point.y + stableOffset("$slug:y", radius),
            )
        }

    private fun stableOffset(key: String, radius: Int): Int {
        val span = radius * 2 + 1
        val hash = key.fold(0) { acc, char -> acc * 31 + char.code }
        return Math.floorMod(hash, span) - radius
    }

    private fun point(point: GridPoint): String = "${point.x}x${point.y}"

    private fun render(ast: IntAst): String =
        when (ast) {
            is IntAst.Lit -> ast.value.toString()
            else -> "{int}"
        }
}

fun TerminalPort.writeScriptEvents(
    events: List<EngineTraceEvent>,
    renderer: TerminalTextRenderer,
    runtime: RuntimeSessionState,
) {
    val context = TerminalTextContext(runtime = runtime)
    events.forEach { event ->
        if (writeEngineEvent(event, renderer, context)) {
            return@forEach
        }
        if (writePauseEvent(event, renderer, context)) {
            return@forEach
        }
        when (event) {
            is EngineTraceEvent.View -> Unit
            is EngineTraceEvent.Screen -> write(renderer.lines(event.text, context))
            is EngineTraceEvent.Emit -> write(listOf("[${event.id.id}]"))
            is EngineTraceEvent.EmitIndexed -> write(listOf("[${event.pattern.pattern}: ${event.value}]"))
            is EngineTraceEvent.Menu -> writeKeyPrompt(renderer, event.prompt, event.keys, context)
            is EngineTraceEvent.AskKey -> writeBindingPrompt(renderer, event.prompt, event.bindings, context)
            is EngineTraceEvent.AskText -> write(renderer.lines(event.prompt, context))
            is EngineTraceEvent.AskMember -> write(renderer.lines(event.prompt, context))
            is EngineTraceEvent.AskInventorySlot -> write(renderer.lines(event.prompt, context))
            is EngineTraceEvent.NativeEffect -> write(listOf("Native: ${event.request.path}"))
            is EngineTraceEvent.NativeInt -> write(listOf("Native int: ${event.request.path} = ${event.result}"))
            is EngineTraceEvent.NativeString -> write(listOf("Native string: ${event.request.path} = ${event.result}"))
            is EngineTraceEvent.RunScene -> write(listOf("Scene: ${event.scene.slug}"))
            is EngineTraceEvent.DungeonHandoff ->
                write(renderer.lines(WildernessPromptText.dungeonHandoffPending(event.handoff), context))
            is EngineTraceEvent.TeleportParty,
            is EngineTraceEvent.DiscoverCave -> Unit
            is EngineTraceEvent.Stop -> write(listOf(event.reason ?: "Stop"))
            is EngineTraceEvent.Break -> write(listOf(event.reason ?: "Break"))
            is EngineTraceEvent.WaitInput,
            is EngineTraceEvent.Pause,
            is EngineTraceEvent.SetFlag,
            is EngineTraceEvent.AppendJournal,
            is EngineTraceEvent.GrantReward,
            is EngineTraceEvent.AchieveMilestone,
            is EngineTraceEvent.Speak,
            is EngineTraceEvent.Encounter,
            is EngineTraceEvent.RandomItemOffer,
            is EngineTraceEvent.ShopService,
            is EngineTraceEvent.ShopSellQuote,
            is EngineTraceEvent.ShopTransaction,
            is EngineTraceEvent.EquipmentAction,
            is EngineTraceEvent.TownService,
            is EngineTraceEvent.ConsumeInventoryPool,
            EngineTraceEvent.ConsumeTorch -> Unit
        }
    }
}

fun TerminalPort.writePauseEvent(
    event: EngineTraceEvent,
    renderer: TerminalTextRenderer,
    context: TerminalTextContext,
): Boolean =
    when (event) {
        is EngineTraceEvent.WaitInput -> true.also {
            write(renderer.lines(event.prompt ?: renderer.pausePrompt(), context))
        }
        is EngineTraceEvent.Pause -> true.also {
            pause(event.ms)
        }
        else -> false
    }


sealed interface TerminalInput<out T> {
    data class Value<T>(val value: T) : TerminalInput<T>
    data class Rejected(val rejection: TerminalInputRejection) : TerminalInput<Nothing>
    object Unavailable : TerminalInput<Nothing>
}

data class TerminalChoice<out T>(
    val value: T,
    val binding: InputBindingModel,
)

fun <T> List<InputCommand<T>>.terminalChoices(): List<TerminalChoice<T>> =
    map { command -> TerminalChoice(command.input, command.binding) }

fun InputBindingModel.promptValue(): Int =
    primary.singleOrNull()?.let { char ->
        char.digitToIntOrNull() ?: char.uppercaseChar().code
    } ?: primary.uppercase().hashCode()

fun <T, R> TerminalInput<T>.map(transform: (T) -> R): TerminalInput<R> =
    when (this) {
        is TerminalInput.Value -> TerminalInput.Value(transform(value))
        is TerminalInput.Rejected -> this
        TerminalInput.Unavailable -> TerminalInput.Unavailable
    }

fun TerminalPort.readKey(allowed: List<Char>): TerminalInput<Char> {
    val choices = allowed.map { key -> TerminalChoice(key, inputKey(key, label = key.toString())) }
    return readChoice(choices)
}

fun yesNoChoices(keys: YesNoKeysModel = StandardYesNoKeys): List<TerminalChoice<Boolean>> =
    listOf(
        TerminalChoice(true, keys.yes),
        TerminalChoice(false, keys.no),
    )

fun yesNoKeys(keys: YesNoKeysModel = StandardYesNoKeys): List<Char> =
    yesNoChoices(keys).map { it.binding.primaryChar }

fun TerminalPort.readYesNo(keys: YesNoKeysModel = StandardYesNoKeys): TerminalInput<Boolean> =
    readChoice(yesNoChoices(keys))

fun <T> TerminalPort.readChoice(choices: List<TerminalChoice<T>>): TerminalInput<T> {
    val raw = readLine() ?: return TerminalInput.Unavailable
    val choice = choices.firstOrNull { it.binding.matches(raw) }
    return if (choice != null) {
        TerminalInput.Value(choice.value)
    } else {
        TerminalInput.Rejected(TerminalInputRejection(raw, choices.map { it.binding.label }.distinct()))
    }
}

fun TerminalPort.readInt(): TerminalInput<Int> {
    val raw = readLine() ?: return TerminalInput.Unavailable
    return raw.trim().toIntOrNull()
        ?.let { TerminalInput.Value(it) }
        ?: TerminalInput.Rejected(TerminalInputRejection(raw, listOf("integer")))
}

fun TerminalPort.readMemberNumber(allowed: List<Int>): TerminalInput<Int> {
    val raw = readLine() ?: return TerminalInput.Unavailable
    val memberNumber = raw.trim().toIntOrNull()
    return if (memberNumber != null && memberNumber in allowed) {
        TerminalInput.Value(memberNumber)
    } else {
        TerminalInput.Rejected(TerminalInputRejection(raw, allowed.map { it.toString() }))
    }
}

fun <S> finished(session: S): TerminalAdapterResult<S> =
    TerminalAdapterResult(session, TerminalRunStatus.Finished)

fun <S> waiting(
    session: S,
    rejection: TerminalInputRejection? = null,
): TerminalAdapterResult<S> =
    TerminalAdapterResult(session, TerminalRunStatus.Waiting(rejection))
fun PartyState.inventoryKeys(filter: InventorySlotChoiceFilter): List<Char> =
    inventorySlotChoices(filter, 1..InventorySaveSlotMax, 'A').map { it.first }

fun PartyState.inventorySlotChoices(
    filter: InventorySlotChoiceFilter,
    slots: IntRange,
    firstKey: Char,
): List<Pair<Char, Int>> {
    val itemsBySlot = inventory.associate { it.slot to it.item }
    return slots
        .filter { slot ->
            slot in 1..InventorySaveSlotMax && when (filter) {
                InventorySlotChoiceFilter.Any -> true
                InventorySlotChoiceFilter.NonEmpty -> itemsBySlot[slot] != null
                InventorySlotChoiceFilter.Empty -> itemsBySlot[slot] == null
            }
        }
        .map { slot -> firstKey + (slot - slots.first) to slot }
}

fun PartyState.memberChoices(filter: MemberChoiceFilter): List<Int> =
    members
        .mapIndexedNotNull { index, member ->
            (index + 1).takeIf { member.matches(filter) }
        }

fun PartyMemberState.matches(filter: MemberChoiceFilter): Boolean =
    when (filter) {
        MemberChoiceFilter.Any -> true
        MemberChoiceFilter.AliveOnly -> !dead
        MemberChoiceFilter.DeadOnly -> dead
        MemberChoiceFilter.SpellcasterOnly -> !dead && pcClass in setOf(PcClass.Mage, PcClass.Priest)
    }
private fun List<String>.containsKeyMarkers(keys: List<Char>): Boolean {
    val promptLines = filter { it.isNotBlank() }
    return promptLines.isNotEmpty() && keys.all { key ->
        val marker = "(${key.uppercaseChar()})"
        promptLines.any { line -> line.contains(marker, ignoreCase = true) }
    }
}
