package rylween.runtime

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class RuntimeLogTest {
    @Test
    fun failureRecordsTheErrorAndItsStackTrace(
        @TempDir testTmp: File,
    ) {
        val file = testTmp.resolve("runtime.log")
        val cause = IllegalStateException("inner problem")
        RuntimeLog.open(file).use { log -> log.failure("session.failed user=ada", RuntimeException("outer problem", cause)) }

        val lines = file.readLines()
        assertTrue(lines.any { line -> line.endsWith("session.failed user=ada error=\"java.lang.RuntimeException: outer problem\"") })
        assertTrue(lines.any { line -> "at rylween.runtime.RuntimeLogTest.failureRecordsTheErrorAndItsStackTrace" in line })
        assertTrue(lines.any { line -> "Caused by: java.lang.IllegalStateException: inner problem" in line })
    }
}
