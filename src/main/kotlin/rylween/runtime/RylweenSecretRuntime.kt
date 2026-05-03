package rylween.runtime

import rylween.dsl.*

data class SecretRuntimeActionResult(
    val runtime: RuntimeSessionState,
    val refreshStatus: Boolean,
)

fun SecretSystemModel.playerSecrets(playerName: String): SecretPlayerModel? =
    players.firstOrNull { player -> player.playerName == playerName }

fun SecretSystemModel.keyActionsFor(playerName: String): List<SecretKeyActionModel> =
    playerSecrets(playerName)?.keyActions.orEmpty()

fun SecretSystemModel.stepTriggerFor(runtime: RuntimeSessionState): SecretStepTriggerModel? {
    val playerSecrets = playerSecrets(runtime.activeParty.player) ?: return null
    val trigger = playerSecrets.stepTrigger ?: return null
    val timeLeft = runtime.activeParty.resources.timeLeft
    return trigger.takeIf { timeLeft > 0 && timeLeft % it.everySteps == 0 }
}

fun RuntimeSessionState.applySecretAction(action: SecretActionModel): SecretRuntimeActionResult =
    when (action) {
        is SecretActionModel.AddGold -> {
            val nextParty = activeParty.copy(
                resources = activeParty.resources.copy(gold = activeParty.resources.gold + action.amount),
            )
            SecretRuntimeActionResult(withActiveParty(nextParty), action.refreshStatus)
        }
        is SecretActionModel.AddTimeLeft -> {
            val nextParty = activeParty.copy(
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

fun SecretSystemModel.keyCommandsFor(party: PartyState): List<InputCommand<WildernessSessionInput>> =
    keyActionsFor(party.player).map { secret ->
        InputCommand(
            input = WildernessSessionInput.SecretKey(secret.action),
            binding = secret.binding,
            help = "Secret",
        )
    }
