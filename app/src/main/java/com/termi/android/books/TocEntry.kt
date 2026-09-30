package com.termi.android.books

// Kotlin port of TOCEntry (EPUBParser.swift). `chapterIndex` defaults to -1
// (unresolved) until EPUBParser.backfillChapterIndices matches this entry's
// href against a parsed chapter's own tocEntries — same weak-match tradeoff
// as iOS; the reader must guard chapterIndex in range before jumping.
data class TocEntry(
    val id: String,
    val title: String,
    val href: String,
    val children: List<TocEntry> = emptyList(),
    val chapterIndex: Int = -1,
)

fun List<TocEntry>.flatten(): List<TocEntry> =
    flatMap { listOf(it) + it.children.flatten() }
