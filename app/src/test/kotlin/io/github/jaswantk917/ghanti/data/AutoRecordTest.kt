package io.github.jaswantk917.ghanti.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoRecordTest {
    private val chosen = setOf("9876543210")

    @Test
    fun `off records nothing`() = assertFalse(AutoRecord.Off.wants("+919876543210", isContact = true, chosen))

    @Test
    fun `a picked person records whichever way their number is written`() {
        assertTrue(AutoRecord.Chosen.wants("+91 98765 43210", isContact = true, chosen))
        assertTrue(AutoRecord.Chosen.wants("09876543210", isContact = true, chosen))
        assertFalse(AutoRecord.Chosen.wants("+919999999999", isContact = true, chosen))
    }

    @Test
    fun `a private number is never someone you picked`() = assertFalse(AutoRecord.Chosen.wants("", isContact = false, setOf("")))

    @Test
    fun `strangers only`() {
        assertTrue(AutoRecord.Strangers.wants("+919999999999", isContact = false, chosen))
        assertFalse(AutoRecord.Strangers.wants("+919876543210", isContact = true, chosen))
    }

    @Test
    fun `every call`() = assertTrue(AutoRecord.Everyone.wants("", isContact = false, emptySet()))
}
