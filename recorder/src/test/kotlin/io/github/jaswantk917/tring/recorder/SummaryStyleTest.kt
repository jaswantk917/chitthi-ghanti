package io.github.jaswantk917.tring.recorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryStyleTest {
    @Test
    fun `the caller and reporting verbs are gone`() {
        assertEquals("• Recording feature on a phone", Summarizer.clean("• The caller inquired about a recording feature on a phone"))
        assertEquals("• Land someone will come to see", Summarizer.clean("• The caller has been discussing land someone will come to see"))
        assertEquals("• Plans to eat together on Sunday", Summarizer.clean("• They discussed plans to eat together on Sunday"))
    }

    @Test
    fun `what the caller needs stays`() {
        assertEquals("• Needs to deposit 100 rupees at college", Summarizer.clean("• The caller needs to deposit 100 rupees at college"))
    }

    @Test
    fun `a line already direct is left alone`() {
        assertEquals("• Pay electricity bill tonight", Summarizer.clean("• Pay electricity bill tonight"))
        assertEquals("• They owe 500 rupees", Summarizer.clean("• They owe 500 rupees"))
    }

    @Test
    fun `longer calls get more lines`() {
        assertEquals(1, Summarizer.linesFor(30_000))
        assertEquals(2, Summarizer.linesFor(2 * 60_000L))
        assertEquals(3, Summarizer.linesFor(5 * 60_000L))
        assertEquals(4, Summarizer.linesFor(15 * 60_000L))
        assertEquals(5, Summarizer.linesFor(53 * 60_000L))
    }

    @Test
    fun `a short call is cut to its length and repeats are dropped`() {
        assertEquals("• One", Summarizer.tidy("• One\n• Two\n• Three", 20_000))
        assertEquals("• One\n• Two", Summarizer.tidy("• One\n• one\n• Two", 2 * 60_000L))
    }

    @Test
    fun `no summary stays no summary`() {
        assertEquals("", Summarizer.tidy("", 60_000))
    }

    @Test
    fun `a copied transcript is plain paragraphs, one per minute`() {
        val lines = listOf(Line(null, 0, "हाँ बोलो", 0.9f), Line(null, 5_000, "ठीक है", 0.2f), Line(null, 61_000, "चलो", 0.9f))
        assertEquals("हाँ बोलो ठीक है\n\nचलो", Minute.plain(lines))
    }

    private fun room(
        free: Long = 4_000_000_000L, low: Boolean = false, thermal: Int = 0, saver: Boolean = false, onCall: Boolean = false, callMs: Long = 60_000,
    ) = BigModel.hasRoom(free, low, thermal, saver, onCall, callMs)

    @Test
    fun `the big model takes a call when there is room`() {
        assertTrue(room())
        assertTrue(room(thermal = 1))
    }

    @Test
    fun `the big model stays out when the phone is short or busy`() {
        assertFalse(room(free = BigModel.LEAST_FREE_RAM - 1))
        assertFalse(room(low = true))
        assertFalse(room(thermal = 2))
        assertFalse(room(saver = true))
        assertFalse(room(onCall = true))
        assertFalse(room(callMs = BigModel.LONGEST_CALL_MS + 1))
    }
}
