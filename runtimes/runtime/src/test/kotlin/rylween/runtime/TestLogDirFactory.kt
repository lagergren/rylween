/**
 * Default `@TempDir` factory for the runtime tests (set in junit-platform.properties).
 */

package rylween.runtime

import org.junit.jupiter.api.extension.AnnotatedElementContext
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.io.TempDirFactory
import java.lang.reflect.Method
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Puts each `@TempDir` in `build/test-logs/<test class>/<test method>` instead of the system temp
 * directory. junit-platform.properties keeps the directories after the run, so the logs, saves, and
 * reports a test writes can be read afterwards; the Gradle test task empties `build/test-logs` before
 * each run.
 */
class TestLogDirFactory : TempDirFactory {
    override fun createTempDirectory(
        elementContext: AnnotatedElementContext,
        extensionContext: ExtensionContext,
    ): Path {
        val testClass = extensionContext.requiredTestClass.simpleName
        val testName = extensionContext.testMethod.map(Method::getName).orElse("shared")
        val parent = Files.createDirectories(Path.of(requiredRuntimeTestProperty("rylween.runtime.testLogs"), testClass))
        return parent.createUniqueDirectory(testName)
    }

    /** A test with several `@TempDir`s, or one that runs more than once, gets numbered siblings. */
    private fun Path.createUniqueDirectory(name: String): Path {
        var index = 1
        while (true) {
            try {
                return Files.createDirectory(resolve(if (index == 1) name else "$name-$index"))
            } catch (_: FileAlreadyExistsException) {
                index++
            }
        }
    }
}
