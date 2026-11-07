/**
 * File-backed account, password, session, and cookie helpers for the browser
 * runtimes.
 */

package rylween.webcommon.auth

import rylween.webcommon.WebHttpHeader
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Locale
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.concurrent.withLock

private const val SESSION_COOKIE = "rylween_session"
private const val REMEMBERED_LOGIN_COOKIE = "rylween_login"
private const val PASSWORD_ALGORITHM = "PBKDF2WithHmacSHA256"
private const val PASSWORD_ITERATIONS = 120_000
private const val PASSWORD_BITS = 256
private const val SALT_BYTES = 16
private const val TOKEN_BYTES = 32
private const val RESERVED_NAME = "That adventurer name is reserved."

data class WebAccount(
    val slug: String,
    val displayName: String,
    val email: String,
    val emailConfirmed: Boolean,
)

data class WebAuthBootstrapAccount(
    val displayName: String,
    val email: String?,
    val password: String,
)

data class WebAuthFieldAvailability(
    val available: Boolean,
    val message: String,
)

data class WebAuthAvailability(
    val name: WebAuthFieldAvailability,
    val email: WebAuthFieldAvailability,
)

sealed class WebAuthResult {
    data class Success(
        val account: WebAccount,
    ) : WebAuthResult()

    data class Failure(
        val message: String,
    ) : WebAuthResult()
}

sealed class WebAuthBootstrapResult {
    data class Created(
        val account: WebAccount,
    ) : WebAuthBootstrapResult()

    data class Existing(
        val account: WebAccount,
    ) : WebAuthBootstrapResult()

    data class Failure(
        val message: String,
    ) : WebAuthBootstrapResult()
}

sealed class WebSysopAccountResult {
    /** The account was created; [passwordFile] holds its generated password, or is null when the server set one. */
    data class Created(
        val account: WebAccount,
        val passwordFile: File?,
    ) : WebSysopAccountResult()

    data class Existing(
        val account: WebAccount,
    ) : WebSysopAccountResult()

    data class Failure(
        val message: String,
    ) : WebSysopAccountResult()
}

/**
 * Accounts on disk. Players cannot register [reservedNames], such as the sysop
 * account; the server creates those itself as bootstrap accounts.
 */
