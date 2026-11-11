package io.github.jaswantk917.ghanti.incall

import org.junit.Assert.assertEquals
import org.junit.Test

class ConferenceNamesTest {
    @Test
    fun `two people join with an ampersand`() = assertEquals("Aman & Riya", names(listOf("Aman", "Riya")))

    @Test
    fun `three or more read like a sentence`() = assertEquals("Aman, Riya & Dev", names(listOf("Aman", "Riya", "Dev")))

    @Test
    fun `one person is just their name`() = assertEquals("Aman", names(listOf("Aman")))
}
