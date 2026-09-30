package com.termi.android.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termi.android.books.BookFolderManager
import com.termi.android.settings.AppSettings
import com.termi.android.theme.LocalTerminalTheme
import com.termi.android.theme.TerminalTheme
import com.termi.android.writing.WritingFolderManager
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

// Kotlin/Compose port of BootSequenceView.swift — a real terminal-style
// typing animation, not the native splash-screen API (which can't show
// custom animated text). Line text, exact absolute reveal timings, and
// color rules are ported 1:1 from the Swift source so this matches iOS,
// not just approximates it. Purely cosmetic: never blocks on real data
// actually finishing loading (MainActivity keeps TermiNavHost mounted
// underneath this the whole time — see its comment).
private data class MediaCounts(val songs: Int, val books: Int, val journalEntries: Int)

private fun scanCounts(context: Context): MediaCounts {
    val musicPrefs = context.getSharedPreferences("termi_music_library", Context.MODE_PRIVATE)
    val songCount = try {
        JSONArray(musicPrefs.getString("songPaths", null) ?: "[]").length()
    } catch (e: Exception) {
        0
    }
    val bookCount = try { BookFolderManager.scanForEpubs(context).size } catch (e: Exception) { 0 }
    val journalCount = try { WritingFolderManager.scanForDocuments(context).size } catch (e: Exception) { 0 }
    return MediaCounts(songCount, bookCount, journalCount)
}

// Consecutive calendar days the app has been launched on (not listening/
// reading activity) — mirrors BootSequenceView.swift's `updateAppOpenStreak`
// UserDefaults logic 1:1, kept as plain SharedPreferences since it's a tiny,
// boot-screen-only concern.
private fun updateAppOpenStreak(context: Context): Int {
    val prefs = context.getSharedPreferences("termi_boot", Context.MODE_PRIVATE)
    val today = LocalDate.now()
    val lastOpenEpochDay = prefs.getLong("lastAppOpenEpochDay", Long.MIN_VALUE)
    if (lastOpenEpochDay == Long.MIN_VALUE) {
        prefs.edit().putInt("appOpenStreak", 1).putLong("lastAppOpenEpochDay", today.toEpochDay()).apply()
        return 1
    }
    val daysSince = today.toEpochDay() - lastOpenEpochDay
    val previousStreak = prefs.getInt("appOpenStreak", 0)
    val newStreak = when (daysSince) {
        0L -> maxOf(previousStreak, 1)
        1L -> previousStreak + 1
        else -> 1
    }
    prefs.edit().putInt("appOpenStreak", newStreak).putLong("lastAppOpenEpochDay", today.toEpochDay()).apply()
    return newStreak
}

private fun pluralS(count: Int) = if (count == 1) "" else "S"

// (text, absolute seconds from sequence start) — matches
// makeFirstLaunchLines/makeReturnLaunchLines in BootSequenceView.swift.
private fun firstLaunchLines(counts: MediaCounts): List<Pair<String, Double>> {
    val musicStatus = if (counts.songs > 0) "[ ${counts.songs} SONG${pluralS(counts.songs)} FOUND ]" else "[ EMPTY ]"
    val bookStatus = if (counts.books > 0) "[ ${counts.books} BOOK${pluralS(counts.books)} FOUND ]" else "[ EMPTY ]"
    val journalStatus = if (counts.journalEntries > 0) {
        "[ ${counts.journalEntries} ENTR${if (counts.journalEntries == 1) "Y" else "IES"} FOUND ]"
    } else {
        "[ EMPTY ]"
    }

    val lines = mutableListOf(
        "TERMI OS v1.0" to 0.0,
        "INITIALIZING..." to 0.3,
        "" to 0.55,
        "> LOADING THEME ENGINE............. [ OK ]" to 0.65,
        "> CHECKING MUSIC LIBRARY........... $musicStatus" to 1.1,
        "> CHECKING BOOK LIBRARY............ $bookStatus" to 1.6,
        "> CHECKING JOURNAL................. $journalStatus" to 2.1,
        "> CALIBRATING PHOSPHOR GLOW........ [ OK ]" to 2.6,
        "" to 3.0,
    )
    if (counts.songs == 0 && counts.books == 0) {
        lines += listOf(
            "WARNING: NO MEDIA DETECTED." to 3.2,
            "" to 3.5,
            "TO BEGIN, IMPORT A MUSIC FILE OR EPUB." to 3.7,
        )
    } else {
        lines += "SYSTEM READY." to 3.2
    }
    return lines
}

