package com.termi.android.stats

import com.termi.android.data.ListenEvent
import com.termi.android.data.ListenEventDao
import com.termi.android.music.Song
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Kotlin port of StatsManager.swift's Music/Listening half, Room-backed —
// same pattern as ReadingStatsManager/WritingStatsManager. A "session" here
// is just one song play (see ListenEvent), not a start/end timer: begin/end
// are driven by AudioPlayerEngine.onSongTransition (wired in TermiNavHost),
// firing once per track change/skip/queue-load rather than per pause.
class MusicStatsManager(
    private val dao: ListenEventDao,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    val events: StateFlow<List<ListenEvent>> =
        dao.observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())

    private var activeEvent: ListenEvent? = null

    // Closes any dangling session with whatever was already recorded for
    // it (defensive — the real close should come from endListening below,
    // called by AudioPlayerEngine with the real elapsed time just before
    // this fires), then starts tracking the new song.
    fun beginListening(song: Song) {
        activeEvent?.let { pending ->
            if (pending.secondsListened > 0.5) scope.launch { dao.update(pending) }
        }
        val event = ListenEvent(
            songTitle = song.title,
            artist = song.artist,
            album = song.album,
            playedAt = Instant.now(),
            songDurationSeconds = song.durationMs / 1000.0,
            secondsListened = 0.0,
        )
        activeEvent = event
        scope.launch { dao.insert(event) }
    }

    fun endListening(secondsListened: Double) {
        val event = activeEvent ?: return
        activeEvent = null
        if (secondsListened <= 0.5) return
        val updated = event.copy(secondsListened = maxOf(event.secondsListened, secondsListened))
        scope.launch { dao.update(updated) }
    }

    fun deleteEvents(forYear: Int?) {
        scope.launch {
            if (forYear == null) {
                dao.deleteAll()
            } else {
                val zone = ZoneId.systemDefault()
                val ids = events.value.filter { it.playedAt.atZone(zone).year == forYear }.map { it.id }
                dao.deleteByIds(ids)
            }
        }
    }

    fun availableYears(): List<Int> {
        val zone = ZoneId.systemDefault()
        return events.value.map { it.playedAt.atZone(zone).year }.toSortedSet().toList()
    }

    fun stats(forYear: Int?): MusicStats {
        val zone = ZoneId.systemDefault()
        val filtered = if (forYear == null) events.value else events.value.filter { it.playedAt.atZone(zone).year == forYear }
        val label = forYear?.toString() ?: "ALL TIME"

        val totalSeconds = filtered.sumOf { it.secondsListened }
        val uniqueSongs = filtered.map { it.songTitle to it.artist }.toSet().size
        val uniqueArtists = filtered.map { it.artist }.toSet().size
        val uniqueAlbums = filtered.map { it.album to it.artist }.toSet().size

        val days = run {
            val first = filtered.minOfOrNull { it.playedAt.epochSecond }
            val last = filtered.maxOfOrNull { it.playedAt.epochSecond }
            if (first != null && last != null) maxOf(1.0, (last - first) / 86400.0) else 1.0
        }
        val avgDailyMinutes = (totalSeconds / 60) / days

        val hourCounts = mutableMapOf<Int, Int>()
        val dayOfWeekCounts = mutableMapOf<DayOfWeek, Int>()
        for (e in filtered) {
            val zoned = e.playedAt.atZone(zone)
            hourCounts[zoned.hour] = (hourCounts[zoned.hour] ?: 0) + 1
            dayOfWeekCounts[zoned.dayOfWeek] = (dayOfWeekCounts[zoned.dayOfWeek] ?: 0) + 1
        }
        val mostActiveHour = hourCounts.entries.maxByOrNull { it.value }?.key
        val mostActiveDay = dayOfWeekCounts.entries.maxByOrNull { it.value }?.key
            ?.getDisplayName(TextStyle.FULL, Locale.US)

        // Longest run of consecutive calendar days with at least one play —
        // not just the current streak (matches iOS's longestStreak, distinct
        // from Reading/Writing's currentStreak).
        val distinctDays = filtered.map { it.playedAt.atZone(zone).toLocalDate() }.toSortedSet().toList()
        var longestStreak = 0
        var run = 0
        var prevDay: java.time.LocalDate? = null
        for (day in distinctDays) {
            run = if (prevDay != null && java.time.temporal.ChronoUnit.DAYS.between(prevDay, day) == 1L) run + 1 else 1
            longestStreak = maxOf(longestStreak, run)
            prevDay = day
        }

        // A play mostly skipped through (heard less than 30% of the track),
        // most frequently — surfaces songs the user keeps starting then
        // bailing on, which the overview/top-lists alone wouldn't show.
        val mostSkipped = filtered
            .filter { it.songDurationSeconds > 0 && it.secondsListened / it.songDurationSeconds < 0.3 }
            .groupBy { it.songTitle to it.artist }
            .maxByOrNull { it.value.size }
            ?.key?.first

        val songGroups = filtered.groupBy { it.songTitle to it.artist }
        val topSongs = songGroups.values.map { group ->
            val f = group[0]
            SongListenStats(title = f.songTitle, artist = f.artist, album = f.album, playCount = group.size, totalSeconds = group.sumOf { it.secondsListened })
        }.sortedByDescending { it.playCount }.take(10)

        val artistGroups = filtered.groupBy { it.artist }
        val topArtists = artistGroups.values.map { group ->
            val topSong = group.groupBy { it.songTitle }.maxByOrNull { it.value.size }?.key ?: ""
            ArtistListenStats(artist = group[0].artist, topSong = topSong, playCount = group.size, totalSeconds = group.sumOf { it.secondsListened })
        }.sortedByDescending { it.playCount }.take(10)

        val albumGroups = filtered.groupBy { it.album to it.artist }
        val topAlbums = albumGroups.values.map { group ->
            val f = group[0]
            AlbumListenStats(album = f.album, artist = f.artist, playCount = group.size, totalSeconds = group.sumOf { it.secondsListened })
        }.sortedByDescending { it.playCount }.take(10)

        return MusicStats(
            periodLabel = label,
            totalPlays = filtered.size,
            totalSeconds = totalSeconds,
            uniqueSongs = uniqueSongs,
            uniqueArtists = uniqueArtists,
            uniqueAlbums = uniqueAlbums,
            averageDailyMinutes = avgDailyMinutes,
            longestStreak = longestStreak,
            mostActiveHour = mostActiveHour,
            mostActiveDay = mostActiveDay,
            mostSkipped = mostSkipped,
            topSongs = topSongs,
            topArtists = topArtists,
            topAlbums = topAlbums,
        )
    }

    fun exportCsv(): String {
        val fmt = java.time.format.DateTimeFormatter.ISO_INSTANT
        val lines = mutableListOf("id,songTitle,artist,album,playedAt,songDurationSeconds,secondsListened")
        for (e in events.value) {
            val row = listOf(
                e.id.toString(), e.songTitle, e.artist, e.album,
                fmt.format(e.playedAt), e.songDurationSeconds.toString(), e.secondsListened.toString(),
            )
            lines.add(row.joinToString(",") { "\"$it\"" })
        }
        return lines.joinToString("\n")
    }
}
