package rylween.runtime

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SaveFileStorageTest {
    @Test
    fun separateJvmProcessesPreserveEveryUpdate(
        @TempDir directory: File,
    ) {
        val processes = mutableListOf<Process>()
        try {
            val save = directory.resolve("world.rsave").also { it.writeText("0") }
            val classpath =
                listOf(SaveFileProcessWriter::class.java, SaveFileStorage::class.java, Unit::class.java)
                    .map {
                        File(
                            it.protectionDomain.codeSource.location
                                .toURI(),
                        ).path
                    }.distinct()
                    .joinToString(File.pathSeparator)
            repeat(2) {
                processes +=
                    ProcessBuilder(
                        File(System.getProperty("java.home"), "bin/java").path,
                        "-Xmx64m",
                        "-cp",
                        classpath,
                        SaveFileProcessWriter::class.java.name,
                        save.path,
                    ).redirectErrorStream(true).start()
            }
            val output = processes.map { it.inputStream.bufferedReader() }
            output.forEach { assertEquals("ready", it.readLine()) }
            processes.forEach { process -> process.outputStream.use { it.write(1) } }
            processes.forEachIndexed { index, process ->
                assertTrue(process.waitFor(30, TimeUnit.SECONDS), "save worker should complete")
                assertEquals(0, process.exitValue(), output[index].readText())
            }
            assertEquals("100", save.readText())
        } finally {
            processes.forEach { process ->
                if (process.isAlive) process.destroyForcibly()
                process.waitFor(10, TimeUnit.SECONDS)
            }
        }
    }

    @Test
    fun replacingSavePreservesPermissionsAndRemovesTemporaryFiles(
        @TempDir directory: Path,
    ) {
        val file = directory.resolve("world.rsave")
        Files.writeString(file, "old")
        val permissions = PosixFilePermissions.fromString("rw-r-----")
        Files.setPosixFilePermissions(file, permissions)

        SaveFileStorage.update(file.toFile()) { SaveFileStorage.replace(it, "new") }

        assertEquals("new", Files.readString(file))
        assertEquals(permissions, Files.getPosixFilePermissions(file))
        assertEquals(setOf("world.rsave", "world.rsave.lock"), directory.toFile().list()?.toSet())
    }
}

/** Runs outside the test JVM so the operating-system save lock is exercised. */
internal object SaveFileProcessWriter {
    @JvmStatic
    fun main(args: Array<String>) {
        val file = File(args.single())
        println("ready")
        check(System.`in`.read() >= 0)
        repeat(50) {
            SaveFileStorage.update(file) { target ->
                val next = target.readText().toInt() + 1
                Thread.yield()
                SaveFileStorage.replace(target, next.toString())
            }
        }
    }
}
