package io.github.jaswantk917.tring.recorder

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Records the microphone while Tring says a call is on. Android gives the microphone to the call
 * alone, except to an Accessibility service; that is the only reason this is one. It reads no
 * screen content. Your voice comes through clearly; the other side only as loud as the earpiece or
 * speaker is near the microphone, since only system apps may record the call line itself.
 */
class CallRecorder : AccessibilityService() {
    /** one call's recording: its file in the media library, open for writing, and the microphones */
    private class Take(val uri: Uri, val output: ParcelFileDescriptor, val capture: Capture, val who: String, val startedAt: Long) {
        var endedAt = startedAt
    }

    // Only the call being recorded. A finished call keeps its own Take while it mixes down, which
    // can outlast the next call's start: sharing one slot, a 23-minute call's mixdown published the
    // next call's empty file, hid its own, and left the next call's microphone on (1.8, 2026-11-21).
    private var take: Take? = null
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val callEnded = AudioManager.OnModeChangedListener { mode -> if (mode == AudioManager.MODE_NORMAL) stop() }

    private val commands = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getBooleanExtra(EXTRA_ON, false)) start(intent.getStringExtra(EXTRA_WHO).orEmpty()) else stop()
        }
    }

    override fun onServiceConnected() {
        Updates.schedule(this)
        val connected = System.currentTimeMillis()
        // whatever an earlier run left half done: recordings published or dropped, raw sound deleted
        thread(name = "recover") {
            runCatching { Recordings.recoverPending(this, before = connected) }.onFailure { Log.w(TAG, "couldn't recover recordings", it) }
            (getExternalFilesDir(null) ?: cacheDir).listFiles { file -> file.name.startsWith("call-") && file.lastModified() < connected }
                ?.forEach { it.delete() }
        }
        // Tring is another app, so the receiver must be exported; it only records while a call holds the audio
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commands, IntentFilter(ACTION_RECORD), RECEIVER_EXPORTED)
        } else {
            registerReceiver(commands, IntentFilter(ACTION_RECORD))
        }
    }

    override fun onDestroy() {
        stop()
        runCatching { unregisterReceiver(commands) }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    private fun start(who: String) {
        if (take != null) return report(STATE_RECORDING)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return report(STATE_NEEDS_SETUP)
        if (audio.mode != AudioManager.MODE_IN_CALL && audio.mode != AudioManager.MODE_IN_COMMUNICATION) return report(STATE_OFF)
        val stamp = SimpleDateFormat("d MMM, h.mm a", Locale.getDefault()).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "${who.ifBlank { "Call" }} · $stamp.m4a")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, folder())
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        var uri: Uri? = null
        var output: ParcelFileDescriptor? = null
        try {
            uri = contentResolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return report(STATE_OFF)
            output = contentResolver.openFileDescriptor(uri, "w") ?: error("no file for $uri")
            // the app's own folder on shared storage, readable over USB: the last call's raw sound and the per-call numbers;
            // each call has its own scratch file, since the last one can still be mixing down
            val own = getExternalFilesDir(null)
            Processor.get(this).yieldToCall()
            val capture = Capture(
                File(own ?: cacheDir, "call-${System.currentTimeMillis()}.pcm"),
                keep = own?.let { File(it, "last-call.pcm") }, stats = own?.let { File(it, "calls.log") },
            )
            if (!capture.start()) error("no microphone")
            take = Take(uri, output, capture, who, System.currentTimeMillis())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audio.addOnModeChangedListener(mainExecutor, callEnded)
            report(STATE_RECORDING)
        } catch (e: Exception) {
            Log.w(TAG, "couldn't start recording", e)
            runCatching { output?.close() }
            uri?.let { runCatching { contentResolver.delete(it, null, null) } }
            report(STATE_OFF)
        }
    }

    private fun stop() {
        val call = take ?: return
        take = null
        call.endedAt = System.currentTimeMillis()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audio.removeOnModeChangedListener(callEnded)
        report(STATE_OFF)
        notifySaving(call, null)
        val lead = Screening.takeLead(call.startedAt)
        // the mixdown reads the whole call back, a minute or more for a long call, so it runs off the main thread
        thread(name = "mixdown") {
            // a call ended before any sound came through leaves nothing worth keeping
            val kept = call.capture.finish(call.output.fileDescriptor, Notes(this).turnsFile(ContentUris.parseId(call.uri)), lead) { step -> notifySaving(call, step) }
            release(call, keep = kept)
        }
    }

    private fun release(call: Take, keep: Boolean) {
        runCatching { call.output.close() }
        val uri = call.uri
        if (keep) {
            contentResolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            RecordingsProvider.changed(this)
            notifySaved(uri)
            // the processor's queue lives on the main thread; this runs on the mixdown thread
            Recordings.get(this, ContentUris.parseId(uri))?.let { recording -> mainExecutor.execute { afterCall(uri, recording) } }

        } else {
            getSystemService(NotificationManager::class.java).cancel(uri.hashCode())
            runCatching { contentResolver.delete(uri, null, null) }
        }
    }

    /**
     * While a call mixes down, a minute or more after a long one, so its recording isn't simply
     * missing. Silent; "Call recorded" takes its place, and it times out if the app is killed first.
     */
    private fun notifySaving(call: Take, step: Capture.Step?) {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Saved recordings", NotificationManager.IMPORTANCE_LOW))
        val minutes = ((call.endedAt - call.startedAt) / 60_000).coerceAtLeast(1)
        val steps = Capture.Step.entries.size
        manager.notify(
            call.uri.hashCode(),
            Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle(if (call.who.isBlank()) "Saving the call" else "Saving the call with ${call.who}")
                .setContentText("$minutes min · " + (step?.let { "${it.label}…" } ?: "ready in a moment"))
                .setProgress(steps, step?.let { it.ordinal + 1 } ?: 0, step == null)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setTimeoutAfter(SAVING_TIMEOUT_MS)
                .build(),
        )
    }

    private fun notifySaved(uri: Uri) {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Saved recordings", NotificationManager.IMPORTANCE_LOW))
        manager.notify(
            uri.hashCode(),
            Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Call recorded")
                .setContentText("Tap to play it and read what was said")
                .setContentIntent(openCall(uri))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build(),
        )
    }

    private fun afterCall(uri: Uri, recording: Recording) {
        val processor = Processor.get(this)
        processor.afterCall(recording) { summary ->
            if (!summary.isNullOrEmpty()) {
                notifySummary(uri, recording, summary)
                processor.lately(Recordings.list(this))
            }
        }
    }

    /** the "Call recorded" note becomes what the call was about, in its own channel so these can be turned down on their own */
    private fun notifySummary(uri: Uri, recording: Recording, summary: String) {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(SUMMARIES, "Call summaries", NotificationManager.IMPORTANCE_LOW))
        // switched off: the plain "Call recorded" note stays
        if (manager.getNotificationChannel(SUMMARIES)?.importance == NotificationManager.IMPORTANCE_NONE) return
        manager.notify(
            uri.hashCode(),
            Notification.Builder(this, SUMMARIES)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle(recording.who)
                .setContentText(summary.lineSequence().first().removePrefix("• "))
                .setStyle(Notification.BigTextStyle().bigText(summary))
                .setContentIntent(openCall(uri))
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun openCall(uri: Uri) = PendingIntent.getActivity(
        this, uri.hashCode(),
        Intent(this, RecordingsActivity::class.java).putExtra(RecordingsActivity.EXTRA_OPEN, ContentUris.parseId(uri))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun report(state: String) {
        TRING.forEach { sendBroadcast(Intent(ACTION_STATE).setPackage(it).putExtra(EXTRA_STATE, state)) }
    }

    private fun folder() = Recordings.folder()

    companion object {
        private const val TAG = "CallRecorder"
        private const val CHANNEL = "saved"
        private const val SAVING_TIMEOUT_MS = 15 * 60_000L
        private const val SUMMARIES = "summaries"
        // the contract with Tring's Ghanti; Recorder.kt there holds the same strings
        const val ACTION_RECORD = "io.github.jaswantk917.tring.recorder.RECORD"
        const val ACTION_STATE = "io.github.jaswantk917.tring.RECORDING"
        const val EXTRA_ON = "on"
        const val EXTRA_WHO = "who"
        const val EXTRA_STATE = "state"
        const val STATE_RECORDING = "recording"
        const val STATE_OFF = "off"
        const val STATE_NEEDS_SETUP = "needs_setup"
        private val TRING = listOf("io.github.jaswantk917.chitthighanti", "io.github.jaswantk917.chitthighanti.debug")
    }
}
