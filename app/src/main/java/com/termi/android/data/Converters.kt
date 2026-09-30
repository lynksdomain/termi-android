package com.termi.android.data

import androidx.room.TypeConverter
import java.time.Instant
import java.util.UUID

class Converters {
    @TypeConverter
    fun uuidToString(id: UUID): String = id.toString()

    @TypeConverter
    fun stringToUuid(value: String): UUID = UUID.fromString(value)

    @TypeConverter
    fun instantToEpochMillis(instant: Instant): Long = instant.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(millis: Long): Instant = Instant.ofEpochMilli(millis)

    //  (unit separator) as the join delimiter — won't appear in file paths.
    private val listDelimiter = ""

    @TypeConverter
    fun stringListToString(list: List<String>): String = list.joinToString(listDelimiter)

    @TypeConverter
    fun stringToStringList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split(listDelimiter)
}
