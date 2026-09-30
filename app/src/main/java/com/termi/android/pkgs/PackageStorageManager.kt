package com.termi.android.pkgs

import android.content.Context
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

// Kotlin port of PackageStorageManager.swift. Received packages are
// extracted into an app-private folder (never exposed via the file
// browser the way the normal library-import folder is — storage location
// IS the security boundary here, not encryption, matching iOS). Sent
// packages keep only a lightweight receipt — no media persists
// sender-side, matching the "you give a CD, you don't keep the CD" rule:
// this app never holds onto a copy of something it already gave away.
data class PackageSentReceipt(
    val id: UUID = UUID.randomUUID(),
    val title: String,
    val recipientSignature: String?,
    val sentAt: Instant = Instant.now(),
    val mood: String,
    val hasMusic: Boolean,
    val hasBook: Boolean,
    val hasWriting: Boolean,
)

data class ReceivedPackage(
    val manifest: PackageManifest,
    val folder: File,
    val quote: PackageBookQuote?,
    val note: PackageWritingNote?,
    val mixtapeFile: File?,
    val tracklist: List<PackageTrackInfo>,
    val messages: List<PackageMixtapeMessage>,
    // Receipt-time-only metadata (never part of the package contents
    // itself) — the sender's platform, used solely to cosmetically flag a
    // package that crossed from the other platform. Mirrors iOS's
    // PackageReceiptMeta/CrossedPlatformBadge exactly: absent/unknown means
    // "don't flag," not "same platform."
    val senderPlatform: String? = null,
) {
    // A package only gets flagged when it demonstrably crossed FROM the
    // other platform — never for same-platform or unknown-origin receipts.
    val crossedPlatformLabel: String?
        get() = senderPlatform?.takeIf { it.lowercase() != "android" }
}

class PackageStorageManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("termi_packages", Context.MODE_PRIVATE)

    private val _received = MutableStateFlow<List<ReceivedPackage>>(emptyList())
    val received: StateFlow<List<ReceivedPackage>> = _received.asStateFlow()

    private val _sent = MutableStateFlow<List<PackageSentReceipt>>(emptyList())
    val sent: StateFlow<List<PackageSentReceipt>> = _sent.asStateFlow()

    private val receivedRoot: File
        get() = File(context.filesDir, "Packages/Received").apply { mkdirs() }

    fun loadAll() {
        loadSentReceipts()
        val root = receivedRoot
        _received.value = (root.listFiles { f -> f.isDirectory } ?: emptyArray())
            .mapNotNull { folder -> loadReceivedFrom(folder) }
            .sortedByDescending { it.manifest.createdAt }
    }

    private fun loadReceivedFrom(folder: File): ReceivedPackage? {
        val manifestFile = File(folder, "manifest.json")
        if (!manifestFile.isFile) return null
        return try {
            val manifest = manifestFromJson(manifestFile.readText()) ?: return null
            val quoteFile = File(folder, "quote.json")
            val noteFile = File(folder, "note.json")
            val mixtapeFile = File(folder, "mixtape.m4a")
            val tracklistFile = File(folder, "tracklist.json")
            val messagesFile = File(folder, "messages.json")
            val platformFile = File(folder, "sender_platform.txt")
            ReceivedPackage(
                manifest = manifest,
                folder = folder,
                quote = if (quoteFile.isFile) quoteFromJson(quoteFile.readText()) else null,
                note = if (noteFile.isFile) noteFromJson(noteFile.readText()) else null,
                mixtapeFile = if (mixtapeFile.isFile) mixtapeFile else null,
                tracklist = if (tracklistFile.isFile) tracklistFromJson(tracklistFile.readText()) else emptyList(),
                messages = if (messagesFile.isFile) messagesFromJson(messagesFile.readText()) else emptyList(),
                senderPlatform = if (platformFile.isFile) platformFile.readText().trim().ifEmpty { null } else null,
            )
        } catch (e: Exception) {
            null
        }
    }

    // Extracts a just-received `.termi` file (a transient temp copy — see
    // PackageBridgeManager) into its own permanent folder under
    // Packages/Received/<uuid>/, then deletes the transient copy. Returns
    // the manifest title for the ritual's "PACKAGE RECEIVED" line.
    fun ingestReceivedPackage(transientFile: File, senderPlatform: String? = null): String? {
        val parsed = PackageArchive.read(transientFile, extractMixtapeTo = null) ?: return null
        val folder = File(receivedRoot, parsed.manifest.id.toString()).apply { mkdirs() }
        File(folder, "manifest.json").writeText(manifestToJson(parsed.manifest))
        parsed.quote?.let { File(folder, "quote.json").writeText(quoteToJson(it)) }
        parsed.note?.let { File(folder, "note.json").writeText(noteToJson(it)) }
        if (parsed.manifest.hasMusic) {
            PackageArchive.read(transientFile, extractMixtapeTo = File(folder, "mixtape.m4a"))
            if (parsed.tracklist.isNotEmpty()) File(folder, "tracklist.json").writeText(tracklistToJsonPublic(parsed.tracklist))
            if (parsed.messages.isNotEmpty()) File(folder, "messages.json").writeText(messagesToJsonPublic(parsed.messages))
        }
        if (!senderPlatform.isNullOrBlank()) {
            File(folder, "sender_platform.txt").writeText(senderPlatform)
        }
        transientFile.delete()
        loadAll()
        return parsed.manifest.title
    }

    fun recordSent(manifest: PackageManifest) {
        val receipt = PackageSentReceipt(
            title = manifest.title,
            recipientSignature = manifest.recipientSignature,
            mood = manifest.mood,
            hasMusic = manifest.hasMusic,
            hasBook = manifest.hasBook,
            hasWriting = manifest.hasWriting,
        )
        val updated = listOf(receipt) + loadSentReceipts()
        saveSentReceipts(updated)
        _sent.value = updated
    }

    fun deleteReceived(pkg: ReceivedPackage) {
        pkg.folder.deleteRecursively()
        _received.value = _received.value.filterNot { it.manifest.id == pkg.manifest.id }
    }

    private fun loadSentReceipts(): List<PackageSentReceipt> {
        val json = prefs.getString("sentReceipts", null) ?: run { _sent.value = emptyList(); return emptyList() }
        val list = try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PackageSentReceipt(
                    id = UUID.fromString(o.getString("id")),
                    title = o.optString("title"),
                    recipientSignature = if (o.isNull("recipientSignature")) null else o.optString("recipientSignature"),
                    sentAt = Instant.parse(o.getString("sentAt")),
                    mood = o.optString("mood"),
                    hasMusic = o.optBoolean("hasMusic"),
                    hasBook = o.optBoolean("hasBook"),
                    hasWriting = o.optBoolean("hasWriting"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
        _sent.value = list
        return list
    }

    private fun saveSentReceipts(receipts: List<PackageSentReceipt>) {
        val arr = JSONArray()
        receipts.forEach { r ->
            arr.put(
                JSONObject().apply {
                    put("id", r.id.toString())
                    put("title", r.title)
                    put("recipientSignature", r.recipientSignature ?: JSONObject.NULL)
                    put("sentAt", java.time.format.DateTimeFormatter.ISO_INSTANT.format(r.sentAt))
                    put("mood", r.mood)
                    put("hasMusic", r.hasMusic)
                    put("hasBook", r.hasBook)
                    put("hasWriting", r.hasWriting)
                },
            )
        }
        prefs.edit().putString("sentReceipts", arr.toString()).apply()
    }

    // Small private re-encoders mirroring PackageArchive's private JSON
    // helpers — kept separate since PackageArchive's are private to that
    // file and this manager writes loose files, not a zip.
    private fun manifestToJson(m: PackageManifest): String = JSONObject().apply {
        put("id", m.id.toString())
        put("formatVersion", m.formatVersion)
        put("title", m.title)
        put("description", m.description)
        put("senderSignature", m.senderSignature)
        put("recipientSignature", m.recipientSignature ?: JSONObject.NULL)
        put("createdAt", java.time.format.DateTimeFormatter.ISO_INSTANT.format(m.createdAt))
        put("mood", m.mood)
        put("hasMusic", m.hasMusic)
        put("hasBook", m.hasBook)
        put("hasWriting", m.hasWriting)
    }.toString()

    private fun manifestFromJson(json: String): PackageManifest? = try {
        val o = JSONObject(json)
        PackageManifest(
            id = UUID.fromString(o.getString("id")),
            formatVersion = o.optInt("formatVersion", 1),
            title = o.optString("title"),
            description = o.optString("description"),
            senderSignature = o.optString("senderSignature"),
            recipientSignature = if (o.isNull("recipientSignature")) null else o.optString("recipientSignature"),
            createdAt = Instant.parse(o.getString("createdAt")),
            mood = o.optString("mood", "friendly"),
            hasMusic = o.optBoolean("hasMusic"),
            hasBook = o.optBoolean("hasBook"),
            hasWriting = o.optBoolean("hasWriting"),
        )
    } catch (e: Exception) {
        null
    }

    private fun quoteToJson(q: PackageBookQuote): String = JSONObject().apply {
        put("text", q.text); put("bookTitle", q.bookTitle); put("author", q.author)
    }.toString()

    private fun quoteFromJson(json: String): PackageBookQuote? = try {
        val o = JSONObject(json)
        PackageBookQuote(text = o.optString("text"), bookTitle = o.optString("bookTitle"), author = o.optString("author"))
    } catch (e: Exception) {
        null
    }

    private fun noteToJson(n: PackageWritingNote): String = JSONObject().apply {
        put("text", n.text); put("kind", n.kind); put("sourceEntryTitle", n.sourceEntryTitle ?: JSONObject.NULL)
    }.toString()

    private fun noteFromJson(json: String): PackageWritingNote? = try {
        val o = JSONObject(json)
        PackageWritingNote(
            text = o.optString("text"),
            kind = o.optString("kind"),
            sourceEntryTitle = if (o.isNull("sourceEntryTitle")) null else o.optString("sourceEntryTitle"),
        )
    } catch (e: Exception) {
        null
    }

    private fun tracklistToJsonPublic(tracks: List<PackageTrackInfo>): String {
        val arr = JSONArray()
        tracks.forEach { t ->
            arr.put(JSONObject().apply { put("title", t.title); put("artist", t.artist); put("startOffsetSeconds", t.startOffsetSeconds) })
        }
        return arr.toString()
    }

    private fun tracklistFromJson(json: String): List<PackageTrackInfo> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            PackageTrackInfo(title = o.optString("title"), artist = o.optString("artist"), startOffsetSeconds = o.optDouble("startOffsetSeconds", 0.0))
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun messagesToJsonPublic(messages: List<PackageMixtapeMessage>): String {
        val arr = JSONArray()
        messages.forEach { m ->
            arr.put(JSONObject().apply { put("id", m.id.toString()); put("timestampSeconds", m.timestampSeconds); put("text", m.text) })
        }
        return arr.toString()
    }

    private fun messagesFromJson(json: String): List<PackageMixtapeMessage> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            PackageMixtapeMessage(
                id = UUID.fromString(o.getString("id")),
                timestampSeconds = o.optDouble("timestampSeconds", 0.0),
                text = o.optString("text"),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}
