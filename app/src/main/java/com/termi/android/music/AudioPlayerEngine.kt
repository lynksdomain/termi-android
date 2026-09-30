package com.termi.android.music

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// Library playback engine owned by PlaybackService. ExoPlayer provides queue,
// shuffle/repeat, audio focus, and noisy-output handling. The service owns the
// media session and foreground notification; FFT data feeds VisualizerEngine.
// Gain normalization and sleep-timer fade-out remain deferred.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioPlayerEngine(context: Context, private val requestPlay: () -> Unit) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Tapped directly into ExoPlayer's own audio-sink pipeline (see
    // buildAudioSink override below) instead of going through
    // android.media.audiofx.Visualizer, which some devices' audio HALs
    // don't support at all (see FftTapAudioProcessor's header comment).
    val fftTap = FftTapAudioProcessor()

    val player: ExoPlayer = ExoPlayer.Builder(
        context,
        object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessorChain(DefaultAudioSink.DefaultAudioProcessorChain(fftTap))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        },
    ).build().apply {
        setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
        setHandleAudioBecomingNoisy(true)
    }
    val sleepTimer = SleepTimerManager().also { it.onExpiry = { pause() } }

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isShuffled = MutableStateFlow(false)
    val isShuffled: StateFlow<Boolean> = _isShuffled.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatModeState: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private var positionPollJob: Job? = null

    // Set by TermiNavHost to wire MusicStatsManager without this playback
    // engine needing to know stats exist at all. Fires once per track
    // change (skip, auto-advance, new queue loaded) with the OUTGOING
    // song + its last known elapsed position (from the position-poll
    // below — good enough for stats, same as iOS's own approximate
    // secondsListened tracking) and the INCOMING song.
    var onSongTransition: ((previous: Song?, previousElapsedMs: Long, next: Song?) -> Unit)? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                _isPlaying.value = isPlayingNow
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val previousSong = _currentSong.value
                val previousElapsedMs = _currentPositionMs.value
                val index = player.currentMediaItemIndex
                _currentIndex.value = index
                _currentSong.value = _queue.value.getOrNull(index)
                onSongTransition?.invoke(previousSong, previousElapsedMs, _currentSong.value)
            }
        })
        positionPollJob = scope.launch {
            while (isActive) {
                _currentPositionMs.value = player.currentPosition.coerceAtLeast(0)
                delay(250)
            }
        }
    }

    fun loadQueue(songs: List<Song>, startingAt: Int = 0) {
        _queue.value = songs
        player.setMediaItems(
            songs.map {
                MediaItem.Builder().setUri(Uri.fromFile(it.file)).setMediaId(it.file.absolutePath)
                    .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(it.title).setArtist(it.artist).setAlbumTitle(it.album).build())
                    .build()
            },
            startingAt.coerceIn(0, (songs.size - 1).coerceAtLeast(0)),
            0L,
        )
        player.prepare()
    }

    fun togglePlayPause() {
        if (player.isPlaying) pause() else play()
    }

    fun play() = requestPlay()
    fun pause() = player.pause()

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun next() {
        if (player.hasNextMediaItem()) player.seekToNextMediaItem()
    }

    fun previous() {
        // Mirrors iOS: restart current track if >2s elapsed, else go to previous.
        if (player.currentPosition > 2000 || !player.hasPreviousMediaItem()) {
            player.seekTo(0)
        } else {
            player.seekToPreviousMediaItem()
        }
    }

    fun setShuffleEnabled(enabled: Boolean) {
        _isShuffled.value = enabled
        player.shuffleModeEnabled = enabled
    }

    fun toggleShuffle() {
        _isShuffled.value = !_isShuffled.value
        player.shuffleModeEnabled = _isShuffled.value
    }

    fun cycleRepeatMode() {
        val next = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _repeatMode.value = next
        player.repeatMode = when (next) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
    }

    fun release() {
        onSongTransition?.invoke(_currentSong.value, _currentPositionMs.value, null)
        positionPollJob?.cancel()
        sleepTimer.release()
        scope.cancel()
        player.release()
    }
}

enum class RepeatMode { OFF, ALL, ONE }
