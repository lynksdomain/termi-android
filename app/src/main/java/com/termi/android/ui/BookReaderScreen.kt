package com.termi.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termi.android.books.Book
import com.termi.android.books.BookLibraryManager
import com.termi.android.books.Bookmark
import com.termi.android.books.EpubParser
import com.termi.android.books.ParsedEpub
import com.termi.android.books.PaginationResult
import com.termi.android.books.Paginator
import com.termi.android.books.TocEntry
import com.termi.android.books.flatten
import com.termi.android.music.AudioPlayerEngine
import com.termi.android.settings.AppSettings
import com.termi.android.stats.ReadingStatsManager
import com.termi.android.theme.LocalTerminalTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Kotlin/Compose port of EReaderView.swift — a real paginated reader:
// text is pre-broken into fixed pages via Paginator (Android StaticLayout
// measurement standing in for iOS's NSLayoutManager pass), with tap-zone
// page turns, swipe-down-to-dismiss, a tap-center chrome toggle, header/
// footer overlays, a TOC drawer, a bookmarks drawer, a 36-bar scrubber, and
// font-size controls — all reusing the app's TerminalTheme (there is no
// separate "reading theme" on iOS either; light/sepia reading = picking a
// light TerminalTheme). Includes the daily reading-goal footer bar
// (settings.dailyReadingGoal, backed by AppSettings — see
// recordPageAdvance() below, ported from EReaderView.swift's own
// UserDefaults-backed pagesReadToday). Still deferred vs iOS: the
// system-dictionary long-press lookup (no Android equivalent), the
// first-run tutorial overlay, and real custom reader fonts (ReaderFont is
// Pro-gated on iOS and Lifetime Pro purchasing isn't implemented on
// Android — font size still works).
@Composable
fun BookReaderScreen(
    book: Book,
    library: BookLibraryManager,
    readingStats: ReadingStatsManager,
    audioPlayer: AudioPlayerEngine,
    appSettings: AppSettings,
    onClose: () -> Unit,
) {
    val theme = LocalTerminalTheme.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var parsed by remember(book.id) { mutableStateOf<ParsedEpub?>(null) }
    var isLoading by remember(book.id) { mutableStateOf(true) }
    var pagination by remember(book.id) { mutableStateOf<PaginationResult?>(null) }
    var currentPageIndex by remember(book.id) { mutableIntStateOf(0) }
    var fontSize by remember { mutableFloatStateOf(15f) }
    var hasRestoredPage by remember(book.id) { mutableStateOf(false) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    var showControls by remember { mutableStateOf(false) }
    var showTOC by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var bookmarks by remember(book.id) { mutableStateOf<List<Bookmark>>(emptyList()) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPageIndex by remember { mutableIntStateOf(0) }

    val chapterBreaks = pagination?.chapterBreaks ?: emptyList()
    val pages = pagination?.pages ?: emptyList()
    val currentChapterIndex = chapterBreaks.indexOfLast { it <= currentPageIndex }.coerceAtLeast(0)
    val flatToc = remember(parsed) { parsed?.toc?.flatten() ?: emptyList() }
    val isCurrentBookmarked = bookmarks.any { it.chapter == currentChapterIndex && it.page == (currentPageIndex - (chapterBreaks.getOrElse(currentChapterIndex) { 0 })) }

    fun saveProgress() {
        val charOffset = pagination?.pageCharOffsets?.getOrElse(currentPageIndex) { 0 } ?: 0
        library.updateProgress(book.id, chapter = currentChapterIndex, charOffset = charOffset, pageIndex = currentPageIndex)
    }

    fun goToPage(index: Int) {
        val clamped = index.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        if (clamped == currentPageIndex) return
        currentPageIndex = clamped
        saveProgress()
    }

    val dailyReadingGoal by appSettings.dailyReadingGoal.collectAsState()
    val pagesReadToday by appSettings.pagesReadToday.collectAsState()

    // Mirrors EReaderView.swift's nextPage(): the ONLY page-turn path that
    // records goal progress — a scrub, TOC jump, or bookmark jump doesn't
    // count as "reading," only an actual forward single-page turn does.
    fun nextPage() {
        val target = currentPageIndex + 1
        if (target > (pages.size - 1).coerceAtLeast(0)) return
        goToPage(target)
        if (dailyReadingGoal > 0) appSettings.recordPageAdvance()
    }

    LaunchedEffect(book.id) {
        isLoading = true
        val result = withContext(Dispatchers.IO) { EpubParser.parseFull(context, book.file) }
        parsed = result
        bookmarks = library.bookmarks(book)
        isLoading = false
        readingStats.beginReading(book.file.name, book.title, book.author, book.currentPageIndex)
    }

    DisposableEffect(book.id) {
        onDispose { readingStats.endReading(currentPageIndex, currentChapterIndex) }
    }

    LaunchedEffect(parsed, viewportSize, fontSize) {
        val chapters = parsed?.chapters ?: return@LaunchedEffect
        if (viewportSize.width == 0 || viewportSize.height == 0 || chapters.isEmpty()) return@LaunchedEffect
        val widthPx = viewportSize.width - with(density) { 32.dp.toPx() }.toInt()
        val heightPx = viewportSize.height - with(density) { 180.dp.toPx() }.toInt()
        val fontPx = with(density) { fontSize.sp.toPx() }
        val result = withContext(Dispatchers.Default) {
            Paginator.paginate(chapters, widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), fontPx)
        }
        pagination = result
        if (!hasRestoredPage) {
            val savedOffset = library.savedCharOffset(book)
            val restoreIndex = result.pageCharOffsets.indexOfLast { it <= savedOffset }.coerceAtLeast(0)
            currentPageIndex = restoreIndex.coerceIn(0, result.pages.size - 1)
            hasRestoredPage = true
        } else {
            currentPageIndex = currentPageIndex.coerceIn(0, result.pages.size - 1)
        }
        library.updateTotalPages(book.id, result.pages.size)
    }

    fun toggleBookmark() {
        val pageInChapter = currentPageIndex - chapterBreaks.getOrElse(currentChapterIndex) { 0 }
        val existing = bookmarks.firstOrNull { it.chapter == currentChapterIndex && it.page == pageInChapter }
        if (existing != null) {
            library.removeBookmark(existing.id, book.file.name)
        } else {
            library.addBookmark(Bookmark(bookFilename = book.file.name, chapter = currentChapterIndex, page = pageInChapter))
        }
        bookmarks = library.bookmarks(book)
    }

    fun jumpToBookmark(bookmark: Bookmark) {
        val target = (chapterBreaks.getOrElse(bookmark.chapter) { 0 } + bookmark.page).coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        currentPageIndex = target
        saveProgress()
        showBookmarks = false
    }

    fun jumpToTocEntry(entry: TocEntry) {
        if (entry.chapterIndex < 0 || entry.chapterIndex >= chapterBreaks.size) return
        currentPageIndex = chapterBreaks[entry.chapterIndex]
        saveProgress()
        showTOC = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewportSize = it }
                .offset { androidx.compose.ui.unit.IntOffset(0, dragOffset.roundToInt()) },
        ) {
            when {
                isLoading -> CenteredMessage("LOADING BOOK…")
                parsed == null -> CenteredMessage("COULD NOT PARSE THIS EPUB")
                pages.isEmpty() -> CenteredMessage("PAGINATING…")
                else -> {
                    val pageText = pages.getOrElse(currentPageIndex) { "" }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInputPageGestures(
                                hasChromeOpen = showControls || showTOC || showBookmarks,
                                onTapLeft = { goToPage(currentPageIndex - 1) },
                                onTapRight = { nextPage() },
                                onTapCenter = { showControls = !showControls },
                                onSwipeLeft = { nextPage() },
                                onSwipeRight = { goToPage(currentPageIndex - 1) },
                                onDismiss = onClose,
                            ),
                    ) {
                        PageContent(text = pageText, fontSize = fontSize.sp)
                    }
                }
            }
        }

        CRTOverlay()

        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ReaderHeader(title = book.title, onClose = onClose)
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            AnimatedVisibility(
                visible = showControls,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                ReaderFooter(
                    audioPlayer = audioPlayer,
                    isBookmarked = isCurrentBookmarked,
                    hasBookmarks = bookmarks.isNotEmpty(),
                    hasToc = flatToc.isNotEmpty(),
                    currentPage = currentPageIndex,
                    totalPages = pages.size,
                    isScrubbing = isScrubbing,
                    scrubPageIndex = scrubPageIndex,
                    dailyReadingGoal = dailyReadingGoal,
                    pagesReadToday = pagesReadToday,
                    onToggleBookmark = { toggleBookmark() },
                    onToggleBookmarksDrawer = { showBookmarks = !showBookmarks },
                    onToggleTocDrawer = { showTOC = !showTOC },
                    onDecreaseFont = { fontSize = (fontSize - 1f).coerceAtLeast(11f) },
                    onIncreaseFont = { fontSize = (fontSize + 1f).coerceAtMost(24f) },
                    onScrubStart = { isScrubbing = true },
                    onScrubMove = { scrubPageIndex = it.coerceIn(0, (pages.size - 1).coerceAtLeast(0)) },
                    onScrubEnd = {
                        isScrubbing = false
                        goToPage(scrubPageIndex)
                    },
                )
            }
        }

        if (showBookmarks) {
            SideDrawer(onDismiss = { showBookmarks = false }) {
                BookmarksDrawerContent(bookmarks = bookmarks, onJump = { jumpToBookmark(it) }, onDelete = { library.removeBookmark(it.id, book.file.name); bookmarks = library.bookmarks(book) }, onClose = { showBookmarks = false })
            }
        }
        if (showTOC) {
            SideDrawer(onDismiss = { showTOC = false }) {
                TocDrawerContent(entries = flatToc, currentChapterIndex = currentChapterIndex, onJump = { jumpToTocEntry(it) }, onClose = { showTOC = false })
            }
        }
    }
}

