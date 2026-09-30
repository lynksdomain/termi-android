package com.termi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termi.android.books.Book
import com.termi.android.books.BookLibraryManager
import com.termi.android.books.EpubChapter
import com.termi.android.books.EpubParser
import com.termi.android.pkgs.PackageBookQuote
import com.termi.android.theme.LocalTerminalTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Kotlin/Compose analog to iOS's PackageQuoteBookPickerView: book → chapter
// → passage, from a REAL book in the library — iOS has no manual quote-
// typing path at all for this slot, so Android shouldn't either (the old
// free-text quote editor was removed). One deliberate difference: iOS
// drag-selects an arbitrary text range from a non-editable text view via a
// hacked system-menu-suppressing UITextView subclass; Compose has no
// equivalent "read the current selection" hook without real internal-API
// work, so this picks a whole PARAGRAPH by tap instead of an arbitrary
// character range — still a real passage from a real chapter, just
// paragraph-granularity rather than free-form drag-select.
private sealed class QuoteStep {
    object Books : QuoteStep()
    data class Chapters(val book: Book, val chapters: List<EpubChapter>) : QuoteStep()
    data class Paragraphs(val book: Book, val chapters: List<EpubChapter>, val paragraphs: List<String>) : QuoteStep()
}

@Composable
fun BookQuotePickerScreen(
    bookLibrary: BookLibraryManager,
    onDone: (PackageBookQuote) -> Unit,
    onCancel: () -> Unit,
) {
    val theme = LocalTerminalTheme.current
    val context = LocalContext.current
    val books by bookLibrary.books.collectAsState()
    var step by remember { mutableStateOf<QuoteStep>(QuoteStep.Books) }
    var isLoading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var pendingBook by remember { mutableStateOf<Book?>(null) }

    // The book library is a shared instance that only actually scans once
    // BooksScreen's own LaunchedEffect triggers it — if the user opens
    // PKGS's quote picker without ever visiting the Books tab first, this
    // would otherwise show "no books" even when real books exist on disk.
    LaunchedEffect(Unit) { if (!bookLibrary.hasLoadedOnce.value) bookLibrary.loadLibrary() }

    LaunchedEffect(pendingBook) {
        val book = pendingBook ?: return@LaunchedEffect
        isLoading = true
        loadError = null
        val parsed = withContext(Dispatchers.IO) { EpubParser.parseFull(context, book.file) }
        isLoading = false
        if (parsed == null || parsed.chapters.isEmpty()) {
            loadError = "Couldn't load this book's chapters."
        } else {
            step = QuoteStep.Chapters(book, parsed.chapters)
        }
    }

    // Matches real iOS: the book-list step is its own screen
    // (PackageQuoteBookPickerView, header "PICK A BOOK"), then chapter-list
    // AND passage-select share one header ("SELECT A QUOTE" on
    // BookQuotePickerView) — iOS doesn't retitle per step once a book is
    // picked, it just swaps the center content and the back-button target.
    val title = when (step) {
        is QuoteStep.Books -> "PICK A BOOK"
        is QuoteStep.Chapters, is QuoteStep.Paragraphs -> "SELECT A QUOTE"
    }

    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "┌─[ $title ]", color = theme.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Row {
                    val currentStep = step
                    if (currentStep !is QuoteStep.Books) {
                        Text(
                            text = "[BACK]",
                            color = theme.dim,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.clickable {
                                step = when (currentStep) {
                                    is QuoteStep.Paragraphs -> QuoteStep.Chapters(currentStep.book, currentStep.chapters)
                                    else -> QuoteStep.Books
                                }
                            }.padding(vertical = 8.dp, horizontal = 10.dp),
                        )
                    }
                    Text(
                        text = "[CANCEL]",
                        color = theme.secondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable(onClick = onCancel).padding(vertical = 8.dp),
                    )
                }
            }

            when (val s = step) {
                is QuoteStep.Books -> {
                    if (isLoading) {
                        CenterMessage("loading chapters…")
                    } else if (loadError != null) {
                        CenterMessage(loadError!!)
                    } else if (books.isEmpty()) {
                        CenterMessage("No books in your library yet.")
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(books, key = { it.id }) { book ->
                                PickRow(title = book.title, subtitle = book.author) { pendingBook = book }
                            }
                        }
                    }
                }
                is QuoteStep.Chapters -> {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(s.chapters, key = { it.index }) { chapter ->
                            PickRow(title = chapter.title, subtitle = null) {
                                val paragraphs = splitIntoQuotableParagraphs(chapter.text)
                                step = QuoteStep.Paragraphs(s.book, s.chapters, paragraphs)
                            }
                        }
                    }
                }
                is QuoteStep.Paragraphs -> {
                    if (s.paragraphs.isEmpty()) {
                        CenterMessage("NO QUOTABLE PASSAGES FOUND IN THIS CHAPTER")
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            items(s.paragraphs) { paragraph ->
                                Text(
                                    text = paragraph,
                                    color = theme.foreground,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 4,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onDone(PackageBookQuote(text = paragraph, bookTitle = s.book.title, author = s.book.author))
                                        }
                                        .padding(vertical = 12.dp),
                                )
                                Box(modifier = Modifier.fillMaxWidth().background(theme.dim.copy(alpha = 0.15f)).padding(top = 0.dp))
                            }
                        }
                    }
                }
            }
        }
        CRTOverlay()
    }
}

@Composable
private fun PickRow(title: String, subtitle: String?, onClick: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text = title, color = theme.foreground, fontSize = 13.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
        if (subtitle != null) {
            Text(text = subtitle, color = theme.dim, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
        }
    }
}

@Composable
private fun CenterMessage(text: String) {
    val theme = LocalTerminalTheme.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text(text = "│  $text", color = theme.dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

// Splits a chapter's rich text (heading markers, blockquote bars, etc.
// already applied by EpubParser) into reasonably quotable paragraphs —
// long enough to be a real passage, short enough to read as a quote, and
// not a heading/structural line.
private fun splitIntoQuotableParagraphs(text: String): List<String> {
    return text.split(Regex("\n{2,}"))
        .map { it.trim() }
        .filter { p ->
            p.length in 40..400 &&
                !p.startsWith("══") && !p.startsWith("──") && !p.startsWith("┌─") && !p.startsWith("└─") &&
                !p.startsWith("[[IMG:")
        }
}
