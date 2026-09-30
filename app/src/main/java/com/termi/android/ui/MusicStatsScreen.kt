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
import com.termi.android.stats.AlbumListenStats
import com.termi.android.stats.ArtistListenStats
import com.termi.android.stats.MusicStats
import com.termi.android.stats.MusicStatsManager
import com.termi.android.stats.SongListenStats
import com.termi.android.theme.LocalTerminalTheme

// Kotlin/Compose port of StatsView.swift's Music/Listening half — same
// header/year-selector/card-grid shell as ReadingStatsScreen/
// WritingStatsScreen, plus an INSIGHTS section (most-skipped song) that has
// no Reading/Writing analog on iOS either.
@Composable
fun MusicStatsScreen(
    manager: MusicStatsManager,
    onBack: (() -> Unit)? = null,
) {
    val theme = LocalTerminalTheme.current
    val events by manager.events.collectAsState()
    var selectedYear by remember { mutableStateOf<Int?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val years = remember(events) { manager.availableYears() }
    val displayStats = remember(events, selectedYear) { manager.stats(forYear = selectedYear) }

    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            TerminalStatsHeaderBar(
                title = "LISTENING STATS",
                showDelete = events.isNotEmpty(),
                onDelete = { showDeleteConfirm = true },
                onBack = onBack,
            )
            TerminalYearSelector(years = years, selectedYear = selectedYear, onSelect = { selectedYear = it })
            if (events.isEmpty()) {
                EmptyMusicStatsState()
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item { OverviewSection(displayStats) }
                    item { TerminalSectionHeader(title = "TOP SONGS") }
                    if (displayStats.topSongs.isEmpty()) {
                        item { EmptyRow("NO DATA YET") }
                    } else {
                        itemsIndexed(displayStats.topSongs) { index, song -> TopSongRow(index, song) }
                    }
                    item { TerminalSectionHeader(title = "TOP ARTISTS") }
                    if (displayStats.topArtists.isEmpty()) {
                        item { EmptyRow("NO DATA YET") }
                    } else {
                        itemsIndexed(displayStats.topArtists) { index, artist -> TopArtistRow(index, artist) }
                    }
                    item { TerminalSectionHeader(title = "TOP ALBUMS") }
                    if (displayStats.topAlbums.isEmpty()) {
                        item { EmptyRow("NO DATA YET") }
                    } else {
                        itemsIndexed(displayStats.topAlbums) { index, album -> TopAlbumRow(index, album) }
                    }
                    if (displayStats.mostSkipped != null) {
                        item { TerminalSectionHeader(title = "INSIGHTS") }
                        item { InsightRow(label = "MOST SKIPPED", value = displayStats.mostSkipped) }
                    }
                    item {
                        TerminalDataBackupSection(eventsCount = events.size) {
                            // CSV export wiring (share sheet) comes with persistence.
                        }
                    }
                    item { Spacer(modifier = Modifier.height(64.dp)) }
                }
            }
        }
        CRTOverlay()
    }

    if (showDeleteConfirm) {
        ActionOverlayScaffold(onDismiss = { showDeleteConfirm = false }) {
            ActionConfirmPanel(
                title = if (selectedYear == null) "╔═[ DELETE ALL HISTORY ]" else "╔═[ DELETE $selectedYear HISTORY ]",
                message = if (selectedYear == null)
                    "This will permanently erase your entire listening history. This cannot be undone."
                else
                    "This will permanently erase all listening data for $selectedYear. This cannot be undone.",
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

@Composable
private fun EmptyMusicStatsState() {
    val theme = LocalTerminalTheme.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text(
            text = "║  NO LISTENING DATA YET — PLAY SOME MUSIC",
            color = theme.dim,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
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
private fun OverviewSection(stats: MusicStats) {
    val items = buildList {
        add(TerminalStat(label = "PLAYS", value = "${stats.totalPlays}"))
        add(TerminalStat(label = "TIME LISTENED", value = formatSeconds(stats.totalSeconds)))
        add(TerminalStat(label = "SONGS", value = "${stats.uniqueSongs}"))
        add(TerminalStat(label = "ARTISTS", value = "${stats.uniqueArtists}"))
        add(TerminalStat(label = "ALBUMS", value = "${stats.uniqueAlbums}"))
        add(TerminalStat(label = "DAILY AVG", value = "${stats.averageDailyMinutes.toInt()} min"))
        add(TerminalStat(label = "LONGEST STREAK", value = "${stats.longestStreak}d"))
        stats.mostActiveHour?.let { add(TerminalStat(label = "PEAK HOUR", value = formatHour(it))) }
        stats.mostActiveDay?.let { add(TerminalStat(label = "PEAK DAY", value = it.uppercase())) }
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
private fun InsightRow(label: String, value: String) {
    val theme = LocalTerminalTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = "$label:",
            color = theme.dim,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            color = theme.foreground,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TopSongRow(index: Int, song: SongListenStats) {
    val theme = LocalTerminalTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
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
                text = song.title,
                color = theme.foreground,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            Text(
                text = song.artist,
                color = theme.dim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }
        Text(
            text = "${song.playCount}x",
            color = theme.dim,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun TopArtistRow(index: Int, artist: ArtistListenStats) {
    val theme = LocalTerminalTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
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
                text = artist.artist,
                color = theme.foreground,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            Text(
                text = "Top: ${artist.topSong}",
                color = theme.dim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }
        Text(
            text = "${artist.playCount}x",
            color = theme.dim,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun TopAlbumRow(index: Int, album: AlbumListenStats) {
    val theme = LocalTerminalTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
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
                text = album.album,
                color = theme.foreground,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            Text(
                text = album.artist,
                color = theme.dim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }
        Text(
            text = "${album.playCount}x",
            color = theme.dim,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}
