package io.github.jaswantk917.ghanti.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsualNumberTest {
    private val home = PhoneNumber("+91 98765 43210", "Mobile")
    private val work = PhoneNumber("080 4123 4567", "Work")

    @Test fun `one number is called without asking`() {
        assertEquals(home, Contact(1, "A", null, true, listOf(home)).usualNumber(null))
    }

    @Test fun `several numbers ask unless one was chosen`() {
        val c = Contact(1, "A", null, true, listOf(home, work))
        assertNull(c.usualNumber(null))
        assertEquals(home, c.usualNumber("9876543210"))
    }

    @Test fun `a chosen number that was since deleted asks again`() {
        val c = Contact(1, "A", null, true, listOf(home, work))
        assertNull(c.usualNumber("9000000000"))
    }
}
