package io.github.jaswantk917.tring.recorder

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor

/**
 * Lets Tring show its calls' recordings in Recents: the list (when, how long, the summary) and
 * each one's sound to play. It answers Tring and no one else; Tring needs no storage permission,
 * and never learns where the files are.
 */
class RecordingsProvider : ContentProvider() {
    override fun onCreate() = true

    private fun checkCaller() {
        if (callingPackage !in TRING) throw SecurityException("only Tring may read recordings")
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
        checkCaller()
        val context = requireContext()
        val notes = Notes(context)
        return MatrixCursor(COLUMNS).apply {
            Recordings.list(context).forEach { recording ->
                // "" is a call too short to say what it was about: no summary to show
                addRow(arrayOf<Any?>(recording.id, recording.at, recording.durationMs, notes.summary(recording.id)?.ifEmpty { null }))
            }
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        checkCaller()
        if (mode != "r") throw SecurityException("recordings are read only")
        val id = ContentUris.parseId(uri)
        val recording = Recordings.get(requireContext(), id) ?: return null
        return requireContext().contentResolver.openFileDescriptor(recording.uri, "r")
    }

    override fun getType(uri: Uri) = "audio/mp4"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0

    companion object {
        /** Tring's Recents reads again */
        fun changed(context: android.content.Context) =
            runCatching { context.contentResolver.notifyChange(Uri.parse("content://${context.packageName}.recordings/calls"), null) }

        /** Tring's Play build and its debug build */
        private val TRING = setOf("io.github.jaswantk917.chitthighanti", "io.github.jaswantk917.chitthighanti.debug")
        private val COLUMNS = arrayOf("_id", "at", "duration_ms", "summary")
    }
}
