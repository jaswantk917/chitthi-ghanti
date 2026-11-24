package io.github.jaswantk917.tring.recorder

import android.telecom.Call
import android.telecom.InCallService

/** Telecom's line into the recorder on every call; it only acts on one Tring handed over. */
class ScreenService : InCallService() {
    override fun onCallAdded(call: Call) = Screening.added(this, call)

    override fun onCallRemoved(call: Call) = Screening.removed(call)
}
