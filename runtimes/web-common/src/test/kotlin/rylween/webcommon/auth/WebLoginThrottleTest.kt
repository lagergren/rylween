package rylween.webcommon.auth

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class WebLoginThrottleTest {
    private var clock = Instant.parse("2026-11-07T12:00:00Z")
    private val throttle = WebLoginThrottle(maxFailures = 3, window = Duration.ofMinutes(15), now = { clock })

    private fun advance(minutes: Long) {
        clock = clock.plus(Duration.ofMinutes(minutes))
    }

    @Test
    fun aClientMayFailUpToTheLimitAndThenWaitsOutTheWindow() {
        repeat(2) { throttle.recordFailure("198.51.100.1") }
        assertEquals(Duration.ZERO, throttle.retryAfter("198.51.100.1"))

        advance(5)
        throttle.recordFailure("198.51.100.1")
        assertEquals(Duration.ofMinutes(10), throttle.retryAfter("198.51.100.1"))

        advance(10)
        assertEquals(Duration.ZERO, throttle.retryAfter("198.51.100.1"))
    }

    @Test
    fun aFailureAfterTheWindowStartsAFreshCount() {
        repeat(3) { throttle.recordFailure("198.51.100.1") }
        advance(15)
        throttle.recordFailure("198.51.100.1")

        assertEquals(Duration.ZERO, throttle.retryAfter("198.51.100.1"))
    }

    @Test
    fun aSuccessClearsTheRecord() {
        repeat(2) { throttle.recordFailure("198.51.100.1") }
        throttle.recordSuccess("198.51.100.1")
        repeat(2) { throttle.recordFailure("198.51.100.1") }

        assertEquals(Duration.ZERO, throttle.retryAfter("198.51.100.1"))
    }

    @Test
    fun clientsAreCountedApart() {
        repeat(3) { throttle.recordFailure("198.51.100.1") }

        assertEquals(Duration.ofMinutes(15), throttle.retryAfter("198.51.100.1"))
        assertEquals(Duration.ZERO, throttle.retryAfter("203.0.113.7"))
    }
}
