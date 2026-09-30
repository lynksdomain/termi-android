package com.termi.android.writing

import java.io.File
import java.time.Instant
import java.util.UUID

// Kotlin port of WritingDocument.swift. Storage is plain text files on disk
// (not Room) — same model as iOS: one file per document, id is a fresh UUID
// per load (not stable identity — the file itself, keyed by name, is).
data class WritingDocument(
    val id: UUID = UUID.randomUUID(),
    val file: File,
    val title: String,
    val wordCount: Int,
    val lastModified: Instant,
    val content: String,
    // Which WritingSource this document was imported from — null for
    // documents imported before source tracking existed, or created/dropped
    // via WiFi Transfer (no distinct inbox; see WritingSource). Migration-safe
    // default: existing persisted content is unaffected, this is populated
    // separately by WritingManager's own source map.
    val sourceRootPath: String? = null,
) {
    companion object {
        fun from(file: File, sourceRootPath: String? = null): WritingDocument {
            val text = WritingFolderManager.readContent(file)
            return WritingDocument(
                file = file,
                title = file.nameWithoutExtension,
                wordCount = WritingFolderManager.wordCount(text),
                lastModified = Instant.ofEpochMilli(file.lastModified()),
                content = text,
                sourceRootPath = sourceRootPath,
            )
        }
    }
}
