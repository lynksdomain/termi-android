package com.termi.android

import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ServiceTestRule
import com.termi.android.music.PlaybackService
import com.termi.android.music.Song
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackServiceTest {
    @get:Rule val serviceRule = ServiceTestRule()
    @Test fun musicAndMixtapesContinueInBackgroundAndRespondToSystemControls() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = writeSilentWav(File(context.cacheDir, "playback-test.wav"), 30)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val binder = serviceRule.bindService(Intent(context, PlaybackService::class.java).setAction(PlaybackService.BIND_UI)) as PlaybackService.LocalBinder
        val service = binder.service
        fun await(condition: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 10_000
            while (SystemClock.elapsedRealtime() < deadline) {
                var result = false
                instrumentation.runOnMainSync { result = condition() }
                if (result) return
                SystemClock.sleep(100)
            }
            fail("Playback state did not arrive within 10 seconds")
        }
        try {
            instrumentation.runOnMainSync {
                service.music.loadQueue(listOf(Song(file = file, title = "Test track", artist = "Termi", album = "Test", durationMs = 30_000)))
                service.music.play()
            }
            await { service.music.player.isPlaying }
            scenario.moveToState(Lifecycle.State.CREATED)
            SystemClock.sleep(500)
            await { service.music.player.isPlaying && service.music.player.currentPosition > 250 }
            // Connect through Media3's actual session rather than calling UI controls.
            val token = androidx.media3.session.SessionToken(context, android.content.ComponentName(context, PlaybackService::class.java))
            lateinit var future: com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaController>
            instrumentation.runOnMainSync { future = androidx.media3.session.MediaController.Builder(context, token).buildAsync() }
            val controller = future.get(10, java.util.concurrent.TimeUnit.SECONDS)
            try {
                instrumentation.runOnMainSync {
                    assertEquals("Test track", controller.mediaMetadata.title.toString())
                    controller.pause()
                }
                await { !service.music.player.isPlaying }
                scenario.moveToState(Lifecycle.State.RESUMED)
                instrumentation.runOnMainSync {
                    service.mixtape.load(java.util.UUID.randomUUID(), file, emptyList(), emptyList(), "Test mixtape") { service.music.pause() }
                }
                await { service.mixtape.player.isPlaying }
                scenario.moveToState(Lifecycle.State.CREATED)
                await { service.mixtape.player.isPlaying && !service.music.player.isPlaying }
                instrumentation.runOnMainSync { controller.pause() }
                await { !service.mixtape.player.isPlaying }
            } finally { instrumentation.runOnMainSync { controller.release() } }
        } finally {
            instrumentation.runOnMainSync { service.music.pause(); service.mixtape.stop() }
            scenario.close()
            file.delete()
        }
    }
}