@Composable
private fun CenteredMessage(text: String) {
    val theme = LocalTerminalTheme.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "║  $text", color = theme.dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun PageContent(text: String, fontSize: androidx.compose.ui.unit.TextUnit) {
    val theme = LocalTerminalTheme.current
    val blocks = remember(text) { splitImageBlocks(text) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 56.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is PageBlock.TextBlock -> Text(
                    text = formatInline(block.text),
                    color = theme.foreground,
                    fontSize = fontSize,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = fontSize * 1.2f,
                )
                is PageBlock.ImageBlock -> {
                    val bitmap = remember(block.path) { loadImageBitmap(block.path) }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

private sealed class PageBlock {
    data class TextBlock(val text: String) : PageBlock()
    data class ImageBlock(val path: String) : PageBlock()
}

private val imageMarkerRegex = Regex("\\[\\[IMG:(.*?):(\\d+):(\\d+)]]")

private fun splitImageBlocks(text: String): List<PageBlock> {
    val blocks = mutableListOf<PageBlock>()
    var lastEnd = 0
    for (m in imageMarkerRegex.findAll(text)) {
        if (m.range.first > lastEnd) {
            val chunk = text.substring(lastEnd, m.range.first).trim('\n')
            if (chunk.isNotBlank()) blocks.add(PageBlock.TextBlock(chunk))
        }
        blocks.add(PageBlock.ImageBlock(m.groupValues[1]))
        lastEnd = m.range.last + 1
    }
    if (lastEnd < text.length) {
        val chunk = text.substring(lastEnd).trim('\n')
        if (chunk.isNotBlank()) blocks.add(PageBlock.TextBlock(chunk))
    }
    if (blocks.isEmpty() && text.isNotBlank()) blocks.add(PageBlock.TextBlock(text))
    return blocks
}

private fun loadImageBitmap(path: String): androidx.compose.ui.graphics.ImageBitmap? {
    return try {
        val bmp = android.graphics.BitmapFactory.decodeFile(path) ?: return null
        bmp.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

// Renders *bold*, _italic_ and `code` markers as real spans — the iOS
// source only strips these markers without applying styling (an admitted
// unfinished feature there); doing it properly here is strictly better and
// doesn't change the reading experience iOS users get elsewhere.
private fun formatInline(raw: String): androidx.compose.ui.text.AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < raw.length) {
        val c = raw[i]
        when {
            c == '*' && raw.indexOf('*', i + 1) > i -> {
                val end = raw.indexOf('*', i + 1)
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(raw.substring(i + 1, end)) }
                i = end + 1
            }
            c == '_' && raw.indexOf('_', i + 1) > i -> {
                val end = raw.indexOf('_', i + 1)
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(raw.substring(i + 1, end)) }
                i = end + 1
            }
            c == '`' && raw.indexOf('`', i + 1) > i -> {
                val end = raw.indexOf('`', i + 1)
                withStyle(SpanStyle(background = Color.Gray.copy(alpha = 0.2f))) { append(raw.substring(i + 1, end)) }
                i = end + 1
            }
            else -> {
                append(c)
                i++
            }
        }
    }
}

@Composable
private fun ReaderHeader(title: String, onClose: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.background.copy(alpha = 0.92f))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = title,
            color = theme.foreground,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().align(Alignment.Center),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            text = "[CLOSE]",
            color = theme.dim,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.CenterEnd).clickable(onClick = onClose).padding(vertical = 8.dp, horizontal = 6.dp),
        )
        Box(modifier = Modifier.fillMaxWidth().align(Alignment.BottomStart).height(0.5.dp).background(theme.accent.copy(alpha = 0.3f)))
    }
}

