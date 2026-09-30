package com.termi.android.writing

import android.content.Context
import java.io.File
import com.termi.android.filesystem.ImportLimits

// Kotlin port of WritingFolderManager.swift. Uses the app's private files
// dir instead of iOS's Documents dir — same "flat folder of .txt/.md files"
// model, no database involved.
object WritingFolderManager {

    private const val FOLDER_NAME = "Termi Writing"

    fun writingFolder(context: Context): File {
        val folder = File(context.filesDir, FOLDER_NAME)
        if (!folder.exists()) folder.mkdirs()
        return folder
    }

    fun scanForDocuments(context: Context): List<File> {
        val folder = writingFolder(context)
        return folder.listFiles { f ->
            f.isFile && f.extension.lowercase() in setOf("txt", "md")
        }?.toList() ?: emptyList()
    }

    fun createDocument(context: Context, title: String): File {
        val base = title.trim().ifEmpty { "untitled" }
        val folder = writingFolder(context)
        var file = ImportLimits.destination(folder, "$base.txt")
        var counter = 2
        while (file.exists()) {
            file = ImportLimits.destination(folder, "${base}_$counter.txt")
            counter += 1
        }
        file.createNewFile()
        return file
    }

    fun importFile(context: Context, source: File): File? {
        val folder = writingFolder(context)
        val base = source.nameWithoutExtension
        val ext = source.extension
        var dest = File(folder, source.name)
        var counter = 2
        while (dest.exists()) {
            dest = ImportLimits.destination(folder, "${base}_$counter.$ext")
            counter += 1
        }
        return try {
            source.copyTo(dest)
            dest
        } catch (e: Exception) {
            null
        }
    }

    fun delete(file: File) {
        file.delete()
    }

    fun rename(context: Context, file: File, newTitle: String): File? {
        val base = newTitle.trim()
        if (base.isEmpty()) return null
        val folder = writingFolder(context)
        val ext = file.extension
        var dest = ImportLimits.destination(folder, "$base.$ext")
        var counter = 2
        while (dest.path != file.path && dest.exists()) {
            dest = ImportLimits.destination(folder, "${base}_$counter.$ext")
            counter += 1
        }
        if (dest.path == file.path) return file
        return if (file.renameTo(dest)) dest else null
    }

    fun readContent(file: File): String =
        try { file.readText(Charsets.UTF_8) } catch (e: Exception) { "" }

    // Write-to-temp-then-rename for atomicity, matching SwiftUI's `atomically: true`.
    fun writeContent(text: String, file: File) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(text, Charsets.UTF_8)
        check(temp.renameTo(file)) { "Could not save ${file.name}; original file preserved." }
    }

    fun wordCount(text: String): Int =
        text.split(Regex("\\s+")).count { it.isNotEmpty() }
}
