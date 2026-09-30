package com.termi.android.books

import android.content.Context
import java.io.File

// Kotlin port of BookFolderManager.swift — same "copy into the app's own
// private folder" model as Music/Journal.
object BookFolderManager {

    private const val FOLDER_NAME = "Termi Books"

    fun booksFolder(context: Context): File {
        val folder = File(context.filesDir, FOLDER_NAME)
        if (!folder.exists()) folder.mkdirs()
        return folder
    }

    fun scanForEpubs(context: Context): List<File> {
        val folder = booksFolder(context)
        return folder.listFiles { f -> f.isFile && f.extension.lowercase() == "epub" }
            ?.toList() ?: emptyList()
    }

    fun copyIntoFolder(context: Context, source: File): File? {
        val folder = booksFolder(context)
        val base = source.nameWithoutExtension
        var dest = File(folder, source.name)
        var counter = 2
        while (dest.exists()) {
            dest = File(folder, "${base}_$counter.epub")
            counter += 1
        }
        return try {
            source.copyTo(dest)
            dest
        } catch (e: Exception) {
            null
        }
    }

    fun remove(file: File) {
        file.delete()
    }
}
