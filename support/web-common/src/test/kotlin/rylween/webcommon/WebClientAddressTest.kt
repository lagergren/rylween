package rylween.webcommon

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebClientAddressTest {
    private fun address(text: String): InetAddress = InetAddress.ofLiteral(text)

    private val dockerNetworks = WebTrustedProxies.parse("10.0.0.0/8, 172.16.0.0/12 192.168.0.0/16,fc00::/7")

    @Test
    fun rangesMatchTheirPrefix() {
        val range = WebAddressRange.parse("172.16.0.0/12")

        assertTrue(range.contains(address("172.16.0.1")))
        assertTrue(range.contains(address("172.31.255.254")))
        assertFalse(range.contains(address("172.32.0.1")))
        assertFalse(range.contains(address("fc00::1")))
        assertTrue(WebAddressRange.parse("fc00::/7").contains(address("fd12:3456::1")))
        assertTrue(WebAddressRange.parse("203.0.113.7").contains(address("203.0.113.7")))
        assertFalse(WebAddressRange.parse("203.0.113.7").contains(address("203.0.113.8")))
        assertTrue(WebAddressRange.parse("0.0.0.0/0").contains(address("198.51.100.1")))
    }

    @Test
    fun rangesRejectAnythingButLiteralAddresses() {
        assertFailsWith<IllegalArgumentException> { WebAddressRange.parse("proxy.example.test") }
        assertFailsWith<IllegalArgumentException> { WebAddressRange.parse("10.0.0.0/33") }
        assertFailsWith<IllegalArgumentException> { WebAddressRange.parse("10.0.0.0/x") }
        assertEquals("10.0.0.0/8", WebAddressRange.parse(" 10.0.0.0/8 ").toString())
    }

    @Test
    fun anUntrustedPeerIsTheClientWhateverItForwards() {
        assertEquals(address("203.0.113.7"), dockerNetworks.clientAddress(address("203.0.113.7"), "198.51.100.1"))
        assertEquals(address("172.18.0.3"), WebTrustedProxies.None.clientAddress(address("172.18.0.3"), "198.51.100.1"))
    }

    @Test
    fun aTrustedProxyNamesTheNearestUntrustedHop() {
        val caddy = address("172.18.0.3")

        assertEquals(address("198.51.100.1"), dockerNetworks.clientAddress(caddy, "198.51.100.1"))
        // A client can put anything first; the proxy appends the address it really saw.
        assertEquals(address("198.51.100.1"), dockerNetworks.clientAddress(caddy, "203.0.113.99, 198.51.100.1"))
        assertEquals(address("198.51.100.1"), dockerNetworks.clientAddress(caddy, "198.51.100.1, 10.1.2.3"))
        assertEquals(address("2001:db8::5"), dockerNetworks.clientAddress(caddy, "[2001:db8::5]:443"))
        assertEquals(address("198.51.100.1"), dockerNetworks.clientAddress(caddy, "198.51.100.1:5050"))
        assertEquals(caddy, dockerNetworks.clientAddress(caddy, null))
    }

    @Test
    fun aHopThatIsNoAddressEndsTheWalk() {
        val caddy = address("172.18.0.3")

        // Never looked up in DNS: a name ends the walk at the proxy that forwarded it.
        assertEquals(caddy, dockerNetworks.clientAddress(caddy, "localhost"))
        assertEquals(address("10.1.2.3"), dockerNetworks.clientAddress(caddy, "198.51.100.1, unknown, 10.1.2.3"))
        // When every hop is trusted, the farthest one is the client.
        assertEquals(address("10.9.9.9"), dockerNetworks.clientAddress(caddy, "10.9.9.9, 10.1.2.3"))
    }
}
