package io.github.jaswantk917.ghanti.signals

import android.app.Notification
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
        val text = textOf(sbn.notification.extras).ifBlank { return }
        val signal = DeliveryRules.fromNotification(sbn.packageName, labelOf(sbn.packageName), text, sbn.postTime) ?: return
        GhantiApp.instance.signals.record(signal)
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
