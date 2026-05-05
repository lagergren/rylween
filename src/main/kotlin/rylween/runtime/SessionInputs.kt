// Shared session input types and small text/key helpers used by every reusable
// runtime session. Kept backend-neutral: no terminal, ANSI, server, or C glue.

package rylween.runtime

import rylween.dsl.Gender
import rylween.dsl.ItemId
import rylween.dsl.NpcId
import rylween.dsl.PcClass
import rylween.dsl.QuestionTopic
import rylween.dsl.Race

enum class EquipmentKind { Weapon, Armor }

enum class ShopMainChoice { Buy, Sell, Leave }

enum class SpeakMainChoice { Chitchat, Question, Rumour, Bye }

sealed interface RandomItemOfferInput {
    data class Buy(
        val accept: Boolean,
    ) : RandomItemOfferInput

    data class DropSlot(
        val slot: Int,
    ) : RandomItemOfferInput

    object Continue : RandomItemOfferInput
}

sealed interface RewardGrantInput {
    data class DropSlot(
        val slot: Int,
    ) : RewardGrantInput

    object Continue : RewardGrantInput
}

sealed interface InventoryItemUseInput {
    data class InventorySlot(
        val slot: Int,
    ) : InventoryItemUseInput

    data class Equipment(
        val input: EquipmentPromptInput,
    ) : InventoryItemUseInput

    object Continue : InventoryItemUseInput
}

sealed interface PriestSpellInput {
    data class Caster(
        val memberNumber: Int,
    ) : PriestSpellInput

    data class SpellSlot(
        val slot: Int,
    ) : PriestSpellInput

    object Continue : PriestSpellInput
}

sealed interface EquipmentPromptInput {
    data class InventorySlot(
        val slot: Int,
    ) : EquipmentPromptInput

    data class Member(
        val memberNumber: Int,
    ) : EquipmentPromptInput
}

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

sealed interface TownLeaveEquipmentInput {
    data class ReadyAnswer(
        val yes: Boolean,
    ) : TownLeaveEquipmentInput

    data class Equipment(
        val input: EquipmentPromptInput,
    ) : TownLeaveEquipmentInput
}

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
