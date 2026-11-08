package io.github.jaswantk917.block

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract.PhoneLookup

/** Whether a number belongs to someone saved; rules never touch them. */
fun isSaved(context: Context, number: String): Boolean {
    if (number.none { it.isDigit() }) return false
    return runCatching {
        context.contentResolver.query(Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)), arrayOf(PhoneLookup._ID), null, null, null)
            ?.use { it.moveToFirst() } ?: false
    }.getOrDefault(false)
}
