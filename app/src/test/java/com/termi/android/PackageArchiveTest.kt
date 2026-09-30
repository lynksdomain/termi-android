package com.termi.android

import com.termi.android.pkgs.*
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class PackageArchiveTest {
    @Test fun roundTripsAllContentAndCrossPlatformFields() {
        val folder = Files.createTempDirectory("termi-package").toFile()
        try {
            val audio = File(folder, "source.m4a").apply { writeBytes(byteArrayOf(1,2,3)) }
            val archive = File(folder, "gift.termi")
            val manifest = PackageManifest(title = "Gift", senderSignature = "sender", hasMusic = true, hasBook = true, hasWriting = true)
            val quote = PackageBookQuote("quote", "title", "author")
            val note = PackageWritingNote("note", "fresh")
            val tracks = listOf(PackageTrackInfo("song", "artist", 0.0))
            val messages = listOf(PackageMixtapeMessage(timestampSeconds = 8.0, text = "hi"))
            PackageArchive.write(archive, manifest, audio, tracks, messages, quote, note)
            val parsed = requireNotNull(PackageArchive.read(archive, File(folder, "decoded.m4a")))
            assertEquals(manifest, parsed.manifest)
            assertEquals(quote, parsed.quote)
            assertEquals(note, parsed.note)
            assertEquals(tracks, parsed.tracklist)
            assertEquals(messages, parsed.messages)
            assertArrayEquals(audio.readBytes(), requireNotNull(parsed.mixtapeFile).readBytes())
        } finally { folder.deleteRecursively() }
    }
    @Test fun rejectsCompressedOversizeTextWithoutKeepingExtractedAudio() {
        val folder = Files.createTempDirectory("termi-bomb").toFile()
        try {
            val archive = File(folder, "bad.termi")
            ZipOutputStream(archive.outputStream()).use {
                it.putNextEntry(ZipEntry("manifest.json"))
                it.write(ByteArray(8 * 1024 * 1024 + 1) { 32 })
                it.closeEntry()
            }
            val extracted = File(folder, "decoded.m4a")
            assertNull(PackageArchive.read(archive, extracted))
            assertFalse(extracted.exists())
        } finally { folder.deleteRecursively() }
    }
}
