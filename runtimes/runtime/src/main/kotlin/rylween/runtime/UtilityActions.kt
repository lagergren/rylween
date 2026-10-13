/**
 * Shared party utility commands for the runtime.
 *
 * Dungeon, castle, and wilderness command loops all need the same small set of
 * inventory, equipment, potion, and priest-spell mutations. This file consumes
 * compiled item/spell/script rows directly and keeps those commands out of the
 * old Kotlin utility sessions.
 */

package rylween.runtime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledInventorySlot
import rylween.compiled.CompiledItemRecord
import rylween.compiled.CompiledPartyMember
import rylween.compiled.CompiledScriptProgram
import rylween.compiled.CompiledSpell
import rylween.compiled.ExternalRuntimeSystems
import rylween.compiled.ExternalRuntimeTextBlock
import rylween.dsl.AudioCue
import rylween.dsl.impl.EquipmentCommand
import rylween.dsl.impl.INVENTORY_SAVE_SLOT_MAX
import rylween.dsl.impl.ItemConsumePolicy
import rylween.dsl.impl.ItemKind
import rylween.dsl.impl.ItemStorageKind
import rylween.dsl.impl.ItemUseContext
import rylween.dsl.impl.PcClass

private const val INVENTORY_FIRST_KEY = 'A'

/** Formats priest spell menu rows with a readable gap between the key mnemonic and spell name. */
internal fun priestSpellMenuLine(
    key: Char,
    spellName: String,
): String = "($key) $spellName"

