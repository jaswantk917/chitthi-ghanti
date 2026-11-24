package io.github.jaswantk917.tring.recorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepTest {
    @Test
    fun `a blip is not kept, however loud`() {
        assertFalse(Keep.worth(callMs = 1_000, speechMs = 1_000))
        assertFalse(Keep.worth(callMs = 1_499, speechMs = 1_499))
    }

    @Test
    fun `a call nobody spoke on is not kept, however long`() {
        assertFalse(Keep.worth(callMs = 600_000, speechMs = 0))
        assertFalse(Keep.worth(callMs = 600_000, speechMs = 299))
    }

    @Test
    fun `a short call with real speech is kept`() {
        assertTrue(Keep.worth(callMs = 1_500, speechMs = 300))
        assertTrue(Keep.worth(callMs = 12_000, speechMs = 7_000))
    }

    private fun line(text: String, confidence: Float = 0.9f) = Line(null, 0, text, confidence)

    @Test
    fun `a call with no words has no excerpt`() {
        assertNull(Excerpt.of(emptyList()))
        assertNull(Excerpt.of(listOf(line("  "))))
    }

    @Test
    fun `a short transcript is shown whole with nothing more to open`() {
        assertEquals(Excerpt("हलो कैसे हो ठीक हूँ", more = false), Excerpt.of(listOf(line("हलो कैसे हो"), line("ठीक हूँ"))))
    }

    @Test
    fun `lines the model doubted stay out, and the rest of the call can be opened`() {
        assertEquals(Excerpt("ठीक हूँ", more = true), Excerpt.of(listOf(line("गलत शब्द", 0.2f), line("ठीक हूँ"))))
    }

    @Test
    fun `a call of only doubted lines still shows them`() {
        assertEquals("शायद यह", Excerpt.of(listOf(line("शायद यह", 0.2f)))?.text)
    }

    @Test
    fun `a long call shows its middle, not its hellos`() {
        val lines = listOf(line("hello hello")) + (1..40).map { line("line number $it of the call") } + line("okay bye")
        val excerpt = Excerpt.of(lines)!!
        assertTrue(excerpt.more)
        assertFalse(excerpt.text.contains("hello"))
        assertFalse(excerpt.text.contains("bye"))
        assertTrue(excerpt.text.contains("line number 20"))
        assertTrue(excerpt.text.startsWith("… ") && excerpt.text.endsWith(" …"))
        assertTrue(excerpt.text.length <= Excerpt.LONGEST + 4)
    }

    @Test
    fun `one very long line is cut between words`() {
        val excerpt = Excerpt.of(listOf(line("शब्द ".repeat(200).trim())))!!
        assertTrue(excerpt.more)
        assertTrue(excerpt.text.removeSuffix(" …").split(' ').all { it == "शब्द" })
        assertTrue(excerpt.text.endsWith(" …"))
    }
}
