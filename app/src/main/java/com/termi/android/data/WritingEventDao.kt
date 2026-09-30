package com.termi.android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WritingEventDao {
    @Query("SELECT * FROM writing_events ORDER BY date ASC")
    fun observeAll(): Flow<List<WritingEvent>>

    @Insert
    suspend fun insert(event: WritingEvent)

    @Query("DELETE FROM writing_events")
    suspend fun deleteAll()

    @Query("DELETE FROM writing_events WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<java.util.UUID>)
}
