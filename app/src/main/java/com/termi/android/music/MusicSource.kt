package com.termi.android.music

// A user-visible grouping of registered song paths under one root folder —
// what the "MUSIC SOURCES" screen shows and lets the user remove as a unit.
// Reference-in-place means the library's real source of truth is a flat list
// of individual file paths (see MusicLibraryManager); `MusicSource` is the
// layer on top that remembers WHICH folder each batch came from, so
// "remove this source" can mean something instead of only ever removing one
// song at a time. `rootPath` is also the identity/dedupe key.
data class MusicSource(
    val rootPath: String,
    val label: String,
    val isInbox: Boolean = false,
)

// Assigns a song path to the most specific (longest matching root) source —
// needed because sources can legitimately nest or overlap (e.g. the user
// adds /Music, then later /Music/Rock separately); without this a song
// under both would double-count instead of belonging to exactly one.
fun MusicSource.contains(path: String): Boolean =
    path == rootPath || path.startsWith("$rootPath/")

fun List<MusicSource>.bestMatchFor(path: String): MusicSource? =
    filter { it.contains(path) }.maxByOrNull { it.rootPath.length }
