package io.github.jaswantk917.tring.recorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class LevelerTest {
    private val rate = 48_000

    /** the loudest sample of the last half second of a [seconds]-long 300 Hz tone at [amplitude] */
    private fun peakAfter(amplitude: Float, seconds: Float = 2f): Float {
        val leveler = Leveler(rate)
        val n = (seconds * rate).toInt()
        var peak = 0f
        for (i in 0 until n) {
            val out = leveler.process(amplitude * sin(2 * PI * 300 * i / rate).toFloat())
            if (i > n - rate / 2) peak = maxOf(peak, abs(out))
        }
        return peak
    }

    @Test
    fun `a faint earpiece leak is brought up to talking level`() {
        val leak = peakAfter(0.02f)
        assertTrue("leak peaked at $leak", leak > 0.2f)
    }

    @Test
    fun `a loud voice is brought down, not clipped`() {
        val loud = peakAfter(0.9f)
        assertTrue("loud peaked at $loud", loud < 0.6f)
    }

    @Test
    fun `room hiss below the gate is left alone`() = assertEquals(0.001f, peakAfter(0.001f), 0.0005f)

    @Test
    fun `the gain is capped`() = assertTrue(peakAfter(0.004f) <= 0.004f * Leveler.MAX_GAIN * 1.05f)

    @Test
    fun `a pause after a faint voice isn't lifted with its hiss`() {
        val leveler = Leveler(rate)
        repeat(rate) { leveler.process(0.02f * sin(2 * PI * 300 * it / rate).toFloat()) }
        var peak = 0f
        for (i in 0 until 2 * rate) {
            val out = leveler.process(0.001f * sin(2 * PI * 3_000 * i / rate).toFloat(), speech = false)
            if (i > 3 * rate / 2) peak = maxOf(peak, abs(out))
        }
        assertTrue("hiss came out at $peak", peak < 0.0015f)
    }

    @Test
    fun `the gaps between words keep the gain`() {
        val leveler = Leveler(rate)
        repeat(rate) { leveler.process(0.02f * sin(2 * PI * 300 * it / rate).toFloat()) }
        repeat(rate / 10) { leveler.process(0f, speech = false) }
        val out = (0 until rate / 50).maxOf { abs(leveler.process(0.02f * sin(2 * PI * 300 * it / rate).toFloat())) }
        assertTrue("first word back at $out", out > 0.15f)
    }
}