/** Direct compiled implementations for reusable party utility commands. */
internal class UtilityActions(
    private val content: CompiledContentBundle,
    private val systems: ExternalRuntimeSystems,
    private val input: RuntimeInputPort,
    private val screen: RuntimeScreenPort,
    private val log: RuntimeLog,
) {
    private val audio = RuntimeAudio(systems.audio, screen)

    fun drop(runtime: RuntimeState): RuntimeState {
        val choices = runtime.party.inventory.filter { slot -> slot.item != null }
        if (choices.isEmpty()) return runtime
        val lines =
            screen.inventoryPickerLines(
                systems.prompts.party.dropPrompt
                    .renderUtility(runtime),
                choices.inventoryChoiceLines(::itemName),
                returnCancelLines(),
            )
        screen.writeText(lines)
        val slot = chooseInventorySlot(lines, choices) ?: return runtime
        audio.cue(AudioCue.Drop)
        return runtime.withParty(runtime.party.withInventoryItem(slot, null))
    }

    fun readyWeapon(runtime: RuntimeState): RuntimeState = readyEquipment(runtime, EquipmentCommand.ReadyWeapon).shown()

    fun readyArmor(runtime: RuntimeState): RuntimeState = readyEquipment(runtime, EquipmentCommand.ReadyArmor).shown()

    /**
     * Asks the town-leave equipment questions in [commands] order. A question comes
     * up while the party carries an item of its kind that a living member with
     * nothing of that kind equipped can use; each yes readies one item and asks
     * again. The result's message is the outcome of the last ready, not yet shown.
     */
    fun readyBeforeLeavingTown(
        runtime: RuntimeState,
        commands: List<EquipmentCommand>,
    ): EquipmentReadyResult {
        var result = EquipmentReadyResult(runtime, emptyList())
        for (command in commands) {
            while (result.runtime.canReadyIntoEmptySlot(command)) {
                val lines =
                    result.message.withChoiceGap() +
                        command
                            .leavePrompt()
                            .renderUtility(result.runtime)
                screen.writeText(lines)
                val answer =
                    input.readValidChoice(screen, lines, audio) { key -> key.uppercaseChar() in readyAnswerKeys }
                        ?: return result
                if (answer.uppercaseChar() != READY_YES_KEY) {
                    result = result.copy(message = emptyList())
                    break
                }
                result = readyEquipment(result.runtime, command)
            }
        }
        return result
    }

    fun removeWeapon(runtime: RuntimeState): RuntimeState =
        removeEquipment(
            runtime = runtime,
            equippedItem = { member -> member.equipment.weapon },
            fallbackCue = AudioCue.UnreadyWeapon,
        ) { member ->
            member.copy(equipment = member.equipment.copy(weapon = content.emptyItem()))
        }

    fun removeArmor(runtime: RuntimeState): RuntimeState =
        removeEquipment(
            runtime = runtime,
            equippedItem = { member -> member.equipment.armor },
            fallbackCue = AudioCue.UnreadyArmor,
        ) { member ->
            member.copy(equipment = member.equipment.copy(armor = content.emptyItem()))
        }

    fun quaff(runtime: RuntimeState): RuntimeState {
        val choices =
            runtime.party.inventory.mapNotNull { slot ->
                val item = slot.item ?: return@mapNotNull null
                val use =
                    content.itemUses.firstOrNull { use -> use.item == item && ItemUseContext.Inventory in use.contexts }
                slot.takeIf { use != null && itemRecord(item).kind == ItemKind.Potion }
            }
        if (choices.isEmpty()) return runtime
        val lines =
            screen.inventoryPickerLines(
                systems.prompts.inventoryUse.quaff
                    .renderUtility(runtime),
                choices.inventoryChoiceLines(::itemName),
                returnCancelLines(),
            )
        screen.writeText(lines)
        val slot = chooseInventorySlot(lines, choices) ?: return runtime
        val item = runtime.party.inventoryItem(slot) ?: return runtime
        audio.cue(AudioCue.Quaff)
        audio.itemUse(content.slug(item))
        val use =
            content.itemUses.firstOrNull { candidate -> candidate.item == item && ItemUseContext.Inventory in candidate.contexts }
        val prepared =
            when (use?.consume) {
                ItemConsumePolicy.BeforeUse -> runtime.withParty(runtime.party.withInventoryItem(slot, null))
                else -> runtime
            }
        val program = use?.scriptRoot?.let(::programByRef) ?: return prepared
        return ScriptExecutor(content, systems, input, screen, prepared, activeInventorySlot = slot, log = log)
            .execute(program)
    }

    fun castPriestSpell(runtime: RuntimeState): RuntimeState {
        val casterIndex = chooseCaster(runtime) ?: return runtime
        val spells =
            content.spells
                .filter { spell -> content.slug(spell.id).startsWith(PRIEST_SPELL_PREFIX) }
                .sortedBy(CompiledSpell::slot)
        if (spells.isEmpty()) {
            screen.showMessage(
                systems.prompts.priest.noSpells
                    .renderUtility(runtime),
            )
            return runtime
        }
        val choices = spells.withMenuKeys(priestSpellFirstKey())
        val lines = priestSpellLines(choices)
        screen.writeText(lines)
        val cancelKey = priestCancelKey()
        val command =
            input
                .readValidChoice(screen, lines, audio) { candidate ->
                    val key = candidate.uppercaseChar()
                    key == cancelKey || choices.any { choice -> choice.key == key }
                }?.uppercaseChar() ?: return runtime
        if (command == cancelKey) return runtime
        val spell = choices.firstOrNull { choice -> choice.key == command }?.value ?: return runtime
        audio.spell(content.slug(spell.id))
        val program = programByRef(spell.scriptRoot)
        return ScriptExecutor(
            content = content,
            systems = systems,
            input = input,
            screen = screen,
            initialRuntime = runtime,
            initialLocals = mapOf("caster_idx" to casterIndex + 1),
            log = log,
        ).execute(program)
    }

    private fun readyEquipment(
        runtime: RuntimeState,
        command: EquipmentCommand,
    ): EquipmentReadyResult {
        val unchanged = EquipmentReadyResult(runtime, emptyList())
        val memberIndex = chooseMember(runtime) ?: return unchanged
        val member = runtime.party.members.getOrNull(memberIndex) ?: return unchanged
        val choices =
            runtime.party.inventory.filter { slot ->
                slot.item?.let { item -> itemRecord(item).canBeReadiedBy(member, command) } == true
            }
        if (choices.isEmpty()) {
            return EquipmentReadyResult(
                runtime,
                systems.prompts.equipment.noAvailable
                    .renderUtility(runtime, equipmentKindName = command.itemKind().equipmentName()),
            )
        }
        val lines =
            screen.inventoryPickerLines(
                systems.prompts.equipment.inventory
                    .renderUtility(runtime),
                choices.inventoryChoiceLines(::itemName),
                returnCancelLines(),
            )
        screen.writeText(lines)
        val slot = chooseInventorySlot(lines, choices) ?: return unchanged
        val item = runtime.party.inventoryItem(slot) ?: return unchanged
        val previous = member.equipped(command).takeUnless(::isEmptyItem)
        audio.ready(content.slug(item), if (command == EquipmentCommand.ReadyWeapon) AudioCue.ReadyWeapon else AudioCue.ReadyArmor)
        return EquipmentReadyResult(
            runtime =
                runtime
                    .withParty(runtime.party.withInventoryItem(slot, previous))
                    .updateMember(memberIndex) { current -> current.withEquipped(command, item) },
            message =
                systems.prompts.equipment.equipped
                    .renderUtility(runtime, selectedItem = item),
        )
    }

    private fun EquipmentReadyResult.shown(): RuntimeState {
        if (message.isNotEmpty()) screen.writeText(message)
        return runtime
    }

    private fun RuntimeState.canReadyIntoEmptySlot(command: EquipmentCommand): Boolean {
        val emptyHanded = party.members.filter { member -> !member.dead && isEmptyItem(member.equipped(command)) }
        return party.inventory.any { slot ->
            val record = slot.item?.let(::itemRecord) ?: return@any false
            emptyHanded.any { member -> record.canBeReadiedBy(member, command) }
        }
    }

    private fun CompiledItemRecord.canBeReadiedBy(
        member: CompiledPartyMember,
        command: EquipmentCommand,
    ): Boolean = kind == command.itemKind() && (equippableBy.isEmpty() || member.pcClass in equippableBy)

    private fun EquipmentCommand.leavePrompt(): ExternalRuntimeTextBlock =
        when (this) {
            EquipmentCommand.ReadyWeapon -> systems.prompts.equipment.readyWeapons
            EquipmentCommand.ReadyArmor -> systems.prompts.equipment.readyArmors
        }

    private fun ItemKind.equipmentName(): String =
        when (this) {
            ItemKind.Weapon -> "weapon"
            ItemKind.Armor -> "armor"
            else -> "item"
        }

    private fun removeEquipment(
        runtime: RuntimeState,
        equippedItem: (rylween.compiled.CompiledPartyMember) -> CompiledHandleRef,
        fallbackCue: AudioCue,
        update: (rylween.compiled.CompiledPartyMember) -> rylween.compiled.CompiledPartyMember,
    ): RuntimeState {
        val memberIndex = chooseMember(runtime) ?: return runtime
        val member = runtime.party.members.getOrNull(memberIndex) ?: return runtime
        val item = equippedItem(member).takeUnless(::isEmptyItem) ?: return runtime
        val party = placeLooseItem(runtime.party, item) ?: return runtime
        audio.unready(content.slug(item), fallbackCue)
        return runtime.withParty(party).updateMember(memberIndex, update)
    }

    private fun chooseInventorySlot(
        lines: List<String>,
        choices: List<CompiledInventorySlot>,
    ): Int? =
        input.readValidInventorySlotChoice(
            screen = screen,
            lines = lines,
            validSlots = choices.mapTo(mutableSetOf(), CompiledInventorySlot::slot),
            cancelKeys = inventoryCancelKeys,
            audio = audio,
        )

    private fun placeLooseItem(
        party: PartyState,
        item: CompiledHandleRef,
    ): PartyState? {
        val freeSlot = party.inventory.firstOrNull { slot -> slot.item == null }?.slot
        if (freeSlot != null) return party.withInventoryItem(freeSlot, item)
        val replacementSlot =
            chooseDropSlotForInventoryOverflow(
                systems = systems,
                input = input,
                screen = screen,
                party = party,
                itemName = ::itemName,
            ) ?: return null
        return party.withInventoryItem(replacementSlot, item)
    }

    private fun returnCancelLines(): List<String> = listOf(systems.prompts.returnLeavesLine())

    private fun chooseCaster(runtime: RuntimeState): Int? {
        if (runtime.party.members.none { member -> !member.dead && member.pcClass == PcClass.Priest }) {
            screen.showMessage(
                systems.prompts.priest.wrongClass
                    .renderUtility(runtime),
            )
            return null
        }
        val baseLines =
            screen.choiceMenuLines(
                systems.prompts.priest.who
                    .renderUtility(runtime),
                List(runtime.party.members.size) { index -> "${index + 1}. ${runtime.party.memberName(content, index)}" },
                systems.prompts.priest.none
                    .renderUtility(),
            )
        var rejection = emptyList<String>()
        while (true) {
            val lines = baseLines + rejection
            screen.writeText(lines)
            val command = input.readCommand()?.uppercaseChar() ?: return null
            if (command == priestCancelKey()) return null
            val index = command.digitToIntOrNull()?.minus(1)
            val member = index?.let(runtime.party.members::getOrNull)
            rejection =
                when {
                    index == null ->
                        systems.prompts.priest.invalidCaster
                            .renderUtility()
                    member == null ->
                        systems.prompts.priest.invalidCaster
                            .renderUtility()
                    member.dead ->
                        systems.prompts.priest.dead
                            .renderUtility()
                    member.pcClass != PcClass.Priest ->
                        systems.prompts.priest.wrongClass
                            .renderUtility()
                    else -> return index
                }
        }
    }

    private fun priestSpellLines(spells: List<MenuChoice<CompiledSpell>>): List<String> =
        screen.choiceMenuLines(
            prompt = emptyList(),
            choices = spells.map { choice -> priestSpellMenuLine(choice.key, content.strings.value(choice.value.nameString)) },
            trailer =
                systems.prompts.priest.none
                    .renderUtility(),
        )

    private fun priestCancelKey(): Char =
        systems.prompts.priest.noneKey
            .singleRuntimeKey("priest none key")

    private fun priestSpellFirstKey(): Char =
        systems.prompts.priest.firstSpellKey
            .singleRuntimeKey("priest first spell key")

    private fun chooseMember(runtime: RuntimeState): Int? {
        val choices =
            runtime.party.members
                .mapIndexedNotNull { index, member -> index.takeIf { !member.dead } }
        return chooseMemberIndex(runtime, choices)
    }

    private fun chooseMemberIndex(
        runtime: RuntimeState,
        indexes: List<Int>,
    ): Int? {
        if (indexes.isEmpty()) return null
        val lines =
            systems.prompts.equipment.who
                .renderUtility(runtime) +
                "" +
                indexes.map { index ->
                    "${index + 1}. ${runtime.party.memberName(content, index)}"
                } +
                systems.prompts.returnLeavesLine()
        screen.writeText(lines)
        return input
            .readValidDigitChoice(
                screen = screen,
                lines = lines,
                validDigits = indexes.mapTo(mutableSetOf()) { index -> index + 1 },
                cancelKeys = inventoryCancelKeys,
                audio = audio,
            )?.minus(1)
    }

    private fun programByRef(root: CompiledHandleRef): CompiledScriptProgram =
        content.scripts.roots.firstOrNull { program -> program.rootHandle == root }
            ?: error("missing compiled script root ${content.slug(root)}")

    private fun CompiledContentBundle.emptyItem(): CompiledHandleRef =
        items
            .firstOrNull { record -> record.kind == ItemKind.Empty && record.storage == ItemStorageKind.EmptySentinel }
            ?.id
            ?: items.first { record -> record.kind == ItemKind.Empty }.id

    private fun itemName(item: CompiledHandleRef): String = content.strings.value(itemRecord(item).displayNameString)

    private fun isEmptyItem(item: CompiledHandleRef): Boolean = itemRecord(item).kind == ItemKind.Empty

    private fun ExternalRuntimeTextBlock.renderUtility(
        runtime: RuntimeState? = null,
        selectedItem: CompiledHandleRef? = null,
        inventorySlotName: String? = null,
        equipmentKindName: String? = null,
    ): List<String> =
        render(
            values = { kind ->
                when (kind) {
                    "selectedItemName" -> selectedItem?.let(::itemName)
                    "inventorySlotName" -> inventorySlotName
                    "equipmentKindName" -> equipmentKindName
                    else -> runtime?.runtimeTextValue(content, kind)
                } ?: when (kind) {
                    "partyMemberMax" ->
                        systems.characterCreation
                            ?.start
                            ?.partyMemberMax
                            ?.toString()
                    else -> null
                }
            },
            wrapWidth = screen.textColumns,
        )

    private fun itemRecord(item: CompiledHandleRef): CompiledItemRecord =
        content.items.firstOrNull { record -> record.id == item }
            ?: error("missing item record ${content.slug(item)}")
}

