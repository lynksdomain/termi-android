package com.termi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termi.android.theme.LocalTerminalTheme
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.util.Locale

@Composable
fun WritingStatsScreen(
    manager: com.termi.android.stats.WritingStatsManager,
    onBack: (() -> Unit)? = null,
) {
    val theme = LocalTerminalTheme.current
    val events by manager.events.collectAsState()
    var selectedYear by remember { mutableStateOf<Int?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val years = remember(events) { manager.availableYears() }
    val displayStats = remember(events, selectedYear) { manager.stats(forYear = selectedYear) }
    val streak = remember(events) { manager.currentStreak() }

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
                title = "WRITING STATS",
                showDelete = events.isNotEmpty(),
                onDelete = { showDeleteConfirm = true },
                onBack = onBack,
            )
            TerminalYearSelector(years = years, selectedYear = selectedYear, onSelect = { selectedYear = it })
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item { OverviewSection(displayStats, streak) }
                if (displayStats.topDocuments.isNotEmpty()) {
                    item { TerminalSectionHeader(title = "TOP DOCUMENTS") }
                    itemsIndexed(displayStats.topDocuments) { index, doc ->
                        TopDocumentRow(index = index, filename = doc.filename, words = doc.totalWords)
                    }
                }
                if (displayStats.recentSessions.isNotEmpty()) {
                    item { TerminalSectionHeader(title = "RECENT SESSIONS") }
                    items(displayStats.recentSessions) { event ->
                        RecentSessionRow(event)
                    }
                }
                item {
                    TerminalDataBackupSection(eventsCount = events.size) {
                        // CSV export wiring (share sheet equivalent) comes with persistence.
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
                    "This will permanently erase your entire writing history. This cannot be undone."
                else
                    "This will permanently erase all writing data for $selectedYear. This cannot be undone.",
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

private fun formatMinutes(minutes: Int): String {
    if (minutes < 60) return "${minutes}m"
    return "${minutes / 60}h ${minutes % 60}m"
}

private fun formatted(n: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(n)

@Composable
private fun OverviewSection(stats: com.termi.android.stats.WritingStats, streak: Int) {
    Column {
        TerminalSectionHeader(title = "OVERVIEW — ${stats.periodLabel}")
        TerminalStatCardGrid(
            stats = listOf(
                TerminalStat(label = "WORDS WRITTEN", value = formatted(stats.totalWords)),
                TerminalStat(label = "TIME WRITING", value = formatMinutes(stats.totalMinutes)),
                TerminalStat(label = "SESSIONS", value = "${stats.totalSessions}"),
                TerminalStat(label = "WRITING STREAK", value = if (streak > 0) "${streak}d" else "—"),
                TerminalStat(label = "AVG WORDS/SESSION", value = formatted(stats.avgWordsPerSession)),
                TerminalStat(label = "DOCUMENTS", value = "${stats.documentCount}"),
            ),
        )
    }
}

@Composable
private fun TopDocumentRow(index: Int, filename: String, words: Int) {
    val theme = LocalTerminalTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = "[${index + 1}]",
            color = theme.dim,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(end = 10.dp),
        )
        Text(
            text = filename,
            color = theme.foreground,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${formatted(words)} words",
            color = theme.secondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun RecentSessionRow(event: com.termi.android.data.WritingEvent) {
    val theme = LocalTerminalTheme.current
    val dateFmt = remember {
        DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US).withZone(ZoneId.systemDefault())
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.filename,
                color = theme.foreground,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            Text(
                text = dateFmt.format(event.date),
                color = theme.dim,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Column {
            Text(
                text = "+${event.wordsAdded} words",
                color = theme.secondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = formatMinutes((event.durationSeconds / 60).toInt()),
                color = theme.dim,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
