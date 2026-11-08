package io.github.jaswantk917.ghanti.incall

import android.content.Intent
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

/** Telecom's line into Ghanti: every call on the phone passes through here while Ghanti is the phone app. */
class GhantiInCallService : InCallService() {
    private var proximity: PowerManager.WakeLock? = null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = changed()
        override fun onDetailsChanged(call: Call, details: Call.Details) = changed()
        override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: MutableList<Call>) = changed()
    }

    override fun onCreate() {
        super.onCreate()
        Calls.service = this
    }

    override fun onDestroy() {
        Calls.service = null
        releaseProximity()
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        call.registerCallback(callback)
        Calls.track(this, call)
        changed()
        // an incoming call shows through its notification: full screen when locked, a banner when not
        @Suppress("DEPRECATION")
        if (call.state != Call.STATE_RINGING) {
            startActivity(Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        Calls.untrack(call)
        changed()
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        Calls.setAudio(audioState)
        updateProximity()
    }

    private fun changed() {
        Calls.publish()
        CallNotifications.update(this)
        updateProximity()
    }

    /** the screen goes dark against the cheek, only while talking on the earpiece */
    private fun updateProximity() {
        val talking = Calls.calls.value.any { it.phase == Phase.Active || it.phase == Phase.Dialing || it.phase == Phase.Holding } &&
            Calls.calls.value.none { it.phase == Phase.Ringing }
        val earpiece = Calls.audio.value?.route?.let { it == CallAudioState.ROUTE_EARPIECE } ?: true
        if (talking && earpiece) acquireProximity() else releaseProximity()
    }

    private fun acquireProximity() {
        if (proximity?.isHeld == true) return
        val power = getSystemService(PowerManager::class.java)
        if (!power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
        proximity = power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "ghanti:proximity").apply {
            setReferenceCounted(false)
            acquire(4 * 60 * 60_000L)
        }
    }

    private fun releaseProximity() {
        proximity?.takeIf { it.isHeld }?.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
        proximity = null
    }
}
