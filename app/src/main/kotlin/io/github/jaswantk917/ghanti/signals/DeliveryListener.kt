package io.github.jaswantk917.ghanti.signals

import android.app.Notification
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.github.jaswantk917.ghanti.GhantiApp

/** Reads notifications only to notice orders and rides on their way; nothing is kept but the brand, time and any number. */
class DeliveryListener : NotificationListenerService() {

    override fun onListenerConnected() {
        runCatching { activeNotifications }.getOrNull()?.forEach(::consider)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = consider(sbn)

    private fun consider(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        if (silent(sbn) && sbn.notification.flags and Notification.FLAG_ONGOING_EVENT == 0) return
        val text = textOf(sbn.notification.extras).ifBlank { return }
        val signal = DeliveryRules.fromNotification(sbn.packageName, labelOf(sbn.packageName), text, sbn.postTime) ?: return
        GhantiApp.instance.signals.record(signal)
    }

    // promos come on silent channels ("Chef crafted meals at your door in 30 mins"); live trackers are silent too, but ongoing
    private fun silent(sbn: StatusBarNotification): Boolean {
        val ranking = Ranking()
        return currentRanking?.getRanking(sbn.key, ranking) == true && ranking.importance < NotificationManager.IMPORTANCE_DEFAULT
    }

    private fun labelOf(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg
    }

    private fun textOf(extras: Bundle): String {
        // chat apps put the newest message in the messaging style's list, not in the text
        // the typed getter is 13+ only
        @Suppress("DEPRECATION")
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            ?.mapNotNull { (it as? Bundle)?.getCharSequence("text") }.orEmpty()
        return listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_TITLE),
            extras.getCharSequence(Notification.EXTRA_TEXT),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT),
            messages.lastOrNull(),
        ).joinToString("\n")
    }
}
