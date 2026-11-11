package io.github.jaswantk917.ghanti.incall

import android.content.Context
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
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
    /** for a conference: the people in it, each still its own call in Telecom */
    val participants: List<CallUi> = emptyList(),
    /** a participant that can be taken back out to talk privately */
    val canSplit: Boolean = false,
    /** a participant that can be dropped while the rest carry on */
    val canDrop: Boolean = false,
) {
    val title: String get() = if (participants.isNotEmpty()) names(participants.map { it.title }) else person?.name ?: networkName ?: number.ifBlank { "Private number" }
    val silenced get() = outcome?.rang == false
}

internal fun names(all: List<String>) = when (all.size) {
    1 -> all[0]
    2 -> "${all[0]} & ${all[1]}"
    else -> all.dropLast(1).joinToString(", ") + " & " + all.last()
}

data class Ended(val title: String, val connectedAt: Long, val endedAt: Long)

object Calls {
    /** each call with who its number belonged to when last looked up */
    private val tracked = LinkedHashMap<Call, Pair<String, Person?>>()
    private var context: Context? = null
    /** a conference's names, kept for its "Call ended" beat: the people leave before the conference does */
    private val conferenceNames = HashMap<Call, String>()
    private val _calls = MutableStateFlow<List<CallUi>>(emptyList())
    val calls: StateFlow<List<CallUi>> = _calls
    private val _audio = MutableStateFlow<CallAudioState?>(null)
    val audio: StateFlow<CallAudioState?> = _audio
    private val _ended = MutableStateFlow<Ended?>(null)
    /** the call that just finished, for the "Call ended" beat */
    val ended: StateFlow<Ended?> = _ended

    internal var service: GhantiInCallService? = null

    internal fun track(context: Context, call: Call) {
        this.context = context.applicationContext
        val number = numberOf(call)
        tracked[call] = number to Contacts.lookup(context, number)
        _ended.value = null
        publish()
    }

    internal fun untrack(call: Call) {
        val gone = _calls.value.firstOrNull { it.call == call }
        tracked.remove(call)
        if (tracked.isEmpty() && gone != null && gone.phase != Phase.Ringing) {
            _ended.value = Ended(conferenceNames[call] ?: gone.title, gone.connectedAt, System.currentTimeMillis())
            // only when the last call goes (a merge closes the calls it swallows while the conference carries on),
            // and not when you hung up yourself: the end button already gave its thud
            val yours = call.details.disconnectCause?.code == DisconnectCause.LOCAL
            if (gone.connectedAt > 0 && !yours) service?.let(CallBuzz::ended)
        }
        conferenceNames.remove(call)
        publish()
    }

    internal fun setAudio(state: CallAudioState?) {
        _audio.value = state
    }

    internal fun publish() {
        // a call's number can change under it: when a conference drops to one person, the network
        // turns the conference into that person's call, so look them up again
        context?.let { context ->
            tracked.entries.forEach { entry ->
                val number = numberOf(entry.key)
                if (number != entry.value.first) entry.setValue(number to Contacts.lookup(context, number))
            }
        }
        val before = _calls.value.associate { it.call to it.phase }
        val all = tracked.map { (call, known) -> describe(call, known.second) }
        val byCall = all.associateBy { it.call }
        // merged calls live inside their conference: Telecom keeps them as children, still "active",
        // and listing them beside it made one look like it was still going and the other look swappable
        _calls.value = all.filter { it.call.parent?.let { parent -> parent !in tracked } ?: true }
            .map { ui ->
                val people = ui.call.children.mapNotNull { byCall[it] }.filter { it.phase != Phase.Ended }
                if (people.isEmpty()) ui else ui.copy(isConference = true, participants = people).also { conferenceNames[ui.call] = it.title }
            }
            .filter { it.phase != Phase.Ended }
            // the call needing a decision first, then the live one
            .sortedBy { it.phase.ordinal }
        // the other side picked up
        if (_calls.value.any { before[it.call] == Phase.Dialing && it.phase == Phase.Active }) service?.let(CallBuzz::connected)
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
    /** take one person out of the conference to talk privately; the rest go on hold */
    fun split(call: Call) = call.splitFromConference()
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
            canSplit = d.can(Call.Details.CAPABILITY_SEPARATE_FROM_CONFERENCE),
            canDrop = d.can(Call.Details.CAPABILITY_DISCONNECT_FROM_CONFERENCE),
            isConference = d.hasProperty(Call.Details.PROPERTY_CONFERENCE),
            sims = sims,
        )
    }
}
