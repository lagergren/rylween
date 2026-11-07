package rylween.webcommon

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebOriginTest {
    private fun request(vararg headers: Pair<String, String>): WebHttpRequest =
        WebHttpRequest("POST", "/api/login", "/api/login", emptyMap(), headers.toMap(), ByteArray(0))

    private val local = "http://localhost:8081/"
    private val public = "https://rylween.example.net/"

    @Test
    fun aPageFromTheServerItselfPasses() {
        assertTrue(request("origin" to "http://localhost:8081", "host" to "localhost:8081").isSameOrigin(local))
        assertTrue(request("origin" to "http://192.168.1.20:8081", "host" to "192.168.1.20:8081").isSameOrigin(local))
        // Behind HTTPS on the default port, neither side names the port.
        assertTrue(request("origin" to "https://rylween.example.net", "host" to "rylween.example.net").isSameOrigin(public))
        assertTrue(request("origin" to "HTTPS://Rylween.Example.Net", "host" to "rylween.example.net:443").isSameOrigin(public))
    }

    @Test
    fun thePublicUrlPassesWhenAProxyRewritesHost() {
        assertTrue(request("origin" to "https://rylween.example.net", "host" to "127.0.0.1:8081").isSameOrigin(public))
    }

    @Test
    fun anotherSiteIsRefused() {
        assertFalse(request("origin" to "https://evil.example.test", "host" to "rylween.example.net").isSameOrigin(public))
        assertFalse(request("origin" to "http://rylween.example.net", "host" to "rylween.example.net").isSameOrigin(public))
        assertFalse(request("origin" to "http://localhost:9999", "host" to "localhost:8081").isSameOrigin(local))
        assertFalse(request("origin" to "null", "host" to "localhost:8081").isSameOrigin(local))
    }

    @Test
    fun aRequestFromOutsideABrowserPasses() {
        assertTrue(request("host" to "localhost:8081").isSameOrigin(local))
    }
}
