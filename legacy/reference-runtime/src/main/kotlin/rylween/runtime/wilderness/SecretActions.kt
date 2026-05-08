/**
 * Runtime application of authored secret/debug actions.
 *
 * [WildernessSession] uses these helpers to expose per-player secret key
 * commands and step triggers from [rylween.dsl.SecretSystemModel] without
 * mixing secret-action mutation logic into the main movement state machine.
 */
package rylween.runtime.wilderness

import rylween.dsl.PartyState
import rylween.dsl.SecretActionModel
import rylween.dsl.SecretKeyActionModel
import rylween.dsl.SecretPlayerModel
import rylween.dsl.SecretStepTriggerModel
import rylween.dsl.SecretSystemModel
import rylween.dsl.item
import rylween.runtime.InputCommand
import rylween.runtime.RuntimeSessionState
import rylween.runtime.party
import rylween.runtime.withInventoryItem

/** Result of applying one secret action to live runtime state. */
data class SecretRuntimeActionResult(
    val runtime: RuntimeSessionState,
    val refreshStatus: Boolean,
)

/** Returns the secret-action block authored for [playerName], if any. */
fun SecretSystemModel.playerSecrets(playerName: String): SecretPlayerModel? =
    players.firstOrNull { player -> player.playerName == playerName }

/** Returns secret key actions available to [playerName]. */
fun SecretSystemModel.keyActionsFor(playerName: String): List<SecretKeyActionModel> = playerSecrets(playerName)?.keyActions.orEmpty()

/** Returns the step trigger that should fire for the current [runtime], if any. */
fun SecretSystemModel.stepTriggerFor(runtime: RuntimeSessionState): SecretStepTriggerModel? {
    val playerSecrets = playerSecrets(runtime.activeParty.player) ?: return null
    val trigger = playerSecrets.stepTrigger ?: return null
    val timeLeft = runtime.activeParty.resources.timeLeft
    return trigger.takeIf { timeLeft > 0 && timeLeft % it.everySteps == 0 }
}

/** Applies an authored [action] to this runtime state. */
fun RuntimeSessionState.applySecretAction(action: SecretActionModel): SecretRuntimeActionResult =
    when (action) {
        is SecretActionModel.AddGold -> {
            val nextParty =
                activeParty.copy(
                    resources = activeParty.resources.copy(gold = activeParty.resources.gold + action.amount),
                )
            SecretRuntimeActionResult(withActiveParty(nextParty), action.refreshStatus)
        }
        is SecretActionModel.AddTimeLeft -> {
            val nextParty =
                activeParty.copy(
                    resources = activeParty.resources.copy(timeLeft = activeParty.resources.timeLeft + action.amount),
                )
            SecretRuntimeActionResult(withActiveParty(nextParty), action.refreshStatus)
        }
        is SecretActionModel.SetInventory ->
            SecretRuntimeActionResult(
                withActiveParty(activeParty.withInventoryItem(action.slot, action.item)),
                refreshStatus = false,
            )
        is SecretActionModel.SpawnEncounter ->
            SecretRuntimeActionResult(this, refreshStatus = false)
    }

/** Converts the active party's secret key actions into wilderness commands. */
fun SecretSystemModel.keyCommandsFor(party: PartyState): List<InputCommand<WildernessSessionInput>> =
    keyActionsFor(party.player).map { secret ->
        InputCommand(
            input = WildernessSessionInput.SecretKey(secret.action),
            binding = secret.binding,
            help = "Secret",
        )
    }
