package io.github.jaswantk917.tring.recorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LineTest {
    @Test
    fun `a line heard below half sure is unclear`() = assertTrue(Line(false, 0, "बस", 0.49f).unsure)

    @Test
    fun `a line heard half sure or better is kept plain`() = assertFalse(Line(true, 0, "अच्छा यहाँ पे पेड़ बैठ सकता है", 0.5f).unsure)

    @Test
    fun `an old line without a confidence is kept plain`() = assertFalse(Line(null, 0, "hello").unsure)

    @Test
    fun `a saved line reads with nobody named, whatever side was guessed`() {
        assertEquals(Line(null, 1_000, "हलो", 0.9f), Line.read("T\t1000\t0.9\tहलो"))
        assertEquals(Line(null, 2_000, "हाँ", 0.8f), Line.read("Y\t2000\t0.8\tहाँ"))
    }

    @Test
    fun `a row from before confidences is not read`() = assertNull(Line.read("T\t1000\tहलो"))

    @Test
    fun `lines of the same minute make one paragraph, in order`() {
        val first = Line(null, 2_000, "हलो")
        val second = Line(null, 59_999, "हाँ")
        assertEquals(listOf(Minute(0, listOf(first, second))), Minute.of(listOf(first, second)))
    }

    @Test
    fun `a new minute starts a new paragraph and a silent minute shows nothing`() {
        val minutes = Minute.of(listOf(Line(null, 59_999, "a"), Line(null, 60_000, "b"), Line(null, 185_000, "c")))
        assertEquals(listOf(0, 1, 3), minutes.map { it.index })
        assertEquals(listOf(0, 60_000, 180_000), minutes.map { it.atMs })
    }

    @Test
    fun `no lines make no paragraphs`() = assertEquals(emptyList<Minute>(), Minute.of(emptyList()))
}
