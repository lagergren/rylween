/**
 * The sysop's hidden powers and the punishment the sysop gods send after a
 * victim.
 *
 * The game authors what the admin and victim roles can do; each server picks
 * the accounts that play them, so nobody gets the powers by choosing a name.
 */

package rylween.runtime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledHandleRef
import rylween.compiled.ExternalRuntimeSystems
import rylween.compiled.ExternalSecretAction
import rylween.dsl.AudioCue
import rylween.dsl.impl.SecretRole

/**
 * The accounts that play the game's secret roles on this server.
 *
 * The sysop is [admin], `admin` unless `RYLWEEN_ADMIN_ACCOUNT` names another
 * account. The victim is the account `RYLWEEN_VICTIM_ACCOUNT` names, or nobody.
 */
data class RuntimeSecretAccounts(
    val admin: String = DEFAULT_ADMIN_ACCOUNT,
    val victim: String? = null,
) {
    /** The roles [userId] plays. Names compare loosely, so `Admin` and `admin` are the same account. */
    internal fun rolesOf(userId: String): Set<SecretRole> =
        buildSet {
            if (userId in admin.accountAliases()) add(SecretRole.Admin)
            if (victim != null && userId in victim.accountAliases()) add(SecretRole.Victim)
        }

    companion object {
        const val ADMIN_ACCOUNT_ENV = "RYLWEEN_ADMIN_ACCOUNT"
        const val VICTIM_ACCOUNT_ENV = "RYLWEEN_VICTIM_ACCOUNT"
        const val DEFAULT_ADMIN_ACCOUNT = "admin"

        /** Reads the accounts from the environment; a blank value counts as unset. */
        fun fromEnvironment(env: (String) -> String? = System::getenv): RuntimeSecretAccounts =
            RuntimeSecretAccounts(
                admin = env(ADMIN_ACCOUNT_ENV)?.trim()?.ifBlank { null } ?: DEFAULT_ADMIN_ACCOUNT,
                victim = env(VICTIM_ACCOUNT_ENV)?.trim()?.ifBlank { null },
            )
    }
}

/** The save-user spellings of an account name, with either `_` or `-` for spaces and punctuation. */
private fun String.accountAliases(): Set<String> {
    val lowered = trim().lowercase()
    return listOf('_', '-').mapTo(mutableSetOf()) { separator ->
        lowered
            .map { char -> char.takeIf { it.isLetterOrDigit() || it == '-' || it == '_' } ?: separator }
            .joinToString("")
            .trim('-', '_')
    }
}

/** Runs the secret actions of the roles the active account plays. */
internal class SecretActions(
    private val content: CompiledContentBundle,
    private val systems: ExternalRuntimeSystems,
    private val input: RuntimeInputPort,
    private val screen: RuntimeScreenPort,
    private val log: RuntimeLog,
) {
    private val audio = RuntimeAudio(systems.audio, screen)

    /** The action bound to the wilderness key [command] for this account, if any. */
    fun keyAction(
        runtime: RuntimeState,
        command: Char,
    ): ExternalSecretAction? =
        runtime.secretPlayers
            .flatMap { player -> player.keyActions }
            .firstOrNull { keyAction -> keyAction.binding.matches(command) }
            ?.action

    /** The step trigger that fires after this move: the day's moves left are a positive multiple of its step count. */
    fun stepAction(runtime: RuntimeState): ExternalSecretAction? {
        val timeLeft = runtime.party.resources.timeLeft
        return runtime.secretPlayers
            .mapNotNull { player -> player.stepTrigger }
            .firstOrNull { trigger -> timeLeft > 0 && timeLeft % trigger.everySteps == 0 }
            ?.action
    }

    fun apply(
        runtime: RuntimeState,
        action: ExternalSecretAction,
    ): WildernessActionResult =
        when (action) {
            is ExternalSecretAction.AddTimeLeft -> {
                log.event("secret.add_time_left amount=${action.amount}")
                val party = runtime.party
                runtime
                    .withParty(party.copy(resources = party.resources.copy(timeLeft = party.resources.timeLeft + action.amount)))
                    .refreshed(action.refreshStatus)
            }

            is ExternalSecretAction.AddGold -> {
                log.event("secret.add_gold amount=${action.amount}")
                runtime.withParty(runtime.party.withGold(runtime.party.resources.gold + action.amount)).refreshed(action.refreshStatus)
            }

            is ExternalSecretAction.GrantItems ->
                grantItems(
                    runtime,
                    action.items.map { slug -> content.ref(CompiledHandleKind.Item, slug) },
                )

            is ExternalSecretAction.SpawnEncounter -> {
                log.event("secret.spawn_encounter monster=${action.monster} time_left=${runtime.party.resources.timeLeft}")
                val battle = battleRunner().runMonster(runtime, content.ref(CompiledHandleKind.Monster, action.monster), mandatory = true)
                WildernessActionResult(battle.runtime, battle.messages)
            }
        }

    private fun RuntimeState.refreshed(refreshStatus: Boolean): WildernessActionResult {
        if (refreshStatus) screen.updateStatus(this)
        return WildernessActionResult(this)
    }

    /**
     * Puts each of [items] in a free slot. When the pack is full, the player
     * drops something for the item through the usual full-inventory prompts,
     * or passes it up.
     */
    private fun grantItems(
        runtime: RuntimeState,
        items: List<CompiledHandleRef>,
    ): WildernessActionResult {
        var party = runtime.party
        val notices = mutableListOf<String>()
        items.forEach { item ->
            val freeSlot = party.inventory.firstOrNull { slot -> slot.item == null }?.slot
            val slot =
                freeSlot ?: chooseDropSlotForInventoryOverflow(
                    systems = systems,
                    input = input,
                    screen = screen,
                    party = party,
                    leadLines = runtimeChromeText("${itemName(item)} does not fit."),
                    itemName = ::itemName,
                )
            log.event("secret.grant_item item=${content.slug(item)} slot=${slot ?: "passed"}")
            if (slot != null) {
                val carried = party.withInventoryItem(slot, item).leaveExcessFood(content, systems, screen.textColumns)
                party = carried.party
                notices += carried.notice
            }
        }
        audio.cue(AudioCue.ItemTaken)
        val next = runtime.withParty(party)
        screen.updateStatus(next)
        return WildernessActionResult(next, notices.distinct())
    }

    private fun itemName(item: CompiledHandleRef): String =
        content.items
            .firstOrNull { record -> record.id == item }
            ?.let { record -> content.strings.value(record.displayNameString) }
            ?: content.slug(item)

    private fun battleRunner(): BattleRunner =
        BattleRunner(content, systems.combat, systems.pacing.battle, input, screen, log, PausePrompts(systems.prompts), systems.audio)
}
