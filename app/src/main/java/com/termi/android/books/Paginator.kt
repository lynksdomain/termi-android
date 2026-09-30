package com.termi.android.books

import android.graphics.Paint
import android.text.StaticLayout
import android.text.TextPaint

// Kotlin port of EReaderView.swift's repaginate() — the pagination engine.
// iOS measures wrapped-line counts per paragraph with NSLayoutManager;
// Android's equivalent is StaticLayout.getLineCount() over a TextPaint at
// the target width. Runs outside Compose (pure function) so it can be
// called from a background coroutine.
data class PaginationResult(
    val pages: List<String>,
    val chapterBreaks: List<Int>,
    val pageCharOffsets: List<Int>,
)

object Paginator {

    // Matches EReaderView's `[[IMG:path:w:h]]` sentinel format.
    private val imageLineRegex = Regex("^\\[\\[IMG:(.*):(\\d+):(\\d+)]]$")

    fun paginate(chapters: List<EpubChapter>, widthPx: Int, heightPx: Int, fontSizePx: Float): PaginationResult {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = fontSizePx
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val lineSpacingExtraPx = 3f * (fontSizePx / 15f) // mirrors iOS's fixed lineSpacing: 3 at base size 15
        // The page-height BUDGET must use the exact same per-line height the
        // reader actually renders with — `PageContent`'s Text uses Compose's
        // `lineHeight = fontSize * 1.2f`, not native font metrics. Using
        // `paint.fontSpacing` here (a different, unrelated height model)
        // caused a real bug: whenever that metric came out smaller than the
        // real rendered line height, the paginator fit one line too many
        // per page, pushing the true last line off the bottom edge —
        // reported live as "the last line is still cut sometimes."
        // `paint`/`StaticLayout` below are still used for word-wrap LINE
        // COUNTING within a paragraph, which is a separate, legitimate use.
        val lineHeight = fontSizePx * 1.2f
        val maxLines = maxOf(1, (heightPx / lineHeight).toInt())

        val newPages = mutableListOf<String>()
        val newBreaks = mutableListOf<Int>()
        val newCharOffsets = mutableListOf<Int>()
        var globalCharOffset = 0

        for (chapter in chapters) {
            newBreaks.add(newPages.size)
            val text = chapter.text
            if (text.isEmpty()) {
                newPages.add("")
                newCharOffsets.add(globalCharOffset)
                globalCharOffset += 1
                continue
            }

            var pageLines = mutableListOf<String>()
            var measuredLines = 0
            var charCursor = 0

            fun flushPage() {
                if (pageLines.isEmpty()) return
                val joined = pageLines.joinToString("\n")
                newPages.add(joined)
                newCharOffsets.add(globalCharOffset + charCursor - joined.length)
                pageLines = mutableListOf()
                measuredLines = 0
            }

            for (rawLine in text.split("\n")) {
                val imgMatch = imageLineRegex.find(rawLine)
                if (imgMatch != null) {
                    val imgW = imgMatch.groupValues[2].toIntOrNull() ?: 0
                    val imgH = imgMatch.groupValues[3].toIntOrNull() ?: 0
                    val renderedW = if (imgW > 0) minOf(widthPx, imgW) else widthPx
                    val aspect = if (imgW > 0 && imgH > 0) imgH.toFloat() / imgW.toFloat() else 0.6f
                    val renderedH = renderedW * aspect
                    val linesNeeded = maxOf(3, kotlin.math.ceil(renderedH / lineHeight).toInt())
                    if (measuredLines + linesNeeded > maxLines && pageLines.isNotEmpty()) flushPage()
                    pageLines.add(rawLine)
                    measuredLines += linesNeeded
                } else {
                    val lineForMeasure = rawLine.ifEmpty { " " }
                    val layout = StaticLayout.Builder.obtain(lineForMeasure, 0, lineForMeasure.length, paint, widthPx)
                        .setLineSpacing(lineSpacingExtraPx, 1f)
                        .build()
                    val wrapped = maxOf(1, layout.lineCount)
                    val wouldOverflow = measuredLines + wrapped > maxLines && pageLines.isNotEmpty()
                    if (wouldOverflow && rawLine.isBlank()) {
                        // A blank/whitespace-only line (source markup often
                        // uses an empty paragraph purely for scene-break
                        // vertical spacing) landing exactly at a page-full
                        // boundary used to flush the current page and then
                        // become the ENTIRE next page by itself — a real
                        // bug caught live via debug logging on a real EPUB
                        // (dozens of wholly blank pages scattered through a
                        // 1864-page book, always right after a page ended
                        // exactly on a paragraph break). Drop it instead:
                        // the page break itself already reads as the
                        // intended visual gap, so nothing is lost.
                        charCursor += rawLine.length + 1
                        continue
                    }
                    if (wouldOverflow) flushPage()
                    pageLines.add(rawLine)
                    measuredLines += wrapped
                }
                charCursor += rawLine.length + 1
            }
            flushPage()
            globalCharOffset += text.length
        }

        return PaginationResult(
            pages = newPages.ifEmpty { listOf("") },
            chapterBreaks = newBreaks,
            pageCharOffsets = newCharOffsets.ifEmpty { listOf(0) },
        )
    }
}
