package io.github.jaswantk917.ghanti.incall

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.TelecomManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.jaswantk917.ghanti.GhantiApp
import io.github.jaswantk917.ghanti.MainActivity
import io.github.jaswantk917.chitthi.R
import io.github.jaswantk917.ghanti.data.Contacts

/** Telecom asks the phone app to show missed calls; a stranger Ghanti silenced is told apart from a real miss. */
class MissedCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = NotificationManagerCompat.from(context)
        val count = intent.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT, 0)
        if (count == 0) {
            manager.cancel(ID)
            return
        }
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val number = intent.getStringExtra(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER).orEmpty()
        val outcome = GhantiApp.instance.screenLog.forCall(number, System.currentTimeMillis())
        val silenced = count == 1 && outcome?.rang == false
        val person = Contacts.lookup(context, number)
        val who = person?.name ?: outcome?.guess?.title?.let { "$it · $number" } ?: number.ifBlank { "Private number" }

        @Suppress("DEPRECATION") // the typed getter is 13+ only
        val callBack = intent.getParcelableExtra<PendingIntent>(EXTRA_CALL_BACK)
        @Suppress("DEPRECATION")
        val clear = intent.getParcelableExtra<PendingIntent>(EXTRA_CLEAR)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, if (silenced) CallNotifications.CH_SILENCED else CallNotifications.CH_MISSED)
            .setSmallIcon(R.drawable.ghanti_notification)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setContentTitle(if (count > 1) "$count missed calls" else if (silenced) "Silenced call" else "Missed call")
            .setContentText(if (silenced) "$who rang quietly" else who)
            .setContentIntent(open)
            .setDeleteIntent(clear)
            .setAutoCancel(true)
            .apply { person?.photo?.let { CallNotifications.photo(context, it) }?.let(::setLargeIcon) }
        if (callBack != null) builder.addAction(0, "Call back", callBack)
        if (number.isNotBlank()) {
            val message = PendingIntent.getActivity(
                context, 1, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "Message", message)
        }
        manager.notify(ID, builder.build())
    }

    companion object {
        const val ID = 2
        // system-API constants on TelecomManager, but Telecom puts them on this broadcast for the phone app
        private const val EXTRA_CALL_BACK = "android.telecom.extra.CALL_BACK_INTENT"
        private const val EXTRA_CLEAR = "android.telecom.extra.CLEAR_MISSED_CALLS_INTENT"
    }
}