class WebUserStore(
    private val dataDir: File,
    reservedNames: Set<String> = emptySet(),
) {
    private val reservedSlugs = reservedNames.mapNotNull(::slugFrom).toSet()
    private val lock = ReentrantLock(true)
    private val usersDir = dataDir.resolve("users/by-slug")
    private val emailIndexFile = dataDir.resolve("users/email-index.properties")

    fun availability(
        displayName: String,
        email: String,
    ): WebAuthAvailability =
        lock.withLock {
            val cleanName = displayName.trim().take(40)
            val slug = slugFrom(cleanName)
            val normalizedEmail = normalizeEmail(email)
            val emailIndex = loadProperties(emailIndexFile)
            WebAuthAvailability(
                name =
                    when {
                        cleanName.isBlank() || slug == null -> WebAuthFieldAvailability(false, "Enter an adventurer name.")
                        slug in reservedSlugs -> WebAuthFieldAvailability(false, RESERVED_NAME)
                        userFile(slug).isFile -> WebAuthFieldAvailability(false, "That adventurer name is already registered.")
                        else -> WebAuthFieldAvailability(true, "Adventurer name is available.")
                    },
                email =
                    when {
                        normalizedEmail == null -> WebAuthFieldAvailability(false, "Enter a valid email address.")
                        emailIndex.getProperty(
                            normalizedEmail,
                        ) != null -> WebAuthFieldAvailability(false, "That email is already registered.")
                        else -> WebAuthFieldAvailability(true, "Email is available.")
                    },
            )
        }

    fun register(
        displayName: String,
        email: String,
        password: String,
    ): WebAuthResult =
        lock.withLock {
            val normalizedEmail = normalizeEmail(email) ?: return WebAuthResult.Failure("Enter a valid email address.")
            val cleanName = displayName.trim().take(40).ifBlank { normalizedEmail.substringBefore('@') }
            val slug = slugFrom(cleanName) ?: return WebAuthResult.Failure("Enter an adventurer name.")
            if (slug in reservedSlugs) return WebAuthResult.Failure(RESERVED_NAME)
            if (password.length < 8) return WebAuthResult.Failure("Password must be at least 8 characters.")
            usersDir.mkdirs()
            emailIndexFile.parentFile?.mkdirs()
            val emailIndex = loadProperties(emailIndexFile)
            if (emailIndex.getProperty(normalizedEmail) != null) return WebAuthResult.Failure("That email is already registered.")
            val userFile = userFile(slug)
            if (userFile.isFile) return WebAuthResult.Failure("That adventurer name is already registered.")
            val properties = createAccount(slug, cleanName, normalizedEmail, password, emailIndex)
            WebAuthResult.Success(properties.account())
        }

    fun ensureAccount(account: WebAuthBootstrapAccount): WebAuthBootstrapResult =
        lock.withLock {
            val requestedSlug = slugFrom(account.displayName) ?: return WebAuthBootstrapResult.Failure("Enter an adventurer name.")
            val normalizedEmail =
                normalizeEmail(account.email ?: "$requestedSlug@local.invalid")
                    ?: return WebAuthBootstrapResult.Failure("Enter a valid email address.")
            val cleanName =
                account.displayName
                    .trim()
                    .take(40)
                    .ifBlank { normalizedEmail.substringBefore('@') }
            val slug = slugFrom(cleanName) ?: return WebAuthBootstrapResult.Failure("Enter an adventurer name.")
            if (account.password.length < 8) return WebAuthBootstrapResult.Failure("Password must be at least 8 characters.")
            usersDir.mkdirs()
            emailIndexFile.parentFile?.mkdirs()
            val emailIndex = loadProperties(emailIndexFile)
            val existingProperties =
                emailIndex.getProperty(normalizedEmail)?.let { indexedSlug -> loadUserProperties(indexedSlug) }
                    ?: loadUserProperties(slug)
            if (existingProperties != null) {
                if (!existingProperties.passwordMatches(account.password)) {
                    return WebAuthBootstrapResult.Failure("Account already exists but the password did not match.")
                }
                return WebAuthBootstrapResult.Existing(existingProperties.account())
            }
            val properties = createAccount(slug, cleanName, normalizedEmail, account.password, emailIndex)
            WebAuthBootstrapResult.Created(properties.account())
        }

    /**
     * Creates the sysop account [displayName] when it does not exist yet, and
     * leaves an existing one alone. The account gets [password] when the server
     * sets one; otherwise a generated password, written to [passwordFile] where
     * only the server's user can read it.
     */
    fun ensureSysopAccount(
        displayName: String,
        password: String?,
        passwordFile: File,
    ): WebSysopAccountResult =
        lock.withLock {
            val slug = slugFrom(displayName) ?: return WebSysopAccountResult.Failure("Enter an adventurer name.")
            loadUserProperties(slug)?.let { existing -> return WebSysopAccountResult.Existing(existing.account()) }
            val chosen = password ?: randomToken().also(passwordFile::writeOwnerOnly)
            when (val created = ensureAccount(WebAuthBootstrapAccount(displayName, null, chosen))) {
                is WebAuthBootstrapResult.Created ->
                    WebSysopAccountResult.Created(
                        created.account,
                        passwordFile.takeIf { password == null },
                    )
                is WebAuthBootstrapResult.Existing -> WebSysopAccountResult.Existing(created.account)
                is WebAuthBootstrapResult.Failure -> {
                    if (password == null) passwordFile.delete()
                    WebSysopAccountResult.Failure(created.message)
                }
            }
        }

    fun authenticate(
        login: String,
        password: String,
    ): WebAuthResult =
        lock.withLock {
            val account =
                findProperties(login)
                    ?: return WebAuthResult.Failure("Unknown account.")
            val stored =
                StoredPasswordHash(
                    iterations = account.getProperty("password.iterations")?.toIntOrNull() ?: PASSWORD_ITERATIONS,
                    salt = account.getProperty("password.salt").orEmpty(),
                    hash = account.getProperty("password.hash").orEmpty(),
                )
            if (!PasswordHasher.verify(password, stored)) return WebAuthResult.Failure("Wrong password.")
            WebAuthResult.Success(account.account())
        }

    /** Stores a new user record and its email index entry; callers hold [lock] and have checked availability. */
    private fun createAccount(
        slug: String,
        cleanName: String,
        normalizedEmail: String,
        password: String,
        emailIndex: Properties,
    ): Properties {
        val passwordHash = PasswordHasher.hash(password)
        val now = Instant.now().toString()
        val properties =
            Properties().apply {
                setProperty("slug", slug)
                setProperty("displayName", cleanName)
                setProperty("email", normalizedEmail)
                setProperty("emailConfirmed", "true")
                setProperty("password.algorithm", PASSWORD_ALGORITHM)
                setProperty("password.iterations", passwordHash.iterations.toString())
                setProperty("password.salt", passwordHash.salt)
                setProperty("password.hash", passwordHash.hash)
                setProperty("createdAt", now)
                setProperty("updatedAt", now)
            }
        storeProperties(userFile(slug), properties)
        emailIndex.setProperty(normalizedEmail, slug)
        storeProperties(emailIndexFile, emailIndex)
        return properties
    }

    private fun findProperties(login: String): Properties? {
        val raw = login.trim()
        if (raw.isBlank()) return null
        val slug =
            if (raw.contains('@')) {
                val emailIndex = loadProperties(emailIndexFile)
                emailIndex.getProperty(raw.lowercase(Locale.ROOT))
            } else {
                slugFrom(raw)
            } ?: return null
        val file = userFile(slug)
        if (!file.isFile) return null
        return loadProperties(file)
    }

    private fun loadUserProperties(slug: String): Properties? {
        val file = userFile(slug)
        if (!file.isFile) return null
        return loadProperties(file)
    }

    private fun userFile(slug: String): File = usersDir.resolve("$slug.properties")

    private fun Properties.account(): WebAccount =
        WebAccount(
            slug = getProperty("slug"),
            displayName = getProperty("displayName"),
            email = getProperty("email"),
            emailConfirmed = getProperty("emailConfirmed") == "true",
        )

    private fun Properties.passwordMatches(password: String): Boolean {
        val stored =
            StoredPasswordHash(
                iterations = getProperty("password.iterations")?.toIntOrNull() ?: PASSWORD_ITERATIONS,
                salt = getProperty("password.salt").orEmpty(),
                hash = getProperty("password.hash").orEmpty(),
            )
        return PasswordHasher.verify(password, stored)
    }
}

