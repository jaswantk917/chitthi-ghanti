package io.github.jaswantk917.tring.recorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallersTest {
    @Test
    fun `a number from home reads without the country code`() {
        assertEquals("98765 43210", Callers.show("+919876543210", "91"))
        assertEquals("98765 43210", Callers.show("+91 98765 43210", "91"))
        assertEquals("98765 43210", Callers.show("919876543210", "91"))
        assertEquals("98765 43210", Callers.show("09876543210", "91"))
        assertEquals("98765 43210", Callers.show("9876543210", "91"))
    }

    @Test
    fun `a foreign number, a name, or a phone abroad keeps it as it is`() {
        assertEquals("+14155550123", Callers.show("+14155550123", "91"))
        assertEquals("Babu Bhaiya", Callers.show("Babu Bhaiya", "91"))
        assertEquals("+919876543210", Callers.show("+919876543210", null))
        assertEquals("Private number", Callers.show("Private number", "91"))
    }

    @Test
    fun `a stranger shows what Tring knows of them, a contact keeps their name`() {
        assertEquals("Swiggy delivery?", Callers.pick("+919876543210", saved = false, label = "Swiggy delivery?", home = "91"))
        assertEquals("98765 43210", Callers.pick("+919876543210", saved = false, label = null, home = "91"))
        assertEquals("Simran", Callers.pick("Simran", saved = true, label = "Swiggy delivery", home = "91"))
    }

    @Test
    fun `a confirmed label is only replaced by another confirmation`() {
        assertTrue("a guess replaces a guess", Callers.keeps(hadConfirmed = false, confirmed = false))
        assertTrue("a confirmation replaces a guess", Callers.keeps(hadConfirmed = false, confirmed = true))
        assertFalse("a guess never replaces what you confirmed", Callers.keeps(hadConfirmed = true, confirmed = false))
        assertTrue("you can confirm it again as something else", Callers.keeps(hadConfirmed = true, confirmed = true))
    }

    @Test
    fun `only something like a phone number counts as one`() {
        assertTrue(Callers.isNumber("+91 98765 43210"))
        assertFalse(Callers.isNumber("Call"))
        assertFalse(Callers.isNumber("12345"))
    }
}
