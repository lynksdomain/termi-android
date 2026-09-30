package com.termi.android.books

import android.content.Context
import java.io.File
import com.termi.android.filesystem.ImportLimits
import java.net.URLDecoder
import java.util.zip.ZipFile

// Kotlin port of EPUBParser.swift. Uses java.util.zip.ZipFile for archive
// access (Android already has this; iOS had to hand-roll a zip reader over
// raw NWConnection-style file access). Two tiers, matching iOS:
//   - parseLightweight: title/author/chapterCount/estimatedPages only, used
//     for the library list (fast, no HTML parsing).
//   - parseFull: real chapters with terminal-formatted text (headings,
//     emphasis, blockquotes, pre blocks, image markers) + TOC (EPUB3 nav
//     first, NCX fallback), used when a book is actually opened.
object EpubParser {

    private const val BYTES_PER_PAGE = 2400

    data class LightweightMeta(
        val title: String,
        val author: String,
        val chapterCount: Int,
        val estimatedPages: Int,
    )

    fun parseLightweight(file: File): LightweightMeta? {
        return try {
            require(file.length() <= ImportLimits.MAX_TRANSFER_BYTES)
            ZipFile(file).use { zip ->
                ImportLimits.validateArchive(zip)
                val opfPath = findOpfPath(zip) ?: return null
                val opfXml = readEntryText(zip, opfPath) ?: return null
                val title = extractDcElement(opfXml, "title") ?: file.nameWithoutExtension
                val author = extractDcElement(opfXml, "creator") ?: "Unknown Author"

                val opfDir = opfPath.substringBeforeLast('/', "")
                val manifest = parseManifest(opfXml)
                val spineIds = parseSpineItemRefs(opfXml)

                var totalBytes = 0L
                for (id in spineIds) {
                    val href = manifest[id] ?: continue
                    val entry = resolveEntry(zip, opfDir, href) ?: continue
                    totalBytes += entry.size
                }
                val estimatedPages = if (totalBytes > 0) (totalBytes / BYTES_PER_PAGE).toInt().coerceAtLeast(1) else 0

                LightweightMeta(title, author, spineIds.size, estimatedPages)
            }
        } catch (e: Exception) {
            null
        }
    }

    // Extracts referenced images into a per-book cache dir (mirrors iOS's
    // stable temp dir keyed by sanitized filename) and returns real chapters
    // + TOC. Images are written to disk (not kept in-memory) so the reader
    // can hand a plain file path to Coil/BitmapFactory without holding the
    // zip open for the whole reading session.
    fun parseFull(context: Context, file: File): ParsedEpub? {
        return try {
            require(file.length() <= ImportLimits.MAX_TRANSFER_BYTES)
            ZipFile(file).use { zip ->
                ImportLimits.validateArchive(zip)
                val opfPath = findOpfPath(zip) ?: return null
                val opfXml = readEntryText(zip, opfPath) ?: return null
                val opfDir = opfPath.substringBeforeLast('/', "")

                val title = extractDcElement(opfXml, "title") ?: file.nameWithoutExtension
                val author = extractDcElement(opfXml, "creator") ?: "Unknown Author"
                val manifest = parseManifest(opfXml)
                val manifestProps = parseManifestProperties(opfXml)
                val spineIds = parseSpineItemRefs(opfXml)

                val imageCacheDir = File(context.cacheDir, "epub_images/${sanitize(file.name)}").apply { mkdirs() }

                val toc = loadToc(zip, opfXml, opfDir, manifest, manifestProps)

                val chapters = mutableListOf<EpubChapter>()
                val seenPaths = mutableSetOf<String>()
                for ((i, id) in spineIds.withIndex()) {
                    val href = manifest[id] ?: continue
                    val entry = resolveEntry(zip, opfDir, href) ?: continue
                    if (!seenPaths.add(entry.name)) continue
                    val html = readEntryText(zip, entry.name) ?: continue
                    val chapterDir = entry.name.substringBeforeLast('/', "")
                    val imagePaths = mutableListOf<String>()
                    val text = htmlToRichText(html) { src, w, h ->
                        val resolved = resolveEntry(zip, chapterDir, src)
                        if (resolved == null) {
                            ""
                        } else {
                            val outFile = File(imageCacheDir, sanitize(resolved.name.substringAfterLast('/')))
                            if (!outFile.exists()) {
                                try {
                                    zip.getInputStream(resolved).use { input ->
                                        outFile.outputStream().use { output -> ImportLimits.copyBounded(input, output, ImportLimits.MAX_TEXT_BYTES) }
                                    }
                                } catch (e: Exception) {
                                    return@htmlToRichText ""
                                }
                            }
                            imagePaths.add(outFile.absolutePath)
                            "[[IMG:${outFile.absolutePath}:$w:$h]]"
                        }
                    }
                    if (text.isBlank()) continue
                    val relPath = entry.name
                    val fileName = relPath.substringAfterLast('/')
                    val tocTitle = toc.flatten().firstOrNull { it.href == relPath || it.href == fileName }?.title
                    val title2 = tocTitle ?: extractFirstHeading(html) ?: extractHtmlTitleTag(html) ?: "Chapter ${i + 1}"
                    chapters.add(EpubChapter(index = chapters.size, title = title2, text = text, imagePaths = imagePaths, sourcePath = relPath))
                }

                val backfilled = backfillChapterIndices(toc, chapters)
                ParsedEpub(title = title, author = author, chapters = chapters, toc = backfilled)
            }
        } catch (e: Exception) {
            null
        }
    }