class WebSessionStore(
    private val ttl: Duration = Duration.ofHours(12),
) {
    private val sessions = ConcurrentHashMap<String, WebSession>()

    fun create(account: WebAccount): String {
        val token = randomToken()
        sessions[token] = WebSession(account, Instant.now())
        return token
    }

    fun account(token: String?): WebAccount? {
        val session = token?.let(sessions::get) ?: return null
        if (session.createdAt.plus(ttl).isBefore(Instant.now())) {
            sessions.remove(token)
            return null
        }
        return session.account
    }

    fun remove(token: String?) {
        if (token != null) sessions.remove(token)
    }
}

object WebAuthCookies {
    fun token(headers: Map<String, String>): String? = cookie(headers, SESSION_COOKIE)

    fun login(
        token: String,
        secure: Boolean = false,
    ): WebHttpHeader =
        WebHttpHeader("Set-Cookie", "$SESSION_COOKIE=$token; Path=/; HttpOnly; SameSite=Lax; Max-Age=43200${secureAttribute(secure)}")

    fun logout(secure: Boolean = false): WebHttpHeader =
        WebHttpHeader("Set-Cookie", "$SESSION_COOKIE=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0${secureAttribute(secure)}")

    fun rememberedLogin(headers: Map<String, String>): String =
        cookie(headers, REMEMBERED_LOGIN_COOKIE)?.let { URLDecoder.decode(it, Charsets.UTF_8) }.orEmpty()

