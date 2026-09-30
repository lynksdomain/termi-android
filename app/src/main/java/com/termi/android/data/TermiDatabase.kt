package com.termi.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.termi.android.music.Playlist
import com.termi.android.music.PlaylistDao

@Database(
    entities = [WritingEvent::class, ReadingEvent::class, Playlist::class, ListenEvent::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TermiDatabase : RoomDatabase() {
    abstract fun writingEventDao(): WritingEventDao
    abstract fun readingEventDao(): ReadingEventDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun listenEventDao(): ListenEventDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS playlists (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, playlistDescription TEXT NOT NULL, songPaths TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS listen_events (id TEXT NOT NULL PRIMARY KEY, songTitle TEXT NOT NULL, artist TEXT NOT NULL, album TEXT NOT NULL, playedAt INTEGER NOT NULL, songDurationSeconds REAL NOT NULL, secondsListened REAL NOT NULL)")
            }
        }

        @Volatile private var instance: TermiDatabase? = null

        fun getInstance(context: Context): TermiDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TermiDatabase::class.java,
                    "termi.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { instance = it }
            }
    }
}
