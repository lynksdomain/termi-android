package com.termi.android.music

import android.content.Context
import android.media.MediaMetadataRetriever
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// Kotlin port of LibraryManager.swift (music side) — but deliberately
// diverges from the iOS "copy into the app's sandbox" model. Android's
// MANAGE_EXTERNAL_STORAGE access makes that copy unnecessary: the library
// just REFERENCES files at their real path, so "select this folder" can
// point at an existing Music folder (with artist/album subfolders and all)
// without duplicating anything onto disk. The registered path list is the
// library's source of truth — persisted here, not derived from scanning one
// fixed folder — with a lightweight auto-registration pass for the WiFi
// Transfer inbox (see MusicFolderManager) since those files DO get written
// by us and have no pre-existing external path to reference.
//
// User-edited metadata overrides (from SongMetadataEditorScreen) persist
// centrally here too, keyed by absolute path — NOT as a sidecar file next to
// the original (that would mean writing into the user's own external
// folders just to remember an edit, which reference-in-place should avoid).
class MusicLibraryManager(private val context: Context) {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _sources = MutableStateFlow<List<MusicSource>>(emptyList())
    val sources: StateFlow<List<MusicSource>> = _sources.asStateFlow()

    private val libraryPrefs = context.getSharedPreferences("termi_music_library", Context.MODE_PRIVATE)
    private val metadataPrefs = context.getSharedPreferences("termi_music_metadata", Context.MODE_PRIVATE)

    // Caches the real extracted fields (title/artist/album/duration/etc.),
    // keyed by path, alongside the file's mtime at extraction time — a
    // MediaMetadataRetriever pass is real native codec-header parsing, not
    // free, and re-running it for every song on EVERY app launch (with zero
    // caching, one file at a time) was the actual root cause of "the time
    // it takes is insane... something feels fundamentally wrong" for a
    // real-sized library. A cache hit (mtime unchanged) skips the retriever
    // entirely; only genuinely new/changed files pay the extraction cost.
    private val songCachePrefs = context.getSharedPreferences("termi_music_song_cache", Context.MODE_PRIVATE)

    private val inboxSource: MusicSource
        get() = MusicSource(rootPath = MusicFolderManager.musicFolder(context).absolutePath, label = "WIFI TRANSFER INBOX", isInbox = true)

    // `MusicLibraryManager` is `remember`'d at the nav-host level and
    // survives tab switches, but Jetpack Navigation tears down and
    // recreates `MusicLibraryScreen` (and its `LaunchedEffect(Unit) {
    // loadLibrary() }`) every time the Music tab is left and revisited.
    // Without this guard, every single revisit re-ran a full
    // MediaMetadataRetriever pass over the ENTIRE library synchronously —
    // caught live as a repeated multi-second main-thread freeze/ANR
    // ("Skipped 717 frames... Davey! duration=11995ms") once the library
    // actually had real songs in it. `_songs`/`_sources` already stay
    // correct across the manager's lifetime via importFromFile/
    // registerSource/removeSource, so a true rescan is only needed once.
    private var hasLoadedOnce = false

    // Cheap, always-safe to call repeatedly — unlike `loadLibrary()`, this
    // does NOT rescan the whole library. It only picks up files freshly
    // dropped into the WiFi Transfer inbox since the last check and imports
    // just those, via the same dedup-safe `importFromFile` used everywhere
    // else. `loadLibrary()`'s own inbox scan only ever runs once now (see
    // the `hasLoadedOnce` note above), so without this, a song uploaded via
    // WiFi Transfer mid-session would never appear until the app restarted
    // — caught live: "i uploaded a book through the wifi server... it
    // didn't show... until i closed it and reopened the app" (Books hit
    // this too — see BookLibraryManager.refreshForNewFiles). Call this from
    // the screen whenever WiFiTransferServer.uploadLog changes.
    suspend fun refreshInbox() {
        val newFiles = withContext(Dispatchers.IO) { MusicFolderManager.scanInboxForAudioFiles(context) }
        withContext(Dispatchers.IO) { newFiles.forEach { importFromFile(it) } }
    }

    suspend fun loadLibrary() {
        if (hasLoadedOnce) return
        hasLoadedOnce = true

        val registered = loadRegisteredPaths().toMutableSet()

        // Auto-register anything sitting in the private inbox (WiFi
        // Transfer writes here directly) that isn't tracked yet.
        val inboxPaths = MusicFolderManager.scanInboxForAudioFiles(context).map { it.absolutePath }
        val newlyRegistered = inboxPaths.filter { it !in registered }
        if (newlyRegistered.isNotEmpty()) {
            registered.addAll(newlyRegistered)
            saveRegisteredPaths(registered.toList())
        }

        // Everything below does real file I/O and, for every UNCACHED song,
        // a MediaMetadataRetriever pass (native codec probing) — genuinely
        // heavy for a real library, so it must never run on the main
        // thread the way it used to, and cache hits skip it entirely.
        withContext(Dispatchers.IO) {
            // Drop paths whose file no longer exists (moved/deleted outside the app).
            val stillValid = registered.filter { File(it).isFile }
            if (stillValid.size != registered.size) {
                saveRegisteredPaths(stillValid)
            }

            // Cache misses (new/changed files) are extracted a few at a
            // time in parallel rather than one at a time — the previous
            // strictly-sequential loop was part of why a real library felt
            // "insanely" slow even before caching existed.
            val semaphore = Semaphore(4)
            _songs.value = coroutineScope {
                stillValid.map { path ->
                    async { semaphore.withPermit { loadSongCached(File(path)) } }
                }.awaitAll()
            }
        }

        // The inbox is a permanent channel (WiFi Transfer can always write
        // into it) so it's always shown, even with zero songs currently in
        // it — unlike a user-picked folder source, which only exists once
        // something's actually been imported through it.
        val stored = loadSources().filterNot { it.isInbox }
        _sources.value = listOf(inboxSource) + stored
    }

