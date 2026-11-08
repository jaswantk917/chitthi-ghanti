package io.github.jaswantk917.ghanti.data

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.PhoneLookup
import android.util.Log
import io.github.jaswantk917.ghanti.signals.DeliveryRules

data class Person(val name: String, val photo: Uri? = null)

data class PhoneNumber(val number: String, val label: String)

data class Contact(val id: Long, val name: String, val photo: Uri?, val starred: Boolean, val numbers: List<PhoneNumber>) {
    /** what a tap calls without asking: the only number, or the one you said to always use, if it still exists */
    fun usualNumber(remembered: String?): PhoneNumber? =
        remembered?.let { r -> numbers.firstOrNull { DeliveryRules.last10(it.number) == DeliveryRules.last10(r) } } ?: numbers.singleOrNull()
}

object Contacts {
    private const val TAG = "Contacts"

    fun lookup(context: Context, number: String): Person? {
        if (number.isBlank()) return null
        return try {
            context.contentResolver.query(
                Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                arrayOf(PhoneLookup.DISPLAY_NAME, PhoneLookup.PHOTO_THUMBNAIL_URI), null, null, null,
            )?.use { c -> if (c.moveToFirst()) Person(c.getString(0), c.getString(1)?.let(Uri::parse)) else null }
        } catch (e: SecurityException) {
            Log.w(TAG, "no contacts access", e)
            null
        }
    }

    fun all(context: Context): List<Contact> = try {
        context.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER, Phone.TYPE, Phone.LABEL, Phone.PHOTO_THUMBNAIL_URI, Phone.STARRED),
            null, null, "${Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
        )?.use { c ->
            val byId = LinkedHashMap<Long, Contact>()
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val number = c.getString(2) ?: continue
                val label = Phone.getTypeLabel(context.resources, c.getInt(3), c.getString(4)).toString()
                val existing = byId[id]
                if (existing == null) {
                    byId[id] = Contact(id, c.getString(1) ?: number, c.getString(5)?.let(Uri::parse), c.getInt(6) == 1, listOf(PhoneNumber(number, label)))
                } else if (existing.numbers.none { DeliveryRules.last10(it.number) == DeliveryRules.last10(number) }) {
                    byId[id] = existing.copy(numbers = existing.numbers + PhoneNumber(number, label))
                }
            }
            byId.values.toList()
        }.orEmpty()
    } catch (e: SecurityException) {
        Log.w(TAG, "no contacts access", e)
        emptyList()
    }
}
