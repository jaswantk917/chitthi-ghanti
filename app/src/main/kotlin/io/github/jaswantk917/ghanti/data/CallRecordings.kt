package io.github.jaswantk917.ghanti.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import io.github.jaswantk917.ghanti.incall.Recorder

/** A call Tring Recorder recorded: when it started, how long, and what it was about when it knows. */
data class CallRecording(val id: Long, val at: Long, val durationMs: Long, val summary: String?)

/** Recordings from Tring Recorder's provider, for Recents; nothing when the recorder isn't installed or is older than 1.9. */
object CallRecordings {
    private const val TAG = "CallRecordings"
    val LIST: Uri by lazy { Uri.parse("content://${Recorder.PACKAGE}.recordings/calls") }
    // a recording starts once the call connects: after the ringing, up to about a minute and a half
    private const val RING_MS = 90_000L
    // the media library keeps whole seconds, so a recording can look up to a second older than it is
    private const val EARLY_MS = 2_000L

    fun load(context: Context): List<CallRecording> = try {
        context.contentResolver.query(LIST, null, null, null, null)?.use { c ->
            buildList {
                while (c.moveToNext()) add(CallRecording(c.getLong(0), c.getLong(1), c.getLong(2), c.getString(3)))
            }
        }.orEmpty()
    } catch (e: Exception) {
        Log.w(TAG, "no recordings from the recorder", e)
        emptyList()
    }

    fun sound(id: Long): Uri = ContentUris.withAppendedId(LIST, id)

    /** the recorder's own page for the call: its transcript */
    fun open(context: Context, id: Long) {
        context.packageManager.getLaunchIntentForPackage(Recorder.PACKAGE)?.let {
            context.startActivity(it.putExtra("open", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
    }

    /**
     * Each recording to the call it was made during, by call log id: one that connected, started
     * before the recording and lasted past its start. When two fit, the later start wins.
     */
    fun match(entries: List<CallEntry>, recordings: List<CallRecording>): Map<Long, CallRecording> {
        val talked = entries.filter { it.durationSec > 0 }
        val out = HashMap<Long, CallRecording>()
        for (recording in recordings) {
            val call = talked
                .filter { it.id !in out && recording.at >= it.date - EARLY_MS && recording.at <= it.date + RING_MS + it.durationSec * 1_000 }
                .maxByOrNull { it.date } ?: continue
            out[call.id] = recording
        }
        return out
    }
}
