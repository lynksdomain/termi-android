package com.termi.android.books

import java.io.File
import java.util.UUID

// Kotlin port of Book.swift. Reading progress fields exist so the reader
// (not yet built) has somewhere to persist to — see BookLibraryManager for
// the SharedPreferences-based persistence mirroring iOS's UserDefaults keys.
data class Book(
    val id: UUID = UUID.randomUUID(),
    val file: File,
    val title: String,
    val author: String = "Unknown Author",
    val chapterCount: Int = 0,
    val estimatedPages: Int = 0,
    val totalPages: Int = 0,
    val currentChapter: Int = 0,
    val currentCharOffset: Int = 0,
    val currentPageIndex: Int = 0,
    // Which BookSource this book was imported from, so a source's count and
    // its removal can be computed — null for books imported before source
    // tracking existed, or dropped via WiFi Transfer (no distinct inbox to
    // tag; see BookSource). Migration-safe default keeps old persisted state
    // (progress prefs keyed by filename, unrelated to this field) working.
    val sourceRootPath: String? = null,
) {
    val progressKey: String get() = "bookProgress_${file.name}"

    val progressFraction: Double
        get() {
            val total = if (totalPages > 0) totalPages else estimatedPages
            if (total <= 1) return 0.0
            return minOf(1.0, currentPageIndex.toDouble() / (total - 1))
        }

    val formattedProgress: String
        get() {
            if (currentPageIndex <= 0 && totalPages <= 0) return "0%"
            return "${(progressFraction * 100).toInt()}%"
        }
}
