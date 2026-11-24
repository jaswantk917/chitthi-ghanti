package io.github.jaswantk917.tring.recorder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicemailTest {
    @Test
    fun `someone who hasn't spoken yet gets seven seconds to start`() {
        assertFalse(Messages.over(elapsedMs = 6_000, quietMs = 6_000, spoken = false, voicemail = true))
        assertTrue(Messages.over(elapsedMs = 7_000, quietMs = 7_000, spoken = false, voicemail = true))
    }

    @Test
    fun `a message ends a few seconds after they stop talking`() {
        assertFalse(Messages.over(elapsedMs = 20_000, quietMs = 3_000, spoken = true, voicemail = true))
        assertTrue(Messages.over(elapsedMs = 20_000, quietMs = 3_500, spoken = true, voicemail = true))
    }

    @Test
    fun `a long message is cut at a minute`() {
        assertFalse(Messages.over(elapsedMs = 59_000, quietMs = 0, spoken = true, voicemail = true))
        assertTrue(Messages.over(elapsedMs = 60_000, quietMs = 0, spoken = true, voicemail = true))
    }

    @Test
    fun `a screened caller's message keeps its shorter limits`() {
        assertTrue(Messages.over(elapsedMs = 20_000, quietMs = 0, spoken = true, voicemail = false))
        assertTrue(Messages.over(elapsedMs = 4_000, quietMs = 3_500, spoken = false, voicemail = false))
    }

    @Test
    fun `a voicemail with speech but no recognised words is kept`() {
        assertTrue(Messages.keep(pickedUp = false, words = "", voicemail = true, spoke = true))
    }

    @Test
    fun `silence is not a message`() {
        assertFalse(Messages.keep(pickedUp = false, words = "", voicemail = true, spoke = false))
        assertFalse(Messages.keep(pickedUp = false, words = "", voicemail = false, spoke = true))
    }

    @Test
    fun `a call you picked up is not a message`() {
        assertFalse(Messages.keep(pickedUp = true, words = "मैं गेट पर हूँ", voicemail = true, spoke = true))
    }

    @Test
    fun `a call Tring kept quiet is never answered for a message`() {
        assertFalse(Messages.mayAnswer("9000041234", silenced = "9000041234" to 1_000L, now = 1_500L))
    }

    @Test
    fun `a private number Tring kept quiet is left alone too`() {
        assertFalse(Messages.mayAnswer("", silenced = "" to 1_000L, now = 1_500L))
    }

    @Test
    fun `a call that rang you is answered when nobody picks up`() {
        assertTrue(Messages.mayAnswer("9000041234", silenced = null, now = 1_500L))
        assertTrue(Messages.mayAnswer("9000041234", silenced = "9000077123" to 1_000L, now = 1_500L))
    }

    @Test
    fun `an old quiet call doesn't stop the next one from the same number`() {
        assertTrue(Messages.mayAnswer("9000041234", silenced = "9000041234" to 1_000L, now = 1_000L + 60_000L))
    }
}
