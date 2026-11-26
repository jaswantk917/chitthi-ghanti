package io.github.jaswantk917.ghanti.incall

import org.junit.Assert.assertEquals
import org.junit.Test

class CallNotificationsTest {
    @Test
    fun `a silenced call pops up silently only while the screen is on`() {
        assertEquals("ringing_quiet_popup", CallNotifications.ringingChannel(silenced = true, screened = false, screenOn = true))
        assertEquals("ringing_quiet", CallNotifications.ringingChannel(silenced = true, screened = false, screenOn = false))
    }

    @Test
    fun `a call that rings, or one the recorder is screening, keeps its channel`() {
        assertEquals("ringing", CallNotifications.ringingChannel(silenced = false, screened = false, screenOn = true))
        assertEquals("ringing_quiet", CallNotifications.ringingChannel(silenced = false, screened = true, screenOn = true))
    }
}
