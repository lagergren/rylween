package rylween.runtime

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

class RuntimeLogTest {
    @Test
    fun failureRecordsTheErrorAndItsStackTrace() {
        val file = Files.createTempFile("runtime-log", ".log").toFile()
        try {
            val cause = IllegalStateException("inner problem")
            RuntimeLog.open(file).use { log -> log.failure("session.failed user=ada", RuntimeException("outer problem", cause)) }

            val lines = file.readLines()
            assertTrue(lines.any { line -> line.endsWith("session.failed user=ada error=\"java.lang.RuntimeException: outer problem\"") })
            assertTrue(lines.any { line -> "at rylween.runtime.RuntimeLogTest.failureRecordsTheErrorAndItsStackTrace" in line })
            assertTrue(lines.any { line -> "Caused by: java.lang.IllegalStateException: inner problem" in line })
        } finally {
            file.delete()
        }
    }
}
