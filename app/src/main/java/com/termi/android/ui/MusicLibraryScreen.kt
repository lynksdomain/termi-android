package com.termi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termi.android.music.AudioPlayerEngine
import com.termi.android.music.MusicLibraryManager
import com.termi.android.music.Playlist
import com.termi.android.music.PlaylistManager
import com.termi.android.music.Song
import com.termi.android.theme.LocalTerminalTheme
import java.util.UUID
import kotlinx.coroutines.launch

// Kotlin/Compose port of LibraryView.swift (music side) — root menu
// (SONGS/ARTISTS/ALBUMS/PLAYLISTS with counts) drilling into flat lists,
// matching the real iOS structure. Playlists are Room-backed
// (PlaylistManager). All confirms/action-sheets use the custom
// TerminalActionSheet/TerminalConfirmDialog/full-screen editors — NOT stock
// Android AlertDialogs — per the project's "no stock Android UI where iOS
// is custom" rule (see android_port_rules memory). Song membership in a
// playlist is assigned ONLY via PlaylistEditorScreen's checkbox list,
// matching iOS exactly — there is no "add to playlist" action on the song's
// own menu (an earlier pass incorrectly invented one; corrected here).
// Deferred vs. the original: the unified multi-section search-results view
// already ported below; IndexedScrollView's alphabet rail was intentionally
// removed in the real app (not a gap).
private sealed class LibraryNav {
    object Root : LibraryNav()
    object Songs : LibraryNav()
    object Artists : LibraryNav()
    object Albums : LibraryNav()
    data class ArtistSongs(val artist: String) : LibraryNav()
    data class AlbumSongs(val album: String) : LibraryNav()
    object Playlists : LibraryNav()
    data class PlaylistSongs(val playlistId: UUID) : LibraryNav()
}

