package io.github.jaswantk917.ghanti.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.PhoneLookup
import android.provider.ContactsContract.RawContacts
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Google's contact sync keeps most contacts at their small photo until someone opens them in
 * Contacts, which asks Play services for the full one. Tring asks the same way, a few contacts at a
 * time, so the call screen has a full photo to show. Each contact is asked once: whatever Google
 * has (720 px, 512, or only a small original) comes back in a few seconds.
 */
object FullPhotos {
    private const val TAG = "FullPhotos"
    // Play services' own receiver for "this contact was opened": Google Contacts sends it
    private const val ACTION = "com.google.android.gms.people.sync.focus.SYNC_HIGH_RES_PHOTO"
    private const val GMS = "com.google.android.gms"
    private const val GOOGLE = "com.google"
    /** how many to ask for each time Tring opens; the rest follow on later opens */
    const val BATCH = 20

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun prefs(context: Context) = context.getSharedPreferences("full_photos", Context.MODE_PRIVATE)

    /** a few Google contacts that only have the small photo and weren't asked before */
    fun askSome(context: Context) {
        val app = context.applicationContext
        scope.launch {
            runCatching {
                if (!canAsk(app)) return@launch
                val waiting = smallOnly(app, contactIds = null)
                val asked = prefs(app).getStringSet("asked", emptySet()).orEmpty()
                val next = pick(waiting, asked, BATCH)
                next.forEach { raw ->
                    ask(app, raw)
                    // one at a time, the way opening contacts one by one would
                    delay(400)
                }
                remember(app, next)
            }.onFailure { Log.w(TAG, "couldn't ask for full photos", it) }
        }
    }

    /** this caller, if they only have the small photo: their next call gets the full one */
    fun askFor(context: Context, number: String) {
        if (number.isBlank()) return
        val app = context.applicationContext
        scope.launch {
            runCatching {
                if (!canAsk(app)) return@launch
                val contact = app.contentResolver.query(
                    Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)), arrayOf(PhoneLookup._ID), null, null, null,
                )?.use { if (it.moveToFirst()) it.getLong(0) else null } ?: return@launch
                val asked = prefs(app).getStringSet("asked", emptySet()).orEmpty()
                val next = pick(smallOnly(app, contactIds = listOf(contact)), asked, BATCH)
                next.forEach { ask(app, it) }
                remember(app, next)
            }.onFailure { Log.w(TAG, "couldn't ask for a caller's full photo", it) }
        }
    }

    /** the raw contacts to ask for: not asked before, at most [limit] */
    internal fun pick(waiting: List<Long>, asked: Set<String>, limit: Int): List<Long> =
        waiting.filter { it.toString() !in asked }.distinct().take(limit)

    /** Google raw contacts whose contact has a small photo and no full one */
    private fun smallOnly(context: Context, contactIds: List<Long>?): List<Long> {
        val resolver = context.contentResolver
        val only = contactIds?.joinToString(",")?.let { " AND ${Contacts._ID} IN ($it)" }.orEmpty()
        val contacts = resolver.query(
            Contacts.CONTENT_URI, arrayOf(Contacts._ID),
            "${Contacts.PHOTO_ID} IS NOT NULL AND ${Contacts.PHOTO_FILE_ID} IS NULL$only", null, null,
        )?.use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }.orEmpty()
        if (contacts.isEmpty()) return emptyList()
        return contacts.chunked(400).flatMap { chunk ->
            resolver.query(
                RawContacts.CONTENT_URI, arrayOf(RawContacts._ID),
                "${RawContacts.ACCOUNT_TYPE}=? AND ${RawContacts.DELETED}=0 AND ${RawContacts.CONTACT_ID} IN (${chunk.joinToString(",")})",
                arrayOf(GOOGLE), null,
            )?.use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }.orEmpty()
        }
    }

    private fun request(raw: Long) = Intent(ACTION)
        .setDataAndType(ContentUris.withAppendedId(RawContacts.CONTENT_URI, raw), RawContacts.CONTENT_ITEM_TYPE)
        .setPackage(GMS)

    private fun ask(context: Context, raw: Long) = context.sendBroadcast(request(raw))

    /** Play services is there and still takes the request */
    private fun canAsk(context: Context) =
        context.packageManager.queryBroadcastReceivers(request(0), 0).isNotEmpty()

    private fun remember(context: Context, raws: List<Long>) {
        if (raws.isEmpty()) return
        val prefs = prefs(context)
        val asked = prefs.getStringSet("asked", emptySet()).orEmpty() + raws.map { it.toString() }
        prefs.edit().putStringSet("asked", asked).apply()
    }
}
