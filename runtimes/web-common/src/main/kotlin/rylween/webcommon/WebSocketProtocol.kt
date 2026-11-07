/** WebSocket upgrade detection and server handshake helpers. */

package rylween.webcommon

import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

// RFC 6455 WebSocket handshake magic string. SHA-1 here is protocol framing,
// not an application security boundary.
private const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

object WebSocketProtocol {
    fun isUpgrade(
        upgradeHeader: String?,
        connectionHeader: String?,
    ): Boolean {
        if (upgradeHeader == null || connectionHeader == null) return false
        if (!upgradeHeader.equals("websocket", ignoreCase = true)) return false
        return connectionHeader.lowercase().contains("upgrade")
    }

    fun writeHandshake(
        output: OutputStream,
        key: String,
    ) {
        val response =
            "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: ${accept(key)}\r\n" +
                "\r\n"
        output.write(response.toByteArray(StandardCharsets.ISO_8859_1))
        output.flush()
    }

    private fun accept(key: String): String {
        val sha1 = MessageDigest.getInstance("SHA-1")
        val bytes = sha1.digest((key.trim() + WEBSOCKET_GUID).toByteArray(StandardCharsets.ISO_8859_1))
        return Base64.getEncoder().encodeToString(bytes)
    }
}
