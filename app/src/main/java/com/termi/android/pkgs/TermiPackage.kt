package com.termi.android.pkgs

import java.time.Instant
import java.util.UUID

// Kotlin port of the data shapes in iOS's Models/TermiPackage.swift — the
// content a `.termi` container can carry. All three content slots are
// optional; presence is tracked via the manifest's has* flags rather than
// null-checking the parsed result, matching iOS.

data class PackageManifest(
    val id: UUID = UUID.randomUUID(),
    val formatVersion: Int = 1,
    val title: String,
    val description: String = "",
    val senderSignature: String,
    val recipientSignature: String? = null,
    val createdAt: Instant = Instant.now(),
    // "romantic" | "friendly" | "cool" — drives the kaomoji flavor on the
    // discovery/accept/completion ritual screens, matching iOS.
    val mood: String = "friendly",
    val hasMusic: Boolean = false,
    val hasBook: Boolean = false,
    val hasWriting: Boolean = false,
)

data class PackageTrackInfo(
    val title: String,
    val artist: String,
    val startOffsetSeconds: Double,
)

// A listener-pinned note on the fused mixtape — max 10 per package, 120
// chars, ≥8s apart, matching iOS's MixtapeComposer constraints (enforced
// by whatever builds the list, not by this container format itself).
data class PackageMixtapeMessage(
    val id: UUID = UUID.randomUUID(),
    val timestampSeconds: Double,
    val text: String,
)

data class PackageBookQuote(
    val text: String,
    val bookTitle: String,
    val author: String,
)

data class PackageWritingNote(
    val text: String,
    // "excerpt" (a whole existing Journal entry) | "fresh" (composed for
    // this package, not saved anywhere else) — matches iOS's decision to
    // not offer passage-level excerpting after real-device QA friction.
    val kind: String,
    val sourceEntryTitle: String? = null,
)

// The fully materialized contents of an opened `.termi` container —
// PackageArchive.read()'s return shape.
data class ParsedPackage(
    val manifest: PackageManifest,
    val mixtapeFile: java.io.File?,
    val tracklist: List<PackageTrackInfo>,
    val messages: List<PackageMixtapeMessage>,
    val quote: PackageBookQuote?,
    val note: PackageWritingNote?,
)
