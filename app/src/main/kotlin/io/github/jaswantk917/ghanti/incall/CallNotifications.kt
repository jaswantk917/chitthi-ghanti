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
import android.telecom.InCallService
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.ServiceCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.jaswantk917.chitthi.R

object CallNotifications {
    private const val TAG = "CallNotifications"
    private const val CH_RINGING = "ringing"
    private const val CH_QUIET = "ringing_quiet"
    private const val CH_ONGOING = "ongoing"
    const val CH_MISSED = "missed"
    const val CH_SILENCED = "silenced"
    private const val ID_CALL = 1

    fun createChannels(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                // Telecom plays the ringtone and vibrates; the channels stay silent so nothing doubles up
                NotificationChannel(CH_RINGING, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null); enableVibration(false)
                },
                NotificationChannel(CH_QUIET, "Silenced calls ringing", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Strangers calling while silenced: shown quietly, with Answer"
                },
                NotificationChannel(CH_ONGOING, "Ongoing calls", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setSound(null, null); enableVibration(false)
                },
                NotificationChannel(CH_MISSED, "Missed calls", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CH_SILENCED, "Silenced calls", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Strangers who called while silenced"
                },
            )
        )
    }

    fun update(service: InCallService) {
        val call = Calls.primary
        if (call == null) {
            ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
            return
        }
        val notification = build(service, call)
        try {
            ServiceCompat.startForeground(service, ID_CALL, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } catch (e: Exception) {
            Log.w(TAG, "no foreground for the call", e)
            NotificationManagerCompat.from(service).notify(ID_CALL, notification)
        }
    }

    private fun build(context: Context, call: CallUi): android.app.Notification {
        val person = Person.Builder()
            .setName(call.title)
            .setImportant(call.person != null)
            .apply { call.person?.photo?.let { photo(context, it) }?.let { setIcon(IconCompat.createWithBitmap(it)) } }
            .build()
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CH_ONGOING)
            .setSmallIcon(R.drawable.ghanti_notification)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (call.phase == Phase.Ringing) {
            val answer = PendingIntent.getActivity(
                context, 1,
                Intent(context, InCallActivity::class.java).setAction(InCallActivity.ACTION_ANSWER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.setChannelId(if (call.silenced) CH_QUIET else CH_RINGING)
                .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, action(context, CallActionReceiver.DECLINE), answer))
                .setContentText(call.outcome?.guess?.title ?: if (call.silenced) "Ringing silently · not in your contacts" else null)
            if (!call.silenced) builder.setFullScreenIntent(open, true)
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