@Composable
fun MusicLibraryScreen(
    library: MusicLibraryManager,
    player: AudioPlayerEngine,
    playlistManager: PlaylistManager,
    onSettings: (() -> Unit)? = null,
) {
    val theme = LocalTerminalTheme.current
    val context = LocalContext.current
    val songs by library.songs.collectAsState()
    val currentSong by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val playlists by playlistManager.playlists.collectAsState()

    // The boot screen's song count is a cheap read (registered-path list
    // length), not the real per-song metadata pass `loadLibrary()` does —
    // so there's a real gap between "boot said N songs" and the list
    // actually rendering. Without this flag, that gap looked identical to
    // a genuinely empty library (same empty-state block), reported live as
    // "why does it take so long to show stuff" right after boot said things
    // were loaded.
    var isLoadingLibrary by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        library.loadLibrary()
        isLoadingLibrary = false
    }
    // Picks up anything WiFi Transfer just wrote into the inbox — without
    // this, a mid-session upload wouldn't show until the app restarted.
    val wifiUploadLog by com.termi.android.network.WiFiTransferServer.uploadLog.collectAsState()
    LaunchedEffect(wifiUploadLog) { library.refreshInbox() }

    var nav by remember { mutableStateOf<LibraryNav>(LibraryNav.Root) }
    var query by remember { mutableStateOf("") }
    var showFileBrowser by remember { mutableStateOf(false) }
    var showImportMethod by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
    val importProgress = rememberImportProgressController()
    val importScope = androidx.compose.runtime.rememberCoroutineScope()
    val sources by library.sources.collectAsState()

    // Song "···" action sheet + its two real actions (EDIT METADATA, REMOVE).
    var actionSheetSong by remember { mutableStateOf<Song?>(null) }
    var editingSong by remember { mutableStateOf<Song?>(null) }
    var removeTarget by remember { mutableStateOf<Song?>(null) }

    // Playlist "···" action sheet + its two real actions (EDIT, DELETE).
    var actionSheetPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var deletePlaylistTarget by remember { mutableStateOf<Playlist?>(null) }
    var showPlaylistEditor by remember { mutableStateOf(false) }
    var editingPlaylist by remember { mutableStateOf<Playlist?>(null) }

    fun playFromFullLibrary(song: Song) {
        val index = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        player.loadQueue(songs, index)
        player.play()
    }

    FullScreenCover(visible = showSources, onDismissRequest = { showSources = false }) {
        MusicSourcesScreen(
            sources = sources,
            songCount = { library.songCount(it) },
            onAddSource = {
                showSources = false
                showFileBrowser = true
            },
            onOpenWifiTransfer = {
                showSources = false
                showImportMethod = true
            },
            onRemoveSource = { library.removeSource(it) },
            onDismiss = { showSources = false },
        )
    }

    FullScreenCover(visible = showImportMethod, onDismissRequest = { showImportMethod = false }) {
        ImportMethodScreen(
            target = ImportTarget.MUSIC,
            onBrowseFiles = {
                showImportMethod = false
                showFileBrowser = true
            },
            onDismiss = { showImportMethod = false },
            openWifiPanel = true,
        )
    }

    FullScreenCover(visible = showFileBrowser, onDismissRequest = { showFileBrowser = false }) {
        if (com.termi.android.filesystem.StoragePermission.isGranted()) {
            FileBrowserScreen(
                startDir = android.os.Environment.getExternalStorageDirectory(),
                extensionFilter = setOf("mp3", "m4a", "mp4", "aac", "wav", "aiff", "flac", "ogg"),
                onFileSelected = { file ->
                    showFileBrowser = false
                    library.registerSource(file.parentFile ?: file)
                    importScope.launch { importProgress.run(listOf(file)) { library.importFromFile(it) } }
                },
                onFolderSelected = { rootDir, files ->
                    showFileBrowser = false
                    library.registerSource(rootDir)
                    importScope.launch { importProgress.run(files) { library.importFromFile(it) } }
                },
                onCancel = { showFileBrowser = false },
                scanProgress = importProgress,
            )
        } else {
            StoragePermissionScreen(
                onRequestPermission = {
                    context.startActivity(com.termi.android.filesystem.StoragePermission.requestIntent(context))
                },
                onBack = { showFileBrowser = false },
            )
        }
    }

    var stickyEditingSong by remember { mutableStateOf<Song?>(null) }
    LaunchedEffect(editingSong) { if (editingSong != null) stickyEditingSong = editingSong }
    FullScreenCover(visible = editingSong != null, onDismissRequest = { editingSong = null }) {
        stickyEditingSong?.let { song ->
            SongMetadataEditorScreen(
                song = song,
                onSave = { updated ->
                    library.updateSong(updated)
                    editingSong = null
                },
                onCancel = { editingSong = null },
            )
        }
    }

    var stickyEditingPlaylist by remember { mutableStateOf<Playlist?>(null) }
    LaunchedEffect(showPlaylistEditor) { if (showPlaylistEditor) stickyEditingPlaylist = editingPlaylist }
    FullScreenCover(
        visible = showPlaylistEditor,
        onDismissRequest = { showPlaylistEditor = false; editingPlaylist = null },
    ) {
        PlaylistEditorScreen(
            playlist = stickyEditingPlaylist,
            allSongs = songs,
            onSave = { title, description, selectedSongs ->
                val existing = stickyEditingPlaylist
                if (existing != null) {
                    playlistManager.update(existing, title, description, selectedSongs)
                } else {
                    playlistManager.create(title, description, selectedSongs)
                }
                showPlaylistEditor = false
                editingPlaylist = null
            },
            onCancel = {
                showPlaylistEditor = false
                editingPlaylist = null
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "┌─[ ${headerTitle(nav, query, playlists)} ]",
                    color = theme.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                if (songs.isNotEmpty() || nav != LibraryNav.Root) {
                    HeaderPillButton("[SOURCES]") { showSources = true }
                }
                if (onSettings != null) {
                    HeaderPillButton("[SETTINGS]", onClick = onSettings)
                }
            }
            if (songs.isNotEmpty()) {
                SearchBar(query = query, onQueryChange = { query = it })
            }

            // Search only replaces the ROOT screen's content with a unified
            // grouped results view — it does NOT filter drilled-in lists.
            val effectiveNav = nav

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                when (effectiveNav) {
                    is LibraryNav.Root -> {
                        if (query.isNotBlank()) {
                            searchResults(
                                query = query,
                                songs = songs,
                                groupedByArtist = library.groupedByArtist,
                                groupedByAlbum = library.groupedByAlbum,
                                playlists = playlists,
                                currentSongId = currentSong?.id,
                                isPlaying = isPlaying,
                                onArtist = { nav = LibraryNav.ArtistSongs(it) },
                                onAlbum = { nav = LibraryNav.AlbumSongs(it) },
                                onPlaylist = { nav = LibraryNav.PlaylistSongs(it) },
                                onSong = { playFromFullLibrary(it) },
                            )
                        } else if (songs.isEmpty() && isLoadingLibrary) {
                            item { LoadingLibraryBlock() }
                        } else if (songs.isEmpty()) {
                            item { RootEmptyLibraryBlock(onImport = { showSources = true }) }
                        } else {
                            item {
                                MenuRow("[S]", "SONGS", songs.size) { nav = LibraryNav.Songs }
                                MenuRow("[A]", "ARTISTS", library.groupedByArtist.size) { nav = LibraryNav.Artists }
                                MenuRow("[L]", "ALBUMS", library.groupedByAlbum.size) { nav = LibraryNav.Albums }
                                MenuRow("[P]", "PLAYLISTS", playlists.size) { nav = LibraryNav.Playlists }
                            }
                        }
                    }
                    is LibraryNav.Songs -> {
                        item { BackRow { nav = LibraryNav.Root } }
                        if (songs.isEmpty()) {
                            item { EmptyLibraryState() }
                        } else {
                            items(songs) { song ->
                                SongRow(
                                    song = song,
                                    isCurrent = song.id == currentSong?.id,
                                    isPlaying = isPlaying && song.id == currentSong?.id,
                                    onClick = { playFromFullLibrary(song) },
                                    onMore = { actionSheetSong = song },
                                )
                            }
                        }
                    }
                    is LibraryNav.Artists -> {
                        item { BackRow { nav = LibraryNav.Root } }
                        items(library.groupedByArtist) { (artist, artistSongs) ->
                            GroupRow("[A]", artist, artistSongs.size) { nav = LibraryNav.ArtistSongs(artist) }
                        }
                    }
                    is LibraryNav.Albums -> {
                        item { BackRow { nav = LibraryNav.Root } }
                        items(library.groupedByAlbum) { (album, albumSongs) ->
                            GroupRow("[L]", album, albumSongs.size) { nav = LibraryNav.AlbumSongs(album) }
                        }
                    }
                    is LibraryNav.ArtistSongs -> {
                        val artist = effectiveNav.artist
                        item { BackRow { nav = LibraryNav.Artists } }
                        items(songs.filter { it.artist == artist }) { song ->
                            SongRow(
                                song = song,
                                isCurrent = song.id == currentSong?.id,
                                isPlaying = isPlaying && song.id == currentSong?.id,
                                onClick = { playFromFullLibrary(song) },
                                onMore = { actionSheetSong = song },
                            )
                        }
                    }
                    is LibraryNav.AlbumSongs -> {
                        val album = effectiveNav.album
                        item { BackRow { nav = LibraryNav.Albums } }
                        items(songs.filter { it.album == album }) { song ->
                            SongRow(
                                song = song,
                                isCurrent = song.id == currentSong?.id,
                                isPlaying = isPlaying && song.id == currentSong?.id,
                                onClick = { playFromFullLibrary(song) },
                                onMore = { actionSheetSong = song },
                            )
                        }
                    }
                    is LibraryNav.Playlists -> {
                        item { BackRow { nav = LibraryNav.Root } }
                        item {
                            MenuActionRow("+ NEW PLAYLIST") {
                                editingPlaylist = null
                                showPlaylistEditor = true
                            }
                        }
                        if (playlists.isEmpty()) {
                            item { EmptyLibraryState(text = "║  NO PLAYLISTS YET") }
                        } else {
                            items(playlists) { playlist ->
                                GroupRow(
                                    "[P]",
                                    playlist.title,
                                    playlist.songPaths.size,
                                    onMore = { actionSheetPlaylist = playlist },
                                ) {
                                    nav = LibraryNav.PlaylistSongs(playlist.id)
                                }
                            }
                        }
                    }
                    is LibraryNav.PlaylistSongs -> {
                        val playlistId = effectiveNav.playlistId
                        val playlist = playlists.firstOrNull { it.id == playlistId }
                        item { BackRow { nav = LibraryNav.Playlists } }
                        if (playlist == null) {
                            item { EmptyLibraryState(text = "║  PLAYLIST NOT FOUND") }
                        } else {
                            val playlistSongs = playlistManager.songsIn(playlist, songs)
                            if (playlistSongs.isEmpty()) {
                                item { EmptyLibraryState(text = "║  NO SONGS IN THIS PLAYLIST YET") }
                            } else {
                                items(playlistSongs) { song ->
                                    SongRow(
                                        song = song,
                                        isCurrent = song.id == currentSong?.id,
                                        isPlaying = isPlaying && song.id == currentSong?.id,
                                        onClick = {
                                            val index = playlistSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                                            player.loadQueue(playlistSongs, index)
                                            player.play()
                                        },
                                        onMore = { actionSheetSong = song },
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(64.dp)) }
            }
        }

        ImportProgressOverlay(importProgress)
    }

    actionSheetSong?.let { song ->
        TerminalActionSheet(
            actions = listOf(
                TerminalAction(label = "EDIT METADATA", icon = "*") { editingSong = song },
                TerminalAction(label = "REMOVE FROM LIBRARY", icon = "x", destructive = true) {
                    removeTarget = song
                },
            ),
            onDismiss = { actionSheetSong = null },
        )
    }

    removeTarget?.let { song ->
        TerminalConfirmDialog(
            title = "Remove \"${song.title}\"?",
            message = "This will permanently remove the song from your library. This cannot be undone.",
            confirmLabel = "[REMOVE]",
            onConfirm = {
                library.removeSong(song)
                removeTarget = null
            },
            onDismiss = { removeTarget = null },
        )
    }

    actionSheetPlaylist?.let { playlist ->
        TerminalActionSheet(
            actions = listOf(
                TerminalAction(label = "EDIT PLAYLIST", icon = "*") {
                    editingPlaylist = playlist
                    showPlaylistEditor = true
                },
                TerminalAction(label = "DELETE PLAYLIST", icon = "x", destructive = true) {
                    deletePlaylistTarget = playlist
                },
            ),
            onDismiss = { actionSheetPlaylist = null },
        )
    }

    deletePlaylistTarget?.let { playlist ->
        TerminalConfirmDialog(
            title = "Delete \"${playlist.title}\"?",
            message = "This cannot be undone. The playlist will be permanently removed.",
            confirmLabel = "[DELETE]",
            onConfirm = {
                playlistManager.delete(playlist)
                if (nav is LibraryNav.PlaylistSongs && (nav as LibraryNav.PlaylistSongs).playlistId == playlist.id) {
                    nav = LibraryNav.Playlists
                }
                deletePlaylistTarget = null
            },
            onDismiss = { deletePlaylistTarget = null },
        )
    }
}

private fun headerTitle(nav: LibraryNav, query: String, playlists: List<Playlist>): String {
    return when (nav) {
        is LibraryNav.Root -> if (query.isNotBlank()) "SEARCH RESULTS" else "TERMI MUSIC"
        is LibraryNav.Songs -> "SONGS"
        is LibraryNav.Artists -> "ARTISTS"
        is LibraryNav.Albums -> "ALBUMS"
        is LibraryNav.ArtistSongs -> nav.artist.uppercase()
        is LibraryNav.AlbumSongs -> nav.album.uppercase()
        is LibraryNav.Playlists -> "PLAYLISTS"
        is LibraryNav.PlaylistSongs -> (playlists.firstOrNull { it.id == nav.playlistId }?.title ?: "PLAYLIST").uppercase()
    }
}

// Port of LibraryView.swift's searchResultsView — a unified, grouped view
// (artists, then albums, then playlists, then songs) shown in place of the
// root menu while a search query is active.
private fun LazyListScope.searchResults(
    query: String,
    songs: List<Song>,
    groupedByArtist: List<Pair<String, List<Song>>>,
    groupedByAlbum: List<Pair<String, List<Song>>>,
    playlists: List<Playlist>,
    currentSongId: UUID?,
    isPlaying: Boolean,
    onArtist: (String) -> Unit,
    onAlbum: (String) -> Unit,
    onPlaylist: (UUID) -> Unit,
    onSong: (Song) -> Unit,
) {
    val q = query.lowercase()
    val matchingArtists = groupedByArtist.filter { it.first.lowercase().contains(q) }
    val matchingAlbums = groupedByAlbum.filter { it.first.lowercase().contains(q) }
    val matchingPlaylists = playlists.filter { it.title.lowercase().contains(q) }
    val matchingSongs = songs.filter { it.title.lowercase().contains(q) }
    val hasResults = matchingArtists.isNotEmpty() || matchingAlbums.isNotEmpty() ||
        matchingPlaylists.isNotEmpty() || matchingSongs.isNotEmpty()

    if (!hasResults) {
        item { NoSearchResultsState(query) }
        return
    }
    if (matchingArtists.isNotEmpty()) {
        item { SearchSectionHeader("ARTISTS") }
        items(matchingArtists) { (artist, artistSongs) ->
            SearchResultRow("[A]", artist, "${artistSongs.size} track" + if (artistSongs.size == 1) "" else "s") {
                onArtist(artist)
            }
        }
    }
    if (matchingAlbums.isNotEmpty()) {
        item { SearchSectionHeader("ALBUMS") }
        items(matchingAlbums) { (album, albumSongs) ->
            SearchResultRow("[L]", album, "${albumSongs.size} track" + if (albumSongs.size == 1) "" else "s") {
                onAlbum(album)
            }
        }
    }
    if (matchingPlaylists.isNotEmpty()) {
        item { SearchSectionHeader("PLAYLISTS") }
        items(matchingPlaylists) { playlist ->
            SearchResultRow("[P]", playlist.title, "${playlist.songPaths.size} track" + if (playlist.songPaths.size == 1) "" else "s") {
                onPlaylist(playlist.id)
            }
        }
    }
    if (matchingSongs.isNotEmpty()) {
        item { SearchSectionHeader("SONGS") }
        items(matchingSongs) { song ->
            SongRow(
                song = song,
                isCurrent = song.id == currentSongId,
                isPlaying = isPlaying && song.id == currentSongId,
                onClick = { onSong(song) },
                onMore = {},
            )
        }
    }
}

@Composable
private fun SearchSectionHeader(title: String) {
    val theme = LocalTerminalTheme.current
    Text(
        text = "┌─[ $title ]",
        color = theme.secondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun SearchResultRow(icon: String, label: String, subtitle: String, onClick: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = icon,
            color = theme.dim,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(end = 10.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = theme.foreground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            Text(
                text = subtitle,
                color = theme.dim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(text = ">", color = theme.dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun NoSearchResultsState(query: String) {
    val theme = LocalTerminalTheme.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "[ NO RESULTS ]",
            color = theme.dim,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = "Nothing matches \"$query\"",
            color = theme.dim.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private fun matchesQuery(song: Song, query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.lowercase()
    return song.title.lowercase().contains(q) || song.artist.lowercase().contains(q) || song.album.lowercase().contains(q)
}

@Composable
fun HeaderPillButton(label: String, onClick: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Text(
        text = label,
        color = theme.secondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(theme.accent.copy(alpha = 0.07f), androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
            .padding(horizontal = 4.dp, vertical = 8.dp),
    )
}

@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    val theme = LocalTerminalTheme.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.selectionBackground.copy(alpha = 0.3f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "│ SEARCH> ",
                color = theme.dim,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
            )
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = TextStyle(color = theme.foreground, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(theme.cursor),
                    singleLine = true,
                )
            }
            if (query.isNotEmpty()) {
                Text(
                    text = "[CLEAR]",
                    color = theme.dim,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable { onQueryChange("") },
                )
            }
            Text(
                text = " │",
                color = theme.dim,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.dim.copy(alpha = 0.3f)))
    }
}

@Composable
private fun MenuRow(icon: String, label: String, count: Int, onClick: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = icon,
            color = theme.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(end = 10.dp),
        )
        Text(
            text = label,
            color = theme.foreground,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$count",
            color = theme.dim,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(end = 6.dp),
        )
        Text(text = ">", color = theme.dim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun GroupRow(icon: String, label: String, count: Int, onMore: (() -> Unit)? = null, onClick: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = icon,
            color = theme.dim,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(end = 10.dp),
        )
        Text(
            text = label,
            color = theme.foreground,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$count " + if (count == 1) "track" else "tracks",
            color = theme.dim,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(end = if (onMore != null) 10.dp else 0.dp),
        )
        if (onMore != null) {
            Text(
                text = "···",
                color = theme.dim,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable(onClick = onMore).padding(horizontal = 6.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun MenuActionRow(label: String, onClick: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Text(
        text = label,
        color = theme.accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

@Composable
private fun BackRow(onClick: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "< [BACK]",
            color = theme.accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

// Port of LibraryView.swift's real `emptyState` — shown only at the root
// when the whole library is empty (matches the header's own [+ADD]-hiding
// condition: `libraryIsEmpty = allSongs.isEmpty && nav == .root`). The old
// `EmptyLibraryState()` text here just SAID "[+ADD]" without being a real
// button — since the header pill is legitimately hidden in this state
// (matching iOS), there was no way at all to start an import. Caught live
// on the real device.
// Shown instead of RootEmptyLibraryBlock while `loadLibrary()`'s real
// per-song metadata pass is still running — without this, mid-load looked
// identical to a genuinely empty library.
@Composable
private fun LoadingLibraryBlock() {
    val theme = LocalTerminalTheme.current
    Box(modifier = Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "║  LOADING LIBRARY…",
            color = theme.dim,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun RootEmptyLibraryBlock(onImport: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Box(modifier = Modifier.fillMaxWidth().padding(top = 60.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        ) {
            Text(text = "> NO MUSIC FOUND.", color = theme.dim, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(
                text = "IMPORT AUDIO FILES TO GET STARTED.",
                color = theme.dim.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                text = "[ IMPORT MUSIC ]",
                color = Color.Black,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                    .background(theme.accent)
                    .clickable(onClick = onImport)
                    .padding(vertical = 18.dp),
            )
            Text(text = "SUPPORTED: MP3 · M4A · AAC · WAV · AIFF · FLAC", color = theme.dim.copy(alpha = 0.5f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun EmptyLibraryState(text: String = "║  NO SONGS YET — [+ADD] TO IMPORT") {
    val theme = LocalTerminalTheme.current
    Text(
        text = text,
        color = theme.dim,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

@Composable
private fun SongRow(song: Song, isCurrent: Boolean, isPlaying: Boolean, onClick: () -> Unit, onMore: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (isCurrent) (if (isPlaying) "> " else "|| ") else "  ",
            color = theme.accent,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isCurrent) theme.accent else theme.foreground,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            Text(
                text = "${song.artist} · ${song.album}",
                color = theme.dim,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }
        Text(
            text = formatMs(song.durationMs),
            color = theme.dim,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(end = 10.dp),
        )
        Text(
            text = "···",
            color = theme.dim,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .clickable(onClick = onMore)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

internal fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
