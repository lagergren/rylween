package rylween.runtime

import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledInventorySlot
import rylween.compiled.ExternalSecretAction
import rylween.compiled.RuntimeCommandGroupId
import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeSystems
import rylween.dsl.impl.SecretRole
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RuntimeSecretsTest {
    private val bundleFile = File(requiredRuntimeTestProperty("rylween.runtime.bundle"))
    private val testTmp = File(requiredRuntimeTestProperty("rylween.runtime.testTmp")).also(File::mkdirs)
    private val bundle = bundleFile.readExternalCompiledGameBundle()
    private val systems = bundleFile.readExternalRuntimeSystems()
    private val content = bundle.content

    @Test
    fun accountsComeFromTheEnvironmentWithAdminAsTheDefaultSysop() {
        assertEquals(RuntimeSecretAccounts("admin", null), RuntimeSecretAccounts.fromEnvironment { null })
        assertEquals(
            RuntimeSecretAccounts("sysop", "pest"),
            RuntimeSecretAccounts.fromEnvironment(
                mapOf(
                    RuntimeSecretAccounts.ADMIN_ACCOUNT_ENV to " sysop ",
                    RuntimeSecretAccounts.VICTIM_ACCOUNT_ENV to "pest",
                )::get,
            ),
        )
        assertEquals(
            RuntimeSecretAccounts("admin", null),
            RuntimeSecretAccounts.fromEnvironment(
                mapOf(RuntimeSecretAccounts.ADMIN_ACCOUNT_ENV to " ", RuntimeSecretAccounts.VICTIM_ACCOUNT_ENV to "")::get,
            ),
        )
    }

    @Test
    fun onlyTheConfiguredAccountsGetTheirRoles() {
        val accounts = RuntimeSecretAccounts(admin = "Head Sysop", victim = "Beta Tester")

        assertEquals(listOf(SecretRole.Admin), roles("head_sysop", accounts))
        assertEquals(listOf(SecretRole.Admin), roles("head-sysop", accounts))
        assertEquals(listOf(SecretRole.Victim), roles("beta_tester", accounts))
        assertEquals(emptyList(), roles("admin", accounts), "admin is only the default")
        assertEquals(emptyList(), roles("someone", accounts))
        assertEquals(listOf(SecretRole.Admin), roles("admin", RuntimeSecretAccounts()))
        assertEquals(emptyList(), roles("beta_tester", RuntimeSecretAccounts()), "there is no victim unless one is named")

        val admin = runtime("admin").withSecretRules(systems, RuntimeSecretAccounts())
        assertTrue(admin.alwaysRunFromNonMandatoryEncounters)
        assertFalse(runtime("someone").withSecretRules(systems, RuntimeSecretAccounts()).alwaysRunFromNonMandatoryEncounters)
    }

    @Test
    fun theSysopKeysAddTimeAndGold() {
        val admin = runtime("admin").withSecretRules(systems, RuntimeSecretAccounts())
        val someone = runtime("someone").withSecretRules(systems, RuntimeSecretAccounts())

        secrets("secret-keys.log") { secrets ->
            assertEquals(null, secrets.keyAction(someone, '+'))
            val time = secrets.apply(admin, checkNotNull(secrets.keyAction(admin, '/'))).runtime
            assertEquals(admin.party.resources.timeLeft + 50, time.party.resources.timeLeft)
            val gold = secrets.apply(admin, checkNotNull(secrets.keyAction(admin, '+'))).runtime
            assertEquals(admin.party.resources.gold + 1_000, gold.party.resources.gold)
        }
    }

    @Test
    fun grantingTheMagicItemsFillsFreeSlotsThenAsksWhatToDrop() {
        val sword = item("sword")
        val packed = (1..14).map { sword } + listOf(null, null)
        val admin = runtime("admin", packed).withSecretRules(systems, RuntimeSecretAccounts())
        val magicItems = magicItemsGrant(admin).items

        // The first two items take the free slots. The third replaces the sword in slot A, and the
        // other nine are passed up at the drop prompt.
        val granted =
            secrets("secret-grant.log", ScriptedInput("YA" + "\n".repeat(magicItems.size - 3))) { secrets ->
                secrets.apply(admin, magicItemsGrant(admin)).runtime
            }

        assertEquals(item(magicItems[0]), granted.party.inventoryItem(15))
        assertEquals(item(magicItems[1]), granted.party.inventoryItem(16))
        assertEquals(item(magicItems[2]), granted.party.inventoryItem(1))
        assertEquals((2..14).map { sword }, (2..14).map(granted.party::inventoryItem))
    }

    @Test
    fun theVictimMeetsTheSmiterWheneverTheMovesLeftAreAMultipleOf101() {
        val accounts = RuntimeSecretAccounts(victim = "pest")
        val victim = runtime("pest").withSecretRules(systems, accounts)

        secrets("secret-step.log") { secrets ->
            val action = secrets.stepAction(victim.withTimeLeft(202))
            assertEquals(ExternalSecretAction.SpawnEncounter("sysop_smiter"), action)
            assertEquals(null, secrets.stepAction(victim.withTimeLeft(201)))
            assertEquals(null, secrets.stepAction(runtime("admin").withSecretRules(systems, accounts).withTimeLeft(202)))
        }
    }

    @Test
    fun aVictimsMoveStartsTheSmiterBattleBeforeRandomEvents() {
        val victim = runtime("pest").withSecretRules(systems, RuntimeSecretAccounts(victim = "pest")).withTimeLeft(101)
        val logFile = testTmp.resolve("secret-step-move.log")
        RuntimeLog.open(logFile).use { log ->
            WildernessActions(
                content = content,
                systems = systems,
                realm = systems.realm,
                combat = systems.combat,
                wilderness = systems.wilderness,
                commands = systems.commands,
                input = ScriptedInput(""),
                screen = NoOpRuntimeScreen(),
                log = log,
            ).applyMoveEvents(victim, MoveEvent(victim.party.position, victim.party.position, null, null))
        }

        val lines = logFile.readLines()
        assertTrue(lines.any { line -> "secret.spawn_encounter monster=sysop_smiter" in line }, lines.joinToString("\n"))
        assertFalse(lines.any { line -> "wilderness.event_gate" in line }, "the smiter replaces the random event")
    }

    @Test
    fun theSysopKeysOnlyWorkForTheAdminInAWildernessSession() {
        assertFalse("Unknown command '+'" in wildernessSession("admin-keys", userId = "test-sysop", admin = "Test Sysop"))
        assertContains(wildernessSession("other-keys", userId = "someone", admin = "Test Sysop"), "Unknown command '+'")
    }

    @Test
    fun secretKeysDoNotShadowWildernessCommands() {
        val commands =
            systems.commands.getValue(RuntimeCommandGroupId.Wilderness.id) +
                systems.commands[RuntimeCommandGroupId.WildernessDebug.id].orEmpty()
        val keys = systems.secrets.players.flatMap { player -> player.keyActions }.map { keyAction -> keyAction.binding.primary.single() }

        assertEquals(listOf('/', '*', '+'), keys)
        keys.forEach { key ->
            assertEquals(emptyList(), commands.filter { command -> command.binding.matches(key) }.map { it.action }, "secret key $key")
        }
    }

    private fun roles(
        userId: String,
        accounts: RuntimeSecretAccounts,
    ): List<SecretRole> = runtime(userId).withSecretRules(systems, accounts).secretPlayers.map { player -> player.role }

    private fun magicItemsGrant(runtime: RuntimeState): ExternalSecretAction.GrantItems =
        assertIs<ExternalSecretAction.GrantItems>(
            runtime.secretPlayers
                .flatMap { player -> player.keyActions }
                .single { keyAction -> keyAction.binding.primary == "*" }
                .action,
        )

    private fun wildernessSession(
        name: String,
        userId: String,
        admin: String,
    ): String {
        val screen = TextRecordingScreen()
        RuntimeLog.open(testTmp.resolve("$name.log")).use { log ->
            RuntimeSessionFactory
                .load(bundleFile, RuntimeSecretAccounts(admin = admin))
                .start(
                    ScriptedInput("+"),
                    screen,
                    RuntimeSessionConfig(
                        userId = userId,
                        stateSlug = "fresh-replay",
                        saveFile = testTmp.resolve("$name.rsave").also(File::delete),
                    ),
                    log,
                )
        }
        return screen.lines.joinToString("\n")
    }

    private fun runtime(
        userId: String,
        inventory: List<CompiledHandleRef?>? = null,
    ): RuntimeState {
        val state = bundle.states.single { state -> content.slug(state.state) == "fresh-replay" }
        val runtime = RuntimeState.from(state, content).withActiveUser(userId)
        if (inventory == null) return runtime
        return runtime.withParty(runtime.party.copy(inventory = inventory.mapIndexed { index, item -> CompiledInventorySlot(index + 1, item) }))
    }

    private fun RuntimeState.withTimeLeft(timeLeft: Int): RuntimeState =
        withParty(party.copy(resources = party.resources.copy(timeLeft = timeLeft)))

    private fun item(slug: String): CompiledHandleRef = content.ref(CompiledHandleKind.Item, slug)

    private fun <T> secrets(
        logName: String,
        input: ScriptedInput = ScriptedInput(""),
        body: (SecretActions) -> T,
    ): T =
        RuntimeLog.open(testTmp.resolve(logName)).use { log ->
            body(SecretActions(content, systems, input, NoOpRuntimeScreen(), log))
        }
}

/** Records every line the runtime writes or shows. */
private class TextRecordingScreen : NoOpRuntimeScreen() {
    val lines = mutableListOf<String>()

    override fun writeText(lines: List<String>) {
        this.lines += lines
    }

    override fun showMessage(lines: List<String>) {
        this.lines += lines
    }
}
