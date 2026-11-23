package io.github.jaswantk917.tring.recorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class IndicTest {
    private fun floats(name: String): FloatArray {
        val bytes = javaClass.getResourceAsStream("/indic/$name")!!.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(buffer.remaining()).also { buffer.get(it) }
    }

    @Test
    fun `the features match AI4Bharat's own preprocessor`() {
        // expected.f32: preprocessor.ts on input.f32, a second of tones and noise
        val (features, frames) = Mel(floats("window.f32"), floats("mel.f32")).features(floats("input.f32"))
        val expected = floats("expected.f32")
        assertEquals(101, frames)
        assertEquals(expected.size, features.size)
        val worst = features.indices.maxOf { kotlin.math.abs(features[it] - expected[it]) }
        assertTrue("worst difference $worst", worst < 1e-3f)
    }

    @Test
    fun `a short turn is one piece and keeps who said it`() {
        val pieces = IndicPieces.of(listOf(Turn(1_000, 4_000, true), Turn(4_200, 9_000, false)), 10_000) { _, _ -> error("not cut") }
        assertEquals(listOf(IndicPieces.Piece(1_000, 4_000, true), IndicPieces.Piece(4_200, 9_000, false)), pieces)
    }

    @Test
    fun `a long turn is cut at its quiet moments, none over twelve seconds`() {
        val quiet = listOf(9_500, 19_000)
        val pieces = IndicPieces.of(listOf(Turn(0, 30_000, false)), 30_000) { from, to -> quiet.first { it in from..to } }
        assertEquals(listOf(0 to 9_500, 9_500 to 19_000, 19_000 to 30_000), pieces.map { it.startMs to it.endMs })
        assertTrue(pieces.all { it.endMs - it.startMs <= IndicPieces.LONGEST_MS && it.them == false })
    }

    @Test
    fun `a cut never lands outside the window it was asked about`() {
        val pieces = IndicPieces.of(listOf(Turn(0, 20_000, true)), 20_000) { _, _ -> 50_000 }
        assertEquals(12_000, pieces.first().endMs)
    }

    @Test
    fun `a call without turns is cut the same way, with nobody named`() {
        val pieces = IndicPieces.of(null, 15_000) { _, _ -> 8_000 }
        assertEquals(listOf(IndicPieces.Piece(0, 8_000, null), IndicPieces.Piece(8_000, 15_000, null)), pieces)
    }

    @Test
    fun `a turn past the end of the decoded call stops at the end`() {
        assertEquals(listOf(IndicPieces.Piece(1_000, 5_000, true)), IndicPieces.of(listOf(Turn(1_000, 6_000, true)), 5_000) { _, _ -> 0 })
    }

    @Test
    fun `tokens read as words where the model starts them`() {
        val vocab = listOf("<unk>", "▁हलो", "▁क्या", "▁हाल", "▁है", "ं")
        assertEquals("हलो क्या हालं है", IndicEngine.text(listOf(1, 2, 3, 5, 4), vocab))
    }

    @Test
    fun `how sure a token was is its share of the softmax`() {
        assertEquals(0.5f, IndicEngine.probability(floatArrayOf(1f, 1f), 0), 1e-6f)
        assertTrue(IndicEngine.probability(floatArrayOf(10f, 0f, 0f), 0) > 0.99f)
    }

    @Test
    fun `the model is offered from 4 GB of RAM, on a processor with dot product`() {
        val modern = "processor\t: 0\nFeatures\t: fp asimd aes crc32 asimddp sha512\n"
        assertTrue(IndicModel.runsOn(7_600_000_000, modern))
        assertTrue("a 4 GB phone reports less than 4", IndicModel.runsOn(3_600_000_000, modern))
        assertFalse("a 3 GB phone", IndicModel.runsOn(3_000_000_000, modern))
        assertFalse("Cortex-A53", IndicModel.runsOn(7_600_000_000, "Features\t: fp asimd aes crc32\n"))
        assertFalse("only a similar name", IndicModel.runsOn(7_600_000_000, "Features\t: fp asimddpx\n"))
        assertFalse("nothing readable", IndicModel.runsOn(7_600_000_000, ""))
    }
}
