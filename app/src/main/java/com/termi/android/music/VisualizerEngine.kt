package com.termi.android.music

import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Kotlin port of the bar-bucketing/scaling half of FFTAnalyzerTap.swift —
// NOT the FFT math itself (see SimpleFft.kt for that). Originally built on
// android.media.audiofx.Visualizer, which attaches to a player's audio
// session and hands back an already-computed FFT for free — but that system
// effect isn't available at all on every device's audio HAL (confirmed
// failing outright on the AYN Thor: AudioFlinger refuses to instantiate the
// effect, error -3/-1, regardless of session id or permissions). Now
// consumes raw per-bin magnitudes from FftTapAudioProcessor instead, which
// taps ExoPlayer's own PCM pipeline directly — works on any device,
// mirrors how iOS's FFTAnalyzerTap taps the audio graph itself, and needs
// no RECORD_AUDIO permission since it's this app's own decoded audio, not
// the microphone. This class just does bin→32-bar log mapping and
// normalization/smoothing, same as before.
class VisualizerEngine(private val rawMagnitudes: StateFlow<FloatArray>, private val barCount: Int = 32) {

    private val _magnitudes = MutableStateFlow(FloatArray(barCount))
    val magnitudes: StateFlow<FloatArray> = _magnitudes.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var smoothed = FloatArray(barCount)

    fun start() {
        job = scope.launch {
            rawMagnitudes.collect { bins -> processBins(bins) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    // bins[i] is the magnitude of FFT bin i (DC at 0, real-valued input so
    // only the first half of the transform is meaningful); bins are
    // log-bucketed into `barCount` bars (mirrors the 250Hz-to-80%-Nyquist
    // log range in the original), then dB-windowed into 0..1 and
    // exponentially smoothed.
    private fun processBins(bins: FloatArray) {
        val binCount = bins.size
        if (binCount < 2) return

        val bars = FloatArray(barCount)
        val minBin = 2
        val maxBin = binCount - 1
        for (bar in 0 until barCount) {
            val t0 = bar.toFloat() / barCount
            val t1 = (bar + 1).toFloat() / barCount
            val lo = (minBin * Math.pow((maxBin.toDouble() / minBin), t0.toDouble())).toInt()
            val hi = (minBin * Math.pow((maxBin.toDouble() / minBin), t1.toDouble())).toInt()
                .coerceAtLeast(lo + 1)
            var sum = 0f
            var count = 0
            for (bin in lo until min(hi, binCount)) {
                sum += bins[bin]
                count += 1
            }
            val avg = if (count > 0) sum / count else 0f
            val db = 20 * ln(max(avg, 1e-4f).toDouble()).toFloat() / ln(10f)
            // Bass bins (low bar indices) naturally carry far more raw FFT
            // energy than treble ones — without correction they pin at the
            // top constantly while the higher bars barely move, reading as
            // "everything on the left is maxed out". Tapers extra
            // attenuation onto roughly the first third of bars (heaviest at
            // bar 0, gone by bar ~11), leaving the rest of the range —
            // including attack/decay smoothing below — untouched.
            val bassRolloffDb = max(0f, 31f - 2.6f * bar)
            // Normalize a dB-ish window (post-log10 magnitude of a 0..1
            // normalized PCM tap, not true dBFS against a fixed reference)
            // into 0..1. Ceiling raised from 0dB to +14dB (was too many bars
            // pinned at the top) — smoothing/attack below is untouched so
            // the bars stay just as jumpy, there's just more headroom
            // before a bar maxes out.
            val normalized = ((db - bassRolloffDb + 40f) / 54f).coerceIn(0f, 1f)
            bars[bar] = normalized
        }

        for (i in bars.indices) {
            val alpha = if (bars[i] > smoothed[i]) 0.5f else 0.15f
            smoothed[i] = smoothed[i] + (bars[i] - smoothed[i]) * alpha
        }
        _magnitudes.value = smoothed.copyOf()
    }
}
