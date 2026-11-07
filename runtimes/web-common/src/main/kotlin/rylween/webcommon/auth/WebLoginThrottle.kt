/** Failed-login limits that slow down password guessing. */

package rylween.webcommon.auth

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** Past this many clients with a record, recording a failure also forgets the records whose window has passed. */
private const val PRUNE_THRESHOLD = 10_000

/**
 * Slows down password guessing. After [maxFailures] failed logins from one client within [window] of the first, that
 * client's further attempts are refused until the window has passed. A successful login clears the client's record.
 * Clients are told apart by address, so behind a proxy this needs the proxy trusted (see WebTrustedProxies).
 */
class WebLoginThrottle(
    private val maxFailures: Int = 10,
    private val window: Duration = Duration.ofMinutes(15),
    private val now: () -> Instant = Instant::now,
) {
    private data class Failures(
        val count: Int,
        val since: Instant,
    )

    // The only state. ConcurrentHashMap.compute keeps each client's update atomic across the request threads.
    private val failures = ConcurrentHashMap<String, Failures>()

    /** How long [client] must wait before its next attempt; zero when it may try now. */
    fun retryAfter(client: String): Duration =
        failures[client]
            ?.takeIf { record -> record.count >= maxFailures }
            ?.let { record -> Duration.between(now(), record.since.plus(window)) }
            ?.takeIf(Duration::isPositive)
            ?: Duration.ZERO

    fun recordFailure(client: String) {
        val at = now()
        failures.compute(client) { _, record ->
            if (record == null || record.expiredAt(at)) Failures(1, at) else record.copy(count = record.count + 1)
        }
        if (failures.size > PRUNE_THRESHOLD) failures.values.removeIf { record -> record.expiredAt(at) }
    }

    fun recordSuccess(client: String) {
        failures.remove(client)
    }

    private fun Failures.expiredAt(at: Instant): Boolean = !at.isBefore(since.plus(window))
}
