package io.github.jaswantk917.tring.recorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PiecesTest {
    private fun words(text: String, segment: Int = 0, startMs: Int = 0, stepMs: Int = 300) =
        text.split(' ').mapIndexed { i, w -> Whisper.Word(w, startMs + i * stepMs, startMs + (i + 1) * stepMs, 0.9f, segment) }

    private fun loud(ms: Int) = FloatArray(ms / Pieces.FRAME_MS) { 1f }

    @Test
    fun aShortCallIsOnePiece() {
        assertEquals(emptyList<Int>(), Pieces.cuts(loud(120_000)))
    }

    @Test
    fun cutsAtTheQuietestMomentNearEachMark() {
        val energy = loud(320_000)
        // quiet at 84 s and at 178 s, inside the 10 s either side of each mark
        for (ms in 84_000 until 84_400 step Pieces.FRAME_MS) energy[ms / Pieces.FRAME_MS] = 0f
        for (ms in 178_000 until 178_400 step Pieces.FRAME_MS) energy[ms / Pieces.FRAME_MS] = 0f
        val cuts = Pieces.cuts(energy)
        assertEquals(3, cuts.size)
        assertTrue(cuts[0] in 84_000..84_400)
        assertTrue(cuts[1] in 178_000..178_400)
        // the next mark counts from the cut before it
        assertTrue(cuts[2] in cuts[1] + 80_000..cuts[1] + 100_000)
    }

    @Test
    fun theLastPieceRunsLongRatherThanLeaveAScrap() {
        // a third cut near 270 s would leave 30 s: the last piece runs on to the end instead
        assertEquals(2, Pieces.cuts(loud(300_000)).size)
    }

    @Test
    fun withNoQuietItCutsAtTheMark() {
        assertTrue(Pieces.cuts(loud(150_000)).single() in 89_000..91_000)
    }

    @Test
    fun aPhraseSaidOverAndOverIsKeptOnce() {
        val kept = Pieces.cutRepeats(words("तुसरा कॉंची करने लग दाता है तुसरा कॉंची करने लग दाता है तुसरा कॉंची करने लग दाता है"))
        assertEquals("तुसरा कॉंची करने लग दाता है", kept.joinToString(" ") { it.text })
    }

    @Test
    fun oneWordOverAndOverIsKeptOnce() {
        val kept = Pieces.cutRepeats(words("कर दो कर दो कर दो कर दो नहीं"))
        assertEquals("कर दो नहीं", kept.joinToString(" ") { it.text })
    }

    @Test
    fun twiceIsLeftAlone() {
        val said = "जैसा जैसा होता है"
        assertEquals(said, Pieces.cutRepeats(words(said)).joinToString(" ") { it.text })
    }

    @Test
    fun punctuationDoesNotHideARepeat() {
        val kept = Pieces.cutRepeats(words("हेलो, हेलो, हेलो, हां"))
        assertEquals(listOf("हेलो,", "हां"), kept.map { it.text })
    }

    @Test
    fun eachSentenceIsALine() {
        val lines = Pieces.lines(words("क्या हाल है", segment = 0) + words("ठीक है", segment = 1, startMs = 2_000), turns = null)
        assertEquals(listOf("क्या हाल है", "ठीक है"), lines.map { it.text })
        assertEquals(listOf<Boolean?>(null, null), lines.map { it.them })
        assertEquals(2_000, lines[1].atMs)
    }

    @Test
    fun aSentenceSplitsWhereTheSpeakerChanges() {
        val turns = listOf(Turn(0, 1_000, false), Turn(1_000, 3_000, true))
        val lines = Pieces.lines(words("हेलो कौन बोल रहा है", stepMs = 400), turns)
        assertEquals(listOf("हेलो कौन", "बोल रहा है"), lines.map { it.text })
        assertEquals(listOf<Boolean?>(false, true), lines.map { it.them })
    }

    @Test
    fun aLinesConfidenceIsItsWordsAverage() {
        val heard = listOf(Whisper.Word("हां", 0, 300, 0.2f, 0), Whisper.Word("जी", 300, 600, 0.6f, 0))
        assertEquals(0.4f, Pieces.lines(heard, null).single().confidence, 0.001f)
    }

    @Test
    fun punctuationAloneIsNoLine() {
        assertEquals(emptyList<Line>(), Pieces.lines(listOf(Whisper.Word("?", 0, 100, 0.9f, 0)), null))
    }
}
