package com.termi.android.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

// Kotlin/Room equivalent of iOS's SwiftData ListenEvent — one row per song
// play, not a session with a start/end like ReadingEvent/WritingEvent
// (a song play is already a natural unit; there's no "chapter"-style
// sub-progress to bound it by).
@Entity(tableName = "listen_events")
data class ListenEvent(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val songTitle: String,
    val artist: String,
    val album: String,
    val playedAt: Instant,
    val songDurationSeconds: Double,
    val secondsListened: Double,
)
