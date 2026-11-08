package io.github.jaswantk917.ghanti.screen

import android.telecom.Call
import android.telecom.CallScreeningService
import io.github.jaswantk917.block.BlockRules
import io.github.jaswantk917.chitthi.ChitthiApp
import io.github.jaswantk917.ghanti.GhantiApp
import io.github.jaswantk917.ghanti.data.Contacts
import io.github.jaswantk917.ghanti.signals.DeliveryRules

/**
 * Decides, before the phone rings, whether a stranger's call makes a sound. A silenced call still
 * shows quietly, so nothing is ever blocked.
 */
class Screener : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }
        val app = GhantiApp.instance
        val number = details.handle?.schemeSpecificPart.orEmpty()
        val now = System.currentTimeMillis()
        val isContact = Contacts.lookup(this, number) != null
        // a blocking rule rejects it outright; Android's own block list never even gets here
        if (BlockRules.forCall(ChitthiApp.instance.blocks.rules.value, number, isContact) != null) {
            respondToCall(details, CallResponse.Builder().setDisallowCall(true).setRejectCall(true).setSkipNotification(true).build())
            return
        }
        val decision = DeliveryRules.decide(
            number = number,
            isContact = isContact,
            signals = app.signals.current(now),
            lastSilencedFromSame = app.screenLog.lastSilenced(number),
            now = now,
            settings = app.prefs.settings.value.screen,
        )
        if (!isContact) app.screenLog.add(Outcome(DeliveryRules.last10(number), now, decision.ring, decision.guess, decision.repeat))
        respondToCall(details, CallResponse.Builder().setSilenceCall(!decision.ring).build())
    }
}
