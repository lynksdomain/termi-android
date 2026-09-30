package com.termi.android.music

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot

// Minimal iterative radix-2 Cooley-Tukey FFT — Android has no public FFT
// utility for raw sample arrays (android.media.audiofx.Visualizer normally
// hands back an already-computed one, but that effect isn't available on
// every device's audio HAL; see FftTapAudioProcessor's header comment for
// why this project needs its own). `real.size` must be a power of two.
object SimpleFft {

    // Returns magnitude per bin for bins 0 until size/2 (the other half is
    // the mirrored negative-frequency image for real input, not useful here).
    fun magnitudes(samples: FloatArray): FloatArray {
        val n = samples.size
        val re = FloatArray(n)
        val im = FloatArray(n)
        // Hann window to reduce spectral leakage from the hard frame edges.
        for (i in 0 until n) {
            val w = 0.5f - 0.5f * cos((2 * PI * i / (n - 1)).toFloat())
            re[i] = samples[i] * w
        }
        transform(re, im)
        val half = n / 2
        val mags = FloatArray(half)
        for (i in 0 until half) {
            mags[i] = hypot(re[i], im[i])
        }
        return mags
    }

    // In-place iterative FFT (bit-reversal permutation + butterfly passes).
    private fun transform(re: FloatArray, im: FloatArray) {
        val n = re.size
        require(n and (n - 1) == 0) { "SimpleFft size must be a power of two, was $n" }
        if (n <= 1) return

        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val angle = (-2 * PI / len).toFloat()
            val wReal = cos(angle)
            val wImag = kotlin.math.sin(angle)
            var i = 0
            while (i < n) {
                var curReal = 1f
                var curImag = 0f
                for (k in 0 until len / 2) {
                    val evenIndex = i + k
                    val oddIndex = i + k + len / 2
                    val oddReal = re[oddIndex] * curReal - im[oddIndex] * curImag
                    val oddImag = re[oddIndex] * curImag + im[oddIndex] * curReal
                    re[oddIndex] = re[evenIndex] - oddReal
                    im[oddIndex] = im[evenIndex] - oddImag
                    re[evenIndex] += oddReal
                    im[evenIndex] += oddImag
                    val nextReal = curReal * wReal - curImag * wImag
                    val nextImag = curReal * wImag + curImag * wReal
                    curReal = nextReal
                    curImag = nextImag
                }
                i += len
            }
            len = len shl 1
        }
    }
}