    fun rememberLogin(
        login: String,
        secure: Boolean = false,
    ): WebHttpHeader =
        WebHttpHeader(
            "Set-Cookie",
            "$REMEMBERED_LOGIN_COOKIE=${URLEncoder.encode(
                login,
                Charsets.UTF_8,
            )}; Path=/; SameSite=Lax; Max-Age=31536000${secureAttribute(secure)}",
        )

    private fun secureAttribute(secure: Boolean): String = if (secure) "; Secure" else ""

    private fun cookie(
        headers: Map<String, String>,
        cookieName: String,
    ): String? =
        headers["cookie"]
            ?.split(';')
            ?.firstNotNullOfOrNull { part ->
                val separator = part.indexOf('=')
                val name = part.substring(0, separator.coerceAtLeast(0)).trim()
                part.substring(separator + 1).trim().takeIf { separator > 0 && name == cookieName }
            }
}

fun slugFrom(value: String): String? {
    val lowered = value.lowercase(Locale.ROOT)
    val cleaned = lowered.replace(Regex("[^a-z0-9_-]+"), "-").trim('-')
    if (cleaned.isBlank()) return null
    return cleaned.take(48)
}

private fun normalizeEmail(email: String): String? =
    email
        .trim()
        .lowercase(Locale.ROOT)
        .takeIf { value -> value.length <= 254 && value.contains('@') }

private data class PasswordHash(
    val iterations: Int,
    val salt: String,
    val hash: String,
)

private data class StoredPasswordHash(
    val iterations: Int,
    val salt: String,
    val hash: String,
)

private data class WebSession(
    val account: WebAccount,
    val createdAt: Instant,
)

private object PasswordHasher {
    private val random = SecureRandom()

    fun hash(password: String): PasswordHash {
        val salt = ByteArray(SALT_BYTES)
        random.nextBytes(salt)
        val hash = derive(password, salt, PASSWORD_ITERATIONS)
        return PasswordHash(
            iterations = PASSWORD_ITERATIONS,
            salt = Base64.getEncoder().encodeToString(salt),
            hash = Base64.getEncoder().encodeToString(hash),
        )
    }

    fun verify(
        password: String,
        stored: StoredPasswordHash,
    ): Boolean {
        val salt = runCatching { Base64.getDecoder().decode(stored.salt) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getDecoder().decode(stored.hash) }.getOrNull() ?: return false
        val actual = derive(password, salt, stored.iterations)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(
        password: String,
        salt: ByteArray,
        iterations: Int,
    ): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, PASSWORD_BITS)
        return SecretKeyFactory.getInstance(PASSWORD_ALGORITHM).generateSecret(spec).encoded
    }
}

private fun randomToken(): String {
    val bytes = ByteArray(TOKEN_BYTES)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

/** Writes [text] to this file, readable and writable only by its owner where the file system supports that. */
private fun File.writeOwnerOnly(text: String) {
    parentFile?.mkdirs()
    delete()
    createNewFile()
    try {
        Files.setPosixFilePermissions(toPath(), PosixFilePermissions.fromString("rw-------"))
    } catch (unsupported: UnsupportedOperationException) {
        // Not a POSIX file system; the file keeps its directory's permissions.
    }
    writeText(text + "\n")
}

private fun loadProperties(file: File): Properties =
    Properties().apply {
        if (file.isFile) file.inputStream().use(::load)
    }

private fun storeProperties(
    file: File,
    properties: Properties,
) {
    file.parentFile?.mkdirs()
    file.outputStream().use { output -> properties.store(output, null) }
}
