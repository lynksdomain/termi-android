package com.termi.android

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.termi.android.data.TermiDatabase
import com.termi.android.stats.WritingStatsManager
import com.termi.android.ui.WritingEditorScreen
import com.termi.android.writing.WritingDocument
import com.termi.android.writing.WritingManager
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class JournalSaveTest {
    @get:Rule val compose = createComposeRule()

    @Test fun disposalImmediatelyAfterTypingPreservesLatestText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "journal-save-test.txt").apply { writeText("original") }
        val db = Room.inMemoryDatabaseBuilder(context, TermiDatabase::class.java).build()
        val visible = mutableStateOf(true)
        try {
            val document = WritingDocument.from(file)
            val manager = WritingManager(context)
            val stats = WritingStatsManager(db.writingEventDao())
            compose.setContent {
                if (visible.value) WritingEditorScreen(document, manager, stats, onClose = { visible.value = false })
            }
            compose.onNode(hasSetTextAction()).performTextReplacement("new words that must survive")
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            assertEquals("new words that must survive", file.readText())
        } finally { db.close(); file.delete() }
    }
}
