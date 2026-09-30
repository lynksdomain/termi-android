package com.termi.android.books

import java.util.UUID

// Kotlin port of Bookmark.swift. `chapter`/`page` anchor a bookmark to a
// chapter index + page-within-chapter (not a flat page index), so it
// survives repagination (font size / viewport changes) approximately, same
// tradeoff as iOS. `note` is always empty in the current UI — no note-
// editing exists on either platform yet.
data class Bookmark(
    val id: UUID = UUID.randomUUID(),
    val bookFilename: String,
    val chapter: Int,
    val page: Int,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    val displayLabel: String
        get() = note.ifEmpty { "CH${chapter + 1} · PG${page + 1}" }
}
