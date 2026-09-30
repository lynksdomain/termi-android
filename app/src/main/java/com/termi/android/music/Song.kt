package com.termi.android.music

import java.io.File
import java.util.UUID

// Kotlin port of Song.swift. Plain data class, not a Room entity. Diverges
// from iOS here: `file` points at the song's REAL location wherever the
// user picked it from (not copied into an app-private folder) — see
// MusicLibraryManager's header comment for why. No embedded-artwork
// extraction (iOS doesn't do this either — its album art is synthetic/
// generated, out of scope here).
data class Song(
    val id: UUID = UUID.randomUUID(),
    val file: File,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String = "",
    val genre: String = "",
    val year: String = "",
    val durationMs: Long,
    val trackNumber: Int? = null,
)
