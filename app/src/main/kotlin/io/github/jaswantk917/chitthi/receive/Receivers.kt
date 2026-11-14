package io.github.jaswantk917.chitthi.receive

import android.app.Activity
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.provider.Telephony
import android.provider.Telephony.Mms
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.core.content.FileProvider
import io.github.jaswantk917.block.BlockRules
import io.github.jaswantk917.block.BlockedSms
import io.github.jaswantk917.block.isSaved
import io.github.jaswantk917.chitthi.ChitthiApp
import io.github.jaswantk917.chitthi.classify.Otp
import io.github.jaswantk917.chitthi.data.SmsStore
import io.github.jaswantk917.chitthi.mms.Pdu
import io.github.jaswantk917.chitthi.notify.Clipboard
import io.github.jaswantk917.chitthi.notify.Notifier
import io.github.jaswantk917.chitthi.send.SmsSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private fun BroadcastReceiver.async(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** Sort, copy the code, and notify for a message that's just been written to the store. */
private object Arrival {
    suspend fun handle(context: Context, key: String) {
        val app = ChitthiApp.instance
        val inbox = app.inbox
        inbox.reloadNow()
        val message = inbox.message(key) ?: return
        val verdict = inbox.verdict(message)
        val senderKey = inbox.senderKey(message)
        val sender = inbox.sender(senderKey) ?: return
        val settings = app.prefs.settings.value
        verdict.otp?.takeIf { settings.otpAutoCopy }?.let { code ->
            Clipboard.copyOtp(context, code)
            withContext(Dispatchers.Main) { Toast.makeText(context, "Copied OTP $code", Toast.LENGTH_SHORT).show() }
        }
        if (inbox.visibleSender == senderKey) {
            inbox.markRead(senderKey)
            return
        }
        val unread = inbox.messagesOf(senderKey).filter { it.incoming && !it.read }
        Notifier.incoming(context, message, verdict, sender, unread.ifEmpty { listOf(message) }, settings.promoNotifications)
    }
}

/** Only the default SMS app gets this, and must write the message to the store itself. */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)?.filterNotNull().orEmpty()
        val first = parts.firstOrNull() ?: return
        val address = first.displayOriginatingAddress ?: first.originatingAddress ?: return
        val body = parts.joinToString("") { it.displayMessageBody ?: it.messageBody.orEmpty() }
        val subId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, SubscriptionManager.getDefaultSmsSubscriptionId())
        async {
            // a rule's catch stays out of the inbox and makes no sound, but is kept on the Blocked screen
            val blocks = ChitthiApp.instance.blocks
            BlockRules.forSms(blocks.rules.value, address, body, isSaved(context, address))?.let { rule ->
                blocks.keep(BlockedSms(address, body, first.timestampMillis, subId, rule.id))
                return@async
            }
            val row = SmsStore.insertIncoming(context, address, body, first.timestampMillis, subId) ?: return@async
            Arrival.handle(context, "s${ContentUris.parseId(row)}")
        }
    }
}

/** An MMS has arrived at the carrier: fetch it. */
class MmsPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pdu = intent.getByteArrayExtra("data") ?: return
        val notification = runCatching { Pdu.parse(pdu) }.getOrNull() ?: return
        if (notification.type != Pdu.TYPE_NOTIFICATION_IND) return
        val location = notification.contentLocation ?: return
        val subId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, SubscriptionManager.getDefaultSmsSubscriptionId())
        val file = File(context.cacheDir, "mms/${System.nanoTime()}.pdu").apply { parentFile?.mkdirs(); createNewFile() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.grantUriPermission(PHONE_PACKAGE, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val downloaded = PendingIntent.getBroadcast(
            context, file.name.hashCode(),
            Intent(context, MmsDownloadedReceiver::class.java).putExtra(MmsDownloadedReceiver.EXTRA_FILE, file.absolutePath),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ONE_SHOT,
        )
        SmsSender.forSim(context, subId)
            .downloadMultimediaMessage(context, location, uri, null, downloaded)
    }

    companion object {
        private const val PHONE_PACKAGE = "com.android.phone"
    }
}

class MmsDownloadedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val file = intent.getStringExtra(EXTRA_FILE)?.let(::File) ?: return
        val ok = resultCode == Activity.RESULT_OK
        async {
            try {
                if (!ok) return@async
                val message = runCatching { Pdu.parse(file.readBytes()) }.getOrNull() ?: return@async
                if (message.type != Pdu.TYPE_RETRIEVE_CONF) return@async
                val id = persist(context, message) ?: return@async
                Arrival.handle(context, "m$id")
            } finally {
                file.delete()
            }
        }
    }

    private fun persist(context: Context, pdu: Pdu.Message): Long? {
        val from = pdu.from ?: return null
        val resolver = context.contentResolver
        val row = resolver.insert(Mms.Inbox.CONTENT_URI, ContentValues().apply {
            put(Mms.THREAD_ID, Telephony.Threads.getOrCreateThreadId(context, from))
            put(Mms.DATE, System.currentTimeMillis() / 1000)
            put(Mms.DATE_SENT, pdu.date ?: (System.currentTimeMillis() / 1000))
            put(Mms.MESSAGE_BOX, Mms.MESSAGE_BOX_INBOX)
            put(Mms.READ, 0)
            put(Mms.SEEN, 0)
            put(Mms.SUBJECT, pdu.subject)
            put(Mms.SUBJECT_CHARSET, UTF8_MIB)
            put(Mms.CONTENT_TYPE, pdu.contentType)
            put(Mms.MESSAGE_TYPE, Pdu.TYPE_RETRIEVE_CONF)
            put(Mms.MESSAGE_ID, pdu.messageId)
            put(Mms.TRANSACTION_ID, pdu.transactionId)
            put(Mms.MMS_VERSION, MMS_VERSION_1_2)
        }) ?: return null
        val id = ContentUris.parseId(row)
        pdu.parts.forEach { part ->
            val values = ContentValues().apply {
                put(Mms.Part.MSG_ID, id)
                put(Mms.Part.CONTENT_TYPE, part.contentType)
                put(Mms.Part.NAME, part.name)
                put(Mms.Part.CONTENT_ID, part.contentId)
                put(Mms.Part.CONTENT_LOCATION, part.contentLocation)
                put(Mms.Part.CHARSET, part.charset ?: UTF8_MIB)
                part.text?.let { put(Mms.Part.TEXT, it) }
            }
            val partUri = resolver.insert(Uri.parse("content://mms/$id/part"), values) ?: return@forEach
            if (part.text == null) resolver.openOutputStream(partUri)?.use { it.write(part.data) }
        }
        resolver.insert(Uri.parse("content://mms/$id/addr"), ContentValues().apply {
            put(Mms.Addr.ADDRESS, from)
            put(Mms.Addr.TYPE, MMS_FROM)
            put(Mms.Addr.CHARSET, UTF8_MIB)
        })
        return id
    }

    companion object {
        const val EXTRA_FILE = "file"
        private const val UTF8_MIB = 106
        private const val MMS_FROM = 137
        private const val MMS_VERSION_1_2 = 0x12
    }
}

/** "Reply with a message" from the incoming-call screen. */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        val to = intent?.data?.schemeSpecificPart?.removePrefix("//")?.split(',', ';')?.filter { it.isNotBlank() }
        if (!text.isNullOrBlank() && !to.isNullOrEmpty()) SmsSender.send(this, to, text)
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
