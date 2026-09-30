package com.termi.android.pkgs

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

// Kotlin analog to iOS's Engine/MixtapeComposer.swift. iOS gets a real
// compose-and-export API for free (AVMutableComposition + AVAssetExportSession);
// Android has no equivalent, so this hand-rolls the same effect with the
// low-level MediaCodec/MediaExtractor/MediaMuxer APIs: decode each source
// to raw PCM, concatenate back-to-back, re-encode once to AAC, mux into a
// single .m4a. Matches iOS's real behavior exactly (see
// pkgs_content_gating_decision memory) — full song durations, no
// trimming/crossfade, "v1: straight back-to-back concatenation" is the
// deliberate parity choice, not a shortcut. Needs real-device
// verification: MediaCodec behavior is genuinely vendor/device-dependent,
// unlike a pure-Kotlin computation.
object MixtapeComposer {

    data class ComposeResult(val outputFile: File, val trackOffsetsSeconds: List<Double>)

    // Canonical PCM format every source gets decoded/resampled/remixed to
    // before the single final re-encode — a safe, universally-supported
    // target for the AAC encoder.
    private const val SAMPLE_RATE = 44100
    private const val CHANNEL_COUNT = 2
    private const val BYTES_PER_FRAME = CHANNEL_COUNT * 2 // 16-bit stereo

    // `onProgress` mirrors iOS's build(songs:progress:) callback — coarse
    // but real, not a fake animated bar: decode is weighted 80% of the
    // total (split evenly per source as each one finishes), the final
    // re-encode pass the remaining 20% (by PCM bytes consumed).
    fun compose(sources: List<File>, outputFile: File, onProgress: (Double) -> Unit = {}): ComposeResult {
        val offsets = mutableListOf<Double>()
        var cumulativeFrames = 0L

        val pcmFile = File(outputFile.parentFile, "${outputFile.nameWithoutExtension}_pcm.raw")
        pcmFile.outputStream().buffered().use { pcmOut ->
            sources.forEachIndexed { index, source ->
                offsets.add(cumulativeFrames.toDouble() / SAMPLE_RATE)
                cumulativeFrames += decodeToPcm(source, pcmOut)
                onProgress(0.8 * (index + 1) / sources.size)
            }
        }

        encodePcmToAac(pcmFile, outputFile) { encodeFraction -> onProgress(0.8 + 0.2 * encodeFraction) }
        pcmFile.delete()

        return ComposeResult(outputFile, offsets)
    }

    // Decodes one source's audio track fully, resampling/remixing to the
    // canonical format, appending raw 16-bit PCM to `pcmOut`. Returns the
    // number of FRAMES written (one frame = one sample per channel) so the
    // caller can compute real elapsed seconds for the next track's offset.
    private fun decodeToPcm(source: File, pcmOut: OutputStream): Long {
        val extractor = MediaExtractor()
        extractor.setDataSource(source.absolutePath)
        val trackIndex = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        }
        if (trackIndex == null) {
            extractor.release()
            return 0L
        }
        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val sourceSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val sourceChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        var framesWritten = 0L
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false

        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val inBuffer = codec.getInputBuffer(inIndex)!!
                    val sampleSize = extractor.readSampleData(inBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEOS = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            if (outIndex >= 0) {
                if (bufferInfo.size > 0) {
                    val outBuffer = codec.getOutputBuffer(outIndex)!!
                    outBuffer.position(bufferInfo.offset)
                    outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    val chunk = ByteArray(bufferInfo.size)
                    outBuffer.get(chunk)
                    val resampled = resampleAndRemix(chunk, sourceSampleRate, sourceChannels)
                    pcmOut.write(resampled)
                    framesWritten += resampled.size / BYTES_PER_FRAME
                }
                codec.releaseOutputBuffer(outIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    sawOutputEOS = true
                }
            }
        }

        codec.stop()
        codec.release()
        extractor.release()
        return framesWritten
    }

    // Nearest-neighbor resample + simple channel remix (mono duplicated to
    // both channels; >2 channels downmixed by taking the first two) to the
    // canonical SAMPLE_RATE/CHANNEL_COUNT. Not audiophile-grade, but
    // correct enough for v1 — real personal music libraries are
    // overwhelmingly mono or stereo at a handful of common sample rates.
    private fun resampleAndRemix(pcm16: ByteArray, sourceRate: Int, sourceChannels: Int): ByteArray {
        if (sourceChannels <= 0) return ByteArray(0)
        val bb = ByteBuffer.wrap(pcm16).order(ByteOrder.LITTLE_ENDIAN)
        val inFrameCount = pcm16.size / 2 / sourceChannels
        if (inFrameCount <= 0) return ByteArray(0)

        fun channelSample(frameIdx: Int, channel: Int): Short {
            val ch = if (sourceChannels == 1) 0 else channel.coerceAtMost(sourceChannels - 1)
            return bb.getShort((frameIdx * sourceChannels + ch) * 2)
        }

        val outFrameCount = if (sourceRate == SAMPLE_RATE) {
            inFrameCount
        } else {
            (inFrameCount.toLong() * SAMPLE_RATE / sourceRate).toInt()
        }
        val out = ByteBuffer.allocate(outFrameCount * BYTES_PER_FRAME).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until outFrameCount) {
            val srcFrame = if (sourceRate == SAMPLE_RATE) {
                i
            } else {
                (i.toLong() * sourceRate / SAMPLE_RATE).toInt().coerceAtMost(inFrameCount - 1)
            }
            val left = channelSample(srcFrame, 0)
            val right = if (sourceChannels == 1) left else channelSample(srcFrame, 1)
            out.putShort(left)
            out.putShort(right)
        }
        return out.array()
    }

    private fun encodePcmToAac(pcmFile: File, outputFile: File, onProgress: (Double) -> Unit = {}) {
        val totalBytes = pcmFile.length().coerceAtLeast(1)
        var bytesRead = 0L
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNEL_COUNT).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerTrackIndex = -1
        var muxerStarted = false

        val input = pcmFile.inputStream().buffered()
        val bufferInfo = MediaCodec.BufferInfo()
        var presentationTimeUs = 0L
        val chunk = ByteArray(4096)
        var sawInputEOS = false
        var sawOutputEOS = false

        try {
            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    val inIndex = encoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val read = input.read(chunk)
                        if (read <= 0) {
                            encoder.queueInputBuffer(inIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            val inBuffer = encoder.getInputBuffer(inIndex)!!
                            inBuffer.clear()
                            inBuffer.put(chunk, 0, read)
                            encoder.queueInputBuffer(inIndex, 0, read, presentationTimeUs, 0)
                            presentationTimeUs += (read.toLong() * 1_000_000L) / (SAMPLE_RATE * BYTES_PER_FRAME)
                            bytesRead += read
                            onProgress((bytesRead.toDouble() / totalBytes).coerceAtMost(1.0))
                        }
                    }
                }

                val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        muxerTrackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outIndex >= 0 -> {
                        if (bufferInfo.size > 0 && muxerStarted) {
                            val outBuffer = encoder.getOutputBuffer(outIndex)!!
                            outBuffer.position(bufferInfo.offset)
                            outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(muxerTrackIndex, outBuffer, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEOS = true
                    }
                }
            }
        } finally {
            input.close()
            encoder.stop()
            encoder.release()
            if (muxerStarted) muxer.stop()
            muxer.release()
        }
    }
}
