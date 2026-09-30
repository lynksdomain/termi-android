package com.termi.android.books

import java.util.UUID

// Kotlin port of BookGroup.swift. `isExpanded` is kept for parity with the
// persisted iOS field but — same as iOS — the current drill-down UI never
// reads it; it's legacy from an older expand/collapse design.
data class BookGroup(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val groupDescription: String = "",
    val bookFilenames: List<String> = emptyList(),
    val isExpanded: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
