package io.github.jaswantk917.ghanti.incall

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.PowerManager
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.ServiceCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.jaswantk917.chitthi.R
import io.github.jaswantk917.ghanti.signals.StrangersRing

object CallNotifications {
    private const val TAG = "CallNotifications"
    private const val CH_RINGING = "ringing"
    private const val CH_QUIET = "ringing_quiet"
    private const val CH_QUIET_POPUP = "ringing_quiet_popup"
    private const val CH_ONGOING = "ongoing"
    const val CH_MISSED = "missed"
    const val CH_SILENCED = "silenced"
    private const val ID_CALL = 1
    // Ringing has its own id: on answer it's cancelled along with its pop-up, and the call posts
    // fresh at Default importance. Sharing one id, the ringing pop-up stayed pinned for the whole
    // call (Pixel, Android 17, 2026-11-21).
    private const val ID_RINGING = 4

    internal fun idFor(phase: Phase) = if (phase == Phase.Ringing) ID_RINGING else ID_CALL

    // whether each silenced call pops up, fixed when it starts ringing: the screen turning on later doesn't re-alert it
    private val popup = java.util.WeakHashMap<Call, Boolean>()

    /** a silenced call pops up only while you're using the phone; with the screen off it waits quietly in the shade */
    internal fun ringingChannel(silenced: Boolean, screened: Boolean, screenOn: Boolean) = when {
        screened -> CH_QUIET
        silenced -> if (screenOn) CH_QUIET_POPUP else CH_QUIET
        else -> CH_RINGING
    }

    fun createChannels(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                // Telecom plays the ringtone and vibrates; the channels stay silent so nothing doubles up
                NotificationChannel(CH_RINGING, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null); enableVibration(false)
                },
                NotificationChannel(CH_QUIET, "Silenced calls ringing", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Strangers calling while silenced, with the screen off: shown quietly, with Answer"
                },
                // pops up without a sound and slides away by itself: no full-screen intent keeps it pinned
                NotificationChannel(CH_QUIET_POPUP, "Silenced calls, screen on", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Strangers calling while silenced and you're using the phone: a silent pop-up, with Answer"
                    setSound(null, null); enableVibration(false)
                },
                NotificationChannel(CH_ONGOING, "Ongoing calls", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setSound(null, null); enableVibration(false)
                },
                NotificationChannel(CH_MISSED, "Missed calls", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CH_SILENCED, "Silenced calls", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Strangers who called while silenced"
                },
                NotificationChannel(StrangersRing.CHANNEL, "Expecting a new number", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "When strangers go back to ringing silently, and whether to stop once a new number has called"
                },
                NotificationChannel(MissedCallReminder.CHANNEL, "Missed-call reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "A missed call you swiped away, an hour later, unless you called back or opened Ghanti"
                },
            )
        )
    }

    fun update(service: InCallService) {
        val call = Calls.primary
        val manager = NotificationManagerCompat.from(service)
        if (call == null) {
            ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
            manager.cancel(ID_RINGING)
            return
        }
        val id = idFor(call.phase)
        val notification = build(service, call)
        try {
            ServiceCompat.startForeground(service, id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } catch (e: Exception) {
            Log.w(TAG, "no foreground for the call", e)
            manager.notify(id, notification)
        }
        manager.cancel(if (id == ID_RINGING) ID_CALL else ID_RINGING)
    }

    internal fun build(context: Context, call: CallUi): android.app.Notification {
        // the number is how Do Not Disturb's "repeat callers" knows someone called before: Android
        // remembers callers from the phone app's call notifications, and a name alone matched no one
        val person = Person.Builder()
            .setName(call.title)
            .setImportant(call.person != null)
            .apply { if (call.number.isNotBlank()) setUri(Uri.fromParts("tel", call.number, null).toString()) }
            .apply { call.person?.photo?.let { photo(context, it) }?.let { setIcon(IconCompat.createWithBitmap(it)) } }
            .build()
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CH_ONGOING)
            .setSmallIcon(R.drawable.ghanti_notification)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .addPerson(person)
            .setOngoing(true)
            // the call is posted again on every change (audio route, hold, a second call); it alerts once
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (call.phase == Phase.Ringing) {
            val answer = PendingIntent.getActivity(
                context, 1,
                Intent(context, InCallActivity::class.java).setAction(InCallActivity.ACTION_ANSWER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            // a handed-back call: Tring rings it itself and the recorder's screen is already up
            val screenOn = popup.getOrPut(call.call) { context.getSystemService(PowerManager::class.java).isInteractive }
            builder.setChannelId(ringingChannel(call.silenced, call.screened, screenOn))
                .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, action(context, CallActionReceiver.DECLINE), answer))
                .setContentText(call.outcome?.guess?.title ?: if (call.silenced) "Ringing silently · not in your contacts" else null)
            if (!call.silenced && !call.screened) builder.setFullScreenIntent(open, true)
        } else {
            builder.setStyle(NotificationCompat.CallStyle.forOngoingCall(person, action(context, CallActionReceiver.HANG_UP)))
                .setContentText(
                    when (call.phase) {
                        Phase.Dialing -> "Calling…"
                        Phase.Holding -> "On hold"
                        Phase.ChooseSim -> "Choose a SIM"
                        else -> null
                    }
                )
            if (call.phase == Phase.Active && call.connectedAt > 0) builder.setUsesChronometer(true).setWhen(call.connectedAt)
        }
        return builder.build()
    }

    private fun action(context: Context, action: String) = PendingIntent.getBroadcast(
        context, action.hashCode(), Intent(context, CallActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    internal fun photo(context: Context, uri: Uri): Bitmap? = try {
        context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
    } catch (e: Exception) {
        null
    }
}
