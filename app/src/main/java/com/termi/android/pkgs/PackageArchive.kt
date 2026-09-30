package com.termi.android.pkgs

import java.io.File
import com.termi.android.filesystem.ImportLimits
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

// Kotlin port of the `.termi` container format from iOS's
// Engine/PackageArchive.swift — but genuinely simpler here: iOS had to
// hand-roll its own ZIP reader/writer (local file headers, central
// directory, EOCD, CRC32) since Foundation has no zip archive API.
// `java.util.zip` is a real built-in library, so this is just a thin
// layout convention on top of it:
//
//   manifest.json          — PackageManifest
//   media/mixtape.m4a       — fused audio (optional; written separately by
//                             MixtapeComposer)
//   media/tracklist.json    — [PackageTrackInfo] (only if mixtape present)
//   media/messages.json     — [PackageMixtapeMessage] (only if non-empty)
//   quote.json              — PackageBookQuote (optional)
//   note.json               — PackageWritingNote (optional)
//
// No encryption at the container level, matching iOS's deliberate choice —
// the security boundary is storage location (an app-private folder,
// never exposed the way the normal library-import folder is), not crypto.
object PackageArchive {

    fun write(
        outputFile: File,
        manifest: PackageManifest,
        mixtapeSource: File? = null,
        tracklist: List<PackageTrackInfo> = emptyList(),
        messages: List<PackageMixtapeMessage> = emptyList(),
        quote: PackageBookQuote? = null,
        note: PackageWritingNote? = null,
    ) {
        ZipOutputStream(outputFile.outputStream().buffered()).use { zip ->
            writeJsonEntry(zip, "manifest.json", manifestToJson(manifest))
            if (mixtapeSource != null && mixtapeSource.isFile) {
                writeFileEntry(zip, "media/mixtape.m4a", mixtapeSource)
                writeJsonEntry(zip, "media/tracklist.json", tracklistToJson(tracklist))
                if (messages.isNotEmpty()) {
                    writeJsonEntry(zip, "media/messages.json", messagesToJson(messages))
                }
            }
            quote?.let { writeJsonEntry(zip, "quote.json", quoteToJson(it)) }
            note?.let { writeJsonEntry(zip, "note.json", noteToJson(it)) }
        }
    }

    // `extractMixtapeTo`, if given, is where media/mixtape.m4a (when
    // present) gets copied out to — the archive itself isn't kept open for
    // playback, mirroring PackageStorageManager's real-file-on-disk model
    // rather than streaming audio out of a held-open zip.
    fun read(file: File, extractMixtapeTo: File? = null): ParsedPackage? {
        return try {
            require(file.length() <= ImportLimits.MAX_TRANSFER_BYTES) { "Package too large" }
            ZipFile(file).use { zip ->
                ImportLimits.validateArchive(zip)
                val manifestEntry = zip.getEntry("manifest.json") ?: return null
                val manifest = parseManifest(readEntryText(zip, manifestEntry) ?: return null) ?: return null

                var mixtapeFile: File? = null
                val mixtapeEntry = zip.getEntry("media/mixtape.m4a")
                if (mixtapeEntry != null && extractMixtapeTo != null) {
                    zip.getInputStream(mixtapeEntry).use { input ->
                        extractMixtapeTo.outputStream().use { output -> ImportLimits.copyBounded(input, output, ImportLimits.MAX_TRANSFER_BYTES) }
                    }
                    mixtapeFile = extractMixtapeTo
                }

                val tracklist = zip.getEntry("media/tracklist.json")
                    ?.let { readEntryText(zip, it) }
                    ?.let { parseTracklist(it) }
                    ?: emptyList()
                val messages = zip.getEntry("media/messages.json")
                    ?.let { readEntryText(zip, it) }
                    ?.let { parseMessages(it) }
                    ?: emptyList()
                val quote = zip.getEntry("quote.json")?.let { readEntryText(zip, it) }?.let { parseQuote(it) }
                val note = zip.getEntry("note.json")?.let { readEntryText(zip, it) }?.let { parseNote(it) }

                ParsedPackage(manifest, mixtapeFile, tracklist, messages, quote, note)
            }
        } catch (e: Exception) {
            extractMixtapeTo?.delete()
            null
        }
    }

    // ── Zip helpers ──────────────────────────────────────────────

