package com.termi.android.writing

import java.time.Instant
import java.util.UUID

// Kotlin port of WritingGroup.swift. A "group" is a virtual tag, not a real
// filesystem folder — it's a list of filenames persisted separately (iOS used
// UserDefaults+JSON; here we use SharedPreferences+JSON, same shape).
data class WritingGroup(
    val id: UUID = UUID.randomUUID(),
    var name: String,
    var groupDescription: String = "",
    var documentFilenames: MutableList<String> = mutableListOf(),
    val createdAt: Instant = Instant.now(),
)
