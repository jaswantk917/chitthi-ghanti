package io.github.jaswantk917.ghanti.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpecialCodesTest {
    @Test
    fun `secret codes give the digits between the markers`() {
        assertEquals(SpecialCode.Secret("4636"), SpecialCode.parse("*#*#4636#*#*"))
        assertEquals(SpecialCode.Secret("225"), SpecialCode.parse("*#*#225#*#*"))
    }

    @Test
    fun `imei code`() {
        assertEquals(SpecialCode.Imei, SpecialCode.parse("*#06#"))
    }

    @Test
    fun `half typed codes wait`() {
        assertNull(SpecialCode.parse("*#*#4636#*#"))
        assertNull(SpecialCode.parse("*#*#4636"))
        assertNull(SpecialCode.parse("*#06"))
    }

    @Test
    fun `numbers and carrier codes are left for calling`() {
        assertNull(SpecialCode.parse("9876543210"))
        assertNull(SpecialCode.parse("*123#"))
        assertNull(SpecialCode.parse("*#*##*#*"))
        assertNull(SpecialCode.parse("1*#*#4636#*#*"))
    }
}
