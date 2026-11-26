package io.github.jaswantk917.ghanti.signals

import io.github.jaswantk917.ghanti.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class StrangersRingTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun t(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 11, day, hour, minute).atZone(zone).toInstant().toEpochMilli()
    private val window = Prefs.StrangersRing(until = t(26, 18, 30), hours = 4)

    @Test
    fun `the call that rang because of it asks after it ends`() {
        assertTrue(StrangersRing.shouldAsk("9876500001", "+91 98765 00001", window, t(26, 15)))
    }

    @Test
    fun `any other call, or one after the window, doesn't ask`() {
        assertFalse(StrangersRing.shouldAsk(null, "+91 98765 00001", window, t(26, 15)))
        assertFalse(StrangersRing.shouldAsk("9876500002", "+91 98765 00001", window, t(26, 15)))
        assertFalse(StrangersRing.shouldAsk("9876500001", "+91 98765 00001", window, t(26, 19)))
    }

    @Test
    fun `a private number that rang because of it asks too`() {
        assertTrue(StrangersRing.shouldAsk("", "", window, t(26, 15)))
    }

    @Test
    fun `until reads as a time today, tomorrow, or a weekday`() {
        assertEquals("until 6:30 pm", StrangersRing.until(t(26, 18, 30), t(26, 14), zone))
        assertEquals("until tomorrow 10:00 am", StrangersRing.until(t(27, 10), t(26, 14), zone))
        assertEquals("until Sat 2:00 pm", StrangersRing.until(t(28, 14), t(26, 14), zone))
    }

    @Test
    fun `the header says it short`() {
        assertEquals("Strangers ring till 11:45 pm", StrangersRing.status(t(26, 23, 45), t(26, 14), zone))
        assertEquals("Strangers ring till tomorrow", StrangersRing.status(t(27, 10), t(26, 14), zone))
        assertEquals("Strangers ring till Sat", StrangersRing.status(t(28, 14), t(26, 14), zone))
    }

    @Test
    fun `the lengths on offer`() {
        assertEquals(listOf("1 hr", "4 hr", "8 hr", "1 day", "2 days"), StrangersRing.choices.map { it.second })
    }
}
