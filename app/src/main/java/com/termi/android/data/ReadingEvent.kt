package com.termi.android.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

// Kotlin equivalent of the SwiftData @Model ReadingEvent, now Room-backed.
@Entity(tableName = "reading_events")
data class ReadingEvent(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val bookFilename: String,
    val bookTitle: String,
    val bookAuthor: String,
    val sessionStart: Instant,
    val sessionEnd: Instant,
    val pagesRead: Int,
    val chapterIndex: Int,
) {
    val sessionDurationSeconds: Double
        get() = (sessionEnd.epochSecond - sessionStart.epochSecond).toDouble()
}
