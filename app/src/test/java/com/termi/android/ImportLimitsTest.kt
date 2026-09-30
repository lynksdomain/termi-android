package com.termi.android

import com.termi.android.filesystem.ImportLimits
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ImportLimitsTest {
    @Test fun rejectsTraversalAbsolutePathsAndControlCharacters() {
        val folder = Files.createTempDirectory("termi-paths").toFile()
        try {
            listOf("../escaped.md", "/tmp/escaped.txt", "a/b.epub", "a\\b.mp3", "a\u0000.txt", "..", "").forEach {
                assertThrows(IllegalArgumentException::class.java) { ImportLimits.destination(folder, it) }
            }
            assertEquals(File(folder.canonicalFile, "hello world.md"), ImportLimits.destination(folder, "hello world.md"))
        } finally { folder.deleteRecursively() }
    }
    @Test fun rejectsSymlinkDestinationsOutsideFolder() {
        val folder = Files.createTempDirectory("termi-symlink").toFile()
        try {
            Files.createSymbolicLink(File(folder, "link.txt").toPath(), requireNotNull(folder.parentFile).toPath().resolve("outside.txt"))
            assertThrows(IllegalArgumentException::class.java) { ImportLimits.destination(folder, "link.txt") }
        } finally { folder.deleteRecursively() }
    }
    @Test fun copiesAtLimitAndRejectsOversizeBeforeWriting() {
        val output = ByteArrayOutputStream()
        assertEquals(4L, ImportLimits.copyBounded(ByteArrayInputStream(byteArrayOf(1,2,3,4)), output, 4))
        assertArrayEquals(byteArrayOf(1,2,3,4), output.toByteArray())
        assertThrows(IllegalArgumentException::class.java) {
            ImportLimits.copyBounded(ByteArrayInputStream(ByteArray(5)), ByteArrayOutputStream(), 4)
        }
    }
}
