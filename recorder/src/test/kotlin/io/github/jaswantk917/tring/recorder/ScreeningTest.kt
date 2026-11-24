package io.github.jaswantk917.tring.recorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ScreeningTest {
    @Test
    fun `no words rings, since the recognizer may have failed on a real person`() {
        assertEquals(true, Importance.byRules("  ")?.ring)
    }

    @Test
    fun `a delivery or an emergency rings, in Hindi or English`() {
        assertEquals(true, Importance.byRules("मैं डिलीवरी के लिए कॉल कर रहा हूं आपका पार्सल गेट पर है")?.ring)
        assertEquals(true, Importance.byRules("your Zomato order is here")?.ring)
        assertEquals(true, Importance.byRules("पापा का एक्सीडेंट हो गया है अस्पताल आ जाओ")?.ring)
    }

    @Test
    fun `a sales pitch doesn't ring`() {
        assertEquals(false, Importance.byRules("sir aapke liye personal loan ka offer hai")?.ring)
        assertEquals(false, Importance.byRules("आपके क्रेडिट कार्ड पर ऑफर है")?.ring)
    }

    @Test
    fun `urgent words win over a sales word`() {
        assertEquals(true, Importance.byRules("accident insurance claim urgent hai")?.ring)
    }

    @Test
    fun `a word inside another word doesn't count`() {
        assertNull(Importance.byRules("mera naam kolam hai"))
        assertNull(Importance.byRules("gateway ke baare mein baat karni thi"))
    }

    @Test
    fun `anything else is left to the model`() {
        assertNull(Importance.byRules("मैं आपके पुराने स्कूल से बोल रहा हूं"))
    }

    @Test
    fun `the timeline rises where someone talked and rests where nobody did`() {
        val bars = Timeline.bars(listOf(Turn(0, 5_000, true)), 10_000, 10)
        assertTrue(bars.take(5).all { it > 0.6f })
        assertTrue(bars.drop(5).all { it == 0.1f })
    }

    @Test
    fun `a call without turns draws a flat line`() {
        assertTrue(Timeline.bars(null, 10_000, 8).all { it == 0.1f })
        assertEquals(8, Timeline.bars(emptyList(), 0, 8).size)
    }

    @Test
    fun `a bullet written with a stray colon reads as a bullet`() {
        assertEquals("• one\n• two", Summarizer.clean("•: one\n• : two"))
    }

    private fun wav(rate: Int, channels: Int, samples: ShortArray, extra: Int = 0): ByteArray {
        val size = samples.size * 2
        val out = ByteBuffer.allocate(12 + 8 + extra + 24 + 8 + size).order(ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray()).putInt(out.capacity() - 8).put("WAVE".toByteArray())
        if (extra > 0) { out.put("LIST".toByteArray()).putInt(extra); repeat(extra) { out.put(0) } } else out.position(out.position() + 0)
        out.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(channels.toShort()).putInt(rate).putInt(rate * channels * 2).putShort((channels * 2).toShort()).putShort(16)
        out.put("data".toByteArray()).putInt(size)
        samples.forEach { out.putShort(it) }
        return out.array().copyOf(out.position())
    }

    @Test
    fun `a wav is read past chunks it doesn't care about`() {
        val read = Pcm.readWav(wav(24_000, 1, shortArrayOf(1, -2, 3), extra = 6))
        assertNotNull(read)
        assertEquals(24_000, read!!.rate)
        assertTrue(shortArrayOf(1, -2, 3).contentEquals(read.samples))
    }

    @Test
    fun `a stereo wav gives its first channel`() {
        assertTrue(shortArrayOf(1, 3).contentEquals(Pcm.readWav(wav(16_000, 2, shortArrayOf(1, 2, 3, 4)))!!.samples))
    }

    @Test
    fun `what isn't a wav is refused`() {
        assertNull(Pcm.readWav(ByteArray(64)))
    }

    @Test
    fun `resampling keeps the length in time`() {
        assertEquals(16_000, Pcm.resample(ShortArray(24_000), 24_000).size)
        val same = shortArrayOf(5, 6)
        assertTrue(same === Pcm.resample(same, Pcm.RATE))
    }

    @Test
    fun `the beep starts and ends quietly`() {
        val beep = Pcm.beep()
        assertEquals(5_600, beep.size)
        assertEquals(0, beep.first().toInt())
        assertTrue(kotlin.math.abs(beep.last().toInt()) < 100)
        assertFalse(beep.all { it.toInt() == 0 })
    }
}