@Composable
private fun ReaderFooter(
    audioPlayer: AudioPlayerEngine,
    isBookmarked: Boolean,
    hasBookmarks: Boolean,
    hasToc: Boolean,
    currentPage: Int,
    totalPages: Int,
    isScrubbing: Boolean,
    scrubPageIndex: Int,
    dailyReadingGoal: Int,
    pagesReadToday: Int,
    onToggleBookmark: () -> Unit,
    onToggleBookmarksDrawer: () -> Unit,
    onToggleTocDrawer: () -> Unit,
    onDecreaseFont: () -> Unit,
    onIncreaseFont: () -> Unit,
    onScrubStart: () -> Unit,
    onScrubMove: (Int) -> Unit,
    onScrubEnd: () -> Unit,
) {
    val theme = LocalTerminalTheme.current
    val hasSong by audioPlayer.currentSong.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.background.copy(alpha = 0.92f)),
    ) {
        if (hasSong != null) {
            MiniPlayerBar(player = audioPlayer, onOpen = {})
        }
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.accent.copy(alpha = 0.3f)))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            FooterButton(if (isBookmarked) "[UNMARK]" else "[MARK]", if (isBookmarked) theme.secondary else theme.dim, onToggleBookmark)
            FooterButton("[BKM]", if (hasBookmarks) theme.accent else theme.dim, onToggleBookmarksDrawer)
            FooterButton("[A-]", theme.dim, onDecreaseFont)
            FooterButton("[A+]", theme.dim, onIncreaseFont)
            FooterButton("[TOC]", if (hasToc) theme.accent else theme.dim, onToggleTocDrawer)
        }
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(0.5.dp).background(theme.accent.copy(alpha = 0.2f)))
        PageScrubber(
            currentPage = currentPage,
            totalPages = totalPages,
            isScrubbing = isScrubbing,
            scrubPageIndex = scrubPageIndex,
            onScrubStart = onScrubStart,
            onScrubMove = onScrubMove,
            onScrubEnd = onScrubEnd,
        )
        val displayIndex = if (isScrubbing) scrubPageIndex else currentPage
        val pct = if (totalPages > 1) (displayIndex * 100 / (totalPages - 1)) else 0
        Text(
            text = if (isScrubbing) "PG ${scrubPageIndex + 1} · $pct%" else "PG ${currentPage + 1} / $totalPages  ·  $pct%",
            color = if (isScrubbing) theme.accent else theme.dim,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = if (dailyReadingGoal > 0 && !isScrubbing) 2.dp else 6.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        // Mirrors EReaderView.swift's footerStatusLabel goal line exactly:
        // a 20-cell ▓/░ bar + "N/GOALpg  PCT%", glowing accent once the
        // goal is met, hidden entirely while scrubbing or when no goal is
        // set (dailyReadingGoal == 0, iOS's "no goal" sentinel).
        if (dailyReadingGoal > 0 && !isScrubbing) {
            val goalPct = (pagesReadToday * 100 / dailyReadingGoal).coerceAtMost(100)
            val filled = (pagesReadToday * 20 / dailyReadingGoal).coerceAtMost(20)
            val bar = "▓".repeat(filled) + "░".repeat(20 - filled)
            val goalMet = pagesReadToday >= dailyReadingGoal
            Text(
                text = "GOAL $bar $pagesReadToday/${dailyReadingGoal}PG  $goalPct%",
                color = if (goalMet) theme.accent else theme.secondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.FooterButton(label: String, color: Color, onClick: () -> Unit) {
    Text(
        text = label,
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.weight(1f).clickable(onClick = onClick).padding(vertical = 10.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun PageScrubber(
    currentPage: Int,
    totalPages: Int,
    isScrubbing: Boolean,
    scrubPageIndex: Int,
    onScrubStart: () -> Unit,
    onScrubMove: (Int) -> Unit,
    onScrubEnd: () -> Unit,
) {
    val theme = LocalTerminalTheme.current
    val display = if (isScrubbing) scrubPageIndex else currentPage
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(top = 6.dp)
            .height(10.dp)
            .pointerInputScrubGestures(totalPages, onScrubStart, onScrubMove, onScrubEnd),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val barCount = 36
            val bw = (size.width - (barCount - 1) * 2f) / barCount
            val filled = if (totalPages > 0) (display.toDouble() / totalPages.toDouble() * barCount).toInt() else 0
            for (i in 0 until barCount) {
                val x = i * (bw + 2f)
                drawRoundRect(
                    color = if (i < filled) theme.accent else theme.dim.copy(alpha = 0.25f),
                    topLeft = Offset(x, 0f),
                    size = Size(bw, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f, 1f),
                )
            }
        }
    }
}

private fun Modifier.pointerInputScrubGestures(
    totalPages: Int,
    onStart: () -> Unit,
    onMove: (Int) -> Unit,
    onEnd: () -> Unit,
): Modifier = this.pointerInput(totalPages) {
    detectDragGestures(
        onDragStart = { onStart() },
        onDragEnd = { onEnd() },
        onDragCancel = { onEnd() },
    ) { change, _ ->
        val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
        onMove((fraction * (totalPages - 1).coerceAtLeast(0)).roundToInt())
    }
}

// A SINGLE gesture detector classifying tap-vs-swipe at pointer-up by total
// movement — mirrors EReaderView.swift's one `DragGesture(minimumDistance:
// 0)` exactly. An earlier version used two separate `pointerInput` blocks
// (detectTapGestures + detectDragGestures) on the same node; Compose feeds
// both the same touch stream and they fought over consumption, making taps
// and swipes unreliable ("can't scroll or turn the page" — caught live).
private fun Modifier.pointerInputPageGestures(
    hasChromeOpen: Boolean,
    onTapLeft: () -> Unit,
    onTapRight: () -> Unit,
    onTapCenter: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onDismiss: () -> Unit,
): Modifier = this.pointerInput(hasChromeOpen) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val startX = down.position.x
        val startY = down.position.y
        var totalDx = 0f
        var totalDy = 0f
        var pointerId = down.id

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
            if (change.changedToUpIgnoreConsumed()) {
                totalDx = change.position.x - startX
                totalDy = change.position.y - startY
                val distance = kotlin.math.sqrt(totalDx * totalDx + totalDy * totalDy)
                if (distance < 10f) {
                    // Tap.
                    when {
                        hasChromeOpen -> onTapCenter()
                        startX < size.width * 0.3f -> onTapLeft()
                        startX > size.width * 0.7f -> onTapRight()
                        else -> onTapCenter()
                    }
                } else if (!hasChromeOpen) {
                    if (totalDy > 80 && totalDy > abs(totalDx) * 2.5f) {
                        onDismiss()
                    } else if (abs(totalDx) > 60 && abs(totalDx) > abs(totalDy) * 2f) {
                        if (totalDx < 0) onSwipeLeft() else onSwipeRight()
                    }
                }
                change.consume()
                break
            }
            change.consume()
        }
    }
}

@Composable
private fun SideDrawer(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val theme = LocalTerminalTheme.current
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onDismiss),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxSize()
                .width(280.dp)
                .background(theme.background)
                .background(theme.selectionBackground.copy(alpha = 0.6f)),
        ) {
            content()
        }
    }
}

