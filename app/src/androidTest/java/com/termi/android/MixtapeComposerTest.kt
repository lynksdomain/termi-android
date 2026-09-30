package com.termi.android

import android.media.MediaMetadataRetriever
import androidx.test.platform.app.InstrumentationRegistry
import com.termi.android.pkgs.MixtapeComposer
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class MixtapeComposerTest {
    @Test fun joinsDifferentSampleRatesAndChannelsWithCorrectOffsets() {
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "mixtape-test").apply { mkdirs() }
        try {
            val first = writeSilentWav(File(folder, "first.wav"), 1, 44100, 2)
            val second = writeSilentWav(File(folder, "second.wav"), 1, 48000, 1)
            val output = File(folder, "joined.m4a")
            val composed = MixtapeComposer.compose(listOf(first, second), output)
            assertEquals(0.0, composed.trackOffsetsSeconds[0], 0.01)
            assertEquals(1.0, composed.trackOffsetsSeconds[1], 0.05)
            val metadata = MediaMetadataRetriever()
            try {
                metadata.setDataSource(output.absolutePath)
                val duration = requireNotNull(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)).toLong()
                assertTrue("Expected roughly 2 seconds, got $duration ms", duration in 1900..2200)
            } finally { metadata.release() }
        } finally { folder.deleteRecursively() }
    }
}
