package io.github.jaswantk917.chitthi.send

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony.Sms
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import io.github.jaswantk917.chitthi.data.SmsStore
import io.github.jaswantk917.chitthi.notify.Notifier
import java.util.concurrent.atomic.AtomicInteger

object SmsSender {
    private val requestCodes = AtomicInteger((System.currentTimeMillis() % Int.MAX_VALUE).toInt())

    /**
     * As the default app Chitthi writes the outbox row itself and flips it to sent or failed;
     * otherwise the system records what it sends.
     */
    fun send(context: Context, addresses: List<String>, text: String) {
        val isDefault = SmsStore.isDefault(context)
        addresses.forEach { address ->
            // a reply goes out on the SIM the conversation is on, while that SIM is still in the phone
            val sim = SmsStore.lastSim(context, address)?.takeIf { SubscriptionManager.getSlotIndex(it) != SubscriptionManager.INVALID_SIM_SLOT_INDEX }
            val sms = forSim(context, sim)
            val parts = sms.divideMessage(text)
            val row = if (isDefault) SmsStore.insertOutgoing(context, address, text, sim) else null
            val sent = ArrayList(parts.indices.map { i ->
                PendingIntent.getBroadcast(
                    context, requestCodes.incrementAndGet(),
                    Intent(context, SmsSentReceiver::class.java)
                        .putExtra(SmsSentReceiver.EXTRA_ROW, row?.toString())
                        .putExtra(SmsSentReceiver.EXTRA_LAST, i == parts.lastIndex)
                        .putExtra(SmsSentReceiver.EXTRA_ADDRESS, address),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ONE_SHOT,
                )
            })
            sms.sendMultipartTextMessage(address, null, parts, sent, null)
        }
    }

    /** null: the phone's default SMS SIM. The SmsManager service and createForSubscriptionId arrived in Android 12; minSdk is 10 */
    @Suppress("DEPRECATION")
    fun forSim(context: Context, subId: Int?): SmsManager = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> if (subId == null) SmsManager.getDefault() else SmsManager.getSmsManagerForSubscriptionId(subId)
        subId == null -> context.getSystemService(SmsManager::class.java)
        else -> context.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
    }
}

class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val row = intent.getStringExtra(EXTRA_ROW)?.let(Uri::parse)
        if (resultCode == Activity.RESULT_OK) {
            if (row != null && intent.getBooleanExtra(EXTRA_LAST, true)) {
                SmsStore.setType(context, row, Sms.MESSAGE_TYPE_SENT)
            }
        } else {
            row?.let { SmsStore.setType(context, it, Sms.MESSAGE_TYPE_FAILED) }
            Notifier.notSent(context, intent.getStringExtra(EXTRA_ADDRESS).orEmpty())
        }
    }

    companion object {
        const val EXTRA_ROW = "row"
        const val EXTRA_LAST = "last"
        const val EXTRA_ADDRESS = "address"
    }
}
