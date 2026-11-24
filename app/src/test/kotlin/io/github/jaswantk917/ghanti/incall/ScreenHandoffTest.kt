package io.github.jaswantk917.ghanti.incall

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenHandoffTest {
    @Test
    fun `the offered number matches however it is written`() {
        assertTrue(ScreenHandoff.matches("9876543210", "+91 98765 43210", 100))
        assertTrue(ScreenHandoff.matches("9876543210", "09876543210", 100))
    }

    @Test
    fun `another number is not the offered call`() {
        assertFalse(ScreenHandoff.matches("9876543210", "+91 98765 43211", 100))
    }

    @Test
    fun `a stale offer is not used for a later call`() {
        assertFalse(ScreenHandoff.matches("9876543210", "9876543210", 8_001))
    }

    @Test
    fun `nothing matches an empty number`() {
        assertFalse(ScreenHandoff.matches("", "", 100))
    }
}