    // Song counts per source, computed on demand rather than stored — a
    // song belongs to whichever registered source has the longest matching
    // root, so overlapping/nested folder sources never double-count.
    fun songCount(source: MusicSource): Int =
        _songs.value.count { _sources.value.bestMatchFor(it.file.absolutePath)?.rootPath == source.rootPath }

    // Called after "[USE THIS FOLDER]" resolves, or after a loose single
    // file import (using its parent dir) — registers the folder as a
    // visible source if it isn't one already, so every song always has a
    // source it can be found and removed under.
    fun registerSource(root: File) {
        val rootPath = root.absolutePath
        if (rootPath == MusicFolderManager.musicFolder(context).absolutePath) return // inbox is implicit
        val stored = loadSources().toMutableList()
        if (stored.any { it.rootPath == rootPath }) return
        stored.add(MusicSource(rootPath = rootPath, label = root.name.ifBlank { rootPath }.uppercase()))
        saveSources(stored)
        _sources.value = listOf(inboxSource) + stored
    }

    // Untracks every song under this source. For the inbox — the one
    // folder we actually own — this deletes the real files too; for a
    // user-picked external folder it only forgets the paths, since with
    // reference-in-place we never own the user's real files.
    fun removeSource(source: MusicSource) {
        val affected = _songs.value.filter { _sources.value.bestMatchFor(it.file.absolutePath)?.rootPath == source.rootPath }
        val registered = loadRegisteredPaths().toMutableList()
        affected.forEach { song ->
            registered.remove(song.file.absolutePath)
            metadataPrefs.edit().remove(song.file.absolutePath).apply()
            songCachePrefs.edit().remove(song.file.absolutePath).apply()
            if (source.isInbox) song.file.delete()
        }
        saveRegisteredPaths(registered)
        _songs.value = _songs.value.filterNot { it.id in affected.map { s -> s.id } }

        if (!source.isInbox) {
            val stored = loadSources().filterNot { it.rootPath == source.rootPath }
            saveSources(stored)
            _sources.value = listOf(inboxSource) + stored
        }
    }

