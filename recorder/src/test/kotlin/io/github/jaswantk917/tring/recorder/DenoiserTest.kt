package io.github.jaswantk917.tring.recorder

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class DenoiserTest {
    private val rate = Mixdown.RATE
    private val random = Random(11)

    /** white hiss at [rms] */
    private fun hiss(seconds: Float, rms: Float) = FloatArray((seconds * rate).toInt()) { (random.nextFloat() * 2 - 1) * rms * sqrt(3f) }

    /**
     * a voice-like buzz at [rms] while it sounds: a 180 Hz voice and its harmonics up to 3.4 kHz, in
     * syllables (200 ms on, 80 ms between) as speech comes; a steady tone would be hiss to anyone
     */
    private fun voice(seconds: Float, rms: Float): FloatArray {
        val harmonics = (1..18).map { 180f * it }
        val syllable = (0.28f * rate).toInt()
        val raw = FloatArray((seconds * rate).toInt()) { n ->
            val at = n % syllable
            val envelope = if (at < 0.2f * rate) sin(PI * at / (0.2 * rate)).toFloat() else 0f
            envelope * harmonics.fold(0f) { s, hz -> s + sin(2 * PI * hz * n / rate).toFloat() / sqrt(hz / 180f) }
        }
        val scale = rms / rms(raw)
        return FloatArray(raw.size) { raw[it] * scale }
    }

    private fun plus(a: FloatArray, b: FloatArray) = FloatArray(a.size) { a[it] + b[it] }
    private fun rms(x: FloatArray, from: Int = 0, to: Int = x.size) = sqrt((from until to).fold(0.0) { s, i -> s + x[i] * x[i].toDouble() } / (to - from)).toFloat()
    private fun db(x: Float) = 20 * log10(x)

    private fun run(signal: FloatArray): Pair<FloatArray, Denoiser.Result> {
        val input = File.createTempFile("denoise-in", ".pcm").apply { deleteOnExit() }
        val output = File.createTempFile("denoise-out", ".pcm").apply { deleteOnExit() }
        val bytes = ByteBuffer.allocate(signal.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        signal.forEach { bytes.putShort((it.coerceIn(-1f, 1f) * 32767).toInt().toShort()) }
        input.writeBytes(bytes.array())
        val result = Denoiser.run(input, output)
        val back = ByteBuffer.wrap(output.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return FloatArray(back.remaining()) { back.get(it) / 32768f } to result
    }

    private fun seconds(from: Float, to: Float) = (from * rate).toInt() until (to * rate).toInt()

    @Test
    fun `steady hiss is measured at its real level and isn't taken for talking`() {
        val (out, result) = run(hiss(8f, 0.01f))
        assertTrue("measured ${db(result.noise)} dB", abs(db(result.noise) - db(0.01f)) < 1.5f)
        val talking = result.speech.count { it }.toFloat() / result.speech.size
        assertTrue("talking ${talking * 100}%", talking < 0.05f)
        assertTrue("left ${db(rms(out) / 0.01f)} dB", db(rms(out) / 0.01f) < -12f)
        assertTrue("same length", out.size == (8f * rate).toInt())
    }

    @Test
    fun `pauses lose their hiss while the voice keeps its level`() {
        val noise = hiss(9f, 0.005f)
        val talk = voice(9f, 0.1f)
        val signal = FloatArray(noise.size) { i -> noise[i] + if ((i / rate) % 3 == 1) talk[i] else 0f }
        val (out, result) = run(signal)
        for (pause in listOf(seconds(0.5f, 0.9f), seconds(6.2f, 6.8f))) {
            val cut = db(rms(out, pause.first, pause.last) / rms(signal, pause.first, pause.last))
            assertTrue("pause cut by only $cut dB", cut < -10f)
        }
        val words = seconds(4.2f, 4.8f)
        val kept = db(rms(out, words.first, words.last) / rms(signal, words.first, words.last))
        assertTrue("voice changed by $kept dB", abs(kept) < 1.5f)
        assertTrue("talking marked", (words.first until words.last step 100).all { result.speechAt(it) })
        assertTrue("pause marked quiet", seconds(0.3f, 0.7f).step(100).none { result.speechAt(it) })
    }

    @Test
    fun `a faint caller 6 dB over the hiss counts as talking`() {
        val noise = hiss(6f, 0.004f)
        val faint = voice(6f, 0.008f)
        val signal = FloatArray(noise.size) { i -> noise[i] + if (i in seconds(3f, 5f)) faint[i] else 0f }
        val (_, result) = run(signal)
        val marked = seconds(3.3f, 4.7f).step(160).count { result.speechAt(it) }.toFloat() / seconds(3.3f, 4.7f).step(160).count()
        assertTrue("marked ${marked * 100}%", marked > 0.9f)
    }

    @Test
    fun `a room that gets louder mid-call is followed`() {
        val signal = hiss(5f, 0.002f) + hiss(5f, 0.02f)
        val (out, _) = run(signal)
        val late = seconds(7f, 9.5f)
        val cut = db(rms(out, late.first, late.last) / rms(signal, late.first, late.last))
        assertTrue("louder room cut by only $cut dB", cut < -10f)
    }

    @Test
    fun `clean sound comes back as it went in`() {
        val signal = plus(voice(2f, 0.1f), hiss(2f, 0.00001f))
        val (out, _) = run(signal)
        val middle = seconds(0.5f, 1.5f)
        val error = rms(FloatArray(middle.count()) { out[middle.first + it] - signal[middle.first + it] })
        assertTrue("error ${db(error / 0.1f)} dB", db(error / 0.1f) < -30f)
    }
}