@Composable
private fun BookmarksDrawerContent(bookmarks: List<Bookmark>, onJump: (Bookmark) -> Unit, onDelete: (Bookmark) -> Unit, onClose: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "╔═[ BOOKMARKS ]", color = theme.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(text = "[X]", color = theme.dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.clickable(onClick = onClose))
        }
        if (bookmarks.isEmpty()) {
            Text(text = "[ NO BOOKMARKS ]", color = theme.dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(20.dp))
        } else {
            LazyColumn {
                items(bookmarks) { bm ->
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = bm.displayLabel, color = theme.foreground, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                            Text(text = "[GO]", color = theme.secondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.clickable { onJump(bm) }.padding(horizontal = 6.dp))
                            Text(text = "[X]", color = theme.secondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.clickable { onDelete(bm) }.padding(horizontal = 6.dp))
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.dim.copy(alpha = 0.2f)))
                    }
                }
            }
        }
    }
}

@Composable
private fun TocDrawerContent(entries: List<TocEntry>, currentChapterIndex: Int, onJump: (TocEntry) -> Unit, onClose: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "╔═[ TABLE OF CONTENTS ]", color = theme.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(text = "[X]", color = theme.dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.clickable(onClick = onClose))
        }
        LazyColumn {
            items(entries) { entry ->
                val active = entry.chapterIndex == currentChapterIndex
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onJump(entry) }.padding(vertical = 8.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = entry.title,
                            color = if (active) theme.accent else theme.foreground,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 2,
                            modifier = Modifier.weight(1f),
                        )
                        if (active) {
                            Text(text = "[CURRENT]", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.dim.copy(alpha = 0.2f)))
                }
            }
        }
    }
}
