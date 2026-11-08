package io.github.jaswantk917.chitthi

import android.app.Application
import io.github.jaswantk917.ghanti.GhantiApp
import io.github.jaswantk917.chitthi.ai.Nano
import io.github.jaswantk917.chitthi.data.Brands
import io.github.jaswantk917.chitthi.data.Inbox
import io.github.jaswantk917.chitthi.data.LocalStore
import io.github.jaswantk917.chitthi.data.Prefs
import io.github.jaswantk917.chitthi.notify.Notifier

class ChitthiApp : Application() {
    val prefs by lazy { Prefs(this) }
    val store by lazy { LocalStore(this) }
    val brands by lazy { Brands(assets.open("brands.json").bufferedReader().use { it.readText() }) }
    val inbox by lazy { Inbox(this, store, brands, prefs, Nano()) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Notifier.createChannels(this)
        GhantiApp.start(this)
    }

    companion object {
        lateinit var instance: ChitthiApp
            private set
    }
}
