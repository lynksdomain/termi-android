package com.termi.android.books

// Kotlin port of EPUBChapter (EPUBParser.swift). `text` contains the
// terminal-formatted chapter body — heading markers, `_italic_`/`*bold*`/
// `` `code` `` inline markers, `│ ` blockquote indent, `┌─/└─` pre blocks,
// and `[[IMG:path:w:h]]` image sentinels, same encoding EReaderView expects
// on iOS. `imagePaths` mirrors the Swift field 1:1 but — same as iOS — is
// never read by the reader; images are located via the inline markers.
data class EpubChapter(
    val index: Int,
    val title: String,
    val text: String,
    val tocEntries: List<TocEntry> = emptyList(),
    val imagePaths: List<String> = emptyList(),
    // The chapter's path inside the epub archive (e.g. "OEBPS/chapter3.xhtml"),
    // used only during TOC parsing to back-fill TocEntry.chapterIndex by
    // matching hrefs — not part of the Swift model, added because Kotlin's
    // parser needed a stronger anchor than iOS's own admittedly-weak backfill.
    val sourcePath: String = "",
)

data class ParsedEpub(
    val title: String,
    val author: String,
    val chapters: List<EpubChapter>,
    val toc: List<TocEntry>,
)
