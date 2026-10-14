/**
 * Standalone HTTP/WebSocket launcher for browser-served ANSI terminal runtime
 * sessions.
 */

package rylween.web

import rylween.runtime.RuntimeLog
import rylween.runtime.RuntimeSecretAccounts
import rylween.runtime.RuntimeSessionConfig
import rylween.runtime.RuntimeSessionFactory
import rylween.webcommon.WebHttpRequest
import rylween.webcommon.WebServerStatus
import rylween.webcommon.WebSocketConnection
import rylween.webcommon.WebSocketProtocol
import rylween.webcommon.auth.WebAccount
import rylween.webcommon.auth.WebAuthAvailability
import rylween.webcommon.auth.WebAuthBootstrapAccount
import rylween.webcommon.auth.WebAuthBootstrapResult
import rylween.webcommon.auth.WebAuthCookies
import rylween.webcommon.auth.WebAuthFieldAvailability
import rylween.webcommon.auth.WebAuthPageMode
import rylween.webcommon.auth.WebAuthResult
import rylween.webcommon.auth.WebSessionStore
import rylween.webcommon.auth.WebUserStore
import rylween.webcommon.auth.slugFrom
import rylween.webcommon.auth.webAuthPageHtml
import rylween.webcommon.readHttpRequest
import rylween.webcommon.redirect
import rylween.webcommon.respondBytes
import rylween.webcommon.respondText
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.system.exitProcess

private const val DEFAULT_HOST = "0.0.0.0"
private const val DEFAULT_PORT = 8080
private const val DEFAULT_BUNDLE = "/app/rylween-bundle.json"
private const val DEFAULT_DATA_DIR = "/data"
private const val DEFAULT_STATE = "fresh-replay"

fun main(args: Array<String>) {
    val config = WebRuntimeConfig.parse(args)
    config.ensureDataDirectories()
    val factory = RuntimeSessionFactory.load(config.bundleFile)
    RuntimeLog.open(config.serverLogFile, listOf(::streamRuntimeLog)).use { serverLog ->
        RuntimeWebServer(config, factory, serverLog).use { server ->
            server.serveForever()
        }
    }
}