    // MARK: - OPF / manifest / spine

    private fun findOpfPath(zip: ZipFile): String? {
        val containerXml = readEntryText(zip, "META-INF/container.xml")
        val fromContainer = containerXml?.let { extractAttribute(it, "rootfile", "full-path") }
        if (fromContainer != null && zip.getEntry(fromContainer) != null) return fromContainer
        // Fallback: first .opf found anywhere in the archive.
        return zip.entries().asSequence().firstOrNull { it.name.endsWith(".opf", ignoreCase = true) }?.name
    }

    private fun resolveEntry(zip: ZipFile, dir: String, href: String) : java.util.zip.ZipEntry? {
        val decoded = try { URLDecoder.decode(href, "UTF-8") } catch (e: Exception) { href }
        val path = if (dir.isEmpty()) decoded else "$dir/$decoded"
        return zip.getEntry(path) ?: zip.getEntry(decoded) ?: zip.getEntry(href)
    }

    private fun readEntryText(zip: ZipFile, path: String): String? {
        val entry = zip.getEntry(path) ?: return null
        return ImportLimits.readText(zip.getInputStream(entry))
    }

    private fun extractAttribute(xml: String, tag: String, attr: String): String? =
        Regex("<$tag\\b[^>]*\\b$attr=\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(xml)?.groupValues?.get(1)

    private fun extractDcElement(xml: String, element: String): String? {
        val regex = Regex("<(?:dc:)?$element\\b[^>]*>([^<]*)</(?:dc:)?$element>", RegexOption.IGNORE_CASE)
        return regex.find(xml)?.groupValues?.get(1)?.trim()?.ifEmpty { null }
    }

    private fun parseManifest(opfXml: String): Map<String, String> {
        val items = mutableMapOf<String, String>()
        val itemRegex = Regex("<item\\b[^>]*>", RegexOption.IGNORE_CASE)
        val idRegex = Regex("\\bid=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
        val hrefRegex = Regex("\\bhref=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
        for (match in itemRegex.findAll(opfXml)) {
            val tag = match.value
            val id = idRegex.find(tag)?.groupValues?.get(1) ?: continue
            val href = hrefRegex.find(tag)?.groupValues?.get(1) ?: continue
            items[id] = href
        }
        return items
    }

    // id -> "properties" attribute (used to find the EPUB3 nav document via properties="nav")
    private fun parseManifestProperties(opfXml: String): Map<String, String> {
        val items = mutableMapOf<String, String>()
        val itemRegex = Regex("<item\\b[^>]*>", RegexOption.IGNORE_CASE)
        val idRegex = Regex("\\bid=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
        val propsRegex = Regex("\\bproperties=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
        val mediaRegex = Regex("\\bmedia-type=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
        for (match in itemRegex.findAll(opfXml)) {
            val tag = match.value
            val id = idRegex.find(tag)?.groupValues?.get(1) ?: continue
            val props = propsRegex.find(tag)?.groupValues?.get(1) ?: ""
            val media = mediaRegex.find(tag)?.groupValues?.get(1) ?: ""
            items[id] = "$props|$media"
        }
        return items
    }

    private fun parseSpineItemRefs(opfXml: String): List<String> {
        val ids = mutableListOf<String>()
        val itemRefRegex = Regex("<itemref\\b[^>]*>", RegexOption.IGNORE_CASE)
        val idRefRegex = Regex("\\bidref=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
        for (match in itemRefRegex.findAll(opfXml)) {
            idRefRegex.find(match.value)?.groupValues?.get(1)?.let { ids.add(it) }
        }
        return ids
    }

    // MARK: - TOC (EPUB3 nav first, then NCX)

    private fun loadToc(
        zip: ZipFile,
        opfXml: String,
        opfDir: String,
        manifest: Map<String, String>,
        manifestProps: Map<String, String>,
    ): List<TocEntry> {
        val navId = manifestProps.entries.firstOrNull { it.value.startsWith("nav") || it.value.contains("|") && it.value.substringBefore('|').split(" ").contains("nav") }?.key
        if (navId != null) {
            val href = manifest[navId]
            val entry = href?.let { resolveEntry(zip, opfDir, it) }
            if (entry != null) {
                val html = readEntryText(zip, entry.name)
                if (html != null) {
                    val parsed = parseNav(html)
                    if (parsed.isNotEmpty()) return parsed
                }
            }
        }
        val ncxIdFromSpine = extractAttribute(opfXml, "spine", "toc")
        val ncxHref = ncxIdFromSpine?.let { manifest[it] }
            ?: manifestProps.entries.firstOrNull { it.value.endsWith("|application/x-dtbncx+xml") }?.let { manifest[it.key] }
            ?: manifest.values.firstOrNull { it.endsWith(".ncx", ignoreCase = true) }
        if (ncxHref != null) {
            val entry = resolveEntry(zip, opfDir, ncxHref)
            if (entry != null) {
                val xml = readEntryText(zip, entry.name)
                if (xml != null) return parseNcx(xml)
            }
        }
        return emptyList()
    }

    private fun parseNav(html: String): List<TocEntry> {
        val navBlock = Regex("<nav\\b[^>]*epub:type=\"toc\"[^>]*>(.*?)</nav>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)
            ?: Regex("<nav\\b[^>]*id=\"toc\"[^>]*>(.*?)</nav>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)
            ?: Regex("<nav\\b[^>]*>(.*?)</nav>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)
        val body = navBlock?.groupValues?.get(1) ?: return emptyList()
        val olMatch = Regex("<ol\\b[^>]*>(.*)</ol>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(body)
        return parseNavList(olMatch?.groupValues?.get(1) ?: body)
    }

    private fun parseNavList(olInner: String): List<TocEntry> {
        val entries = mutableListOf<TocEntry>()
        val liRegex = Regex("<li\\b[^>]*>(.*?)</li>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        for (liMatch in liRegex.findAll(olInner)) {
            val liBody = liMatch.groupValues[1]
            val aMatch = Regex("<a\\b[^>]*href=\"([^\"]*)\"[^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(liBody)
            val href = aMatch?.groupValues?.get(1)?.substringBefore('#') ?: continue
            val title = stripTags(aMatch.groupValues[2]).trim()
            val nestedOl = Regex("<ol\\b[^>]*>(.*)</ol>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(liBody)
            val children = nestedOl?.groupValues?.get(1)?.let { parseNavList(it) } ?: emptyList()
            entries.add(TocEntry(id = href, title = title.ifEmpty { href }, href = href, children = children))
        }
        return entries
    }

    private fun parseNcx(xml: String): List<TocEntry> {
        fun parseNavPoints(scope: String): List<TocEntry> {
            val entries = mutableListOf<TocEntry>()
            val npRegex = Regex("<navPoint\\b[^>]*id=\"([^\"]*)\"[^>]*>(.*?)</navPoint>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            for (m in npRegex.findAll(scope)) {
                val id = m.groupValues[1]
                val body = m.groupValues[2]
                val title = Regex("<navLabel\\b[^>]*>\\s*<text\\b[^>]*>(.*?)</text>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .find(body)?.groupValues?.get(1)?.let { stripTags(it).trim() } ?: continue
                val href = Regex("<content\\b[^>]*src=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
                    .find(body)?.groupValues?.get(1)?.substringBefore('#') ?: continue
                // Only recurse into DIRECT child navPoints, not this navPoint's own body twice —
                // strip the outer navPoint's immediate navLabel/content before recursing.
                val innerStart = body.indexOf("</content>")
                val childScope = if (innerStart >= 0) body.substring(innerStart) else ""
                entries.add(TocEntry(id = id, title = title, href = href, children = parseNavPoints(childScope)))
            }
            return entries
        }
        val navMapMatch = Regex("<navMap\\b[^>]*>(.*)</navMap>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(xml)
        return parseNavPoints(navMapMatch?.groupValues?.get(1) ?: xml)
    }

    // Matches each TOC entry's href against a chapter's archive source path
    // (full path, then bare filename) — same intent as EPUBParser.swift's
    // backfillChapterIndices, but anchored on a real path instead of a title
    // guess, since Kotlin's EpubChapter keeps `sourcePath` around for this.
    private fun backfillChapterIndices(toc: List<TocEntry>, chapters: List<EpubChapter>): List<TocEntry> {
        fun resolve(entry: TocEntry): TocEntry {
            val hrefFile = entry.href.substringAfterLast('/')
            val idx = chapters.indexOfFirst { it.sourcePath == entry.href || it.sourcePath.endsWith("/$hrefFile") || it.sourcePath == hrefFile }
            return entry.copy(chapterIndex = idx, children = entry.children.map(::resolve))
        }
        return toc.map(::resolve)
    }

    // MARK: - HTML → terminal-formatted text

    private fun extractFirstHeading(html: String): String? {
        val m = Regex("<h[123]\\b[^>]*>(.*?)</h[123]>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)
        val text = m?.groupValues?.get(1)?.let { stripTags(it).trim() } ?: return null
        return text.ifEmpty { null }?.take(200)
    }

    private fun extractHtmlTitleTag(html: String): String? {
        val m = Regex("<title\\b[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)
        return m?.groupValues?.get(1)?.let { stripTags(it).trim() }?.ifEmpty { null }?.take(120)
    }

    private fun stripTags(html: String): String = Regex("<[^>]+>").replace(html, "")

    // onImage(src, width, height) -> marker string to splice in (or "" to drop).
    private fun htmlToRichText(html: String, onImage: (String, Int, Int) -> String): String {
        var s = html

        // 1. Extract <img> tags first, before anything else touches them.
        val imgRegex = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
        s = imgRegex.replace(s) { match ->
            val tag = match.value
            val src = Regex("\\bsrc=\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)
            if (src == null || src.startsWith("data:")) return@replace ""
            val w = Regex("\\bwidth=\"(\\d+)\"", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("width:\\s*(\\d+)").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val h = Regex("\\bheight=\"(\\d+)\"", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("height:\\s*(\\d+)").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            "\n${onImage(src, w, h)}\n"
        }

        // 2. Drop non-content blocks entirely.
        for (tag in listOf("head", "script", "style", "noscript", "svg")) {
            s = Regex("<$tag\\b[^>]*>.*?</$tag>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).replace(s, "")
        }

        // 3. Headings.
        s = Regex("<h1\\b[^>]*>(.*?)</h1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .replace(s) { "\n\n══ ${stripTags(it.groupValues[1]).trim().uppercase()} ══\n" }
        s = Regex("<h2\\b[^>]*>(.*?)</h2>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .replace(s) { "\n\n── ${stripTags(it.groupValues[1]).trim()} ──\n" }
        s = Regex("<h3\\b[^>]*>(.*?)</h3>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .replace(s) { "\n\n> ${stripTags(it.groupValues[1]).trim()}\n" }
        s = Regex("<h[4-6]\\b[^>]*>(.*?)</h[4-6]>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .replace(s) { "\n\n- ${stripTags(it.groupValues[1]).trim()}\n" }

        // 4. Inline emphasis.
        s = Regex("<(?:em|i)\\b[^>]*>(.*?)</(?:em|i)>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .replace(s) { "_${stripTags(it.groupValues[1])}_" }
        s = Regex("<(?:strong|b)\\b[^>]*>(.*?)</(?:strong|b)>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .replace(s) { "*${stripTags(it.groupValues[1])}*" }
        s = Regex("<(?:code|kbd|samp)\\b[^>]*>(.*?)</(?:code|kbd|samp)>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .replace(s) { "`${stripTags(it.groupValues[1])}`" }

        // 5. Blockquote — indent every resulting line with "│ ".
        s = Regex("<blockquote\\b[^>]*>(.*?)</blockquote>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .replace(s) { match ->
                val inner = blockify(match.groupValues[1])
                inner.lines().joinToString("\n") { line -> if (line.isBlank()) "│" else "│ $line" }
            }

        // 6. Pre blocks.
        s = Regex("<pre\\b[^>]*>(.*?)</pre>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .replace(s) { "\n┌─\n${stripTags(it.groupValues[1])}\n└─\n" }

        // 7. Remaining block tags -> newline.
        s = blockify(s)

        // 8. Unwrap anchors, strip everything else.
        s = Regex("<a\\b[^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).replace(s) { it.groupValues[1] }
        s = stripTags(s)

        // 9. Entities.
        s = decodeEntities(s)

        // 10. Whitespace normalization.
        s = Regex("[ \\t]+").replace(s, " ")
        s = Regex("\n[ \\t]+").replace(s, "\n")
        s = Regex("\n{4,}").replace(s, "\n\n\n")
        s = s.lines().filterNot { Regex("^[\\s═─>\\-]+$").matches(it) && it.isNotBlank() && !it.contains("│") }.joinToString("\n")

        return s.trim()
    }

    private fun blockify(html: String): String {
        val blockTags = "p|div|article|section|li|tr|br|hr|figure|figcaption|dd|dt|header|footer|main|aside|nav|address|details|summary"
        var s = Regex("<(?:$blockTags)\\b[^>]*/?>", RegexOption.IGNORE_CASE).replace(html, "\n")
        s = Regex("</(?:$blockTags)>", RegexOption.IGNORE_CASE).replace(s, "\n")
        return s
    }

    private fun decodeEntities(text: String): String {
        var s = text
            .replace("&nbsp;", " ")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&rsquo;", "'").replace("&lsquo;", "'")
            .replace("&rdquo;", "\"").replace("&ldquo;", "\"")
            .replace("&hellip;", "…")
            .replace("&copy;", "©")
            .replace("&reg;", "®")
            .replace("&trade;", "™")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
        s = Regex("&#x([0-9a-fA-F]+);").replace(s) { m ->
            m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
        }
        s = Regex("&#(\\d+);").replace(s) { m ->
            m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
        }
        // &lt;/&gt;/&amp; last, so entity-encoded markup in text doesn't get
        // re-parsed as real tags by the steps above.
        s = s.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
        return s
    }

    private fun sanitize(name: String): String = name.replace(Regex("[^a-zA-Z0-9_\\-.]"), "_")
}
