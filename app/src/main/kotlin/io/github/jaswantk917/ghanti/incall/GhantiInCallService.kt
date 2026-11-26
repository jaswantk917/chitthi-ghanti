package io.github.jaswantk917.ghanti.incall

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import io.github.jaswantk917.ghanti.GhantiApp
import io.github.jaswantk917.ghanti.data.AutoRecord
import io.github.jaswantk917.ghanti.signals.StrangersRing

/** Telecom's line into Ghanti: every call on the phone passes through here while Ghanti is the phone app. */
class GhantiInCallService : InCallService() {
    private var proximity: PowerManager.WakeLock? = null
    /** calls auto-record has already looked at: once each, so stopping it by hand sticks through a hold */
    private val autoRecorded = HashSet<Call>()
    private val handler = Handler(Looper.getMainLooper())

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (state == ScreenHandoff.AUDIO_PROCESSING) ScreenHandoff.entered(call)
            if (state != Call.STATE_RINGING && state != ScreenHandoff.SIMULATED_RINGING) RingThrough.stop(this@GhantiInCallService, call)
            changed()
            // the recorder's screen stays up while it rings; Telecom was told to be quiet, so the sound is Tring's
            if (state == ScreenHandoff.SIMULATED_RINGING) RingThrough.ringNow(this@GhantiInCallService, call)
            if (state == Call.STATE_ACTIVE) {
                if (ScreenHandoff.wasScreened(call)) showCall()
                autoRecord(call)
            }
        }
        override fun onDetailsChanged(call: Call, details: Call.Details) = changed()
        override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: MutableList<Call>) = changed()
        override fun onParentChanged(call: Call, parent: Call?) = changed()
        override fun onChildrenChanged(call: Call, children: MutableList<Call>) = changed()
        override fun onPostDialWait(call: Call, remainingPostDialSequence: String) = Calls.waitForTones(call, remainingPostDialSequence)
    }

    override fun onCreate() {
        super.onCreate()
        Calls.service = this
        Recorder.listen(this)
    }

    override fun onDestroy() {
        Calls.service = null
        Recorder.stop(this)
        Recorder.unlisten(this)
        releaseProximity()
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        call.registerCallback(callback)
        val handedOver = ScreenHandoff.claim(call)
        if (handedOver) handler.postDelayed({ notTaken(call) }, ScreenHandoff.TAKE_MS)
        Calls.track(this, call)
        // before this call's own notification goes up: posting it records the caller, which would make
        // Do Not Disturb count this very call as a repeat
        @Suppress("DEPRECATION")
        val ringingThrough = !handedOver && call.state == Call.STATE_RINGING && RingThrough.start(this, call)
        changed()
        // an incoming call shows through its notification: full screen when locked, a banner when not;
        // one ringing through Do Not Disturb opens the screen itself, as Do Not Disturb hides its notification
        @Suppress("DEPRECATION")
        if (call.state != Call.STATE_RINGING || ringingThrough) showCall()
    }

    private fun showCall() = startActivity(Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    /** the recorder said it would take this call and didn't: it goes on as if it had never been offered */
    private fun notTaken(call: Call) {
        val shouldRing = ScreenHandoff.dropped(call) ?: return
        changed()
        if (shouldRing) RingThrough.ringNow(this, call)
    }

    override fun onCallRemoved(call: Call) {
        StrangersRing.callEnded(this, call.details.handle?.schemeSpecificPart.orEmpty())
        RingThrough.stop(this, call)
        call.unregisterCallback(callback)
        ScreenHandoff.forget(call)
        autoRecorded.remove(call)
        Calls.untrack(call)
        changed()
        if (Calls.calls.value.isEmpty()) Recorder.stop(this)
    }

    private fun autoRecord(call: Call) {
        if (call.parent != null || !autoRecorded.add(call) || Recorder.state.value != Recorder.State.Off) return
        val prefs = GhantiApp.instance.prefs
        val mode = prefs.settings.value.autoRecord
        if (mode == AutoRecord.Off || !Recorder.installed(this)) return
        val ui = Calls.calls.value.firstOrNull { it.call == call } ?: return
        if (mode.wants(ui.number, ui.person != null, prefs.recordNumbers.value)) Recorder.record(this, ui.title, ui.number, ui.person != null)
    }

    override fun onSilenceRinger() = RingThrough.stop(this)

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
