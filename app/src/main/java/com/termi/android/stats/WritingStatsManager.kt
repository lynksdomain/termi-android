package com.termi.android.stats

import com.termi.android.data.WritingEvent
import com.termi.android.data.WritingEventDao
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Kotlin port of WritingStatsManager.swift, now backed by Room instead of
// SwiftData. Session lifecycle + stats math are ported 1:1; writes go through
// the DAO on `scope` and `events` reflects the DB via a live Flow, so the UI
// updates automatically after any insert/delete.
class WritingStatsManager(
    private val dao: WritingEventDao,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    val events: StateFlow<List<WritingEvent>> =
        dao.observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())

    private var sessionStart: Instant? = null
    private var sessionStartWordCount: Int = 0
    private var sessionFilename: String = ""

    // Seed hook for sample/demo data — goes through the same DAO insert path
    // real events use, so it's not a special code path.
    fun seedEvent(event: WritingEvent) {
        scope.launch { dao.insert(event) }
    }

    fun beginSession(filename: String, currentWordCount: Int) {
        sessionStart = Instant.now()
        sessionStartWordCount = currentWordCount
        sessionFilename = filename
    }

    fun endSession(currentWordCount: Int, onComplete: (WritingSessionSummary?) -> Unit = {}) {
        val start = sessionStart
        if (start == null) {
            onComplete(null)
            return
        }
        val durationSeconds = Instant.now().epochSecond - start.epochSecond
        sessionStart = null
        if (durationSeconds < 10) {
            onComplete(null)
            return
        }

        val wordsAdded = maxOf(0, currentWordCount - sessionStartWordCount)
        val event = WritingEvent(
            date = start,
            durationSeconds = durationSeconds.toDouble(),
            wordsAdded = wordsAdded,
            filename = sessionFilename,
        )
        val filename = sessionFilename
        scope.launch {
            dao.insert(event)
            val zone = ZoneId.systemDefault()
            val todayStart = Instant.now().atZone(zone).toLocalDate()
            val current = events.value
            val todayEvents = current.filter { it.date.atZone(zone).toLocalDate() == todayStart }
            val totalMinutes = (todayEvents.sumOf { it.durationSeconds } / 60).toInt()
            val totalWords = todayEvents.sumOf { it.wordsAdded }
            onComplete(
                WritingSessionSummary(
                    minutesWritten = totalMinutes,
                    wordsAdded = totalWords,
                    filename = filename,
                    streak = currentStreak(),
                ),
            )
        }
    }

    fun currentStreak(): Int {
        val zone = ZoneId.systemDefault()
        val today = Instant.now().atZone(zone).toLocalDate()
        val writeDays = events.value.map { it.date.atZone(zone).toLocalDate() }
            .toSortedSet()
            .toList()
            .sortedDescending()
        val latest = writeDays.firstOrNull() ?: return 0
        val daysSinceLast = java.time.temporal.ChronoUnit.DAYS.between(latest, today)
        if (daysSinceLast > 1) return 0
        var streak = 1
        var prev = latest
        for (day in writeDays.drop(1)) {
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

    fun availableYears(): List<Int> = events.value.map { it.year }.toSortedSet().toList()

    fun stats(forYear: Int?): WritingStats {
        val filtered = if (forYear == null) events.value else events.value.filter { it.year == forYear }
        val label = forYear?.toString() ?: "ALL TIME"

        val totalWords = filtered.sumOf { it.wordsAdded }
        val totalMinutes = (filtered.sumOf { it.durationSeconds } / 60).toInt()
        val totalSessions = filtered.size
        val avgWords = if (totalSessions > 0) totalWords / totalSessions else 0

        val wordsByDoc = mutableMapOf<String, Int>()
        for (e in filtered) wordsByDoc[e.filename] = (wordsByDoc[e.filename] ?: 0) + e.wordsAdded
        val topDocuments = wordsByDoc.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { DocumentWordStats(filename = it.key, totalWords = it.value) }

        val recentSessions = filtered.sortedByDescending { it.date }.take(10)

        return WritingStats(
            periodLabel = label,
            totalWords = totalWords,
            totalMinutes = totalMinutes,
            totalSessions = totalSessions,
            avgWordsPerSession = avgWords,
            documentCount = wordsByDoc.size,
            topDocuments = topDocuments,
            recentSessions = recentSessions,
        )
    }

    fun deleteEvents(forYear: Int?) {
        scope.launch {
            if (forYear == null) {
                dao.deleteAll()
            } else {
                val ids = events.value.filter { it.year == forYear }.map { it.id }
                dao.deleteByIds(ids)
            }
        }
    }

    fun exportCsv(): String {
        val fmt = DateTimeFormatter.ISO_INSTANT
        val lines = mutableListOf("id,date,durationSeconds,wordsAdded,filename")
        for (e in events.value) {
            val row = listOf(
                e.id.toString(),
                fmt.format(e.date),
                e.durationSeconds.toInt().toString(),
                e.wordsAdded.toString(),
                e.filename,
            )
            lines.add(row.joinToString(",") { "\"$it\"" })
        }
        return lines.joinToString("\n")
    }
}

data class WritingSessionSummary(
    val minutesWritten: Int,
    val wordsAdded: Int,
    val filename: String,
    val streak: Int,
)
