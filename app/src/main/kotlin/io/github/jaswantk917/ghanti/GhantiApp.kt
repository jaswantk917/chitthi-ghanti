package io.github.jaswantk917.ghanti

import android.content.Context
import io.github.jaswantk917.ghanti.data.Memos
import io.github.jaswantk917.ghanti.data.Prefs
import io.github.jaswantk917.ghanti.incall.CallNotifications
import io.github.jaswantk917.ghanti.screen.ScreenLog
import io.github.jaswantk917.ghanti.signals.Signals

/** The phone side's state. One app holds both sides, so ChitthiApp starts this instead of it being an Application. */
class GhantiApp private constructor(context: Context) {
    val prefs by lazy { Prefs(context) }
    val signals by lazy { Signals(context) }
    val screenLog by lazy { ScreenLog(context) }
    val memos by lazy { Memos(context) }

    companion object {
        lateinit var instance: GhantiApp
            private set

        fun start(context: Context) {
            instance = GhantiApp(context.applicationContext)
            CallNotifications.createChannels(context)
        }
    }
}
