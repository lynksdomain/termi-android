package com.termi.android.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

// Kotlin equivalent of the SwiftData @Model WritingEvent, now Room-backed.
@Entity(tableName = "writing_events")
data class WritingEvent(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val date: Instant,
    val durationSeconds: Double,
    val wordsAdded: Int,
    val filename: String,
) {
    val year: Int
        get() = date.atZone(ZoneId.systemDefault()).year
}
