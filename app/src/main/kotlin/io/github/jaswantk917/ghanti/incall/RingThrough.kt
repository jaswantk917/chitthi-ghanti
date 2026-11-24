package io.github.jaswantk917.ghanti.incall

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telecom.Call
import io.github.jaswantk917.ghanti.GhantiApp
import io.github.jaswantk917.ghanti.screen.Outcome

/**
 * Do Not Disturb mutes strangers after Ghanti has let them ring (Pixel 9, 2026-11-20: a Blinkit
 * rider called four times at 1 am, every call logged USER_MISSED_DND_MODE). A stranger Ghanti let
 * through for a reason, a delivery on its way or a second call, rings on the alarm volume instead,
 * which Do Not Disturb lets through by default.
 */
internal object RingThrough {
    private var ringing: Call? = null
    private var tone: Ringtone? = null

    fun needed(outcome: Outcome?, interruptionFilter: Int): Boolean =
        outcome != null && outcome.rang && (outcome.guess != null || outcome.repeat) &&
            interruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            interruptionFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN

    /** true when it rang: the notification is muted too, so the caller should open the call screen itself */
    fun start(context: Context, call: Call): Boolean {
        val number = call.details.handle?.schemeSpecificPart.orEmpty()
        val outcome = GhantiApp.instance.screenLog.forCall(number, call.details.creationTimeMillis)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (ringing != null || !needed(outcome, manager.currentInterruptionFilter)) return false
        // Do Not Disturb lets this one through by itself (a repeat caller, or anyone), so the phone rings already
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            runCatching { manager.matchesCallFilter(Uri.fromParts("tel", number, null)) }.getOrDefault(false)
        ) return false
        play(context, call, alarm = true, vibrate = true)
        return true
    }

    /** a call the recorder handed back as important, or never took: Telecom was told to stay quiet, so the ring is Tring's */
    fun ringNow(context: Context, call: Call) {
        if (ringing != null) return
        val filter = context.getSystemService(NotificationManager::class.java).currentInterruptionFilter
        val doNotDisturb = filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        val silent = context.getSystemService(AudioManager::class.java).ringerMode == AudioManager.RINGER_MODE_SILENT
        play(context, call, alarm = doNotDisturb, vibrate = doNotDisturb || !silent)
    }

    private fun play(context: Context, call: Call, alarm: Boolean, vibrate: Boolean) {
        ringing = call
        val usage = if (alarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION_RINGTONE
        val attributes = AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        tone = RingtoneManager.getRingtone(context, RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE))?.apply {
            audioAttributes = attributes
            isLooping = true
            play()
        }
        if (!vibrate) return
        val pattern = VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator(context).vibrate(pattern, VibrationAttributes.createForUsage(if (alarm) VibrationAttributes.USAGE_ALARM else VibrationAttributes.USAGE_RINGTONE))
        } else {
            @Suppress("DEPRECATION")
            vibrator(context).vibrate(pattern, attributes)
        }
    }

    /** answered, declined, hung up, or silenced with the power or volume key */
    fun stop(context: Context, call: Call? = null) {
        if (ringing == null || (call != null && call != ringing)) return
        ringing = null
        tone?.stop()
        tone = null
        vibrator(context).cancel()
    }

    private fun vibrator(context: Context): Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        context.getSystemService(Vibrator::class.java)
    }
}
