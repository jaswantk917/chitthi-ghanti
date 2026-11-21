package io.github.jaswantk917.ghanti.incall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Answering cancels the ringing notification and its pop-up; on one id the pop-up stayed pinned through the call. */
class CallNotificationIdsTest {
    @Test
    fun `ringing and the answered call are separate notifications`() =
        assertNotEquals(CallNotifications.idFor(Phase.Ringing), CallNotifications.idFor(Phase.Active))

    @Test
    fun `every phase after ringing is the same notification`() =
        assertEquals(1, Phase.entries.filter { it != Phase.Ringing }.map(CallNotifications::idFor).toSet().size)

    @Test
    fun `the call notifications don't take the missed-call or reminder ids`() =
        assertEquals(emptySet<Int>(), Phase.entries.map(CallNotifications::idFor).toSet() intersect setOf(MissedCallReceiver.ID, 3))
}
