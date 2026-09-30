package com.termi.android.writing

import android.content.Context
import java.io.File
import java.time.Instant
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
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// Kotlin port of WritingManager.swift. Documents are plain files (see
// WritingFolderManager); groups are a virtual tag layer persisted as JSON in
// SharedPreferences, matching the iOS UserDefaults+JSON approach 1:1.
class WritingManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val prefs = context.getSharedPreferences("termi_writing", Context.MODE_PRIVATE)
    private val groupsKey = "writingGroups_v1"
    // Documents are copied into one owned folder (see WritingFolderManager),
    // so there's no live external path to derive a source from at load
    // time — this remembers, per filename, which WritingSource a document
    // was imported from.
    private val sourceMapPrefs = context.getSharedPreferences("termi_writing_source_map", Context.MODE_PRIVATE)
    private val sourcesPrefs = context.getSharedPreferences("termi_writing_sources", Context.MODE_PRIVATE)

    private val _documents = MutableStateFlow<List<WritingDocument>>(emptyList())
    val documents: StateFlow<List<WritingDocument>> = _documents.asStateFlow()

    private val _groups = MutableStateFlow<List<WritingGroup>>(emptyList())
    val groups: StateFlow<List<WritingGroup>> = _groups.asStateFlow()

    private val _sources = MutableStateFlow<List<WritingSource>>(emptyList())
    val sources: StateFlow<List<WritingSource>> = _sources.asStateFlow()

    private var autoSaveJob: Job? = null

    val ungroupedDocuments: List<WritingDocument>
        get() {
            val grouped = _groups.value.flatMap { it.documentFilenames }.toSet()
            return _documents.value.filter { it.file.name !in grouped }
        }

    // Real disk I/O (folder scan + per-file metadata read) — used to run
    // synchronously at construction time (`WritingManager(context).also {
    // it.loadDocuments() }` in TermiNavHost), blocking the very first
    // composed frame. Now suspend + off the main thread; call from a
    // coroutine instead.
    suspend fun loadDocuments() {
        withContext(Dispatchers.IO) { WritingFolderManager.writingFolder(context) }
        loadGroupsFromDisk()
        _sources.value = loadSources()
        _documents.value = withContext(Dispatchers.IO) {
            WritingFolderManager.scanForDocuments(context)
                .map { WritingDocument.from(it, sourceRootPathFor(it.name)) }
                .sortedByDescending { it.lastModified }
        }
    }

    // `loadDocuments()` only ever runs once, at construction (see
    // TermiNavHost) — nothing tells this manager when WiFi Transfer drops a
    // new file straight into the same folder mid-session, so without this a
    // transferred entry wouldn't show up until the app restarted. Same gap
    // as MusicLibraryManager.refreshInbox/BookLibraryManager.
    // refreshForNewFiles; call from the screen whenever
    // WiFiTransferServer.uploadLog changes.
    fun refreshForNewFiles() {
        val known = _documents.value.map { it.file.absolutePath }.toSet()
        val newDocs = WritingFolderManager.scanForDocuments(context)
            .filterNot { it.absolutePath in known }
            .map { WritingDocument.from(it, sourceRootPathFor(it.name)) }
        if (newDocs.isEmpty()) return
        _documents.value = (_documents.value + newDocs).sortedByDescending { it.lastModified }
    }

    fun createDocument(title: String = "untitled"): WritingDocument {
        val file = WritingFolderManager.createDocument(context, title)
        val doc = WritingDocument.from(file)
        _documents.value = listOf(doc) + _documents.value
        return doc
    }

    fun importDocuments(sources: List<File>, sourceRootPath: String? = null) {
        val imported = sources.mapNotNull { WritingFolderManager.importFile(context, it) }
            .map { file ->
                if (sourceRootPath != null) setSourceRootPath(file.name, sourceRootPath)
                WritingDocument.from(file, sourceRootPath)
            }
        _documents.value = (imported + _documents.value).sortedByDescending { it.lastModified }
    }

    // Deliberately clears the source-map entry (mirrors BookLibraryManager.
    // removeBook) so a re-import gets a fresh chance to be (re-)tagged.
    fun deleteDocument(document: WritingDocument) {
        WritingFolderManager.delete(document.file)
        _documents.value = _documents.value.filter { it.id != document.id }
        val filename = document.file.name
        _groups.value = _groups.value.map { g ->
            g.copy(documentFilenames = g.documentFilenames.filter { it != filename }.toMutableList())
        }
        saveGroupsToDisk()
        clearSourceRootPath(filename)
    }

    // MARK: - Sources (folder-of-origin tracking; see WritingSource)

    fun itemCount(source: WritingSource): Int =
        _documents.value.count { it.sourceRootPath == source.rootPath }

    fun registerSource(root: File) {
        val rootPath = root.absolutePath
        val stored = _sources.value.toMutableList()
        if (stored.any { it.rootPath == rootPath }) return
        stored.add(WritingSource(rootPath = rootPath, label = root.name.ifBlank { rootPath }.uppercase()))
        _sources.value = stored
        saveSources(stored)
    }

    // Full destructive parity: every document copied from this source has
    // its real on-disk copy deleted (not just forgotten) — same semantics
    // as Books/Music's owned-folder removal.
    fun removeSource(source: WritingSource) {
        val affected = _documents.value.filter { it.sourceRootPath == source.rootPath }
        affected.forEach { doc -> deleteDocument(doc) }
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

    private fun loadSources(): List<WritingSource> {
        val json = sourcesPrefs.getString("sources", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val obj = arr.getJSONObject(it)
                WritingSource(rootPath = obj.getString("rootPath"), label = obj.getString("label"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveSources(sources: List<WritingSource>) {
        val arr = JSONArray()
        sources.forEach { source ->
            arr.put(JSONObject().apply { put("rootPath", source.rootPath); put("label", source.label) })
        }
        sourcesPrefs.edit().putString("sources", arr.toString()).apply()
    }

    fun renameDocument(document: WritingDocument, title: String): WritingDocument? {
        val oldFilename = document.file.name
        val newFile = WritingFolderManager.rename(context, document.file, title) ?: return null
        val updated = WritingDocument.from(newFile)
        _documents.value = _documents.value.map { if (it.id == document.id) updated else it }
        val newFilename = newFile.name
        _groups.value = _groups.value.map { g ->
            g.copy(
                documentFilenames = g.documentFilenames
                    .map { if (it == oldFilename) newFilename else it }
                    .toMutableList(),
            )
        }
        saveGroupsToDisk()
        return updated
    }

    // MARK: - Group CRUD

    fun createGroup(name: String, description: String = "", documentFilenames: List<String> = emptyList()) {
        val group = WritingGroup(name = name, groupDescription = description, documentFilenames = documentFilenames.toMutableList())
        _groups.value = _groups.value + group
        saveGroupsToDisk()
    }

    fun updateGroup(group: WritingGroup, name: String, description: String, documentFilenames: List<String>) {
        _groups.value = _groups.value.map {
            if (it.id == group.id) {
                it.copy(name = name, groupDescription = description, documentFilenames = documentFilenames.toMutableList())
            } else {
                it
            }
        }
        saveGroupsToDisk()
    }

    fun deleteGroup(group: WritingGroup) {
        _groups.value = _groups.value.filter { it.id != group.id }
        saveGroupsToDisk()
    }

    fun addDocument(document: WritingDocument, to: WritingGroup) {
        val filename = document.file.name
        _groups.value = _groups.value.map {
            if (it.id == to.id && filename !in it.documentFilenames) {
                it.copy(documentFilenames = (it.documentFilenames + filename).toMutableList())
            } else {
                it
            }
        }
        saveGroupsToDisk()
    }

    fun removeDocument(document: WritingDocument, from: WritingGroup) {
        val filename = document.file.name
        _groups.value = _groups.value.map {
            if (it.id == from.id) {
                it.copy(documentFilenames = it.documentFilenames.filter { f -> f != filename }.toMutableList())
            } else {
                it
            }
        }
        saveGroupsToDisk()
    }

    fun documentsIn(group: WritingGroup): List<WritingDocument> =
        group.documentFilenames.mapNotNull { fn -> _documents.value.firstOrNull { it.file.name == fn } }

    // MARK: - Save

    fun saveContent(text: String, document: WritingDocument) {
        WritingFolderManager.writeContent(text, document.file)
        _documents.value = _documents.value.map {
            if (it.id == document.id) {
                it.copy(
                    wordCount = WritingFolderManager.wordCount(text),
                    lastModified = Instant.now(),
                    content = text,
                )
            } else {
                it
            }
        }
    }

    fun scheduledSave(text: String, document: WritingDocument) {
        autoSaveJob?.cancel()
        autoSaveJob = scope.launch {
            delay(1500)
            saveContent(text, document)
        }
    }

    fun flushSave(text: String, document: WritingDocument) {
        autoSaveJob?.cancel()
        autoSaveJob = null
        saveContent(text, document)
    }

    // MARK: - Group persistence (JSON in SharedPreferences, mirrors UserDefaults)

    private fun saveGroupsToDisk() {
        val array = JSONArray()
        for (g in _groups.value) {
            val obj = JSONObject()
            obj.put("id", g.id.toString())
            obj.put("name", g.name)
            obj.put("groupDescription", g.groupDescription)
            obj.put("documentFilenames", JSONArray(g.documentFilenames))
            obj.put("createdAt", g.createdAt.toEpochMilli())
            array.put(obj)
        }
        prefs.edit().putString(groupsKey, array.toString()).apply()
    }

    private fun loadGroupsFromDisk() {
        val json = prefs.getString(groupsKey, null) ?: return
        val array = try { JSONArray(json) } catch (e: Exception) { return }
        val loaded = mutableListOf<WritingGroup>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val filenames = mutableListOf<String>()
            val fnArray = obj.getJSONArray("documentFilenames")
            for (j in 0 until fnArray.length()) filenames.add(fnArray.getString(j))
            loaded.add(
                WritingGroup(
                    id = UUID.fromString(obj.getString("id")),
                    name = obj.getString("name"),
                    groupDescription = obj.optString("groupDescription", ""),
                    documentFilenames = filenames,
                    createdAt = Instant.ofEpochMilli(obj.getLong("createdAt")),
                ),
            )
        }
        _groups.value = loaded
    }
}
