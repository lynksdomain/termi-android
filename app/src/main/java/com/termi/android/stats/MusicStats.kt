package com.termi.android.stats

import java.util.UUID

data class SongListenStats(
    val id: UUID = UUID.randomUUID(),
    val title: String,
    val artist: String,
    val album: String,
    val playCount: Int,
    val totalSeconds: Double,
)

data class ArtistListenStats(
    val id: UUID = UUID.randomUUID(),
    val artist: String,
    val topSong: String,
    val playCount: Int,
    val totalSeconds: Double,
)

data class AlbumListenStats(
    val id: UUID = UUID.randomUUID(),
    val album: String,
    val artist: String,
    val playCount: Int,
    val totalSeconds: Double,
)

data class MusicStats(
    val periodLabel: String,
    val totalPlays: Int,
    val totalSeconds: Double,
    val uniqueSongs: Int,
    val uniqueArtists: Int,
    val uniqueAlbums: Int,
    val averageDailyMinutes: Double,
    val longestStreak: Int,
    val mostActiveHour: Int?,
    val mostActiveDay: String?,
    val mostSkipped: String?,
    val topSongs: List<SongListenStats>,
    val topArtists: List<ArtistListenStats>,
    val topAlbums: List<AlbumListenStats>,
)