private fun returnLaunchLines(counts: MediaCounts, appOpenStreak: Int): List<Pair<String, Double>> {
    val hour = LocalTime.now().hour
    val greeting = when {
        hour in 5..11 -> "GOOD MORNING."
        hour in 12..17 -> "GOOD AFTERNOON."
        else -> "GOOD EVENING."
    }
    val musicLine = if (counts.songs > 0) "> MUSIC LIBRARY: ${counts.songs} SONG${pluralS(counts.songs)}." else "> MUSIC LIBRARY: EMPTY."
    val bookLine = if (counts.books > 0) "> BOOK LIBRARY:  ${counts.books} BOOK${pluralS(counts.books)}." else "> BOOK LIBRARY:  EMPTY."
    val journalLine = if (counts.journalEntries > 0) {
        "> JOURNAL:       ${counts.journalEntries} ENTR${if (counts.journalEntries == 1) "Y" else "IES"}."
    } else {
        "> JOURNAL:       EMPTY."
    }
    val streakLine = "> STREAK:        $appOpenStreak DAY${pluralS(appOpenStreak)}."

    return listOf(
        "TERMI OS v1.0" to 0.0,
        greeting to 0.25,
        "" to 0.45,
        musicLine to 0.55,
        bookLine to 0.75,
        journalLine to 0.95,
        streakLine to 1.15,
        "" to 1.35,
        "READY." to 1.45,
    )
}

private fun lineColor(text: String, theme: TerminalTheme): Color = when {
    text.contains("[ OK ]") || text.contains("FOUND ]") || text.contains("COMPLETE") ||
        text == "SYSTEM READY." || text == "READY." -> theme.accent
    text.contains("[ EMPTY ]") || text.contains(": EMPTY.") -> theme.dim
    text.startsWith("WARNING") -> theme.foreground
    text.startsWith(">") -> theme.foreground.copy(alpha = 0.8f)
    else -> theme.foreground
}

@Composable
fun BootSequenceScreen(appSettings: AppSettings, onComplete: () -> Unit) {
    val theme = LocalTerminalTheme.current
    val context = LocalContext.current

    var isFirstLaunch by remember { mutableStateOf<Boolean?>(null) }
    var lines by remember { mutableStateOf<List<Pair<String, Double>>>(emptyList()) }
    var visibleCount by remember { mutableStateOf(0) }
    var showButtons by remember { mutableStateOf(false) }
    var canTapToSkip by remember { mutableStateOf(false) }
    var cursorVisible by remember { mutableStateOf(true) }

    fun finish() {
        appSettings.setHasCompletedBoot(true)
        onComplete()
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            cursorVisible = !cursorVisible
        }
    }

    LaunchedEffect(Unit) {
        val firstLaunch = !appSettings.hasCompletedBootOnce()
        val streak = updateAppOpenStreak(context)
        val counts = withContext(Dispatchers.IO) { scanCounts(context) }
        isFirstLaunch = firstLaunch
        val builtLines = if (firstLaunch) firstLaunchLines(counts) else returnLaunchLines(counts, streak)
        lines = builtLines

        coroutineScope {
            launch {
                var elapsedSeconds = 0.0
                for (index in builtLines.indices) {
                    val target = builtLines[index].second
                    delay(((target - elapsedSeconds) * 1000).toLong().coerceAtLeast(0))
                    elapsedSeconds = target
                    visibleCount = index + 1
                }
                if (firstLaunch) {
                    showButtons = true
                }
                // Return launch never auto-completes — same as iOS, it only
                // ever becomes tap-to-skip (below); the user always taps.
            }
            if (!firstLaunch) {
                launch {
                    delay(400)
                    canTapToSkip = true
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = canTapToSkip,
            ) { finish() },
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
        ) {
            lines.take(visibleCount).forEachIndexed { index, (text, _) ->
                val isLast = index == visibleCount - 1
                Row {
                    Text(
                        text = text,
                        color = lineColor(text, theme),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        style = TextStyle(shadow = Shadow(color = theme.accent.copy(alpha = 0.6f), blurRadius = 8f)),
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                    if (isLast && !showButtons) {
                        Text(
                            text = if (cursorVisible) "█" else " ",
                            color = theme.accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }

            if (canTapToSkip) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "[ TAP TO BEGIN ]",
                    color = theme.dim.copy(alpha = if (cursorVisible) 1f else 0.4f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            if (isFirstLaunch == true && showButtons) {
                Spacer(modifier = Modifier.height(24.dp))
                BootActionButton(label = "[ IMPORT MEDIA ]", onClick = { finish() })
                Text(
                    text = "MP3 · M4A · AAC · WAV · AIFF · FLAC · EPUB",
                    color = theme.dim.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "[ SKIP - ENTER TERMI ]",
                    color = theme.dim,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable(onClick = { finish() }).padding(vertical = 10.dp),
                )
            }
        }

        CRTOverlay()
    }
}

@Composable
private fun BootActionButton(label: String, onClick: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Text(
        text = label,
        color = Color.Black,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(theme.accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}
