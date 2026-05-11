package rylween.web

import java.io.Closeable
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

private const val OPCODE_TEXT = 0x1
private const val OPCODE_CLOSE = 0x8
private const val OPCODE_PING = 0x9
private const val OPCODE_PONG = 0xA
private const val PAYLOAD_16_BIT = 126
private const val PAYLOAD_64_BIT = 127
private const val MAX_FRAME_BYTES = 65_536

internal class WebSocketConnection(
    private val input: InputStream,
    private val output: OutputStream,
) : Closeable {
    private val closed = AtomicBoolean(false)
    private val writeLock = Any()

    fun readClientMessages(onText: (String) -> Unit) {
        try {
            while (!closed.get()) {
                val frame = readFrame()
                when (frame.opcode) {
                    OPCODE_TEXT -> onText(String(frame.payload, StandardCharsets.UTF_8))
                    OPCODE_CLOSE -> {
                        close()
                        return
                    }
                    OPCODE_PING -> sendFrame(OPCODE_PONG, frame.payload)
                }
            }
        } catch (_: EOFException) {
            close()
        } catch (_: java.net.SocketException) {
            close()
        }
    }

    fun sendText(text: String) {
        if (closed.get()) return
        sendFrame(OPCODE_TEXT, text.toByteArray(StandardCharsets.UTF_8))
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { sendFrame(OPCODE_CLOSE, ByteArray(0)) }
        runCatching { input.close() }
        runCatching { output.close() }
    }

    private fun readFrame(): WebSocketFrame {
        val b0 = readRequiredByte()
        val b1 = readRequiredByte()
        val opcode = b0 and 0x0F
        val masked = b1 and 0x80 != 0
        var length = b1 and 0x7F
        if (length == PAYLOAD_16_BIT) length = readUnsignedShort()
        if (length == PAYLOAD_64_BIT) length = readLongPayloadLength()
        check(length <= MAX_FRAME_BYTES) { "WebSocket frame too large: $length" }
        val mask = ByteArray(4)
        if (masked) readFully(mask)
        val payload = ByteArray(length)
        readFully(payload)
        if (masked) unmask(payload, mask)
        return WebSocketFrame(opcode, payload)
    }

    private fun sendFrame(
        opcode: Int,
        payload: ByteArray,
    ) {
        synchronized(writeLock) {
            if (closed.get() && opcode != OPCODE_CLOSE) return
            output.write(0x80 or opcode)
            writePayloadLength(payload.size)
            output.write(payload)
            output.flush()
        }
    }

    private fun writePayloadLength(size: Int) {
        if (size < PAYLOAD_16_BIT) {
            output.write(size)
            return
        }
        if (size <= UShort.MAX_VALUE.toInt()) {
            output.write(PAYLOAD_16_BIT)
            output.write((size ushr 8) and 0xFF)
            output.write(size and 0xFF)
            return
        }
        output.write(PAYLOAD_64_BIT)
        output.write(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(size.toLong()).array())
    }

    private fun readRequiredByte(): Int {
        val value = input.read()
        if (value < 0) throw EOFException()
        return value
    }

    private fun readUnsignedShort(): Int {
        val hi = readRequiredByte()
        val lo = readRequiredByte()
        return (hi shl 8) or lo
    }

    private fun readLongPayloadLength(): Int {
        val bytes = ByteArray(Long.SIZE_BYTES)
        readFully(bytes)
        val length = ByteBuffer.wrap(bytes).long
        check(length <= Int.MAX_VALUE) { "WebSocket frame too large: $length" }
        return length.toInt()
    }

    private fun readFully(bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            val read = input.read(bytes, offset, bytes.size - offset)
            if (read < 0) throw EOFException()
            offset += read
        }
    }

    private fun unmask(
        payload: ByteArray,
        mask: ByteArray,
    ) {
        payload.indices.forEach { index ->
            payload[index] = (payload[index].toInt() xor mask[index % 4].toInt()).toByte()
        }
    }
}

private data class WebSocketFrame(
    val opcode: Int,
    val payload: ByteArray,
)
