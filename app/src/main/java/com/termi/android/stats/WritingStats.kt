package com.termi.android.stats

import com.termi.android.data.WritingEvent
import java.util.UUID

data class DocumentWordStats(
    val id: UUID = UUID.randomUUID(),
    val filename: String,
    val totalWords: Int,
)

data class WritingStats(
    val periodLabel: String,
    val totalWords: Int,
    val totalMinutes: Int,
    val totalSessions: Int,
    val avgWordsPerSession: Int,
    val documentCount: Int,
    val topDocuments: List<DocumentWordStats>,
    val recentSessions: List<WritingEvent>,
)
