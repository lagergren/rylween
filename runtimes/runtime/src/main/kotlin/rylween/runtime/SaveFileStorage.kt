package rylween.runtime

import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.PosixFileAttributeView
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Serializes save transactions across threads and processes, and publishes complete snapshots. */
internal object SaveFileStorage {
    private val locks = ConcurrentHashMap<File, ReentrantLock>()

    fun <T> update(
        file: File,
        action: (File) -> T,
    ): T {
        val target = file.canonicalFile
        return locks.computeIfAbsent(target) { ReentrantLock() }.withLock {
            Files.createDirectories(target.toPath().parent)
            // Keep this sidecar: locking the save itself would lock an obsolete inode after replacement.
            val lockFile = target.toPath().resolveSibling("${target.name}.lock")
            FileChannel.open(lockFile, CREATE, WRITE).use { channel ->
                channel.lock().use { action(target) }
            }
        }
    }

    /** Readers can keep using the previous snapshot until the complete replacement is ready. */
    fun replace(
        file: File,
        text: String,
    ) {
        val target = file.toPath()
        val temporary = Files.createTempFile(target.parent, ".${file.name}.", ".tmp")
        try {
            if (Files.exists(target)) {
                val permissions = Files.getFileAttributeView(target, PosixFileAttributeView::class.java)?.readAttributes()?.permissions()
                if (permissions != null) Files.setPosixFilePermissions(temporary, permissions)
            }
            Files.writeString(temporary, text)
            FileChannel.open(temporary, WRITE).use { it.force(true) }
            // No non-atomic fallback: failure must leave the previous save intact.
            Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
