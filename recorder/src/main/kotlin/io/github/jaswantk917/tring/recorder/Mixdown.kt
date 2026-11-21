package io.github.jaswantk917.tring.recorder

import kotlin.math.exp
import kotlin.math.log10

/**
 * Picks, every 20 ms, the microphone that hears whoever is talking best. On a phone one usually sits
 * near your mouth and another near the earpiece, so each wins when its own speaker talks, but which
 * channel is which differs by phone and nothing here assumes it. "Best" is loudness over that
 * microphone's own background noise, measured in this call, not plain loudness, so a microphone
 * that is merely noisier never wins. The choice is a soft weight, smoothed both ways in time so the
 * hand-over is a crossfade a few frames long, never a click, and it holds through pauses.
 */
object Mixdown {
    const val RATE = 16_000
    const val FRAME = RATE / 50

    /** for each frame, how much of the second channel to use, 0..1; the rest is the first */
    fun topWeights(bottomEnergy: FloatArray, topEnergy: FloatArray): FloatArray {
        val n = minOf(bottomEnergy.size, topEnergy.size)
        val bottomNoise = noiseFloor(bottomEnergy)
        val topNoise = noiseFloor(topEnergy)
        val weights = FloatArray(n)
        var last = 0f
        for (i in 0 until n) {
            val bottomSnr = bottomEnergy[i] / bottomNoise
            val topSnr = topEnergy[i] / topNoise
            // a pause: nobody to follow, so keep the last choice
            last = if (maxOf(bottomSnr, topSnr) < SPEECH) last else sigmoid(10f * log10(topSnr / bottomSnr) / SOFTNESS_DB)
            weights[i] = last
        }
        return smooth(weights)
    }

    /** the quiet end of a microphone's frames: the room, not anyone talking */
    fun noiseFloor(energy: FloatArray): Float {
        if (energy.isEmpty()) return 1e-9f
        val sorted = energy.sortedArray()
        return sorted[(sorted.size * 0.1f).toInt().coerceAtMost(sorted.lastIndex)].coerceAtLeast(1e-9f)
    }

    // forward then backward, so the crossfade is centred on the change instead of trailing it
    private fun smooth(x: FloatArray): FloatArray {
        val a = 1f - exp(-1f / SMOOTH_FRAMES)
        val y = x.copyOf()
        for (i in 1 until y.size) y[i] = y[i - 1] + a * (y[i] - y[i - 1])
        for (i in y.size - 2 downTo 0) y[i] = y[i + 1] + a * (y[i] - y[i + 1])
        return y
    }

    private fun sigmoid(x: Float) = 1f / (1f + exp(-x))

    /** frame energy over the noise floor that counts as someone talking: about 8 dB */
    private const val SPEECH = 6f
    /** how many dB apart the two microphones must be for one to clearly win */
    private const val SOFTNESS_DB = 3f
    /** about 60 ms each way */
    private const val SMOOTH_FRAMES = 3f
}
