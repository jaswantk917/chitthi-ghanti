package io.github.jaswantk917.ghanti.incall

import io.github.jaswantk917.ghanti.incall.MissedCallReminder.LaterCall
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MissedCallReminderTest {
    private val missed = 1_000_000L
    private val asha = "+91 98765 43210"

    @Test
    fun `a miss nobody did anything about comes back`() = assertTrue(MissedCallReminder.due(missed, asha, seenAt = 0, later = emptyList()))

    @Test
    fun `opening Ghanti after the miss settles it`() = assertFalse(MissedCallReminder.due(missed, asha, seenAt = missed + 1, later = emptyList()))

    @Test
    fun `opening Ghanti before the miss doesn't`() = assertTrue(MissedCallReminder.due(missed, asha, seenAt = missed - 1, later = emptyList()))

    @Test
    fun `calling them back settles it, whatever the number's format`() =
        assertFalse(MissedCallReminder.due(missed, asha, 0, listOf(LaterCall(missed + 60_000, "09876543210", reached = true))))

    @Test
    fun `them calling again and getting through settles it`() =
        assertFalse(MissedCallReminder.due(missed, asha, 0, listOf(LaterCall(missed + 60_000, "9876543210", reached = true))))

    @Test
    fun `missing them again doesn't`() =
        assertTrue(MissedCallReminder.due(missed, asha, 0, listOf(LaterCall(missed + 60_000, "9876543210", reached = false))))

    @Test
    fun `calling someone else doesn't`() =
        assertTrue(MissedCallReminder.due(missed, asha, 0, listOf(LaterCall(missed + 60_000, "9123456789", reached = true))))

    @Test
    fun `a call before the miss doesn't count`() =
        assertTrue(MissedCallReminder.due(missed, asha, 0, listOf(LaterCall(missed - 60_000, "9876543210", reached = true))))

    @Test
    fun `several misses go by Ghanti being opened only`() {
        assertTrue(MissedCallReminder.due(missed, "", 0, listOf(LaterCall(missed + 60_000, "9876543210", reached = true))))
        assertFalse(MissedCallReminder.due(missed, "", missed + 5, emptyList()))
    }
}
