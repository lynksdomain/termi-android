package com.termi.android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingEventDao {
    @Query("SELECT * FROM reading_events ORDER BY sessionStart DESC")
    fun observeAll(): Flow<List<ReadingEvent>>

    @Insert
    suspend fun insert(event: ReadingEvent)

    @Query("DELETE FROM reading_events")
    suspend fun deleteAll()

    @Query("DELETE FROM reading_events WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<UUID>)
}