private data class WebRuntimeConfig(
    val bundleFile: File,
    val dataDir: File,
    val host: String,
    val port: Int,
    val stateSlug: String,
    val initialLoadFile: File?,
    val saveFileOverride: File?,
    val devUserId: String?,
    val bootstrapAccount: WebAuthBootstrapAccount?,
    val secureCookies: Boolean,
    val seed: Long?,
) {
    val saveDir: File get() = dataDir.resolve("saves")
    val logDir: File get() = dataDir.resolve("logs")
    val authDir: File get() = dataDir.resolve("auth")
    val serverLogFile: File get() = logDir.resolve("web-server.log")
    val worldSaveFile: File get() = saveFileOverride ?: saveDir.resolve("world.rsave")
    val authRequired: Boolean get() = devUserId == null

    fun ensureDataDirectories() {
        Files.createDirectories(saveDir.toPath())
        Files.createDirectories(logDir.toPath())
        Files.createDirectories(authDir.toPath())
        worldSaveFile.parentFile?.let { parent -> Files.createDirectories(parent.toPath()) }
    }

    fun logFile(user: WebAccount): File = logDir.resolve("${user.slug}-${System.currentTimeMillis()}.log")

    companion object {
        fun parse(args: Array<String>): WebRuntimeConfig {
            var bundleFile = File(envOrDefault("RYLWEEN_BUNDLE", DEFAULT_BUNDLE))
            var dataDir = File(envOrDefault("RYLWEEN_DATA_DIR", DEFAULT_DATA_DIR))
            var host = envOrDefault("RYLWEEN_HOST", DEFAULT_HOST)
            var port = envOrDefault("RYLWEEN_PORT", DEFAULT_PORT.toString()).toIntOrNull() ?: DEFAULT_PORT
            var stateSlug = envOrDefault("RYLWEEN_STATE", DEFAULT_STATE)
            var initialLoadFile = optionalFileEnv("RYLWEEN_LOAD_FILE")
            var saveFileOverride = optionalFileEnv("RYLWEEN_SAVE_FILE")
            var devUserId = optionalEnv("RYLWEEN_AUTH_DEV_USER")
            var accountName = optionalEnv("RYLWEEN_AUTH_ACCOUNT")
            var accountEmail = optionalEnv("RYLWEEN_AUTH_EMAIL")
            var accountPassword = optionalEnv("RYLWEEN_AUTH_PASSWORD")
            var secureCookies = optionalEnv("RYLWEEN_AUTH_SECURE_COOKIES") == "true"
            var seed = optionalSeedEnv()
            var index = 0
            while (index < args.size) {
                val arg = args[index]
                when (arg) {
                    "--bundle" -> {
                        index += 1
                        bundleFile = File(requiredValue(args, index, arg))
                    }
                    "--data" -> {
                        index += 1
                        dataDir = File(requiredValue(args, index, arg))
                    }
                    "--host" -> {
                        index += 1
                        host = requiredValue(args, index, arg)
                    }
                    "--port" -> {
                        index += 1
                        port = requiredValue(args, index, arg).toInt()
                    }
                    "--state" -> {
                        index += 1
                        stateSlug = requiredValue(args, index, arg)
                    }
                    "--load" -> {
                        index += 1
                        initialLoadFile = File(requiredValue(args, index, arg))
                    }
                    "--save" -> {
                        index += 1
                        saveFileOverride = File(requiredValue(args, index, arg))
                    }
                    "--dev-user" -> {
                        index += 1
                        devUserId = requiredValue(args, index, arg)
                    }
                    "--account" -> {
                        index += 1
                        accountName = requiredValue(args, index, arg)
                    }
                    "--email" -> {
                        index += 1
                        accountEmail = requiredValue(args, index, arg)
                    }
                    "--password" -> {
                        index += 1
                        accountPassword = requiredValue(args, index, arg)
                    }
                    "--secure-cookies" -> {
                        secureCookies = true
                    }
                    "--seed" -> {
                        index += 1
                        seed = requiredValue(args, index, arg).toLong()
                    }
                    "--help", "-h" -> {
                        printUsage()
                        exitProcess(0)
                    }
                    else -> error("Unknown option: $arg")
                }
                index += 1
            }
            val bootstrapAccount = parseBootstrapAccount(accountName, accountEmail, accountPassword)
            return WebRuntimeConfig(
                bundleFile,
                dataDir,
                host,
                port,
                stateSlug,
                initialLoadFile,
                saveFileOverride,
                devUserId,
                bootstrapAccount,
                secureCookies,
                seed,
            )
        }

        private fun envOrDefault(
            key: String,
            defaultValue: String,
        ): String = System.getenv(key) ?: defaultValue

        private fun optionalFileEnv(key: String): File? {
            val value = System.getenv(key)?.takeIf(String::isNotBlank) ?: return null
            return File(value)
        }

        private fun optionalEnv(key: String): String? = System.getenv(key)?.takeIf(String::isNotBlank)

        private fun optionalSeedEnv(): Long? {
            val value = System.getenv("RYLWEEN_SEED")?.takeIf(String::isNotBlank) ?: return null
            return value.toLong()
        }

        private fun requiredValue(
            args: Array<String>,
            index: Int,
            option: String,
        ): String = args.getOrNull(index) ?: error("$option requires a value")

        private fun parseBootstrapAccount(
            name: String?,
            email: String?,
            password: String?,
        ): WebAuthBootstrapAccount? {
            if (name == null && email == null && password == null) return null
            val accountName = name ?: error("--account or RYLWEEN_AUTH_ACCOUNT is required when bootstrapping an account")
            val accountPassword = password ?: error("--password or RYLWEEN_AUTH_PASSWORD is required when bootstrapping an account")
            return WebAuthBootstrapAccount(accountName, email, accountPassword)
        }

        private fun printUsage() {
            WebConsole.line(
                """
                Usage: rylween-web [--bundle FILE] [--data DIR] [--host HOST] [--port PORT]
                                   [--state SLUG] [--load FILE] [--save FILE] [--dev-user USER]
                                   [--account NAME --password PASSWORD [--email EMAIL]]
                                   [--secure-cookies] [--seed N]

                Environment:
                  RYLWEEN_BUNDLE    Compiled bundle path. Default: $DEFAULT_BUNDLE
                  RYLWEEN_DATA_DIR  Save/log data directory. Default: $DEFAULT_DATA_DIR
                  RYLWEEN_HOST      Bind host. Default: $DEFAULT_HOST
                  RYLWEEN_PORT      Bind port. Default: $DEFAULT_PORT
                  RYLWEEN_STATE     Initial state slug. Default: $DEFAULT_STATE
                  RYLWEEN_LOAD_FILE Initial save loaded for users without a save.
                  RYLWEEN_SAVE_FILE Shared world save path. Default: data/saves/world.rsave
                  RYLWEEN_AUTH_DEV_USER Optional local/dev auth bypass user.
                  RYLWEEN_AUTH_ACCOUNT Optional account to create/verify before startup.
                  RYLWEEN_AUTH_EMAIL Optional email for RYLWEEN_AUTH_ACCOUNT.
                  RYLWEEN_AUTH_PASSWORD Required when RYLWEEN_AUTH_ACCOUNT is set.
                  RYLWEEN_AUTH_SECURE_COOKIES Set true when served through HTTPS.
                  RYLWEEN_ADMIN_ACCOUNT Sysop account with the secret keys. Default: admin
                  RYLWEEN_VICTIM_ACCOUNT Account the sysop gods hunt down. Default: none
                  RYLWEEN_SEED      Optional runtime RNG seed.
                """.trimIndent(),
            )
        }
    }
}

