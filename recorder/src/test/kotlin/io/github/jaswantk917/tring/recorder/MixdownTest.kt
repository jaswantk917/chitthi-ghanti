package io.github.jaswantk917.tring.recorder

import org.junit.Assert.assertTrue
import org.junit.Test

class MixdownTest {
    /** [frames] of each: quiet room, then you talking (loud on the first channel), then the caller (loud on the second) */
    private fun call(firstNoise: Float = 1e-6f, secondNoise: Float = 1e-6f, frames: Int = 50): Pair<FloatArray, FloatArray> {
        val first = FloatArray(frames * 3) { i -> when (i / frames) { 1 -> 1e-3f; 2 -> 3e-5f; else -> firstNoise } }
        val second = FloatArray(frames * 3) { i -> when (i / frames) { 1 -> 2e-4f; 2 -> 4e-4f; else -> secondNoise } }
        return first to second
    }

    @Test
    fun `follows you on one microphone and the caller on the other`() {
        val (first, second) = call()
        val w = Mixdown.topWeights(first, second)
        assertTrue("while you talk: ${w[75]}", w[75] < 0.2f)
        assertTrue("while they talk: ${w[125]}", w[125] > 0.8f)
    }

    @Test
    fun `whichever channel the phone puts where, the right one still wins`() {
        val (first, second) = call()
        val w = Mixdown.topWeights(second, first)
        assertTrue(w[75] > 0.8f && w[125] < 0.2f)
    }

    @Test
    fun `a merely noisier microphone doesn't win`() {
        // the second channel hisses ten times louder, so the same speech stands out less on it
        val (first, second) = call(secondNoise = 1e-5f)
        val noisy = FloatArray(second.size) { second[it] + 1e-5f }
        val w = Mixdown.topWeights(first, noisy)
        assertTrue("while you talk: ${w[75]}", w[75] < 0.2f)
    }

    @Test
    fun `the hand-over is a crossfade, not a jump`() {
        val (first, second) = call()
        val w = Mixdown.topWeights(first, second)
        assertTrue("one frame before the caller: ${w[99]}", w[99] in 0.05f..0.95f)
    }
}
