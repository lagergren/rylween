package rylween.web

import rylween.runtime.RuntimeLog
import rylween.runtime.RuntimeSessionConfig
import rylween.runtime.RuntimeSessionFactory
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
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
private const val MAX_HTTP_LINE_BYTES = 16_384
private const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

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
    val seed: Long?,
) {
    val saveDir: File get() = dataDir.resolve("saves")
    val logDir: File get() = dataDir.resolve("logs")
    val serverLogFile: File get() = logDir.resolve("web-server.log")
    val worldSaveFile: File get() = saveFileOverride ?: saveDir.resolve("world.rsave")

    fun ensureDataDirectories() {
        Files.createDirectories(saveDir.toPath())
        Files.createDirectories(logDir.toPath())
        worldSaveFile.parentFile?.let { parent -> Files.createDirectories(parent.toPath()) }
    }

    fun logFile(user: WebUser): File = logDir.resolve("${user.slug}-${System.currentTimeMillis()}.log")

    companion object {
        fun parse(args: Array<String>): WebRuntimeConfig {
            var bundleFile = File(envOrDefault("RYLWEEN_BUNDLE", DEFAULT_BUNDLE))
            var dataDir = File(envOrDefault("RYLWEEN_DATA_DIR", DEFAULT_DATA_DIR))
            var host = envOrDefault("RYLWEEN_HOST", DEFAULT_HOST)
            var port = envOrDefault("RYLWEEN_PORT", DEFAULT_PORT.toString()).toIntOrNull() ?: DEFAULT_PORT
            var stateSlug = envOrDefault("RYLWEEN_STATE", DEFAULT_STATE)
            var initialLoadFile = optionalFileEnv("RYLWEEN_LOAD_FILE")
            var saveFileOverride = optionalFileEnv("RYLWEEN_SAVE_FILE")
            var seed = optionalLongEnv("RYLWEEN_SEED")
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
            return WebRuntimeConfig(bundleFile, dataDir, host, port, stateSlug, initialLoadFile, saveFileOverride, seed)
        }

        private fun envOrDefault(
            key: String,
            defaultValue: String,
        ): String = System.getenv(key) ?: defaultValue

        private fun optionalFileEnv(key: String): File? {
            val value = System.getenv(key)?.takeIf(String::isNotBlank) ?: return null
            return File(value)
        }

        private fun optionalLongEnv(key: String): Long? {
            val value = System.getenv(key)?.takeIf(String::isNotBlank) ?: return null
            return value.toLong()
        }

        private fun requiredValue(
            args: Array<String>,
            index: Int,
            option: String,
        ): String = args.getOrNull(index) ?: error("$option requires a value")

        private fun printUsage() {
            println(
                """
                Usage: rylween-web [--bundle FILE] [--data DIR] [--host HOST] [--port PORT] [--state SLUG] [--load FILE] [--save FILE] [--seed N]

                Environment:
                  RYLWEEN_BUNDLE    Compiled bundle path. Default: $DEFAULT_BUNDLE
                  RYLWEEN_DATA_DIR  Save/log data directory. Default: $DEFAULT_DATA_DIR
                  RYLWEEN_HOST      Bind host. Default: $DEFAULT_HOST
                  RYLWEEN_PORT      Bind port. Default: $DEFAULT_PORT
                  RYLWEEN_STATE     Initial state slug. Default: $DEFAULT_STATE
                  RYLWEEN_LOAD_FILE Initial save loaded for users without a save.
                  RYLWEEN_SAVE_FILE Shared world save path. Default: data/saves/world.rsave
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
    private val activeUsers = ConcurrentHashMap.newKeySet<String>()
    private val worldSessionLock = ReentrantLock(true)
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    private val serverSocket = ServerSocket()
    private val clientIds = AtomicLong(0)

    fun serveForever() {
        serverSocket.reuseAddress = true
        serverSocket.bind(InetSocketAddress(config.host, config.port))
        println("Rylween web runtime listening on http://${config.host}:${config.port}/")
        serverLog.event(
            "web.server.start host=${config.host} port=${config.port} bundle=${config.bundleFile.path} " +
                "data=${config.dataDir.path} save=${config.worldSaveFile.path} serverLog=${config.serverLogFile.path}",
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
                    System.err.println("web client failed: ${failure.message}")
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
        if (request.method != "GET") {
            serverLog.event("web.http.response id=$clientId status=405 path=${request.path}")
            respondText(socket, "405 Method Not Allowed", "text/plain; charset=utf-8", "GET required\n")
            return
        }
        when (request.path) {
            "/", "/play" -> {
                serverLog.event("web.http.response id=$clientId status=200 path=${request.path}")
                respondText(socket, "200 OK", "text/html; charset=utf-8", terminalPageHtml())
            }
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

    private fun servePlaySocket(
        socket: Socket,
        input: InputStream,
        request: HttpRequest,
        clientId: Long,
        remote: String,
    ) {
        if (!request.isWebSocketUpgrade()) {
            serverLog.event("web.websocket.reject id=$clientId remote=${logValue(remote)} reason=missing_upgrade")
            respondText(socket, "400 Bad Request", "text/plain; charset=utf-8", "websocket required\n")
            return
        }
        val user = WebUser.from(request.query["user"])
        serverLog.event(
            "web.user.login id=$clientId remote=${logValue(remote)} " +
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
                    connection.readClientMessages { message -> terminal.acceptClientMessage(message) }
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
        request: HttpRequest,
        user: WebUser,
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
        user: WebUser,
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
        user: WebUser,
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
                    showTitleMenu = loadFile == null,
                )
            val result = factory.start(terminal, runtimeConfig, log)
            log.event("web.session.end user=${user.slug} reason=${result.reason}")
            serverLog.event("web.session.close user=${user.slug} reason=${result.reason} sessionLog=${logFile.path}")
        }
    }

    private fun logLoadDecision(
        user: WebUser,
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
}

private fun streamRuntimeLog(line: String) {
    println(line)
}

private fun logValue(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

private data class WebUser(
    val slug: String,
    val displayName: String,
) {
    companion object {
        fun from(value: String?): WebUser {
            val raw = value?.trim().orEmpty()
            val displayName = displayNameFrom(raw)
            val slug = slugFrom(displayName)
            return WebUser(slug, displayName)
        }

        private fun displayNameFrom(raw: String): String {
            if (raw.isBlank()) return "adventurer"
            return raw.take(40)
        }

        private fun slugFrom(displayName: String): String {
            val lowered = displayName.lowercase(Locale.ROOT)
            val cleaned = lowered.replace(Regex("[^a-z0-9_-]+"), "-").trim('-')
            if (cleaned.isNotBlank()) return cleaned.take(48)
            return "adventurer"
        }
    }
}

private data class HttpRequest(
    val method: String,
    val target: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
) {
    fun header(name: String): String? = headers[name.lowercase(Locale.ROOT)]

    fun isWebSocketUpgrade(): Boolean {
        val upgrade = header("upgrade") ?: return false
        val connection = header("connection") ?: return false
        if (!upgrade.equals("websocket", ignoreCase = true)) return false
        return connection.lowercase(Locale.ROOT).contains("upgrade")
    }
}

private fun readHttpRequest(input: InputStream): HttpRequest? {
    val requestLine = readHttpLine(input) ?: return null
    if (requestLine.isBlank()) return null
    val parts = requestLine.split(" ", limit = 3)
    if (parts.size < 2) return null
    val headers = linkedMapOf<String, String>()
    while (true) {
        val line = readHttpLine(input) ?: return null
        if (line.isEmpty()) break
        val separator = line.indexOf(':')
        if (separator < 1) continue
        val name = line.substring(0, separator).trim().lowercase(Locale.ROOT)
        val value = line.substring(separator + 1).trim()
        headers[name] = value
    }
    val target = parts[1]
    val parsed = parseTarget(target)
    return HttpRequest(parts[0], target, parsed.path, parsed.query, headers)
}

private fun readHttpLine(input: InputStream): String? {
    val out = ByteArrayOutputStream()
    while (out.size() < MAX_HTTP_LINE_BYTES) {
        val next = input.read()
        if (next < 0) {
            if (out.size() == 0) return null
            break
        }
        if (next == '\n'.code) break
        if (next != '\r'.code) out.write(next)
    }
    return out.toString(StandardCharsets.ISO_8859_1)
}

private data class ParsedTarget(
    val path: String,
    val query: Map<String, String>,
)

private fun parseTarget(target: String): ParsedTarget {
    val queryStart = target.indexOf('?')
    if (queryStart < 0) return ParsedTarget(target, emptyMap())
    val path = target.substring(0, queryStart)
    val query = parseQuery(target.substring(queryStart + 1))
    return ParsedTarget(path, query)
}

private fun parseQuery(raw: String): Map<String, String> {
    if (raw.isBlank()) return emptyMap()
    val values = linkedMapOf<String, String>()
    raw.split('&').forEach { part ->
        val separator = part.indexOf('=')
        if (separator < 0) {
            values[urlDecode(part)] = ""
            return@forEach
        }
        val key = urlDecode(part.substring(0, separator))
        val value = urlDecode(part.substring(separator + 1))
        values[key] = value
    }
    return values
}

private fun urlDecode(value: String): String = URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8)

private fun respondText(
    socket: Socket,
    status: String,
    contentType: String,
    body: String,
) {
    val bytes = body.toByteArray(StandardCharsets.UTF_8)
    val header =
        "HTTP/1.1 $status\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n" +
            "\r\n"
    val output = socket.getOutputStream()
    output.write(header.toByteArray(StandardCharsets.ISO_8859_1))
    output.write(bytes)
    output.flush()
}

private fun writeWebSocketHandshake(
    output: java.io.OutputStream,
    request: HttpRequest,
) {
    val key = request.header("sec-websocket-key") ?: error("missing websocket key")
    val accept = websocketAccept(key)
    val response =
        "HTTP/1.1 101 Switching Protocols\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Accept: $accept\r\n" +
            "\r\n"
    output.write(response.toByteArray(StandardCharsets.ISO_8859_1))
    output.flush()
}

private fun websocketAccept(key: String): String {
    val sha1 = MessageDigest.getInstance("SHA-1")
    val bytes = sha1.digest((key.trim() + WEBSOCKET_GUID).toByteArray(StandardCharsets.ISO_8859_1))
    return Base64.getEncoder().encodeToString(bytes)
}
