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
import rylween.compiled.CompiledScriptProgram
import rylween.compiled.CompiledSpell
import rylween.compiled.ExternalRuntimeSystems
import rylween.compiled.ExternalRuntimeTextBlock
import rylween.dsl.impl.InventorySaveSlotMax
import rylween.dsl.impl.ItemConsumePolicy
import rylween.dsl.impl.ItemKind
import rylween.dsl.impl.ItemStorageKind
import rylween.dsl.impl.ItemUseContext
import rylween.dsl.impl.PcClass

private const val INVENTORY_FIRST_KEY = 'A'
private const val INVENTORY_ENTRY_WIDTH = 12
private const val INVENTORY_ITEM_WIDTH = 9

/** Direct compiled implementations for reusable party utility commands. */
internal class UtilityActions(
    private val content: CompiledContentBundle,
    private val systems: ExternalRuntimeSystems,
    private val input: RuntimeInputPort,
    private val screen: RuntimeScreenPort,
    private val log: RuntimeLog,
) {
    fun drop(runtime: RuntimeState): RuntimeState {
        val choices = runtime.party.inventory.filter { slot -> slot.item != null }
        if (choices.isEmpty()) return runtime
        val lines =
            systems.prompts.party.dropPrompt
                .renderUtility(runtime) + inventoryLines(choices).withReturnCancelLine()
        screen.writeText(lines)
        val slot = chooseInventorySlot(lines, choices) ?: return runtime
        return runtime.withParty(runtime.party.withInventoryItem(slot, null))
    }

    fun readyWeapon(runtime: RuntimeState): RuntimeState =
        readyEquipment(runtime, acceptedKind = ItemKind.Weapon) { member, item ->
            member.copy(equipment = member.equipment.copy(weapon = item))
        }

    fun readyArmor(runtime: RuntimeState): RuntimeState =
        readyEquipment(runtime, acceptedKind = ItemKind.Armor) { member, item ->
            member.copy(equipment = member.equipment.copy(armor = item))
        }

    fun removeWeapon(runtime: RuntimeState): RuntimeState =
        removeEquipment(runtime) { member -> member.copy(equipment = member.equipment.copy(weapon = content.emptyItem())) }

    fun removeArmor(runtime: RuntimeState): RuntimeState =
        removeEquipment(runtime) { member -> member.copy(equipment = member.equipment.copy(armor = content.emptyItem())) }

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
            systems.prompts.inventoryUse.quaff
                .renderUtility(runtime) + inventoryLines(choices).withReturnCancelLine()
        screen.writeText(lines)
        val slot = chooseInventorySlot(lines, choices) ?: return runtime
        val item = runtime.party.inventoryItem(slot) ?: return runtime
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
                .readValidChoice(screen, lines) { candidate ->
                    val key = candidate.uppercaseChar()
                    key == cancelKey || choices.any { choice -> choice.key == key }
                }?.uppercaseChar() ?: return runtime
        if (command == cancelKey) return runtime
        val spell = choices.firstOrNull { choice -> choice.key == command }?.value ?: return runtime
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
        acceptedKind: ItemKind,
        equip: (rylween.compiled.CompiledPartyMember, CompiledHandleRef) -> rylween.compiled.CompiledPartyMember,
    ): RuntimeState {
        val memberIndex = chooseMember(runtime) ?: return runtime
        val member = runtime.party.members.getOrNull(memberIndex) ?: return runtime
        val equipmentName = acceptedKind.equipmentName()
        val choices =
            runtime.party.inventory.mapNotNull { slot ->
                val item = slot.item ?: return@mapNotNull null
                val record = itemRecord(item)
                slot.takeIf {
                    record.kind == acceptedKind &&
                        (record.equippableBy.isEmpty() || member.pcClass in record.equippableBy)
                }
            }
        if (choices.isEmpty()) {
            screen.writeText(
                systems.prompts.equipment.noAvailable
                    .renderUtility(runtime, equipmentKindName = equipmentName),
            )
            return runtime
        }
        val lines =
            systems.prompts.equipment.inventory
                .renderUtility(runtime) + inventoryLines(choices).withReturnCancelLine()
        screen.writeText(lines)
        val slot = chooseInventorySlot(lines, choices) ?: return runtime
        val item = runtime.party.inventoryItem(slot) ?: return runtime
        screen.writeText(
            systems.prompts.equipment.equipped
                .renderUtility(runtime, selectedItem = item),
        )
        return runtime.updateMember(memberIndex) { current -> equip(current, item) }
    }

    private fun ItemKind.equipmentName(): String =
        when (this) {
            ItemKind.Weapon -> "weapon"
            ItemKind.Armor -> "armor"
            else -> "item"
        }

    private fun removeEquipment(
        runtime: RuntimeState,
        update: (rylween.compiled.CompiledPartyMember) -> rylween.compiled.CompiledPartyMember,
    ): RuntimeState = chooseMember(runtime)?.let { memberIndex -> runtime.updateMember(memberIndex, update) } ?: runtime

    private fun chooseInventorySlot(
        lines: List<String>,
        choices: List<CompiledInventorySlot>,
    ): Int? =
        input.readValidInventorySlotChoice(
            screen = screen,
            lines = lines,
            validSlots = choices.mapTo(mutableSetOf(), CompiledInventorySlot::slot),
            cancelKeys = INVENTORY_CANCEL_KEYS,
        )

    private fun inventoryLines(choices: List<CompiledInventorySlot>): List<String> =
        choices.map { slot ->
            "${inventorySlotKey(slot.slot)}. ${slot.item?.let(::itemName).orEmpty()}"
        }

    private fun List<String>.withReturnCancelLine(): List<String> = this + systems.prompts.returnLeavesLine()

    private fun chooseCaster(runtime: RuntimeState): Int? {
        if (runtime.party.members.none { member -> !member.dead && member.pcClass == PcClass.Priest }) {
            screen.showMessage(
                systems.prompts.priest.wrongClass
                    .renderUtility(runtime),
            )
            return null
        }
        val baseLines =
            systems.prompts.priest.who
                .renderUtility(runtime) +
                systems.prompts.priest.none
                    .renderUtility() +
                "" +
                runtime.party.members.mapIndexed { index, _ -> "${index + 1}. ${runtime.party.memberName(content, index)}" }
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
        spells.map { choice ->
            "(${choice.key})${content.strings.value(choice.value.nameString)}"
        } +
            systems.prompts.priest.none
                .renderUtility()

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
                cancelKeys = INVENTORY_CANCEL_KEYS,
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

private const val PRIEST_SPELL_PREFIX = "priest."
private val INVENTORY_CANCEL_KEYS = setOf('\n')

private fun String.singleRuntimeKey(label: String): Char =
    singleOrNull()?.uppercaseChar()
        ?: error("$label must be exactly one character")

internal fun inventorySlotKey(slot: Int): Char = (INVENTORY_FIRST_KEY.code + slot - 1).toChar()

internal fun Char.inventorySlotFromKey(): Int? {
    val slot = uppercaseChar().code - INVENTORY_FIRST_KEY.code + 1
    return slot.takeIf { it in 1..InventorySaveSlotMax }
}

internal fun List<CompiledInventorySlot>.normalizedInventorySlots(): List<CompiledInventorySlot> {
    val bySlot = filter { it.slot in 1..InventorySaveSlotMax }.associateBy(CompiledInventorySlot::slot)
    return (1..InventorySaveSlotMax).map { slot -> bySlot[slot] ?: CompiledInventorySlot(slot, null) }
}

internal fun inventoryMenuLines(entries: List<Pair<Char, String>>): List<String> =
    entries
        .chunked(2)
        .map { row ->
            row
                .joinToString(" ") { (key, itemName) ->
                    "$key. ${itemName.take(INVENTORY_ITEM_WIDTH)}".padEnd(INVENTORY_ENTRY_WIDTH)
                }.trimEnd()
        }