private class RuntimeWebServer(
    private val config: WebRuntimeConfig,
    private val factory: RuntimeSessionFactory,
    private val serverLog: RuntimeLog,
) : Closeable {
    // Players cannot register the sysop account; only a bootstrap account can claim it.
    private val userStore = WebUserStore(config.authDir, reservedNames = setOf(RuntimeSecretAccounts.fromEnvironment().admin))
    private val sessions = WebSessionStore()
    private val activeUsers = ConcurrentHashMap.newKeySet<String>()
    private val worldSessionLock = ReentrantLock(true)
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    private val serverSocket = ServerSocket()
    private val clientIds = AtomicLong(0)

    fun serveForever() {
        ensureBootstrapAccount()
        serverSocket.reuseAddress = true
        serverSocket.bind(InetSocketAddress(config.host, config.port))
        serverLog.event(
            "web.server.start host=${config.host} port=${config.port} bundle=${config.bundleFile.path} " +
                "data=${config.dataDir.path} auth=${config.authRequired} " +
                "secureCookies=${config.secureCookies} devUser=${config.devUserId.orEmpty()} " +
                "save=${config.worldSaveFile.path} serverLog=${config.serverLogFile.path}",
        )
        while (!serverSocket.isClosed) {
            val socket = serverSocket.accept()
            executor.execute { handleClient(socket) }
        }
    }

    override fun close() {
        serverSocket.close()
        executor.shutdownNow()
    }

    private fun ensureBootstrapAccount() {
        val bootstrap = config.bootstrapAccount ?: return
        when (val result = userStore.ensureAccount(bootstrap)) {
            is WebAuthBootstrapResult.Created ->
                serverLog.event("web.auth.bootstrap_created user=${result.account.slug} email=${logValue(result.account.email)}")
            is WebAuthBootstrapResult.Existing ->
                serverLog.event("web.auth.bootstrap_existing user=${result.account.slug} email=${logValue(result.account.email)}")
            is WebAuthBootstrapResult.Failure ->
                error("Could not bootstrap account: ${result.message}")
        }
    }

    private fun handleClient(socket: Socket) {
        val clientId = clientIds.incrementAndGet()
        val remote = socket.remoteSocketAddress?.toString().orEmpty()
        serverLog.event("web.client.connect id=$clientId remote=${logValue(remote)}")
        socket.use { client ->
            runCatching { route(client, clientId, remote) }
                .onFailure { failure ->
                    serverLog.event("web.client.error id=$clientId remote=${logValue(remote)} error=${logValue(failure.message.orEmpty())}")
                    runCatching {
                        respondText(client, "500 Internal Server Error", "text/plain; charset=utf-8", "server error\n")
                    }
                }
        }
        serverLog.event("web.client.disconnect id=$clientId remote=${logValue(remote)}")
    }

    private fun route(
        socket: Socket,
        clientId: Long,
        remote: String,
    ) {
        val input = socket.getInputStream()
        val request = readHttpRequest(input) ?: return
        serverLog.event("web.http.request id=$clientId remote=${logValue(remote)} method=${request.method} path=${request.path}")
        if (request.method !in setOf("GET", "POST")) {
            serverLog.event("web.http.response id=$clientId status=405 path=${request.path}")
            respondText(socket, "405 Method Not Allowed", "text/plain; charset=utf-8", "GET or POST required\n")
            return
        }
        when (request.path) {
            "/" -> routeHome(socket, request, clientId)
            "/play" -> routePlayPage(socket, request, clientId)
            "/login" -> routeLoginPage(socket, request, clientId)
            "/register" -> routeRegisterPage(socket, request, clientId)
            "/api/login" -> routeLogin(socket, request, clientId)
            "/api/register" -> routeRegister(socket, request, clientId)
            "/api/logout" -> routeLogout(socket, request, clientId)
            "/api/auth/availability" -> routeAuthAvailability(socket, request, clientId)
            "/api/status" -> routeStatus(socket, request, clientId)
            "/auth.css" -> serveCommonResource(socket, "auth.css", "text/css; charset=utf-8", clientId)
            "/healthz" -> {
                serverLog.event("web.http.response id=$clientId status=200 path=${request.path}")
                respondText(socket, "200 OK", "text/plain; charset=utf-8", "ok\n")
            }
            "/ws/play" -> servePlaySocket(socket, input, request, clientId, remote)
            else -> {
                serverLog.event("web.http.response id=$clientId status=404 path=${request.path}")
                respondText(socket, "404 Not Found", "text/plain; charset=utf-8", "not found\n")
            }
        }
    }

    private fun routeHome(
        socket: Socket,
        request: WebHttpRequest,
        clientId: Long,
    ) {
        if (config.authRequired && authenticatedAccount(request) == null) {
            serverLog.event("web.http.response id=$clientId status=303 path=${request.path} location=/login")
            redirect(socket, "/login")
            return
        }
        serverLog.event("web.http.response id=$clientId status=303 path=${request.path} location=/play")
        redirect(socket, "/play")
    }

    private fun routePlayPage(
        socket: Socket,
        request: WebHttpRequest,
        clientId: Long,
    ) {
        if (config.authRequired && authenticatedAccount(request) == null) {
            serverLog.event("web.http.response id=$clientId status=303 path=${request.path} location=/login")
            redirect(socket, "/login")
            return
        }
        serverLog.event("web.http.response id=$clientId status=200 path=${request.path}")
        respondText(socket, "200 OK", "text/html; charset=utf-8", terminalPageHtml())
    }

    private fun routeLoginPage(
        socket: Socket,
        request: WebHttpRequest,
        clientId: Long,
    ) {
        routeAuthPage(socket, request, clientId) {
            webAuthPageHtml(WebAuthPageMode.Login, rememberedLogin = WebAuthCookies.rememberedLogin(request.headers))
        }
    }

    private fun routeRegisterPage(
        socket: Socket,
        request: WebHttpRequest,
        clientId: Long,
    ) {
        routeAuthPage(socket, request, clientId) {
            webAuthPageHtml(WebAuthPageMode.Register)
        }
    }

    /** Serves an account page, or sends visitors who are already signed in (or need no account) to `/play`. */
    private fun routeAuthPage(
        socket: Socket,
        request: WebHttpRequest,
        clientId: Long,
        page: () -> String,
    ) {
        if (!config.authRequired || authenticatedAccount(request) != null) {
            serverLog.event("web.http.response id=$clientId status=303 path=${request.path} location=/play")
            redirect(socket, "/play")
            return
        }
        serverLog.event("web.http.response id=$clientId status=200 path=${request.path}")
        respondText(socket, "200 OK", "text/html; charset=utf-8", page())
    }

    private fun routeLogin(
        socket: Socket,
        request: WebHttpRequest,
        clientId: Long,
    ) {
        val form = request.formFields()
        val email = form["email"].orEmpty()
        when (val result = userStore.authenticate(email, form["password"].orEmpty())) {
            is WebAuthResult.Success -> {
                val token = sessions.create(result.account)
                serverLog.event("web.auth.login id=$clientId user=${result.account.slug} email=${logValue(result.account.email)}")
                serverLog.event("web.http.response id=$clientId status=303 path=${request.path} location=/play")
                redirect(
                    socket,
                    "/play",
                    listOf(
                        WebAuthCookies.login(token, secure = config.secureCookies),
                        WebAuthCookies.rememberLogin(result.account.email, secure = config.secureCookies),
                    ),
                )
            }
            is WebAuthResult.Failure -> {
                serverLog.event("web.auth.login_failed id=$clientId email=${logValue(email)} reason=${logValue(result.message)}")
                serverLog.event("web.http.response id=$clientId status=401 path=${request.path}")
                respondText(
                    socket,
                    "401 Unauthorized",
                    "text/html; charset=utf-8",
                    webAuthPageHtml(WebAuthPageMode.Login, result.message, email),
                )
            }
        }
    }

    private fun routeRegister(
        socket: Socket,
        request: WebHttpRequest,
        clientId: Long,
    ) {
        val form = request.formFields()
        val name = form["name"].orEmpty()
        val email = form["email"].orEmpty()
        savedPartyNameConflict(name)?.let { message ->
            serverLog.event(
                "web.auth.register_failed id=$clientId email=${logValue(email)} name=${logValue(name)} reason=${logValue(message)}",
            )
            serverLog.event("web.http.response id=$clientId status=400 path=${request.path}")
            respondText(
                socket,
                "400 Bad Request",
                "text/html; charset=utf-8",
                webAuthPageHtml(WebAuthPageMode.Register, message, email, name),
            )
            return
        }
        when (val result = userStore.register(name, email, form["password"].orEmpty())) {
            is WebAuthResult.Success -> {
                val token = sessions.create(result.account)
                serverLog.event(
                    "web.auth.register id=$clientId user=${result.account.slug} email=${logValue(
                        result.account.email,
                    )} confirmed=${result.account.emailConfirmed}",
                )
                serverLog.event("web.http.response id=$clientId status=303 path=${request.path} location=/play")
                redirect(
                    socket,
                    "/play",
                    listOf(
                        WebAuthCookies.login(token, secure = config.secureCookies),
                        WebAuthCookies.rememberLogin(result.account.email, secure = config.secureCookies),
                    ),
                )
            }
            is WebAuthResult.Failure -> {
                serverLog.event(
                    "web.auth.register_failed id=$clientId email=${logValue(
                        email,
                    )} name=${logValue(name)} reason=${logValue(result.message)}",
                )
                serverLog.event("web.http.response id=$clientId status=400 path=${request.path}")
                respondText(
                    socket,
                    "400 Bad Request",
                    "text/html; charset=utf-8",
                    webAuthPageHtml(WebAuthPageMode.Register, result.message, email, name),
                )
            }
        }
    }

    private fun routeAuthAvailability(
        socket: Socket,
        request: WebHttpRequest,
        clientId: Long,
    ) {
        val name = request.query["name"].orEmpty()
        val email = request.query["email"].orEmpty()
        val availability = userStore.availability(name, email).withSavedPartyNameConflict(name)
        serverLog.event("web.http.response id=$clientId status=200 path=${request.path}")
        respondText(socket, "200 OK", "application/json; charset=utf-8", availability.toJson())
    }

    private fun savedPartyNameConflict(name: String): String? {
        val slug = slugFrom(name) ?: return null
        return "That adventurer name already has saved game data.".takeIf {
            factory.hasUserSave(config.worldSaveFile, slug)
        }
    }

    private fun WebAuthAvailability.withSavedPartyNameConflict(name: String): WebAuthAvailability {
        val message = savedPartyNameConflict(name) ?: return this
        return copy(name = WebAuthFieldAvailability(false, message))
    }

    private fun routeLogout(
        socket: Socket,
        request: WebHttpRequest,
        clientId: Long,
    ) {
        val token = WebAuthCookies.token(request.headers)
        sessions.remove(token)
        serverLog.event("web.auth.logout id=$clientId hadToken=${token != null}")
        serverLog.event("web.http.response id=$clientId status=303 path=${request.path} location=/login")
        redirect(socket, "/login", listOf(WebAuthCookies.logout(secure = config.secureCookies)))
    }

    private fun routeStatus(
        socket: Socket,
        request: WebHttpRequest,
        clientId: Long,
    ) {
        val account = authenticatedAccount(request)
        if (config.authRequired && account == null) {
            serverLog.event("web.http.response id=$clientId status=401 path=${request.path}")
            respondText(socket, "401 Unauthorized", "application/json; charset=utf-8", """{"error":"login required"}""")
            return
        }
        val body =
            WebServerStatus(
                serverType = "ansi",
                authRequired = config.authRequired,
                currentUser = account?.displayName ?: account?.slug,
                activeUsers = activeUsers.toList().sorted(),
            ).toJson()
        serverLog.event("web.http.response id=$clientId status=200 path=${request.path}")
        respondText(socket, "200 OK", "application/json; charset=utf-8", body)
    }

    private fun serveCommonResource(
        socket: Socket,
        resourcePath: String,
        contentType: String,
        clientId: Long,
    ) {
        val bytes =
            javaClass.classLoader.getResourceAsStream(resourcePath)?.use(InputStream::readBytes)
                ?: run {
                    serverLog.event("web.http.response id=$clientId status=404 resource=${logValue(resourcePath)}")
                    respondText(socket, "404 Not Found", "text/plain; charset=utf-8", "resource not found\n")
                    return
                }
        serverLog.event("web.http.response id=$clientId status=200 resource=${logValue(resourcePath)}")
        respondBytes(socket, contentType, bytes)
    }

    private fun servePlaySocket(
        socket: Socket,
        input: InputStream,
        request: WebHttpRequest,
        clientId: Long,
        remote: String,
    ) {
        if (!request.isWebSocketUpgrade()) {
            serverLog.event("web.websocket.reject id=$clientId remote=${logValue(remote)} reason=missing_upgrade")
            respondText(socket, "400 Bad Request", "text/plain; charset=utf-8", "websocket required\n")
            return
        }
        val user = authenticatedAccount(request)
        if (user == null) {
            serverLog.event("web.websocket.reject id=$clientId remote=${logValue(remote)} reason=auth_required")
            respondText(socket, "401 Unauthorized", "text/plain; charset=utf-8", "login required\n")
            return
        }
        serverLog.event(
            "web.user.session id=$clientId remote=${logValue(remote)} " +
                "user=${user.slug} displayName=${logValue(user.displayName)}",
        )
        if (!activeUsers.add(user.slug)) {
            rejectBusySocket(socket, request, user, clientId, remote)
            return
        }
        val output = socket.getOutputStream()
        writeWebSocketHandshake(output, request)
        serverLog.event("web.websocket.upgrade id=$clientId remote=${logValue(remote)} user=${user.slug}")
        val connection = WebSocketConnection(input, output)
        val terminal = BrowserAnsiTerminal(connection)
        val reader =
            Thread {
                try {
                    connection.readClientMessages { message ->
                        serverLog.event(
                            "web.websocket.input id=$clientId remote=${logValue(remote)} user=${user.slug} token=${logValue(message)}",
                        )
                        terminal.acceptClientMessage(message)
                    }
                } finally {
                    terminal.close()
                }
            }
        reader.name = "rylween-websocket-reader-${user.slug}"
        reader.isDaemon = true
        reader.start()
        try {
            runSession(user, terminal)
        } finally {
            activeUsers.remove(user.slug)
            terminal.close()
            serverLog.event("web.websocket.close id=$clientId remote=${logValue(remote)} user=${user.slug}")
        }
    }

    private fun rejectBusySocket(
        socket: Socket,
        request: WebHttpRequest,
        user: WebAccount,
        clientId: Long,
        remote: String,
    ) {
        serverLog.event("web.websocket.reject id=$clientId remote=${logValue(remote)} user=${user.slug} reason=user_already_active")
        val output = socket.getOutputStream()
        writeWebSocketHandshake(output, request)
        WebSocketConnection(socket.getInputStream(), output).use { connection ->
            connection.sendText("\u001B[2J\u001B[1;1HSession already active for ${user.displayName}.\r\n")
            connection.close()
        }
    }

    private fun runSession(
        user: WebAccount,
        terminal: BrowserAnsiTerminal,
    ) {
        val logFile = config.logFile(user)
        serverLog.event("web.world.wait user=${user.slug} save=${config.worldSaveFile.path}")
        terminal.writeRaw("\u001B[2J\u001B[1;1HWaiting for the Rylween world...\r\n")
        worldSessionLock.withLock {
            runLockedSession(user, terminal, logFile)
        }
    }

    private fun runLockedSession(
        user: WebAccount,
        terminal: BrowserAnsiTerminal,
        logFile: File,
    ) {
        val saveFile = config.worldSaveFile
        val loadFile = sessionLoadFile(saveFile)
        serverLog.event(
            "web.session.open user=${user.slug} load=${loadFile?.path.orEmpty()} " +
                "save=${saveFile.path} sessionLog=${logFile.path}",
        )
        logLoadDecision(user, saveFile, loadFile)
        RuntimeLog.open(logFile, listOf(::streamRuntimeLog)).use { log ->
            log.event("web.session.start user=${user.slug} load=${loadFile?.path.orEmpty()} save=${saveFile.path}")
            val runtimeConfig =
                RuntimeSessionConfig(
                    userId = user.slug,
                    stateSlug = config.stateSlug,
                    loadFile = loadFile,
                    saveFile = saveFile,
                    seed = config.seed,
                    showTitleMenu = true,
                )
            val result =
                try {
                    factory.start(terminal, runtimeConfig, log)
                } catch (error: Throwable) {
                    log.failure("web.session.failed user=${user.slug}", error)
                    serverLog.failure("web.session.failed user=${user.slug} sessionLog=${logFile.path}", error)
                    throw error
                }
            log.event("web.session.end user=${user.slug} reason=${result.reason}")
            serverLog.event("web.session.close user=${user.slug} reason=${result.reason} sessionLog=${logFile.path}")
        }
    }

    private fun logLoadDecision(
        user: WebAccount,
        saveFile: File,
        loadFile: File?,
    ) {
        if (saveFile.isFile) {
            serverLog.event("web.user.restore_world user=${user.slug} file=${saveFile.path}")
            return
        }
        if (loadFile != null) {
            serverLog.event("web.user.initial_world_load user=${user.slug} file=${loadFile.path} save=${saveFile.path}")
            return
        }
        serverLog.event("web.user.create_world_prompt user=${user.slug} save=${saveFile.path}")
    }

    private fun sessionLoadFile(saveFile: File): File? {
        if (saveFile.isFile) return saveFile
        val initialLoadFile = config.initialLoadFile ?: return null
        if (initialLoadFile.isFile) return initialLoadFile
        return null
    }

    private fun authenticatedAccount(request: WebHttpRequest): WebAccount? {
        config.devUserId?.let { userId ->
            val slug = slugFrom(userId) ?: userId
            return WebAccount(slug, userId, "$slug@local.invalid", emailConfirmed = true)
        }
        return sessions.account(WebAuthCookies.token(request.headers))
    }
}

private fun streamRuntimeLog(line: String) {
    WebConsole.line(line)
}

private object WebConsole {
    fun line(value: String) {
        System.out.println(value)
    }
}

private fun WebAuthAvailability.toJson(): String = """{"name":${name.toJson()},"email":${email.toJson()}}"""

private fun WebAuthFieldAvailability.toJson(): String = """{"available":$available,"message":${message.jsonString()}}"""

private fun String.jsonString(): String =
    buildString {
        append('"')
        this@jsonString.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }

private fun logValue(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

private fun writeWebSocketHandshake(
    output: java.io.OutputStream,
    request: WebHttpRequest,
) {
    val key = request.header("sec-websocket-key") ?: error("missing websocket key")
    WebSocketProtocol.writeHandshake(output, key)
}
