package io.github.jaswantk917.ghanti.incall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Decline and hang up from the notification; answering opens the call screen instead. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val call = Calls.primary?.call ?: return
        when (intent.action) {
            DECLINE -> Calls.decline(call)
            HANG_UP -> Calls.hangUp(call)
        }
    }

    companion object {
        const val DECLINE = "io.github.jaswantk917.ghanti.DECLINE"
        const val HANG_UP = "io.github.jaswantk917.ghanti.HANG_UP"
    }
}
