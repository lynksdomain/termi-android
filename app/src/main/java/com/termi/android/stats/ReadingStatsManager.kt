package com.termi.android.stats

import com.termi.android.data.ReadingEvent
import com.termi.android.data.ReadingEventDao
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Kotlin port of ReadingStatsManager.swift, now backed by Room instead of
// SwiftData — see WritingStatsManager for the same pattern. beginReading/
// endReading take plain filename/title/author/page params instead of a
// `Book` object; the Book/EPUB model hasn't been ported yet.
class ReadingStatsManager(
    private val dao: ReadingEventDao,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    val events: StateFlow<List<ReadingEvent>> =
        dao.observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())

    private var sessionStart: Instant? = null
    private var sessionStartPage: Int = 0
    private var sessionBookFilename: String = ""
    private var sessionBookTitle: String = ""
    private var sessionBookAuthor: String = ""

    fun seedEvent(event: ReadingEvent) {
        scope.launch { dao.insert(event) }
    }

    fun beginReading(filename: String, title: String, author: String, currentPage: Int) {
        sessionBookFilename = filename
        sessionBookTitle = title
        sessionBookAuthor = author
        sessionStart = Instant.now()
        sessionStartPage = currentPage
    }

    fun endReading(currentPage: Int, currentChapter: Int) {
        val start = sessionStart ?: return
        val pagesRead = maxOf(0, currentPage - sessionStartPage)
        val end = Instant.now()
        val duration = end.epochSecond - start.epochSecond
        if (duration <= 5) {
            sessionStart = null
            return
        }

        val event = ReadingEvent(
            bookFilename = sessionBookFilename,
            bookTitle = sessionBookTitle,
            bookAuthor = sessionBookAuthor,
            sessionStart = start,
            sessionEnd = end,
            pagesRead = pagesRead,
            chapterIndex = currentChapter,
        )
        scope.launch { dao.insert(event) }

        sessionStart = null
        sessionStartPage = 0
    }

    fun deleteEvents(forYear: Int?) {
        scope.launch {
            if (forYear == null) {
                dao.deleteAll()
            } else {
                val zone = ZoneId.systemDefault()
                val ids = events.value.filter { it.sessionStart.atZone(zone).year == forYear }.map { it.id }
                dao.deleteByIds(ids)
            }
        }
    }

    fun currentStreak(): Int {
        val zone = ZoneId.systemDefault()
        val days = events.value.map { it.sessionStart.atZone(zone).toLocalDate() }
            .toSortedSet()
            .toList()
            .sortedDescending()
        val latest = days.firstOrNull() ?: return 0
        val today = Instant.now().atZone(zone).toLocalDate()
        val yesterday = today.minusDays(1)
        if (latest != today && latest != yesterday) return 0
        var streak = 1
        var prev = latest
        for (day in days.drop(1)) {
            val diff = java.time.temporal.ChronoUnit.DAYS.between(day, prev)
            if (diff == 1L) {
                streak += 1
                prev = day
            } else {
                break
            }
        }
        return streak
    }

    fun availableYears(): List<Int> {
        val zone = ZoneId.systemDefault()
        return events.value.map { it.sessionStart.atZone(zone).year }.toSortedSet().toList()
    }

    fun stats(forYear: Int?): ReadingStats {
        val zone = ZoneId.systemDefault()
        val filtered = if (forYear == null) {
            events.value
        } else {
            events.value.filter { it.sessionStart.atZone(zone).year == forYear }
        }
        val label = forYear?.toString() ?: "ALL TIME"

        val totalPages = filtered.sumOf { it.pagesRead }
        val totalSeconds = filtered.sumOf { it.sessionDurationSeconds }
        val uniqueBooks = filtered.map { it.bookFilename }.toSet().size
        val uniqueAuthors = filtered.map { it.bookAuthor }.toSet().size

        val days = run {
            val first = filtered.minOfOrNull { it.sessionStart.epochSecond }
            val last = filtered.maxOfOrNull { it.sessionEnd.epochSecond }
            if (first != null && last != null) {
                maxOf(1.0, (last - first) / 86400.0)
            } else {
                1.0
            }
        }
        val avgDailyMinutes = (totalSeconds / 60) / days

        val longestSession = filtered.maxOfOrNull { it.sessionDurationSeconds } ?: 0.0

        val hourCounts = mutableMapOf<Int, Int>()
        for (e in filtered) {
            val h = e.sessionStart.atZone(zone).hour
            hourCounts[h] = (hourCounts[h] ?: 0) + 1
        }
        val mostActiveHour = hourCounts.entries.maxByOrNull { it.value }?.key

        val dayFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.US).withZone(zone)
        val dayCounts = mutableMapOf<String, Int>()
        for (e in filtered) {
            val d = dayFmt.format(e.sessionStart)
            dayCounts[d] = (dayCounts[d] ?: 0) + e.pagesRead
        }
        val mostReadDay = dayCounts.entries.maxByOrNull { it.value }?.key

        val bookGroups = filtered.groupBy { it.bookFilename }
        val topBooks = bookGroups.values.map { group ->
            val f = group[0]
            BookReadStats(
                filename = f.bookFilename,
                title = f.bookTitle,
                author = f.bookAuthor,
                sessions = group.size,
                totalPages = group.sumOf { it.pagesRead },
                totalSeconds = group.sumOf { it.sessionDurationSeconds },
            )
        }.sortedByDescending { it.totalPages }.take(10)

        val authorGroups = filtered.groupBy { it.bookAuthor }
        val topAuthors = authorGroups.values.map { group ->
            val top = group.groupBy { it.bookFilename }
                .maxByOrNull { it.value.size }?.value?.firstOrNull()?.bookTitle ?: ""
            AuthorReadStats(
                author = group[0].bookAuthor,
                topBook = top,
                sessions = group.size,
                totalPages = group.sumOf { it.pagesRead },
                totalSeconds = group.sumOf { it.sessionDurationSeconds },
            )
        }.sortedByDescending { it.totalPages }.take(10)

        return ReadingStats(
            periodLabel = label,
            totalSessions = filtered.size,
            totalPages = totalPages,
            totalSeconds = totalSeconds,
            uniqueBooks = uniqueBooks,
            uniqueAuthors = uniqueAuthors,
            averageDailyMinutes = avgDailyMinutes,
            longestSession = longestSession,
            mostActiveHour = mostActiveHour,
            topBooks = topBooks,
            topAuthors = topAuthors,
            mostReadDay = mostReadDay,
        )
    }

    fun exportCsv(): String {
        val fmt = DateTimeFormatter.ISO_INSTANT
        val lines = mutableListOf("id,bookFilename,bookTitle,bookAuthor,sessionStart,sessionEnd,pagesRead,chapterIndex")
        for (e in events.value) {
            val row = listOf(
                e.id.toString(), e.bookFilename, e.bookTitle, e.bookAuthor,
                fmt.format(e.sessionStart), fmt.format(e.sessionEnd),
                e.pagesRead.toString(), e.chapterIndex.toString(),
            )
            lines.add(row.joinToString(",") { "\"$it\"" })
        }
        return lines.joinToString("\n")
    }
}
