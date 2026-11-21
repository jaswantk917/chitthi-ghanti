package io.github.jaswantk917.ghanti.incall

import android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS
import android.app.NotificationManager.INTERRUPTION_FILTER_ALL
import android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
import io.github.jaswantk917.ghanti.screen.Outcome
import io.github.jaswantk917.ghanti.signals.Guess
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RingThroughTest {
    private val blinkit = Outcome("7935048311", 0, rang = true, guess = Guess("Probably Blinkit delivery", confident = true, brands = listOf("Blinkit")))

    @Test
    fun `an expected delivery rings through do not disturb`() {
        assertTrue(RingThrough.needed(blinkit, INTERRUPTION_FILTER_PRIORITY))
        assertTrue(RingThrough.needed(blinkit, INTERRUPTION_FILTER_ALARMS))
    }

    @Test
    fun `a stranger calling again rings through`() =
        assertTrue(RingThrough.needed(Outcome("9", 0, rang = true, guess = null, repeat = true), INTERRUPTION_FILTER_PRIORITY))

    @Test
    fun `without do not disturb the phone rings by itself`() = assertFalse(RingThrough.needed(blinkit, INTERRUPTION_FILTER_ALL))

    @Test
    fun `a silenced stranger stays silent`() =
        assertFalse(RingThrough.needed(blinkit.copy(rang = false), INTERRUPTION_FILTER_PRIORITY))

    @Test
    fun `a stranger rung only because silencing is off is left to do not disturb`() =
        assertFalse(RingThrough.needed(Outcome("9", 0, rang = true, guess = null), INTERRUPTION_FILTER_PRIORITY))

    @Test
    fun `contacts are left to do not disturb`() = assertFalse(RingThrough.needed(null, INTERRUPTION_FILTER_PRIORITY))
}
