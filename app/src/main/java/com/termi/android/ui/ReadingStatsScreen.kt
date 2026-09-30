package com.termi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termi.android.stats.AuthorReadStats
import com.termi.android.stats.BookReadStats
import com.termi.android.stats.ReadingStats
import com.termi.android.stats.ReadingStatsManager
import com.termi.android.theme.LocalTerminalTheme

@Composable
fun ReadingStatsScreen(
    manager: ReadingStatsManager,
    onBack: (() -> Unit)? = null,
) {
    val theme = LocalTerminalTheme.current
    val events by manager.events.collectAsState()
    var selectedYear by remember { mutableStateOf<Int?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val years = remember(events) { manager.availableYears() }
    val displayStats = remember(events, selectedYear) { manager.stats(forYear = selectedYear) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            TerminalStatsHeaderBar(
                title = "READING STATS",
                showDelete = events.isNotEmpty(),
                onDelete = { showDeleteConfirm = true },
                onBack = onBack,
            )
            TerminalYearSelector(years = years, selectedYear = selectedYear, onSelect = { selectedYear = it })
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item { OverviewSection(displayStats) }
                item { TerminalSectionHeader(title = "TOP BOOKS") }
                if (displayStats.topBooks.isEmpty()) {
                    item { EmptyRow("NO DATA YET") }
                } else {
                    itemsIndexed(displayStats.topBooks) { index, book -> TopBookRow(index, book) }
                }
                item { TerminalSectionHeader(title = "TOP AUTHORS") }
                if (displayStats.topAuthors.isEmpty()) {
                    item { EmptyRow("NO DATA YET") }
                } else {
                    itemsIndexed(displayStats.topAuthors) { index, author -> TopAuthorRow(index, author) }
                }
                item {
                    TerminalDataBackupSection(eventsCount = events.size) {
                        // CSV export wiring (share sheet) comes with persistence.
                    }
                }
                item { Spacer(modifier = Modifier.height(64.dp)) }
            }
        }
        CRTOverlay()
    }

    if (showDeleteConfirm) {
        ActionOverlayScaffold(onDismiss = { showDeleteConfirm = false }) {
            ActionConfirmPanel(
                title = if (selectedYear == null) "╔═[ DELETE ALL HISTORY ]" else "╔═[ DELETE $selectedYear HISTORY ]",
                message = if (selectedYear == null)
                    "This will permanently erase your entire reading history. This cannot be undone."
                else
                    "This will permanently erase all reading data for $selectedYear. This cannot be undone.",
                confirmLabel = "[ YES, DELETE ]",
                onCancel = { showDeleteConfirm = false },
                onConfirm = {
                    manager.deleteEvents(forYear = selectedYear)
                    if (selectedYear != null) selectedYear = null
                    showDeleteConfirm = false
                },
            )
        }
    }
}

private fun formatSeconds(s: Double): String {
    val h = s.toInt() / 3600
    val m = (s.toInt() % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun formatHour(h: Int): String {
    val suffix = if (h < 12) "AM" else "PM"
    val display = if (h == 0) 12 else if (h > 12) h - 12 else h
    return "$display:00 $suffix"
}

@Composable
private fun OverviewSection(stats: ReadingStats) {
    val items = buildList {
        add(TerminalStat(label = "SESSIONS", value = "${stats.totalSessions}"))
        add(TerminalStat(label = "PAGES READ", value = "${stats.totalPages}"))
        add(TerminalStat(label = "TIME READ", value = formatSeconds(stats.totalSeconds)))
        add(TerminalStat(label = "BOOKS", value = "${stats.uniqueBooks}"))
        add(TerminalStat(label = "AUTHORS", value = "${stats.uniqueAuthors}"))
        add(TerminalStat(label = "DAILY AVG", value = "${stats.averageDailyMinutes.toInt()} min"))
        add(TerminalStat(label = "LONGEST SES.", value = formatSeconds(stats.longestSession)))
        stats.mostActiveHour?.let { add(TerminalStat(label = "PEAK HOUR", value = formatHour(it))) }
    }
    Column {
        TerminalSectionHeader(title = "OVERVIEW — ${stats.periodLabel}")
        TerminalStatCardGrid(stats = items)
    }
}

@Composable
private fun EmptyRow(label: String) {
    val theme = LocalTerminalTheme.current
    Text(
        text = "║  $label",
        color = theme.dim,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun TopBookRow(index: Int, book: BookReadStats) {
    val theme = LocalTerminalTheme.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                text = "%2d.".format(index + 1),
                color = theme.dim,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(24.dp),
            )
            Text(
                text = book.title,
                color = theme.foreground,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            Text(
                text = "${book.totalPages} pg",
                color = theme.dim,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun TopAuthorRow(index: Int, author: AuthorReadStats) {
    val theme = LocalTerminalTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = "%2d.".format(index + 1),
            color = theme.dim,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(24.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                text = author.author,
                color = theme.foreground,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            Text(
                text = author.topBook,
                color = theme.dim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }
        Text(
            text = "${author.totalPages} pg",
            color = theme.dim,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}
