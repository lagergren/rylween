/**
 * Shared session input types and small text/key helpers.
 *
 * Runtime sessions use these backend-neutral inputs so terminal, ANSI, server,
 * test, and future VM hosts can drive the same state machines without frontend
 * dependencies.
 */

package rylween.runtime

import rylween.dsl.Gender
import rylween.dsl.ItemId
import rylween.dsl.NpcId
import rylween.dsl.PcClass
import rylween.dsl.QuestionTopic
import rylween.dsl.Race

/** Equipment category selected by inventory/equipment sessions. */
enum class EquipmentKind { Weapon, Armor }

/** Main menu choice for a shop service. */
enum class ShopMainChoice { Buy, Sell, Leave }

/** Main menu choice for an NPC conversation. */
enum class SpeakMainChoice { Chitchat, Question, Rumour, Bye }

/** Typed inputs accepted by random-item-offer sessions. */
sealed interface RandomItemOfferInput {
    data class Buy(
        val accept: Boolean,
    ) : RandomItemOfferInput

    data class DropSlot(
        val slot: Int,
    ) : RandomItemOfferInput

    object Continue : RandomItemOfferInput
}

/** Typed inputs accepted by reward-grant sessions. */
sealed interface RewardGrantInput {
    data class DropSlot(
        val slot: Int,
    ) : RewardGrantInput

    object Continue : RewardGrantInput
}

/** Typed inputs accepted by inventory item-use sessions. */
sealed interface InventoryItemUseInput {
    data class InventorySlot(
        val slot: Int,
    ) : InventoryItemUseInput

    data class Equipment(
        val input: EquipmentPromptInput,
    ) : InventoryItemUseInput

    object Continue : InventoryItemUseInput
}

/** Typed inputs accepted by priest-spell sessions. */
sealed interface PriestSpellInput {
    data class Caster(
        val memberNumber: Int,
    ) : PriestSpellInput

    data class SpellSlot(
        val slot: Int,
    ) : PriestSpellInput

    object Continue : PriestSpellInput
}

/** Typed inputs accepted by reusable equipment prompts. */
sealed interface EquipmentPromptInput {
    data class InventorySlot(
        val slot: Int,
    ) : EquipmentPromptInput

    data class Member(
        val memberNumber: Int,
    ) : EquipmentPromptInput
}

/** Typed inputs accepted by conversation sessions. */
sealed interface SpeakInput {
    data class Npc(
        val npc: NpcId,
    ) : SpeakInput

    data class Main(
        val choice: SpeakMainChoice,
    ) : SpeakInput

    data class Question(
        val topic: QuestionTopic,
    ) : SpeakInput

    object Continue : SpeakInput
}

/** Typed inputs accepted by the town-leave equipment flow. */
sealed interface TownLeaveEquipmentInput {
    data class ReadyAnswer(
        val yes: Boolean,
    ) : TownLeaveEquipmentInput

    data class Equipment(
        val input: EquipmentPromptInput,
    ) : TownLeaveEquipmentInput
}

/** Typed inputs accepted by shop-service sessions. */
sealed interface ShopServiceInput {
    object ContinueIntro : ShopServiceInput

    data class Main(
        val choice: ShopMainChoice,
    ) : ShopServiceInput

    data class SelectCatalog(
        val catalogKey: Char,
    ) : ShopServiceInput

    data class BuyItem(
        val item: ItemId,
    ) : ShopServiceInput

    data class Quantity(
        val quantity: Int,
    ) : ShopServiceInput

    data class ConfirmPurchase(
        val accept: Boolean,
    ) : ShopServiceInput

    data class DropSlot(
        val slot: Int,
    ) : ShopServiceInput

    data class SellSlot(
        val slot: Int,
        val bargainRolls: List<Int> = emptyList(),
    ) : ShopServiceInput

    data class ConfirmSale(
        val accept: Boolean,
    ) : ShopServiceInput
}

/** Typed inputs accepted by top-level town sessions. */
sealed interface TownSessionInput {
    data class EntryTax(
        val pay: Boolean,
    ) : TownSessionInput

    data class MenuKey(
        val key: Char,
    ) : TownSessionInput

    data class Shop(
        val input: ShopServiceInput,
    ) : TownSessionInput

    data class Equipment(
        val input: TownLeaveEquipmentInput,
    ) : TownSessionInput

    data class FinishNativeService(
        val runtime: RuntimeSessionState,
    ) : TownSessionInput
}

/** Typed inputs accepted by character-creation sessions. */
sealed interface CharacterCreationInput {
    data class PartyName(
        val name: String,
    ) : CharacterCreationInput

    data class RerollStats(
        val reroll: Boolean,
    ) : CharacterCreationInput

    data class MemberName(
        val name: String,
    ) : CharacterCreationInput

    data class GenderChoice(
        val gender: Gender,
    ) : CharacterCreationInput

    data class RaceChoice(
        val race: Race,
    ) : CharacterCreationInput

    data class ClassChoice(
        val pcClass: PcClass,
    ) : CharacterCreationInput

    object Continue : CharacterCreationInput
}

internal fun Int.digitKey(): Char {
    require(this in 0..9) { "single-key numeric choice must be 0..9: $this" }
    return ('0'.code + this).toChar()
}

internal fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor
