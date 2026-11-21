package io.github.jaswantk917.ghanti.incall

import android.app.Notification
import android.app.Person
import android.net.Uri
import android.telecom.Call
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

/** Do Not Disturb's "repeat callers" remembers callers only from the people on the phone app's call notification. */
class CallNotificationsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun blankCall(): Call {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        return unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, Call::class.java) as Call
    }

    @Test
    fun aRingingStrangerIsListedByNumber() {
        val rider = CallUi(
            call = blankCall(), phase = Phase.Ringing, number = "+917935048311", person = null, networkName = null,
            connectedAt = 0, createdAt = 0, outcome = null,
            canHold = false, canMerge = false, canSwap = false, isConference = false, sims = emptyList(),
        )
        @Suppress("DEPRECATION")
        val people = CallNotifications.build(context, rider).extras.getParcelableArrayList<Person>(Notification.EXTRA_PEOPLE_LIST).orEmpty()
        val uri = Uri.parse(people.single().uri)
        assertEquals("tel" to "+917935048311", uri.scheme to uri.schemeSpecificPart)
    }
}