internal fun RuntimeState.updateMember(
    memberIndex: Int,
    update: (rylween.compiled.CompiledPartyMember) -> rylween.compiled.CompiledPartyMember,
): RuntimeState =
    withParty(
        party.copy(
            members =
                party.members.mapIndexed { index, member ->
                    member.takeUnless { index == memberIndex } ?: update(member)
                },
        ),
    )

/** The party after a ready attempt, and the outcome text to show for it (empty when cancelled). */
internal data class EquipmentReadyResult(
    val runtime: RuntimeState,
    val message: List<String>,
)

private fun EquipmentCommand.itemKind(): ItemKind =
    when (this) {
        EquipmentCommand.ReadyWeapon -> ItemKind.Weapon
        EquipmentCommand.ReadyArmor -> ItemKind.Armor
    }

private fun CompiledPartyMember.equipped(command: EquipmentCommand): CompiledHandleRef =
    when (command) {
        EquipmentCommand.ReadyWeapon -> equipment.weapon
        EquipmentCommand.ReadyArmor -> equipment.armor
    }

private fun CompiledPartyMember.withEquipped(
    command: EquipmentCommand,
    item: CompiledHandleRef,
): CompiledPartyMember =
    when (command) {
        EquipmentCommand.ReadyWeapon -> copy(equipment = equipment.copy(weapon = item))
        EquipmentCommand.ReadyArmor -> copy(equipment = equipment.copy(armor = item))
    }

