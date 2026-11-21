package io.github.jaswantk917.tring.recorder

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.random.Random

class VoicesTest {
    private val rate = Mixdown.RATE
    private val random = Random(7)

    /** [seconds] of a sum of tones, each (hz, amplitude), over a faint hiss */
    private fun tones(seconds: Float, vararg parts: Pair<Float, Float>) = FloatArray((seconds * rate).toInt()) { n ->
        val t = n.toFloat() / rate
        parts.fold(0f) { s, (hz, a) -> s + a * sin(2 * PI * hz * t).toFloat() } + (random.nextFloat() - 0.5f) * 2e-4f
    }

    // you at the microphone: loud and full range
    private fun you(seconds: Float) = tones(seconds, 180f to 0.3f, 450f to 0.25f, 1_600f to 0.08f, 5_000f to 0.03f)
    // you on a thin sound ("ee"): little bass, still loud
    private fun youThin(seconds: Float) = tones(seconds, 300f to 0.04f, 2_300f to 0.25f, 3_000f to 0.1f)
    // the caller through the earpiece on an old-style call: quiet, phone band only
    private fun narrowCaller(seconds: Float) = tones(seconds, 1_300f to 0.02f, 2_200f to 0.015f, 700f to 0.002f)
    // the caller on HD voice: the same spread as you, only far quieter
    private fun hdCaller(seconds: Float) = tones(seconds, 180f to 0.012f, 450f to 0.01f, 1_600f to 0.004f, 5_000f to 0.0015f)
    private fun pause(seconds: Float) = tones(seconds)

    private fun weights(signal: FloatArray): FloatArray {
        val meter = Voices.Meter(rate)
        val shapes = (0 until signal.size / Mixdown.FRAME).map { f -> meter.shape(signal.copyOfRange(f * Mixdown.FRAME, (f + 1) * Mixdown.FRAME)) }
        val floor = shapes.map { it.levelDb }.sorted()[shapes.size / 10]
        return Voices.callerWeights(shapes, BooleanArray(shapes.size) { shapes[it].levelDb > floor + 8 })
    }

    /** share of frames in [from, to) seconds judged the caller, skipping the hand-over edges */
    private fun FloatArray.share(from: Float, to: Float): Float {
        val a = (from * 50).toInt() + 8
        val b = (to * 50).toInt() - 8
        return (a until b).count { this[it] > 0.5f }.toFloat() / (b - a)
    }

    @Test
    fun `an old-style caller is told apart from you`() {
        val w = weights(you(2f) + pause(0.5f) + narrowCaller(2f) + pause(0.5f) + you(2f))
        assertTrue(w.share(0f, 2f) < 0.05f)
        assertTrue(w.share(2.5f, 4.5f) > 0.95f)
        assertTrue(w.share(5f, 7f) < 0.05f)
    }

    @Test
    fun `an HD voice caller, as full as you, is told apart by being far quieter`() {
        val w = weights(you(2f) + pause(0.5f) + hdCaller(2f) + pause(0.5f) + you(2f))
        assertTrue(w.share(0f, 2f) < 0.05f)
        assertTrue(w.share(2.5f, 4.5f) > 0.95f)
    }

    @Test
    fun `your thin sounds stay yours`() {
        val w = weights(you(1.5f) + youThin(1.5f) + pause(0.5f) + narrowCaller(2f) + pause(0.5f) + youThin(1.5f) + you(1.5f))
        assertTrue(w.share(0f, 3f) < 0.05f)
        assertTrue(w.share(3.5f, 5.5f) > 0.95f)
        assertTrue(w.share(6f, 9f) < 0.05f)
    }

    @Test
    fun `when only you talk, nobody is the caller`() {
        val w = weights(you(2f) + youThin(2f) + pause(0.5f) + you(2f) + youThin(1f))
        assertTrue(w.share(0f, 7.5f) < 0.01f)
    }

    @Test
    fun `the caller comes out about as loud as you`() {
        val signal = you(2f) + narrowCaller(2f)
        val caller = weights(signal)
        val you = Leveler(rate)
        val them = Leveler(rate, maxGain = 64f)
        val out = FloatArray(signal.size) { i ->
            val c = caller[(i / Mixdown.FRAME).coerceAtMost(caller.lastIndex)]
            c * them.process(signal[i], listen = c > 0.5f) + (1 - c) * you.process(signal[i], listen = c <= 0.5f)
        }
        fun peak(from: Float, to: Float) = ((from * rate).toInt() until (to * rate).toInt()).maxOf { abs(out[it]) }
        val yours = peak(1.5f, 2f)
        val theirs = peak(3.5f, 4f)
        assertTrue("you $yours, the caller $theirs", theirs > yours * 0.5f && theirs < yours * 2f)
    }

    @Test
    fun `your voice under the caller's high gain is pulled back at once`() {
        val them = Leveler(rate, maxGain = 64f)
        narrowCaller(1f).forEach { them.process(it) }
        // you, wrongly sent through the caller's leveler without it listening
        val out = you(0.1f).map { them.process(it, listen = false) }
        val dbFromFirstMs = 20 * log10(out.drop(rate / 1000).maxOf { abs(it) } / 0.9f)
        assertTrue("peaks reach ${"%.1f".format(dbFromFirstMs)} dB over the ceiling", dbFromFirstMs < 0.5f)
    }
}