    private fun writeJsonEntry(zip: ZipOutputStream, path: String, json: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(json.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun writeFileEntry(zip: ZipOutputStream, path: String, source: File) {
        zip.putNextEntry(ZipEntry(path))
        source.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun readEntryText(zip: ZipFile, entry: ZipEntry): String? =
        ImportLimits.readText(zip.getInputStream(entry))

    // ── JSON encode ──────────────────────────────────────────────

    // Field names here are dictated by real iOS Codable output (verified
    // against Engine/PackageStorageManager.swift + Models/TermiPackage.swift
    // directly, not guessed) — this is the actual cross-platform wire
    // format, distinct from this Kotlin model's own internal property
    // names. Real iOS uses a NESTED "slots": {music,book,writing} object
    // (not flat hasMusic/hasBook/hasWriting) and "senderName"/
    // "recipientName" (not senderSignature/recipientSignature) — a real
    // bug caught on first actual cross-device test 2026-08-24: Android
    // read a real iOS-sent mixtape package and silently treated it as
    // content-free ("nothing there to play") because hasMusic defaulted to
    // false when the flat key it was looking for didn't exist.
    private fun manifestToJson(m: PackageManifest): String = JSONObject().apply {
        put("id", m.id.toString())
        put("formatVersion", m.formatVersion)
        put("title", m.title)
        put("description", m.description)
        put("senderName", m.senderSignature)
        put("recipientName", m.recipientSignature ?: JSONObject.NULL)
        put("createdAt", DateTimeFormatter.ISO_INSTANT.format(m.createdAt))
        put("mood", m.mood)
        put(
            "slots",
            JSONObject().apply {
                put("music", m.hasMusic)
                put("book", m.hasBook)
                put("writing", m.hasWriting)
            },
        )
    }.toString()

    private fun tracklistToJson(tracks: List<PackageTrackInfo>): String {
        val arr = JSONArray()
        tracks.forEach { t ->
            arr.put(
                JSONObject().apply {
                    put("title", t.title)
                    put("artist", t.artist)
                    put("startOffset", t.startOffsetSeconds)
                },
            )
        }
        return arr.toString()
    }

    private fun messagesToJson(messages: List<PackageMixtapeMessage>): String {
        val arr = JSONArray()
        messages.forEach { msg ->
            arr.put(
                JSONObject().apply {
                    put("id", msg.id.toString())
                    put("timestamp", msg.timestampSeconds)
                    put("text", msg.text)
                },
            )
        }
        return arr.toString()
    }

    private fun quoteToJson(q: PackageBookQuote): String = JSONObject().apply {
        put("text", q.text)
        put("bookTitle", q.bookTitle)
        put("author", q.author)
    }.toString()

    private fun noteToJson(n: PackageWritingNote): String = JSONObject().apply {
        put("text", n.text)
        put("kind", n.kind)
        put("sourceEntryTitle", n.sourceEntryTitle ?: JSONObject.NULL)
    }.toString()

    // ── JSON decode ──────────────────────────────────────────────

    private fun parseManifest(json: String): PackageManifest? = try {
        val o = JSONObject(json)
        // "slots" is real iOS's nested shape; fall back to Android's own
        // older flat hasMusic/hasBook/hasWriting keys too, so packages
        // written by earlier Android builds (before this fix) still parse.
        val slots = o.optJSONObject("slots")
        PackageManifest(
            id = UUID.fromString(o.getString("id")),
            formatVersion = o.optInt("formatVersion", 1),
            title = o.optString("title"),
            description = o.optString("description"),
            senderSignature = if (o.has("senderName")) o.optString("senderName") else o.optString("senderSignature"),
            recipientSignature = when {
                o.has("recipientName") -> if (o.isNull("recipientName")) null else o.optString("recipientName")
                o.has("recipientSignature") -> if (o.isNull("recipientSignature")) null else o.optString("recipientSignature")
                else -> null
            },
            createdAt = Instant.parse(o.getString("createdAt")),
            mood = o.optString("mood", "friendly"),
            hasMusic = slots?.optBoolean("music", false) ?: o.optBoolean("hasMusic", false),
            hasBook = slots?.optBoolean("book", false) ?: o.optBoolean("hasBook", false),
            hasWriting = slots?.optBoolean("writing", false) ?: o.optBoolean("hasWriting", false),
        )
    } catch (e: Exception) {
        null
    }

    private fun parseTracklist(json: String): List<PackageTrackInfo> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            PackageTrackInfo(
                title = o.optString("title"),
                artist = o.optString("artist"),
                startOffsetSeconds = if (o.has("startOffset")) o.optDouble("startOffset", 0.0) else o.optDouble("startOffsetSeconds", 0.0),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun parseMessages(json: String): List<PackageMixtapeMessage> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            PackageMixtapeMessage(
                id = UUID.fromString(o.getString("id")),
                timestampSeconds = if (o.has("timestamp")) o.optDouble("timestamp", 0.0) else o.optDouble("timestampSeconds", 0.0),
                text = o.optString("text"),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun parseQuote(json: String): PackageBookQuote? = try {
        val o = JSONObject(json)
        PackageBookQuote(text = o.optString("text"), bookTitle = o.optString("bookTitle"), author = o.optString("author"))
    } catch (e: Exception) {
        null
    }

    private fun parseNote(json: String): PackageWritingNote? = try {
        val o = JSONObject(json)
        PackageWritingNote(
            text = o.optString("text"),
            kind = o.optString("kind"),
            sourceEntryTitle = if (o.isNull("sourceEntryTitle")) null else o.optString("sourceEntryTitle"),
        )
    } catch (e: Exception) {
        null
    }
}
