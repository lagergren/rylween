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
import rylween.webcommon.WebSocketConnection
import rylween.webcommon.WebSocketProtocol
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.exitProcess

private const val DEFAULT_HOST = "127.0.0.1"
private const val DEFAULT_PORT = 8081
private const val DEFAULT_STATE = "fresh-replay"
private const val MAX_HTTP_LINE_BYTES = 16_384
private val BITMAP_ASSETS =
    mapOf(
        "title-rider.png" to "bitmap-assets/title-rider.png",
        "screen-frame.png" to "bitmap-assets/screen-frame.png",
        "town-mainstreet.png" to "bitmap-assets/town-mainstreet.png",
        "encounter-scorpion.png" to "bitmap-assets/encounter-scorpion.png",
        "encounter-bestiary.png" to "bitmap-assets/encounter-bestiary.png",
        "scene-atlas.png" to "bitmap-assets/scene-atlas.png",
        "terrain-atlas.png" to "bitmap-assets/terrain-atlas.png",
        "scene-thord-mainstreet.png" to "bitmap-assets/scene-thord-mainstreet.png",
        "scene-merchant.png" to "bitmap-assets/scene-merchant.png",
        "scene-smith.png" to "bitmap-assets/scene-smith.png",
        "scene-inn.png" to "bitmap-assets/scene-inn.png",
        "scene-tavern.png" to "bitmap-assets/scene-tavern.png",
        "scene-church.png" to "bitmap-assets/scene-church.png",
        "scene-guild-closed.png" to "bitmap-assets/scene-guild-closed.png",
        "monster-goblinoid.png" to "bitmap-assets/monster-goblinoid.png",
        "monster-undead.png" to "bitmap-assets/monster-undead.png",
        "monster-beast.png" to "bitmap-assets/monster-beast.png",
        "monster-flying.png" to "bitmap-assets/monster-flying.png",
        "monster-vermin.png" to "bitmap-assets/monster-vermin.png",
        "monster-brute.png" to "bitmap-assets/monster-brute.png",
        "monster-humanoid-magic.png" to "bitmap-assets/monster-humanoid-magic.png",
        "monster-boss.png" to "bitmap-assets/monster-boss.png",
    )

fun main(args: Array<String>) {
    runCatching {
        val config = BitmapWebConfig.parse(args)
        val game = BitmapGameBundle.load(config.bundleFile)
        BitmapWebServer(config, game).use { server -> server.serveForever() }
    }.getOrElse { error ->
        System.err.println("bitmap web runtime failed: ${error.message ?: error::class.simpleName}")
        exitProcess(1)
    }
}