    private fun loadSources(): List<MusicSource> {
        val json = libraryPrefs.getString("sources", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val obj = arr.getJSONObject(it)
                MusicSource(rootPath = obj.getString("rootPath"), label = obj.getString("label"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveSources(sources: List<MusicSource>) {
        val arr = JSONArray()
        sources.forEach { source ->
            arr.put(JSONObject().apply { put("rootPath", source.rootPath); put("label", source.label) })
        }
        libraryPrefs.edit().putString("sources", arr.toString()).apply()
    }

    // Mirrors LibraryManager.groupedByArtist/groupedByAlbum — used by the
    // ARTISTS/ALBUMS drill-in rows in MusicLibraryScreen.
    val groupedByArtist: List<Pair<String, List<Song>>>
        get() = _songs.value.groupBy { it.artist }.toList().sortedBy { it.first.lowercase() }

    val groupedByAlbum: List<Pair<String, List<Song>>>
        get() = _songs.value.groupBy { it.album }.toList().sortedBy { it.first.lowercase() }

    // "Remove from library" only stops tracking the path — it does NOT
    // delete the user's actual file, since with reference-in-place we don't
    // own it (unlike the old copy-into-sandbox model, where the copy really
    // was ours to delete).
    fun removeSong(song: Song) {
        val registered = loadRegisteredPaths().toMutableList()
        registered.remove(song.file.absolutePath)
        saveRegisteredPaths(registered)
        metadataPrefs.edit().remove(song.file.absolutePath).apply()
        songCachePrefs.edit().remove(song.file.absolutePath).apply()
        _songs.value = _songs.value.filter { it.id != song.id }
    }

    // Import path used by our in-app file browser (MANAGE_EXTERNAL_STORAGE).
    // No copying — just registers the real path. Re-importing a path that's
    // already registered (e.g. re-selecting the same folder) is a silent
    // no-op rather than appending a second Song for it — otherwise every
    // repeat "[USE THIS FOLDER]" on the same folder doubles the library.
    fun importFromFile(source: File): Song? {
        if (!registerPath(source.absolutePath)) return null
        val song = loadSongCached(source)
        _songs.value = _songs.value + song
        return song
    }

    // Bulk import for "[USE THIS FOLDER]" — FileBrowserScreen already
    // resolves this to every matching file found recursively under the
    // picked folder, so this is just a flat batch-register. Paths already
    // in the library are skipped, same reasoning as importFromFile.
    fun importFromFiles(sources: List<File>): List<Song> {
        val imported = sources.mapNotNull { source ->
            if (registerPath(source.absolutePath)) loadSongCached(source) else null
        }
        _songs.value = _songs.value + imported
        return imported
    }

    // Returns false (and registers nothing) if the path was already tracked.
    private fun registerPath(path: String): Boolean {
        val registered = loadRegisteredPaths().toMutableList()
        if (path in registered) return false
        registered.add(path)
        saveRegisteredPaths(registered)
        return true
    }

    private fun loadRegisteredPaths(): List<String> {
        val json = libraryPrefs.getString("songPaths", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveRegisteredPaths(paths: List<String>) {
        libraryPrefs.edit().putString("songPaths", JSONArray(paths).toString()).apply()
    }

    // Applied from SongMetadataEditorScreen's [SAVE] — persists centrally
    // (keyed by absolute path) and updates the in-memory list immediately.
    fun updateSong(updated: Song) {
        val json = JSONObject().apply {
            put("title", updated.title)
            put("artist", updated.artist)
            put("album", updated.album)
            put("albumArtist", updated.albumArtist)
            put("genre", updated.genre)
            put("year", updated.year)
            put("trackNumber", updated.trackNumber ?: JSONObject.NULL)
        }
        metadataPrefs.edit().putString(updated.file.absolutePath, json.toString()).apply()
        _songs.value = _songs.value.map { if (it.id == updated.id) updated else it }
    }

    // Returns a cached Song if the file's mtime matches what was cached,
    // skipping MediaMetadataRetriever entirely; otherwise extracts (real
    // native probing) and writes the result to the cache for next time.
    private fun loadSongCached(file: File): Song {
        val path = file.absolutePath
        val mtime = file.lastModified()
        songCachePrefs.getString(path, null)?.let { cached ->
            try {
                val obj = JSONObject(cached)
                if (obj.optLong("mtime") == mtime) {
                    val song = Song(
                        file = file,
                        title = obj.optString("title", file.nameWithoutExtension),
                        artist = obj.optString("artist", "Unknown Artist"),
                        album = obj.optString("album", "Unknown Album"),
                        albumArtist = obj.optString("albumArtist", ""),
                        genre = obj.optString("genre", ""),
                        year = obj.optString("year", ""),
                        durationMs = obj.optLong("durationMs"),
                        trackNumber = if (obj.isNull("trackNumber")) null else obj.optInt("trackNumber"),
                    )
                    return applyMetadataOverride(song)
                }
            } catch (e: Exception) {
                // Fall through and re-extract.
            }
        }
        val song = loadSong(file)
        cacheSong(path, mtime, song)
        return song
    }

    private fun cacheSong(path: String, mtime: Long, song: Song) {
        val obj = JSONObject().apply {
            put("mtime", mtime)
            put("title", song.title)
            put("artist", song.artist)
            put("album", song.album)
            put("albumArtist", song.albumArtist)
            put("genre", song.genre)
            put("year", song.year)
            put("durationMs", song.durationMs)
            put("trackNumber", song.trackNumber ?: JSONObject.NULL)
        }
        songCachePrefs.edit().putString(path, obj.toString()).apply()
    }

    private fun loadSong(file: File): Song {
        val retriever = MediaMetadataRetriever()
        val base = try {
            retriever.setDataSource(file.absolutePath)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: file.nameWithoutExtension
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: "Unknown Artist"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?: "Unknown Album"
            val albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST) ?: ""
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: ""
            val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR) ?: ""
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val track = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.substringBefore('/')?.toIntOrNull()
            Song(
                file = file,
                title = title,
                artist = artist,
                album = album,
                albumArtist = albumArtist,
                genre = genre,
                year = year,
                durationMs = duration,
                trackNumber = track,
            )
        } catch (e: Exception) {
            Song(file = file, title = file.nameWithoutExtension, artist = "Unknown Artist", album = "Unknown Album", durationMs = 0L)
        } finally {
            retriever.release()
        }
        return applyMetadataOverride(base)
    }

    private fun applyMetadataOverride(song: Song): Song {
        val json = metadataPrefs.getString(song.file.absolutePath, null) ?: return song
        return try {
            val obj = JSONObject(json)
            song.copy(
                title = obj.optString("title", song.title),
                artist = obj.optString("artist", song.artist),
                album = obj.optString("album", song.album),
                albumArtist = obj.optString("albumArtist", song.albumArtist),
                genre = obj.optString("genre", song.genre),
                year = obj.optString("year", song.year),
                trackNumber = if (obj.isNull("trackNumber")) null else obj.optInt("trackNumber", song.trackNumber ?: 0),
            )
        } catch (e: Exception) {
            song
        }
    }
}
