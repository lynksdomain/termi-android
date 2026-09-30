package com.termi.android.filesystem

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipFile

/** Shared limits for files supplied by another device. */
object ImportLimits {
    const val MAX_TRANSFER_BYTES = 512L * 1024 * 1024
    const val MAX_TEXT_BYTES = 8L * 1024 * 1024
    const val MAX_ARCHIVE_ENTRIES = 10_000

    fun destination(folder: File, filename: String): File {
        require(filename.isNotBlank() && filename.length <= 240 && filename != "." && filename != "..") { "Invalid filename" }
        require(filename.none { it == '/' || it == '\\' || it.isISOControl() }) { "Invalid filename" }
        val parent = folder.canonicalFile
        return File(parent, filename).also {
            require(!java.nio.file.Files.isSymbolicLink(it.toPath()) && it.canonicalFile.parentFile == parent) { "Filename escapes destination" }
        }
    }

    fun validateArchive(zip: ZipFile) {
        var total = 0L
        var count = 0
        val names = HashSet<String>()
        for (entry in zip.entries()) {
            require(++count <= MAX_ARCHIVE_ENTRIES && names.add(entry.name)) { "Invalid archive entries" }
            require(entry.size in 0..MAX_TRANSFER_BYTES) { "Archive entry too large" }
            total += entry.size
            require(total <= MAX_TRANSFER_BYTES) { "Expanded archive too large" }
        }
    }

    fun copyBounded(input: InputStream, output: OutputStream, limit: Long): Long {
        val buffer = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return total
            total += read
            require(total <= limit) { "File exceeds size limit" }
            output.write(buffer, 0, read)
        }
    }

    fun readText(input: InputStream, limit: Long = MAX_TEXT_BYTES): String = input.use {
        val output = ByteArrayOutputStream()
        copyBounded(it, output, limit)
        output.toString(Charsets.UTF_8.name())
    }
}
