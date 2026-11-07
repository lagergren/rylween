/**
 * Same-origin checks for the requests a browser can be made to send from another site, such as a form POST or the
 * game's WebSocket, which the browser opens with the player's cookies wherever the page that opens it came from.
 */

package rylween.webcommon

import java.net.URI
import java.util.Locale

/**
 * Whether this request's `Origin` is this server. The browser names the scheme, host and port the page came from. That
 * must be [publicUrl], where players reach the server, perhaps through a proxy that rewrites `Host`. Or, for any other
 * name, such as a LAN address, it must be the `Host` the request was sent to. `Host` has no scheme, which behind a
 * proxy that terminates HTTPS the server cannot see, so for the public URL's own host only its scheme and port count.
 * A request without `Origin` comes from outside a browser and passes: browsers send `Origin` on every POST and
 * WebSocket, and only a browser holds a player's cookies.
 */
fun WebHttpRequest.isSameOrigin(publicUrl: String): Boolean {
    val origin = header("origin") ?: return true
    val from = webOrigin(origin) ?: return false
    val public = webOrigin(publicUrl)
    return from == public ||
        (from.host != public?.host && from == header("host")?.let { host -> webOrigin("${from.scheme}://$host") })
}

private data class WebOrigin(
    val scheme: String,
    val host: String,
    val port: Int,
)

private fun webOrigin(url: String): WebOrigin? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
    val host = uri.host?.lowercase(Locale.ROOT) ?: return null
    val port = uri.port.takeIf { explicit -> explicit >= 0 } ?: if (scheme == "https") 443 else 80
    return WebOrigin(scheme, host, port)
}
