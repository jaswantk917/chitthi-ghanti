package io.github.jaswantk917.ghanti.incall

import android.content.Context
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.PhoneAccountHandle
import android.telecom.VideoProfile
import io.github.jaswantk917.ghanti.GhantiApp
import io.github.jaswantk917.ghanti.data.Contacts
import io.github.jaswantk917.ghanti.data.Person
import io.github.jaswantk917.ghanti.screen.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class Phase { Ringing, Dialing, Active, Holding, ChooseSim, Ended }

/** One call as the screen needs it; rebuilt on every Telecom callback so Compose sees each change. */
data class CallUi(
    val call: Call,
    val phase: Phase,
    val number: String,
    val person: Person?,
    /** the name the network sent, e.g. Jio's caller name */
    val networkName: String?,
    val connectedAt: Long,
    /** when Telecom first saw it; the call log dates the call by this, so memos key on it */
    val createdAt: Long,
    val outcome: Outcome?,
    val canHold: Boolean,
    val canMerge: Boolean,
    val canSwap: Boolean,
    val isConference: Boolean,
    val sims: List<PhoneAccountHandle>,
) {
    val title get() = person?.name ?: networkName ?: number.ifBlank { "Private number" }
    val silenced get() = outcome?.rang == false
}

data class Ended(val title: String, val connectedAt: Long, val endedAt: Long)

object Calls {
    private val tracked = LinkedHashMap<Call, Person?>()
    private val _calls = MutableStateFlow<List<CallUi>>(emptyList())
    val calls: StateFlow<List<CallUi>> = _calls
    private val _audio = MutableStateFlow<CallAudioState?>(null)
    val audio: StateFlow<CallAudioState?> = _audio
    private val _ended = MutableStateFlow<Ended?>(null)
    /** the call that just finished, for the "Call ended" beat */
    val ended: StateFlow<Ended?> = _ended

    internal var service: GhantiInCallService? = null

    internal fun track(context: Context, call: Call) {
        tracked[call] = Contacts.lookup(context, numberOf(call))
        _ended.value = null
        publish()
    }

    internal fun untrack(call: Call) {
        val gone = _calls.value.firstOrNull { it.call == call }
        tracked.remove(call)
        if (tracked.isEmpty() && gone != null && gone.phase != Phase.Ringing) {
            _ended.value = Ended(gone.title, gone.connectedAt, System.currentTimeMillis())
        }
        publish()
    }

    internal fun setAudio(state: CallAudioState?) {
        _audio.value = state
    }

    internal fun publish() {
        _calls.value = tracked.map { (call, person) -> describe(call, person) }
            .filter { it.phase != Phase.Ended }
            // the call needing a decision first, then the live one
            .sortedBy { it.phase.ordinal }
    }

    /** the one the main screen is about */
    val primary get() = _calls.value.firstOrNull { it.phase == Phase.Ringing || it.phase == Phase.ChooseSim }
        ?: _calls.value.firstOrNull { it.phase == Phase.Active || it.phase == Phase.Dialing }
        ?: _calls.value.firstOrNull()

    fun answer(call: Call) = call.answer(VideoProfile.STATE_AUDIO_ONLY)
    fun decline(call: Call) = call.reject(false, null)
    /** Telecom hands the text to the SMS app to send */
    fun declineWith(call: Call, text: String) = call.reject(true, text)
    fun hangUp(call: Call) = call.disconnect()
    fun hold(call: Call, on: Boolean) = if (on) call.hold() else call.unhold()
    fun mute(on: Boolean) = service?.setMuted(on)
    fun route(route: Int) = service?.setAudioRoute(route)
    fun tone(call: Call, digit: Char) {
        call.playDtmfTone(digit)
        call.stopDtmfTone()
    }
    /** back to the held call; Telecom puts the live one on hold itself */
    fun swap() = _calls.value.firstOrNull { it.phase == Phase.Holding }?.call?.unhold()
    fun merge(call: Call) {
        val other = call.conferenceableCalls.firstOrNull()
        if (other != null) call.conference(other) else call.mergeConference()
    }
    fun chooseSim(call: Call, sim: PhoneAccountHandle) = call.phoneAccountSelected(sim, false)

    private fun numberOf(call: Call) = call.details.handle?.schemeSpecificPart.orEmpty()

    private fun describe(call: Call, person: Person?): CallUi {
        val d = call.details
        val number = numberOf(call)
        @Suppress("DEPRECATION") // getState() replaces this from API 31 only
        val state = call.state
        val phase = when (state) {
            Call.STATE_RINGING -> Phase.Ringing
            Call.STATE_SELECT_PHONE_ACCOUNT -> Phase.ChooseSim
            Call.STATE_NEW, Call.STATE_CONNECTING, Call.STATE_DIALING, Call.STATE_PULLING_CALL -> Phase.Dialing
            Call.STATE_ACTIVE -> Phase.Active
            Call.STATE_HOLDING -> Phase.Holding
            else -> Phase.Ended
        }
        @Suppress("DEPRECATION")
        val sims = call.details.intentExtras?.getParcelableArrayList<PhoneAccountHandle>(Call.AVAILABLE_PHONE_ACCOUNTS).orEmpty()
        return CallUi(
            call = call,
            phase = phase,
            number = number,
            person = person,
            networkName = d.callerDisplayName?.takeIf { it.isNotBlank() },
            connectedAt = d.connectTimeMillis,
            createdAt = d.creationTimeMillis,
            outcome = GhantiApp.instance.screenLog.forCall(number, d.creationTimeMillis),
            canHold = d.can(Call.Details.CAPABILITY_HOLD) || d.can(Call.Details.CAPABILITY_SUPPORT_HOLD),
            canMerge = call.conferenceableCalls.isNotEmpty() || d.can(Call.Details.CAPABILITY_MERGE_CONFERENCE),
            canSwap = tracked.size > 1,
            isConference = d.hasProperty(Call.Details.PROPERTY_CONFERENCE),
            sims = sims,
        )
    }
}
