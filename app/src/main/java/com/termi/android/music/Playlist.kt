package com.termi.android.music

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

// Kotlin port of Playlist.swift — Room-backed (the iOS original is
// SwiftData). Songs are referenced by absolute file path, resolved against
// MusicLibraryManager.songs at display time, same "no relationship table"
// tradeoff the original makes.
@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val title: String,
    val playlistDescription: String = "",
    val songPaths: List<String> = emptyList(),
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
