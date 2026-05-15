@file:Suppress("ReplacePrintlnWithLogging", "UseOfSystemOutOrSystemErr")

package rylween.bitmapweb

import rylween.compiled.CompiledGameBundle
import rylween.compiled.ExternalRuntimeSystems
import rylween.compiled.readExternalCompiledBundleContentHash
import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeSystems
import rylween.compiled.toExternalBundleArtifact
import rylween.game.RylweenCompiledGame
import rylween.runtime.RuntimeLog
import rylween.runtime.RuntimeSessionConfig
import rylween.runtime.RuntimeSessionFactory
import rylween.webcommon.WebHttpRequest
import rylween.webcommon.WebServerStatus
import rylween.webcommon.WebSocketConnection
import rylween.webcommon.WebSocketProtocol
import rylween.webcommon.auth.WebAccount
import rylween.webcommon.auth.WebAuthBootstrapAccount
import rylween.webcommon.auth.WebAuthBootstrapResult
import rylween.webcommon.auth.WebAuthCookies
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
import rylween.webcommon.urlDecode
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.exitProcess

private const val DEFAULT_HOST = "127.0.0.1"
private const val DEFAULT_PORT = 8081
private const val DEFAULT_DATA_DIR = "build/rylween-data"
private const val DEFAULT_STATE = "fresh-replay"
private val BITMAP_WEB_STATIC =
    mapOf(
        "styles.css" to ServedResource("bitmap-web/styles.css", "text/css; charset=utf-8"),
        "client.js" to ServedResource("bitmap-web/client.js", "application/javascript; charset=utf-8"),
    )
private val BITMAP_ASSETS =
    mapOf(
        "title-rider.png" to "bitmap/assets/title-rider.png",
        "screen-frame.png" to "bitmap/assets/screen-frame.png",
        "town-mainstreet.png" to "bitmap/assets/town-mainstreet.png",
        "encounter-scorpion.png" to "bitmap/assets/encounter-scorpion.png",
        "encounter-bestiary.png" to "bitmap/assets/encounter-bestiary.png",
        "scene-atlas.png" to "bitmap/assets/scene-atlas.png",
        "terrain-atlas.png" to "bitmap/assets/terrain-atlas.png",
        "castle-atlas.png" to "bitmap/assets/castle-atlas.png",
        "water-connect-atlas.png" to "bitmap/assets/water-connect-atlas.png",
        "party-rider.png" to "bitmap/assets/party-rider.png",
        "scene-thord-mainstreet.png" to "bitmap/assets/scene-thord-mainstreet.png",
        "scene-merchant.png" to "bitmap/assets/scene-merchant.png",
        "scene-smith.png" to "bitmap/assets/scene-smith.png",
        "scene-inn.png" to "bitmap/assets/scene-inn.png",
        "scene-tavern.png" to "bitmap/assets/scene-tavern.png",
        "scene-church.png" to "bitmap/assets/scene-church.png",
        "scene-guild-closed.png" to "bitmap/assets/scene-guild-closed.png",
        "monster-goblinoid.png" to "bitmap/assets/monster-goblinoid.png",
        "monster-undead.png" to "bitmap/assets/monster-undead.png",
        "monster-beast.png" to "bitmap/assets/monster-beast.png",
        "monster-flying.png" to "bitmap/assets/monster-flying.png",
        "monster-vermin.png" to "bitmap/assets/monster-vermin.png",
        "monster-brute.png" to "bitmap/assets/monster-brute.png",
        "monster-humanoid-magic.png" to "bitmap/assets/monster-humanoid-magic.png",
        "monster-boss.png" to "bitmap/assets/monster-boss.png",
    )

fun main(args: Array<String>) {
    runCatching {
        val config = BitmapWebConfig.parse(args)
        config.ensureDataDirectories()
        val game = BitmapGameBundle.load(config.bundleFile)
        RuntimeLog.open(config.serverLogFile, listOf(::streamServerLog)).use { serverLog ->
            BitmapWebServer(config, game, serverLog).use { server -> server.serveForever() }
        }
    }.getOrElse { error ->
        System.err.println("bitmap web runtime failed: ${error.message ?: error::class.simpleName}")
        exitProcess(1)
    }
}

