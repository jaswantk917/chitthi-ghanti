package io.github.jaswantk917.tring.recorder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LineTest {
    @Test
    fun `a line heard below half sure is unclear`() = assertTrue(Line(false, 0, "बस", 0.49f).unsure)

    @Test
    fun `a line heard half sure or better is kept plain`() = assertFalse(Line(true, 0, "अच्छा यहाँ पे पेड़ बैठ सकता है", 0.5f).unsure)

    @Test
    fun `an old line without a confidence is kept plain`() = assertFalse(Line(null, 0, "hello").unsure)
}
