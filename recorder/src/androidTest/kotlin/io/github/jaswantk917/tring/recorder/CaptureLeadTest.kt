package io.github.jaswantk917.tring.recorder

import android.media.MediaMetadataRetriever
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/** A screened call you picked up and recorded: what they said first, then the call, as one recording. */
class CaptureLeadTest {
    private val dir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
    private val scratch = File(dir, "lead-test.pcm")
    private val out = File(dir, "lead-test.m4a")
    private val turns = File(dir, "lead-test.turns")

    @After
    fun clear() {
        listOf(scratch, out, turns).forEach { it.delete() }
    }

    /** syllable-like bursts of a voiced tone, 16 kHz mono */
    private fun talking(ms: Int): ShortArray = ShortArray(Mixdown.RATE * ms / 1_000) { i ->
        val t = i.toDouble() / Mixdown.RATE
        val syllable = (sin(2 * PI * 4 * t)).coerceAtLeast(0.0)
        val voice = sin(2 * PI * 150 * t) + 0.5 * sin(2 * PI * 300 * t) + 0.25 * sin(2 * PI * 450 * t)
        (voice * syllable * 6_000).toInt().toShort()
    }

    private fun mix(lead: ShortArray?): Long {
        val call = talking(6_000)
        scratch.outputStream().use { file ->
            val bytes = ByteBuffer.allocate(call.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            bytes.asShortBuffer().put(call)
            file.write(bytes.array())
        }
        assertTrue("the recording was kept", out.outputStream().use { Capture(scratch).finish(it.fd, turns, lead) })
        return MediaMetadataRetriever().run {
            setDataSource(out.path)
            extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong().also { release() }
        }
    }

    @Test
    fun whatTheySaidWhileScreenedOpensTheRecording() {
        // the encoder pads every file a little, so the screening part is measured against the call alone
        val alone = mix(lead = null)
        val joined = mix(lead = talking(3_000))
        assertEquals("the screening part adds its own length: $alone → $joined", 3_000.0, (joined - alone).toDouble(), 70.0)
        val split = Turns.read(turns)!!
        assertEquals(Turn(0, 3_000, them = true), split.first())
        assertTrue("the call's own turns come after: $split", split.drop(1).all { it.startMs >= 3_000 })
    }
}
