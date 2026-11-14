package io.github.jaswantk917.chitthi.data

import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.provider.Telephony.Mms
import android.provider.Telephony.Sms
import android.util.Log
import io.github.jaswantk917.chitthi.model.Box
import io.github.jaswantk917.chitthi.model.Message
import io.github.jaswantk917.chitthi.model.MmsPart

/** The system's SMS/MMS tables. Reads work with READ_SMS; writes only while Chitthi is the default app. */
object SmsStore {
    private const val TAG = "SmsStore"
    private val MMS_PART_URI: Uri = Uri.parse("content://mms/part")
    private const val MMS_FROM = 137

    /** the role is the source of truth; Sms.getDefaultSmsPackage can lag behind or miss a role change */
    fun isDefault(context: Context): Boolean =
        context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_SMS)

    fun loadSms(context: Context, threadIds: Collection<Long>? = null): List<Message> {
        val selection = threadIds?.let { "${Sms.THREAD_ID} IN (${it.joinToString(",")})" }
        return context.contentResolver.query(
            Sms.CONTENT_URI,
            arrayOf(Sms._ID, Sms.THREAD_ID, Sms.ADDRESS, Sms.BODY, Sms.DATE, Sms.TYPE, Sms.READ),
            selection, null, "${Sms.DATE} DESC",
        )?.use { c ->
            val out = ArrayList<Message>(c.count)
            while (c.moveToNext()) {
                val id = c.getLong(0)
                out += Message(
                    key = "s$id",
                    id = id,
                    isMms = false,
                    threadId = c.getLong(1),
                    address = c.getString(2).orEmpty(),
                    body = c.getString(3).orEmpty(),
                    date = c.getLong(4),
                    box = smsBox(c.getInt(5)),
                    read = c.getInt(6) != 0,
                )
            }
            out
        }.orEmpty()
    }

    /** MMS bodies come from their text parts; the sender of a 1:1 thread is its only recipient. */
    fun loadMms(context: Context, recipients: Map<Long, List<String>>, threadIds: Collection<Long>? = null): List<Message> {
        val texts = HashMap<Long, String>()
        val withImage = HashSet<Long>()
        context.contentResolver.query(
            MMS_PART_URI, arrayOf(Mms.Part.MSG_ID, Mms.Part.CONTENT_TYPE, Mms.Part.TEXT), null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val mid = c.getLong(0)
                val type = c.getString(1).orEmpty()
                when {
                    type == "text/plain" -> texts[mid] = listOfNotNull(texts[mid], c.getString(2)).joinToString("\n")
                    type.startsWith("image/") -> withImage += mid
                }
            }
        }
        val selection = buildString {
            // 130 is a notification of an MMS that was never downloaded
            append("${Mms.MESSAGE_TYPE} != 130")
            threadIds?.let { append(" AND ${Mms.THREAD_ID} IN (${it.joinToString(",")})") }
        }
        return context.contentResolver.query(
            Mms.CONTENT_URI,
            arrayOf(Mms._ID, Mms.THREAD_ID, Mms.DATE, Mms.MESSAGE_BOX, Mms.READ),
            selection, null, "${Mms.DATE} DESC",
        )?.use { c ->
            val out = ArrayList<Message>(c.count)
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val threadId = c.getLong(1)
                out += Message(
                    key = "m$id",
                    id = id,
                    isMms = true,
                    threadId = threadId,
                    address = recipients[threadId]?.joinToString(",").orEmpty(),
                    body = texts[id].orEmpty(),
                    date = c.getLong(2) * 1000,
                    box = mmsBox(c.getInt(3)),
                    read = c.getInt(4) != 0,
                    hasImage = id in withImage,
                )
            }
            out
        }.orEmpty()
    }

    fun mmsParts(context: Context, mmsId: Long): List<MmsPart> = context.contentResolver.query(
        MMS_PART_URI,
        arrayOf(Mms.Part._ID, Mms.Part.CONTENT_TYPE, Mms.Part.TEXT),
        "${Mms.Part.MSG_ID} = ?", arrayOf(mmsId.toString()), null,
    )?.use { c ->
        buildList {
            while (c.moveToNext()) {
                val type = c.getString(1).orEmpty()
                if (type == "application/smil") continue
                add(MmsPart(Uri.withAppendedPath(MMS_PART_URI, c.getLong(0).toString()), type, c.getString(2)))
            }
        }
    }.orEmpty()

    fun mmsSender(context: Context, mmsId: Long): String? = context.contentResolver.query(
        Uri.parse("content://mms/$mmsId/addr"), arrayOf(Mms.Addr.ADDRESS, Mms.Addr.TYPE), null, null, null,
    )?.use { c ->
        var from: String? = null
        while (c.moveToNext()) if (c.getInt(1) == MMS_FROM) from = c.getString(0)
        from
    }

    /** thread id → its recipients' addresses. Some OEM builds don't expose these tables, hence the catch. */
    fun threadRecipients(context: Context): Map<Long, List<String>> = try {
        val addresses = context.contentResolver.query(
            Uri.parse("content://mms-sms/canonical-addresses"), arrayOf("_id", "address"), null, null, null,
        )?.use { c -> buildMap { while (c.moveToNext()) put(c.getLong(0), c.getString(1).orEmpty()) } }.orEmpty()
        context.contentResolver.query(
            Telephony.Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple", "true").build(),
            arrayOf(Telephony.Threads._ID, Telephony.Threads.RECIPIENT_IDS), null, null, null,
        )?.use { c ->
            buildMap {
                while (c.moveToNext()) {
                    val ids = c.getString(1).orEmpty().split(' ').mapNotNull { it.toLongOrNull() }
                    put(c.getLong(0), ids.mapNotNull { addresses[it] })
                }
            }
        }.orEmpty()
    } catch (e: Exception) {
        Log.w(TAG, "thread recipients unavailable", e)
        emptyMap()
    }

    fun insertIncoming(context: Context, address: String, body: String, sentAt: Long, subId: Int): Uri? =
        context.contentResolver.insert(Sms.Inbox.CONTENT_URI, ContentValues().apply {
            put(Sms.ADDRESS, address)
            put(Sms.BODY, body)
            put(Sms.DATE, System.currentTimeMillis())
            put(Sms.DATE_SENT, sentAt)
            put(Sms.READ, 0)
            put(Sms.SEEN, 0)
            put(Sms.TYPE, Sms.MESSAGE_TYPE_INBOX)
            put(Sms.SUBSCRIPTION_ID, subId)
        })

    fun insertOutgoing(context: Context, address: String, body: String, subId: Int?): Uri? =
        context.contentResolver.insert(Sms.CONTENT_URI, ContentValues().apply {
            put(Sms.ADDRESS, address)
            put(Sms.BODY, body)
            put(Sms.DATE, System.currentTimeMillis())
            put(Sms.READ, 1)
            put(Sms.SEEN, 1)
            put(Sms.TYPE, Sms.MESSAGE_TYPE_OUTBOX)
            put(Sms.THREAD_ID, Telephony.Threads.getOrCreateThreadId(context, address))
            subId?.let { put(Sms.SUBSCRIPTION_ID, it) }
        })

    /** the SIM the conversation with [address] last went through, sent or received */
    fun lastSim(context: Context, address: String): Int? = try {
        val thread = Telephony.Threads.getOrCreateThreadId(context, address)
        context.contentResolver.query(
            Sms.CONTENT_URI, arrayOf(Sms.SUBSCRIPTION_ID),
            "${Sms.THREAD_ID} = ? AND ${Sms.SUBSCRIPTION_ID} >= 0", arrayOf(thread.toString()), "${Sms.DATE} DESC LIMIT 1",
        )?.use { c -> if (c.moveToFirst()) c.getInt(0) else null }
    } catch (e: Exception) {
        Log.w(TAG, "no SIM for $address", e)
        null
    }

    fun setType(context: Context, uri: Uri, type: Int) {
        context.contentResolver.update(uri, ContentValues().apply { put(Sms.TYPE, type) }, null, null)
    }

    fun markRead(context: Context, threadIds: Collection<Long>) {
        if (threadIds.isEmpty() || !isDefault(context)) return
        val values = ContentValues().apply { put(Sms.READ, 1); put(Sms.SEEN, 1) }
        val inThreads = "thread_id IN (${threadIds.joinToString(",")}) AND read = 0"
        context.contentResolver.update(Sms.CONTENT_URI, values, inThreads, null)
        context.contentResolver.update(Mms.CONTENT_URI, values, inThreads, null)
    }

    fun delete(context: Context, messages: Collection<Message>): Boolean {
        if (!isDefault(context)) return false
        messages.forEach {
            val uri = if (it.isMms) Uri.withAppendedPath(Mms.CONTENT_URI, it.id.toString())
            else Uri.withAppendedPath(Sms.CONTENT_URI, it.id.toString())
            context.contentResolver.delete(uri, null, null)
        }
        return true
    }

    private fun smsBox(type: Int) = when (type) {
        Sms.MESSAGE_TYPE_INBOX -> Box.Inbox
        Sms.MESSAGE_TYPE_SENT -> Box.Sent
        Sms.MESSAGE_TYPE_DRAFT -> Box.Draft
        Sms.MESSAGE_TYPE_FAILED -> Box.Failed
        else -> Box.Sending
    }

    private fun mmsBox(box: Int) = when (box) {
        Mms.MESSAGE_BOX_INBOX -> Box.Inbox
        Mms.MESSAGE_BOX_SENT -> Box.Sent
        Mms.MESSAGE_BOX_DRAFTS -> Box.Draft
        Mms.MESSAGE_BOX_FAILED -> Box.Failed
        else -> Box.Sending
    }
}