private data class BitmapWebConfig(
    val host: String,
    val port: Int,
    val stateSlug: String,
    val bundleFile: File?,
    val mapTiles: Int,
    val tileSize: Int,
) {
    companion object {
        fun parse(args: Array<String>): BitmapWebConfig {
            var host = envOrDefault("RYLWEEN_BITMAP_HOST", envOrDefault("RYLWEEN_HOST", DEFAULT_HOST))
            var port = envOrDefault("RYLWEEN_BITMAP_PORT", envOrDefault("RYLWEEN_PORT", DEFAULT_PORT.toString())).toInt()
            var stateSlug = envOrDefault("RYLWEEN_BITMAP_STATE", envOrDefault("RYLWEEN_STATE", DEFAULT_STATE))
            var bundleFile = optionalFileEnv("RYLWEEN_BITMAP_BUNDLE")
            var mapTiles = envOrDefault("RYLWEEN_BITMAP_MAP_TILES", DEFAULT_BITMAP_VIEWPORT_TILES.toString()).toInt()
            var tileSize = envOrDefault("RYLWEEN_BITMAP_TILE_SIZE", DEFAULT_BITMAP_TILE_SIZE.toString()).toInt()
            var index = 0
            while (index < args.size) {
                val arg = args[index]
                when (arg) {
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
                    "--bundle" -> {
                        index += 1
                        bundleFile = File(requiredValue(args, index, arg))
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
            return BitmapWebConfig(host, port, stateSlug, bundleFile, mapTiles, tileSize)
        }

        private fun envOrDefault(
            key: String,
            defaultValue: String,
        ): String = System.getenv(key) ?: defaultValue

        private fun optionalFileEnv(key: String): File? {
            val value = System.getenv(key)?.takeIf(String::isNotBlank) ?: return null
            return File(value)
        }

        private fun requiredValue(
            args: Array<String>,
            index: Int,
            option: String,
        ): String = args.getOrNull(index) ?: error("$option requires a value")

        private fun printUsage() {
            println(
                """
                Usage: rylween-bitmap-web [--host HOST] [--port PORT] [--state SLUG] [--bundle FILE] [--map-tiles N] [--tile-size PX]

                The bitmap runtime uses the embedded :game bundle by default. --bundle can point
                at build/compiled/rylween-bundle.json to exercise an emitted artifact instead.
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
) : Closeable {
    private val runtimeFactory = RuntimeSessionFactory.load(game.bundle, game.systems, game.contentHash)
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    private val serverSocket = ServerSocket()
    private val clientIds = AtomicLong(0)

    fun serveForever() {
        serverSocket.reuseAddress = true
        serverSocket.bind(InetSocketAddress(config.host, config.port))
        log(
            "bitmap.server.start host=${config.host} port=${config.port} " +
                "state=${config.stateSlug} mapTiles=${config.mapTiles} tileSize=${config.tileSize} bundle=${game.source}",
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
        socket.use { client ->
            runCatching { route(client, clientId) }
                .onFailure { failure ->
                    log("bitmap.client.error id=$clientId error=${failure.message ?: failure::class.simpleName}")
                    runCatching { respondText(client, "500 Internal Server Error", "text/plain; charset=utf-8", "server error\n") }
                }
        }
    }

    private fun route(
        socket: Socket,
        clientId: Long,
    ) {
        val input = socket.getInputStream()
        val request = readHttpRequest(input) ?: return
        if (request.method != "GET") {
            respondText(socket, "405 Method Not Allowed", "text/plain; charset=utf-8", "GET required\n")
            return
        }
        when (request.path) {
            "/", "/play" -> respondText(socket, "200 OK", "text/html; charset=utf-8", bitmapPageHtml())
            "/healthz" -> respondText(socket, "200 OK", "text/plain; charset=utf-8", "ok\n")
            "/ws/play" -> servePlaySocket(socket, input, request, clientId)
            else ->
                if (request.path.startsWith("/assets/")) {
                    serveAsset(socket, request.path.removePrefix("/assets/"))
                } else {
                    respondText(socket, "404 Not Found", "text/plain; charset=utf-8", "not found\n")
                }
        }
    }

    private fun serveAsset(
        socket: Socket,
        assetName: String,
    ) {
        val resourcePath = BITMAP_ASSETS[assetName]
        if (resourcePath == null) {
            respondText(socket, "404 Not Found", "text/plain; charset=utf-8", "asset not found\n")
            return
        }
        val bytes =
            javaClass.classLoader.getResourceAsStream(resourcePath)?.use(InputStream::readBytes)
                ?: run {
                    respondText(socket, "404 Not Found", "text/plain; charset=utf-8", "asset not found\n")
                    return
                }
        respondBytes(socket, "200 OK", "image/png", bytes)
    }

    private fun servePlaySocket(
        socket: Socket,
        input: InputStream,
        request: HttpRequest,
        clientId: Long,
    ) {
        if (!request.isWebSocketUpgrade()) {
            respondText(socket, "400 Bad Request", "text/plain; charset=utf-8", "websocket required\n")
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
        val logFile = File("build/logs/runtime-bitmap-web/client-$clientId.log")
        val saveFile = File("build/saves/runtime-bitmap-web/client-$clientId.rsave")
        log("bitmap.websocket.open id=$clientId")
        val runtime =
            executor.submit<Any?> {
                RuntimeLog.open(logFile).use { runtimeLog ->
                    runtimeFactory.start(
                        input = runtimeInput,
                        screen = screen,
                        config =
                            RuntimeSessionConfig(
                                userId = "bitmap-$clientId",
                                stateSlug = config.stateSlug,
                                saveFile = saveFile,
                                showTitleMenu = true,
                            ),
                        log = runtimeLog,
                    )
                }
                connection.close()
                null
            }
        try {
            connection.readClientMessages(runtimeInput::acceptClientMessage)
        } finally {
            runtimeInput.close()
            runCatching { runtime.get(5, TimeUnit.SECONDS) }
        }
        log("bitmap.websocket.close id=$clientId")
    }
}

private fun log(line: String) {
    println("[rylween-bitmap] $line")
}

private data class HttpRequest(
    val method: String,
    val target: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
) {
    fun header(name: String): String? = headers[name.lowercase(Locale.ROOT)]

    fun isWebSocketUpgrade(): Boolean = WebSocketProtocol.isUpgrade(header("upgrade"), header("connection"))
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
        values[urlDecode(part.substring(0, separator))] = urlDecode(part.substring(separator + 1))
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
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n" +
            "\r\n"
    val output = socket.getOutputStream()
    output.write(header.toByteArray(StandardCharsets.ISO_8859_1))
    output.write(bytes)
    output.flush()
}

private fun respondBytes(
    socket: Socket,
    status: String,
    contentType: String,
    bytes: ByteArray,
) {
    val header =
        "HTTP/1.1 $status\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n" +
            "Cache-Control: no-store\r\n" +
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
    WebSocketProtocol.writeHandshake(output, key)
}
