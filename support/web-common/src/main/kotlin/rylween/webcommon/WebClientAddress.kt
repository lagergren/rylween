/**
 * Client addresses behind a reverse proxy. A proxy that terminates HTTPS, such as Caddy, connects to the server itself
 * and says in `X-Forwarded-For` whom it forwards for. Only the proxies the server is told to trust are believed: any
 * client can send that header.
 */

package rylween.webcommon

import java.net.InetAddress

/** An address range in CIDR notation, such as `172.16.0.0/12` or `fc00::/7`. A bare address is a range of one. */
class WebAddressRange private constructor(
    private val network: ByteArray,
    private val prefixLength: Int,
) {
    fun contains(address: InetAddress): Boolean {
        val bytes = address.address
        if (bytes.size != network.size) return false
        val wholeBytes = prefixLength / 8
        val restBits = prefixLength % 8
        val mask = (0xFF shl (8 - restBits)) and 0xFF
        return (0 until wholeBytes).all { index -> bytes[index] == network[index] } &&
            (restBits == 0 || (bytes[wholeBytes].toInt() and mask) == (network[wholeBytes].toInt() and mask))
    }

    override fun toString(): String = "${InetAddress.getByAddress(network).hostAddress}/$prefixLength"

    companion object {
        /** Parses `address` or `address/prefix`, and fails for anything that is not a literal address. */
        fun parse(text: String): WebAddressRange {
            val addressText = text.substringBefore('/').trim()
            val prefixText = text.substringAfter('/', missingDelimiterValue = "").trim()
            val address = webAddressLiteral(addressText) ?: throw IllegalArgumentException("not an address range: \"$text\"")
            val bits = address.address.size * 8
            val prefix =
                if (prefixText.isEmpty()) {
                    bits
                } else {
                    prefixText.toIntOrNull()?.takeIf { length -> length in 0..bits }
                        ?: throw IllegalArgumentException("not an address range: \"$text\"")
                }
            return WebAddressRange(address.address, prefix)
        }
    }
}

/** The proxies whose `X-Forwarded-For` the server believes. */
class WebTrustedProxies(
    private val ranges: List<WebAddressRange>,
) {
    fun trusts(address: InetAddress): Boolean = ranges.any { range -> range.contains(address) }

    /**
     * The client a request came from. That is [peer], the address that connected, unless [peer] is a trusted proxy.
     * Then it is the nearest address in [forwardedFor] that is not one, walking back from the end, where each proxy
     * appends whom it forwards for. An entry that is no address ends the walk, since no trusted proxy wrote it.
     */
    fun clientAddress(
        peer: InetAddress,
        forwardedFor: String?,
    ): InetAddress {
        if (forwardedFor == null || !trusts(peer)) return peer
        val hops =
            forwardedFor
                .split(',')
                .asReversed()
                .asSequence()
                .map(::webForwardedAddress)
                .takeWhile { hop -> hop != null }
                .filterNotNull()
                .toList()
        return hops.firstOrNull { hop -> !trusts(hop) } ?: hops.lastOrNull() ?: peer
    }

    override fun toString(): String = ranges.joinToString(",")

    companion object {
        val None = WebTrustedProxies(emptyList())

        /** Parses a comma- or space-separated list of addresses and CIDR ranges. Blank trusts no one. */
        fun parse(text: String): WebTrustedProxies =
            WebTrustedProxies(
                text
                    .split(',', ' ')
                    .filter(String::isNotBlank)
                    .map(WebAddressRange::parse),
            )
    }
}

/** A literal IPv4 or IPv6 address, optionally in brackets, never looked up in DNS; null for anything else. */
private fun webAddressLiteral(text: String): InetAddress? =
    runCatching { InetAddress.ofLiteral(text.removePrefix("[").removeSuffix("]")) }.getOrNull()

/** One `X-Forwarded-For` entry: an address, maybe in brackets and maybe with a port. */
private fun webForwardedAddress(entry: String): InetAddress? {
    val text = entry.trim()
    val address =
        when {
            text.startsWith("[") -> text.substringAfter('[').substringBefore(']')
            text.count { char -> char == ':' } == 1 -> text.substringBefore(':')
            else -> text
        }
    return webAddressLiteral(address)
}
