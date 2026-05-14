package rylween.webcommon

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

private const val DEFAULT_MAX_HTTP_LINE_BYTES = 16_384
private const val DEFAULT_MAX_HTTP_BODY_BYTES = 65_536

data class WebHttpHeader(
    val name: String,
    val value: String,
)

data class WebHttpRequest(
    val method: String,
    val target: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
    val body: ByteArray,
) {
    fun header(name: String): String? = headers[name.lowercase(Locale.ROOT)]

    fun cookie(name: String): String? = header("cookie")?.parseCookieHeader()?.get(name)

    fun formFields(): Map<String, String> = parseFormUrlEncoded(body.toString(StandardCharsets.UTF_8))

    fun isWebSocketUpgrade(): Boolean = WebSocketProtocol.isUpgrade(header("upgrade"), header("connection"))
}

fun readHttpRequest(
    input: InputStream,
    maxLineBytes: Int = DEFAULT_MAX_HTTP_LINE_BYTES,
    maxBodyBytes: Int = DEFAULT_MAX_HTTP_BODY_BYTES,
): WebHttpRequest? {
    val requestLine = readHttpLine(input, maxLineBytes) ?: return null
    if (requestLine.isBlank()) return null
    val parts = requestLine.split(" ", limit = 3)
    if (parts.size < 2) return null
    val headers = linkedMapOf<String, String>()
    while (true) {
        val line = readHttpLine(input, maxLineBytes) ?: return null
        if (line.isEmpty()) break
        val separator = line.indexOf(':')
        if (separator < 1) continue
        val name = line.substring(0, separator).trim().lowercase(Locale.ROOT)
        val value = line.substring(separator + 1).trim()
        headers[name] = value
    }
    val contentLength = headers["content-length"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    check(contentLength <= maxBodyBytes) { "HTTP request body too large: $contentLength" }
    val body = ByteArray(contentLength)
    var offset = 0
    while (offset < body.size) {
        val read = input.read(body, offset, body.size - offset)
        if (read < 0) break
        offset += read
    }
    val target = parts[1]
    val parsed = parseTarget(target)
    return WebHttpRequest(parts[0], target, parsed.path, parsed.query, headers, body)
}

fun respondText(
    socket: Socket,
    status: String,
    contentType: String,
    body: String,
    headers: List<WebHttpHeader> = emptyList(),
    cacheControl: String? = "no-store",
) {
    respondBytes(socket, status, contentType, body.toByteArray(StandardCharsets.UTF_8), headers, cacheControl)
}

fun respondBytes(
    socket: Socket,
    contentType: String,
    bytes: ByteArray,
    headers: List<WebHttpHeader> = emptyList(),
    cacheControl: String? = "no-store",
) {
    respondBytes(socket, "200 OK", contentType, bytes, headers, cacheControl)
}

fun redirect(
    socket: Socket,
    location: String,
    headers: List<WebHttpHeader> = emptyList(),
) {
    respondText(
        socket = socket,
        status = "303 See Other",
        contentType = "text/plain; charset=utf-8",
        body = "See $location\n",
        headers = headers + WebHttpHeader("Location", location),
    )
}

fun parseFormUrlEncoded(raw: String): Map<String, String> = parseKeyValue(raw, ::formDecode)

fun urlDecode(value: String): String = URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8)

private fun formDecode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8)

private data class ParsedTarget(
    val path: String,
    val query: Map<String, String>,
)

private fun readHttpLine(
    input: InputStream,
    maxLineBytes: Int,
): String? {
    val out = ByteArrayOutputStream()
    while (out.size() < maxLineBytes) {
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

private fun parseTarget(target: String): ParsedTarget {
    val queryStart = target.indexOf('?')
    if (queryStart < 0) return ParsedTarget(target, emptyMap())
    val path = target.substring(0, queryStart)
    val query = parseQuery(target.substring(queryStart + 1))
    return ParsedTarget(path, query)
}

private fun parseQuery(raw: String): Map<String, String> = parseKeyValue(raw, ::urlDecode)

private fun parseKeyValue(
    raw: String,
    decode: (String) -> String,
): Map<String, String> {
    if (raw.isBlank()) return emptyMap()
    val values = linkedMapOf<String, String>()
    raw.split('&').forEach { part ->
        val separator = part.indexOf('=')
        if (separator < 0) {
            values[decode(part)] = ""
            return@forEach
        }
        values[decode(part.substring(0, separator))] = decode(part.substring(separator + 1))
    }
    return values
}

private fun String.parseCookieHeader(): Map<String, String> =
    split(';')
        .mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator < 1) return@mapNotNull null
            part.substring(0, separator).trim() to part.substring(separator + 1).trim()
        }.toMap()

private fun respondBytes(
    socket: Socket,
    status: String,
    contentType: String,
    bytes: ByteArray,
    headers: List<WebHttpHeader>,
    cacheControl: String?,
) {
    val header =
        buildString {
            append("HTTP/1.1 ")
            append(status)
            append("\r\n")
            append("Content-Type: ")
            append(contentType)
            append("\r\n")
            append("Content-Length: ")
            append(bytes.size)
            append("\r\n")
            cacheControl?.let { value ->
                append("Cache-Control: ")
                append(value)
                append("\r\n")
            }
            headers.forEach { extra ->
                append(extra.name)
                append(": ")
                append(extra.value)
                append("\r\n")
            }
            append("Connection: close\r\n")
            append("\r\n")
        }
    val output = socket.getOutputStream()
    output.write(header.toByteArray(StandardCharsets.ISO_8859_1))
    output.write(bytes)
    output.flush()
}
