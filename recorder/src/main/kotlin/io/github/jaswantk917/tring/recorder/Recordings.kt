package io.github.jaswantk917.tring.recorder

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

data class Recording(val id: Long, val uri: Uri, val who: String, val at: Long, val durationMs: Long)

/** The calls this app recorded, from the phone's media library. */
object Recordings {
    /** where recordings are saved: Recordings/Tring, or Music/Tring recordings before Android 12, which had no Recordings folder */
    fun folder() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "${Environment.DIRECTORY_RECORDINGS}/Tring" else "${Environment.DIRECTORY_MUSIC}/Tring recordings"

    private val collection get() = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    fun list(context: Context): List<Recording> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DURATION,
        )
        val found = ArrayList<Recording>()
        context.contentResolver.query(
            collection, projection,
            "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Audio.Media.IS_PENDING} = 0",
            arrayOf(folder() + "%"),
            "${MediaStore.Audio.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                found += Recording(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    who = who(cursor.getString(1).orEmpty()),
                    at = cursor.getLong(2) * 1_000,
                    durationMs = cursor.getLong(3),
                )
            }
        }
        return found
    }

    /** "Asha · 21 Nov, 3.16 pm.m4a" → "Asha" */
    fun who(displayName: String) = displayName.substringBefore(" · ").removeSuffix(".m4a").ifBlank { "Call" }
}
