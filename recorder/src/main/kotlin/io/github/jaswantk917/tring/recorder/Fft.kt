package io.github.jaswantk917.tring.recorder

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** In-place radix-2 FFT of one [size], with its twiddles worked out once: an hour's call runs it about half a million times. */
internal class Fft(private val size: Int) {
    private val cosTable = DoubleArray(size / 2) { cos(2 * PI * it / size) }
    private val sinTable = DoubleArray(size / 2) { -sin(2 * PI * it / size) }
    private val reversed = IntArray(size).also { table ->
        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            table[i] = j
        }
    }

    fun forward(re: DoubleArray, im: DoubleArray) {
        for (i in 0 until size) {
            val j = reversed[i]
            if (i < j) { re[i] = re[j].also { re[j] = re[i] }; im[i] = im[j].also { im[j] = im[i] } }
        }
        var len = 2
        while (len <= size) {
            val step = size / len
            val half = len / 2
            for (i in 0 until size step len) {
                for (k in 0 until half) {
                    val wr = cosTable[k * step]; val wi = sinTable[k * step]
                    val a = i + k; val b = a + half
                    val xr = re[b] * wr - im[b] * wi; val xi = re[b] * wi + im[b] * wr
                    re[b] = re[a] - xr; im[b] = im[a] - xi
                    re[a] += xr; im[a] += xi
                }
            }
            len = len shl 1
        }
    }

    fun inverse(re: DoubleArray, im: DoubleArray) {
        for (i in 0 until size) im[i] = -im[i]
        forward(re, im)
        for (i in 0 until size) { re[i] /= size; im[i] = -im[i] / size }
    }
}
