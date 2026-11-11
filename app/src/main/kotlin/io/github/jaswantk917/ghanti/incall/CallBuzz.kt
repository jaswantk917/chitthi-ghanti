package io.github.jaswantk917.ghanti.incall

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibrationEffect.Composition
import android.os.Vibrator
import android.os.VibratorManager
import io.github.jaswantk917.ghanti.GhantiApp

/**
 * The call-status pulses, felt with the phone at your ear and the screen off: firm when the other
 * side picks up, soft when they hang up or the call drops. Played from the call service, since no
 * screen is showing.
 */
internal object CallBuzz {
    fun connected(context: Context) = play(context, connect = true)

    fun ended(context: Context) = play(context, connect = false)

    private fun play(context: Context, connect: Boolean) {
        if (!GhantiApp.instance.prefs.settings.value.callBuzz) return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
        if (!vibrator.hasVibrator()) return
        val primitive = if (connect) Composition.PRIMITIVE_CLICK else Composition.PRIMITIVE_TICK
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator.areAllPrimitivesSupported(primitive)) {
            VibrationEffect.startComposition().addPrimitive(primitive, if (connect) 1f else 0.7f).compose()
        } else {
            VibrationEffect.createPredefined(if (connect) VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_TICK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // a call starting or ending: follows Android's own vibration switch, not touch feedback
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_COMMUNICATION_REQUEST))
        } else {
            vibrator.vibrate(effect)
        }
    }
}
