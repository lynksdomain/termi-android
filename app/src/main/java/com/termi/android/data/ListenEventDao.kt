package com.termi.android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface ListenEventDao {
    @Query("SELECT * FROM listen_events ORDER BY playedAt DESC")
    fun observeAll(): Flow<List<ListenEvent>>

    @Insert
    suspend fun insert(event: ListenEvent)

    @Update
    suspend fun update(event: ListenEvent)

    @Query("DELETE FROM listen_events")
    suspend fun deleteAll()

    @Query("DELETE FROM listen_events WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<UUID>)
}
