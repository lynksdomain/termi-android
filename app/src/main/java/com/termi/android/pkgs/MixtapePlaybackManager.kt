package com.termi.android.pkgs

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import java.util.UUID
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

// Kotlin/Media3 port of MixtapePlaybackManager.swift — dedicated playback
// for a received Package's fused mixtape, deliberately separate from
// AudioPlayerEngine (a mixtape has no shuffle, and "next" means jump to the
// next in-file track marker, not pull a new file from a library queue).
// PlaybackService switches its media session to this player when a mixtape
// starts, so background playback and system transport controls work for both.
class MixtapePlaybackManager(context: Context, private val requestPlay: () -> Unit) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
        setHandleAudioBecomingNoisy(true)
    }

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentTime = MutableStateFlow(0.0)
    val currentTime: StateFlow<Double> = _currentTime.asStateFlow()

    private val _duration = MutableStateFlow(0.0)
    val duration: StateFlow<Double> = _duration.asStateFlow()

    private val _packageId = MutableStateFlow<UUID?>(null)
    val packageId: StateFlow<UUID?> = _packageId.asStateFlow()

    private val _packageTitle = MutableStateFlow<String?>(null)
    val packageTitle: StateFlow<String?> = _packageTitle.asStateFlow()

    private val _tracklist = MutableStateFlow<List<PackageTrackInfo>>(emptyList())
    val tracklist: StateFlow<List<PackageTrackInfo>> = _tracklist.asStateFlow()

    private val _messages = MutableStateFlow<List<PackageMixtapeMessage>>(emptyList())
    val messages: StateFlow<List<PackageMixtapeMessage>> = _messages.asStateFlow()

    // Set only when forward playback naturally crosses a pinned timestamp —
    // never on an explicit seek/scrub/track-jump — and auto-clears itself.
    private val _activeMessage = MutableStateFlow<PackageMixtapeMessage?>(null)
    val activeMessage: StateFlow<PackageMixtapeMessage?> = _activeMessage.asStateFlow()

    private val _currentTrackIndex = MutableStateFlow<Int?>(null)
    val currentTrackIndex: StateFlow<Int?> = _currentTrackIndex.asStateFlow()

    val hasMixtapeLoaded: Boolean get() = _packageTitle.value != null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timerJob: Job? = null
    private var messageClearJob: Job? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) stop()
            }
        })
    }

    // Loads and immediately plays a mixtape, pausing whatever real-music
    // playback might be active first — only one thing this app plays should
    // ever be audible at once.
    fun load(id: UUID, file: File, tracklist: List<PackageTrackInfo>, messages: List<PackageMixtapeMessage>, title: String, pauseRealMusic: () -> Unit) {
        pauseRealMusic()
        stop()
        player.setMediaItem(MediaItem.Builder().setUri(Uri.fromFile(file))
            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(title).build()).build())
        player.prepare()
        requestPlay()
        _packageId.value = id
        _packageTitle.value = title
        _tracklist.value = tracklist
        _messages.value = messages
        startTimer()
    }

    fun togglePlayPause() {
        if (!hasMixtapeLoaded) return
        if (player.isPlaying) {
            player.pause()
            _isPlaying.value = false
        } else {
            requestPlay()
        }
    }

    fun seek(toSeconds: Double) {
        if (!hasMixtapeLoaded) return
        val clamped = toSeconds.coerceIn(0.0, _duration.value)
        player.seekTo((clamped * 1000).toLong())
        _currentTime.value = clamped
        updateCurrentTrackIndex()
    }

    fun nextTrack() {
        val index = _currentTrackIndex.value ?: return
        val list = _tracklist.value
        if (index + 1 < list.size) seek(list[index + 1].startOffsetSeconds)
    }

    fun previousTrack() {
        val index = _currentTrackIndex.value ?: return
        val list = _tracklist.value
        // Within the first ~2s of a track, "previous" jumps to the track
        // before it, matching how most music players treat a quick
        // double-tap on "back" versus restarting the current track.
        if (index > 0 && _currentTime.value - list[index].startOffsetSeconds < 2) {
            seek(list[index - 1].startOffsetSeconds)
        } else {
            seek(list[index].startOffsetSeconds)
        }
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
        _isPlaying.value = false
        _packageId.value = null
        _packageTitle.value = null
        _tracklist.value = emptyList()
        _messages.value = emptyList()
        _activeMessage.value = null
        _currentTrackIndex.value = null
        _currentTime.value = 0.0
        _duration.value = 0.0
        messageClearJob?.cancel()
        stopTimer()
    }

    // Messages pinned inside the given track's time range — for the quiet
    // "this one has stuff" discoverability hint, not the transient pop.
    fun messageCount(forTrackIndex: Int): Int {
        val list = _tracklist.value
        if (forTrackIndex >= list.size) return 0
        val start = list[forTrackIndex].startOffsetSeconds
        val end = if (forTrackIndex + 1 < list.size) list[forTrackIndex + 1].startOffsetSeconds else Double.POSITIVE_INFINITY
        return _messages.value.count { it.timestampSeconds >= start && it.timestampSeconds < end }
    }

    private fun updateCurrentTrackIndex() {
        val time = _currentTime.value
        var index: Int? = null
        _tracklist.value.forEachIndexed { i, track -> if (time >= track.startOffsetSeconds) index = i }
        _currentTrackIndex.value = index
    }

    private fun startTimer() {
        stopTimer()
        timerJob = scope.launch {
            while (isActive) {
                delay(250)
                val previous = _currentTime.value
                val updated = player.currentPosition / 1000.0
                _currentTime.value = updated
                if (player.duration > 0) _duration.value = player.duration / 1000.0
                updateCurrentTrackIndex()
                // Only a small, forward, same-tick advance counts as
                // "natural playback" — an explicit seek already set
                // currentTime itself, so the gap here stays ~0 and no
                // skipped-over messages get surfaced.
                if (updated > previous && updated - previous < 1) {
                    val crossed = _messages.value.firstOrNull { it.timestampSeconds > previous && it.timestampSeconds <= updated }
                    if (crossed != null) showMessage(crossed)
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    // Mirrors iOS's scaled display window: a fixed 5s floor plus a
    // per-character allowance so a long message doesn't eat its own
    // display time just finishing "typing" on the reveal UI.
    private fun showMessage(message: PackageMixtapeMessage) {
        _activeMessage.value = message
        messageClearJob?.cancel()
        val displaySeconds = maxOf(5.0, message.text.length * (0.022 + 0.06))
        messageClearJob = scope.launch {
            delay((displaySeconds * 1000).toLong())
            _activeMessage.value = null
        }
    }

    fun release() {
        stop()
        scope.cancel()
        player.release()
    }
}
