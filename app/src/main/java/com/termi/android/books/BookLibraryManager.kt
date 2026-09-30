package com.termi.android.books

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

// Kotlin port of BookLibraryManager.swift — library scan/import, groups,
// bookmarks, search, and debounced reading-progress persistence. Progress
// keys are filename-anchored (book.progressKey), same as iOS, since
// Book.id is a fresh UUID every rescan on both platforms.
class BookLibraryManager(private val context: Context) {

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    private val _groups = MutableStateFlow<List<BookGroup>>(emptyList())
    val groups: StateFlow<List<BookGroup>> = _groups.asStateFlow()

    private val _sources = MutableStateFlow<List<BookSource>>(emptyList())
    val sources: StateFlow<List<BookSource>> = _sources.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _hasLoadedOnce = MutableStateFlow(false)
    val hasLoadedOnce: StateFlow<Boolean> = _hasLoadedOnce.asStateFlow()

    private val progressPrefs = context.getSharedPreferences("termi_book_progress", Context.MODE_PRIVATE)
    private val groupsPrefs = context.getSharedPreferences("termi_book_groups", Context.MODE_PRIVATE)
    private val bookmarksPrefs = context.getSharedPreferences("termi_book_bookmarks", Context.MODE_PRIVATE)
    private val sourcesPrefs = context.getSharedPreferences("termi_book_sources", Context.MODE_PRIVATE)
    // Books are copied into one owned folder (see BookFolderManager), so
    // there's no live external path to derive a source from at load time —
    // this remembers, per filename, which BookSource a book was imported
    // from, keyed the same filename-anchored way as progressPrefs.
    private val sourceMapPrefs = context.getSharedPreferences("termi_book_source_map", Context.MODE_PRIVATE)

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val pendingSaves = mutableMapOf<UUID, Job>()

    val filteredBooks: List<Book>
        get() {
            val q = _searchQuery.value.trim()
            if (q.isEmpty()) return _books.value
            return _books.value.filter { it.title.contains(q, ignoreCase = true) || it.author.contains(q, ignoreCase = true) }
        }

    val ungroupedBooks: List<Book>
        get() {
            val grouped = _groups.value.flatMap { it.bookFilenames }.toSet()
            return _books.value.filter { it.file.name !in grouped }
        }