private const val PRIEST_SPELL_PREFIX = "priest."

// The town-leave questions offer (Y)es and (N)o, like the game's other yes/no prompts.
private const val READY_YES_KEY = 'Y'
private val readyAnswerKeys = setOf(READY_YES_KEY, 'N')
private val inventoryCancelKeys = setOf('\n')

private fun String.singleRuntimeKey(label: String): Char =
    singleOrNull()?.uppercaseChar()
        ?: error("$label must be exactly one character")

internal fun inventorySlotKey(slot: Int): Char = (INVENTORY_FIRST_KEY.code + slot - 1).toChar()

internal fun Char.inventorySlotFromKey(): Int? {
    val slot = uppercaseChar().code - INVENTORY_FIRST_KEY.code + 1
    return slot.takeIf { it in 1..INVENTORY_SAVE_SLOT_MAX }
}

internal fun List<CompiledInventorySlot>.normalizedInventorySlots(): List<CompiledInventorySlot> {
    val bySlot = filter { it.slot in 1..INVENTORY_SAVE_SLOT_MAX }.associateBy(CompiledInventorySlot::slot)
    return (1..INVENTORY_SAVE_SLOT_MAX).map { slot -> bySlot[slot] ?: CompiledInventorySlot(slot, null) }
}

internal fun chooseDropSlotForInventoryOverflow(
    systems: ExternalRuntimeSystems,
    input: RuntimeInputPort,
    screen: RuntimeScreenPort,
    party: PartyState,
    leadLines: List<String> = emptyList(),
    itemName: (CompiledHandleRef) -> String,
): Int? {
    val confirmPrompt = systems.prompts.party.dropOverflowConfirm.render({ null }, screen.textColumns)
    val confirmLines = if (leadLines.isEmpty()) confirmPrompt else leadLines + "" + confirmPrompt
    screen.writeText(confirmLines)
    val confirm =
        input
            .readValidChoice(screen, confirmLines) { choice -> choice.uppercaseChar() == 'Y' || choice == '\n' }
            ?.uppercaseChar()
            ?: return null
    if (confirm != "Y".single()) return null
    val slotLines =
        screen.inventoryPickerLines(
            systems.prompts.party.dropOverflowPrompt
                .render({ null }, screen.textColumns),
            party.inventory.inventoryChoiceLines(itemName),
        )
    screen.writeText(slotLines)
    return input.readValidInventorySlotChoice(
        screen = screen,
        lines = slotLines,
        validSlots = party.inventory.filter { slot -> slot.item != null }.mapTo(mutableSetOf(), CompiledInventorySlot::slot),
        cancelKeys = inventoryCancelKeys,
    )
}
