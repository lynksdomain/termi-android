package com.termi.android

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.termi.android.data.TermiDatabase
import org.junit.Assert.*
import org.junit.Test

class DatabaseMigrationTest {
    @Test fun upgradesLegacyVersionsWithoutLosingStatsOrPlaylists() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (version in listOf(1, 2)) {
            val name = "migration-test-$version.db"
            context.deleteDatabase(name)
            SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
                db.execSQL("CREATE TABLE writing_events (id TEXT NOT NULL PRIMARY KEY, date INTEGER NOT NULL, durationSeconds REAL NOT NULL, wordsAdded INTEGER NOT NULL, filename TEXT NOT NULL)")
                db.execSQL("CREATE TABLE reading_events (id TEXT NOT NULL PRIMARY KEY, bookFilename TEXT NOT NULL, bookTitle TEXT NOT NULL, bookAuthor TEXT NOT NULL, sessionStart INTEGER NOT NULL, sessionEnd INTEGER NOT NULL, pagesRead INTEGER NOT NULL, chapterIndex INTEGER NOT NULL)")
                db.execSQL("INSERT INTO writing_events VALUES ('11111111-1111-1111-1111-111111111111', 12345, 60, 42, 'journal.txt')")
                db.execSQL("INSERT INTO reading_events VALUES ('22222222-2222-2222-2222-222222222222', 'book.epub', 'Book', 'Author', 100, 200, 3, 0)")
                if (version == 2) {
                    db.execSQL("CREATE TABLE playlists (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, playlistDescription TEXT NOT NULL, songPaths TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                    db.execSQL("INSERT INTO playlists VALUES ('33333333-3333-3333-3333-333333333333', 'Favorites', '', '/music/song.mp3', 100, 200)")
                }
                db.version = version
            }
            val upgraded = Room.databaseBuilder(context, TermiDatabase::class.java, name)
                .addMigrations(TermiDatabase.MIGRATION_1_2, TermiDatabase.MIGRATION_2_3).build()
            try {
                val db = upgraded.openHelper.writableDatabase // Room validates the final schema.
                db.query("SELECT wordsAdded FROM writing_events").use { assertTrue(it.moveToFirst()); assertEquals(42, it.getInt(0)) }
                db.query("SELECT pagesRead FROM reading_events").use { assertTrue(it.moveToFirst()); assertEquals(3, it.getInt(0)) }
                if (version == 2) db.query("SELECT title FROM playlists").use { assertTrue(it.moveToFirst()); assertEquals("Favorites", it.getString(0)) }
            } finally { upgraded.close(); context.deleteDatabase(name) }
        }
    }
}