    fun booksIn(group: BookGroup): List<Book> {
        val byName = _books.value.associateBy { it.file.name }
        return group.bookFilenames.mapNotNull { byName[it] }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // `loadBook` parses each EPUB's manifest/TOC/lightweight metadata — real
    // archive I/O, not free. This used to run synchronously on the main
    // thread; harmless with only a couple of books, but the same shape of
    // bug that caused a confirmed multi-second ANR in MusicLibraryManager's
    // equivalent path once a library had enough real content (see
    // android_port_status memory) — fixed here the same way before it hit
    // the same way.
    suspend fun loadLibrary() {
        loadGroupsFromDisk()
        _sources.value = loadSources()
        val files = BookFolderManager.scanForEpubs(context)
        _books.value = kotlinx.coroutines.withContext(Dispatchers.IO) {
            files.map { loadBook(it) }.sortedBy { it.title.lowercase() }
        }
        _hasLoadedOnce.value = true
    }

    // Cheap, safe to call repeatedly — unlike `loadLibrary()`, does not
    // re-parse anything already in `_books`. WiFi Transfer writes straight
    // into the same folder `loadLibrary()` scans, but nothing tells this
    // manager a new file landed there mid-session, so without this a
    // WiFi-uploaded book wouldn't appear until the app restarted — caught
    // live: "i uploaded a book through the wifi server... it didn't show
    // until i closed it and reopened the app." Call from the screen
    // whenever WiFiTransferServer.uploadLog changes.
    suspend fun refreshForNewFiles() {
        val known = _books.value.map { it.file.absolutePath }.toSet()
        val newFiles = kotlinx.coroutines.withContext(Dispatchers.IO) {
            BookFolderManager.scanForEpubs(context).filterNot { it.absolutePath in known }
        }
        if (newFiles.isEmpty()) return
        val newBooks = kotlinx.coroutines.withContext(Dispatchers.IO) { newFiles.map { loadBook(it) } }
        _books.value = (_books.value + newBooks).sortedBy { it.title.lowercase() }
    }

    fun importFromFile(source: File, sourceRootPath: String? = null): Book? {
        val file = BookFolderManager.copyIntoFolder(context, source) ?: return null
        if (sourceRootPath != null) setSourceRootPath(file.name, sourceRootPath)
        val book = loadBook(file)
        _books.value = (_books.value + book).sortedBy { it.title.lowercase() }
        return book
    }

    fun importFromFiles(sources: List<File>, sourceRootPath: String? = null): List<Book> {
        val imported = sources.mapNotNull { source ->
            BookFolderManager.copyIntoFolder(context, source)?.let { file ->
                if (sourceRootPath != null) setSourceRootPath(file.name, sourceRootPath)
                loadBook(file)
            }
        }
        _books.value = (_books.value + imported).sortedBy { it.title.lowercase() }
        return imported
    }

    // Deletes the file and drops it from every group, but — matching iOS —
    // deliberately leaves the bookProgress_/bookmarks_ prefs behind, so
    // re-importing the same filename resurrects old progress/bookmarks. The
    // source-map entry IS cleared though, since a re-import gets a fresh
    // chance to be (re-)tagged with whatever source it's imported through.
    fun removeBook(book: Book) {
        BookFolderManager.remove(book.file)
        _books.value = _books.value.filter { it.id != book.id }
        _groups.value = _groups.value.map { it.copy(bookFilenames = it.bookFilenames - book.file.name) }
        saveGroupsToDisk()
        clearSourceRootPath(book.file.name)
    }

    // MARK: - Sources (folder-of-origin tracking; see BookSource)

    fun itemCount(source: BookSource): Int =
        _books.value.count { it.sourceRootPath == source.rootPath }

    fun registerSource(root: File) {
        val rootPath = root.absolutePath
        val stored = _sources.value.toMutableList()
        if (stored.any { it.rootPath == rootPath }) return
        stored.add(BookSource(rootPath = rootPath, label = root.name.ifBlank { rootPath }.uppercase()))
        _sources.value = stored
        saveSources(stored)
    }

    // Full destructive parity with removing a book one at a time: every
    // Book copied from this source has its real on-disk copy deleted (not
    // just forgotten), since with the copy-into-folder model the file is
    // genuinely ours to delete — same semantics as Music's inbox removal.
    fun removeSource(source: BookSource) {
        val affected = _books.value.filter { it.sourceRootPath == source.rootPath }
        affected.forEach { book -> removeBook(book) }
        _sources.value = _sources.value.filterNot { it.rootPath == source.rootPath }
        saveSources(_sources.value)
    }

    private fun setSourceRootPath(filename: String, rootPath: String) {
        sourceMapPrefs.edit().putString("sourceRoot_$filename", rootPath).apply()
    }

    private fun clearSourceRootPath(filename: String) {
        sourceMapPrefs.edit().remove("sourceRoot_$filename").apply()
    }

    private fun sourceRootPathFor(filename: String): String? =
        sourceMapPrefs.getString("sourceRoot_$filename", null)

    private fun loadSources(): List<BookSource> {
        val json = sourcesPrefs.getString("sources", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val obj = arr.getJSONObject(it)
                BookSource(rootPath = obj.getString("rootPath"), label = obj.getString("label"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveSources(sources: List<BookSource>) {
        val arr = JSONArray()
        sources.forEach { source ->
            arr.put(JSONObject().apply { put("rootPath", source.rootPath); put("label", source.label) })
        }
        sourcesPrefs.edit().putString("sources", arr.toString()).apply()
    }

    fun resetProgress(book: Book) {
        _books.value = _books.value.map {
            if (it.id == book.id) it.copy(currentChapter = 0, currentCharOffset = 0, currentPageIndex = 0, totalPages = 0) else it
        }
        progressPrefs.edit().remove(book.progressKey).apply()
    }

    fun savedCharOffset(book: Book): Int = book.currentCharOffset

    // Debounced (1s) to avoid a SharedPreferences commit on every page turn —
    // same rationale as iOS's DispatchWorkItem-based debounce.
    fun updateProgress(bookId: UUID, chapter: Int, charOffset: Int, pageIndex: Int) {
        val book = _books.value.firstOrNull { it.id == bookId } ?: return
        val updated = book.copy(currentChapter = chapter, currentCharOffset = charOffset, currentPageIndex = pageIndex)
        _books.value = _books.value.map { if (it.id == bookId) updated else it }
        scheduleSave(updated)
    }

    fun updateTotalPages(bookId: UUID, totalPages: Int) {
        val book = _books.value.firstOrNull { it.id == bookId } ?: return
        val updated = book.copy(totalPages = totalPages)
        _books.value = _books.value.map { if (it.id == bookId) updated else it }
        scheduleSave(updated)
    }

    private fun scheduleSave(book: Book) {
        pendingSaves[book.id]?.cancel()
        pendingSaves[book.id] = scope.launch {
            delay(1000)
            saveProgress(book)
        }
    }

    private fun saveProgress(book: Book) {
        progressPrefs.edit()
            .putInt("${book.progressKey}_chapter", book.currentChapter)
            .putInt("${book.progressKey}_charOffset", book.currentCharOffset)
            .putInt("${book.progressKey}_pageIndex", book.currentPageIndex)
            .putInt("${book.progressKey}_totalPages", book.totalPages)
            .apply()
    }

    private fun loadBook(file: File): Book {
        val meta = EpubParser.parseLightweight(file)
        val base = Book(
            file = file,
            title = meta?.title ?: file.nameWithoutExtension,
            author = meta?.author ?: "Unknown Author",
            chapterCount = meta?.chapterCount ?: 0,
            estimatedPages = meta?.estimatedPages ?: 0,
        )
        val key = base.progressKey
        return base.copy(
            currentChapter = progressPrefs.getInt("${key}_chapter", 0),
            currentCharOffset = progressPrefs.getInt("${key}_charOffset", 0),
            currentPageIndex = progressPrefs.getInt("${key}_pageIndex", 0),
            totalPages = progressPrefs.getInt("${key}_totalPages", 0),
            sourceRootPath = sourceRootPathFor(file.name),
        )
    }

    // MARK: - Groups

    fun createGroup(name: String, description: String, bookFilenames: List<String>) {
        _groups.value = _groups.value + BookGroup(name = name, groupDescription = description, bookFilenames = bookFilenames)
        saveGroupsToDisk()
    }

    fun updateGroup(group: BookGroup, name: String, description: String, bookFilenames: List<String>) {
        val updated = group.copy(name = name, groupDescription = description, bookFilenames = bookFilenames)
        _groups.value = _groups.value.map { if (it.id == group.id) updated else it }
        saveGroupsToDisk()
    }

    fun deleteGroup(group: BookGroup) {
        _groups.value = _groups.value.filter { it.id != group.id }
        saveGroupsToDisk()
    }

    fun addToGroup(book: Book, group: BookGroup) {
        if (book.file.name in group.bookFilenames) return
        val updated = group.copy(bookFilenames = group.bookFilenames + book.file.name)
        _groups.value = _groups.value.map { if (it.id == group.id) updated else it }
        saveGroupsToDisk()
    }

    fun removeFromGroup(book: Book, group: BookGroup) {
        val updated = group.copy(bookFilenames = group.bookFilenames - book.file.name)
        _groups.value = _groups.value.map { if (it.id == group.id) updated else it }
        saveGroupsToDisk()
    }

    fun toggleGroupMembership(book: Book, group: BookGroup) {
        if (book.file.name in group.bookFilenames) removeFromGroup(book, group) else addToGroup(book, group)
    }

    private fun loadGroupsFromDisk() {
        val json = groupsPrefs.getString("bookGroups_v1", null) ?: return
        try {
            val arr = JSONArray(json)
            val loaded = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val filenames = obj.getJSONArray("bookFilenames").let { fa -> (0 until fa.length()).map { fa.getString(it) } }
                BookGroup(
                    id = UUID.fromString(obj.getString("id")),
                    name = obj.getString("name"),
                    groupDescription = obj.optString("groupDescription", ""),
                    bookFilenames = filenames,
                    isExpanded = obj.optBoolean("isExpanded", false),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                )
            }
            _groups.value = loaded
        } catch (e: Exception) {
            // corrupt/missing — start empty
        }
    }

    private fun saveGroupsToDisk() {
        val arr = JSONArray()
        _groups.value.forEach { g ->
            val obj = JSONObject()
            obj.put("id", g.id.toString())
            obj.put("name", g.name)
            obj.put("groupDescription", g.groupDescription)
            obj.put("bookFilenames", JSONArray(g.bookFilenames))
            obj.put("isExpanded", g.isExpanded)
            obj.put("createdAt", g.createdAt)
            arr.put(obj)
        }
        groupsPrefs.edit().putString("bookGroups_v1", arr.toString()).apply()
    }

    // MARK: - Bookmarks

    fun bookmarks(book: Book): List<Bookmark> = bookmarksForFilename(book.file.name)

    private fun bookmarksForFilename(filename: String): List<Bookmark> {
        val json = bookmarksPrefs.getString("bookmarks_$filename", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Bookmark(
                    id = UUID.fromString(obj.getString("id")),
                    bookFilename = obj.getString("bookFilename"),
                    chapter = obj.getInt("chapter"),
                    page = obj.getInt("page"),
                    note = obj.optString("note", ""),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                )
            }.sortedBy { it.createdAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addBookmark(bookmark: Bookmark) {
        val existing = bookmarksForFilename(bookmark.bookFilename).toMutableList()
        existing.add(bookmark)
        saveBookmarks(bookmark.bookFilename, existing)
    }

    fun removeBookmark(id: UUID, bookFilename: String) {
        val existing = bookmarksPrefs.getString("bookmarks_$bookFilename", null) ?: return
        try {
            val arr = JSONArray(existing)
            val kept = (0 until arr.length()).map { arr.getJSONObject(it) }.filterNot { it.getString("id") == id.toString() }
            val newArr = JSONArray()
            kept.forEach { newArr.put(it) }
            bookmarksPrefs.edit().putString("bookmarks_$bookFilename", newArr.toString()).apply()
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun saveBookmarks(bookFilename: String, list: List<Bookmark>) {
        val arr = JSONArray()
        list.forEach { b ->
            val obj = JSONObject()
            obj.put("id", b.id.toString())
            obj.put("bookFilename", b.bookFilename)
            obj.put("chapter", b.chapter)
            obj.put("page", b.page)
            obj.put("note", b.note)
            obj.put("createdAt", b.createdAt)
            arr.put(obj)
        }
        bookmarksPrefs.edit().putString("bookmarks_$bookFilename", arr.toString()).apply()
    }
}
