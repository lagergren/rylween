package rylween.runtime

import rylween.compiled.readExternalCompiledBundleContentHash
import rylween.compiled.readExternalCompiledGameBundle
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SaveFileConcurrencyTest {
    private val bundleFile = File(checkNotNull(System.getProperty("rylween.runtime.bundle")))
    private val bundle = bundleFile.readExternalCompiledGameBundle()
    private val content = bundle.content
    private val bundleHash = bundleFile.readExternalCompiledBundleContentHash()
    private val state = bundle.states.single { content.slug(it.state) == "fresh-replay" }
    private val initialRuntime = RuntimeState.from(state, content)

    @Test
    fun concurrentSavesPreserveEveryUsersLatestParty() =
        withSaveFile { file ->
            save(file, "existing", 99)
            val workers = 8
            val rounds = 10
            val barrier = CyclicBarrier(workers)
            val executor = Executors.newFixedThreadPool(workers)
            try {
                val futures =
                    (1..workers).map { worker ->
                        executor.submit {
                            val path = if (worker % 2 == 0) File(file.parentFile, "./${file.name}") else file
                            repeat(rounds) { round ->
                                barrier.await(20, TimeUnit.SECONDS)
                                save(path, "user-$worker", round)
                            }
                        }
                    }
                futures.forEach { it.get(60, TimeUnit.SECONDS) }
            } finally {
                executor.shutdownNow()
                executor.awaitTermination(10, TimeUnit.SECONDS)
            }

            val values = SaveCodec.decodeValues(file.readLines())
            assertEquals((workers + 1).toString(), values["users.count"])
            assertEquals(99, read(file, "existing").party.resources.gold)
            (1..workers).forEach { worker ->
                val party = read(file, "user-$worker").party
                assertEquals("user-$worker", party.name)
                assertEquals(rounds - 1, party.resources.gold)
            }
        }

    @Test
    fun deletingOneUserDuringAnotherSaveDoesNotRestoreTheDeletedParty() =
        withSaveFile { file ->
            save(file, "survivor", 0)
            val executor = Executors.newFixedThreadPool(2)
            try {
                repeat(10) { round ->
                    save(file, "deleted", round)
                    val barrier = CyclicBarrier(2)
                    val deletion =
                        executor.submit {
                            barrier.await(20, TimeUnit.SECONDS)
                            assertTrue(SaveFile.removeUser(file, "deleted"))
                        }
                    val writer =
                        executor.submit {
                            barrier.await(20, TimeUnit.SECONDS)
                            save(file, "survivor", round)
                        }
                    deletion.get(30, TimeUnit.SECONDS)
                    writer.get(30, TimeUnit.SECONDS)
                    assertFalse(SaveFile.hasUserParty(file, "deleted"))
                    assertEquals(round, read(file, "survivor").party.resources.gold)
                }
            } finally {
                executor.shutdownNow()
                executor.awaitTermination(10, TimeUnit.SECONDS)
            }
        }

    @Test
    fun malformedExistingSaveIsPreservedAndTheLockIsReleased() =
        withSaveFile { file ->
            val malformed = "this is not a save record\n"
            file.writeText(malformed)
            assertFailsWith<IllegalArgumentException> { save(file, "new-user", 1) }
            assertEquals(malformed, file.readText())

            file.delete()
            save(file, "recovered", 2)
            assertEquals(2, read(file, "recovered").party.resources.gold)
        }

    @Test
    fun incompatibleExistingSaveIsNotOverwritten() =
        withSaveFile { file ->
            save(file, "existing", 99)
            val incompatible = SaveCodec.decodeValues(file.readLines()) + ("version" to "999")
            file.writeText(SaveCodec.encodeValues(incompatible))
            val before = file.readText()

            assertFailsWith<IllegalArgumentException> { save(file, "new-user", 1) }
            assertEquals(before, file.readText())
        }

    @Test
    fun savingThroughASymlinkUpdatesTheTargetAndPreservesTheLink() =
        withSaveFile { file ->
            save(file, "existing", 99)
            val link = file.toPath().resolveSibling("linked.rsave")
            Files.createSymbolicLink(link, file.toPath())

            save(link.toFile(), "new-user", 1)

            assertTrue(Files.isSymbolicLink(link))
            assertEquals(99, read(file, "existing").party.resources.gold)
            assertEquals(1, read(file, "new-user").party.resources.gold)
        }

    @Test
    fun readersSeeCompleteSavesWhileAnotherUserWrites() =
        withSaveFile { file ->
            save(file, "existing", 99)
            val barrier = CyclicBarrier(2)
            val executor = Executors.newFixedThreadPool(2)
            try {
                val writer =
                    executor.submit {
                        repeat(30) { round ->
                            barrier.await(20, TimeUnit.SECONDS)
                            save(file, "writer", round)
                        }
                    }
                val reader =
                    executor.submit {
                        repeat(30) {
                            barrier.await(20, TimeUnit.SECONDS)
                            assertEquals("existing", read(file, "existing").party.name)
                            assertEquals(99, read(file, "existing").party.resources.gold)
                            assertEquals("rylween-save", SaveCodec.decodeValues(SaveFile.lines(file))["format"])
                        }
                    }
                reader.get(60, TimeUnit.SECONDS)
                writer.get(60, TimeUnit.SECONDS)
            } finally {
                executor.shutdownNow()
                executor.awaitTermination(10, TimeUnit.SECONDS)
            }
        }

    @Test
    fun savesWrittenByAnOlderBundleStillLoadAndAreRewrittenWithTheCurrentHash() =
        withSaveFile { file ->
            save(file, "veteran", 1234)
            file.writeText(file.readText().replace("bundleHash=$bundleHash", "bundleHash=sha256:older-bundle"))

            assertTrue(SaveFile.hasUserParty(file, "veteran"))
            assertEquals(1234, read(file, "veteran").party.resources.gold)
            assertEquals("sha256:older-bundle", SaveFile.savedBundleHash(file))

            save(file, "newcomer", 5)
            assertEquals(bundleHash, SaveFile.savedBundleHash(file))
            assertEquals(1234, read(file, "veteran").party.resources.gold)
        }

    private fun save(
        file: File,
        user: String,
        gold: Int,
    ) {
        val runtime =
            initialRuntime.withParty(
                initialRuntime.party.copy(
                    name = user,
                    resources = initialRuntime.party.resources.copy(gold = gold),
                    journal = List(100) { "Journal entry $it for $user" },
                ),
            )
        SaveFile.write(file, runtime, bundleHash, content, user, null)
    }

    private fun read(
        file: File,
        user: String,
    ): RuntimeState = SaveFile.read(file, content, { state }, user)

    private fun withSaveFile(action: (File) -> Unit) {
        val directory = Files.createTempDirectory("rylween-concurrent-save-").toFile()
        try {
            action(directory.resolve("world.rsave"))
        } finally {
            directory.deleteRecursively()
        }
    }
}
