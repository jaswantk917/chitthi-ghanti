package io.github.jaswantk917.ghanti.incall

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.Call
import android.util.Log
import io.github.jaswantk917.ghanti.signals.DeliveryRules

/**
 * A call Tring Recorder offered to screen. Tring stays out of the way while it does, and takes the
 * call back if the recorder doesn't pick it up in [TAKE_MS]. Tring itself holds no call audio.
 */
internal object ScreenHandoff {
    const val AUDIO_PROCESSING = 12
    const val SIMULATED_RINGING = 13
    const val TAKE_MS = 3_000L
    private const val OFFER_MS = 8_000L

    private class Offer(val number: String, val ringIfDropped: Boolean, val at: Long)

    @Volatile private var offer: Offer? = null
    private val held = HashMap<Call, Boolean>()
    private val screened = HashSet<Call>()

    fun matches(offered: String, from: String, age: Long) = offered.isNotEmpty() && offered == DeliveryRules.last10(from) && age in 0..OFFER_MS

    /** asks the recorder before the phone rings; false from a recorder that can't or doesn't screen */
    fun offer(context: Context, number: String, stranger: Boolean, wouldRing: Boolean): Boolean {
        val taking = try {
            context.contentResolver.call(
                Uri.parse("content://${Recorder.PACKAGE}.recordings"), "screen", number,
                Bundle().apply { putBoolean("stranger", stranger) },
            )?.getBoolean("taking") == true
        } catch (e: Exception) {
            Log.w("ScreenHandoff", "the recorder didn't answer", e)
            false
        }
        if (taking) offer = Offer(DeliveryRules.last10(number), wouldRing, System.currentTimeMillis())
        return taking
    }

    /** tells the recorder a call won't make a sound, so its voicemail leaves it alone; an older recorder ignores this */
    fun silenced(context: Context, number: String) {
        try {
            context.contentResolver.call(Uri.parse("content://${Recorder.PACKAGE}.recordings"), "silenced", number, null)
        } catch (e: Exception) {
            Log.w("ScreenHandoff", "the recorder didn't hear about a quiet call", e)
        }
    }

    /**
     * tells the recorder what Tring knows of a stranger: the likely label ("Swiggy delivery?") as the call
     * comes in, or the one you confirmed; it names their recordings and messages by it. An older recorder ignores this.
     */
    fun describe(context: Context, number: String, label: String, confirmed: Boolean) {
        if (number.isBlank() || label.isBlank() || !Recorder.installed(context)) return
        try {
            context.contentResolver.call(
                Uri.parse("content://${Recorder.PACKAGE}.recordings"), "describe", number,
                Bundle().apply { putString("label", label); putBoolean("confirmed", confirmed) },
            )
        } catch (e: Exception) {
            Log.w("ScreenHandoff", "the recorder didn't hear who called", e)
        }
    }

    fun claim(call: Call): Boolean {
        val mine = offer ?: return false
        if (!matches(mine.number, call.details.handle?.schemeSpecificPart.orEmpty(), System.currentTimeMillis() - mine.at)) return false
        offer = null
        held[call] = mine.ringIfDropped
        return true
    }

    fun isHeld(call: Call) = call in held

    fun entered(call: Call) {
        held.remove(call)
        screened += call
    }

    fun wasScreened(call: Call) = call in screened

    /** the recorder never took it; true when it should ring now */
    fun dropped(call: Call): Boolean? = held.remove(call)

    fun forget(call: Call) {
        held.remove(call)
        screened.remove(call)
    }
}
