package com.termi.android.music

import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Kotlin port of PlaylistManager.swift, Room-backed.
class PlaylistManager(
    private val dao: PlaylistDao,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    val playlists: StateFlow<List<Playlist>> =
        dao.observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun create(title: String, description: String = "", songs: List<Song> = emptyList()) {
        scope.launch {
            dao.upsert(
                Playlist(title = title, playlistDescription = description, songPaths = songs.map { it.file.absolutePath }),
            )
        }
    }

    // Matches PlaylistEditorView.swift's onSave(title, desc, songs) — the
    // one place membership is actually reassigned, as a full replacement
    // list from the editor's checkbox selection, not incremental add/remove.
    fun update(playlist: Playlist, title: String, description: String, songs: List<Song>) {
        scope.launch {
            dao.upsert(
                playlist.copy(
                    title = title,
                    playlistDescription = description,
                    songPaths = songs.map { it.file.absolutePath },
                    updatedAt = Instant.now(),
                ),
            )
        }
    }

    fun rename(playlist: Playlist, title: String) {
        scope.launch { dao.upsert(playlist.copy(title = title, updatedAt = Instant.now())) }
    }

    fun delete(playlist: Playlist) {
        scope.launch { dao.delete(playlist) }
    }

    fun addSong(playlist: Playlist, song: Song) {
        val path = song.file.absolutePath
        if (path in playlist.songPaths) return
        scope.launch { dao.upsert(playlist.copy(songPaths = playlist.songPaths + path, updatedAt = Instant.now())) }
    }

    fun removeSong(playlist: Playlist, song: Song) {
        val path = song.file.absolutePath
        scope.launch {
            dao.upsert(playlist.copy(songPaths = playlist.songPaths - path, updatedAt = Instant.now()))
        }
    }

    fun songsIn(playlist: Playlist, allSongs: List<Song>): List<Song> =
        playlist.songPaths.mapNotNull { path -> allSongs.firstOrNull { it.file.absolutePath == path } }
}
