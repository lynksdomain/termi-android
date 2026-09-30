package com.termi.android.music

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Real audio-reactive replacement for android.media.audiofx.Visualizer: on
// the AYN Thor (and presumably other gaming handhelds with a custom DSP/
// audio-effects chain), AudioFlinger fails to instantiate the system
// Visualizer effect entirely (logcat: "AudioFlinger could not create effect
// ... status: -1" / "Cannot initialize Visualizer engine, error: -3") — a
// HAL-level limitation no amount of session-id/capture-size handling can
// work around. This sidesteps the OS effects framework altogether by
// inserting a passthrough AudioProcessor directly into ExoPlayer's own
// audio-sink pipeline (see AudioPlayerEngine's RenderersFactory override) —
// closer to what iOS's FFTAnalyzerTap does (reading PCM directly off the
// audio graph). Needs no RECORD_AUDIO permission: this taps audio this app
// is already decoding for its own playback, not the microphone or another
// app's output.
//
// Pure passthrough: every sample is copied to the output buffer unchanged,
// just inspected on the way through. Downmixes to mono and accumulates a
// rolling non-overlapping frame of FFT_SIZE samples; each time a frame
// fills, runs SimpleFft and publishes raw per-bin magnitudes for
// VisualizerEngine to log-bucket into display bars. Runs on ExoPlayer's
// internal audio-processing thread, not the main thread — MutableStateFlow
// writes are safe to publish cross-thread.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class FftTapAudioProcessor : BaseAudioProcessor() {

    private val _rawMagnitudes = MutableStateFlow(FloatArray(FFT_SIZE / 2))
    val rawMagnitudes: StateFlow<FloatArray> = _rawMagnitudes.asStateFlow()

    private var channelCount = 2
    private var frame = FloatArray(FFT_SIZE)
    private var frameFill = 0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            // Only 16-bit PCM is tapped; anything else just passes through
            // untouched (frame accumulation is skipped in queueInput below).
            return AudioProcessor.AudioFormat.NOT_SET
        }
        channelCount = inputAudioFormat.channelCount
        frameFill = 0
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val order = inputBuffer.order()
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val startPosition = inputBuffer.position()

        val bytesPerFrame = channelCount * 2
        var pos = startPosition
        val limit = inputBuffer.limit()
        while (pos + bytesPerFrame <= limit && frameFill < FFT_SIZE) {
            var sum = 0
            for (ch in 0 until channelCount) {
                sum += inputBuffer.getShort(pos + ch * 2)
            }
            frame[frameFill] = (sum / channelCount) / 32768f
            frameFill++
            pos += bytesPerFrame
            if (frameFill == FFT_SIZE) {
                _rawMagnitudes.value = SimpleFft.magnitudes(frame)
                frameFill = 0
            }
        }

        inputBuffer.order(order)
        inputBuffer.position(startPosition)
        val outputBuffer = replaceOutputBuffer(remaining)
        outputBuffer.put(inputBuffer)
        outputBuffer.flip()
    }

    companion object {
        // A power of two; ~23ms at 44.1kHz — small enough for a responsive
        // visualizer, large enough for reasonable low-frequency resolution.
        const val FFT_SIZE = 1024
    }
}
