package com.termi.android

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Silent PCM avoids making device tests audible. */
fun writeSilentWav(file: File, seconds: Int, sampleRate: Int = 44100, channels: Int = 2): File {
    val bytes = seconds * sampleRate * channels * 2
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    header.put("RIFF".toByteArray()).putInt(36 + bytes).put("WAVEfmt ".toByteArray())
        .putInt(16).putShort(1).putShort(channels.toShort()).putInt(sampleRate)
        .putInt(sampleRate * channels * 2).putShort((channels * 2).toShort()).putShort(16)
        .put("data".toByteArray()).putInt(bytes)
    file.outputStream().use { it.write(header.array()); it.write(ByteArray(bytes)) }
    return file
}
