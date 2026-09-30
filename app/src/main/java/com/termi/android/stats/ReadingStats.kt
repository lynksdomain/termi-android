package com.termi.android.stats

import java.util.UUID

data class BookReadStats(
    val id: UUID = UUID.randomUUID(),
    val filename: String,
    val title: String,
    val author: String,
    val sessions: Int,
    val totalPages: Int,
    val totalSeconds: Double,
)

data class AuthorReadStats(
    val id: UUID = UUID.randomUUID(),
    val author: String,
    val topBook: String,
    val sessions: Int,
    val totalPages: Int,
    val totalSeconds: Double,
)

data class ReadingStats(
    val periodLabel: String,
    val totalSessions: Int,
    val totalPages: Int,
    val totalSeconds: Double,
    val uniqueBooks: Int,
    val uniqueAuthors: Int,
    val averageDailyMinutes: Double,
    val longestSession: Double,
    val mostActiveHour: Int?,
    val topBooks: List<BookReadStats>,
    val topAuthors: List<AuthorReadStats>,
    val mostReadDay: String?,
)