private data class BitmapWebConfig(
    val host: String,
    val port: Int,
    val dataDir: File,
    val stateSlug: String,
    val bundleFile: File?,
    val userId: String?,
    val loadFile: File?,
    val saveFile: File?,
    val bootstrapAccount: WebAuthBootstrapAccount?,
    val secureCookies: Boolean,
    val showTitleMenu: Boolean,
    val mapTiles: Int,
    val tileSize: Int,
) {
    val saveDir: File get() = dataDir.resolve("saves")
    val logDir: File get() = dataDir.resolve("logs/runtime-bitmap-web")
    val authDir: File get() = dataDir.resolve("auth")
    val serverLogFile: File get() = logDir.resolve("server.log")
    val worldSaveFile: File get() = saveFile ?: saveDir.resolve("world.rsave")
    val authRequired: Boolean get() = userId == null

    fun ensureDataDirectories() {
        Files.createDirectories(saveDir.toPath())
        Files.createDirectories(logDir.toPath())
        Files.createDirectories(authDir.toPath())
        worldSaveFile.parentFile?.let { parent -> Files.createDirectories(parent.toPath()) }
    }

    fun logFile(user: WebAccount): File = logDir.resolve("${user.slug}-${System.currentTimeMillis()}.log")

    companion object {
        fun parse(args: Array<String>): BitmapWebConfig {
            var host = envOrDefault("RYLWEEN_BITMAP_HOST", envOrDefault("RYLWEEN_HOST", DEFAULT_HOST))
            var port = envOrDefault("RYLWEEN_BITMAP_PORT", envOrDefault("RYLWEEN_PORT", DEFAULT_PORT.toString())).toInt()
            var dataDir = File(envOrDefault("RYLWEEN_DATA_DIR", DEFAULT_DATA_DIR))
            var stateSlug = envOrDefault("RYLWEEN_BITMAP_STATE", envOrDefault("RYLWEEN_STATE", DEFAULT_STATE))
            var bundleFile = optionalFileEnv("RYLWEEN_BITMAP_BUNDLE")
            var userId = optionalEnv("RYLWEEN_BITMAP_USER") ?: optionalEnv("RYLWEEN_USER")
            var loadFile = optionalFileEnv("RYLWEEN_BITMAP_LOAD_FILE") ?: optionalFileEnv("RYLWEEN_LOAD_FILE")
            var saveFile = optionalFileEnv("RYLWEEN_BITMAP_SAVE_FILE") ?: optionalFileEnv("RYLWEEN_SAVE_FILE")
            var accountName = optionalEnv("RYLWEEN_AUTH_ACCOUNT")
            var accountEmail = optionalEnv("RYLWEEN_AUTH_EMAIL")
            var accountPassword = optionalEnv("RYLWEEN_AUTH_PASSWORD")
            var secureCookies = optionalEnv("RYLWEEN_AUTH_SECURE_COOKIES") == "true"
            var showTitleMenu = optionalEnv("RYLWEEN_BITMAP_NO_TITLE") != "true"
            var mapTiles = envOrDefault("RYLWEEN_BITMAP_MAP_TILES", DEFAULT_BITMAP_VIEWPORT_TILES.toString()).toInt()
            var tileSize = envOrDefault("RYLWEEN_BITMAP_TILE_SIZE", DEFAULT_BITMAP_TILE_SIZE.toString()).toInt()
            var index = 0
            while (index < args.size) {
                when (val arg = args[index]) {
                    "--host" -> {
                        index += 1
                        host = requiredValue(args, index, arg)
                    }
                    "--port" -> {
                        index += 1
                        port = requiredValue(args, index, arg).toInt()
                    }
                    "--data" -> {
                        index += 1
                        dataDir = File(requiredValue(args, index, arg))
                    }
                    "--state" -> {
                        index += 1
                        stateSlug = requiredValue(args, index, arg)
                    }
                    "--bundle" -> {
                        index += 1
                        bundleFile = File(requiredValue(args, index, arg))
                    }
                    "--user" -> {
                        index += 1
                        userId = requiredValue(args, index, arg)
                    }
                    "--load" -> {
                        index += 1
                        loadFile = File(requiredValue(args, index, arg))
                    }
                    "--save" -> {
                        index += 1
                        saveFile = File(requiredValue(args, index, arg))
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
                    "--title" -> {
                        showTitleMenu = true
                    }
                    "--no-title" -> {
                        showTitleMenu = false
                    }
                    "--map-tiles" -> {
                        index += 1
                        mapTiles = requiredValue(args, index, arg).toInt()
                    }
                    "--tile-size" -> {
                        index += 1
                        tileSize = requiredValue(args, index, arg).toInt()
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
            return BitmapWebConfig(
                host,
                port,
                dataDir,
                stateSlug,
                bundleFile,
                userId,
                loadFile,
                saveFile,
                bootstrapAccount,
                secureCookies,
                showTitleMenu,
                mapTiles,
                tileSize,
            )
        }

        private fun envOrDefault(
            key: String,
            defaultValue: String,
        ): String = System.getenv(key) ?: defaultValue

        private fun optionalEnv(key: String): String? = System.getenv(key)?.takeIf(String::isNotBlank)

        private fun optionalFileEnv(key: String): File? {
            val value = System.getenv(key)?.takeIf(String::isNotBlank) ?: return null
            return File(value)
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
            println(
                """
                Usage: rylween-bitmap-web [--host HOST] [--port PORT] [--data DIR] [--state SLUG] [--bundle FILE] [--user ID] [--load FILE] [--save FILE] [--account NAME --password PASSWORD [--email EMAIL]] [--secure-cookies] [--title|--no-title] [--map-tiles N] [--tile-size PX]

                The bitmap runtime uses the embedded :game bundle by default. --bundle can point
                at build/compiled/rylween-bundle.json to exercise an emitted artifact instead.
                Authenticated saves and logs live under --data. --user keeps the local dev bypass.
                --account/--password ensures an auth account exists before the server starts.
                --secure-cookies adds the Secure cookie attribute for HTTPS deployments.
                Map defaults: ${DEFAULT_BITMAP_VIEWPORT_TILES}x${DEFAULT_BITMAP_VIEWPORT_TILES} tiles at ${DEFAULT_BITMAP_TILE_SIZE}px.
                """.trimIndent(),
            )
        }
    }
}

private data class BitmapGameBundle(
    val bundle: CompiledGameBundle,
    val systems: ExternalRuntimeSystems,
    val contentHash: String,
    val source: String,
) {
    companion object {
        fun load(bundleFile: File?): BitmapGameBundle {
            if (bundleFile != null) {
                val bytes = bundleFile.readBytes()
                return BitmapGameBundle(
                    bundle = bytes.readExternalCompiledGameBundle(),
                    systems = bytes.readExternalRuntimeSystems(),
                    contentHash = bytes.readExternalCompiledBundleContentHash(),
                    source = bundleFile.path,
                )
            }
            val bytes = RylweenCompiledGame.nextRylween.toExternalBundleArtifact()
            return BitmapGameBundle(
                bundle = bytes.readExternalCompiledGameBundle(),
                systems = bytes.readExternalRuntimeSystems(),
                contentHash = bytes.readExternalCompiledBundleContentHash(),
                source = "embedded:RylweenCompiledGame.nextRylween",
            )
        }
    }
}

private class BitmapWebServer(
    private val config: BitmapWebConfig,
    private val game: BitmapGameBundle,
    private val serverLog: RuntimeLog,
) : Closeable {
    private val runtimeFactory = RuntimeSessionFactory.load(game.bundle, game.systems, game.contentHash)
    private val userStore = WebUserStore(config.authDir)
    private val sessions = WebSessionStore()
    private val activeUsers = ConcurrentHashMap.newKeySet<String>()
    private val bitmapImageById =
        game.systems.presentation.images
            .filter { image ->
                image.kind == "Bitmap"
            }.associateBy { image -> image.id }
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    private val serverSocket = ServerSocket()
    private val clientIds = AtomicLong(0)

    fun serveForever() {
        ensureBootstrapAccount()
        serverSocket.reuseAddress = true
        serverSocket.bind(InetSocketAddress(config.host, config.port))
        serverLog.event(
            "bitmap.server.start host=${config.host} port=${config.port} " +
                "data=${config.dataDir.path} auth=${config.authRequired} secureCookies=${config.secureCookies} state=${config.stateSlug} " +
                "devUser=${config.userId.orEmpty()} load=${config.loadFile?.path.orEmpty()} " +
                "save=${config.worldSaveFile.path} title=${config.showTitleMenu} mapTiles=${config.mapTiles} " +
                "tileSize=${config.tileSize} bundle=${game.source}",
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
                serverLog.event("bitmap.auth.bootstrap_created user=${result.account.slug} email=${serverLogValue(result.account.email)}")
            is WebAuthBootstrapResult.Existing ->
                serverLog.event("bitmap.auth.bootstrap_existing user=${result.account.slug} email=${serverLogValue(result.account.email)}")
            is WebAuthBootstrapResult.Failure ->
                error("Could not bootstrap account: ${result.message}")
        }
    }

    private fun handleClient(socket: Socket) {
        val clientId = clientIds.incrementAndGet()
        val remote = socket.remoteSocketAddress?.toString().orEmpty()
        serverLog.event("bitmap.client.connect id=$clientId remote=${serverLogValue(remote)}")
        socket.use { client ->
            runCatching { route(client, clientId) }
                .onFailure { failure ->
                    serverLog.event(
                        "bitmap.client.error id=$clientId remote=${serverLogValue(
                            remote,
                        )} error=${serverLogValue(failure.message ?: failure::class.simpleName.orEmpty())}",
                    )
                    runCatching { respondText(client, "500 Internal Server Error", "text/plain; charset=utf-8", "server error\n") }
                }
        }
        serverLog.event("bitmap.client.disconnect id=$clientId remote=${serverLogValue(remote)}")
    }

    private fun route(
        socket: Socket,
        clientId: Long,
    ) {
        val input = socket.getInputStream()
        val request = readHttpRequest(input) ?: return
        serverLog.event("bitmap.http.request id=$clientId method=${request.method} path=${request.path}")
        if (request.method !in setOf("GET", "POST")) {
            serverLog.event("bitmap.http.response id=$clientId status=405 path=${request.path}")
            respondText(socket, "405 Method Not Allowed", "text/plain; charset=utf-8", "GET required\n")
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
            "/api/status" -> routeStatus(socket, request, clientId)
            "/auth.css" -> serveCommonResource(socket, "auth.css", "text/css; charset=utf-8", clientId)
            "/healthz" -> {
                serverLog.event("bitmap.http.response id=$clientId status=200 path=${request.path}")
                respondText(socket, "200 OK", "text/plain; charset=utf-8", "ok\n")
            }
            "/ws/play" -> servePlaySocket(socket, input, request, clientId)
            else ->
                if (request.path.startsWith("/static/")) {
                    serveStaticResource(socket, request.path.removePrefix("/static/"), clientId)
                } else if (request.path.startsWith("/assets/game-image/")) {
                    serveBundleBitmapImage(socket, urlDecode(request.path.removePrefix("/assets/game-image/")), clientId)
                } else if (request.path.startsWith("/assets/")) {
                    serveAsset(socket, request.path.removePrefix("/assets/"), clientId)
                } else {
                    serverLog.event("bitmap.http.response id=$clientId status=404 path=${request.path}")
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
            serverLog.event("bitmap.http.response id=$clientId status=303 path=${request.path} location=/login")
            redirect(socket, "/login")
            return
        }
        serverLog.event("bitmap.http.response id=$clientId status=303 path=${request.path} location=/play")
        redirect(socket, "/play")
    }

    private fun routePlayPage(
        socket: Socket,
        request: WebHttpRequest,
        clientId: Long,
    ) {
        if (config.authRequired && authenticatedAccount(request) == null) {
            serverLog.event("bitmap.http.response id=$clientId status=303 path=${request.path} location=/login")
            redirect(socket, "/login")
            return
        }
        serverLog.event("bitmap.http.response id=$clientId status=200 path=${request.path}")
        respondText(socket, "200 OK", "text/html; charset=utf-8", bitmapPageHtml())
    }

    private fun routeLoginPage(
        socket: Socket,
        request: WebHttpRequest,
        clientId: Long,
    ) {
        if (!config.authRequired || authenticatedAccount(request) != null) {
            serverLog.event("bitmap.http.response id=$clientId status=303 path=${request.path} location=/play")
            redirect(socket, "/play")
            return
        }
        serverLog.event("bitmap.http.response id=$clientId status=200 path=${request.path}")
        respondText(
            socket,
            "200 OK",
            "text/html; charset=utf-8",
            webAuthPageHtml(WebAuthPageMode.Login, backgroundImage = "/assets/title-rider.png"),
        )
    }

    private fun routeRegisterPage(
        socket: Socket,
        request: WebHttpRequest,
        clientId: Long,
    ) {
        if (!config.authRequired || authenticatedAccount(request) != null) {
            serverLog.event("bitmap.http.response id=$clientId status=303 path=${request.path} location=/play")
            redirect(socket, "/play")
            return
        }
        serverLog.event("bitmap.http.response id=$clientId status=200 path=${request.path}")
        respondText(
            socket,
            "200 OK",
            "text/html; charset=utf-8",
            webAuthPageHtml(WebAuthPageMode.Register, backgroundImage = "/assets/title-rider.png"),
        )
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
                serverLog.event("bitmap.auth.login id=$clientId user=${result.account.slug} email=${serverLogValue(result.account.email)}")
                serverLog.event("bitmap.http.response id=$clientId status=303 path=${request.path} location=/play")
                redirect(socket, "/play", listOf(WebAuthCookies.login(token, secure = config.secureCookies)))
            }
            is WebAuthResult.Failure -> {
                serverLog.event(
                    "bitmap.auth.login_failed id=$clientId email=${serverLogValue(email)} reason=${serverLogValue(result.message)}",
                )
                serverLog.event("bitmap.http.response id=$clientId status=401 path=${request.path}")
                respondText(
                    socket,
                    "401 Unauthorized",
                    "text/html; charset=utf-8",
                    webAuthPageHtml(WebAuthPageMode.Login, result.message, email, backgroundImage = "/assets/title-rider.png"),
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
        when (val result = userStore.register(name, email, form["password"].orEmpty())) {
            is WebAuthResult.Success -> {
                val token = sessions.create(result.account)
                serverLog.event(
                    "bitmap.auth.register id=$clientId user=${result.account.slug} email=${serverLogValue(
                        result.account.email,
                    )} confirmed=${result.account.emailConfirmed}",
                )
                serverLog.event("bitmap.http.response id=$clientId status=303 path=${request.path} location=/play")
                redirect(socket, "/play", listOf(WebAuthCookies.login(token, secure = config.secureCookies)))
            }
            is WebAuthResult.Failure -> {
                serverLog.event(
                    "bitmap.auth.register_failed id=$clientId email=${serverLogValue(
                        email,
                    )} name=${serverLogValue(name)} reason=${serverLogValue(result.message)}",
                )
                serverLog.event("bitmap.http.response id=$clientId status=400 path=${request.path}")
                respondText(
                    socket,
                    "400 Bad Request",
                    "text/html; charset=utf-8",
                    webAuthPageHtml(WebAuthPageMode.Register, result.message, email, name, backgroundImage = "/assets/title-rider.png"),
                )
            }
        }
    }

    private fun routeLogout(
        socket: Socket,
        request: WebHttpRequest,
        clientId: Long,
    ) {
        val token = WebAuthCookies.token(request.headers)
        sessions.remove(token)
        serverLog.event("bitmap.auth.logout id=$clientId hadToken=${token != null}")
        serverLog.event("bitmap.http.response id=$clientId status=303 path=${request.path} location=/login")
        redirect(socket, "/login", listOf(WebAuthCookies.logout(secure = config.secureCookies)))
    }

    private fun routeStatus(
        socket: Socket,
        request: WebHttpRequest,
        clientId: Long,
    ) {
        val account = authenticatedAccount(request)
        if (config.authRequired && account == null) {
            serverLog.event("bitmap.http.response id=$clientId status=401 path=${request.path}")
            respondText(socket, "401 Unauthorized", "application/json; charset=utf-8", """{"error":"login required"}""")
            return
        }
        val body =
            WebServerStatus(
                serverType = "bitmap",
                authRequired = config.authRequired,
                currentUser = account?.displayName ?: account?.slug,
                activeUsers = activeUsers.toList().sorted(),
            ).toJson()
        serverLog.event("bitmap.http.response id=$clientId status=200 path=${request.path}")
        respondText(socket, "200 OK", "application/json; charset=utf-8", body)
    }

    private fun serveBundleBitmapImage(
        socket: Socket,
        imageId: String,
        clientId: Long,
    ) {
        val image = bitmapImageById[imageId]
        if (image == null || !image.resource.startsWith("bitmap/assets/")) {
            serverLog.event("bitmap.http.response id=$clientId status=404 image=${serverLogValue(imageId)}")
            respondText(socket, "404 Not Found", "text/plain; charset=utf-8", "image not found\n")
            return
        }
        val bytes =
            javaClass.classLoader.getResourceAsStream(image.resource)?.use(InputStream::readBytes)
                ?: run {
                    serverLog.event("bitmap.http.response id=$clientId status=404 image=${serverLogValue(imageId)}")
                    respondText(socket, "404 Not Found", "text/plain; charset=utf-8", "image not found\n")
                    return
                }
        serverLog.event("bitmap.http.response id=$clientId status=200 image=${serverLogValue(imageId)}")
        respondBytes(socket, "image/png", bytes)
    }

    private fun serveStaticResource(
        socket: Socket,
        assetName: String,
        clientId: Long,
    ) {
        val resource = BITMAP_WEB_STATIC[assetName]
        if (resource == null) {
            serverLog.event("bitmap.http.response id=$clientId status=404 static=${serverLogValue(assetName)}")
            respondText(socket, "404 Not Found", "text/plain; charset=utf-8", "asset not found\n")
            return
        }
        val bytes =
            javaClass.classLoader.getResourceAsStream(resource.path)?.use(InputStream::readBytes)
                ?: run {
                    serverLog.event("bitmap.http.response id=$clientId status=404 static=${serverLogValue(assetName)}")
                    respondText(socket, "404 Not Found", "text/plain; charset=utf-8", "asset not found\n")
                    return
                }
        serverLog.event("bitmap.http.response id=$clientId status=200 static=${serverLogValue(assetName)}")
        respondBytes(socket, resource.contentType, bytes)
    }

    private fun serveAsset(
        socket: Socket,
        assetName: String,
        clientId: Long,
    ) {
        val resourcePath = BITMAP_ASSETS[assetName]
        if (resourcePath == null) {
            serverLog.event("bitmap.http.response id=$clientId status=404 asset=${serverLogValue(assetName)}")
            respondText(socket, "404 Not Found", "text/plain; charset=utf-8", "asset not found\n")
            return
        }
        val bytes =
            javaClass.classLoader.getResourceAsStream(resourcePath)?.use(InputStream::readBytes)
                ?: run {
                    serverLog.event("bitmap.http.response id=$clientId status=404 asset=${serverLogValue(assetName)}")
                    respondText(socket, "404 Not Found", "text/plain; charset=utf-8", "asset not found\n")
                    return
                }
        serverLog.event("bitmap.http.response id=$clientId status=200 asset=${serverLogValue(assetName)}")
        respondBytes(socket, "image/png", bytes)
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
                    serverLog.event("bitmap.http.response id=$clientId status=404 resource=${serverLogValue(resourcePath)}")
                    respondText(socket, "404 Not Found", "text/plain; charset=utf-8", "resource not found\n")
                    return
                }
        serverLog.event("bitmap.http.response id=$clientId status=200 resource=${serverLogValue(resourcePath)}")
        respondBytes(socket, contentType, bytes)
    }

    private fun servePlaySocket(
        socket: Socket,
        input: InputStream,
        request: WebHttpRequest,
        clientId: Long,
    ) {
        if (!request.isWebSocketUpgrade()) {
            serverLog.event("bitmap.websocket.reject id=$clientId reason=missing_upgrade")
            respondText(socket, "400 Bad Request", "text/plain; charset=utf-8", "websocket required\n")
            return
        }
        val account = authenticatedAccount(request)
        if (account == null) {
            serverLog.event("bitmap.websocket.reject id=$clientId reason=auth_required")
            respondText(socket, "401 Unauthorized", "text/plain; charset=utf-8", "login required\n")
            return
        }
        if (!activeUsers.add(account.slug)) {
            serverLog.event("bitmap.websocket.reject id=$clientId user=${account.slug} reason=user_already_active")
            respondText(socket, "409 Conflict", "text/plain; charset=utf-8", "session already active\n")
            return
        }
        val output = socket.getOutputStream()
        writeWebSocketHandshake(output, request)
        val connection = WebSocketConnection(input, output)
        val runtimeInput = BitmapRuntimeInput()
        val screen =
            BitmapRuntimeScreen(
                game.bundle,
                game.systems,
                connection,
                mapTiles = config.mapTiles,
                tileSize = config.tileSize,
            )
        runtimeInput.textInputChanged = screen::setTextInput
        val logFile = config.logFile(account)
        val saveFile = config.worldSaveFile
        val loadFile = sessionLoadFile(saveFile, account)
        val showTitleMenu = config.showTitleMenu
        saveFile.parentFile?.mkdirs()
        serverLog.event(
            "bitmap.websocket.open id=$clientId user=${account.slug} load=${loadFile?.path.orEmpty()} " +
                "save=${saveFile.path} title=$showTitleMenu sessionLog=${logFile.path}",
        )
        val runtime =
            executor.submit<Any?> {
                RuntimeLog.open(logFile).use { runtimeLog ->
                    runtimeInput.logEvent = runtimeLog::event
                    runtimeLog.event(
                        "bitmap.session.start user=${account.slug} load=${loadFile?.path.orEmpty()} " +
                            "save=${saveFile.path} title=$showTitleMenu",
                    )
                    runtimeFactory
                        .start(
                            input = runtimeInput,
                            screen = screen,
                            config =
                                RuntimeSessionConfig(
                                    userId = account.slug,
                                    stateSlug = config.stateSlug,
                                    loadFile = loadFile,
                                    saveFile = saveFile,
                                    showTitleMenu = showTitleMenu,
                                ),
                            log = runtimeLog,
                        ).also { result ->
                            runtimeLog.event("bitmap.session.end user=${account.slug} reason=${result.reason}")
                            serverLog.event("bitmap.session.close user=${account.slug} reason=${result.reason} sessionLog=${logFile.path}")
                        }
                }
                connection.close()
                null
            }
        try {
            connection.readClientMessages { message ->
                serverLog.event("bitmap.websocket.input id=$clientId user=${account.slug} token=${serverLogValue(message)}")
                runtimeInput.acceptClientMessage(message)
            }
        } finally {
            runtimeInput.close()
            runCatching { runtime.get(5, TimeUnit.SECONDS) }
            activeUsers.remove(account.slug)
        }
        serverLog.event("bitmap.websocket.close id=$clientId user=${account.slug}")
    }

    private fun authenticatedAccount(request: WebHttpRequest): WebAccount? {
        config.userId?.let { userId ->
            val slug = slugFrom(userId) ?: userId
            return WebAccount(slug, userId, "$slug@local.invalid", emailConfirmed = true)
        }
        return sessions.account(WebAuthCookies.token(request.headers))
    }

    private fun sessionLoadFile(
        saveFile: File,
        account: WebAccount,
    ): File? {
        if (saveFile.hasUserSave(account.slug)) return saveFile
        val initialLoadFile = config.loadFile ?: return saveFile.takeIf(File::isFile)
        if (initialLoadFile.isFile) return initialLoadFile
        return saveFile.takeIf(File::isFile)
    }

    private fun File.hasUserSave(userId: String): Boolean = runtimeFactory.hasUserSave(this, userId)
}

private fun streamServerLog(line: String) {
    println("[rylween-bitmap] $line")
}

private fun serverLogValue(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

private fun writeWebSocketHandshake(
    output: java.io.OutputStream,
    request: WebHttpRequest,
) {
    val key = request.header("sec-websocket-key") ?: error("missing websocket key")
    WebSocketProtocol.writeHandshake(output, key)
}

private data class ServedResource(
    val path: String,
    val contentType: String,
)
