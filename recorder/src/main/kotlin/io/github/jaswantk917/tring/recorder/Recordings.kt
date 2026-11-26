package io.github.jaswantk917.tring.recorder

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore

data class Recording(val id: Long, val uri: Uri, val who: String, val at: Long, val durationMs: Long)

/** The calls this app recorded, from the phone's media library. */
object Recordings {
    /** where recordings are saved: Recordings/Tring, or Music/Tring recordings before Android 12, which had no Recordings folder */
    fun folder() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "${Environment.DIRECTORY_RECORDINGS}/Tring" else "${Environment.DIRECTORY_MUSIC}/Tring recordings"

    private val collection get() = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    fun get(context: Context, id: Long): Recording? = query(context, "${MediaStore.Audio.Media._ID} = ?", arrayOf(id.toString())).firstOrNull()

    fun list(context: Context): List<Recording> = query(
        context, "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Audio.Media.IS_PENDING} = 0", arrayOf(folder() + "%"),
    )

    private fun query(context: Context, selection: String, args: Array<String>): List<Recording> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DURATION,
        )
        val found = ArrayList<Recording>()
        context.contentResolver.query(collection, projection, selection, args, "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                found += Recording(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    who = Callers.name(context, id, who(cursor.getString(1).orEmpty())),
                    at = cursor.getLong(2) * 1_000,
                    durationMs = cursor.getLong(3),
                )
            }
        }
        return found
    }

    /**
     * Recordings an earlier run left unfinished: a crash mid-call, or 1.8 mixing up two calls.
     * Each one with sound is published, and each empty one deleted. Only those started before
     * [before], so a call recording right now is left alone.
     */
    fun recoverPending(context: Context, before: Long) {
        val resolver = context.contentResolver
        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Audio.Media.DATE_ADDED} < ?"
        val args = arrayOf(folder() + "%", (before / 1_000).toString())
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.IS_PENDING)
        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            resolver.query(
                collection, projection,
                Bundle().apply {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
                    putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
                },
                null,
            )
        } else {
            @Suppress("DEPRECATION")
            resolver.query(MediaStore.setIncludePending(collection), projection, selection, args, null)
        }
        cursor?.use {
            while (it.moveToNext()) {
                val uri = ContentUris.withAppendedId(collection, it.getLong(0))
                // the media library's own size can lag a file that was never closed properly, so the file itself is asked
                val bytes = runCatching { resolver.openFileDescriptor(uri, "r")?.use { file -> file.statSize } }.getOrNull() ?: 0L
                when {
                    bytes == 0L -> resolver.delete(uri, null, null)
                    it.getInt(1) == 1 -> resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
                }
            }
        }
    }

    /** "Asha · 21 Nov, 3.16 pm.m4a" → "Asha" */
    fun who(displayName: String) = displayName.substringBefore(" · ").removeSuffix(".m4a").ifBlank { "Call" }
}
