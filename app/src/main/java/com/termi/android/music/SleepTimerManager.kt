package com.termi.android.music

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

// Minimal port of SleepTimerManager.swift — cycles OFF -> 15 -> 30 -> 45 ->
// 60 (minutes) -> OFF, counting down and firing onExpiry when it hits zero.
// Deferred vs. the original: the 30-second fade-out on expiry (this just
// pauses immediately) — see AudioPlayerEngine's fadeOutAndPause TODO.
class SleepTimerManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val durationsMinutes = listOf(0, 15, 30, 45, 60)
    private var durationIndex = 0
    private var job: Job? = null

    private val _remainingSeconds = MutableStateFlow(0)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    val isActive: Boolean get() = durationsMinutes[durationIndex] > 0

    var onExpiry: (() -> Unit)? = null

    val remainingLabel: String
        get() {
            val s = _remainingSeconds.value
            return "%d:%02d".format(s / 60, s % 60)
        }

    fun release() {
        onExpiry = null
        scope.cancel()
    }

    fun cycle() {
        job?.cancel()
        durationIndex = (durationIndex + 1) % durationsMinutes.size
        val minutes = durationsMinutes[durationIndex]
        if (minutes == 0) {
            _remainingSeconds.value = 0
            return
        }
        _remainingSeconds.value = minutes * 60
        job = scope.launch {
            while (isActive && _remainingSeconds.value > 0) {
                delay(1000)
                _remainingSeconds.value -= 1
            }
            if (_remainingSeconds.value <= 0) {
                durationIndex = 0
                onExpiry?.invoke()
            }
        }
    }
}
