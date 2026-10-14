package rylween.webcommon.auth

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class WebAuthTest {
    @Test
    fun registeredAccountCanAuthenticateByEmailAndSlug() {
        val store = WebUserStore(createTempDirectory("rylween-auth").toFile())

        val registered = assertIs<WebAuthResult.Success>(store.register("Strong Party", "strong@example.test", "password123"))
        val byEmail = assertIs<WebAuthResult.Success>(store.authenticate("strong@example.test", "password123"))
        val bySlug = assertIs<WebAuthResult.Success>(store.authenticate("strong-party", "password123"))

        assertEquals("strong-party", registered.account.slug)
        assertEquals(registered.account, byEmail.account)
        assertEquals(registered.account, bySlug.account)
    }

    @Test
    fun availabilityReportsDuplicateNameAndEmail() {
        val store = WebUserStore(createTempDirectory("rylween-auth").toFile())

        store.register("Strong Party", "strong@example.test", "password123")

        val duplicate = store.availability("Strong Party", "strong@example.test")
        val fresh = store.availability("New Party", "new@example.test")

        assertEquals(false, duplicate.name.available)
        assertEquals(false, duplicate.email.available)
        assertEquals(true, fresh.name.available)
        assertEquals(true, fresh.email.available)
    }

    @Test
    fun reservedNamesCannotBeRegisteredButCanBeBootstrapped() {
        val store = WebUserStore(createTempDirectory("rylween-auth").toFile(), reservedNames = setOf("Admin"))

        val refused = assertIs<WebAuthResult.Failure>(store.register("admin", "someone@example.test", "password123"))
        assertEquals("That adventurer name is reserved.", refused.message)
        assertEquals(false, store.availability("ADMIN", "someone@example.test").name.available)
        assertIs<WebAuthBootstrapResult.Created>(store.ensureAccount(WebAuthBootstrapAccount("admin", null, "password123")))
        assertIs<WebAuthResult.Success>(store.authenticate("admin", "password123"))
    }

    @Test
    fun bootstrapAccountIsCreatedOrVerified() {
        val store = WebUserStore(createTempDirectory("rylween-auth").toFile())
        val bootstrap = WebAuthBootstrapAccount("Strong Party", "strong@example.test", "password123")

        val created = assertIs<WebAuthBootstrapResult.Created>(store.ensureAccount(bootstrap))
        val existing = assertIs<WebAuthBootstrapResult.Existing>(store.ensureAccount(bootstrap))
        val byEmail = assertIs<WebAuthResult.Success>(store.authenticate("strong@example.test", "password123"))

        assertEquals("strong-party", created.account.slug)
        assertEquals(created.account, existing.account)
        assertEquals(created.account, byEmail.account)
    }

    @Test
    fun bootstrapAccountRejectsMismatchedExistingPassword() {
        val store = WebUserStore(createTempDirectory("rylween-auth").toFile())

        store.ensureAccount(WebAuthBootstrapAccount("Strong", "strong@example.test", "password123"))

        assertIs<WebAuthBootstrapResult.Failure>(
            store.ensureAccount(WebAuthBootstrapAccount("Strong", "strong@example.test", "different123")),
        )
    }

    @Test
    fun wrongPasswordIsRejected() {
        val store = WebUserStore(createTempDirectory("rylween-auth").toFile())

        store.register("Strong", "strong@example.test", "password123")

        assertIs<WebAuthResult.Failure>(store.authenticate("strong@example.test", "nope"))
    }

    @Test
    fun rememberedLoginCookieRoundTripsWithoutPassword() {
        val header = WebAuthCookies.rememberLogin("strong+tester@example.test")
        val value = WebAuthCookies.rememberedLogin(mapOf("cookie" to header.value.substringAfter("Set-Cookie: ")))

        assertEquals("strong+tester@example.test", value)
    }

    @Test
    fun sessionsResolveAndCanBeRemoved() {
        val sessions = WebSessionStore()
        val account = WebAccount("strong", "Strong", "strong@example.test", emailConfirmed = true)
        val token = sessions.create(account)

        assertEquals(account, sessions.account(token))
        sessions.remove(token)
        assertNull(sessions.account(token))
    }
}
